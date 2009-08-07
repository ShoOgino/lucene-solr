package org.apache.lucene.search.payloads;
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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.QueryWeight;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Similarity;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.spans.NearSpansOrdered;
import org.apache.lucene.search.spans.NearSpansUnordered;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanScorer;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * The BoostingNearQuery is very similar to the {@link org.apache.lucene.search.spans.SpanNearQuery} except
 * that it factors in the value of the payloads located at each of the positions where the
 * {@link org.apache.lucene.search.spans.TermSpans} occurs.
 * <p/>
 * In order to take advantage of this, you must override {@link org.apache.lucene.search.Similarity#scorePayload(String, byte[],int,int)}
 * which returns 1 by default.
 * <p/>
 * Payload scores are averaged across term occurrences in the document.
 *
 * @see org.apache.lucene.search.Similarity#scorePayload(String, byte[], int, int)
 */

public class BoostingNearQuery extends SpanNearQuery implements PayloadQuery {
  protected String fieldName;
  protected PayloadFunction function;

  public BoostingNearQuery(SpanQuery[] clauses, int slop, boolean inOrder) {
    this(clauses, slop, inOrder, new AveragePayloadFunction());
  }

  public BoostingNearQuery(SpanQuery[] clauses, int slop, boolean inOrder, PayloadFunction function) {
    super(clauses, slop, inOrder);
    fieldName = clauses[0].getField(); // all clauses must have same field
    this.function = function;
  }


  public QueryWeight createQueryWeight(Searcher searcher) throws IOException {
    return new BoostingSpanWeight(this, searcher);
  }

  public class BoostingSpanWeight extends SpanWeight {
    public BoostingSpanWeight(SpanQuery query, Searcher searcher) throws IOException {
      super(query, searcher);
    }

    public Scorer scorer(IndexReader reader) throws IOException {
      return new BoostingSpanScorer(query.getSpans(reader), this,
              similarity,
              reader.norms(query.getField()));
    }

    public Scorer scorer(IndexReader reader, boolean scoreDocsInOrder, boolean topScorer) throws IOException {
      return new BoostingSpanScorer(query.getSpans(reader), this,
              similarity,
              reader.norms(query.getField()));
    }
  }

  public class BoostingSpanScorer extends SpanScorer {
    Spans spans;
    Spans[] subSpans = null;
    protected float payloadScore;
    private int payloadsSeen;
    Similarity similarity = getSimilarity();

    protected BoostingSpanScorer(Spans spans, Weight weight, Similarity similarity, byte[] norms)
            throws IOException {
      super(spans, weight, similarity, norms);
      this.spans = spans;
    }

    // Get the payloads associated with all underlying subspans
    public void getPayloads(Spans[] subSpans) throws IOException {
      for (int i = 0; i < subSpans.length; i++) {
        if (subSpans[i] instanceof NearSpansOrdered) {
          if (((NearSpansOrdered) subSpans[i]).isPayloadAvailable()) {
            processPayloads(((NearSpansOrdered) subSpans[i]).getPayload());
          }
          getPayloads(((NearSpansOrdered) subSpans[i]).getSubSpans());
        } else if (subSpans[i] instanceof NearSpansUnordered) {
          if (((NearSpansUnordered) subSpans[i]).isPayloadAvailable()) {
            processPayloads(((NearSpansUnordered) subSpans[i]).getPayload());
          }
          getPayloads(((NearSpansUnordered) subSpans[i]).getSubSpans());
        }
      }
    }

    /**
     * By default, sums the payloads, but can be overridden to do other things.
     *
     * @param payLoads The payloads
     */
    protected void processPayloads(Collection payLoads) {
      for (Iterator iterator = payLoads.iterator(); iterator.hasNext();) {
        byte[] thePayload = (byte[]) iterator.next();
        payloadScore = function.currentScore(doc, fieldName, payloadsSeen, payloadScore,
                similarity.scorePayload(doc, fieldName, thePayload, 0, thePayload.length));
        ++payloadsSeen;
      }
    }

    //
    protected boolean setFreqCurrentDoc() throws IOException {
      Spans[] spansArr = new Spans[1];
      spansArr[0] = spans;
      payloadScore = 0;
      payloadsSeen = 0;
      getPayloads(spansArr);
      return super.setFreqCurrentDoc();
    }

    public float score() throws IOException {

      return super.score() * function.docScore(doc, fieldName, payloadsSeen, payloadScore);
    }

    public Explanation explain(int doc) throws IOException {
      Explanation result = new Explanation();
      Explanation nonPayloadExpl = super.explain(doc);
      result.addDetail(nonPayloadExpl);
      Explanation payloadBoost = new Explanation();
      result.addDetail(payloadBoost);
      float avgPayloadScore = (payloadsSeen > 0 ? (payloadScore / payloadsSeen) : 1);
      payloadBoost.setValue(avgPayloadScore);
      payloadBoost.setDescription("scorePayload(...)");
      result.setValue(nonPayloadExpl.getValue() * avgPayloadScore);
      result.setDescription("bnq, product of:");
      return result;
    }
  }


}
