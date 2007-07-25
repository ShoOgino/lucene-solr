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

package org.apache.lucene.benchmark.byTask;

import java.io.StringReader;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import org.apache.lucene.benchmark.byTask.Benchmark;
import org.apache.lucene.benchmark.byTask.tasks.CountingSearchTestTask;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;

import junit.framework.TestCase;

/**
 * Test very simply that perf tasks - simple algorithms - are doing what they should.
 */
public class TestPerfTasksLogic extends TestCase {

  private static final boolean DEBUG = false;
  static final String NEW_LINE = System.getProperty("line.separator");
  
  // properties in effect in all tests here
  static final String propLines [] = {
    "directory=RAMDirectory",
    "print.props=false",
  };
  
  /**
   * @param name test name
   */
  public TestPerfTasksLogic(String name) {
    super(name);
  }

  /**
   * Test index creation logic
   */
  public void testIndexAndSearchTasks() throws Exception {
    // 1. alg definition (required in every "logic" test)
    String algLines[] = {
        "ResetSystemErase",
        "CreateIndex",
        "{ AddDoc } : 1000",
        "Optimize",
        "CloseIndex",
        "OpenReader",
        "{ CountingSearchTest } : 200",
        "CloseReader",
        "[ CountingSearchTest > : 70",
        "[ CountingSearchTest > : 9",
    };
    
    // 2. we test this value later
    CountingSearchTestTask.numSearches = 0;
    
    // 3. execute the algorithm  (required in every "logic" test)
    Benchmark benchmark = execBenchmark(algLines);

    // 4. test specific checks after the benchmark run completed.
    assertEquals("TestSearchTask was supposed to be called!",279,CountingSearchTestTask.numSearches);
    assertTrue("Index does not exist?...!", IndexReader.indexExists(benchmark.getRunData().getDirectory()));
    // now we should be able to open the index for write. 
    IndexWriter iw = new IndexWriter(benchmark.getRunData().getDirectory(),null,false);
    iw.close();
    IndexReader ir = IndexReader.open(benchmark.getRunData().getDirectory());
    assertEquals("1000 docs were added to the index, this is what we expect to find!",1000,ir.numDocs());
    ir.close();
  }

  /**
   * Test Exhasting Doc Maker logic
   */
  public void testExhaustDocMaker() throws Exception {
    // 1. alg definition (required in every "logic" test)
    String algLines[] = {
        "# ----- properties ",
        "doc.maker=org.apache.lucene.benchmark.byTask.feeds.SimpleDocMaker",
        "doc.add.log.step=1",
        "doc.term.vector=false",
        "doc.maker.forever=false",
        "directory=RAMDirectory",
        "doc.stored=false",
        "doc.tokenized=false",
        "# ----- alg ",
        "CreateIndex",
        "{ AddDoc } : * ",
        "Optimize",
        "CloseIndex",
        "OpenReader",
        "{ CountingSearchTest } : 100",
        "CloseReader",
        "[ CountingSearchTest > : 30",
        "[ CountingSearchTest > : 9",
    };
    
    // 2. we test this value later
    CountingSearchTestTask.numSearches = 0;
    
    // 3. execute the algorithm  (required in every "logic" test)
    Benchmark benchmark = execBenchmark(algLines);

    // 4. test specific checks after the benchmark run completed.
    assertEquals("TestSearchTask was supposed to be called!",139,CountingSearchTestTask.numSearches);
    assertTrue("Index does not exist?...!", IndexReader.indexExists(benchmark.getRunData().getDirectory()));
    // now we should be able to open the index for write. 
    IndexWriter iw = new IndexWriter(benchmark.getRunData().getDirectory(),null,false);
    iw.close();
    IndexReader ir = IndexReader.open(benchmark.getRunData().getDirectory());
    assertEquals("1 docs were added to the index, this is what we expect to find!",1,ir.numDocs());
    ir.close();
  }

  /**
   * Test Parallel Doc Maker logic (for LUCENE-940)
   */
  public void testParallelDocMaker() throws Exception {
    // 1. alg definition (required in every "logic" test)
    String algLines[] = {
        "# ----- properties ",
        "doc.maker=org.apache.lucene.benchmark.byTask.feeds.ReutersDocMaker",
        "doc.add.log.step=2697",
        "doc.term.vector=false",
        "doc.maker.forever=false",
        "directory=FSDirectory",
        "doc.stored=false",
        "doc.tokenized=false",
        "# ----- alg ",
        "CreateIndex",
        "[ { AddDoc } : * ] : 4 ",
        "CloseIndex",
    };
    
    // 2. execute the algorithm  (required in every "logic" test)
    Benchmark benchmark = execBenchmark(algLines);

    // 3. test number of docs in the index
    IndexReader ir = IndexReader.open(benchmark.getRunData().getDirectory());
    int ndocsExpected = 21578; // that's how many docs there are in the Reuters collecton.
    assertEquals("wrong number of docs in the index!", ndocsExpected, ir.numDocs());
    ir.close();
  }

  /**
   * Test WriteLineDoc and LineDocMaker.
   */
  public void testLineDocFile() throws Exception {
    File lineFile = new File(System.getProperty("tempDir"), "test.reuters.lines.txt");

    // We will call WriteLineDocs this many times
    final int NUM_TRY_DOCS = 500;

    // Creates a line file with first 500 docs from reuters
    String algLines1[] = {
      "# ----- properties ",
      "doc.maker=org.apache.lucene.benchmark.byTask.feeds.ReutersDocMaker",
      "doc.maker.forever=false",
      "line.file.out=" + lineFile.getAbsolutePath().replace('\\', '/'),
      "# ----- alg ",
      "{WriteLineDoc()}:" + NUM_TRY_DOCS,
    };

    // Run algo
    Benchmark benchmark = execBenchmark(algLines1);

    // Verify we got somewhere between 1-500 lines (some
    // Reuters docs have no body, which WriteLineDoc task
    // skips).
    BufferedReader r = new BufferedReader(new FileReader(lineFile));
    int numLines = 0;
    while(r.readLine() != null)
      numLines++;
    r.close();
    assertTrue("did not see the right number of docs; should be > 0 and <= " + NUM_TRY_DOCS + " but was " + numLines, numLines > 0 && numLines <= NUM_TRY_DOCS);
    
    // Index the line docs
    String algLines2[] = {
      "# ----- properties ",
      "analyzer=org.apache.lucene.analysis.SimpleAnalyzer",
      "doc.maker=org.apache.lucene.benchmark.byTask.feeds.LineDocMaker",
      "docs.file=" + lineFile.getAbsolutePath().replace('\\', '/'),
      "doc.maker.forever=false",
      "autocommit=false",
      "ram.flush.mb=4",
      "# ----- alg ",
      "ResetSystemErase",
      "CreateIndex",
      "{AddDoc}: *",
      "CloseIndex",
    };
    
    // Run algo
    benchmark = execBenchmark(algLines2);

    // now we should be able to open the index for write. 
    IndexWriter iw = new IndexWriter(benchmark.getRunData().getDirectory(),null,false);
    iw.close();

    IndexReader ir = IndexReader.open(benchmark.getRunData().getDirectory());
    assertEquals(numLines + " lines were were created but " + ir.numDocs() + " docs are in the index", numLines, ir.numDocs());
    ir.close();

    lineFile.delete();
  }
  
  // create the benchmark and execute it. 
  private Benchmark execBenchmark(String[] algLines) throws Exception {
    String algText = algLinesToText(algLines);
    logTstLogic(algText);
    Benchmark benchmark = new Benchmark(new StringReader(algText));
    benchmark.execute();
    return benchmark;
  }
  
  // catenate alg lines to make the alg text
  private String algLinesToText(String[] algLines) {
    String indent = "  ";
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < propLines.length; i++) {
      sb.append(indent).append(propLines[i]).append(NEW_LINE);
    }
    for (int i = 0; i < algLines.length; i++) {
      sb.append(indent).append(algLines[i]).append(NEW_LINE);
    }
    return sb.toString();
  }

  private void logTstLogic (String txt) {
    if (!DEBUG) 
      return;
    System.out.println("Test logic of:");
    System.out.println(txt);
  }

}
