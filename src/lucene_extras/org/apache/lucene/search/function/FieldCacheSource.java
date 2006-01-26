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

package org.apache.lucene.search.function;

import org.apache.lucene.search.FieldCache;

/**
 * A base class for ValueSource implementations that retrieve values for
 * a single field from the {@link org.apache.lucene.search.FieldCache}.
 *
 * @author yonik
 * @version $Id: FieldCacheSource.java,v 1.1 2005/11/22 05:23:20 yonik Exp $
 */
public abstract class FieldCacheSource extends ValueSource {
  protected String field;
  protected FieldCache cache = FieldCache.DEFAULT;

  public FieldCacheSource(String field) {
    this.field=field;
  }

  public void setFieldCache(FieldCache cache) {
    this.cache = cache;
  }

  public FieldCache getFieldCache() {
    return cache;
  }

  public String description() {
    return field;
  }

  public boolean equals(Object o) {
    if (!(o instanceof FieldCacheSource)) return false;
    FieldCacheSource other = (FieldCacheSource)o;
    return this.field.equals(other.field)
           && this.cache == other.cache;
  }

  public int hashCode() {
    return cache.hashCode() + field.hashCode();
  };

}
