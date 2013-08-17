package org.apache.lucene.codecs.temp;

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
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMOutputStream;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.fst.Builder;
import org.apache.lucene.util.fst.FST;
import org.apache.lucene.util.fst.PositiveIntOutputs;
import org.apache.lucene.util.fst.Util;
import org.apache.lucene.codecs.BlockTermState;
import org.apache.lucene.codecs.TempPostingsWriterBase;
import org.apache.lucene.codecs.PostingsConsumer;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.TermsConsumer;
import org.apache.lucene.codecs.TermStats;
import org.apache.lucene.codecs.CodecUtil;

/** FST based term dict, only ords is hold in FST, 
 *  other metadata encoded into single byte block */

public class TempFSTOrdTermsWriter extends FieldsConsumer {
  static final String TERMS_INDEX_EXTENSION = "tix";
  static final String TERMS_BLOCK_EXTENSION = "tbk";
  static final String TERMS_CODEC_NAME = "FST_ORD_TERMS_DICT";
  public static final int TERMS_VERSION_START = 0;
  public static final int TERMS_VERSION_CURRENT = TERMS_VERSION_START;
  public static final int SKIP_INTERVAL = 8;
  //static final boolean TEST = false;
  
  final TempPostingsWriterBase postingsWriter;
  final FieldInfos fieldInfos;
  final List<FieldMetaData> fields = new ArrayList<FieldMetaData>();
  IndexOutput blockOut = null;
  IndexOutput indexOut = null;  // nocommit: hmm, do we really need two streams?

  public TempFSTOrdTermsWriter(SegmentWriteState state, TempPostingsWriterBase postingsWriter) throws IOException {
    final String termsIndexFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, TERMS_INDEX_EXTENSION);
    final String termsBlockFileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, TERMS_BLOCK_EXTENSION);

    this.postingsWriter = postingsWriter;
    this.fieldInfos = state.fieldInfos;

    boolean success = false;
    try {
      this.indexOut = state.directory.createOutput(termsIndexFileName, state.context);
      this.blockOut = state.directory.createOutput(termsBlockFileName, state.context);
      writeHeader(indexOut);
      writeHeader(blockOut);
      this.postingsWriter.init(blockOut); 
      success = true;
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(indexOut, blockOut);
      }
    }
  }

  @Override
  public TermsConsumer addField(FieldInfo field) throws IOException {
    return new TermsWriter(field);
  }

  @Override
  public void close() throws IOException {
    IOException ioe = null;
    try {
      final long indexDirStart = indexOut.getFilePointer();
      final long blockDirStart = blockOut.getFilePointer();

      // write field summary
      blockOut.writeVInt(fields.size());
      for (FieldMetaData field : fields) {
        blockOut.writeVInt(field.fieldInfo.number);
        blockOut.writeVLong(field.numTerms);
        if (field.fieldInfo.getIndexOptions() != IndexOptions.DOCS_ONLY) {
          blockOut.writeVLong(field.sumTotalTermFreq);
        }
        blockOut.writeVLong(field.sumDocFreq);
        blockOut.writeVInt(field.docCount);
        blockOut.writeVInt(field.longsSize);
        blockOut.writeVLong(field.statsOut.getFilePointer());
        blockOut.writeVLong(field.metaLongsOut.getFilePointer());
        blockOut.writeVLong(field.metaBytesOut.getFilePointer());

        field.skipOut.writeTo(blockOut);
        field.statsOut.writeTo(blockOut);
        field.metaLongsOut.writeTo(blockOut);
        field.metaBytesOut.writeTo(blockOut);
        field.dict.save(indexOut);
      }
      writeTrailer(indexOut, indexDirStart);
      writeTrailer(blockOut, blockDirStart);
    } catch (IOException ioe2) {
      ioe = ioe2;
    } finally {
      IOUtils.closeWhileHandlingException(ioe, blockOut, indexOut, postingsWriter);
    }
  }

  private void writeHeader(IndexOutput out) throws IOException {
    CodecUtil.writeHeader(out, TERMS_CODEC_NAME, TERMS_VERSION_CURRENT);   
  }
  private void writeTrailer(IndexOutput out, long dirStart) throws IOException {
    out.writeLong(dirStart);
  }

  // nocommit: nuke this? we don't need to buffer so much data, 
  // since close() can do this naturally
  private static class FieldMetaData {
    public FieldInfo fieldInfo;
    public long numTerms;
    public long sumTotalTermFreq;
    public long sumDocFreq;
    public int docCount;
    public int longsSize;
    public FST<Long> dict;

    // nocommit: block encode each part 
    // (so that we'll have metaLongsOut[])
    public RAMOutputStream skipOut;       // vint encode next skip point (all values start from 0, fully decoded when reading)
    public RAMOutputStream statsOut;      // vint encode df, (ttf-df)
    public RAMOutputStream metaLongsOut;  // vint encode monotonic long[] and length for corresponding byte[]
    public RAMOutputStream metaBytesOut;  // put all bytes blob here
  }

  final class TermsWriter extends TermsConsumer {
    private final Builder<Long> builder;
    private final PositiveIntOutputs outputs;
    private final FieldInfo fieldInfo;
    private final int longsSize;
    private long numTerms;

    private final IntsRef scratchTerm = new IntsRef();
    private final RAMOutputStream statsOut = new RAMOutputStream();
    private final RAMOutputStream metaLongsOut = new RAMOutputStream();
    private final RAMOutputStream metaBytesOut = new RAMOutputStream();

    private final RAMOutputStream skipOut = new RAMOutputStream();
    private long lastBlockStatsFP;
    private long lastBlockMetaLongsFP;
    private long lastBlockMetaBytesFP;
    private long[] lastBlockLongs;

    private long[] lastLongs;
    private long lastMetaBytesFP;

    TermsWriter(FieldInfo fieldInfo) {
      this.numTerms = 0;
      this.fieldInfo = fieldInfo;
      this.longsSize = postingsWriter.setField(fieldInfo);
      this.outputs = PositiveIntOutputs.getSingleton();
      this.builder = new Builder<Long>(FST.INPUT_TYPE.BYTE1, outputs);

      this.lastBlockStatsFP = 0;
      this.lastBlockMetaLongsFP = 0;
      this.lastBlockMetaBytesFP = 0;
      this.lastBlockLongs = new long[longsSize];

      this.lastLongs = new long[longsSize];
      this.lastMetaBytesFP = 0;
    }

    @Override
    public Comparator<BytesRef> getComparator() {
      return BytesRef.getUTF8SortedAsUnicodeComparator();
    }

    @Override
    public PostingsConsumer startTerm(BytesRef text) throws IOException {
      postingsWriter.startTerm();
      return postingsWriter;
    }

    @Override
    public void finishTerm(BytesRef text, TermStats stats) throws IOException {
      if (numTerms > 0 && numTerms % SKIP_INTERVAL == 0) {
        bufferSkip();
      }
      // write term meta data into fst
      final long longs[] = new long[longsSize];
      final long delta = stats.totalTermFreq - stats.docFreq;
      if (stats.totalTermFreq > 0) {
        if (delta == 0) {
          statsOut.writeVInt(stats.docFreq<<1|1);
        } else {
          statsOut.writeVInt(stats.docFreq<<1|0);
          statsOut.writeVLong(stats.totalTermFreq-stats.docFreq);
        }
      } else {
        statsOut.writeVInt(stats.docFreq);
      }
      BlockTermState state = postingsWriter.newTermState();
      state.docFreq = stats.docFreq;
      state.totalTermFreq = stats.totalTermFreq;
      postingsWriter.finishTerm(state);
      postingsWriter.encodeTerm(longs, metaBytesOut, fieldInfo, state, false);
      for (int i = 0; i < longsSize; i++) {
        metaLongsOut.writeVLong(longs[i]);
        lastLongs[i] += longs[i];
      }
      metaLongsOut.writeVLong(metaBytesOut.getFilePointer() - lastMetaBytesFP);

      builder.add(Util.toIntsRef(text, scratchTerm), numTerms);
      numTerms++;

      lastMetaBytesFP = metaBytesOut.getFilePointer();
    }

    @Override
    public void finish(long sumTotalTermFreq, long sumDocFreq, int docCount) throws IOException {
      if (numTerms > 0) {
        final FieldMetaData metadata = new FieldMetaData();
        metadata.fieldInfo = fieldInfo;
        metadata.numTerms = numTerms;
        metadata.sumTotalTermFreq = sumTotalTermFreq;
        metadata.sumDocFreq = sumDocFreq;
        metadata.docCount = docCount;
        metadata.longsSize = longsSize;
        metadata.skipOut = skipOut;
        metadata.statsOut = statsOut;
        metadata.metaLongsOut = metaLongsOut;
        metadata.metaBytesOut = metaBytesOut;
        metadata.dict = builder.finish();
        fields.add(metadata);
      }
    }

    private void bufferSkip() throws IOException {
      skipOut.writeVLong(statsOut.getFilePointer() - lastBlockStatsFP);
      skipOut.writeVLong(metaLongsOut.getFilePointer() - lastBlockMetaLongsFP);
      skipOut.writeVLong(metaBytesOut.getFilePointer() - lastBlockMetaBytesFP);
      for (int i = 0; i < longsSize; i++) {
        skipOut.writeVLong(lastLongs[i] - lastBlockLongs[i]);
      }
      lastBlockStatsFP = statsOut.getFilePointer();
      lastBlockMetaLongsFP = metaLongsOut.getFilePointer();
      lastBlockMetaBytesFP = metaBytesOut.getFilePointer();
      System.arraycopy(lastLongs, 0, lastBlockLongs, 0, longsSize);
    }
  }
}
