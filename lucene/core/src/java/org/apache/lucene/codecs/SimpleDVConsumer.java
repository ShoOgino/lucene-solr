package org.apache.lucene.codecs;

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

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;

// prototype streaming DV api
public abstract class SimpleDVConsumer implements Closeable {
  // TODO: are any of these params too "infringing" on codec?
  // we want codec to get necessary stuff from IW, but trading off against merge complexity.

  // nocommit should we pass SegmentWriteState...?
  public abstract void addNumericField(FieldInfo field, Iterable<Number> values) throws IOException;    

  public abstract BinaryDocValuesConsumer addBinaryField(FieldInfo field, boolean fixedLength, int maxLength) throws IOException;
  // nocommit: figure out whats fair here.
  public abstract SortedDocValuesConsumer addSortedField(FieldInfo field, int valueCount, boolean fixedLength, int maxLength) throws IOException;

  // dead simple impl: codec can optimize
  public void mergeNumericField(FieldInfo fieldInfo, final MergeState mergeState, final List<NumericDocValues> toMerge) throws IOException {

    addNumericField(fieldInfo,
                    new Iterable<Number>() {
                      @Override
                      public Iterator<Number> iterator() {
                        return new Iterator<Number>() {
                          int readerUpto = -1;
                          int docIDUpto;
                          long nextValue;
                          AtomicReader currentReader;
                          NumericDocValues currentValues;
                          Bits currentLiveDocs;
                          boolean nextIsSet;

                          @Override
                          public boolean hasNext() {
                            return nextIsSet || setNext();
                          }

                          @Override
                          public void remove() {
                            throw new UnsupportedOperationException();
                          }

                          @Override
                          public Number next() {
                            if (!hasNext()) {
                              throw new NoSuchElementException();
                            }
                            assert nextIsSet;
                            nextIsSet = false;
                            // nocommit make a mutable number
                            return nextValue;
                          }

                          private boolean setNext() {
                            while (true) {
                              if (readerUpto == toMerge.size()) {
                                return false;
                              }

                              if (currentReader == null || docIDUpto == currentReader.maxDoc()) {
                                readerUpto++;
                                if (readerUpto < toMerge.size()) {
                                  currentReader = mergeState.readers.get(readerUpto);
                                  currentValues = toMerge.get(readerUpto);
                                  currentLiveDocs = currentReader.getLiveDocs();
                                }
                                docIDUpto = 0;
                                continue;
                              }

                              if (currentLiveDocs == null || currentLiveDocs.get(docIDUpto)) {
                                nextIsSet = true;
                                nextValue = currentValues.get(docIDUpto);
                                docIDUpto++;
                                return true;
                              }

                              docIDUpto++;
                            }
                          }
                        };
                      }
                    });
  }
  
  // dead simple impl: codec can optimize
  public void mergeBinaryField(FieldInfo fieldInfo, MergeState mergeState, List<BinaryDocValues> toMerge) throws IOException {
    // first compute fixedLength and maxLength of live ones to be merged.
    // nocommit: messy, and can be simplified by using docValues.maxLength/fixedLength in many cases.
    boolean fixedLength = true;
    int maxLength = -1;
    BytesRef bytes = new BytesRef();
    for (int readerIDX=0;readerIDX<toMerge.size();readerIDX++) {
      AtomicReader reader = mergeState.readers.get(readerIDX);      
      int maxDoc = reader.maxDoc();
      Bits liveDocs = reader.getLiveDocs();
      BinaryDocValues values = toMerge.get(readerIDX);
      for (int i = 0; i < maxDoc; i++) {
        if (liveDocs == null || liveDocs.get(i)) {
          values.get(i, bytes);
          if (maxLength == -1) {
            maxLength = bytes.length;
          } else {
            fixedLength &= bytes.length == maxLength;
            maxLength = Math.max(bytes.length, maxLength);
          }
        }
        mergeState.checkAbort.work(300);
      }
    }
    // now we can merge
    assert maxLength >= 0; // could this happen (nothing to do?)
    BinaryDocValuesConsumer field = addBinaryField(fieldInfo, fixedLength, maxLength);
    field.merge(mergeState, toMerge);
  }

  public void mergeSortedField(FieldInfo fieldInfo, MergeState mergeState, List<SortedDocValues> toMerge) throws IOException {
    SortedDocValuesConsumer.Merger merger = new SortedDocValuesConsumer.Merger();
    merger.merge(mergeState, toMerge);
    SortedDocValuesConsumer consumer = addSortedField(fieldInfo, merger.numMergedTerms, merger.fixedLength >= 0, merger.maxLength);
    consumer.merge(mergeState, merger);
  }
}
