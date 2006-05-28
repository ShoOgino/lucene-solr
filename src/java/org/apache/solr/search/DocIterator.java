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

package org.apache.solr.search;

import java.util.Iterator;

/**
 * Simple Iterator of document Ids which may include score information.
 *
 * <p>
 * The order of the documents is determined by the context in which the
 * DocIterator instance was retrieved.
 * </p>
 *
 * @author yonik
 * @version $Id$
 */
public interface DocIterator extends Iterator<Integer> {
  // allready declared in superclass, redeclaring prevents javadoc inheritence
  //public boolean hasNext();

  /**
   * Returns the next document id if hasNext()==true
   *
   * <code>
   * This method is functionally equivilent to <code>next()</code>
   * @see #next()
   */
  public int nextDoc();

  /**
   * Returns the score for the document just returned by <code>nextDoc()</code>
   *
   * <p>
   * The value returned may be meaningless depending on the context
   * in which the DocIterator instance was retrieved.
   */
  public float score();
}
