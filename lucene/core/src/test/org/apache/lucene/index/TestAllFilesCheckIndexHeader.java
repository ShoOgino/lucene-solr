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
package org.apache.lucene.index;


import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.store.BaseDirectoryWrapper;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MockDirectoryWrapper;
import org.apache.lucene.util.LineFileDocs;
import org.apache.lucene.util.LuceneTestCase.SuppressFileSystems;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

/**
 * Test that a plain default detects broken index headers early (on opening a reader).
 */
@SuppressFileSystems("ExtrasFS")
public class TestAllFilesCheckIndexHeader extends LuceneTestCase {
  public void test() throws Exception {
    Directory dir = newDirectory();

    IndexWriterConfig conf = newIndexWriterConfig(new MockAnalyzer(random()));
    conf.setCodec(TestUtil.getDefaultCodec());

    // Disable CFS 80% of the time so we can truncate individual files, but the other 20% of the time we test truncation of .cfs/.cfe too:
    if (random().nextInt(5) != 1) {
      conf.setUseCompoundFile(false);
      conf.getMergePolicy().setNoCFSRatio(0.0);
    }

    RandomIndexWriter riw = new RandomIndexWriter(random(), dir, conf);
    // Use LineFileDocs so we (hopefully) get most Lucene features
    // tested, e.g. IntPoint was recently added to it:
    LineFileDocs docs = new LineFileDocs(random());
    for (int i = 0; i < 100; i++) {
      riw.addDocument(docs.nextDoc());
      if (random().nextInt(7) == 0) {
        riw.commit();
      }
      if (random().nextInt(20) == 0) {
        riw.deleteDocuments(new Term("docid", Integer.toString(i)));
      }
      if (random().nextInt(15) == 0) {
        riw.updateNumericDocValue(new Term("docid", Integer.toString(i)), "docid_intDV", Long.valueOf(i));
      }
    }

    if (TEST_NIGHTLY == false) {
      riw.forceMerge(1);
    }
    riw.close();
    checkIndexHeader(dir);
    dir.close();
  }
  
  private void checkIndexHeader(Directory dir) throws IOException {
    for(String name : dir.listAll()) {
      if (name.equals(IndexWriter.WRITE_LOCK_NAME) == false) {
        checkOneFile(dir, name);
      }
    }
  }
  
  private void checkOneFile(Directory dir, String victim) throws IOException {
    try (BaseDirectoryWrapper dirCopy = newDirectory()) {
      dirCopy.setCheckIndexOnClose(false);
      long victimLength = dir.fileLength(victim);
      int wrongBytes = TestUtil.nextInt(random(), 1, (int) Math.min(100, victimLength));
      assert victimLength > 0;

      if (VERBOSE) {
        System.out.println("TEST: now break file " + victim + " by randomizing first " + wrongBytes + " of " + victimLength);
      }

      for(String name : dir.listAll()) {
        if (name.equals(victim) == false) {
          dirCopy.copyFrom(dir, name, name, IOContext.DEFAULT);
        } else {

          // Iterate until our randomly generated bytes are indeed different from the first bytes of the file ... the vast majority of the
          // time this will only require one iteration!
          while (true) {
            try(IndexOutput out = dirCopy.createOutput(name, IOContext.DEFAULT);
                IndexInput in = dir.openInput(name, IOContext.DEFAULT)) {
              // keeps same file length, but replaces the first wrongBytes with random bytes:
              byte[] bytes = new byte[wrongBytes];
              random().nextBytes(bytes);
              out.writeBytes(bytes, 0, bytes.length);
              byte[] bytes2 = new byte[wrongBytes];
              in.readBytes(bytes2, 0, bytes2.length);
              if (Arrays.equals(bytes, bytes2) == false) {
                // We successfully randomly generated bytes that differ from the bytes in the file:
                out.copyBytes(in, victimLength - wrongBytes);
                break;
              }
            }
          }
        }
        dirCopy.sync(Collections.singleton(name));
      }

      try {
        // NOTE: we .close so that if the test fails (truncation not detected) we don't also get all these confusing errors about open files:
        DirectoryReader.open(dirCopy).close();
        fail("wrong bytes not detected after randomizing first " + wrongBytes + " bytes out of " + victimLength + " for file " + victim);
      } catch (CorruptIndexException | EOFException | IndexFormatTooOldException e) {
        // expected
      }

      // CheckIndex should also fail:
      try {
        TestUtil.checkIndex(dirCopy, true, true);
        fail("wrong bytes not detected after randomizing first " + wrongBytes + " bytes out of " + victimLength + " for file " + victim);
      } catch (CorruptIndexException | EOFException | IndexFormatTooOldException e) {
        // expected
      }
    }
  }
}
