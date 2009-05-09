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

import org.apache.lucene.index.IndexReader;
/* Description from Doug Cutting (excerpted from
 * LUCENE-1483):
 *
 * BooleanScorer uses a ~16k array to score windows of
 * docs. So it scores docs 0-16k first, then docs 16-32k,
 * etc. For each window it iterates through all query terms
 * and accumulates a score in table[doc%16k]. It also stores
 * in the table a bitmask representing which terms
 * contributed to the score. Non-zero scores are chained in
 * a linked list. At the end of scoring each window it then
 * iterates through the linked list and, if the bitmask
 * matches the boolean constraints, collects a hit. For
 * boolean queries with lots of frequent terms this can be
 * much faster, since it does not need to update a priority
 * queue for each posting, instead performing constant-time
 * operations per posting. The only downside is that it
 * results in hits being delivered out-of-order within the
 * window, which means it cannot be nested within other
 * scorers. But it works well as a top-level scorer.
 *
 * The new BooleanScorer2 implementation instead works by
 * merging priority queues of postings, albeit with some
 * clever tricks. For example, a pure conjunction (all terms
 * required) does not require a priority queue. Instead it
 * sorts the posting streams at the start, then repeatedly
 * skips the first to to the last. If the first ever equals
 * the last, then there's a hit. When some terms are
 * required and some terms are optional, the conjunction can
 * be evaluated first, then the optional terms can all skip
 * to the match and be added to the score. Thus the
 * conjunction can reduce the number of priority queue
 * updates for the optional terms. */

final class BooleanScorer extends Scorer {
  private SubScorer scorers = null;
  private BucketTable bucketTable = new BucketTable();

  private int maxCoord = 1;
  private float[] coordFactors = null;

  private int requiredMask = 0;
  private int prohibitedMask = 0;
  private int nextMask = 1;

  private final int minNrShouldMatch;

  BooleanScorer(Similarity similarity) {
    this(similarity, 1);
  }
  
  BooleanScorer(Similarity similarity, int minNrShouldMatch) {
    super(similarity);
    this.minNrShouldMatch = minNrShouldMatch;
  }
  
  static final class SubScorer {
    public Scorer scorer;
    public boolean done;
    public boolean required = false;
    public boolean prohibited = false;
    public Collector collector;
    public SubScorer next;

    public SubScorer(Scorer scorer, boolean required, boolean prohibited,
        Collector collector, SubScorer next)
      throws IOException {
      this.scorer = scorer;
      this.done = !scorer.next();
      this.required = required;
      this.prohibited = prohibited;
      this.collector = collector;
      this.next = next;
    }
  }

  final void add(Scorer scorer, boolean required, boolean prohibited)
    throws IOException {
    int mask = 0;
    if (required || prohibited) {
      if (nextMask == 0) {
        throw new IndexOutOfBoundsException(
            "More than 32 required/prohibited clauses in query.");
      }
      mask = nextMask;
      nextMask = nextMask << 1;
    }

    if (!prohibited) {
      maxCoord++;
      if (required) {
        requiredMask |= mask;                       // update required mask
      }
    } else {
      // prohibited
      prohibitedMask |= mask;                     // update prohibited mask
    }

    scorers = new SubScorer(scorer, required, prohibited,
                            bucketTable.newCollector(mask), scorers);
  }

  private final void computeCoordFactors() {
    coordFactors = new float[maxCoord];
    Similarity sim = getSimilarity();
    for (int i = 0; i < maxCoord; i++) {
      coordFactors[i] = sim.coord(i, maxCoord - 1); 
    }
  }

  private int end;
  private Bucket current;

  /** @deprecated use {@link #score(Collector)} instead. */
  public void score(HitCollector hc) throws IOException {
    score(new HitCollectorWrapper(hc));
  }
  
  public void score(Collector collector) throws IOException {
    next();
    score(collector, Integer.MAX_VALUE);
  }

  /** @deprecated use {@link #score(Collector, int)} instead. */
  protected boolean score(HitCollector hc, int max) throws IOException {
    return score(new HitCollectorWrapper(hc), max);
  }

  protected boolean score(Collector collector, int max) throws IOException {
    if (coordFactors == null) {
      computeCoordFactors();
    }
    boolean more;
    Bucket tmp;
    
    BucketScorer bs = new BucketScorer();
    // The internal loop will set the score and doc before calling collect.
    collector.setScorer(bs);
    do {
      bucketTable.first = null;
      
      while (current != null) {         // more queued 

        // check prohibited & required
        if ((current.bits & prohibitedMask) == 0 && 
            (current.bits & requiredMask) == requiredMask) {
          
          if (current.doc >= max){
            tmp = current;
            current = current.next;
            tmp.next = bucketTable.first;
            bucketTable.first = tmp;
            continue;
          }
          
          if (current.coord >= minNrShouldMatch) {
            bs.score = current.score * coordFactors[current.coord];
            bs.doc = current.doc;
            collector.collect(current.doc);
          }
        }
        
        current = current.next;         // pop the queue
      }
      
      if (bucketTable.first != null){
        current = bucketTable.first;
        bucketTable.first = current.next;
        return true;
      }

      // refill the queue
      more = false;
      end += BucketTable.SIZE;
      for (SubScorer sub = scorers; sub != null; sub = sub.next) {
        if (!sub.done) {
          sub.done = !sub.scorer.score(sub.collector, end);
          if (!sub.done)
            more = true;
        }
      }
      current = bucketTable.first;
      
    } while (current != null || more);

    return false;
  }

  public int doc() { return current.doc; }

  public boolean next() throws IOException {
    boolean more;
    do {
      while (bucketTable.first != null) {         // more queued
        current = bucketTable.first;
        bucketTable.first = current.next;         // pop the queue

        // check prohibited & required, and minNrShouldMatch
        if ((current.bits & prohibitedMask) == 0 &&
            (current.bits & requiredMask) == requiredMask &&
            current.coord >= minNrShouldMatch) {
          return true;
        }
      }

      // refill the queue
      more = false;
      end += BucketTable.SIZE;
      for (SubScorer sub = scorers; sub != null; sub = sub.next) {
        Scorer scorer = sub.scorer;
        sub.collector.setScorer(scorer);
        while (!sub.done && scorer.doc() < end) {
          sub.collector.collect(scorer.doc());
          sub.done = !scorer.next();
        }
        if (!sub.done) {
          more = true;
        }
      }
    } while (bucketTable.first != null || more);

    return false;
  }

  public float score() {
    if (coordFactors == null) {
      computeCoordFactors();
    }
    return current.score * coordFactors[current.coord];
  }

  static final class Bucket {
    int doc = -1;                                 // tells if bucket is valid
    float       score;                            // incremental score
    int bits;                                     // used for bool constraints
    int coord;                                    // count of terms in score
    Bucket      next;                             // next valid bucket
  }

  // An internal class which is used in score(Collector, int) for setting the
  // current score. This is required since Collector exposes a setScorer method
  // and implementations that need the score will call scorer.score().
  // Therefore the only methods that are implemented are score() and doc().
  private static final class BucketScorer extends Scorer {

    float score;
    int doc;
    
    public BucketScorer() {
      super(null);
    }
    
    
    public Explanation explain(int doc) throws IOException {
      return null;
    }

    public float score() throws IOException {
      return score;
    }

    public int doc() {
      return doc;
    }

    public boolean next() throws IOException {
      return false;
    }

    public boolean skipTo(int target) throws IOException {
      return false;
    }
    
  }
  
  /** A simple hash table of document scores within a range. */
  static final class BucketTable {
    public static final int SIZE = 1 << 11;
    public static final int MASK = SIZE - 1;

    final Bucket[] buckets = new Bucket[SIZE];
    Bucket first = null;                          // head of valid list
  
    public BucketTable() {}

    public final int size() { return SIZE; }

    public Collector newCollector(int mask) {
      return new BooleanScorerCollector(mask, this);
    }
  }

  private static final class BooleanScorerCollector extends Collector {
    private BucketTable bucketTable;
    private int mask;
    private Scorer scorer;
    
    public BooleanScorerCollector(int mask, BucketTable bucketTable) {
      this.mask = mask;
      this.bucketTable = bucketTable;
    }
    public void setScorer(Scorer scorer) throws IOException {
      this.scorer = scorer;
    }
    
    public final void collect(final int doc) throws IOException {
      final BucketTable table = bucketTable;
      final int i = doc & BucketTable.MASK;
      Bucket bucket = table.buckets[i];
      if (bucket == null)
        table.buckets[i] = bucket = new Bucket();
      
      if (bucket.doc != doc) {                    // invalid bucket
        bucket.doc = doc;                         // set doc
        bucket.score = scorer.score();            // initialize score
        bucket.bits = mask;                       // initialize mask
        bucket.coord = 1;                         // initialize coord

        bucket.next = table.first;                // push onto valid list
        table.first = bucket;
      } else {                                    // valid bucket
        bucket.score += scorer.score();           // increment score
        bucket.bits |= mask;                      // add bits in mask
        bucket.coord++;                           // increment coord
      }
    }
    public void setNextReader(IndexReader reader, int docBase) {
      // not needed by this implementation
    }
  }

  public boolean skipTo(int target) {
    throw new UnsupportedOperationException();
  }

  public Explanation explain(int doc) {
    throw new UnsupportedOperationException();
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("boolean(");
    for (SubScorer sub = scorers; sub != null; sub = sub.next) {
      buffer.append(sub.scorer.toString());
      buffer.append(" ");
    }
    buffer.append(")");
    return buffer.toString();
  }

}
