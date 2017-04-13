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

package org.apache.solr.recipe;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.util.Utils;
import org.apache.solr.common.util.ValidatingJsonMap;

import static org.apache.solr.common.params.CollectionParams.CollectionAction.ADDREPLICA;
import static org.apache.solr.common.util.Utils.getObjectByPath;

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
        "  'policies': {" +
        "    'default': {" +
        "      'conditions': [" +
        "        {  replica:'#ANY' , 'nodeRole': '!overseer'}," +
        "        { 'replica': '<2', 'shard': '#EACH', node:'#ANY'}" +
        "      ]," +
        "      'preferences':[" +
        "        {'maximize': 'freedisk', 'precision':50}," +
        "      {'minimize': 'replica', 'precision':50}]" +
        "    }," +
        "    'policy1': {" +
        "      'conditions': [" +
        "        { 'replica': '1', 'sysprop.fs': 'ssd', 'shard': '#EACH'}," +
        "        { 'replica': '<2', 'shard': '#ANY' , node: '#ANY'}," +
        "        { 'replica': '<2', 'shard':'#EACH', 'rack': 'rack1' }" +
        "      ], preferences: [{maximize:freedisk, precision:75}]} } }");
    map = (Map) map.get("policies");
    map = Policy.mergePolicies("mycoll", (Map<String,Object>)map.get("policy1"),(Map<String,Object>)map.get("default"));
    assertEquals(((List)map.get("conditions")).size(), 4 );
    assertEquals(((List) map.get("preferences")).size(), 2);
    assertEquals(String.valueOf(getObjectByPath(map, true, "conditions[0]/replica")),"1");
    assertEquals(String.valueOf(getObjectByPath(map, true, "conditions[1]/replica")),"<2");
    assertEquals(String.valueOf(getObjectByPath(map, true, "conditions[1]/shard")),"#ANY");
    assertEquals(String.valueOf(getObjectByPath(map, true, "conditions[2]/rack")),"rack1");
    assertEquals(String.valueOf(getObjectByPath(map, true, "conditions[3]/nodeRole")),"!overseer");

    assertEquals(String.valueOf(getObjectByPath(map, true, "preferences[0]/maximize")),"freedisk");
    assertEquals(String.valueOf(getObjectByPath(map, true, "preferences[0]/precision")),"75");
    assertEquals(String.valueOf(getObjectByPath(map, true, "preferences[1]/precision")),"50");
  }

  public void testRules() throws IOException {
    String rules = "{" +
        "conditions:[{nodeRole:'!overseer', strict:false},{replica:'<1',node:node3}," +
        "{replica:'<2',node:'#ANY', shard:'#EACH'}]," +
        " preferences:[" +
        "{minimize:cores , precision:2}," +
        "{maximize:freedisk, precision:50}, " +
        "{minimize:heap, precision:1000}]}";


    Map<String, Map> nodeValues = (Map<String, Map>) Utils.fromJSONString("{" +
        "node1:{cores:12, freedisk: 334, heap:10480}," +
        "node2:{cores:4, freedisk: 749, heap:6873}," +
        "node3:{cores:7, freedisk: 262, heap:7834}," +
        "node4:{cores:8, freedisk: 375, heap:16900, nodeRole:overseer}" +
        "}");


    ValidatingJsonMap m = ValidatingJsonMap
        .getDeepCopy((Map) Utils.fromJSONString(clusterState), 6, true);


    Policy policy = new Policy((Map<String, Object>) Utils.fromJSONString(rules));
    Policy.Session session;
    ClusterDataProvider snitch = new ClusterDataProvider() {
      @Override
      public Map<String, Object> getNodeValues(String node, Collection<String> keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        keys.stream().forEach(s -> result.put(s, nodeValues.get(node).get(s)));
        return result;
      }

      @Override
      public Collection<String> getNodes() {
        return Arrays.asList("node1", "node2", "node3", "node4");
      }

      @Override
      public Map<String, Map<String, List<Policy.ReplicaInfo>>> getReplicaInfo(String node, Collection<String> keys) {
        return getReplicaDetails(node, m);
      }

    };

    session = policy.createSession(snitch);

    session.applyRules();
    List<Row> l = session.getSorted();
    assertEquals("node1", l.get(0).node);
    assertEquals("node3", l.get(1).node);
    assertEquals("node4", l.get(2).node);
    assertEquals("node2", l.get(3).node);


    System.out.printf(Utils.toJSONString(Utils.getDeepCopy(session.toMap(new LinkedHashMap<>()), 8)));
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

    Policy.Suggester suggester = session.getSuggester(ADDREPLICA, "gettingstarted", "r1");
    Map operation = suggester.getOperation();
    assertEquals("node2", operation.get("node"));
    System.out.println(Utils.toJSONString(operation));


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

  public static Map<String, Map<String, List<Policy.ReplicaInfo>>> getReplicaDetails(String node, ValidatingJsonMap m) {
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
