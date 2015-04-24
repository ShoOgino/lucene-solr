package org.apache.lucene.spatial.spatial4j.geo3d;

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

/** An object for accumulating bounds information.
* The bounds object is initially empty.  Bounding points
* are then applied by supplying (x,y,z) tuples.  It is also
* possible to indicate the following edge cases:
* (1) No longitude bound possible
* (2) No upper latitude bound possible
* (3) No lower latitude bound possible
* When any of these have been applied, further application of
* points cannot override that decision.
 */
public class Bounds
{
    protected boolean noLongitudeBound = false;
    protected boolean noTopLatitudeBound = false;
    protected boolean noBottomLatitudeBound = false;

    protected Double minLatitude = null;
    protected Double maxLatitude = null;

    // For longitude bounds, this class needs to worry about keeping track of the distinction
    // between left-side bounds and right-side bounds.  Points are always submitted in pairs
    // which have a maximum longitude separation of Math.PI.  It's therefore always possible
    // to determine which point represents a left bound, and which point represents a right
    // bound.
    //
    // The next problem is how to compare two of the same kind of bound, e.g. two left bounds.
    // We need to keep track of the leftmost longitude of the shape, but since this is a circle,
    // this is arbitrary.  What we could try to do instead would be to find a pair of (left,right) bounds such
    // that:
    // (1) all other bounds are within, and
    // (2) the left minus right distance is minimized
    // Unfortunately, there are still shapes that cannot be summarized in this way correctly.
    // For example. consider a spiral that entirely circles the globe; we might arbitrarily choose
    // lat/lon bounds that do not in fact circle the globe.
    //
    // One way to handle the longitude issue correctly is therefore to stipulate that we
    // walk the bounds of the shape in some kind of connected order.  Each point or circle is therefore
    // added in a sequence.  We also need an interior point to make sure we have the right
    // choice of longitude bounds.  But even with this, we still can't always choose whether the actual shape
    // goes right or left.
    //
    // We can make the specification truly general by submitting the following in order:
    // addSide(PlaneSide side, Membership... constraints)
    // ...
    // This is unambiguous, but I still can't see yet how this would help compute the bounds.  The plane
    // solution would in general seem to boil down to the same logic that relies on points along the path
    // to define the shape boundaries.  I guess the one thing that you do know for a bounded edge is that
    // the endpoints are actually connected.  But it is not clear whether relationship helps in any way.
    //
    // In any case, if we specify shapes by a sequence of planes, we should stipulate that multiple sequences
    // are allowed, provided they progressively tile an area of the sphere that is connected and sequential.
    // For example, paths do alternating rectangles and circles, in sequence.  Each sequence member is
    // described by a sequence of planes.  I think it would also be reasonable to insist that the first segment
    // of a shape overlap or adjoin the previous shape.
    //
    // Here's a way to think about it that might help: Traversing every edge should grow the longitude bounds 
    // in the direction of the traversal.  So if the traversal is always known to be less than PI in total longitude
    // angle, then it is possible to use the endpoints to determine the unambiguous extension of the envelope.
    // For example, say you are currently at longitude -0.5.  The next point is at longitude PI-0.1.  You could say
    // that the difference in longitude going one way around would be beter than the distance the other way
    // around, and therefore the longitude envelope should be extended accordingly.  But in practice, when an
    // edge goes near a pole and may be inclined as well, the longer longitude change might be the right path, even
    // if the arc length is short.  So this too doesn't work.
    // 
    // Given we have a hard time making an exact match, here's the current proposal.  The proposal is a
    // heuristic, based on the idea that most areas are small compared to the circumference of the globe.
    // We keep track of the last point we saw, and take each point as it arrives, and compute its longitude.
    // Then, we have a choice as to which way to expand the envelope: we can expand by going to the left or
    // to the right.  We choose the direction with the least longitude difference.  (If we aren't sure,
    // and can recognize that, we can set "unconstrained in longitude".)
    
    protected Double leftLongitude = null;
    protected Double rightLongitude = null;
    
    public Bounds() {
    }

    public Double getMaxLatitude() {
        return maxLatitude;
    }
    
    public Double getMinLatitude() {
        return minLatitude;
    }
    
    public Double getLeftLongitude() {
        return leftLongitude;
    }
    
    public Double getRightLongitude() {
        return rightLongitude;
    }
    
    public boolean checkNoLongitudeBound() {
        return noLongitudeBound;
    }
    
    public boolean checkNoTopLatitudeBound() {
        return noTopLatitudeBound;
    }
    
    public boolean checkNoBottomLatitudeBound() {
        return noBottomLatitudeBound;
    }
    
    public Bounds addHorizontalCircle(double z) {
        if (!noTopLatitudeBound || !noBottomLatitudeBound) {
            // Compute a latitude value
            double latitude = Math.asin(z);
            addLatitudeBound(latitude);
        }
        return this;
    }

    public Bounds addLatitudeZone(double latitude) {
        if (!noTopLatitudeBound || !noBottomLatitudeBound) {
            addLatitudeBound(latitude);
        }
        return this;
    }

    public Bounds addLongitudeSlice(double newLeftLongitude, double newRightLongitude) {
        if (!noLongitudeBound) {
            addLongitudeBound(newLeftLongitude,newRightLongitude);
        }
        return this;
    }
    
    protected void addLatitudeBound(double latitude) {
        if (!noTopLatitudeBound && (maxLatitude == null || latitude > maxLatitude))
            maxLatitude = latitude;
        if (!noBottomLatitudeBound && (minLatitude == null || latitude < minLatitude))
            minLatitude = latitude;
    }
    
    protected void addLongitudeBound(double newLeftLongitude, double newRightLongitude) {
        if (leftLongitude == null && rightLongitude == null) {
            leftLongitude = newLeftLongitude;
            rightLongitude = newRightLongitude;
        } else {
            // Map the current range to something monotonically increasing
            double currentLeftLongitude = leftLongitude;
            double currentRightLongitude = rightLongitude;
            if (currentRightLongitude < currentLeftLongitude)
                currentRightLongitude += 2.0 * Math.PI;
            double adjustedLeftLongitude = newLeftLongitude;
            double adjustedRightLongitude = newRightLongitude;
            if (adjustedRightLongitude < adjustedLeftLongitude)
                adjustedRightLongitude += 2.0 * Math.PI;
            // Compare to see what the relationship is
            if (currentLeftLongitude <= adjustedLeftLongitude && currentRightLongitude >= adjustedRightLongitude) {
                // No adjustment needed.
            } else if (currentLeftLongitude >= adjustedLeftLongitude && currentRightLongitude <= adjustedRightLongitude) {
                // New longitude entirely contains old one
                leftLongitude = newLeftLongitude;
                rightLongitude = newRightLongitude;
            } else {
                if (currentLeftLongitude > adjustedLeftLongitude) {
                    // New left longitude needed
                    leftLongitude = newLeftLongitude;
                }
                if (currentRightLongitude < adjustedRightLongitude) {
                    // New right longitude needed
                    rightLongitude = newRightLongitude;
                }
            }
        }
        double testRightLongitude = rightLongitude;
        if (testRightLongitude < leftLongitude)
            testRightLongitude += Math.PI * 2.0;
        if (testRightLongitude - leftLongitude >= Math.PI * 2.0) {
            noLongitudeBound = true;
            leftLongitude = null;
            rightLongitude = null;
        }
    }
    
    protected void addLongitudeBound(double longitude) {
        // If this point is within the current bounds, we're done; otherwise
        // expand one side or the other.
        if (leftLongitude == null && rightLongitude == null) {
            leftLongitude = longitude;
            rightLongitude = longitude;
        } else {
            // Compute whether we're to the right of the left value.  But the left value may be greater than
            // the right value.
            double currentLeftLongitude = leftLongitude;
            double currentRightLongitude = rightLongitude;
            if (currentRightLongitude < currentLeftLongitude)
                currentRightLongitude += 2.0 * Math.PI;
            // We have a range to look at that's going in the right way.
            // Now, do the same trick with the computed longitude.
            if (longitude < currentLeftLongitude)
                longitude += 2.0 * Math.PI;
                
            if (longitude < currentLeftLongitude || longitude > currentRightLongitude) {
                // Outside of current bounds.  Consider carefully how we'll expand.
                double leftExtensionAmt;
                double rightExtensionAmt;
                if (longitude < currentLeftLongitude) {
                    leftExtensionAmt = currentLeftLongitude - longitude;
                } else {
                    leftExtensionAmt = currentLeftLongitude + 2.0 * Math.PI - longitude;
                }
                if (longitude > currentRightLongitude) {
                    rightExtensionAmt = longitude - currentRightLongitude;
                } else {
                    rightExtensionAmt = longitude + 2.0 * Math.PI - currentRightLongitude;
                }
                if (leftExtensionAmt < rightExtensionAmt) {
                    currentLeftLongitude = leftLongitude - leftExtensionAmt;
                    while (currentLeftLongitude <= -Math.PI) {
                        currentLeftLongitude += 2.0 * Math.PI;
                    }
                    leftLongitude = currentLeftLongitude;
                } else {
                    currentRightLongitude = rightLongitude + rightExtensionAmt;
                    while (currentRightLongitude > Math.PI) {
                        currentRightLongitude -= 2.0 * Math.PI;
                    }
                    rightLongitude = currentRightLongitude;
                }
            }
        }
        double testRightLongitude = rightLongitude;
        if (testRightLongitude < leftLongitude)
            testRightLongitude += Math.PI * 2.0;
        if (testRightLongitude - leftLongitude >= Math.PI * 2.0) {
            noLongitudeBound = true;
            leftLongitude = null;
            rightLongitude = null;
        }
    }
    
    public Bounds addPoint(Vector v) {
        return addPoint(v.x, v.y, v.z);
    }
    
    public Bounds addPoint(double x, double y, double z) {
        if (!noLongitudeBound) {
            // Get a longitude value
            double longitude = Math.atan2(y,x);
            //System.err.println(" add longitude bound at "+longitude * 180.0/Math.PI);
            addLongitudeBound(longitude);
        }
        if (!noTopLatitudeBound || !noBottomLatitudeBound) {
            // Compute a latitude value
            double latitude = Math.asin(z);
            addLatitudeBound(latitude);
        }
        return this;
    }

    public Bounds addPoint(double latitude, double longitude) {
        if (!noLongitudeBound) {
            // Get a longitude value
            addLongitudeBound(longitude);
        }
        if (!noTopLatitudeBound || !noBottomLatitudeBound) {
            // Compute a latitude value
            addLatitudeBound(latitude);
        }
        return this;
    }
    
    public Bounds noLongitudeBound() {
        noLongitudeBound = true;
        leftLongitude = null;
        rightLongitude = null;
        return this;
    }
    
    public Bounds noTopLatitudeBound() {
        noTopLatitudeBound = true;
        maxLatitude = null;
        return this;
    }
    
    public Bounds noBottomLatitudeBound() {
        noBottomLatitudeBound = true;
        minLatitude = null;
        return this;
    }
}
