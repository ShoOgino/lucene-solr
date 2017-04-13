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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.solr.common.MapWriter;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CollectionParams.CollectionAction;
import org.apache.solr.common.params.CoreAdminParams;
import org.apache.solr.common.util.Utils;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.solr.common.params.CollectionParams.CollectionAction.ADDREPLICA;
import static org.apache.solr.common.util.Utils.getDeepCopy;

public class Policy {
  public static final String EACH = "#EACH";
  public static final String ANY = "#ANY";
  List<Clause> clauses = new ArrayList<>();
  List<Preference> preferences = new ArrayList<>();
  List<String> params= new ArrayList<>();


  public Policy(Map<String, Object> jsonMap) {
    List<Map<String, Object>> l = getListOfMap("conditions", jsonMap);
    clauses = l.stream().map(Clause::new).collect(toList());
    l = getListOfMap("preferences", jsonMap);
    preferences = l.stream().map(Preference::new).collect(toList());
    for (int i = 0; i < preferences.size() - 1; i++) {
      Preference preference = preferences.get(i);
      preference.next = preferences.get(i + 1);
    }

    for (Clause c : clauses) params.add(c.tag.name);
    for (Preference preference : preferences) {
      if (params.contains(preference.name.name())) {
        throw new RuntimeException(preference.name + " is repeated");
      }
      params.add(preference.name.toString());
      preference.idx = params.size() - 1;
    }
  }


  public class Session implements MapWriter {
    final List<String> nodes;
    final ClusterDataProvider snitch;
    final List<Row> matrix;
    Set<String> collections = new HashSet<>();

    Session(List<String> nodes, ClusterDataProvider snitch, List<Row> matrix) {
      this.nodes = nodes;
      this.snitch = snitch;
      this.matrix = matrix;
    }

    Session(ClusterDataProvider snitch) {
      this.nodes = new ArrayList<>(snitch.getNodes());
      this.snitch = snitch;
      matrix = new ArrayList<>(nodes.size());
      for (String node : nodes) matrix.add(new Row(node, params, snitch));
      for (Row row : matrix) row.replicaInfo.forEach((s, e) -> collections.add(s));
    }

    Session copy() {
      return new Session(nodes, snitch, getMatrixCopy());
    }

    List<Row> getMatrixCopy() {
      return matrix.stream()
          .map(Row::copy)
          .collect(Collectors.toList());
    }

    Policy getPolicy() {
      return Policy.this;

    }

    /**Apply the preferences and conditions
     */
    public void applyRules() {
      if (!preferences.isEmpty()) {
        //this is to set the approximate value according to the precision
        ArrayList<Row> tmpMatrix = new ArrayList<>(matrix);
        for (Preference p : preferences) {
          Collections.sort(tmpMatrix, (r1, r2) -> p.compare(r1, r2, false));
          p.setApproxVal(tmpMatrix);
        }
        //approximate values are set now. Let's do recursive sorting
        Collections.sort(matrix, (r1, r2) -> preferences.get(0).compare(r1, r2, true));
      }

      if (!clauses.isEmpty()) {
        for (Clause clause : clauses) {
          for (Row row : matrix) {
            clause.test(row);
          }
        }
      }

    }

    public Map<String, List<Clause>> getViolations() {
      return matrix.stream()
          .filter(row -> !row.violations.isEmpty())
          .collect(Collectors.toMap(r -> r.node, r -> r.violations));
    }

    public Suggester getSuggester(CollectionAction action, String collection, String shard) {
      Suggester op = ops.get(action).get();
      if (op == null) throw new UnsupportedOperationException(action.toString() + "is not supported");
      return op.init(collection, shard, this);
    }

    @Override
    public void writeMap(EntryWriter ew) throws IOException {
      for (int i = 0; i < matrix.size(); i++) {
        Row row = matrix.get(i);
        ew.put(row.node, row);
      }
    }

    @Override
    public String toString() {
      return Utils.toJSONString(toMap(new LinkedHashMap<>()));
    }
    public List<Row> getSorted(){
      return Collections.unmodifiableList(matrix);
    }
  }


  public Session createSession(ClusterDataProvider snitch) {
    return new Session(snitch);
  }


  static List<Map<String, Object>> getListOfMap(String key, Map<String, Object> jsonMap) {
    Object o = jsonMap.get(key);
    if (o != null) {
      if (!(o instanceof List)) o = singletonList(o);
      return (List) o;
    } else {
      return Collections.emptyList();
    }
  }


  enum SortParam {
    replica, freedisk, cores, heap, cpu;

    static SortParam get(String m) {
      for (SortParam p : values()) if (p.name().equals(m)) return p;
      throw new RuntimeException( "Invalid sort "+ m+  " Sort must be on one of these " + Arrays.asList(values()));
    }

  }

  enum Sort {
    maximize(1), minimize(-1);
    final int sortval;

    Sort(int i) {
      sortval = i;
    }

    static Sort get(Map<String, Object> m) {
      if (m.containsKey(maximize.name()) && m.containsKey(minimize.name())) {
        throw new RuntimeException("Cannot have both 'maximize' and 'minimize'");
      }
      if (m.containsKey(maximize.name())) return maximize;
      if (m.containsKey(minimize.name())) return minimize;
      throw new RuntimeException("must have either 'maximize' or 'minimize'");
    }
  }


  public static class ReplicaInfo implements MapWriter {
    final String name;
    Map<String, Object> variables;

    public ReplicaInfo(String name, Map<String, Object> vals) {
      this.name = name;
      this.variables = vals;
    }

    @Override
    public void writeMap(EntryWriter ew) throws IOException {
      ew.put(name, variables);
    }
  }


  public static abstract class Suggester {
    String coll;
    String shard;
    Policy.Session session;
    List<Row> matrix;

    Map operation;

    Suggester init(String coll, String shard, Policy.Session session) {
      this.coll = coll;
      this.shard = shard;
      this.session = session.copy();
      matrix = session.getMatrixCopy();
      this.operation = init();
      return this;
    }

    abstract Map init();


    public Map getOperation() {
      return operation;
    }

    public Session getSession() {
      return session;
    }
  }

  public static Map<String, List<String>> getReplicaLocations(String collName, Map<String, Object> autoScalingJson,
                                                              String policyName, ClusterDataProvider cdp,
                                                              List<String> shardNames,
                                                              int repFactor) {
    Map<String, List<String>> positionMapping = new HashMap<>();
    for (String shardName : shardNames) positionMapping.put(shardName, new ArrayList<>(repFactor));
    Map policyJson = (Map) Utils.getObjectByPath(autoScalingJson, false, asList("policies", policyName));
    if (policyJson == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "no such policy exists " + policyName);
    }
    Map defaultPolicy = (Map) Utils.getObjectByPath(autoScalingJson, false, asList("policies", "default"));

    Policy policy = new Policy(Policy.mergePolicies(collName, policyJson, defaultPolicy));
    Policy.Session session = policy.createSession(cdp);
    for (String shardName : shardNames) {
      for (int i = 0; i < repFactor; i++) {
        Policy.Suggester suggester = session.getSuggester(ADDREPLICA, collName, shardName);
        Map op = suggester.getOperation();
        if (op == null) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "No node can satisfy the rules");
        }
        session = suggester.getSession();
        positionMapping.get(shardName).add((String) op.get(CoreAdminParams.NODE));
      }
    }

    return positionMapping;
  }

  public static Map<String, Object> mergePolicies(String coll,
                                                  Map<String, Object> collPolicy,
                                                  Map<String, Object> defaultPolicy) {
    Collection<Map<String, Object>> conditions = getDeepCopy(getListOfMap("conditions", collPolicy), 4, true);
    insertColl(coll, conditions);
    List<Clause> parsedConditions = conditions.stream().map(Clause::new).collect(toList());
    Collection<Map<String, Object>> preferences = getDeepCopy(getListOfMap("preferences", collPolicy), 4, true);
    List<Preference> parsedPreferences = preferences.stream().map(Preference::new).collect(toList());
    if (defaultPolicy != null) {
      Collection<Map<String, Object>> defaultConditions = getDeepCopy(getListOfMap("conditions", defaultPolicy), 4, true);
      insertColl(coll,defaultConditions);
      defaultConditions.forEach(e -> {
        Clause clause = new Clause(e);
        for (Clause c : parsedConditions) {
          if (c.collection.equals(clause.collection) &&
              c.tag.name.equals(clause.tag.name) ) return;
        }
        conditions.add(e);
      });
      Collection<Map<String,Object>> defaultPreferences = getDeepCopy(getListOfMap("preferences", defaultPolicy), 4, true);
      defaultPreferences.forEach(e -> {
        Preference preference = new Preference(e);
        for (Preference p : parsedPreferences) {
          if(p.name == preference.name) return;
        }
        preferences.add(e);

      });
  }
    return Utils.makeMap("conditions", conditions, "preferences", preferences );

  }

  private static Collection<Map<String, Object>> insertColl(String coll, Collection<Map<String, Object>> conditions) {
    conditions.forEach(e -> {
      if (!e.containsKey("collection")) e.put("collection", coll);
    });
    return conditions;
  }

  private static final Map<CollectionAction, Supplier<Suggester>> ops = new HashMap<>();

  static {
    ops.put(CollectionAction.ADDREPLICA, () -> new AddReplicaSuggester());
    ops.put(CollectionAction.MOVEREPLICA, () -> new MoveReplicaSuggester());
  }



}
