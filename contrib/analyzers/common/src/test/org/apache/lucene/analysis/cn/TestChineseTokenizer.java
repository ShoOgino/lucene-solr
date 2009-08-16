package org.apache.lucene.analysis.cn;

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
import java.io.StringReader;

import junit.framework.TestCase;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;


public class TestChineseTokenizer extends TestCase
{
    public void testOtherLetterOffset() throws IOException
    {
        String s = "a天b";
        ChineseTokenizer tokenizer = new ChineseTokenizer(new StringReader(s));

        int correctStartOffset = 0;
        int correctEndOffset = 1;
        OffsetAttribute offsetAtt = (OffsetAttribute) tokenizer.getAttribute(OffsetAttribute.class);
        while (tokenizer.incrementToken()) {
          assertEquals(correctStartOffset, offsetAtt.startOffset());
          assertEquals(correctEndOffset, offsetAtt.endOffset());
          correctStartOffset++;
          correctEndOffset++;
        }
    }
    
    public void testReusableTokenStream() throws Exception
    {
      Analyzer a = new ChineseAnalyzer();
      assertAnalyzesToReuse(a, "中华人民共和国", 
        new String[] { "中", "华", "人", "民", "共", "和", "国" },
        new int[] { 0, 1, 2, 3, 4, 5, 6 },
        new int[] { 1, 2, 3, 4, 5, 6, 7 });
      assertAnalyzesToReuse(a, "北京市", 
        new String[] { "北", "京", "市" },
        new int[] { 0, 1, 2 },
        new int[] { 1, 2, 3 });
    }
    
    private void assertAnalyzesToReuse(Analyzer a, String input, String[] output,
      int startOffsets[], int endOffsets[])
      throws Exception {
      TokenStream ts = a.reusableTokenStream("dummy", new StringReader(input));
      TermAttribute termAtt = (TermAttribute) ts
        .getAttribute(TermAttribute.class);

      for (int i = 0; i < output.length; i++) {
        assertTrue(ts.incrementToken());
        assertEquals(output[i], termAtt.term());
      }

      assertFalse(ts.incrementToken());
    }
}
