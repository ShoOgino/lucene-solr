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

package org.apache.lucene.analysis.cn;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import junit.framework.TestCase;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

public class TestSmartChineseAnalyzer extends TestCase {
  
  public void testChineseStopWordsDefault() throws Exception {
    Analyzer ca = new SmartChineseAnalyzer(); /* will load stopwords */
    String sentence = "我购买了道具和服装。";
    String result[] = { "我", "购买", "了", "道具", "和", "服装" };
    assertAnalyzesTo(ca, sentence, result);
  }
  
  /*
   * This test is the same as the above, except with two phrases.
   * This tests to ensure the SentenceTokenizer->WordTokenFilter chain works correctly.
   */
  public void testChineseStopWordsDefaultTwoPhrases() throws Exception {
    Analyzer ca = new SmartChineseAnalyzer(); /* will load stopwords */
    String sentence = "我购买了道具和服装。 我购买了道具和服装。";
    String result[] = { "我", "购买", "了", "道具", "和", "服装", "我", "购买", "了", "道具", "和", "服装" };
    assertAnalyzesTo(ca, sentence, result);
  }
  
  /*
   * This test is the same as the above, except using an ideographic space as a separator.
   * This tests to ensure the stopwords are working correctly.
   */
  public void testChineseStopWordsDefaultTwoPhrasesIdeoSpache() throws Exception {
    Analyzer ca = new SmartChineseAnalyzer(); /* will load stopwords */
    String sentence = "我购买了道具和服装　我购买了道具和服装。";
    String result[] = { "我", "购买", "了", "道具", "和", "服装", "我", "购买", "了", "道具", "和", "服装" };
    assertAnalyzesTo(ca, sentence, result);
  }
  
  /*
   * Punctuation is handled in a strange way if you disable stopwords
   * In this example the IDEOGRAPHIC FULL STOP is converted into a comma.
   * if you don't supply (true) to the constructor, or use a different stopwords list,
   * then punctuation is indexed.
   */
  public void testChineseStopWordsOff() throws Exception {  
    Analyzer ca = new SmartChineseAnalyzer(false); /* doesnt load stopwords */
    String sentence = "我购买了道具和服装。";
    String result[] = { "我", "购买", "了", "道具", "和", "服装", "," };
    assertAnalyzesTo(ca, sentence, result);
  }
  
  public void testChineseAnalyzer() throws IOException {
    Token nt = new Token();
    Analyzer ca = new SmartChineseAnalyzer(true);
    Reader sentence = new StringReader("我购买了道具和服装。");
    String[] result = { "我", "购买", "了", "道具", "和", "服装" };
    TokenStream ts = ca.tokenStream("sentence", sentence);
    int i = 0;
    nt = ts.next(nt);
    while (nt != null) {
      assertEquals(result[i], nt.term());
      i++;
      nt = ts.next(nt);
    }
    ts.close();
  }
  
  /*
   * English words are lowercased and porter-stemmed.
   */
  public void testMixedLatinChinese() throws Exception {
    assertAnalyzesTo(new SmartChineseAnalyzer(true), "我购买 Tests 了道具和服装", 
        new String[] { "我", "购买", "test", "了", "道具", "和", "服装"});
  }
  
  public void testOffsets() throws Exception {
    assertAnalyzesTo(new SmartChineseAnalyzer(true), "我购买了道具和服装",
        new String[] { "我", "购买", "了", "道具", "和", "服装" },
        new int[] { 0, 1, 3, 4, 6, 7 },
        new int[] { 1, 3, 4, 6, 7, 9 });
  }
  
  public void assertAnalyzesTo(Analyzer a, String input, String[] output, int startOffsets[], int endOffsets[], String types[])
  throws Exception {

    TokenStream ts = a.tokenStream("dummy", new StringReader(input));
    TermAttribute termAtt = (TermAttribute) ts.getAttribute(TermAttribute.class);
    OffsetAttribute offsetAtt = (OffsetAttribute) ts.getAttribute(OffsetAttribute.class);
    TypeAttribute typeAtt = (TypeAttribute) ts.getAttribute(TypeAttribute.class);
    for (int i = 0; i < output.length; i++) {
      assertTrue(ts.incrementToken());
      assertEquals(termAtt.term(), output[i]);
      if (startOffsets != null)
        assertEquals(offsetAtt.startOffset(), startOffsets[i]);
      if (endOffsets != null)
        assertEquals(offsetAtt.endOffset(), endOffsets[i]);
      if (types != null)
        assertEquals(typeAtt.type(), types[i]);
    }
    assertFalse(ts.incrementToken());
    ts.close();
  }

public void assertAnalyzesTo(Analyzer a, String input, String[] output) throws Exception {
  assertAnalyzesTo(a, input, output, null, null, null);
}

public void assertAnalyzesTo(Analyzer a, String input, String[] output, String[] types) throws Exception {
  assertAnalyzesTo(a, input, output, null, null, types);
}

public void assertAnalyzesTo(Analyzer a, String input, String[] output, int startOffsets[], int endOffsets[]) throws Exception {
  assertAnalyzesTo(a, input, output, startOffsets, endOffsets, null);
}


  /**
   * @param args
   * @throws IOException
   */
  public static void main(String[] args) throws IOException {
    new TestSmartChineseAnalyzer().sampleMethod();
  }

  /**
   * @throws UnsupportedEncodingException
   * @throws FileNotFoundException
   * @throws IOException
   */
  private void sampleMethod() throws UnsupportedEncodingException,
      FileNotFoundException, IOException {
    Token nt = new Token();
    Analyzer ca = new SmartChineseAnalyzer(true);
    Reader sentence = new StringReader(
        "我从小就不由自主地认为自己长大以后一定得成为一个象我父亲一样的画家, 可能是父母潜移默化的影响。其实我根本不知道作为画家意味着什么，我是否喜欢，最重要的是否适合我，我是否有这个才华。其实人到中年的我还是不确定我最喜欢什么，最想做的是什么？我相信很多人和我一样有同样的烦恼。毕竟不是每个人都能成为作文里的宇航员，科学家和大教授。知道自己适合做什么，喜欢做什么，能做好什么其实是个非常困难的问题。"
            + "幸运的是，我想我的孩子不会为这个太过烦恼。通过老大，我慢慢发现美国高中的一个重要功能就是帮助学生分析他们的专长和兴趣，从而帮助他们选择大学的专业和未来的职业。我觉得帮助一个未成形的孩子找到她未来成长的方向是个非常重要的过程。"
            + "美国高中都有专门的职业顾问，通过接触不同的课程，和各种心理，个性，兴趣很多方面的问答来帮助每个学生找到最感兴趣的专业。这样的教育一般是要到高年级才开始， 可老大因为今年上计算机的课程就是研究一个职业走向的软件项目，所以她提前做了这些考试和面试。看来以后这样的教育会慢慢由电脑来测试了。老大带回家了一些试卷，我挑出一些给大家看看。这门课她花了2个多月才做完，这里只是很小的一部分。"
            + "在测试里有这样的一些问题："
            + "你是个喜欢动手的人吗？ 你喜欢修东西吗？你喜欢体育运动吗？你喜欢在室外工作吗？你是个喜欢思考的人吗？你喜欢数学和科学课吗？你喜欢一个人工作吗？你对自己的智力自信吗？你的创造能力很强吗？你喜欢艺术，音乐和戏剧吗？  你喜欢自由自在的工作环境吗？你喜欢尝试新的东西吗？ 你喜欢帮助别人吗？你喜欢教别人吗？你喜欢和机器和工具打交道吗？你喜欢当领导吗？你喜欢组织活动吗？你什么和数字打交道吗？");
    TokenStream ts = ca.tokenStream("sentence", sentence);

    System.out.println("start: " + (new Date()));
    long before = System.currentTimeMillis();
    nt = ts.next(nt);
    while (nt != null) {
      System.out.println(nt.term());
      nt = ts.next(nt);
    }
    ts.close();
    long now = System.currentTimeMillis();
    System.out.println("time: " + (now - before) / 1000.0 + " s");
  }
}
