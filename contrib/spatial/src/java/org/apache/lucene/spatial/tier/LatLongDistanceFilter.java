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

package org.apache.lucene.spatial.tier;

import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.spatial.tier.DistanceHandler.Precision;




public class LatLongDistanceFilter extends DistanceFilter {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  
  double distance;
  double lat;
  double lng;
  String latField;
  String lngField;
  Logger log = Logger.getLogger(getClass().getName());

  int nextOffset = 0;
  
  Map<Integer,Double> distances = null;
  private Precision precise = null;
  
  /**
   * Provide a distance filter based from a center point with a radius
   * in miles
   * @param lat
   * @param lng
   * @param miles
   * @param latField
   * @param lngField
   */
  public LatLongDistanceFilter(double lat, double lng, double miles, String latField, String lngField){
    distance = miles;
    this.lat = lat;
    this.lng = lng;
    this.latField = latField;
    this.lngField = lngField;
  }
  
  
  public Map<Integer,Double> getDistances(){
    return distances;
  }
  
  public Double getDistance(int docid){
    return distances.get(docid);
  }
  
  @Override
  public BitSet bits(IndexReader reader) throws IOException {

    /* Create a BitSet to store the result */
    int maxdocs = reader.maxDoc();
    BitSet bits = new BitSet(maxdocs);
    
    setPrecision(maxdocs);
    // create an intermediate cache to avoid recomputing
    //   distances for the same point 
    //   TODO: Why is this a WeakHashMap? 
    WeakHashMap<String,Double> cdistance = new WeakHashMap<String,Double>(maxdocs);
    long start = System.currentTimeMillis();
    double[] latIndex = FieldCache.DEFAULT.getDoubles(reader, latField);
    double[] lngIndex = FieldCache.DEFAULT.getDoubles(reader, lngField);

    /* store calculated distances for reuse by other components */
    distances = new HashMap<Integer,Double>(maxdocs);
    
    if (distances == null){
    	distances = new HashMap<Integer,Double>();
    }

    TermDocs td = reader.termDocs(null);
    while(td.next()) {
      int doc = td.doc();
      
      double x = latIndex[doc];
      double y = lngIndex[doc];
      
      // round off lat / longs if necessary
//      x = DistanceHandler.getPrecision(x, precise);
//      y = DistanceHandler.getPrecision(y, precise);
      
      String ck = new Double(x).toString()+","+new Double(y).toString();
      Double cachedDistance = cdistance.get(ck);
      
      
      double d;
      
      if(cachedDistance != null){
        d = cachedDistance.doubleValue();
      } else {
        d = DistanceUtils.getInstance().getDistanceMi(lat, lng, x, y);
        cdistance.put(ck, d);
      }
      
   // why was i storing all distances again?
      if (d < distance){
        bits.set(doc);
        distances.put(doc+ nextOffset, d); // include nextOffset for multi segment reader  
      }
    }
    int size = bits.cardinality();
    nextOffset += reader.maxDoc();  // this should be something that's part of indexReader
    long end = System.currentTimeMillis();
    log.fine("Bits 1: Time taken : "+ (end - start) + 
            ", results : "+ distances.size() + 
            ", cached : "+ cdistance.size() +
            ", incoming size: "+ size+
            ", nextOffset: "+ nextOffset);
    
    return bits;
  }

  
  @Override
  public BitSet bits(IndexReader reader, BitSet bits) throws Exception {

  
    /* Create a BitSet to store the result */
  	
    int size = bits.cardinality();
    BitSet result = new BitSet(size);
    
    
    /* create an intermediate cache to avoid recomputing
         distances for the same point  */
    HashMap<String,Double> cdistance = new HashMap<String,Double>(size);
    

    
    if (distances == null){
    	distances = new HashMap<Integer,Double>();
    }
    
    long start = System.currentTimeMillis();
    double[] latIndex = FieldCache.DEFAULT.getDoubles(reader, latField);
    double[] lngIndex = FieldCache.DEFAULT.getDoubles(reader, lngField);
    
    /* loop over all set bits (hits from the boundary box filters) */
    int i = bits.nextSetBit(0);
    while (i >= 0){

      if (reader.isDeleted(i)) {
        i = bits.nextSetBit(i+1);
        continue;
      }

      double x,y;
      
      // if we have a completed
      // filter chain, lat / lngs can be retrived from 
      // memory rather than document base.

      x = latIndex[i];
      y = lngIndex[i];
      
      // round off lat / longs if necessary
//      x = DistanceHandler.getPrecision(x, precise);
//      y = DistanceHandler.getPrecision(y, precise);

      String ck = new Double(x).toString()+","+new Double(y).toString();
      Double cachedDistance = cdistance.get(ck);
      double d;
      
      if(cachedDistance != null){
        d = cachedDistance.doubleValue();
        
      } else {
        d = DistanceUtils.getInstance().getDistanceMi(lat, lng, x, y);
        //d = DistanceUtils.getLLMDistance(lat, lng, x, y);
        cdistance.put(ck, d);
      }
      
      // why was i storing all distances again?
      if (d < distance){
        result.set(i);
        int did = i + nextOffset;
        distances.put(did, d); // include nextOffset for multi segment reader  
        
      }
      i = bits.nextSetBit(i+1);
    }
    
    long end = System.currentTimeMillis();
    nextOffset += reader.maxDoc();  // this should be something that's part of indexReader
    log.fine("Time taken : "+ (end - start) + 
        ", results : "+ distances.size() + 
        ", cached : "+ cdistance.size() +
        ", incoming size: "+ size+
        ", nextOffset: "+ nextOffset);
  

    cdistance = null;
    
    
    return result;
  }

  /** Returns true if <code>o</code> is equal to this. */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LatLongDistanceFilter)) return false;
    LatLongDistanceFilter other = (LatLongDistanceFilter) o;

    if (this.distance != other.distance ||
        this.lat != other.lat ||
        this.lng != other.lng ||
        !this.latField.equals(other.latField) ||
        !this.lngField.equals(other.lngField)) {
      return false;
    }
    return true;
  }

  /** Returns a hash code value for this object.*/
  @Override
  public int hashCode() {
    int h = new Double(distance).hashCode();
    h ^= new Double(lat).hashCode();
    h ^= new Double(lng).hashCode();
    h ^= latField.hashCode();
    h ^= lngField.hashCode();
    return h;
  }
  


  public void setDistances(Map<Integer, Double> distances) {
    this.distances = distances;
  }

  void setPrecision(int maxDocs) {
    precise = Precision.EXACT;
    
    if (maxDocs > 1000 && distance > 10) {
      precise = Precision.TWENTYFEET;
    }
    
    if (maxDocs > 10000 && distance > 10){
      precise = Precision.TWOHUNDREDFEET;
    }
  }
}
