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
package org.apache.lucene.spatial.search;

import org.apache.lucene.spatial.util.GeoUtils;

/** NOTE: package private; just used so {@link GeoPointInPolygonQuery} can communicate its bounding box to {@link GeoPointInBBoxQuery}. */
class GeoBoundingBox {
  /** minimum longitude value (in degrees) */
  public final double minLon;
  /** minimum latitude value (in degrees) */
  public final double maxLon;
  /** maximum longitude value (in degrees) */
  public final double minLat;
  /** maximum latitude value (in degrees) */
  public final double maxLat;

  /**
   * Constructs a bounding box by first validating the provided latitude and longitude coordinates
   */
  public GeoBoundingBox(double minLon, double maxLon, double minLat, double maxLat) {
    if (GeoUtils.isValidLon(minLon) == false) {
      throw new IllegalArgumentException("invalid minLon " + minLon);
    }
    if (GeoUtils.isValidLon(maxLon) == false) {
      throw new IllegalArgumentException("invalid maxLon " + minLon);
    }
    if (GeoUtils.isValidLat(minLat) == false) {
      throw new IllegalArgumentException("invalid minLat " + minLat);
    }
    if (GeoUtils.isValidLat(maxLat) == false) {
      throw new IllegalArgumentException("invalid maxLat " + minLat);
    }
    this.minLon = minLon;
    this.maxLon = maxLon;
    this.minLat = minLat;
    this.maxLat = maxLat;
  }
}
