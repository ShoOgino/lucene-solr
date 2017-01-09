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
package org.apache.lucene.analysis.ngram;


import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.AttributeSource;

/**
 * Tokenizes the given token into n-grams of given size(s).
 * <p>
 * This {@link TokenFilter} create n-grams from the beginning edge of a input token.
 * <p><a name="match_version"></a>As of Lucene 4.4, this filter handles correctly
 * supplementary characters.
 */
public final class EdgeNGramTokenFilter extends TokenFilter {
  public static final int DEFAULT_MAX_GRAM_SIZE = 1;
  public static final int DEFAULT_MIN_GRAM_SIZE = 1;

  private final int minGram;
  private final int maxGram;
  private char[] curTermBuffer;
  private int curTermLength;
  private int curCodePointCount;
  private int curGramSize;
  private int savePosIncr;
  private AttributeSource attributes;
  
  private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
  private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

  /**
   * Creates EdgeNGramTokenFilter that can generate n-grams in the sizes of the given range
   *
   * @param input {@link TokenStream} holding the input to be tokenized
   * @param minGram the smallest n-gram to generate
   * @param maxGram the largest n-gram to generate
   */
  public EdgeNGramTokenFilter(TokenStream input, int minGram, int maxGram) {
    super(input);

    if (minGram < 1) {
      throw new IllegalArgumentException("minGram must be greater than zero");
    }

    if (minGram > maxGram) {
      throw new IllegalArgumentException("minGram must not be greater than maxGram");
    }

    this.minGram = minGram;
    this.maxGram = maxGram;
  }

  @Override
  public final boolean incrementToken() throws IOException {
    while (true) {
      if (curTermBuffer == null) {
        if (!input.incrementToken()) {
          return false;
        } else {
          curTermBuffer = termAtt.buffer().clone();
          curTermLength = termAtt.length();
          curCodePointCount = Character.codePointCount(termAtt, 0, termAtt.length());
          curGramSize = minGram;
          attributes = input.cloneAttributes();
          savePosIncr += posIncrAtt.getPositionIncrement();
        }
      }
      if (curGramSize <= maxGram) {         // if we have hit the end of our n-gram size range, quit
        if (curGramSize <= curCodePointCount) { // if the remaining input is too short, we can't generate any n-grams
          // grab gramSize chars from front or back
          clearAttributes();
          attributes.copyTo(this);
          // first ngram gets increment, others don't
          if (curGramSize == minGram) {
            posIncrAtt.setPositionIncrement(savePosIncr);
            savePosIncr = 0;
          } else {
            posIncrAtt.setPositionIncrement(0);
          }
          final int charLength = Character.offsetByCodePoints(curTermBuffer, 0, curTermLength, 0, curGramSize);
          termAtt.copyBuffer(curTermBuffer, 0, charLength);
          curGramSize++;
          return true;
        }
      }
      curTermBuffer = null;
    }
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    curTermBuffer = null;
    savePosIncr = 0;
  }
}
