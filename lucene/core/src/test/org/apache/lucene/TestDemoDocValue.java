package org.apache.lucene;

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

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.lucene42.Lucene42Codec;
import org.apache.lucene.document.BinaryDocValuesField;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.StoredDocument;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;

/**
 * A very simple demo used in the API documentation (src/java/overview.html).
 *
 * Please try to keep src/java/overview.html up-to-date when making changes
 * to this class.
 */
public class TestDemoDocValue extends LuceneTestCase {

  public void testDemoNumber() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv", 5));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
    }

    ireader.close();
    directory.close();
  }

  public void testDemoFloat() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new FloatDocValuesField("dv", 5.7f));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
      assertEquals(Float.floatToRawIntBits(5.7f), dv.get(hits.scoreDocs[i].doc));
    }

    ireader.close();
    directory.close();
  }
  
  public void testDemoTwoFieldsNumber() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 5));
    doc.add(new NumericDocValuesField("dv2", 17));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv1");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
      dv = ireader.leaves().get(0).reader().getNumericDocValues("dv2");
      assertEquals(17, dv.get(hits.scoreDocs[i].doc));
    }

    ireader.close();
    directory.close();
  }

  public void testDemoTwoFieldsMixed() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 5));
    doc.add(new BinaryDocValuesField("dv2", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv1");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv2 = ireader.leaves().get(0).reader().getBinaryDocValues("dv2");
      dv2.get(hits.scoreDocs[i].doc, scratch);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testDemoThreeFieldsMixed() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new SortedDocValuesField("dv1", new BytesRef("hello hello")));
    doc.add(new NumericDocValuesField("dv2", 5));
    doc.add(new BinaryDocValuesField("dv3", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv1");
      int ord = dv.getOrd(0);
      dv.lookupOrd(ord, scratch);
      assertEquals(new BytesRef("hello hello"), scratch);
      NumericDocValues dv2 = ireader.leaves().get(0).reader().getNumericDocValues("dv2");
      assertEquals(5, dv2.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv3 = ireader.leaves().get(0).reader().getBinaryDocValues("dv3");
      dv3.get(hits.scoreDocs[i].doc, scratch);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testDemoThreeFieldsMixed2() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv1", new BytesRef("hello world")));
    doc.add(new SortedDocValuesField("dv2", new BytesRef("hello hello")));
    doc.add(new NumericDocValuesField("dv3", 5));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv2");
      int ord = dv.getOrd(0);
      dv.lookupOrd(ord, scratch);
      assertEquals(new BytesRef("hello hello"), scratch);
      NumericDocValues dv2 = ireader.leaves().get(0).reader().getNumericDocValues("dv3");
      assertEquals(5, dv2.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv3 = ireader.leaves().get(0).reader().getBinaryDocValues("dv1");
      dv3.get(hits.scoreDocs[i].doc, scratch);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testTwoDocumentsNumeric() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("dv", 1));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new NumericDocValuesField("dv", 2));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    assertEquals(1, dv.get(0));
    assertEquals(2, dv.get(1));

    ireader.close();
    directory.close();
  }
  
  public void testTwoDocumentsMerged() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(newField("id", "0", StringField.TYPE_STORED));
    doc.add(new NumericDocValuesField("dv", -10));
    iwriter.addDocument(doc);
    iwriter.commit();
    doc = new Document();
    doc.add(newField("id", "1", StringField.TYPE_STORED));
    doc.add(new NumericDocValuesField("dv", 99));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    for(int i=0;i<2;i++) {
      StoredDocument doc2 = ireader.leaves().get(0).reader().document(i);
      long expected;
      if (doc2.get("id").equals("0")) {
        expected = -10;
      } else {
        expected = 99;
      }
      assertEquals(expected, dv.get(i));
    }

    ireader.close();
    directory.close();
  }

  public void testBigRange() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("dv", Long.MIN_VALUE));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new NumericDocValuesField("dv", Long.MAX_VALUE));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    assertEquals(Long.MIN_VALUE, dv.get(0));
    assertEquals(Long.MAX_VALUE, dv.get(1));

    ireader.close();
    directory.close();
  }
  
  public void testRange2() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new NumericDocValuesField("dv", -8841491950446638677L));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new NumericDocValuesField("dv", 9062230939892376225L));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv");
    assertEquals(-8841491950446638677L, dv.get(0));
    assertEquals(9062230939892376225L, dv.get(1));

    ireader.close();
    directory.close();
  }
  
  public void testDemoBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
      dv.get(hits.scoreDocs[i].doc, scratch);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testBytesTwoDocumentsMerged() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(newField("id", "0", StringField.TYPE_STORED));
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    iwriter.commit();
    doc = new Document();
    doc.add(newField("id", "1", StringField.TYPE_STORED));
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello 2")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = new BytesRef();
    for(int i=0;i<2;i++) {
      StoredDocument doc2 = ireader.leaves().get(0).reader().document(i);
      String expected;
      if (doc2.get("id").equals("0")) {
        expected = "hello world 1";
      } else {
        expected = "hello 2";
      }
      dv.get(i, scratch);
      assertEquals(expected, scratch.utf8ToString());
    }

    ireader.close();
    directory.close();
  }

  public void testDemoSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriter iwriter = new IndexWriter(directory, newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer));
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
      dv.lookupOrd(dv.getOrd(hits.scoreDocs[i].doc), scratch);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }

  public void testSortedBytesTwoDocuments() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.lookupOrd(dv.getOrd(0), scratch);
    assertEquals("hello world 1", scratch.utf8ToString());
    dv.lookupOrd(dv.getOrd(1), scratch);
    assertEquals("hello world 2", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testSortedBytesThreeDocuments() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    assertEquals(2, dv.getValueCount());
    BytesRef scratch = new BytesRef();
    assertEquals(0, dv.getOrd(0));
    dv.lookupOrd(0, scratch);
    assertEquals("hello world 1", scratch.utf8ToString());
    assertEquals(1, dv.getOrd(1));
    dv.lookupOrd(1, scratch);
    assertEquals("hello world 2", scratch.utf8ToString());
    assertEquals(0, dv.getOrd(2));

    ireader.close();
    directory.close();
  }

  public void testSortedBytesTwoDocumentsMerged() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(newField("id", "0", StringField.TYPE_STORED));
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 1")));
    iwriter.addDocument(doc);
    iwriter.commit();
    doc = new Document();
    doc.add(newField("id", "1", StringField.TYPE_STORED));
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    assertEquals(2, dv.getValueCount()); // 2 ords
    BytesRef scratch = new BytesRef();
    dv.lookupOrd(0, scratch);
    assertEquals(new BytesRef("hello world 1"), scratch);
    dv.lookupOrd(1, scratch);
    assertEquals(new BytesRef("hello world 2"), scratch);
    for(int i=0;i<2;i++) {
      StoredDocument doc2 = ireader.leaves().get(0).reader().document(i);
      String expected;
      if (doc2.get("id").equals("0")) {
        expected = "hello world 1";
      } else {
        expected = "hello world 2";
      }
      dv.lookupOrd(dv.getOrd(i), scratch);
      assertEquals(expected, scratch.utf8ToString());
    }

    ireader.close();
    directory.close();
  }

  public void testBytesWithNewline() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("hello\nworld\r1")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.get(0, scratch);
    assertEquals(new BytesRef("hello\nworld\r1"), scratch);

    ireader.close();
    directory.close();
  }

  public void testMissingSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("hello world 2")));
    iwriter.addDocument(doc);
    // 2nd doc missing the DV field
    iwriter.addDocument(new Document());
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.lookupOrd(dv.getOrd(0), scratch);
    assertEquals(new BytesRef("hello world 2"), scratch);
    dv.lookupOrd(dv.getOrd(1), scratch);
    assertEquals(new BytesRef(""), scratch);
    ireader.close();
    directory.close();
  }
  
  // nocommit: if we are going to pass down suffixes to segmentread/writestate,
  // then they should be respected by *all* codec apis!
  public void testDemoTwoFieldsTwoFormats() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    final DocValuesFormat fast = DocValuesFormat.forName("Lucene42");
    final DocValuesFormat slow = DocValuesFormat.forName("SimpleText");
    iwc.setCodec(new Lucene42Codec() {
      @Override
      public DocValuesFormat getDocValuesFormatForField(String field) {
        if ("dv1".equals(field)) {
          return fast;
        } else {
          return slow;
        }
      }
    });
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    String longTerm = "longtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongtermlongterm";
    String text = "This is the text to be indexed. " + longTerm;
    doc.add(newTextField("fieldname", text, Field.Store.YES));
    doc.add(new NumericDocValuesField("dv1", 5));
    doc.add(new BinaryDocValuesField("dv2", new BytesRef("hello world")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    IndexSearcher isearcher = new IndexSearcher(ireader);

    assertEquals(1, isearcher.search(new TermQuery(new Term("fieldname", longTerm)), 1).totalHits);
    Query query = new TermQuery(new Term("fieldname", "text"));
    TopDocs hits = isearcher.search(query, null, 1);
    assertEquals(1, hits.totalHits);
    BytesRef scratch = new BytesRef();
    // Iterate through the results:
    for (int i = 0; i < hits.scoreDocs.length; i++) {
      StoredDocument hitDoc = isearcher.doc(hits.scoreDocs[i].doc);
      assertEquals(text, hitDoc.get("fieldname"));
      assert ireader.leaves().size() == 1;
      NumericDocValues dv = ireader.leaves().get(0).reader().getNumericDocValues("dv1");
      assertEquals(5, dv.get(hits.scoreDocs[i].doc));
      BinaryDocValues dv2 = ireader.leaves().get(0).reader().getBinaryDocValues("dv2");
      dv2.get(hits.scoreDocs[i].doc, scratch);
      assertEquals(new BytesRef("hello world"), scratch);
    }

    ireader.close();
    directory.close();
  }
  
  public void testEmptySortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    SortedDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = new BytesRef();
    assertEquals(0, dv.getOrd(0));
    assertEquals(0, dv.getOrd(1));
    dv.lookupOrd(dv.getOrd(0), scratch);
    assertEquals("", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testEmptyBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("")));
    iwriter.addDocument(doc);
    iwriter.forceMerge(1);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.get(0, scratch);
    assertEquals("", scratch.utf8ToString());
    dv.get(1, scratch);
    assertEquals("", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testTooLargeBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    byte bytes[] = new byte[100000];
    BytesRef b = new BytesRef(bytes);
    random().nextBytes(bytes);
    doc.add(new BinaryDocValuesField("dv", b));
    try {
      iwriter.addDocument(doc);
      fail("did not get expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    iwriter.close();

    directory.close();
  }
  
  public void testTooLargeSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    byte bytes[] = new byte[100000];
    BytesRef b = new BytesRef(bytes);
    random().nextBytes(bytes);
    doc.add(new SortedDocValuesField("dv", b));
    try {
      iwriter.addDocument(doc);
      fail("did not get expected exception");
    } catch (IllegalArgumentException expected) {
      // expected
    }
    iwriter.close();
    directory.close();
  }
  
  public void testVeryLargeButLegalBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    byte bytes[] = new byte[32766];
    BytesRef b = new BytesRef(bytes);
    random().nextBytes(bytes);
    doc.add(new BinaryDocValuesField("dv", b));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.get(0, scratch);
    assertEquals(new BytesRef(bytes), scratch);

    ireader.close();
    directory.close();
  }
  
  public void testVeryLargeButLegalSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    byte bytes[] = new byte[32766];
    BytesRef b = new BytesRef(bytes);
    random().nextBytes(bytes);
    doc.add(new SortedDocValuesField("dv", b));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.get(0, scratch);
    assertEquals(new BytesRef(bytes), scratch);
    ireader.close();
    directory.close();
  }
  
  public void testCodecUsesOwnBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("boo!")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    byte mybytes[] = new byte[20];
    BytesRef scratch = new BytesRef(mybytes);
    dv.get(0, scratch);
    assertEquals("boo!", scratch.utf8ToString());
    assertFalse(scratch.bytes == mybytes);

    ireader.close();
    directory.close();
  }
  
  public void testCodecUsesOwnSortedBytes() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("boo!")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    byte mybytes[] = new byte[20];
    BytesRef scratch = new BytesRef(mybytes);
    dv.get(0, scratch);
    assertEquals("boo!", scratch.utf8ToString());
    assertFalse(scratch.bytes == mybytes);

    ireader.close();
    directory.close();
  }
  
  public void testCodecUsesOwnBytesEachTime() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("foo!")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryDocValuesField("dv", new BytesRef("bar!")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getBinaryDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.get(0, scratch);
    assertEquals("foo!", scratch.utf8ToString());
    
    BytesRef scratch2 = new BytesRef();
    dv.get(1, scratch2);
    assertEquals("bar!", scratch2.utf8ToString());
    // check scratch is still valid
    assertEquals("foo!", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  public void testCodecUsesOwnSortedBytesEachTime() throws IOException {
    Analyzer analyzer = new MockAnalyzer(random());

    Directory directory = newDirectory();
    // we don't use RandomIndexWriter because it might add more docvalues than we expect !!!!1
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, analyzer);
    iwc.setMergePolicy(newLogMergePolicy());
    IndexWriter iwriter = new IndexWriter(directory, iwc);
    Document doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("foo!")));
    iwriter.addDocument(doc);
    doc = new Document();
    doc.add(new SortedDocValuesField("dv", new BytesRef("bar!")));
    iwriter.addDocument(doc);
    iwriter.close();
    
    // Now search the index:
    IndexReader ireader = DirectoryReader.open(directory); // read-only=true
    assert ireader.leaves().size() == 1;
    BinaryDocValues dv = ireader.leaves().get(0).reader().getSortedDocValues("dv");
    BytesRef scratch = new BytesRef();
    dv.get(0, scratch);
    assertEquals("foo!", scratch.utf8ToString());
    
    BytesRef scratch2 = new BytesRef();
    dv.get(1, scratch2);
    assertEquals("bar!", scratch2.utf8ToString());
    // check scratch is still valid
    assertEquals("foo!", scratch.utf8ToString());

    ireader.close();
    directory.close();
  }
  
  // nocommit: test add twice
}
