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

package org.apache.solr.handler.admin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.PriorityQueue;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.SolrException;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.RequestHandlerUtils;
import org.apache.solr.request.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SolrQueryParser;
import org.apache.solr.util.NamedList;
import org.apache.solr.util.SimpleOrderedMap;

/**
 * This handler exposes the internal lucene index.  It is inspired by and 
 * modeled on Luke, the Lucene Index Browser by Andrzej Bialecki.
 *   http://www.getopt.org/luke/
 * <p>
 * NOTE: the response format is still likely to change.  It should be designed so
 * that it works nicely with an XSLT transformation.  Untill we have a nice
 * XSLT frontend for /admin, the format is still open to change.
 * </p>
 * 
 * For more documentation see:
 *  
 * 
 * @author ryan
 * @version $Id$
 * @since solr 1.2
 */
public class LukeRequestHandler extends RequestHandlerBase 
{
  private static Logger log = Logger.getLogger(LukeRequestHandler.class.getName());
  
  public static final String NUMTERMS = "numTerms";
  public static final String DOC_ID = "docID";
  public static final String ID = "id";
  public static final int DEFAULT_COUNT = 10;
  
  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception
  {
    RequestHandlerUtils.addExperimentalFormatWarning( rsp );
    
    IndexSchema schema = req.getSchema();
    SolrIndexSearcher searcher = req.getSearcher();
    IndexReader reader = searcher.getReader();
    SolrParams params = req.getParams();
        
    // Always show the core lucene info
    rsp.add("index", getIndexInfo(reader) );

    Integer docID = params.getInt( DOC_ID );
    if( docID == null && params.get( ID ) != null ) {
      // Look for somethign with a given solr ID
      SchemaField uniqueKey = schema.getUniqueKeyField();
      String v = uniqueKey.getType().toInternal( params.get(ID) );
      Term t = new Term( uniqueKey.getName(), v );
      docID = searcher.getFirstMatch( t );
      if( docID < 0 ) {
        throw new SolrException( 404, "Can't find document: "+params.get( ID ) );
      }
    }
        
    // Read the document from the index
    if( docID != null ) {
      Document doc = null;
      try {
        doc = reader.document( docID );
      }
      catch( Exception ex ) {}
      if( doc == null ) {
        throw new SolrException( 404, "Can't find document: "+docID );
      }
      
      SimpleOrderedMap<Object> info = getDocumentFieldsInfo( doc, docID, reader, schema );
      
      SimpleOrderedMap<Object> docinfo = new SimpleOrderedMap<Object>();
      docinfo.add( "docID", docID );
      docinfo.add( "lucene", info );
      docinfo.add( "solr", doc );
      rsp.add( "doc", docinfo );
    }
    else {
      // If no doc is given, show all fields and top terms
      int numTerms = params.getInt( NUMTERMS, DEFAULT_COUNT );
      Set<String> fields = null;
      if( params.get( SolrParams.FL ) != null ) {
        fields = new HashSet<String>();
        for( String f : params.getParams( SolrParams.FL ) ) {
          fields.add( f );
        }
      }
      rsp.add( "key", getFieldFlagsKey() );
      rsp.add( "fields", getIndexedFieldsInfo( searcher, fields, numTerms ) ) ;
    }
  }
  
  /**
   * @return a string representing a Fieldable's flags.  
   */
  private static String getFieldFlags( Fieldable f )
  {
    StringBuilder flags = new StringBuilder();
    flags.append( (f != null && f.isIndexed())                     ? 'I' : '-' );
    flags.append( (f != null && f.isTokenized())                   ? 'T' : '-' );
    flags.append( (f != null && f.isStored())                      ? 'S' : '-' );
    flags.append( (false)                                          ? 'M' : '-' ); // SchemaField Specific
    flags.append( (f != null && f.isTermVectorStored())            ? 'V' : '-' );
    flags.append( (f != null && f.isStoreOffsetWithTermVector())   ? 'o' : '-' );
    flags.append( (f != null && f.isStorePositionWithTermVector()) ? 'p' : '-' );
    flags.append( (f != null && f.getOmitNorms())                  ? 'O' : '-' );
    flags.append( (f != null && f.isLazy())                        ? 'L' : '-' );
    flags.append( (f != null && f.isBinary())                      ? 'B' : '-' );
    flags.append( (f != null && f.isCompressed())                  ? 'C' : '-' );
    flags.append( (false)                                          ? 'f' : '-' ); // SchemaField Specific
    flags.append( (false)                                          ? 'l' : '-' ); // SchemaField Specific
    return flags.toString();
  }
  
  /**
   * @return a string representing a SchemaField's flags.  
   */
  private static String getFieldFlags( SchemaField f )
  {
    FieldType t = (f==null) ? null : f.getType();
    
    // see: http://www.nabble.com/schema-field-properties-tf3437753.html#a9585549
    boolean lazy = false; // "lazy" is purely a property of reading fields
    boolean binary = false; // Currently not possible
    
    StringBuilder flags = new StringBuilder();
    flags.append( (f != null && f.indexed())             ? 'I' : '-' );
    flags.append( (t != null && t.isTokenized())         ? 'T' : '-' );
    flags.append( (f != null && f.stored())              ? 'S' : '-' );
    flags.append( (f != null && f.multiValued())         ? 'M' : '-' );
    flags.append( (f != null && f.storeTermVector() )    ? 'V' : '-' );
    flags.append( (f != null && f.storeTermOffsets() )   ? 'o' : '-' );
    flags.append( (f != null && f.storeTermPositions() ) ? 'p' : '-' );
    flags.append( (f != null && f.omitNorms())           ? 'O' : '-' );
    flags.append( (lazy)                                 ? 'L' : '-' );
    flags.append( (binary)                               ? 'B' : '-' );
    flags.append( (f != null && f.isCompressed())        ? 'C' : '-' );
    flags.append( (f != null && f.sortMissingFirst() )   ? 'f' : '-' );
    flags.append( (f != null && f.sortMissingLast() )    ? 'l' : '-' );
    return flags.toString();
  }
  
  /**
   * @return a key to what each character means
   */
  private static SimpleOrderedMap<String> getFieldFlagsKey()
  {
    SimpleOrderedMap<String> key = new SimpleOrderedMap<String>();
    key.add( "I", "Indexed" );                     
    key.add( "T", "Tokenized" );                   
    key.add( "S", "Stored" );                   
    key.add( "M", "Multivalued" );                     
    key.add( "V", "TermVector Stored" );            
    key.add( "o", "Store Offset With TermVector" );   
    key.add( "p", "Store Position With TermVector" ); 
    key.add( "O", "Omit Norms" );                  
    key.add( "L", "Lazy" );                        
    key.add( "B", "Binary" );                      
    key.add( "C", "Compressed" );                  
    key.add( "f", "Sort Missing First" );                  
    key.add( "l", "Sort Missing Last" );                  
    return key;
  }
  
  private static SimpleOrderedMap<Object> getDocumentFieldsInfo( Document doc, int docID, IndexReader reader, IndexSchema schema ) throws IOException
  { 
    SimpleOrderedMap<Object> finfo = new SimpleOrderedMap<Object>();
    for( Object o : doc.getFields() ) {
      Fieldable fieldable = (Fieldable)o;
      SimpleOrderedMap<Object> f = new SimpleOrderedMap<Object>();
      
      SchemaField sfield = schema.getFieldOrNull( fieldable.name() );
      FieldType ftype = (sfield==null)?null:sfield.getType();

      f.add( "type", (ftype==null)?null:ftype.getTypeName() );
      f.add( "schema", getFieldFlags( sfield ) );
      f.add( "flags", getFieldFlags( fieldable ) );
      
      Term t = new Term( fieldable.name(), fieldable.stringValue() );
      f.add( "value", (ftype==null)?null:ftype.toExternal( fieldable ) );
      f.add( "internal", fieldable.stringValue() );  // may be a binary number
      f.add( "boost", fieldable.getBoost() );
      
      // TODO? how can this ever be 0?!  it is in the document!
      int freq = reader.docFreq( t );
      if( freq > 0 ) {
        f.add( "docFreq", reader.docFreq( t ) ); 
      }
      else {
        f.add( "docFreq", "zero! How can that be?" ); 
      }
      
      // If we have a term vector, return that
      if( fieldable.isTermVectorStored() ) {
        try {
          TermFreqVector v = reader.getTermFreqVector( docID, fieldable.name() );
          if( v != null ) {
            SimpleOrderedMap<Integer> tfv = new SimpleOrderedMap<Integer>();
            for( int i=0; i<v.size(); i++ ) {
              tfv.add( v.getTerms()[i], v.getTermFrequencies()[i] );
            }
            f.add( "termVector", tfv );
          }
        }
        catch( Exception ex ) {
          log.log( Level.WARNING, "error writing term vector", ex );
        }
      }
      
      finfo.add( fieldable.name(), f );
    }
    return finfo;
  }

  @SuppressWarnings("unchecked")
  private static SimpleOrderedMap<Object> getIndexedFieldsInfo( 
    final SolrIndexSearcher searcher, final Set<String> fields, final int numTerms ) 
    throws Exception
  { 
    Query matchAllDocs = new MatchAllDocsQuery();
    SolrQueryParser qp = searcher.getSchema().getSolrQueryParser(null);

    int filterCacheSize = SolrConfig.config.getInt( "query/filterCache/@size", -1 );
    IndexReader reader = searcher.getReader();
    IndexSchema schema = searcher.getSchema();
    
    // Walk the term enum and keep a priority quey for each map in our set
    Map<String,TopTermQueue> ttinfo = getTopTerms(reader, fields, numTerms, null );
    SimpleOrderedMap<Object> finfo = new SimpleOrderedMap<Object>();
    Collection<String> fieldNames = reader.getFieldNames(IndexReader.FieldOption.ALL);
    for (String fieldName : fieldNames) {
      if( fields != null && !fields.contains( fieldName ) ) {
        continue; // if a field is specified, only return one
      }
      
      SimpleOrderedMap<Object> f = new SimpleOrderedMap<Object>();
      
      SchemaField sfield = schema.getFieldOrNull( fieldName );
      FieldType ftype = (sfield==null)?null:sfield.getType();

      f.add( "type", (ftype==null)?null:ftype.getTypeName() );
      f.add( "schema", getFieldFlags( sfield ) );
      
      Query q = qp.parse( fieldName+":[* TO *]" ); 
      int docCount = searcher.numDocs( q, matchAllDocs );
// TODO?  Is there a way to get the Fieldable infomation for this field?
// The following approach works fine for stored fields, but does not work for non-stored fields
//      if( docCount > 0 ) {
//        // Find a document with this field
//        DocList ds = searcher.getDocList( q, (Query)null, (Sort)null, 0, 1 );
//        try {
//          Document doc = searcher.doc( ds.iterator().next() );
//          Fieldable fld = doc.getFieldable( fieldName );
//          f.add( "index", getFieldFlags( fld ) );
//        }
//        catch( Exception ex ) {
//          log.warning( "error reading field: "+fieldName );
//        }
//        // Find one document so we can get the fieldable
//      }
      f.add( "docs", docCount );
      
      TopTermQueue topTerms = ttinfo.get( fieldName );
      if( topTerms != null ) {
        f.add( "distinct", topTerms.distinctTerms );
        
        // TODO? is this the correct logic?
        f.add( "cacheableFaceting", topTerms.distinctTerms < filterCacheSize );
        
        // Only show them if we specify something
        f.add( "topTerms", topTerms.toNamedList( searcher.getSchema() ) );
      }
      
      // Add the field
      finfo.add( fieldName, f );
    }
    return finfo;
  }
    
  
  private static SimpleOrderedMap<Object> getIndexInfo( IndexReader reader ) throws IOException
  {
    // Count the terms
    TermEnum te = reader.terms();
    int numTerms = 0;
    while (te.next()) {
      numTerms++;
    }
    
    Directory dir = reader.directory();
    SimpleOrderedMap<Object> indexInfo = new SimpleOrderedMap<Object>();
    indexInfo.add("numDocs", reader.numDocs());
    indexInfo.add("maxDoc", reader.maxDoc());
    indexInfo.add("numTerms", numTerms );
    indexInfo.add("version", reader.getVersion());  // TODO? Is this different then: IndexReader.getCurrentVersion( dir )?
    indexInfo.add("optimized", reader.isOptimized() );
    indexInfo.add("current", reader.isCurrent() );
    indexInfo.add("hasDeletions", reader.hasDeletions() );
    indexInfo.add("directory", dir );
    indexInfo.add("lastModified", new Date(IndexReader.lastModified(dir)) );
    return indexInfo;
  }
  
  //////////////////////// SolrInfoMBeans methods //////////////////////

  @Override
  public String getDescription() {
    return "Lucene Index Browser.  Inspired and modeled after Luke: http://www.getopt.org/luke/";
  }

  @Override
  public String getVersion() {
    return "$Revision: 501512 $";
  }

  @Override
  public String getSourceId() {
    return "$Id: IndexInfoRequestHandler.java 487199 2006-12-14 13:03:40Z bdelacretaz $";
  }

  @Override
  public String getSource() {
    return "$URL: https://svn.apache.org/repos/asf/lucene/solr/trunk/src/java/org/apache/solr/request/IndexInfoRequestHandler.java $";
  }

  @Override
  public URL[] getDocs() {
    try {
      return new URL[] { new URL("http://wiki.apache.org/solr/LukeRequestHandler") };
    }
    catch( MalformedURLException ex ) { return null; }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  
  /**
   * Private internal class that counts up frequent terms
   */
  private static class TopTermQueue extends PriorityQueue 
  {
    static class TermInfo {
      TermInfo(Term t, int df) {
        term = t;
        docFreq = df;
      }
      int docFreq;
      Term term;
    }
    
    public int minFreq = 0;
    public int distinctTerms = 0;
    
    TopTermQueue(int size) {
      initialize(size);
    }
    
    @Override
    protected final boolean lessThan(Object a, Object b) {
      TermInfo termInfoA = (TermInfo)a;
      TermInfo termInfoB = (TermInfo)b;
      return termInfoA.docFreq < termInfoB.docFreq;
    }
    
    /**
     * This is a destructive call... the queue is empty at the end
     */
    public NamedList<Integer> toNamedList( IndexSchema schema )
    {
      // reverse the list..
      List<TermInfo> aslist = new LinkedList<TermInfo>();
      while( size() > 0 ) {
        aslist.add( 0, (TermInfo)pop() );
      }
      
      NamedList<Integer> list = new NamedList<Integer>();
      for (TermInfo i : aslist) {
        String txt = i.term.text();
        SchemaField ft = schema.getFieldOrNull( i.term.field() );
        if( ft != null ) {
          txt = ft.getType().indexedToReadable( txt );
        }
        list.add( txt, i.docFreq );
      }
      return list;
    }
  }

  private static Map<String,TopTermQueue> getTopTerms( IndexReader reader, Set<String> fields, int numTerms, Set<String> junkWords ) throws Exception 
  {
    Map<String,TopTermQueue> info = new HashMap<String, TopTermQueue>();
    TermEnum terms = reader.terms();
    
    while (terms.next()) {
      String field = terms.term().field();
      String t = terms.term().text();

      // Compute distinct terms for every field
      TopTermQueue tiq = info.get( field );
      if( tiq == null ) {
        tiq = new TopTermQueue( numTerms );
        info.put( field, tiq );
      }
      tiq.distinctTerms++;
      
      // Only save the distinct terms for fields we worry about
      if (fields != null && fields.size() > 0) {
        if( !fields.contains( field ) ) {
          continue;
        }
      }
      if( junkWords != null && junkWords.contains( t ) ) {
        continue;
      }
      
      if( terms.docFreq() > tiq.minFreq ) {
        tiq.put(new TopTermQueue.TermInfo(terms.term(), terms.docFreq()));
        if (tiq.size() >= numTerms) { // if tiq overfull
          tiq.pop(); // remove lowest in tiq
          tiq.minFreq = ((TopTermQueue.TermInfo)tiq.top()).docFreq; // reset minFreq
        }
      }
    }
    return info;
  }
}



