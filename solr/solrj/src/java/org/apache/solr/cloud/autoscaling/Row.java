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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.solr.common.IteratorWriter;
import org.apache.solr.common.MapWriter;
import org.apache.solr.common.util.Pair;
import org.apache.solr.common.util.Utils;
import org.apache.solr.cloud.autoscaling.Policy.ReplicaInfo;

import static org.apache.solr.common.params.CoreAdminParams.NODE;


class Row implements MapWriter {
  public final String node;
  final Cell[] cells;
  Map<String, Map<String, List<ReplicaInfo>>> replicaInfo;
  List<Clause> violations = new ArrayList<>();
  boolean anyValueMissing = false;

  Row(String node, List<String> params, ClusterDataProvider snitch) {
    replicaInfo = snitch.getReplicaInfo(node, params);
    if (replicaInfo == null) replicaInfo = new HashMap<>();
    this.node = node;
    cells = new Cell[params.size()];
    Map<String, Object> vals = snitch.getNodeValues(node, params);
    for (int i = 0; i < params.size(); i++) {
      String s = params.get(i);
      cells[i] = new Cell(i, s, vals.get(s));
      if (NODE.equals(s)) cells[i].val = node;
      if (cells[i].val == null) anyValueMissing = true;
    }
  }

  Row(String node, Cell[] cells, boolean anyValueMissing, Map<String, Map<String, List<ReplicaInfo>>> replicaInfo, List<Clause> violations) {
    this.node = node;
    this.cells = new Cell[cells.length];
    for (int i = 0; i < this.cells.length; i++) {
      this.cells[i] = cells[i].copy();

    }
    this.anyValueMissing = anyValueMissing;
    this.replicaInfo = replicaInfo;
    this.violations = violations;
  }

  @Override
  public void writeMap(EntryWriter ew) throws IOException {
    ew.put(node, (IteratorWriter) iw -> {
      iw.add((MapWriter) e -> e.put("replicas", replicaInfo));
      for (Cell cell : cells) iw.add(cell);
    });
  }

  Row copy() {
    return new Row(node, cells, anyValueMissing, Utils.getDeepCopy(replicaInfo, 3), new ArrayList<>(violations));
  }

  Object getVal(String name) {
    for (Cell cell : cells) if (cell.name.equals(name)) return cell.val;
    return null;
  }

  @Override
  public String toString() {
    return node;
  }

  Row addReplica(String coll, String shard) {
    Row row = copy();
    Map<String, List<ReplicaInfo>> c = row.replicaInfo.get(coll);
    if (c == null) row.replicaInfo.put(coll, c = new HashMap<>());
    List<ReplicaInfo> s = c.get(shard);
    if (s == null) c.put(shard, s = new ArrayList<>());
    s.add(new ReplicaInfo(""+new Random().nextInt(1000)+1000,coll,shard, new HashMap<>()));
    return row;

  }

  Pair<Row, ReplicaInfo> removeReplica(String coll, String shard) {
    Row row = copy();
    Map<String, List<ReplicaInfo>> c = row.replicaInfo.get(coll);
    if(c == null) return null;
    List<ReplicaInfo> s = c.get(shard);
    if (s == null || s.isEmpty()) return null;
    return new Pair(row,s.remove(0));

  }
}
