package org.apache.lucene.document;

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

import java.io.Reader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.NumericTokenStream;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.search.NumericRangeQuery; // javadocs
import org.apache.lucene.search.NumericRangeFilter; // javadocs
import org.apache.lucene.search.SortField; // javadocs
import org.apache.lucene.search.FieldCache; // javadocs

/**
 * <p>This class provides a {@link Field} that enables indexing
 * of numeric values for efficient range filtering and
 * sorting.  Here's an example usage, adding an int value:
 * <pre>
 *   document.add(new NumericField(name).setIntValue(value));
 * </pre>
 *
 * For optimal performance, re-use the
 * NumericField and {@link Document} instance for more than
 * one document:
 *
 * <pre>
 *  <em>// init</em>
 *  NumericField field = new NumericField(name);
 *  Document document = new Document();
 *  document.add(field);
 *
 *  for(all documents) {
 *    ...
 *    field.setIntValue(value)
 *    writer.addDocument(document);
 *    ...
 *  }
 * </pre>
 *
 * <p>The java native types int, long, float and double are
 * directly supported.  However, any value that can be
 * converted into these native types can also be indexed.
 * For example, date/time values represented by a
 * <code>java.util.Date</code> can be translated into a long
 * value using the <code>getTime</code> method.  If you
 * don't need millisecond precision, you can quantize the
 * value, either by dividing the result of
 * <code>getTime</code> or using the separate getters (for
 * year, month, etc.) to construct an int or long value.</p>
 *
 * <p>To perform range querying or filtering against a
 * NumericField, use {@link NumericRangeQuery} or {@link
 * NumericRangeFilter}.  To sort according to a
 * NumericField, use the normal numeric sort types, eg
 * {@link SortField#INT} (note that {@link SortField#AUTO}
 * will not work with these fields).  NumericField values
 * can also be loaded directly from {@link FieldCache}.</p>
 *
 * <p>By default, a NumericField's value is not stored but
 * is indexed for range filtering and sorting.  You can use
 * the {@link #NumericField(String,Field.Store,boolean)}
 * constructor if you need to change these defaults.</p>
 *
 * <p>You may add the same field name as a NumericField to
 * the same document more than once.  Range querying and
 * filtering will be the logical OR of all values, however
 * sort behavior is not defined.  If you need to sort, you
 * should separately index a single-valued NumericField.</p>
 *
 * <p>A NumericField will consume somewhat more disk space
 * in the index than an ordindary single-valued field.
 * However, for a typical index that includes substantial
 * textual content per document, this increase will likely
 * be in the noise. </p>
 *
 * <p>Within lucene, each numeric value is indexed as a
 * <em>trie</em> structure, where each term is logically
 * assigned to larger and larger pre-defined brackets.  The
 * step size between each successive bracket is called the
 * <code>precisionStep</code>, measured in bits.  Smaller
 * <code>precisionStep</code> values result in larger number
 * of brackets, which consumes more disk space in the index
 * but may result in faster range search performance.  The
 * default value, 4, was selected for a reasonable tradeoff
 * of disk space consumption versus performance.  You can
 * use the expert constructor {@link
 * #NumericField(String,int,Field.Store,boolean)} if you'd
 * like to change the value.  Note that you must also
 * specify a congruent value when creating {@link
 * NumericRangeQuery} or {@link NumericRangeFilter}.
 *
 * <p>If you only need to sort by numeric value, and never
 * run range querying/filtering, you can index using a
 * <code>precisionStep</code> of {@link Integer#MAX_VALUE}.
 * This will minimize disk space consumed. </p>
 *
 * <p>More advanced users can instead use {@link
 * NumericTokenStream} directly, when indexing numbers. This
 * class is a wrapper around this token stream type for
 * easier, more intuitive usage.</p>
 *
 * <p>For more information on the internals of numeric trie
 * indexing, including the <a
 * href="../search/NumericRangeQuery.html#precisionStepDesc"><code>precisionStep</code></a>
 * configuration, see {@link NumericRangeQuery}.
 *
 * <p><b>NOTE:</b> This class is only used during
 * indexing. When retrieving the stored field value from a
 * {@link Document} instance after search, you will get a
 * conventional {@link Fieldable} instance where the numeric
 * values are returned as {@link String}s (according to
 * <code>toString(value)</code> of the used data type).
 *
 * <p><font color="red"><b>NOTE:</b> This API is
 * experimental and might change in incompatible ways in the
 * next release.</font>
 *
 * @since 2.9
 */
public final class NumericField extends AbstractField {

  private final NumericTokenStream tokenStream;

  /**
   * Creates a field for numeric values using the default <code>precisionStep</code>
   * {@link NumericUtils#PRECISION_STEP_DEFAULT} (4). The instance is not yet initialized with
   * a numeric value, before indexing a document containing this field,
   * set a value using the various set<em>???</em>Value() methods.
   * This constrcutor creates an indexed, but not stored field.
   * @param name the field name
   */
  public NumericField(String name) {
    this(name, NumericUtils.PRECISION_STEP_DEFAULT, Field.Store.NO, true);
  }
  
  /**
   * Creates a field for numeric values using the default <code>precisionStep</code>
   * {@link NumericUtils#PRECISION_STEP_DEFAULT} (4). The instance is not yet initialized with
   * a numeric value, before indexing a document containing this field,
   * set a value using the various set<em>???</em>Value() methods.
   * @param name the field name
   * @param store if the field should be stored in plain text form
   *  (according to <code>toString(value)</code> of the used data type)
   * @param index if the field should be indexed using {@link NumericTokenStream}
   */
  public NumericField(String name, Field.Store store, boolean index) {
    this(name, NumericUtils.PRECISION_STEP_DEFAULT, store, index);
  }
  
  /**
   * Creates a field for numeric values with the specified
   * <code>precisionStep</code>. The instance is not yet initialized with
   * a numeric value, before indexing a document containing this field,
   * set a value using the various set<em>???</em>Value() methods.
   * This constrcutor creates an indexed, but not stored field.
   * @param name the field name
   * @param precisionStep the used <a href="../search/NumericRangeQuery.html#precisionStepDesc">precision step</a>
   */
  public NumericField(String name, int precisionStep) {
    this(name, precisionStep, Field.Store.NO, true);
  }

  /**
   * Creates a field for numeric values with the specified
   * <code>precisionStep</code>. The instance is not yet initialized with
   * a numeric value, before indexing a document containing this field,
   * set a value using the various set<em>???</em>Value() methods.
   * @param name the field name
   * @param precisionStep the used <a href="../search/NumericRangeQuery.html#precisionStepDesc">precision step</a>
   * @param store if the field should be stored in plain text form
   *  (according to <code>toString(value)</code> of the used data type)
   * @param index if the field should be indexed using {@link NumericTokenStream}
   */
  public NumericField(String name, int precisionStep, Field.Store store, boolean index) {
    super(name, store, index ? Field.Index.ANALYZED_NO_NORMS : Field.Index.NO, Field.TermVector.NO);
    setOmitTermFreqAndPositions(true);
    tokenStream = new NumericTokenStream(precisionStep);
  }

  /** Returns a {@link NumericTokenStream} for indexing the numeric value. */
  public TokenStream tokenStreamValue()   {
    return isIndexed() ? tokenStream : null;
  }
  
  /** Returns always <code>null</code> for numeric fields */
  public byte[] binaryValue() {
    return null;
  }
  
  /** Returns always <code>null</code> for numeric fields */
  public byte[] getBinaryValue(byte[] result){
    return null;
  }

  /** Returns always <code>null</code> for numeric fields */
  public Reader readerValue() {
    return null;
  }
    
  /** Returns the numeric value as a string (how it is stored, when {@link Field.Store#YES} is choosen). */
  public String stringValue()   {
    return (fieldsData == null) ? null : fieldsData.toString();
  }
  
  /** Returns the current numeric value as a subclass of {@link Number}, <code>null</code> if not yet initialized. */
  public Number getNumericValue() {
    return (Number) fieldsData;
  }
  
  /**
   * Initializes the field with the supplied <code>long</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setLongValue(value))</code>
   */
  public NumericField setLongValue(final long value) {
    tokenStream.setLongValue(value);
    fieldsData = new Long(value);
    return this;
  }
  
  /**
   * Initializes the field with the supplied <code>int</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setIntValue(value))</code>
   */
  public NumericField setIntValue(final int value) {
    tokenStream.setIntValue(value);
    fieldsData = new Integer(value);
    return this;
  }
  
  /**
   * Initializes the field with the supplied <code>double</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setDoubleValue(value))</code>
   */
  public NumericField setDoubleValue(final double value) {
    tokenStream.setDoubleValue(value);
    fieldsData = new Double(value);
    return this;
  }
  
  /**
   * Initializes the field with the supplied <code>float</code> value.
   * @param value the numeric value
   * @return this instance, because of this you can use it the following way:
   * <code>document.add(new NumericField(name, precisionStep).setFloatValue(value))</code>
   */
  public NumericField setFloatValue(final float value) {
    tokenStream.setFloatValue(value);
    fieldsData = new Float(value);
    return this;
  }

}
