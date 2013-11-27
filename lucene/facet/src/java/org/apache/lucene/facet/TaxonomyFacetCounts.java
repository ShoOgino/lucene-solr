package org.apache.lucene.facet;

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

import java.io.IOException;
import java.util.List;

import org.apache.lucene.facet.FacetsCollector.MatchingDocs;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.IntsRef;

/** Reads from any {@link OrdinalsReader}; use {@link
 *  FastTaxonomyFacetCounts} if you are just using the
 *  default encoding from {@link BinaryDocValues}.
 * 
 * @lucene.experimental */

// nocommit remove & add specialized Cached variation only?
public class TaxonomyFacetCounts extends IntTaxonomyFacets {
  private final OrdinalsReader ordinalsReader;

  /** Create {@code TaxonomyFacetCounts}, which also
   *  counts all facet labels.  Use this for a non-default
   *  {@link OrdinalsReader}; otherwise use {@link
   *  FastTaxonomyFacetCounts}. */
  public TaxonomyFacetCounts(OrdinalsReader ordinalsReader, TaxonomyReader taxoReader, FacetsConfig config, FacetsCollector fc) throws IOException {
    super(ordinalsReader.getIndexFieldName(), taxoReader, config);
    this.ordinalsReader = ordinalsReader;
    count(fc.getMatchingDocs());
  }

  private final void count(List<MatchingDocs> matchingDocs) throws IOException {
    IntsRef scratch  = new IntsRef();
    for(MatchingDocs hits : matchingDocs) {
      OrdinalsReader.OrdinalsSegmentReader ords = ordinalsReader.getReader(hits.context);
      FixedBitSet bits = hits.bits;
    
      final int length = hits.bits.length();
      int doc = 0;
      while (doc < length && (doc = bits.nextSetBit(doc)) != -1) {
        ords.get(doc, scratch);
        for(int i=0;i<scratch.length;i++) {
          values[scratch.ints[scratch.offset+i]]++;
        }
        ++doc;
      }
    }

    rollup();
  }
}
