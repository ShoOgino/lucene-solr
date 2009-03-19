package org.apache.lucene.collation;

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


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.Tokenizer;

import java.text.Collator;
import java.io.Reader;
import java.io.IOException;

/**
 * <p>
 *   Filters {@link KeywordTokenizer} with {@link CollationKeyFilter}.
 * </p>
 * <p>
 *   Converts the token into its {@link java.text.CollationKey}, and then
 *   encodes the CollationKey with 
 *   {@link org.apache.lucene.util.IndexableBinaryStringTools}, to allow 
 *   it to be stored as an index term.
 * </p>
 * <p>
 *   <strong>WARNING:</strong> Make sure you use exactly the same Collator at
 *   index and query time -- CollationKeys are only comparable when produced by
 *   the same Collator.  Since {@link java.text.RuleBasedCollator}s are not
 *   independently versioned, it is unsafe to search against stored
 *   CollationKeys unless the following are exactly the same (best practice is
 *   to store this information with the index and check that they remain the
 *   same at query time):
 * </p>
 * <ol>
 *   <li>JVM vendor</li>
 *   <li>JVM version, including patch version</li>
 *   <li>
 *     The language (and country and variant, if specified) of the Locale
 *     used when constructing the collator via
 *     {@link Collator#getInstance(java.util.Locale)}.
 *   </li>
 *   <li>
 *     The collation strength used - see {@link Collator#setStrength(int)}
 *   </li>
 * </ol> 
 * <p>
 *   NB 1: {@link ICUCollationKeyAnalyzer} uses ICU4J's Collator, which makes 
 *   its version available, thus allowing collation to be versioned
 *   independently from the JVM.
 * </p>
 * <p>
 *   NB 2: CollationKeys generated by java.text.Collators are not compatible
 *   with those those generated by ICU Collators.  Specifically, if you use 
 *   CollationKeyAnalyzer to generate index terms, do not use
 *   ICUCollationKeyAnalyzer on the query side, or vice versa.
 * </p>
 */
public class CollationKeyAnalyzer extends Analyzer {
  private Collator collator;

  CollationKeyAnalyzer(Collator collator) {
    this.collator = collator;
  }

  public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream result = new KeywordTokenizer(reader);
    result = new CollationKeyFilter(result, collator);
    return result;
  }
  
  private class SavedStreams {
    Tokenizer source;
    TokenStream result;
  }
  
  public TokenStream reusableTokenStream(String fieldName, Reader reader) 
    throws IOException {
    
    SavedStreams streams = (SavedStreams)getPreviousTokenStream();
    if (streams == null) {
      streams = new SavedStreams();
      streams.source = new KeywordTokenizer(reader);
      streams.result = new CollationKeyFilter(streams.source, collator);
      setPreviousTokenStream(streams);
    } else {
      streams.source.reset(reader);
    }
    return streams.result;
  }
}
