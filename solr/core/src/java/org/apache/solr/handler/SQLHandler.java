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
package org.apache.solr.handler;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.comp.StreamComparator;
import org.apache.solr.client.solrj.io.stream.ExceptionStream;
import org.apache.solr.client.solrj.io.stream.StreamContext;
import org.apache.solr.client.solrj.io.stream.TupleStream;
import org.apache.solr.client.solrj.io.stream.expr.Explanation;
import org.apache.solr.client.solrj.io.stream.expr.StreamExplanation;
import org.apache.solr.client.solrj.io.stream.expr.StreamFactory;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.sql.CalciteSolrDriver;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLHandler extends RequestHandlerBase implements SolrCoreAware , PermissionNameProvider {

  private static String defaultZkhost = null;
  private static String defaultWorkerCollection = null;

  private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public void inform(SolrCore core) {

    CoreContainer coreContainer = core.getCoreDescriptor().getCoreContainer();

    if(coreContainer.isZooKeeperAware()) {
      defaultZkhost = core.getCoreDescriptor().getCoreContainer().getZkController().getZkServerAddress();
      defaultWorkerCollection = core.getCoreDescriptor().getCollectionName();
    }
  }

  @Override
  public PermissionNameProvider.Name getPermissionName(AuthorizationContext request) {
    return PermissionNameProvider.Name.READ_PERM;
  }

  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    ModifiableSolrParams params = new ModifiableSolrParams(req.getParams());
    String sql = params.get("stmt");
    // Set defaults for parameters
    params.set("numWorkers", params.getInt("numWorkers", 1));
    params.set("workerCollection", params.get("workerCollection", defaultWorkerCollection));
    params.set("workerZkhost", params.get("workerZkhost", defaultZkhost));
    params.set("aggregationMode", params.get("aggregationMode", "map_reduce"));
    // JDBC driver requires metadata from the SQLHandler. Default to false since this adds a new Metadata stream.
    params.set("includeMetadata", params.getBool("includeMetadata", false));

    TupleStream tupleStream = null;
    try {
      if(sql == null) {
        throw new Exception("stmt parameter cannot be null");
      }

      /*
       * Would be great to replace this with the JDBCStream. Can't do that currently since need to have metadata
       * added to the stream for the JDBC driver. This could be fixed by using the Calcite Avatica server and client.
       */
      tupleStream = new StreamHandler.TimerStream(new ExceptionStream(new SqlHandlerStream(sql, params)));

      rsp.add("result-set", tupleStream);
    } catch(Exception e) {
      //Catch the SQL parsing and query transformation exceptions.
      if(tupleStream != null) {
        tupleStream.close();
      }
      SolrException.log(logger, e);
      rsp.add("result-set", new StreamHandler.DummyErrorStream(e));
    }
  }

  public String getDescription() {
    return "SQLHandler";
  }

  public String getSource() {
    return null;
  }

  private class SqlHandlerStream extends TupleStream {
    private final String sql;
    private final SolrParams params;
    private boolean firstTuple = true;
    private Connection connection;
    private Statement statement;
    private ResultSet resultSet;
    private ResultSetMetaData resultSetMetaData;
    private int numColumns;

    SqlHandlerStream(String sql, SolrParams params) {
      this.sql = sql;
      this.params = params;
    }

    public List<TupleStream> children() {
      return Collections.emptyList();
    }

    public void open() throws IOException {
      Properties properties = new Properties();
      // Add all query parameters
      Iterator<String> parameterNamesIterator = params.getParameterNamesIterator();
      while(parameterNamesIterator.hasNext()) {
        String param = parameterNamesIterator.next();
        properties.setProperty(param, params.get(param));
      }

      // Set these last to ensure that they are set properly
      properties.setProperty("lex", "MYSQL");
      properties.setProperty("zk", defaultZkhost);

      try {
        Class.forName(CalciteSolrDriver.class.getCanonicalName());
      } catch (ClassNotFoundException e) {
        throw new IOException(e);
      }

      try {
        connection = DriverManager.getConnection("jdbc:calcitesolr:", properties);
        statement = connection.createStatement();
        resultSet = statement.executeQuery(sql);
        resultSetMetaData = this.resultSet.getMetaData();
        numColumns = resultSetMetaData.getColumnCount();
      } catch (SQLException e) {
        this.close();
        throw new IOException(e);
      }
    }

    @Override
    public Explanation toExplanation(StreamFactory factory) throws IOException {

      return new StreamExplanation(getStreamNodeId().toString())
          .withFunctionName("SQL Handler")
          .withExpression("--non-expressible--")
          .withImplementingClass(this.getClass().getName())
          .withExpressionType(Explanation.ExpressionType.STREAM_DECORATOR);
    }

    // Return a metadata tuple as the first tuple and then pass through to the underlying stream.
    public Tuple read() throws IOException {
      try {
        Map<String, Object> fields = new HashMap<>();
        if(firstTuple && params.getBool("includeMetadata")) {
          firstTuple = false;

          List<String> metadataFields = new ArrayList<>();
          Map<String, String> metadataAliases = new HashMap<>();
          for(int i = 1; i <= numColumns; i++) {
            String columnName = resultSetMetaData.getColumnName(i);
            String columnLabel = resultSetMetaData.getColumnLabel(i);
            metadataFields.add(columnName);
            metadataAliases.put(columnName, columnLabel);
          }

          fields.put("isMetadata", true);
          fields.put("fields", metadataFields);
          fields.put("aliases", metadataAliases);
        } else {
          if(this.resultSet.next()){
            for(int i = 1; i <= numColumns; i++) {
              fields.put(resultSetMetaData.getColumnName(i), this.resultSet.getObject(i));
            }
          } else {
            fields.put("EOF", true);
          }

        }
        return new Tuple(fields);
      } catch (SQLException e) {
        throw new IOException(e);
      }
    }

    public StreamComparator getStreamSort() {
      return null;
    }

    private void closeQuietly(AutoCloseable closeable) {
      if(closeable != null) {
        try {
          closeable.close();
        } catch (Exception ignore) {
        } finally {
          closeable = null;
        }
      }
    }

    public void close() throws IOException {
      this.closeQuietly(this.resultSet);
      this.closeQuietly(this.statement);
      this.closeQuietly(this.connection);
    }

    public void setStreamContext(StreamContext context) {

    }
  }
}
