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

package org.apache.solr.client.solrj.cloud.autoscaling;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.solr.common.MapWriter;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.ZkStateReader;


public class ReplicaInfo implements MapWriter {
  final String name;
  final String core, collection, shard;
  final Map<String, Object> variables = new HashMap<>();

  public ReplicaInfo(String name, String coreName, String coll, String shard, Map<String, Object> vals) {
    this.name = name;
    this.core = coreName == null ? (String) vals.get("core") : coreName;
    if (vals != null) {
      this.variables.putAll(vals);
    }
    this.collection = coll;
    this.shard = shard;
  }

  @Override
  public void writeMap(EntryWriter ew) throws IOException {
    ew.put(name, (MapWriter) ew1 -> {
      for (Map.Entry<String, Object> e : variables.entrySet()) {
        ew1.put(e.getKey(), e.getValue());
      }
    });
  }

  public String getName() {
    return name;
  }

  public String getCore() {
    return core;
  }

  public String getCollection() {
    return collection;
  }

  public String getShard() {
    return shard;
  }

  public Replica.Type getType() {
    Object o = variables.get(ZkStateReader.REPLICA_TYPE);
    if (o == null) {
      variables.put(ZkStateReader.REPLICA_TYPE, Replica.Type.NRT);
      return Replica.Type.NRT;
    } else if (o instanceof Replica.Type) {
      return (Replica.Type)o;
    } else {
      Replica.Type type = Replica.Type.get(String.valueOf(o).toUpperCase(Locale.ROOT));
      variables.put(ZkStateReader.REPLICA_TYPE, type);
      return type;
    }
  }

  public Replica.State getState() {
    if (variables.get(ZkStateReader.STATE_PROP) != null) {
      return Replica.State.getState((String) variables.get(ZkStateReader.STATE_PROP));
    } else {
      // default to ACTIVE
      variables.put(ZkStateReader.STATE_PROP, Replica.State.ACTIVE.toString());
      return Replica.State.ACTIVE;
    }
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public Object getVariable(String name) {
    return variables.get(name);
  }

  @Override
  public String toString() {
    return "ReplicaInfo{" +
        "name='" + name + '\'' +
        ", collection='" + collection + '\'' +
        ", shard='" + shard + '\'' +
        ", variables=" + variables +
        '}';
  }
}
