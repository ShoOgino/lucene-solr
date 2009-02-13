package org.apache.lucene.search.trie;

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

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.util.OpenBitSet;


/**
 * Implementation of a Lucene {@link Filter} that implements trie-based range filtering for ints/floats.
 * This filter depends on a specific structure of terms in the index that can only be created
 * by {@link TrieUtils} methods.
 * For more information, how the algorithm works, see the {@linkplain org.apache.lucene.search.trie package description}.
 */
public class IntTrieRangeFilter extends AbstractTrieRangeFilter {

  /**
   * A trie filter for matching trie coded values using the given field name and
   * the default helper field.
   * <code>precisionStep</code> must me equal or a multiple of the <code>precisionStep</code>
   * used for indexing the values.
   * You can leave the bounds open, by supplying <code>null</code> for <code>min</code> and/or
   * <code>max</code>. Inclusive/exclusive bounds can also be supplied.
   * To query float values use the converter {@link TrieUtils#floatToSortableInt}.
   * <p>This is the counterpart to {@link TrieUtils#addIndexedFields(Document,String,String[])}.
   * <p><b>This is the recommended usage of TrieUtils/IntTrieRangeFilter.</b>
   */
  public IntTrieRangeFilter(final String field, final int precisionStep,
    final Integer min, final Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    this(
      new String[]{field, field+TrieUtils.LOWER_PRECISION_FIELD_NAME_SUFFIX},
      precisionStep,min,max,minInclusive,maxInclusive
    );
  }
  
  /**
   * Expert: A trie filter for matching trie coded values using the given field names.
   * You can specify the main and helper field name, that was used to idex the values.
   * <code>precisionStep</code> must me equal or a multiple of the <code>precisionStep</code>
   * used for indexing the values.
   * You can leave the bounds open, by supplying <code>null</code> for <code>min</code> and/or
   * <code>max</code>. Inclusive/exclusive bounds can also be supplied.
   * To query float values use the converter {@link TrieUtils#floatToSortableInt}.
   * <p>This is the counterpart to {@link TrieUtils#addIndexedFields(Document,String,String,String[])}.
   */
  public IntTrieRangeFilter(final String field, final String lowerPrecisionField, final int precisionStep,
    final Integer min, final Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    this(new String[]{field, lowerPrecisionField},precisionStep,min,max,minInclusive,maxInclusive);
  }

  /**
   * Expert: A trie filter for matching trie coded values
   * using the given field names. If the array of field names is shorter than the
   * trieCoded one, all trieCoded values with higher index get the last field name.
   * <code>precisionStep</code> must me equal or a multiple of the <code>precisionStep</code>
   * used for indexing the values.
   * You can leave the bounds open, by supplying <code>null</code> for <code>min</code> and/or
   * <code>max</code>. Inclusive/exclusive bounds can also be supplied.
   * To query float values use the converter {@link TrieUtils#floatToSortableInt}.
   * <p>This is the counterpart to {@link TrieUtils#addIndexedFields(Document,String[],String[])}.
   */
  public IntTrieRangeFilter(final String[] fields, final int precisionStep,
    Integer min, Integer max, final boolean minInclusive, final boolean maxInclusive
  ) {
    super(fields, precisionStep, min, max, minInclusive, maxInclusive);
  }

  /**
   * Returns a DocIdSet that provides the documents which should be permitted or prohibited in search results.
   */
  //@Override
  public DocIdSet getDocIdSet(final IndexReader reader) throws IOException {
    // calculate the upper and lower bounds respecting the inclusive and null values.
    int minBound=(this.min==null) ? Integer.MIN_VALUE : (
      minInclusive ? this.min.intValue() : (this.min.intValue()+1)
    );
    int maxBound=(this.max==null) ? Integer.MAX_VALUE : (
      maxInclusive ? this.max.intValue() : (this.max.intValue()-1)
    );
    
    resetLastNumberOfTerms();
    if (minBound > maxBound) {
      // shortcut, no docs will match this
      return DocIdSet.EMPTY_DOCIDSET;
    } else {
      final OpenBitSet bits = new OpenBitSet(reader.maxDoc());
      final TermDocs termDocs = reader.termDocs();
      try {
        TrieUtils.splitIntRange(new TrieUtils.IntRangeBuilder() {
        
          //@Override
          public final void addRange(String minPrefixCoded, String maxPrefixCoded, int level) {
            try {
              fillBits(
                reader, bits, termDocs,
                fields[Math.min(fields.length-1, level)],
                minPrefixCoded, maxPrefixCoded
              );
            } catch (IOException ioe) {
              // IntRangeBuilder is not allowed to throw checked exceptions:
              // wrap as RuntimeException
              throw new RuntimeException(ioe);
            }
          }
        
        }, precisionStep, minBound, maxBound);
      } catch (RuntimeException e) {
        if (e.getCause() instanceof IOException) throw (IOException)e.getCause();
        throw e;
      } finally {
        termDocs.close();
      }
      return bits;
    }
  }

}
