package org.apache.lucene.facet.search;

import org.apache.lucene.facet.params.FacetIndexingParams;
import org.apache.lucene.facet.taxonomy.FacetLabel;

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

/**
 * A {@link FacetRequest} for weighting facets by summing the scores of matching
 * documents.
 * 
 * @lucene.experimental
 */
public class SumScoreFacetRequest extends FacetRequest {

  /** Create a score facet request for a given node in the taxonomy. */
  public SumScoreFacetRequest(FacetLabel path, int num) {
    super(path, num);
  }

  @Override
  public FacetsAggregator createFacetsAggregator(FacetIndexingParams fip) {
    return new SumScoreFacetsAggregator();
  }
  
}
