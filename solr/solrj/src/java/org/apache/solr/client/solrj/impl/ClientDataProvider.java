/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.client.solrj.impl;


import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.GenericSolrRequest;
import org.apache.solr.client.solrj.response.SimpleSolrResponse;
import org.apache.solr.cloud.autoscaling.ClusterDataProvider;
import org.apache.solr.cloud.autoscaling.Policy.ReplicaInfo;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.solr.common.cloud.rule.ImplicitSnitch;
import org.apache.solr.common.cloud.rule.RemoteCallback;
import org.apache.solr.common.cloud.rule.SnitchContext;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.StrUtils;
import org.apache.solr.common.util.Utils;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientDataProvider implements ClusterDataProvider {

  private final CloudSolrClient solrClient;
  private Set<String> liveNodes;
  private Map<String,Object> snitchSession = new HashMap<>();
  private final Map<String, Map<String, Map<String, List<ReplicaInfo>>>> data = new HashMap<>();

  public ClientDataProvider(CloudSolrClient solrClient) {
    this.solrClient = solrClient;
    ZkStateReader zkStateReader = solrClient.getZkStateReader();
    ClusterState clusterState = zkStateReader.getClusterState();
    this.liveNodes = clusterState.getLiveNodes();
    Map<String, ClusterState.CollectionRef> all = clusterState.getCollectionStates();
    all.forEach((collName, ref) -> {
      DocCollection coll = ref.get();
      if (coll == null) return;
      coll.forEachReplica((shard, replica) -> {
        Map<String, Map<String, List<ReplicaInfo>>> nodeData = data.get(replica.getNodeName());
        if (nodeData == null) data.put(replica.getNodeName(), nodeData = new HashMap<>());
        Map<String, List<ReplicaInfo>> collData = nodeData.get(collName);
        if (collData == null) nodeData.put(collName, collData = new HashMap<>());
        List<ReplicaInfo> replicas = collData.get(shard);
        if (replicas == null) collData.put(shard, replicas = new ArrayList<>());
        replicas.add(new ReplicaInfo(replica.getName(), new HashMap<>()));
      });
    });
  }

  @Override
  public Map<String, Object> getNodeValues(String node, Collection<String> keys) {
    AutoScalingSnitch  snitch = new AutoScalingSnitch();
    ClientSnitchCtx ctx = new ClientSnitchCtx(null, node, snitchSession, solrClient);
    snitch.getRemoteInfo(node, new HashSet<>(keys), ctx);
    return ctx.getTags();
  }

  @Override
  public Map<String, Map<String, List<ReplicaInfo>>> getReplicaInfo(String node, Collection<String> keys) {
    return data.get(node);//todo fill other details
  }

  @Override
  public Collection<String> getNodes() {
    return liveNodes;
  }


  static class ClientSnitchCtx
      extends SnitchContext {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    ZkClientClusterStateProvider zkClientClusterStateProvider;
    CloudSolrClient solrClient;

    public ClientSnitchCtx(SnitchInfo perSnitch,
                           String node, Map<String, Object> session,
                           CloudSolrClient solrClient) {
      super(perSnitch, node, session);
      this.solrClient = solrClient;
      this.zkClientClusterStateProvider = (ZkClientClusterStateProvider) solrClient.getClusterStateProvider();
    }


    public Map getZkJson(String path) {
      try {
        byte[] data = zkClientClusterStateProvider.getZkStateReader().getZkClient().getData(path, null, new Stat(), true);
        if (data == null) return null;
        return (Map) Utils.fromJSON(data);
      } catch (Exception e) {
        log.warn("Unable to read from ZK path : " + path, e);
        return null;
      }
    }

    public void invokeRemote(String node, ModifiableSolrParams params, String klas, RemoteCallback callback) {

    }

    public SimpleSolrResponse invoke(String solrNode, String path, SolrParams params)
        throws IOException, SolrServerException {
      String url = zkClientClusterStateProvider.getZkStateReader().getBaseUrlForNodeName(solrNode);

      GenericSolrRequest request = new GenericSolrRequest(SolrRequest.METHOD.GET, path, params);
      try (HttpSolrClient client = new HttpSolrClient.Builder()
          .withHttpClient(solrClient.getHttpClient())
          .withBaseSolrUrl(url)
          .withResponseParser(new BinaryResponseParser())
          .build()) {
        NamedList<Object> rsp = client.request(request);
        request.response.nl = rsp;
        return request.response;
      }
    }

  }

  //uses metrics API to get node information
  static class AutoScalingSnitch extends ImplicitSnitch {


    @Override
    protected void getRemoteInfo(String solrNode, Set<String> requestedTags, SnitchContext ctx) {
      ClientSnitchCtx snitchContext = (ClientSnitchCtx) ctx;
      List<String> groups = new ArrayList<>();
      List<String> prefixes = new ArrayList<>();
      if (requestedTags.contains(DISK)) {
        groups.add("solr.node");
        prefixes.add("CONTAINER.fs.usableSpace");
      }
      if (requestedTags.contains(CORES)) {
        groups.add("solr.core");
        prefixes.add("CORE.coreName");
      }
      if(groups.isEmpty() || prefixes.isEmpty()) return;

      ModifiableSolrParams params = new ModifiableSolrParams();
      params.add("group", StrUtils.join(groups, ','));
      params.add("prefix", StrUtils.join(prefixes,','));

      try {
        SimpleSolrResponse rsp = snitchContext.invoke(solrNode, CommonParams.METRICS_PATH, params);
        Map m = rsp.nl.asMap(4);
        if(requestedTags.contains(DISK)){
          Number n = (Number) Utils.getObjectByPath(m,true, "metrics/solr.node/CONTAINER.fs.usableSpace/value");
          if(n != null) ctx.getTags().put(DISK, n.longValue());
        }
        if(requestedTags.contains(CORES)){
          int count = 0;
          Map cores  = (Map) m.get("metrics");
          for (Object o : cores.keySet()) {
            if(o.toString().startsWith("solr.core.")) count++;
          }
          ctx.getTags().put(CORES, count);
        }

      } catch (Exception e) {
        throw new SolrException(SolrException.ErrorCode.SERVER_ERROR, "", e);
      }

    }
  }
}
