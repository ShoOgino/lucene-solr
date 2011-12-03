package org.apache.lucene.index;

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

import org.apache.lucene.util.InfoStream;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.codecs.Codec;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.Collection;

public class TestSegmentMerger extends LuceneTestCase {
  //The variables for the new merged segment
  private Directory mergedDir;
  private String mergedSegment = "test";
  //First segment to be merged
  private Directory merge1Dir;
  private Document doc1 = new Document();
  private SegmentReader reader1 = null;
  //Second Segment to be merged
  private Directory merge2Dir;
  private Document doc2 = new Document();
  private SegmentReader reader2 = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mergedDir = newDirectory();
    merge1Dir = newDirectory();
    merge2Dir = newDirectory();
    DocHelper.setupDoc(doc1);
    SegmentInfo info1 = DocHelper.writeDoc(random, merge1Dir, doc1);
    DocHelper.setupDoc(doc2);
    SegmentInfo info2 = DocHelper.writeDoc(random, merge2Dir, doc2);
    reader1 = SegmentReader.get(true, info1, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR, newIOContext(random));
    reader2 = SegmentReader.get(true, info2, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR, newIOContext(random));
  }

  @Override
  public void tearDown() throws Exception {
    reader1.close();
    reader2.close();
    mergedDir.close();
    merge1Dir.close();
    merge2Dir.close();
    super.tearDown();
  }

  public void test() {
    assertTrue(mergedDir != null);
    assertTrue(merge1Dir != null);
    assertTrue(merge2Dir != null);
    assertTrue(reader1 != null);
    assertTrue(reader2 != null);
  }

  public void testMerge() throws IOException {
    final Codec codec = Codec.getDefault();
    SegmentMerger merger = new SegmentMerger(InfoStream.getDefault(), mergedDir, IndexWriterConfig.DEFAULT_TERM_INDEX_INTERVAL, mergedSegment, MergeState.CheckAbort.NONE, null, new FieldInfos(new FieldInfos.FieldNumberBiMap()), codec, newIOContext(random));
    merger.add(reader1);
    merger.add(reader2);
    MergeState mergeState = merger.merge();
    int docsMerged = mergeState.mergedDocCount;
    assertTrue(docsMerged == 2);
    final FieldInfos fieldInfos = mergeState.fieldInfos;
    //Should be able to open a new SegmentReader against the new directory
    SegmentReader mergedReader = SegmentReader.get(false, mergedDir, new SegmentInfo(mergedSegment, docsMerged, mergedDir, false,
                                                                                     codec, fieldInfos),
                                                   true, IndexReader.DEFAULT_TERMS_INDEX_DIVISOR, newIOContext(random));
    assertTrue(mergedReader != null);
    assertTrue(mergedReader.numDocs() == 2);
    Document newDoc1 = mergedReader.document(0);
    assertTrue(newDoc1 != null);
    //There are 2 unstored fields on the document
    assertTrue(DocHelper.numFields(newDoc1) == DocHelper.numFields(doc1) - DocHelper.unstored.size());
    Document newDoc2 = mergedReader.document(1);
    assertTrue(newDoc2 != null);
    assertTrue(DocHelper.numFields(newDoc2) == DocHelper.numFields(doc2) - DocHelper.unstored.size());

    DocsEnum termDocs = MultiFields.getTermDocsEnum(mergedReader,
                                                    MultiFields.getLiveDocs(mergedReader),
                                                    DocHelper.TEXT_FIELD_2_KEY,
                                                    new BytesRef("field"));
    assertTrue(termDocs != null);
    assertTrue(termDocs.nextDoc() != DocsEnum.NO_MORE_DOCS);

    Collection<String> stored = mergedReader.getFieldNames(IndexReader.FieldOption.INDEXED_WITH_TERMVECTOR);
    assertTrue(stored != null);
    //System.out.println("stored size: " + stored.size());
    assertTrue("We do not have 3 fields that were indexed with term vector",stored.size() == 3);

    Terms vector = mergedReader.getTermVectors(0).terms(DocHelper.TEXT_FIELD_2_KEY);
    assertNotNull(vector);
    assertEquals(3, vector.getUniqueTermCount());
    TermsEnum termsEnum = vector.iterator(null);

    int i = 0;
    while (termsEnum.next() != null) {
      String term = termsEnum.term().utf8ToString();
      int freq = (int) termsEnum.totalTermFreq();
      //System.out.println("Term: " + term + " Freq: " + freq);
      assertTrue(DocHelper.FIELD_2_TEXT.indexOf(term) != -1);
      assertTrue(DocHelper.FIELD_2_FREQS[i] == freq);
      i++;
    }

    TestSegmentReader.checkNorms(mergedReader);
    mergedReader.close();
  }
  
  // LUCENE-3143
  public void testInvalidFilesToCreateCompound() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig(TEST_VERSION_CURRENT, new MockAnalyzer(random));
    IndexWriter w = new IndexWriter(dir, iwc);
    
    // Create an index w/ .del file
    w.addDocument(new Document());
    Document doc = new Document();
    doc.add(new TextField("c", "test"));
    w.addDocument(doc);
    w.commit();
    w.deleteDocuments(new Term("c", "test"));
    w.close();
    
    // Assert that SM fails if .del exists
    SegmentMerger sm = new SegmentMerger(InfoStream.getDefault(), dir, 1, "a", MergeState.CheckAbort.NONE, null, null, Codec.getDefault(), newIOContext(random));
    boolean doFail = false;
    try {
      IndexWriter.createCompoundFile(dir, "b1", MergeState.CheckAbort.NONE, w.segmentInfos.info(0), newIOContext(random));
      doFail = true; // should never get here
    } catch (AssertionError e) {
      // expected
    }
    assertFalse("should not have been able to create a .cfs with .del and .s* files", doFail);
    
    dir.close();
  }

}
