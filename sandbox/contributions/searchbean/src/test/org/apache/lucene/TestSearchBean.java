package org.apache.lucene;

/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" and
 *    "Apache Lucene" must not be used to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    "Apache Lucene", nor may "Apache" appear in their name, without
 *    prior written permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;

import org.apache.lucene.beans.SearchBean;
import org.apache.lucene.beans.HitsIterator;
import org.apache.lucene.beans.SortedField;

import junit.framework.TestCase;

import java.io.IOException;

/**
 *
 *
 */
public class TestSearchBean extends TestCase{
    public TestSearchBean(String name) {
        super(name);
    }
    
    /*
     *
     */
    public void testSearchBean() throws IOException, ParseException {
        Directory indexStore = createIndex();
        SortedField.addField("text",indexStore);
        //IndexSearcher searcher = new IndexSearcher(indexStore);
        
        SearchBean sb = new SearchBean(indexStore);
        HitsIterator hi = sb.search("metal");
        
        assertEquals(1, hi.getTotalHits());
        
        assertEquals(1, hi.getPageCount());
        
        assertEquals("metal",hi.next().get("text"));
    }
    
    public void testUnoptimizedSearchBean() throws IOException, ParseException {
        Directory indexStore = createIndex();
        IndexReader reader = IndexReader.open(indexStore);
        reader.delete(0);
        //
        reader.close();
        
        SortedField.addField("text",indexStore);
        //IndexSearcher searcher = new IndexSearcher(indexStore);
        
        SearchBean sb = new SearchBean(indexStore);
        HitsIterator hi = sb.search("metal");
        
        assertEquals(0, hi.getTotalHits());
        
        assertEquals(0, hi.getPageCount());
        
        //assertEquals("metal",hi.next().get("text"));
    }
    
    public Directory createIndex() throws IOException{
        RAMDirectory indexStore = new RAMDirectory();
        IndexWriter writer = new IndexWriter(indexStore, new StandardAnalyzer(), true);
        Document doc1 = new Document();
        Document doc2 = new Document();
        doc1.add(Field.Text("text", "metal"));
        doc2.add(Field.Text("text", "metals"));
        writer.addDocument(doc1);
        writer.addDocument(doc2);
        writer.optimize();
        writer.close();
        return (Directory) indexStore;
    }
}


