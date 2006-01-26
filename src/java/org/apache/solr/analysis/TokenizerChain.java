/**
 * Copyright 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.solr.analysis.TokenizerFactory;

import java.io.Reader;

/**
 * @author yonik
 * @version $Id: TokenizerChain.java,v 1.3 2005/08/26 05:21:08 yonik Exp $
 */

//
// An analyzer that uses a tokenizer and a list of token filters to
// create a TokenStream.
//
public class TokenizerChain extends Analyzer {
  final private TokenizerFactory tokenizer;
  final private TokenFilterFactory[] filters;

  public TokenizerChain(TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
    this.tokenizer = tokenizer;
    this.filters = filters;
  }

  public TokenizerFactory getTokenizerFactory() { return tokenizer; }
  public TokenFilterFactory[] getTokenFilterFactories() { return filters; }

  public TokenStream tokenStream(String fieldName, Reader reader) {
    TokenStream ts = tokenizer.create(reader);
    for (int i=0; i<filters.length; i++) {
      ts = filters[i].create(ts);
    }
    return ts;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("TokenizerChain(");
    sb.append(tokenizer);
    for (TokenFilterFactory filter: filters) {
      sb.append(", ");
      sb.append(filter);
    }
    sb.append(')');
    return sb.toString();
  }

}
