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

package org.apache.solr.client.solrj.response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;

/**
 * 
 * @author ryan
 * @version $Id$
 * @since solr 1.3
 */
@SuppressWarnings("unchecked")
public class QueryResponse extends SolrResponseBase 
{
  // Direct pointers to known types
  private NamedList<Object> _header = null;
  private SolrDocumentList _results = null;
  private NamedList<Object> _facetInfo = null;
  private NamedList<Object> _debugInfo = null;
  private NamedList<Object> _highlightingInfo = null;

  // Facet stuff
  private Map<String,Integer> _facetQuery = null;
  private List<FacetField> _facetFields = null;
  private List<FacetField> _limitingFacets = null;
  
  // Highlight Info
  private Map<String,Map<String,List<String>>> _highlighting = null;
  
  // Debug Info
  private Map<String,Object> _debugMap = null;
  private Map<String,Integer> _docIdMap = null;
  private Map<String,String> _explainMap = null;

  public QueryResponse( NamedList<Object> res ) 
  {
    super( res );
    
    // Look for known things
    for( int i=0; i<res.size(); i++ ) {
      String n = res.getName( i );
      if( "responseHeader".equals( n ) ) {
        _header = (NamedList<Object>) res.getVal( i );
      }
      else if( "response".equals( n ) ) {
        _results = (SolrDocumentList) res.getVal( i );
      }
      else if( "facet_counts".equals( n ) ) {
        _facetInfo = (NamedList<Object>) res.getVal( i );
        extractFacetInfo( _facetInfo );
      }
      else if( "debug".equals( n ) ) {
        _debugInfo = (NamedList<Object>) res.getVal( i );
        extractDebugInfo( _debugInfo );
      }
      else if( "highlighting".equals( n ) ) {
        _highlightingInfo = (NamedList<Object>) res.getVal( i );
        extractHighlightingInfo( _highlightingInfo );
      }
    }
  }
  
  private static final String DKEY = ",internal_docid=";
  
  private void extractDebugInfo( NamedList<Object> debug )
  {
    _debugMap = new LinkedHashMap<String, Object>(); // keep the order
    for( Map.Entry<String, Object> info : debug ) {
      _debugMap.put( info.getKey(), info.getValue() );
    }

    // Parse out interisting bits from the debug info
    _explainMap = new HashMap<String, String>();
    _docIdMap = new HashMap<String, Integer>();
    NamedList<String> explain = (NamedList<String>)_debugMap.get( "explain" );
    if( explain != null ) {
      for( Map.Entry<String, String> info : explain ) {
        String key = info.getKey();
        int idx0 = key.indexOf( '=' )+1;
        int idx1 = info.getKey().indexOf( DKEY );
        int idx2 = idx1 + DKEY.length();

        String id = key.substring( idx0, idx1 );
        String docID = key.substring( idx2 );
        
        _explainMap.put( id, info.getValue() );
        _docIdMap.put( id, Integer.valueOf( docID ) );
      }
    }
  }

  private void extractHighlightingInfo( NamedList<Object> info )
  {
    _highlighting = new HashMap<String,Map<String,List<String>>>();
    for( Map.Entry<String, Object> doc : info ) {
      Map<String,List<String>> fieldMap = new HashMap<String, List<String>>();
      _highlighting.put( doc.getKey(), fieldMap );
      
      NamedList<List<String>> fnl = (NamedList<List<String>>)doc.getValue();
      for( Map.Entry<String, List<String>> field : fnl ) {
        fieldMap.put( field.getKey(), field.getValue() );
      }
    }
  }

  private void extractFacetInfo( NamedList<Object> info )
  {
    // Parse the queries
    _facetQuery = new HashMap<String, Integer>();
    NamedList<Integer> fq = (NamedList<Integer>) info.get( "facet_queries" );
    for( Map.Entry<String, Integer> entry : fq ) {
      _facetQuery.put( entry.getKey(), entry.getValue() );
    }
    
    // Parse the facet info into fields
    NamedList<NamedList<Integer>> ff = (NamedList<NamedList<Integer>>) info.get( "facet_fields" );
    if( ff != null ) {
      _facetFields = new ArrayList<FacetField>( ff.size() );
      _limitingFacets = new ArrayList<FacetField>( ff.size() );
      
      int minsize = _results.getNumFound();
      for( Map.Entry<String,NamedList<Integer>> facet : ff ) {
        FacetField f = new FacetField( facet.getKey() );
        for( Map.Entry<String, Integer> entry : facet.getValue() ) {
          f.add( entry.getKey(), entry.getValue() );
        }
        
        _facetFields.add( f );
        FacetField nl = f.getLimitingFields( minsize );
        if( nl.getValueCount() > 0 ) {
          _limitingFacets.add( nl );
        }
      }
    }
  }

  //------------------------------------------------------
  //------------------------------------------------------

  /**
   * Remove the field facet info
   */
  public void removeFacets() {
    _facetFields = new ArrayList<FacetField>();
  }
  
  //------------------------------------------------------
  //------------------------------------------------------

  public NamedList<Object> getHeader() {
    return _header;
  }

  public SolrDocumentList getResults() {
    return _results;
  }

  public Map<String, Object> getDebugMap() {
    return _debugMap;
  }

  public Map<String, Integer> getDocIdMap() {
    return _docIdMap;
  }

  public Map<String, String> getExplainMap() {
    return _explainMap;
  }

  public Map<String,Integer> getFacetQuery() {
    return _facetQuery;
  }

  public Map<String, Map<String, List<String>>> getHighlighting() {
    return _highlighting;
  }

  public List<FacetField> getFacetFields() {
    return _facetFields;
  }
  
  /** get 
   * 
   * @param name the name of the 
   * @return the FacetField by name or null if it does not exist
   */
  public FacetField getFacetField(String name) {
    if (_facetFields==null) return null;
    for (FacetField f : _facetFields) {
      if (f.getName().equals(name)) return f;
    }
    return null;
  }
  
  public List<FacetField> getLimitingFacets() {
    return _limitingFacets;
  }
}



