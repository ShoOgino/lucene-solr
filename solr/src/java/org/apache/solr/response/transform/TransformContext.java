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
package org.apache.solr.response.transform;

import org.apache.lucene.search.Query;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.SolrIndexSearcher;

/**
 * Environment variables for the transformed documents
 * 
 * @version $Id: JSONResponseWriter.java 1065304 2011-01-30 15:10:15Z rmuir $
 * @since solr 4.0
 */
public class TransformContext
{
  public Query query;
  public boolean wantsScores = false;
  public DocIterator iterator;
  public SolrIndexSearcher searcher;
}
