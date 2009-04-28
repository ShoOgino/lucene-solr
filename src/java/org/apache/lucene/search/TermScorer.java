package org.apache.lucene.search;

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

import org.apache.lucene.index.TermDocs;

/** Expert: A <code>Scorer</code> for documents matching a <code>Term</code>.
 */
final class TermScorer extends Scorer {
  
  private static final float[] SIM_NORM_DECODER = Similarity.getNormDecoder();
  
  private Weight weight;
  private TermDocs termDocs;
  private byte[] norms;
  private float weightValue;
  private int doc;

  private final int[] docs = new int[32];         // buffered doc numbers
  private final int[] freqs = new int[32];        // buffered term freqs
  private int pointer;
  private int pointerMax;

  private static final int SCORE_CACHE_SIZE = 32;
  private float[] scoreCache = new float[SCORE_CACHE_SIZE];

  /** Construct a <code>TermScorer</code>.
   * @param weight The weight of the <code>Term</code> in the query.
   * @param td An iterator over the documents matching the <code>Term</code>.
   * @param similarity The </code>Similarity</code> implementation to be used for score computations.
   * @param norms The field norms of the document fields for the <code>Term</code>.
   */
  TermScorer(Weight weight, TermDocs td, Similarity similarity,
             byte[] norms) {
    super(similarity);
    this.weight = weight;
    this.termDocs = td;
    this.norms = norms;
    this.weightValue = weight.getValue();

    for (int i = 0; i < SCORE_CACHE_SIZE; i++)
      scoreCache[i] = getSimilarity().tf(i) * weightValue;
  }

  /** @deprecated use {@link #score(Collector)} instead. */
  public void score(HitCollector hc) throws IOException {
    score(new HitCollectorWrapper(hc));
  }

  public void score(Collector c) throws IOException {
    next();
    score(c, Integer.MAX_VALUE);
  }

  /** @deprecated use {@link #score(Collector, int)} instead. */
  protected boolean score(HitCollector c, int end) throws IOException {
    return score(new HitCollectorWrapper(c), end);
  }
  
  protected boolean score(Collector c, int end) throws IOException {
    c.setScorer(this);
    while (doc < end) {                           // for docs in window
      c.collect(doc);                      // collect score
        
      if (++pointer >= pointerMax) {
        pointerMax = termDocs.read(docs, freqs);  // refill buffers
        if (pointerMax != 0) {
          pointer = 0;
        } else {
          termDocs.close();                       // close stream
          doc = Integer.MAX_VALUE;                // set to sentinel value
          return false;
        }
      } 
      doc = docs[pointer];
    }
    return true;
  }

  /** Returns the current document number matching the query.
   * Initially invalid, until {@link #next()} is called the first time.
   */
  public int doc() { return doc; }

  /** Advances to the next document matching the query.
   * <br>The iterator over the matching documents is buffered using
   * {@link TermDocs#read(int[],int[])}.
   * @return true iff there is another document matching the query.
   */
  public boolean next() throws IOException {
    pointer++;
    if (pointer >= pointerMax) {
      pointerMax = termDocs.read(docs, freqs);    // refill buffer
      if (pointerMax != 0) {
        pointer = 0;
      } else {
        termDocs.close();                         // close stream
        doc = Integer.MAX_VALUE;                  // set to sentinel value
        return false;
      }
    } 
    doc = docs[pointer];
    return true;
  }

  public float score() {
    int f = freqs[pointer];
    float raw =                                   // compute tf(f)*weight
      f < SCORE_CACHE_SIZE                        // check cache
      ? scoreCache[f]                             // cache hit
      : getSimilarity().tf(f)*weightValue;        // cache miss

    return norms == null ? raw : raw * SIM_NORM_DECODER[norms[doc] & 0xFF]; // normalize for field
  }

  /** Skips to the first match beyond the current whose document number is
   * greater than or equal to a given target. 
   * <br>The implementation uses {@link TermDocs#skipTo(int)}.
   * @param target The target document number.
   * @return true iff there is such a match.
   */
  public boolean skipTo(int target) throws IOException {
    // first scan in cache
    for (pointer++; pointer < pointerMax; pointer++) {
      if (docs[pointer] >= target) {
        doc = docs[pointer];
        return true;
      }
    }

    // not found in cache, seek underlying stream
    boolean result = termDocs.skipTo(target);
    if (result) {
      pointerMax = 1;
      pointer = 0;
      docs[pointer] = doc = termDocs.doc();
      freqs[pointer] = termDocs.freq();
    } else {
      doc = Integer.MAX_VALUE;
    }
    return result;
  }

  /** Returns an explanation of the score for a document.
   * <br>When this method is used, the {@link #next()} method
   * and the {@link #score(HitCollector)} method should not be used.
   * @param doc The document number for the explanation.
   */
  public Explanation explain(int doc) throws IOException {
    TermQuery query = (TermQuery)weight.getQuery();
    Explanation tfExplanation = new Explanation();
    int tf = 0;
    while (pointer < pointerMax) {
      if (docs[pointer] == doc)
        tf = freqs[pointer];
      pointer++;
    }
    if (tf == 0) {
        if (termDocs.skipTo(doc))
        {
            if (termDocs.doc() == doc)
            {
                tf = termDocs.freq();
            }
        }
    }
    termDocs.close();
    tfExplanation.setValue(getSimilarity().tf(tf));
    tfExplanation.setDescription("tf(termFreq("+query.getTerm()+")="+tf+")");
    
    return tfExplanation;
  }

  /** Returns a string representation of this <code>TermScorer</code>. */
  public String toString() { return "scorer(" + weight + ")"; }
}
