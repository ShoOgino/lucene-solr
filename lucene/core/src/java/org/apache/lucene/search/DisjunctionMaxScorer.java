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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.List;

/**
 * The Scorer for DisjunctionMaxQuery.  The union of all documents generated by the the subquery scorers
 * is generated in document number order.  The score for each document is the maximum of the scores computed
 * by the subquery scorers that generate that document, plus tieBreakerMultiplier times the sum of the scores
 * for the other subqueries that generate the document.
 */
final class DisjunctionMaxScorer extends DisjunctionScorer {
  /* Multiplier applied to non-maximum-scoring subqueries for a document as they are summed into the result. */
  private final float tieBreakerMultiplier;

  /**
   * Creates a new instance of DisjunctionMaxScorer
   * 
   * @param weight
   *          The Weight to be used.
   * @param tieBreakerMultiplier
   *          Multiplier applied to non-maximum-scoring subqueries for a
   *          document as they are summed into the result.
   * @param subScorers
   *          The sub scorers this Scorer should iterate on
   */
  DisjunctionMaxScorer(Weight weight, float tieBreakerMultiplier, List<Scorer> subScorers, boolean needsScores) {
    super(weight, subScorers, needsScores);
    this.tieBreakerMultiplier = tieBreakerMultiplier;
  }

  @Override
  protected float score(DisiWrapper topList) throws IOException {
    float scoreSum = 0;
    float scoreMax = 0;
    for (DisiWrapper w = topList; w != null; w = w.next) {
      final float subScore = w.scorer.score();
      scoreSum += subScore;
      if (subScore > scoreMax) {
        scoreMax = subScore;
      }
    }
    return scoreMax + (scoreSum - scoreMax) * tieBreakerMultiplier; 
  }
}
