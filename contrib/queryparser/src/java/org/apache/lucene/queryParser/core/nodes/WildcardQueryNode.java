package org.apache.lucene.queryParser.core.nodes;

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

import org.apache.lucene.queryParser.core.parser.EscapeQuerySyntax;
import org.apache.lucene.search.MultiTermQuery;

/**
 * A {@link WildcardQueryNode} represents wildcard query This does not apply to
 * phrases. Examples: a*b*c Fl?w? m?ke*g
 */
public class WildcardQueryNode extends FieldQueryNode {
  private static final long serialVersionUID = 0L;
  private MultiTermQuery.RewriteMethod multiTermRewriteMethod;

  /**
   * @param field
   *          - field name
   * @param text
   *          - value that contains one or more wild card characters (? or *)
   * @param begin
   *          - position in the query string
   * @param end
   *          - position in the query string
   */
  public WildcardQueryNode(CharSequence field, CharSequence text, int begin,
      int end) {
    super(field, text, begin, end);
  }

  public CharSequence toQueryString(EscapeQuerySyntax escaper) {
    if (isDefaultField(this.field)) {
      return getTermEscaped(escaper);
    } else {
      return this.field + ":" + getTermEscaped(escaper);
    }
  }

  public String toString() {
    return "<wildcard field='" + this.field + "' term='" + this.text + "'/>";
  }

  public WildcardQueryNode cloneTree() throws CloneNotSupportedException {
    WildcardQueryNode clone = (WildcardQueryNode) super.cloneTree();

    // nothing to do here

    return clone;
  }

  /**
   * @return the rewrite method
   */
  public MultiTermQuery.RewriteMethod getMultiTermRewriteMethod() {
    return multiTermRewriteMethod;
  }

  public void setMultiTermRewriteMethod(MultiTermQuery.RewriteMethod method) {
    multiTermRewriteMethod = method;
  }
}
