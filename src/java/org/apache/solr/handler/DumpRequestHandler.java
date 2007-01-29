/**
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
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.solr.request.ContentStream;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryResponse;
import org.apache.solr.util.NamedList;

public class DumpRequestHandler extends RequestHandlerBase
{
  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws IOException 
  {
    // Show params
    rsp.add( "params", req.getParams().toNamedList() );
        
    // Write the streams...
    if( req.getContentStreams() != null ) {
      NamedList<Object> streams = new NamedList<Object>();
      // Cycle through each stream
      for( ContentStream content : req.getContentStreams() ) {
        NamedList<Object> stream = new NamedList<Object>();
        stream.add( "name", content.getName() );
        stream.add( "fieldName", content.getSourceInfo() );
        stream.add( "size", content.getSize() );
        stream.add( "contentType", content.getContentType() );
        stream.add( "stream", IOUtils.toString( content.getStream() ) );
        streams.add( "stream", stream );
      }
      rsp.add( "streams", streams );
    }

    // Show the context
    Map<Object,Object> context = req.getContext();
    if( context != null ) {
      NamedList ctx = new NamedList();
      for( Map.Entry<Object,Object> entry : context.entrySet() ) {
        ctx.add( entry.getKey().toString(), entry.getValue() );
      }
      rsp.add( "context", ctx );
    }
  }

  //////////////////////// SolrInfoMBeans methods //////////////////////

  @Override
  public String getDescription() {
    return "Dump handler (debug)";
  }

  @Override
  public String getVersion() {
      return "$Revision:$";
  }

  @Override
  public String getSourceId() {
    return "$Id:$";
  }

  @Override
  public String getSource() {
    return "$URL:$";
  }
}
