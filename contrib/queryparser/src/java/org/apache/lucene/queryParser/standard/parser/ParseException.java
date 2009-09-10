/* Generated By:JavaCC: Do not edit this line. ParseException.java Version 4.1 */
/* JavaCCOptions:KEEP_LINE_COL=null */
package org.apache.lucene.queryParser.standard.parser;

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
import org.apache.lucene.messages.Message;
import org.apache.lucene.messages.MessageImpl;
import org.apache.lucene.queryParser.core.QueryNodeParseException;
import org.apache.lucene.queryParser.core.messages.QueryParserMessages;

/**
 * This exception is thrown when parse errors are encountered. You can
 * explicitly create objects of this exception type by calling the method
 * generateParseException in the generated parser.
 * 
 * You can modify this class to customize your error reporting mechanisms so
 * long as you retain the public fields.
 */
public class ParseException extends QueryNodeParseException {

  /**
   * The version identifier for this Serializable class. Increment only if the
   * <i>serialized</i> form of the class changes.
   */
  private static final long serialVersionUID = 1L;

  /**
   * This constructor is used by the method "generateParseException" in the
   * generated parser. Calling this constructor generates a new object of this
   * type with the fields "currentToken", "expectedTokenSequences", and
   * "tokenImage" set.
   */
  public ParseException(Token currentTokenVal,
      int[][] expectedTokenSequencesVal, String[] tokenImageVal) {
    super(new MessageImpl(QueryParserMessages.INVALID_SYNTAX, new Object[]{initialise(
        currentTokenVal, expectedTokenSequencesVal, tokenImageVal)}));
    this.currentToken = currentTokenVal;
    this.expectedTokenSequences = expectedTokenSequencesVal;
    this.tokenImage = tokenImageVal;
  }

  public ParseException(Message message) {
    super(message);
  }

  public ParseException() {
    super(new MessageImpl(QueryParserMessages.INVALID_SYNTAX, new Object[]{"Error"}));
  }

  /**
   * This is the last token that has been consumed successfully. If this object
   * has been created due to a parse error, the token followng this token will
   * (therefore) be the first error token.
   */
  @SuppressWarnings("unused")
  private Token currentToken;

  /**
   * Each entry in this array is an array of integers. Each array of integers
   * represents a sequence of tokens (by their ordinal values) that is expected
   * at this point of the parse.
   */
  @SuppressWarnings("unused")
  private int[][] expectedTokenSequences;

  /**
   * This is a reference to the "tokenImage" array of the generated parser
   * within which the parse error occurred. This array is defined in the
   * generated ...Constants interface.
   */
  @SuppressWarnings("unused")
  private String[] tokenImage;

  /**
   * It uses "currentToken" and "expectedTokenSequences" to generate a parse
   * error message and returns it. If this object has been created due to a
   * parse error, and you do not catch it (it gets thrown from the parser) the
   * correct error message gets displayed.
   */
  private static String initialise(Token currentToken,
      int[][] expectedTokenSequences, String[] tokenImage) {
    String eol = System.getProperty("line.separator", "\n");
    StringBuffer expected = new StringBuffer();
    int maxSize = 0;
    for (int i = 0; i < expectedTokenSequences.length; i++) {
      if (maxSize < expectedTokenSequences[i].length) {
        maxSize = expectedTokenSequences[i].length;
      }
      for (int j = 0; j < expectedTokenSequences[i].length; j++) {
        expected.append(tokenImage[expectedTokenSequences[i][j]]).append(' ');
      }
      if (expectedTokenSequences[i][expectedTokenSequences[i].length - 1] != 0) {
        expected.append("...");
      }
      expected.append(eol).append("    ");
    }
    String retval = "Encountered \"";
    Token tok = currentToken.next;
    for (int i = 0; i < maxSize; i++) {
      if (i != 0)
        retval += " ";
      if (tok.kind == 0) {
        retval += tokenImage[0];
        break;
      }
      retval += " " + tokenImage[tok.kind];
      retval += " \"";
      retval += add_escapes(tok.image);
      retval += " \"";
      tok = tok.next;
    }
    retval += "\" at line " + currentToken.next.beginLine + ", column "
        + currentToken.next.beginColumn;
    retval += "." + eol;
    if (expectedTokenSequences.length == 1) {
      retval += "Was expecting:" + eol + "    ";
    } else {
      retval += "Was expecting one of:" + eol + "    ";
    }
    retval += expected.toString();
    return retval;
  }

  /**
   * The end of line string for this machine.
   */
  @SuppressWarnings("unused")
  private String eol = System.getProperty("line.separator", "\n");

  /**
   * Used to convert raw characters to their escaped version when these raw
   * version cannot be used as part of an ASCII string literal.
   */
  static private String add_escapes(String str) {
    StringBuffer retval = new StringBuffer();
    char ch;
    for (int i = 0; i < str.length(); i++) {
      switch (str.charAt(i)) {
      case 0:
        continue;
      case '\b':
        retval.append("\\b");
        continue;
      case '\t':
        retval.append("\\t");
        continue;
      case '\n':
        retval.append("\\n");
        continue;
      case '\f':
        retval.append("\\f");
        continue;
      case '\r':
        retval.append("\\r");
        continue;
      case '\"':
        retval.append("\\\"");
        continue;
      case '\'':
        retval.append("\\\'");
        continue;
      case '\\':
        retval.append("\\\\");
        continue;
      default:
        if ((ch = str.charAt(i)) < 0x20 || ch > 0x7e) {
          String s = "0000" + Integer.toString(ch, 16);
          retval.append("\\u" + s.substring(s.length() - 4, s.length()));
        } else {
          retval.append(ch);
        }
        continue;
      }
    }
    return retval.toString();
  }

}
/*
 * JavaCC - StandardChecksum=c04ac45b94787832e67e6d1b49d8774c (do not edit this
 * line)
 */
