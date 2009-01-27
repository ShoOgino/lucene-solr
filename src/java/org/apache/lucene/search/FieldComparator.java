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
import java.text.Collator;
import java.util.Locale;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.ExtendedFieldCache.DoubleParser;
import org.apache.lucene.search.ExtendedFieldCache.LongParser;
import org.apache.lucene.search.FieldCache.ByteParser;
import org.apache.lucene.search.FieldCache.FloatParser;
import org.apache.lucene.search.FieldCache.IntParser;
import org.apache.lucene.search.FieldCache.ShortParser;
import org.apache.lucene.search.FieldCache.StringIndex;

/**
 * A FieldComparator compares hits across multiple IndexReaders.
 * 
 * A comparator can compare a hit at hit 'slot a' with hit 'slot b',
 * compare a hit on 'doc i' with hit 'slot a', or copy a hit at 'doc i'
 * to 'slot a'. Each slot refers to a hit while each doc refers to the
 * current IndexReader.
 *
 * <b>NOTE:</b> This API is experimental and might change in
 * incompatible ways in the next release.
 */
public abstract class FieldComparator {

  /** Parses field's values as byte (using {@link
   *  FieldCache#getBytes} and sorts by ascending value */
  public static final class ByteComparator extends FieldComparator {
    private final byte[] values;
    private byte[] currentReaderValues;
    private final String field;
    private ByteParser parser;
    private byte bottom;

    ByteComparator(int numHits, String field, FieldCache.Parser parser) {
      values = new byte[numHits];
      this.field = field;
      this.parser = (ByteParser) parser;
    }

    public int compare(int slot1, int slot2) {
      return values[slot1] - values[slot2];
    }

    public int compareBottom(int doc, float score) {
      return bottom - currentReaderValues[doc];
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      currentReaderValues = parser != null ? ExtendedFieldCache.EXT_DEFAULT
        .getBytes(reader, field, parser) : ExtendedFieldCache.EXT_DEFAULT
        .getBytes(reader, field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.BYTE;
    }

    public Comparable value(int slot) {
      return new Byte(values[slot]);
    }
  };

  /** Sorts by ascending docID */
  public static final class DocComparator extends FieldComparator {
    private final int[] docIDs;
    private int docBase;
    private int bottom;

    DocComparator(int numHits) {
      docIDs = new int[numHits];
    }

    public int compare(int slot1, int slot2) {
      // No overflow risk because docIDs are non-negative
      return docIDs[slot1] - docIDs[slot2];
    }

    public int compareBottom(int doc, float score) {
      // No overflow risk because docIDs are non-negative
      return bottom - (docBase + doc);
    }

    public void copy(int slot, int doc, float score) {
      docIDs[slot] = docBase + doc;
    }

    public void setNextReader(IndexReader reader, int docBase, int numSlotsFull) {
      // TODO: can we "map" our docIDs to the current
      // reader? saves having to then subtract on every
      // compare call
      this.docBase = docBase;
    }
    
    public void setBottom(final int bottom) {
      this.bottom = docIDs[bottom];
    }

    public int sortType() {
      return SortField.DOC;
    }

    public Comparable value(int slot) {
      return new Integer(docIDs[slot]);
    }
  };

  /** Parses field's values as double (using {@link
   *  ExtendedFieldCache#getDoubles} and sorts by ascending value */
  public static final class DoubleComparator extends FieldComparator {
    private final double[] values;
    private double[] currentReaderValues;
    private final String field;
    private DoubleParser parser;
    private double bottom;

    DoubleComparator(int numHits, String field, FieldCache.Parser parser) {
      values = new double[numHits];
      this.field = field;
      this.parser = (DoubleParser) parser;
    }

    public int compare(int slot1, int slot2) {
      final double v1 = values[slot1];
      final double v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public int compareBottom(int doc, float score) {
      final double v2 = currentReaderValues[doc];
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase, int numSlotsFull) throws IOException {
      currentReaderValues = parser != null ? ExtendedFieldCache.EXT_DEFAULT
          .getDoubles(reader, field, parser) : ExtendedFieldCache.EXT_DEFAULT
          .getDoubles(reader, field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.DOUBLE;
    }

    public Comparable value(int slot) {
      return new Double(values[slot]);
    }
  };

  /** Parses field's values as float (using {@link
   *  FieldCache#getFloats} and sorts by ascending value */
  public static final class FloatComparator extends FieldComparator {
    private final float[] values;
    private float[] currentReaderValues;
    private final String field;
    private FloatParser parser;
    private float bottom;

    FloatComparator(int numHits, String field, FieldCache.Parser parser) {
      values = new float[numHits];
      this.field = field;
      this.parser = (FloatParser) parser;
    }

    public int compare(int slot1, int slot2) {
      // TODO: are there sneaky non-branch ways to compute
      // sign of float?
      final float v1 = values[slot1];
      final float v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public int compareBottom(int doc, float score) {
      // TODO: are there sneaky non-branch ways to compute
      // sign of float?
      final float v2 = currentReaderValues[doc];
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      currentReaderValues = parser != null ? FieldCache.DEFAULT.getFloats(
          reader, field, parser) : FieldCache.DEFAULT.getFloats(reader, field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.FLOAT;
    }

    public Comparable value(int slot) {
      return new Float(values[slot]);
    }
  };

  /** Parses field's values as int (using {@link
   *  FieldCache#getInts} and sorts by ascending value */
  public static final class IntComparator extends FieldComparator {
    private final int[] values;
    private int[] currentReaderValues;
    private final String field;
    private IntParser parser;
    private int bottom;                           // Value of bottom of queue

    IntComparator(int numHits, String field, FieldCache.Parser parser) {
      values = new int[numHits];
      this.field = field;
      this.parser = (IntParser) parser;
    }

    public int compare(int slot1, int slot2) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      // Cannot return values[slot1] - values[slot2] because that
      // may overflow
      final int v1 = values[slot1];
      final int v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public int compareBottom(int doc, float score) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      // Cannot return bottom - values[slot2] because that
      // may overflow
      final int v2 = currentReaderValues[doc];
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      currentReaderValues = parser != null ? FieldCache.DEFAULT.getInts(reader,
          field, parser) : FieldCache.DEFAULT.getInts(reader, field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.INT;
    }

    public Comparable value(int slot) {
      return new Integer(values[slot]);
    }
  };

  /** Parses field's values as long (using {@link
   *  ExtendedFieldCache#getLongs} and sorts by ascending value */
  public static final class LongComparator extends FieldComparator {
    private final long[] values;
    private long[] currentReaderValues;
    private final String field;
    private LongParser parser;
    private long bottom;

    LongComparator(int numHits, String field, FieldCache.Parser parser) {
      values = new long[numHits];
      this.field = field;
      this.parser = (LongParser) parser;
    }

    public int compare(int slot1, int slot2) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      final long v1 = values[slot1];
      final long v2 = values[slot2];
      if (v1 > v2) {
        return 1;
      } else if (v1 < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public int compareBottom(int doc, float score) {
      // TODO: there are sneaky non-branch ways to compute
      // -1/+1/0 sign
      final long v2 = currentReaderValues[doc];
      if (bottom > v2) {
        return 1;
      } else if (bottom < v2) {
        return -1;
      } else {
        return 0;
      }
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      currentReaderValues = parser != null ? ExtendedFieldCache.EXT_DEFAULT
          .getLongs(reader, field, parser) : ExtendedFieldCache.EXT_DEFAULT
          .getLongs(reader, field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.LONG;
    }

    public Comparable value(int slot) {
      return new Long(values[slot]);
    }
  };

  /** Sorts by descending relevance.  NOTE: if you are
   *  sorting only by descending relevance and then
   *  secondarily by ascending docID, peformance is faster
   *  using {@link TopScoreDocCollector} directly (which {@link
   *  IndexSearcher#search} uses when no {@link Sort} is
   *  specified). */
  public static final class RelevanceComparator extends FieldComparator {
    private final float[] scores;
    private float bottom;

    RelevanceComparator(int numHits) {
      scores = new float[numHits];
    }

    public int compare(int slot1, int slot2) {
      final float score1 = scores[slot1];
      final float score2 = scores[slot2];
      if (score1 > score2) {
        return -1;
      } else if (score1 < score2) {
        return 1;
      } else {
        return 0;
      }
    }

    public int compareBottom(int doc, float score) {
      if (bottom > score) {
        return -1;
      } else if (bottom < score) {
        return 1;
      } else {
        return 0;
      }
    }

    public void copy(int slot, int doc, float score) {
      scores[slot] = score;
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) {
    }
    
    public void setBottom(final int bottom) {
      this.bottom = scores[bottom];
    }

    public int sortType() {
      return SortField.SCORE;
    }

    public Comparable value(int slot) {
      return new Float(scores[slot]);
    }
  };

  /** Parses field's values as short (using {@link
   *  FieldCache#getShorts} and sorts by ascending value */
  public static final class ShortComparator extends FieldComparator {
    private final short[] values;
    private short[] currentReaderValues;
    private final String field;
    private ShortParser parser;
    private short bottom;

    ShortComparator(int numHits, String field, FieldCache.Parser parser) {
      values = new short[numHits];
      this.field = field;
      this.parser = (ShortParser) parser;
    }

    public int compare(int slot1, int slot2) {
      return values[slot1] - values[slot2];
    }

    public int compareBottom(int doc, float score) {
      return bottom - currentReaderValues[doc];
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      currentReaderValues = parser != null ? ExtendedFieldCache.EXT_DEFAULT
          .getShorts(reader, field, parser) : ExtendedFieldCache.EXT_DEFAULT
          .getShorts(reader, field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.BYTE;
    }

    public Comparable value(int slot) {
      return new Short(values[slot]);
    }
  };

  /** Sorts by a field's value using the Collator for a
   *  given Locale.*/
  public static final class StringComparatorLocale extends FieldComparator {

    private final String[] values;
    private String[] currentReaderValues;
    private final String field;
    final Collator collator;
    private String bottom;

    StringComparatorLocale(int numHits, String field, Locale locale) {
      values = new String[numHits];
      this.field = field;
      collator = Collator.getInstance(locale);
    }

    public int compare(int slot1, int slot2) {
      final String val1 = values[slot1];
      final String val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return collator.compare(val1, val2);
    }

    public int compareBottom(int doc, float score) {
      final String val2 = currentReaderValues[doc];
      if (bottom == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return collator.compare(bottom, val2);
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      currentReaderValues = ExtendedFieldCache.EXT_DEFAULT.getStrings(reader,
          field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.STRING;
    }

    public Comparable value(int slot) {
      return values[slot];
    }
  };

  // NOTE: there were a number of other interesting String
  // comparators explored, but this one seemed to perform
  // best all around.  See LUCENE-1483 for details.
  public static final class StringOrdValComparator extends FieldComparator {

    private final int[] ords;
    private final String[] values;
    private final int[] readerGen;

    private int currentReaderGen = -1;
    private String[] lookup;
    private int[] order;
    private final String field;

    private int bottomSlot = -1;
    private int bottomOrd;
    private String bottomValue;
    private final boolean reversed;
    private final int sortPos;

    public StringOrdValComparator(int numHits, String field, int sortPos, boolean reversed) {
      ords = new int[numHits];
      values = new String[numHits];
      readerGen = new int[numHits];
      this.sortPos = sortPos;
      this.reversed = reversed;
      this.field = field;
    }

    public int compare(int slot1, int slot2) {
      if (readerGen[slot1] == readerGen[slot2]) {
        int cmp = ords[slot1] - ords[slot2];
        if (cmp != 0) {
          return cmp;
        }
      }

      final String val1 = values[slot1];
      final String val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return val1.compareTo(val2);
    }

    public int compareBottom(int doc, float score) {
      assert bottomSlot != -1;
      int order = this.order[doc];
      final int cmp = bottomOrd - order;
      if (cmp != 0) {
        return cmp;
      }

      final String val2 = lookup[order];
      if (bottomValue == null) {
        if (val2 == null) {
          return 0;
        }
        // bottom wins
        return -1;
      } else if (val2 == null) {
        // doc wins
        return 1;
      }
      return bottomValue.compareTo(val2);
    }

    private void convert(int slot) {
      readerGen[slot] = currentReaderGen;
      int index = 0;
      String value = values[slot];
      if (value == null) {
        ords[slot] = 0;
        return;
      }

      if (sortPos == 0 && bottomSlot != -1 && bottomSlot != slot) {
        // Since we are the primary sort, the entries in the
        // queue are bounded by bottomOrd:
        assert bottomOrd < lookup.length;
        if (reversed) {
          index = binarySearch(lookup, value, bottomOrd, lookup.length-1);
        } else {
          index = binarySearch(lookup, value, 0, bottomOrd);
        }
      } else {
        // Full binary search
        index = binarySearch(lookup, value);
      }

      if (index < 0) {
        index = -index - 2;
      }
      ords[slot] = index;
    }

    public void copy(int slot, int doc, float score) {
      final int ord = order[doc];
      ords[slot] = ord;
      assert ord >= 0;
      values[slot] = lookup[ord];
      readerGen[slot] = currentReaderGen;
    }

    public void setNextReader(IndexReader reader, int docBase,  int numSlotsFull) throws IOException {
      StringIndex currentReaderValues = ExtendedFieldCache.EXT_DEFAULT.getStringIndex(reader, field);
      currentReaderGen++;
      order = currentReaderValues.order;
      lookup = currentReaderValues.lookup;
      assert lookup.length > 0;
      if (bottomSlot != -1) {
        convert(bottomSlot);
        bottomOrd = ords[bottomSlot];
      }
    }
    
    public void setBottom(final int bottom) {
      bottomSlot = bottom;
      if (readerGen[bottom] != currentReaderGen) {
        convert(bottomSlot);
      }
      bottomOrd = ords[bottom];
      assert bottomOrd >= 0;
      assert bottomOrd < lookup.length;
      bottomValue = values[bottom];
    }

    public int sortType() {
      return SortField.STRING;
    }

    public Comparable value(int slot) {
      return values[slot];
    }

    public String[] getValues() {
      return values;
    }

    public int getBottomSlot() {
      return bottomSlot;
    }

    public String getField() {
      return field;
    }
  };

  /** Sorts by field's natural String sort order.  All
   *  comparisons are done using String.compareTo, which is
   *  slow for medium to large result sets but possibly
   *  very fast for very small results sets. */
  public static final class StringValComparator extends FieldComparator {

    private String[] values;
    private String[] currentReaderValues;
    private final String field;
    private String bottom;

    StringValComparator(int numHits, String field) {
      values = new String[numHits];
      this.field = field;
    }

    public int compare(int slot1, int slot2) {
      final String val1 = values[slot1];
      final String val2 = values[slot2];
      if (val1 == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }

      return val1.compareTo(val2);
    }

    public int compareBottom(int doc, float score) {
      final String val2 = currentReaderValues[doc];
      if (bottom == null) {
        if (val2 == null) {
          return 0;
        }
        return -1;
      } else if (val2 == null) {
        return 1;
      }
      return bottom.compareTo(val2);
    }

    public void copy(int slot, int doc, float score) {
      values[slot] = currentReaderValues[doc];
    }

    public void setNextReader(IndexReader reader, int docBase, int numSlotsFull) throws IOException {
      currentReaderValues = ExtendedFieldCache.EXT_DEFAULT.getStrings(reader,
          field);
    }
    
    public void setBottom(final int bottom) {
      this.bottom = values[bottom];
    }

    public int sortType() {
      return SortField.STRING_VAL;
    }

    public Comparable value(int slot) {
      return values[slot];
    }
  };

  final protected static int binarySearch(String[] a, String key) {
    return binarySearch(a, key, 0, a.length-1);
  };

  final protected static int binarySearch(String[] a, String key, int low, int high) {

    while (low <= high) {
      int mid = (low + high) >>> 1;
      String midVal = a[mid];
      int cmp;
      if (midVal != null) {
        cmp = midVal.compareTo(key);
      } else {
        cmp = -1;
      }

      if (cmp < 0)
        low = mid + 1;
      else if (cmp > 0)
        high = mid - 1;
      else
        return mid;
    }
    return -(low + 1);
  };

  /**
   * Compare hit at slot1 with hit at slot2.  Return 
   * 
   * @param slot1 first slot to compare
   * @param slot2 second slot to compare
   * @return any N < 0 if slot2's value is sorted after
   * slot1, any N > 0 if the slot2's value is sorted before
   * slot1 and 0 if they are equal
   */
  public abstract int compare(int slot1, int slot2);

  /**
   * Set the bottom queue slot, ie the "weakest" (sorted
   * last) entry in the queue.
   * 
   * @param slot the currently weakest (sorted lost) slot in the queue
   */
  public abstract void setBottom(final int slot);

  /**
   * Compare the bottom of the queue with doc.  This will
   * only invoked after setBottom has been called.  
   * 
   * @param doc that was hit
   * @param score of the hit
   * @return any N < 0 if the doc's value is sorted after
   * the bottom entry (not competitive), any N > 0 if the
   * doc's value is sorted before the bottom entry and 0 if
   * they are equal.
   */
  public abstract int compareBottom(int doc, float score);

  /**
   * Copy hit (doc,score) to hit slot.
   * 
   * @param slot which slot to copy the hit to
   * @param doc docID relative to current reader
   * @param score hit score
   */
  public abstract void copy(int slot, int doc, float score);

  /**
   * Set a new Reader. All doc correspond to the current Reader.
   * 
   * @param reader current reader
   * @param docBase docBase of this reader 
   * @throws IOException
   * @throws IOException
   */
  public abstract void setNextReader(IndexReader reader, int docBase, int numSlotsFull) throws IOException;

  /**
   * @return SortField.TYPE
   */
  public abstract int sortType();

  /**
   * Return the actual value at slot.
   * 
   * @param slot the value
   * @return value in this slot upgraded to Comparable
   */
  public abstract Comparable value(int slot);
}
