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
import java.util.LinkedList;

import org.apache.lucene.analysis.NumericTokenStream; // for javadocs
import org.apache.lucene.document.NumericField; // for javadocs
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.ToStringUtils;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 * Implementation of a {@link Query} that implements <em>trie-based</em> range querying
 * for numeric values.
 *
 * <h3>Usage</h3>
 * <h4>Indexing</h4>
 * Before numeric values can be queried, they must be indexed in a special way. You can do this
 * by adding numeric fields to the index by specifying a {@link NumericField} (expert: {@link NumericTokenStream}).
 * An important setting is the <a href="#precisionStepDesc"><code>precisionStep</code></a>, which specifies,
 * how many different precisions per numeric value are indexed to speed up range queries.
 * Lower values create more terms but speed up search, higher values create less terms, but
 * slow down search. Suitable values are 2, 4, or 8. A good starting point to test is 4.
 * For code examples see {@link NumericField}.
 *
 * <h4>Searching</h4>
 * <p>This class has no constructor, you can create queries depending on the data type
 * by using the static factories {@linkplain #newLongRange NumericRangeQuery.newLongRange()},
 * {@linkplain #newIntRange NumericRangeQuery.newIntRange()}, {@linkplain #newDoubleRange NumericRangeQuery.newDoubleRange()},
 * and {@linkplain #newFloatRange NumericRangeQuery.newFloatRange()}, e.g.:
 * <pre>
 * Query q = NumericRangeQuery.newFloatRange(field, <a href="#precisionStepDesc">precisionStep</a>,
 *                                           new Float(0.3f), new Float(0.10f),
 *                                           true, true);
 * </pre>
 *
 * <h3>How it works</h3>
 *
 * <p>See the publication about <a target="_blank" href="http://www.panfmp.org">panFMP</a>,
 * where this algorithm was described (referred to as <code>TrieRangeQuery</code>):
 *
 * <blockquote><strong>Schindler, U, Diepenbroek, M</strong>, 2008.
 * <em>Generic XML-based Framework for Metadata Portals.</em>
 * Computers &amp; Geosciences 34 (12), 1947-1955.
 * <a href="http://dx.doi.org/10.1016/j.cageo.2008.02.023"
 * target="_blank">doi:10.1016/j.cageo.2008.02.023</a></blockquote>
 *
 * <p><em>A quote from this paper:</em> Because Apache Lucene is a full-text
 * search engine and not a conventional database, it cannot handle numerical ranges
 * (e.g., field value is inside user defined bounds, even dates are numerical values).
 * We have developed an extension to Apache Lucene that stores
 * the numerical values in a special string-encoded format with variable precision
 * (all numerical values like doubles, longs, floats, and ints are converted to
 * lexicographic sortable string representations and stored with different precisions
 * (for a more detailed description of how the values are stored,
 * see {@link NumericUtils}). A range is then divided recursively into multiple intervals for searching:
 * The center of the range is searched only with the lowest possible precision in the <em>trie</em>,
 * while the boundaries are matched more exactly. This reduces the number of terms dramatically.</p>
 *
 * <p>For the variant that stores long values in 8 different precisions (each reduced by 8 bits) that
 * uses a lowest precision of 1 byte, the index contains only a maximum of 256 distinct values in the
 * lowest precision. Overall, a range could consist of a theoretical maximum of
 * <code>7*255*2 + 255 = 3825</code> distinct terms (when there is a term for every distinct value of an
 * 8-byte-number in the index and the range covers almost all of them; a maximum of 255 distinct values is used
 * because it would always be possible to reduce the full 256 values to one term with degraded precision).
 * In practise, we have seen up to 300 terms in most cases (index with 500,000 metadata records
 * and a uniform value distribution).</p>
 *
 * <a name="precisionStepDesc"><h3>Precision Step</h3>
 * <p>You can choose any <code>precisionStep</code> when encoding values.
 * Lower step values mean more precisions and so more terms in index (and index gets larger).
 * On the other hand, the maximum number of terms to match reduces, which optimized query speed.
 * The formula to calculate the maximum term count is:
 * <pre>
 *  n = [ (bitsPerValue/precisionStep - 1) * (2^precisionStep - 1 ) * 2 ] + (2^precisionStep - 1 )
 * </pre>
 * <p><em>(this formula is only correct, when <code>bitsPerValue/precisionStep</code> is an integer;
 * in other cases, the value must be rounded up and the last summand must contain the modulo of the division as
 * precision step)</em>.
 * For longs stored using a precision step of 4, <code>n = 15*15*2 + 15 = 465</code>, and for a precision
 * step of 2, <code>n = 31*3*2 + 3 = 189</code>. But the faster search speed is reduced by more seeking
 * in the term enum of the index. Because of this, the ideal <code>precisionStep</code> value can only
 * be found out by testing. <b>Important:</b> You can index with a lower precision step value and test search speed
 * using a multiple of the original step value.</p>
 *
 * <p>This dramatically improves the performance of Apache Lucene with range queries, which
 * are no longer dependent on the index size and the number of distinct values because there is
 * an upper limit unrelated to either of these properties.</p>
 *
 * <p>Comparisions of the different types of RangeQueries on an index with about 500,000 docs showed
 * that the old {@link RangeQuery} (with raised {@link BooleanQuery} clause count) took about 30-40
 * secs to complete, {@link ConstantScoreRangeQuery} took 5 secs and executing
 * this class took &lt;100ms to complete (on an Opteron64 machine, Java 1.5, 8 bit precision step).
 * This query type was developed for a geographic portal, where the performance for
 * e.g. bounding boxes or exact date/time stamps is important.</p>
 *
 * <p>The query is in {@linkplain #setConstantScoreRewrite constant score mode} per default.
 * With precision steps of &le;4, this query can be run in conventional {@link BooleanQuery}
 * rewrite mode without changing the max clause count.
 *
 * <p><font color="red"><b>NOTE:</b> This API is experimental and
 * might change in incompatible ways in the next release.</font>
 *
 * @since 2.9
 **/
public final class NumericRangeQuery extends MultiTermQuery {

  private NumericRangeQuery(final String field, final int precisionStep, final int valSize,
    Number min, Number max, final boolean minInclusive, final boolean maxInclusive
  ) {
    assert (valSize == 32 || valSize == 64);
    if (precisionStep < 1 || precisionStep > valSize)
      throw new IllegalArgumentException("precisionStep may only be 1.."+valSize);
    this.field = field.intern();
    this.precisionStep = precisionStep;
    this.valSize = valSize;
    this.min = min;
    this.max = max;
    this.minInclusive = minInclusive;
    this.maxInclusive = maxInclusive;
    setConstantScoreRewrite(true);
  }
  
  /**
   * Factory that creates a <code>NumericRangeQuery</code>, that queries a <code>long</code>
   * range using the given <a href="#precisionStepDesc"><code>precisionStep</code></a>.
   * You can have half-open ranges (which are in fact &lt;/&le; or &gt;/&ge; queries)
   * by setting the min or max value to <code>null</code>. By setting inclusive to false, it will
   * match all documents excluding the bounds, with inclusive on, the boundaries are hits, too.
   */
  public static NumericRangeQuery newLongRange(final String field, final int precisionStep,
    Long min, Long max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery(field, precisionStep, 64, min, max, minInclusive, maxInclusive);
  }
  
  /**
   * Factory that creates a <code>NumericRangeQuery</code>, that queries a <code>int</code>
   * range using the given <a href="#precisionStepDesc"><code>precisionStep</code></a>.
   * You can have half-open ranges (which are in fact &lt;/&le; or &gt;/&ge; queries)
   * by setting the min or max value to <code>null</code>. By setting inclusive to false, it will
   * match all documents excluding the bounds, with inclusive on, the boundaries are hits, too.
   */
  public static NumericRangeQuery newIntRange(final String field, final int precisionStep,
    Integer min, Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery(field, precisionStep, 32, min, max, minInclusive, maxInclusive);
  }
  
  /**
   * Factory that creates a <code>NumericRangeQuery</code>, that queries a <code>double</code>
   * range using the given <a href="#precisionStepDesc"><code>precisionStep</code></a>.
   * You can have half-open ranges (which are in fact &lt;/&le; or &gt;/&ge; queries)
   * by setting the min or max value to <code>null</code>. By setting inclusive to false, it will
   * match all documents excluding the bounds, with inclusive on, the boundaries are hits, too.
   */
  public static NumericRangeQuery newDoubleRange(final String field, final int precisionStep,
    Double min, Double max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery(field, precisionStep, 64, min, max, minInclusive, maxInclusive);
  }
  
  /**
   * Factory that creates a <code>NumericRangeQuery</code>, that queries a <code>float</code>
   * range using the given <a href="#precisionStepDesc"><code>precisionStep</code></a>.
   * You can have half-open ranges (which are in fact &lt;/&le; or &gt;/&ge; queries)
   * by setting the min or max value to <code>null</code>. By setting inclusive to false, it will
   * match all documents excluding the bounds, with inclusive on, the boundaries are hits, too.
   */
  public static NumericRangeQuery newFloatRange(final String field, final int precisionStep,
    Float min, Float max, final boolean minInclusive, final boolean maxInclusive
  ) {
    return new NumericRangeQuery(field, precisionStep, 32, min, max, minInclusive, maxInclusive);
  }
  
  //@Override
  protected FilteredTermEnum getEnum(final IndexReader reader) throws IOException {
    return new NumericRangeTermEnum(reader);
  }

  /** Returns the field name for this query */
  public String getField() { return field; }

  /** Returns <code>true</code> if the lower endpoint is inclusive */
  public boolean includesMin() { return minInclusive; }
  
  /** Returns <code>true</code> if the upper endpoint is inclusive */
  public boolean includesMax() { return maxInclusive; }

  /** Returns the lower value of this range query */
  public Number getMin() { return min; }

  /** Returns the upper value of this range query */
  public Number getMax() { return max; }
  
  //@Override
  public String toString(final String field) {
    final StringBuffer sb = new StringBuffer();
    if (!this.field.equals(field)) sb.append(this.field).append(':');
    return sb.append(minInclusive ? '[' : '{')
      .append((min == null) ? "*" : min.toString())
      .append(" TO ")
      .append((max == null) ? "*" : max.toString())
      .append(maxInclusive ? ']' : '}')
      .append(ToStringUtils.boost(getBoost()))
      .toString();
  }

  //@Override
  public final boolean equals(final Object o) {
    if (o==this) return true;
    if (!super.equals(o))
      return false;
    if (o instanceof NumericRangeQuery) {
      final NumericRangeQuery q=(NumericRangeQuery)o;
      return (
        field==q.field &&
        (q.min == null ? min == null : q.min.equals(min)) &&
        (q.max == null ? max == null : q.max.equals(max)) &&
        minInclusive == q.minInclusive &&
        maxInclusive == q.maxInclusive &&
        precisionStep == q.precisionStep
      );
    }
    return false;
  }

  //@Override
  public final int hashCode() {
    int hash = super.hashCode();
    hash += field.hashCode()^0x4565fd66 + precisionStep^0x64365465;
    if (min != null) hash += min.hashCode()^0x14fa55fb;
    if (max != null) hash += max.hashCode()^0x733fa5fe;
    return hash +
      (Boolean.valueOf(minInclusive).hashCode()^0x14fa55fb)+
      (Boolean.valueOf(maxInclusive).hashCode()^0x733fa5fe);
  }
  
  // members (package private, to be also fast accessible by NumericRangeTermEnum)
  final String field;
  final int precisionStep, valSize;
  final Number min, max;
  final boolean minInclusive,maxInclusive;

  /**
   * Subclass of FilteredTermEnum for enumerating all terms that match the
   * sub-ranges for trie range queries.
   * <p>
   * WARNING: This term enumeration is not guaranteed to be always ordered by
   * {@link Term#compareTo}.
   * The ordering depends on how {@link NumericUtils#splitLongRange} and
   * {@link NumericUtils#splitIntRange} generates the sub-ranges. For
   * {@link MultiTermQuery} ordering is not relevant.
   */
  private final class NumericRangeTermEnum extends FilteredTermEnum {

    private final IndexReader reader;
    private final LinkedList/*<String>*/ rangeBounds = new LinkedList/*<String>*/();
    private String currentUpperBound = null;

    NumericRangeTermEnum(final IndexReader reader) throws IOException {
      this.reader = reader;
      
      switch (valSize) {
        case 64: {
          // lower
          long minBound = Long.MIN_VALUE;
          if (min instanceof Long) {
            minBound = min.longValue();
          } else if (min instanceof Double) {
            minBound = NumericUtils.doubleToSortableLong(min.doubleValue());
          }
          if (!minInclusive && min != null) {
            if (minBound == Long.MAX_VALUE) break;
            minBound++;
          }
          
          // upper
          long maxBound = Long.MAX_VALUE;
          if (max instanceof Long) {
            maxBound = max.longValue();
          } else if (max instanceof Double) {
            maxBound = NumericUtils.doubleToSortableLong(max.doubleValue());
          }
          if (!maxInclusive && max != null) {
            if (maxBound == Long.MIN_VALUE) break;
            maxBound--;
          }
          
          NumericUtils.splitLongRange(new NumericUtils.LongRangeBuilder() {
            //@Override
            public final void addRange(String minPrefixCoded, String maxPrefixCoded) {
              rangeBounds.add(minPrefixCoded);
              rangeBounds.add(maxPrefixCoded);
            }
          }, precisionStep, minBound, maxBound);
          break;
        }
          
        case 32: {
          // lower
          int minBound = Integer.MIN_VALUE;
          if (min instanceof Integer) {
            minBound = min.intValue();
          } else if (min instanceof Float) {
            minBound = NumericUtils.floatToSortableInt(min.floatValue());
          }
          if (!minInclusive && min != null) {
            if (minBound == Integer.MAX_VALUE) break;
            minBound++;
          }
          
          // upper
          int maxBound = Integer.MAX_VALUE;
          if (max instanceof Integer) {
            maxBound = max.intValue();
          } else if (max instanceof Float) {
            maxBound = NumericUtils.floatToSortableInt(max.floatValue());
          }
          if (!maxInclusive && max != null) {
            if (maxBound == Integer.MIN_VALUE) break;
            maxBound--;
          }
          
          NumericUtils.splitIntRange(new NumericUtils.IntRangeBuilder() {
            //@Override
            public final void addRange(String minPrefixCoded, String maxPrefixCoded) {
              rangeBounds.add(minPrefixCoded);
              rangeBounds.add(maxPrefixCoded);
            }
          }, precisionStep, minBound, maxBound);
          break;
        }
          
        default:
          // should never happen
          throw new IllegalArgumentException("valSize must be 32 or 64");
      }
      
      // seek to first term
      next();
    }

    //@Override
    public float difference() {
      return 1.0f;
    }
    
    /** this is a dummy, it is not used by this class. */
    //@Override
    protected boolean endEnum() {
      assert false; // should never be called
      return (currentTerm != null);
    }

    /**
     * Compares if current upper bound is reached,
     * this also updates the term count for statistics.
     * In contrast to {@link FilteredTermEnum}, a return value
     * of <code>false</code> ends iterating the current enum
     * and forwards to the next sub-range.
     */
    //@Override
    protected boolean termCompare(Term term) {
      return (term.field() == field && term.text().compareTo(currentUpperBound) <= 0);
    }
    
    /** Increments the enumeration to the next element.  True if one exists. */
    //@Override
    public boolean next() throws IOException {
      // if a current term exists, the actual enum is initialized:
      // try change to next term, if no such term exists, fall-through
      if (currentTerm != null) {
        assert actualEnum!=null;
        if (actualEnum.next()) {
          currentTerm = actualEnum.term();
          if (termCompare(currentTerm)) return true;
        }
      }
      // if all above fails, we go forward to the next enum,
      // if one is available
      currentTerm = null;
      if (rangeBounds.size() < 2) return false;
      // close the current enum and read next bounds
      if (actualEnum != null) {
        actualEnum.close();
        actualEnum = null;
      }
      final String lowerBound = (String)rangeBounds.removeFirst();
      this.currentUpperBound = (String)rangeBounds.removeFirst();
      // this call recursively uses next(), if no valid term in
      // next enum found.
      // if this behavior is changed/modified in the superclass,
      // this enum will not work anymore!
      setEnum(reader.terms(new Term(field, lowerBound)));
      return (currentTerm != null);
    }

    /** Closes the enumeration to further activity, freeing resources.  */
    //@Override
    public void close() throws IOException {
      rangeBounds.clear();
      currentUpperBound = null;
      super.close();
    }

  }
  
}
