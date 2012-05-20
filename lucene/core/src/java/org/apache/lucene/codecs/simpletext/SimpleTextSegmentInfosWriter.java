package org.apache.lucene.codecs.simpletext;

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

import java.io.IOException;
import java.util.Map.Entry;
import java.util.Map;

import org.apache.lucene.codecs.SegmentInfosWriter;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.store.ChecksumIndexOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FlushInfo;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;

/**
 * writes plaintext segments files
 * <p>
 * <b><font color="red">FOR RECREATIONAL USE ONLY</font></B>
 * @lucene.experimental
 */
public class SimpleTextSegmentInfosWriter extends SegmentInfosWriter {

  final static BytesRef SI_VERSION          = new BytesRef("    version ");
  final static BytesRef SI_DOCCOUNT         = new BytesRef("    number of documents ");
  final static BytesRef SI_USECOMPOUND      = new BytesRef("    uses compound file ");
  final static BytesRef SI_NUM_DIAG         = new BytesRef("    diagnostics ");
  final static BytesRef SI_DIAG_KEY         = new BytesRef("      key ");
  final static BytesRef SI_DIAG_VALUE       = new BytesRef("      value ");
  
  @Override
  public void write(Directory dir, SegmentInfo si, FieldInfos fis, IOContext ioContext) throws IOException {
    assert si.getDelCount() <= si.docCount: "delCount=" + si.getDelCount() + " docCount=" + si.docCount + " segment=" + si.name;

    String fileName = IndexFileNames.segmentFileName(si.name, "", SimpleTextSegmentInfosFormat.SI_EXTENSION);
    boolean success = false;
    IndexOutput output = dir.createOutput(fileName,  ioContext);

    try {
      BytesRef scratch = new BytesRef();
    
      SimpleTextUtil.write(output, SI_VERSION);
      SimpleTextUtil.write(output, si.getVersion(), scratch);
      SimpleTextUtil.writeNewline(output);
    
      SimpleTextUtil.write(output, SI_DOCCOUNT);
      SimpleTextUtil.write(output, Integer.toString(si.docCount), scratch);
      SimpleTextUtil.writeNewline(output);
    
      SimpleTextUtil.write(output, SI_USECOMPOUND);
      SimpleTextUtil.write(output, Boolean.toString(si.getUseCompoundFile()), scratch);
      SimpleTextUtil.writeNewline(output);
    
      Map<String,String> diagnostics = si.getDiagnostics();
      int numDiagnostics = diagnostics == null ? 0 : diagnostics.size();
      SimpleTextUtil.write(output, SI_NUM_DIAG);
      SimpleTextUtil.write(output, Integer.toString(numDiagnostics), scratch);
      SimpleTextUtil.writeNewline(output);
    
      if (numDiagnostics > 0) {
        for (Map.Entry<String,String> diagEntry : diagnostics.entrySet()) {
          SimpleTextUtil.write(output, SI_DIAG_KEY);
          SimpleTextUtil.write(output, diagEntry.getKey(), scratch);
          SimpleTextUtil.writeNewline(output);
        
          SimpleTextUtil.write(output, SI_DIAG_VALUE);
          SimpleTextUtil.write(output, diagEntry.getValue(), scratch);
          SimpleTextUtil.writeNewline(output);
        }
      }
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(output);
      } else {
        output.close();
      }
    }
  }
}
