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

package org.apache.solr.cloud.autoscaling;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.cloud.autoscaling.Policy.Suggester.Hint;
import org.apache.solr.common.util.Utils;
import org.apache.solr.common.util.ValidatingJsonMap;

import static org.apache.solr.common.params.CollectionParams.CollectionAction.ADDREPLICA;

public class TestPolicy extends SolrTestCaseJ4 {

  public void testOperands() {
    Clause c = new Clause((Map<String, Object>) Utils.fromJSONString("{replica:'<2', node:'#ANY'}"));
    assertFalse(c.replica.isPass(3));
    assertFalse(c.replica.isPass(2));
    assertTrue(c.replica.isPass(1));

    c = new Clause((Map<String, Object>) Utils.fromJSONString("{replica:'>2', node:'#ANY'}"));
    assertTrue(c.replica.isPass(3));
    assertFalse(c.replica.isPass(2));
    assertFalse(c.replica.isPass(1));

    c = new Clause((Map<String, Object>) Utils.fromJSONString("{nodeRole:'!overseer'}"));
    assertTrue(c.tag.isPass("OVERSEER"));
    assertFalse(c.tag.isPass("overseer"));
  }

  public void testRow() {
    Row row = new Row("nodex", new Cell[]{new Cell(0, "node", "nodex")}, false, new HashMap<>(), new ArrayList<>());
    Row r1 = row.addReplica("c1", "s1");
    Row r2 = r1.addReplica("c1", "s1");
    assertEquals(1, r1.replicaInfo.get("c1").get("s1").size());
    assertEquals(2, r2.replicaInfo.get("c1").get("s1").size());
    assertTrue(r2.replicaInfo.get("c1").get("s1").get(0) instanceof Policy.ReplicaInfo);
    assertTrue(r2.replicaInfo.get("c1").get("s1").get(1) instanceof Policy.ReplicaInfo);
  }

  public void testMerge() {

    Map map = (Map) Utils.fromJSONString("{" +
        "  'cluster-preferences': [" +
        "    { 'maximize': 'freedisk', 'precision': 50}," +
        "    { 'minimize': 'cores', 'precision': 50}" +
        "  ]," +
        "  'cluster-policy': [" +
        "    { 'replica': '#ANY', 'nodeRole': '!overseer'}," +
        "    { 'replica': '<2', 'shard': '#EACH', 'node': '#ANY'}" +
        "  ]," +
        "  'policies': {" +
        "    'policy1': [" +
        "      { 'replica': '1', 'sysprop.fs': 'ssd', 'shard': '#EACH'}," +
        "      { 'replica': '<2', 'shard': '#ANY', 'node': '#ANY'}," +
        "      { 'replica': '<2', 'shard': '#EACH', 'rack': 'rack1'}" +
        "    ]" +
        "  }" +
        "}");
    Policy policy = new Policy(map);
    List<Clause> clauses = Policy.mergePolicies("mycoll", policy.policies.get("policy1"), policy.clusterPolicy);
    Collections.sort(clauses);
    assertEquals(clauses.size(), 4);
    assertEquals("1", String.valueOf(clauses.get(0).original.get("replica")));
    assertEquals("<2", String.valueOf(clauses.get(1).original.get("replica")));
    assertEquals("#ANY", clauses.get(3).original.get("shard"));
    assertEquals("rack1",clauses.get(1).original.get("rack"));
    assertEquals("!overseer", clauses.get(2).original.get("nodeRole"));
  }


  public void testConditionsSort(){
    String rules = "{" +
        "    'cluster-policy':[" +
        "      { 'nodeRole':'!overseer', 'strict':false}," +
        "      { 'replica':'<1', 'node':'node3', 'shard':'#EACH'}," +
        "      { 'replica':'<2', 'node':'#ANY', 'shard':'#EACH'}," +
        "      { 'replica':1, 'rack':'rack1'}]" +
        "  }";
    Policy p = new Policy((Map<String, Object>) Utils.fromJSONString(rules));
    List<Clause> clauses = new ArrayList<>(p.getClusterPolicy());
    Collections.sort(clauses);
    assertEquals("rack", clauses.get(0).tag.name);
  }
  public static String clusterState = "{'gettingstarted':{" +
      "    'router':{'name':'compositeId'}," +
      "    'shards':{" +
      "      'shard1':{" +
      "        'range':'80000000-ffffffff'," +
      "        'replicas':{" +
      "          'r1':{" +
      "            'core':r1," +
      "            'base_url':'http://10.0.0.4:8983/solr'," +
      "            'node_name':'node1'," +
      "            'state':'active'," +
      "            'leader':'true'}," +
      "          'r2':{" +
      "            'core':r2," +
      "            'base_url':'http://10.0.0.4:7574/solr'," +
      "            'node_name':'node2'," +
      "            'state':'active'}}}," +
      "      'shard2':{" +
      "        'range':'0-7fffffff'," +
      "        'replicas':{" +
      "          'r3':{" +
      "            'core':r3," +
      "            'base_url':'http://10.0.0.4:8983/solr'," +
      "            'node_name':'node1'," +
      "            'state':'active'," +
      "            'leader':'true'}," +
      "          'r4':{" +
      "            'core':r4," +
      "            'base_url':'http://10.0.0.4:8987/solr'," +
      "            'node_name':'node4'," +
      "            'state':'active'}," +
      "          'r6':{" +
      "            'core':r6," +
      "            'base_url':'http://10.0.0.4:8989/solr'," +
      "            'node_name':'node3'," +
      "            'state':'active'}," +
      "          'r5':{" +
      "            'core':r5," +
      "            'base_url':'http://10.0.0.4:7574/solr'," +
      "            'node_name':'node1'," +
      "            'state':'active'}}}}}}";

  public void testRules() throws IOException {
    String rules = "{" +
        "cluster-policy:[" +
        "{nodeRole:'!overseer', strict:false}," +
        "{replica:'<1',node:node3}," +
        "{replica:'<2',node:'#ANY', shard:'#EACH'}]," +
        " cluster-preferences:[" +
        "{minimize:cores , precision:2}," +
        "{maximize:freedisk, precision:50}, " +
        "{minimize:heap, precision:1000}]}";

    Map<String, Map> nodeValues = (Map<String, Map>) Utils.fromJSONString("{" +
        "node1:{cores:12, freedisk: 334, heap:10480}," +
        "node2:{cores:4, freedisk: 749, heap:6873}," +
        "node3:{cores:7, freedisk: 262, heap:7834}," +
        "node4:{cores:8, freedisk: 375, heap:16900, nodeRole:overseer}" +
        "}");

    Policy policy = new Policy((Map<String, Object>) Utils.fromJSONString(rules));
    Policy.Session session;
    session = policy.createSession(getClusterDataProvider(nodeValues, clusterState));

    List<Row> l = session.getSorted();
    assertEquals("node1", l.get(0).node);
    assertEquals("node3", l.get(1).node);
    assertEquals("node4", l.get(2).node);
    assertEquals("node2", l.get(3).node);


    Map<String, List<Clause>> violations = session.getViolations();
    System.out.println(Utils.getDeepCopy(violations, 6));
    assertEquals(3, violations.size());
    List<Clause> v = violations.get("node4");
    assertNotNull(v);
    assertEquals(v.get(0).tag.name, "nodeRole");
    v = violations.get("node1");
    assertNotNull(v);
    assertEquals(v.get(0).replica.op, Operand.LESS_THAN);
    assertEquals(v.get(0).replica.val, 2);
    v = violations.get("node3");
    assertNotNull(v);
    assertEquals(v.get(0).replica.op, Operand.LESS_THAN);
    assertEquals(v.get(0).replica.val, 1);
    assertEquals(v.get(0).tag.val, "node3");
    Policy.Suggester suggester = session.getSuggester(ADDREPLICA)
        .hint(Hint.COLL, "gettingstarted")
        .hint(Hint.SHARD, "r1");
    Map operation = suggester.getOperation();
    assertEquals("node2", operation.get("node"));



  }

 /* public void testOtherTag(){
    String rules = "{" +
        "conditions:[" +
        "{nodeRole:'!overseer', strict:false}," +
        "{replica:'<1',node:node3}," +
        "{replica:'<2',node:'#ANY', shard:'#EACH'}," +
        "{replica:'<3',shard:'#EACH', rack:'#ANY' }" +
        "]," +
        " preferences:[" +
        "{minimize:cores , precision:2}," +
        "{maximize:freedisk, precision:50}, " +
        "{minimize:heap, precision:1000}]}";

    Map<String, Map> nodeValues = (Map<String, Map>) Utils.fromJSONString("{" +
        "node1:{cores:12, freedisk: 334, heap:10480, rack: rack4}," +
        "node2:{cores:4, freedisk: 749, heap:6873, rack: rack3}," +
        "node3:{cores:7, freedisk: 262, heap:7834, rack: rack2}," +
        "node4:{cores:8, freedisk: 375, heap:16900, nodeRole:overseer, rack: rack1}" +
        "}");
    Policy policy = new Policy((Map<String, Object>) Utils.fromJSONString(rules));
    Policy.Session session = policy.createSession(getClusterDataProvider(nodeValues, clusterState));

    Map op = session
        .getSuggester(ADDREPLICA)
        .hint(Hint.COLL, "newColl")
        .hint(Hint.SHARD, "s1").getOperation();
    assertNotNull(op);
  }*/


  private ClusterDataProvider getClusterDataProvider(final Map<String, Map> nodeValues, String  clusterState) {
    return new ClusterDataProvider() {
        @Override
        public Map<String, Object> getNodeValues(String node, Collection<String> tags) {
          Map<String, Object> result = new LinkedHashMap<>();
          tags.stream().forEach(s -> result.put(s, nodeValues.get(node).get(s)));
          return result;
        }

        @Override
        public Collection<String> getNodes() {
          return nodeValues.keySet();
        }

      @Override
      public String getPolicy(String coll) {
        return null;
      }

      @Override
        public Map<String, Map<String, List<Policy.ReplicaInfo>>> getReplicaInfo(String node, Collection<String> keys) {
          return getReplicaDetails(node, clusterState);
        }

      };
  }

  /*public void testMultiReplicaPlacement() {
    String autoScaleJson ="{" +
        "  'policies': {" +
        "    'default': {" +
        "      'conditions': [" +
        "        { 'nodeRole': '!overseer'}," +
        "        { 'replica': '<2', 'shard': '#EACH', node:'#ANY'}" +
        "      ]," +
        "      'preferences':[" +
        "      {'minimize': 'freedisk', 'precision':50}]" +
        "    }," +
        "    'policy1': {" +
        "      'conditions': [" +
        "        { replica: '<2', shard: '#ANY', node:'#ANY'}," +
        "        { replica: '<2', shard:'#EACH', rack: rack1}," +
        "        { replica: '1', sysprop.fs: ssd, shard: '#EACH'}" +
        "      ], preferences : [ {maximize: freedisk, precision:50}]" +
        "}}}";

    Map<String,Map> nodeValues = (Map<String, Map>) Utils.fromJSONString( "{" +
        "node1:{cores:12, freedisk: 334, heap:10480, rack:rack3}," +
        "node2:{cores:4, freedisk: 749, heap:6873, sysprop.fs : ssd, rack:rack1}," +
        "node3:{cores:7, freedisk: 262, heap:7834, rack:rack4}," +
        "node4:{cores:8, freedisk: 375, heap:16900, nodeRole:overseer, rack:rack2}" +
        "}");

    ClusterDataProvider dataProvider = new ClusterDataProvider() {
      @Override
      public Map<String, Object> getNodeValues(String node, Collection<String> keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        keys.stream().forEach(s -> result.put(s, nodeValues.get(node).get(s)));
        return result;
      }

      @Override
      public Map<String, Map<String, List<Policy.ReplicaInfo>>> getReplicaInfo(String node, Collection<String> keys) {
        return getReplicaDetails(node, clusterState);
      }

      @Override
      public Collection<String> getNodes() {
        return Arrays.asList("node1", "node2", "node3", "node4");
      }
    };
    Map<String, List<String>> locations = Policy.getReplicaLocations("newColl", (Map<String, Object>) Utils.fromJSONString(autoScaleJson),
        "policy1", dataProvider, Arrays.asList("shard1", "shard2"), 3);
    System.out.println(Utils.toJSONString(locations));


  }*/

  public static Map<String, Map<String, List<Policy.ReplicaInfo>>> getReplicaDetails(String node, String s) {
    ValidatingJsonMap m = ValidatingJsonMap
        .getDeepCopy((Map) Utils.fromJSONString(s), 6, true);
    Map<String, Map<String, List<Policy.ReplicaInfo>>> result = new LinkedHashMap<>();

    m.forEach((collName, o) -> {
      ValidatingJsonMap coll = (ValidatingJsonMap) o;
      coll.getMap("shards").forEach((shard, o1) -> {
        ValidatingJsonMap sh = (ValidatingJsonMap) o1;
        sh.getMap("replicas").forEach((replicaName, o2) -> {
          ValidatingJsonMap r = (ValidatingJsonMap) o2;
          String node_name = (String) r.get("node_name");
          if (!node_name.equals(node)) return;
          Map<String, List<Policy.ReplicaInfo>> shardVsReplicaStats = result.get(collName);
          if (shardVsReplicaStats == null) result.put(collName, shardVsReplicaStats = new HashMap<>());
          List<Policy.ReplicaInfo> replicaInfos = shardVsReplicaStats.get(shard);
          if (replicaInfos == null) shardVsReplicaStats.put(shard, replicaInfos = new ArrayList<>());
          replicaInfos.add(new Policy.ReplicaInfo(replicaName, new HashMap<>()));
        });
      });
    });
    return result;
  }


}
