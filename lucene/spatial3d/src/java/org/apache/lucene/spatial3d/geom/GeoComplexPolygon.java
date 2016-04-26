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
package org.apache.lucene.spatial3d.geom;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * GeoComplexPolygon objects are structures designed to handle very large numbers of edges.
 * They perform very well in this case compared to the alternatives, which all have O(N) evaluation
 * and O(N^2) setup times.  Complex polygons have O(N) setup times and best case O(log(N))
 * evaluation times.
 *
 * The tradeoff is that these objects perform object creation when evaluating intersects() and
 * isWithin().
 *
 * @lucene.internal
 */
class GeoComplexPolygon extends GeoBasePolygon {
  
  private final XTree xTree = new XTree();
  private final YTree yTree = new YTree();
  private final ZTree zTree = new ZTree();
  
  private final boolean testPointInSet;
  private final GeoPoint testPoint;
  
  private final Plane testPointXZPlane;
  private final Plane testPointXZAbovePlane;
  private final Plane testPointXZBelowPlane;
  private final Plane testPointYZPlane;
  private final Plane testPointYZAbovePlane;
  private final Plane testPointYZBelowPlane;
  private final Plane testPointXYPlane;
  private final Plane testPointXYAbovePlane;
  private final Plane testPointXYBelowPlane;
  
  private final GeoPoint[] edgePoints;
  private final Edge[] shapeStartEdges;
  
  /**
   * Create a complex polygon from multiple lists of points, and a single point which is known to be in or out of
   * set.
   *@param planetModel is the planet model.
   *@param pointsList is the list of lists of edge points.  The edge points describe edges, and have an implied
   *  return boundary, so that N edges require N points.  These points have furthermore been filtered so that
   *  no adjacent points are identical (within the bounds of the definition used by this package).  It is assumed
   *  that no edges intersect, but the structure can contain both outer rings as well as holes.
   *@param testPoint is the point whose in/out of setness is known.
   *@param testPointInSet is true if the test point is considered "within" the polygon.
   */
  public GeoComplexPolygon(final PlanetModel planetModel, final List<List<GeoPoint>> pointsList, final GeoPoint testPoint, final boolean testPointInSet) {
    super(planetModel);
    this.testPointInSet = testPointInSet;
    this.testPoint = testPoint;
    
    this.testPointXZPlane = new Plane(0.0, 1.0, 0.0, -testPoint.y);
    this.testPointYZPlane = new Plane(1.0, 0.0, 0.0, -testPoint.x);
    this.testPointXYPlane = new Plane(0.0, 0.0, 1.0, -testPoint.z);
    
    this.testPointXZAbovePlane = new Plane(testPointXZPlane, true);
    this.testPointXZBelowPlane = new Plane(testPointXZPlane, false);
    this.testPointYZAbovePlane = new Plane(testPointYZPlane, true);
    this.testPointYZBelowPlane = new Plane(testPointYZPlane, false);
    this.testPointXYAbovePlane = new Plane(testPointXYPlane, true);
    this.testPointXYBelowPlane = new Plane(testPointXYPlane, false);

    this.edgePoints = new GeoPoint[pointsList.size()];
    this.shapeStartEdges = new Edge[pointsList.size()];
    int edgePointIndex = 0;
    for (final List<GeoPoint> shapePoints : pointsList) {
      GeoPoint lastGeoPoint = shapePoints.get(shapePoints.size()-1);
      edgePoints[edgePointIndex] = lastGeoPoint;
      Edge lastEdge = null;
      Edge firstEdge = null;
      for (final GeoPoint thisGeoPoint : shapePoints) {
        final Edge edge = new Edge(planetModel, lastGeoPoint, thisGeoPoint);
        xTree.add(edge);
        yTree.add(edge);
        zTree.add(edge);
        // Now, link
        if (firstEdge == null) {
          firstEdge = edge;
        }
        if (lastEdge != null) {
          lastEdge.next = edge;
          edge.previous = lastEdge;
        }
        lastEdge = edge;
        lastGeoPoint = thisGeoPoint;
      }
      firstEdge.previous = lastEdge;
      lastEdge.next = firstEdge;
      shapeStartEdges[edgePointIndex] = firstEdge;
      edgePointIndex++;
    }
  }

  @Override
  public boolean isWithin(final double x, final double y, final double z) {
    return isWithin(new Vector(x, y, z));
  }
  
  @Override
  public boolean isWithin(final Vector thePoint) {
    // If we're right on top of the point, we know the answer.
    if (testPoint.isNumericallyIdentical(thePoint)) {
      return testPointInSet;
    }
    
    // Choose our navigation route!
    final double xDelta = Math.abs(thePoint.x - testPoint.x);
    final double yDelta = Math.abs(thePoint.y - testPoint.y);
    final double zDelta = Math.abs(thePoint.z - testPoint.z);
    
    // If we're right on top of any of the test planes, we navigate solely on that plane.
    if (testPointXZPlane.evaluateIsZero(thePoint)) {
      // Use the XZ plane exclusively.
      final LinearCrossingEdgeIterator crossingEdgeIterator = new LinearCrossingEdgeIterator(testPointXZPlane, testPointXZAbovePlane, testPointXZBelowPlane, testPoint, thePoint);
      // Traverse our way from the test point to the check point.  Use the y tree because that's fixed.
      if (!yTree.traverse(crossingEdgeIterator, testPoint.y, testPoint.y)) {
        // Endpoint is on edge
        return true;
      }
      return ((crossingEdgeIterator.crossingCount & 1) == 0)?testPointInSet:!testPointInSet;
    } else if (testPointYZPlane.evaluateIsZero(thePoint)) {
      // Use the YZ plane exclusively.
      final LinearCrossingEdgeIterator crossingEdgeIterator = new LinearCrossingEdgeIterator(testPointYZPlane, testPointYZAbovePlane, testPointYZBelowPlane, testPoint, thePoint);
      // Traverse our way from the test point to the check point.  Use the x tree because that's fixed.
      if (!xTree.traverse(crossingEdgeIterator, testPoint.x, testPoint.x)) {
        // Endpoint is on edge
        return true;
      }
      return ((crossingEdgeIterator.crossingCount & 1) == 0)?testPointInSet:!testPointInSet;
    } else if (testPointXYPlane.evaluateIsZero(thePoint)) {
      // Use the XY plane exclusively.
      final LinearCrossingEdgeIterator crossingEdgeIterator = new LinearCrossingEdgeIterator(testPointXYPlane, testPointXYAbovePlane, testPointXYBelowPlane, testPoint, thePoint);
      // Traverse our way from the test point to the check point.  Use the z tree because that's fixed.
      if (!zTree.traverse(crossingEdgeIterator, testPoint.z, testPoint.z)) {
        // Endpoint is on edge
        return true;
      }
      return ((crossingEdgeIterator.crossingCount & 1) == 0)?testPointInSet:!testPointInSet;
    } else {
      
      // We need to use two planes to get there.  We can use any two planes, and order doesn't matter.
      // The best to pick are the ones with the shortest overall distance.
      if (xDelta + yDelta <= xDelta + zDelta && xDelta + yDelta <= yDelta + zDelta) {
        // Travel in X and Y
        // We'll do this using the testPointYZPlane, and create a travel plane for the right XZ plane.
        final Plane travelPlane = new Plane(0.0, 1.0, 0.0, -thePoint.y);
        final DualCrossingEdgeIterator edgeIterator = new DualCrossingEdgeIterator(testPointYZPlane, testPointYZAbovePlane, testPointYZBelowPlane, travelPlane, testPoint, thePoint);
        if (!xTree.traverse(edgeIterator, testPoint.x, testPoint.x)) {
          return true;
        }
        edgeIterator.setSecondLeg();
        if (!yTree.traverse(edgeIterator, thePoint.y, thePoint.y)) {
          return true;
        }
        return ((edgeIterator.crossingCount  & 1) == 0)?testPointInSet:!testPointInSet;
      } else if (xDelta + zDelta <= xDelta + yDelta && xDelta + zDelta <= zDelta + yDelta) {
        // Travel in X and Z
        // We'll do this using the testPointXYPlane, and create a travel plane for the right YZ plane.
        final Plane travelPlane = new Plane(1.0, 0.0, 0.0, -thePoint.x);
        final DualCrossingEdgeIterator edgeIterator = new DualCrossingEdgeIterator(testPointXYPlane, testPointXYAbovePlane, testPointXYBelowPlane, travelPlane, testPoint, thePoint);
        if (!zTree.traverse(edgeIterator, testPoint.z, testPoint.z)) {
          return true;
        }
        edgeIterator.setSecondLeg();
        if (!xTree.traverse(edgeIterator, thePoint.x, thePoint.x)) {
          return true;
        }
        return ((edgeIterator.crossingCount & 1) == 0)?testPointInSet:!testPointInSet;
      } else if (yDelta + zDelta <= xDelta + yDelta && yDelta + zDelta <= xDelta + zDelta) {
        // Travel in Y and Z
        // We'll do this using the testPointXZPlane, and create a travel plane for the right XY plane.
        final Plane travelPlane = new Plane(0.0, 0.0, 1.0, -thePoint.z);
        final DualCrossingEdgeIterator edgeIterator = new DualCrossingEdgeIterator(testPointXZPlane, testPointXZAbovePlane, testPointXZBelowPlane, travelPlane, testPoint, thePoint);
        if (!yTree.traverse(edgeIterator, testPoint.y, testPoint.y)) {
          return true;
        }
        edgeIterator.setSecondLeg();
        if (!zTree.traverse(edgeIterator, thePoint.z, thePoint.z)) {
          return true;
        }
        return ((edgeIterator.crossingCount & 1) == 0)?testPointInSet:!testPointInSet;
      }
    }
    return false;
  }
  
  @Override
  public GeoPoint[] getEdgePoints() {
    return edgePoints;
  }

  @Override
  public boolean intersects(final Plane p, final GeoPoint[] notablePoints, final Membership... bounds) {
    // Create the intersector
    final EdgeIterator intersector = new IntersectorEdgeIterator(p, notablePoints, bounds);
    // First, compute the bounds for the the plane
    final XYZBounds xyzBounds = new XYZBounds();
    p.recordBounds(planetModel, xyzBounds, bounds);
    // Figure out which tree likely works best
    final double xDelta = xyzBounds.getMaximumX() - xyzBounds.getMinimumX();
    final double yDelta = xyzBounds.getMaximumY() - xyzBounds.getMinimumY();
    final double zDelta = xyzBounds.getMaximumZ() - xyzBounds.getMinimumZ();
    // Select the smallest range
    if (xDelta <= yDelta && xDelta <= zDelta) {
      // Drill down in x
      return !xTree.traverse(intersector, xyzBounds.getMinimumX(), xyzBounds.getMaximumX());
    } else if (yDelta <= xDelta && yDelta <= zDelta) {
      // Drill down in y
      return !yTree.traverse(intersector, xyzBounds.getMinimumY(), xyzBounds.getMaximumY());
    } else if (zDelta <= xDelta && zDelta <= yDelta) {
      // Drill down in z
      return !zTree.traverse(intersector, xyzBounds.getMinimumZ(), xyzBounds.getMaximumZ());
    }
    return true;
  }


  @Override
  public void getBounds(Bounds bounds) {
    super.getBounds(bounds);
    for (final Edge startEdge : shapeStartEdges) {
      Edge currentEdge = startEdge;
      while (true) {
        bounds.addPlane(this.planetModel, currentEdge.plane, currentEdge.startPlane, currentEdge.endPlane);
        currentEdge = currentEdge.next;
        if (currentEdge == startEdge) {
          break;
        }
      }
    }
  }

  @Override
  protected double outsideDistance(final DistanceStyle distanceStyle, final double x, final double y, final double z) {
    // MHL
    return 0.0;
  }

  /**
   * An instance of this class describes a single edge, and includes what is necessary to reliably determine intersection
   * in the context of the even/odd algorithm used.
   */
  private static class Edge {
    public final GeoPoint startPoint;
    public final GeoPoint endPoint;
    public final GeoPoint[] notablePoints;
    public final SidedPlane startPlane;
    public final SidedPlane endPlane;
    public final Plane plane;
    public final XYZBounds planeBounds;
    public Edge previous = null;
    public Edge next = null;
    
    public Edge(final PlanetModel pm, final GeoPoint startPoint, final GeoPoint endPoint) {
      this.startPoint = startPoint;
      this.endPoint = endPoint;
      this.notablePoints = new GeoPoint[]{startPoint, endPoint};
      this.plane = new Plane(startPoint, endPoint);
      this.startPlane =  new SidedPlane(endPoint, plane, startPoint);
      this.endPlane = new SidedPlane(startPoint, plane, endPoint);
      this.planeBounds = new XYZBounds();
      this.plane.recordBounds(pm, this.planeBounds, this.startPlane, this.endPlane);
    }
  }
  
  /**
   * Iterator execution interface, for tree traversal.  Pass an object implementing this interface
   * into the traversal method of a tree, and each edge that matches will cause this object to be
   * called.
   */
  private static interface EdgeIterator {
    /**
     * @param edge is the edge that matched.
     * @return true if the iteration should continue, false otherwise.
     */
    public boolean matches(final Edge edge);
  }
  
  /**
   * Comparison interface for tree traversal.  An object implementing this interface
   * gets to decide the relationship between the Edge object and the criteria being considered.
   */
  private static interface TraverseComparator {
    
    /**
     * Compare an edge.
     * @param edge is the edge to compare.
     * @param minValue is the minimum value to compare (bottom of the range)
     * @param maxValue is the maximum value to compare (top of the range)
     * @return -1 if "less" than this one, 0 if overlaps, or 1 if "greater".
     */
    public int compare(final Edge edge, final double minValue, final double maxValue);
    
  }

  /**
   * Comparison interface for tree addition.  An object implementing this interface
   * gets to decide the relationship between the Edge object and the criteria being considered.
   */
  private static interface AddComparator {
    
    /**
     * Compare an edge.
     * @param edge is the edge to compare.
     * @param addEdge is the edge being added.
     * @return -1 if "less" than this one, 0 if overlaps, or 1 if "greater".
     */
    public int compare(final Edge edge, final Edge addEdge);
    
  }
  
  /**
   * An instance of this class represents a node in a tree.  The tree is designed to be given
   * a value and from that to iterate over a list of edges.
   * In order to do this efficiently, each new edge is dropped into the tree using its minimum and
   * maximum value.  If the new edge's value does not overlap the range, then it gets added
   * either to the lesser side or the greater side, accordingly.  If it does overlap, then the
   * "overlapping" chain is instead traversed.
   *
   * This class is generic and can be used for any definition of "value".
   *
   */
  private static class Node {
    public final Edge edge;
    public Node lesser = null;
    public Node greater = null;
    public Node overlaps = null;
    
    public Node(final Edge edge) {
      this.edge = edge;
    }
    
    public void add(final Edge newEdge, final AddComparator edgeComparator) {
      Node currentNode = this;
      while (true) {
        final int result = edgeComparator.compare(edge, newEdge);
        if (result < 0) {
          if (lesser == null) {
            lesser = new Node(newEdge);
            return;
          }
          currentNode = lesser;
        } else if (result > 0) {
          if (greater == null) {
            greater = new Node(newEdge);
            return;
          }
          currentNode = greater;
        } else {
          if (overlaps == null) {
            overlaps = new Node(newEdge);
            return;
          }
          currentNode = overlaps;
        }
      }
    }
    
    public boolean traverse(final EdgeIterator edgeIterator, final TraverseComparator edgeComparator, final double minValue, final double maxValue) {
      Node currentNode = this;
      while (currentNode != null) {
        final int result = edgeComparator.compare(currentNode.edge, minValue, maxValue);
        if (result < 0) {
          currentNode = lesser;
        } else if (result > 0) {
          currentNode = greater;
        } else {
          if (!edgeIterator.matches(edge)) {
            return false;
          }
          currentNode = overlaps;
        }
      }
      return true;
    }
  }
  
  /** This is the z-tree.
   */
  private static class ZTree implements TraverseComparator, AddComparator {
    public Node rootNode = null;
    
    public ZTree() {
    }
    
    public void add(final Edge edge) {
      if (rootNode == null) {
        rootNode = new Node(edge);
      } else {
        rootNode.add(edge, this);
      }
    }
    
    public boolean traverse(final EdgeIterator edgeIterator, final double minValue, final double maxValue) {
      if (rootNode == null) {
        return true;
      }
      return rootNode.traverse(edgeIterator, this, minValue, maxValue);
    }
    
    @Override
    public int compare(final Edge edge, final Edge addEdge) {
      if (edge.planeBounds.getMaximumZ() < addEdge.planeBounds.getMinimumZ()) {
        return 1;
      } else if (edge.planeBounds.getMinimumZ() > addEdge.planeBounds.getMaximumZ()) {
        return -1;
      }
      return 0;
    }
    
    @Override
    public int compare(final Edge edge, final double minValue, final double maxValue) {
      if (edge.planeBounds.getMinimumZ() > maxValue) {
        return -1;
      } else if (edge.planeBounds.getMaximumZ() < minValue) {
        return 1;
      }
      return 0;
    }
    
  }
  
  /** This is the y-tree.
   */
  private static class YTree implements TraverseComparator, AddComparator {
    public Node rootNode = null;
    
    public YTree() {
    }
    
    public void add(final Edge edge) {
      if (rootNode == null) {
        rootNode = new Node(edge);
      } else {
        rootNode.add(edge, this);
      }
    }
    
    public boolean traverse(final EdgeIterator edgeIterator, final double minValue, final double maxValue) {
      if (rootNode == null) {
        return true;
      }
      return rootNode.traverse(edgeIterator, this, minValue, maxValue);
    }
    
    @Override
    public int compare(final Edge edge, final Edge addEdge) {
      if (edge.planeBounds.getMaximumY() < addEdge.planeBounds.getMinimumY()) {
        return 1;
      } else if (edge.planeBounds.getMinimumY() > addEdge.planeBounds.getMaximumY()) {
        return -1;
      }
      return 0;
    }
    
    @Override
    public int compare(final Edge edge, final double minValue, final double maxValue) {
      if (edge.planeBounds.getMinimumY() > maxValue) {
        return -1;
      } else if (edge.planeBounds.getMaximumY() < minValue) {
        return 1;
      }
      return 0;
    }
    
  }

  /** This is the x-tree.
   */
  private static class XTree implements TraverseComparator, AddComparator {
    public Node rootNode = null;
    
    public XTree() {
    }
    
    public void add(final Edge edge) {
      if (rootNode == null) {
        rootNode = new Node(edge);
      } else {
        rootNode.add(edge, this);
      }
    }
    
    public boolean traverse(final EdgeIterator edgeIterator, final double minValue, final double maxValue) {
      if (rootNode == null) {
        return true;
      }
      return rootNode.traverse(edgeIterator, this, minValue, maxValue);
    }
    
    @Override
    public int compare(final Edge edge, final Edge addEdge) {
      if (edge.planeBounds.getMaximumX() < addEdge.planeBounds.getMinimumX()) {
        return 1;
      } else if (edge.planeBounds.getMinimumX() > addEdge.planeBounds.getMaximumX()) {
        return -1;
      }
      return 0;
    }
    
    @Override
    public int compare(final Edge edge, final double minValue, final double maxValue) {
      if (edge.planeBounds.getMinimumX() > maxValue) {
        return -1;
      } else if (edge.planeBounds.getMaximumX() < minValue) {
        return 1;
      }
      return 0;
    }
    
  }

  /** Assess whether edge intersects the provided plane plus bounds.
   */
  private class IntersectorEdgeIterator implements EdgeIterator {
    
    private final Plane plane;
    private final GeoPoint[] notablePoints;
    private final Membership[] bounds;
    
    public IntersectorEdgeIterator(final Plane plane, final GeoPoint[] notablePoints, final Membership... bounds) {
      this.plane = plane;
      this.notablePoints = notablePoints;
      this.bounds = bounds;
    }
    
    @Override
    public boolean matches(final Edge edge) {
      return !plane.intersects(planetModel, edge.plane, notablePoints, edge.notablePoints, bounds, edge.startPlane, edge.endPlane);
    }

  }
  
  /** Count the number of verifiable edge crossings.
   */
  private class LinearCrossingEdgeIterator implements EdgeIterator {
    
    private final Plane plane;
    private final Plane abovePlane;
    private final Plane belowPlane;
    private final Membership bound1;
    private final Membership bound2;
    private final Vector thePoint;
    
    public int crossingCount = 0;
    
    public LinearCrossingEdgeIterator(final Plane plane, final Plane abovePlane, final Plane belowPlane, final Vector testPoint, final Vector thePoint) {
      this.plane = plane;
      this.abovePlane = abovePlane;
      this.belowPlane = belowPlane;
      this.bound1 = new SidedPlane(thePoint, plane, testPoint);
      this.bound2 = new SidedPlane(testPoint, plane, thePoint);
      this.thePoint = thePoint;
    }
    
    @Override
    public boolean matches(final Edge edge) {
      // Early exit if the point is on the edge.
      if (thePoint != null && edge.plane.evaluateIsZero(thePoint) && edge.startPlane.isWithin(thePoint) && edge.endPlane.isWithin(thePoint)) {
        return false;
      }
      final GeoPoint[] crossingPoints = plane.findCrossings(planetModel, edge.plane, bound1, bound2, edge.startPlane, edge.endPlane);
      if (crossingPoints != null) {
        // We need to handle the endpoint case, which is quite tricky.
        for (final GeoPoint crossingPoint : crossingPoints) {
          countCrossingPoint(crossingPoint, edge);
        }
      }
      return true;
    }

    private void countCrossingPoint(final GeoPoint crossingPoint, final Edge edge) {
      if (crossingPoint.isNumericallyIdentical(edge.startPoint)) {
        // We have to figure out if this crossing should be counted.
        
        // Does the crossing for this edge go up, or down?  Or can't we tell?
        final GeoPoint[] aboveIntersections = abovePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
        final GeoPoint[] belowIntersections = belowPlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
        
        assert !(aboveIntersections.length > 0 && belowIntersections.length > 0) : "edge that ends in a crossing can't both up and down";
        
        if (aboveIntersections.length == 0 && belowIntersections.length == 0) {
          return;
        }

        final boolean edgeCrossesAbove = aboveIntersections.length > 0;

        // This depends on the previous edge that first departs from identicalness.
        Edge assessEdge = edge;
        GeoPoint[] assessAboveIntersections;
        GeoPoint[] assessBelowIntersections;
        while (true) {
          assessEdge = assessEdge.previous;
          assessAboveIntersections = abovePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);
          assessBelowIntersections = belowPlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);

          assert !(assessAboveIntersections.length > 0 && assessBelowIntersections.length > 0) : "assess edge that ends in a crossing can't both up and down";

          if (assessAboveIntersections.length == 0 && assessBelowIntersections.length == 0) {
            continue;
          }
          break;
        }
        
        // Basically, we now want to assess whether both edges that come together at this endpoint leave the plane in opposite
        // directions.  If they do, then we should count it as a crossing; if not, we should not.  We also have to remember that
        // each edge we look at can also be looked at again if it, too, seems to cross the plane.
        
        // To handle the latter situation, we need to know if the other edge will be looked at also, and then we can make
        // a decision whether to count or not based on that.
        
        // Compute the crossing points of this other edge.
        final GeoPoint[] otherCrossingPoints = plane.findCrossings(planetModel, assessEdge.plane, bound1, bound2, assessEdge.startPlane, assessEdge.endPlane);
        
        // Look for a matching endpoint.  If the other endpoint doesn't show up, it is either out of bounds (in which case the
        // transition won't be counted for that edge), or it is not a crossing for that edge (so, same conclusion).
        for (final GeoPoint otherCrossingPoint : otherCrossingPoints) {
          if (otherCrossingPoint.isNumericallyIdentical(assessEdge.endPoint)) {
            // Found it!
            // Both edges will try to contribute to the crossing count.  By convention, we'll only include the earlier one.
            // Since we're the latter point, we exit here in that case.
            return;
          }
        }
        
        // Both edges will not count the same point, so we can proceed.  We need to determine the direction of both edges at the
        // point where they hit the plane.  This may be complicated by the 3D geometry; it may not be safe just to look at the endpoints of the edges
        // and make an assessment that way, since a single edge can intersect the plane at more than one point.
        
        final boolean assessEdgeAbove = assessAboveIntersections.length > 0;
        if (assessEdgeAbove != edgeCrossesAbove) {
          crossingCount++;
        }
        
      } else if (crossingPoint.isNumericallyIdentical(edge.endPoint)) {
        // Figure out if the crossing should be counted.
        
        // Does the crossing for this edge go up, or down?  Or can't we tell?
        final GeoPoint[] aboveIntersections = abovePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
        final GeoPoint[] belowIntersections = belowPlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
        
        assert !(aboveIntersections.length > 0 && belowIntersections.length > 0) : "edge that ends in a crossing can't both up and down";
        
        if (aboveIntersections.length == 0 && belowIntersections.length == 0) {
          return;
        }

        final boolean edgeCrossesAbove = aboveIntersections.length > 0;

        // This depends on the previous edge that first departs from identicalness.
        Edge assessEdge = edge;
        GeoPoint[] assessAboveIntersections;
        GeoPoint[] assessBelowIntersections;
        while (true) {
          assessEdge = assessEdge.next;
          assessAboveIntersections = abovePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);
          assessBelowIntersections = belowPlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);

          assert !(assessAboveIntersections.length > 0 && assessBelowIntersections.length > 0) : "assess edge that ends in a crossing can't both up and down";

          if (assessAboveIntersections.length == 0 && assessBelowIntersections.length == 0) {
            continue;
          }
          break;
        }
        
        // Basically, we now want to assess whether both edges that come together at this endpoint leave the plane in opposite
        // directions.  If they do, then we should count it as a crossing; if not, we should not.  We also have to remember that
        // each edge we look at can also be looked at again if it, too, seems to cross the plane.
        
        // By definition, we're the earlier plane in this case, so any crossing we detect we must count, by convention.  It is unnecessary
        // to consider what the other edge does, because when we get to it, it will look back and figure out what we did for this one.
        
        // We need to determine the direction of both edges at the
        // point where they hit the plane.  This may be complicated by the 3D geometry; it may not be safe just to look at the endpoints of the edges
        // and make an assessment that way, since a single edge can intersect the plane at more than one point.

        final boolean assessEdgeAbove = assessAboveIntersections.length > 0;
        if (assessEdgeAbove != edgeCrossesAbove) {
          crossingCount++;
        }

      } else {
        crossingCount++;
      }
    }
  }
  
  /** Count the number of verifiable edge crossings for a dual-leg journey.
   */
  private class DualCrossingEdgeIterator implements EdgeIterator {
    
    private boolean isSecondLeg = false;
    
    private final Plane testPointPlane;
    private final Plane testPointInsidePlane;
    private final Plane testPointOutsidePlane;
    private final Plane travelPlane;
    private final Plane travelInsidePlane;
    private final Plane travelOutsidePlane;
    private final Vector thePoint;
    
    private final GeoPoint intersectionPoint;
    
    private final SidedPlane testPointCutoffPlane;
    private final SidedPlane checkPointCutoffPlane;
    private final SidedPlane testPointOtherCutoffPlane;
    private final SidedPlane checkPointOtherCutoffPlane;

    private final SidedPlane insideTestPointCutoffPlane;
    private final SidedPlane insideTravelCutoffPlane;
    
    public int crossingCount = 0;

    public DualCrossingEdgeIterator(final Plane testPointPlane, final Plane testPointAbovePlane, final Plane testPointBelowPlane,
      final Plane travelPlane, final Vector testPoint, final Vector thePoint) {
      this.testPointPlane = testPointPlane;
      this.travelPlane = travelPlane;
      this.thePoint = thePoint;
      this.testPointCutoffPlane = new SidedPlane(thePoint, testPointPlane, testPoint);
      this.checkPointCutoffPlane = new SidedPlane(testPoint, travelPlane, thePoint);
      // Now, find the intersection of the check and test point planes.
      final GeoPoint[] intersectionPoints = travelPlane.findIntersections(planetModel, testPointPlane, testPointCutoffPlane, checkPointCutoffPlane);
      assert intersectionPoints != null : "couldn't find any intersections";
      assert intersectionPoints.length != 1 : "wrong number of intersection points";
      this.intersectionPoint = intersectionPoints[0];
      this.testPointOtherCutoffPlane = new SidedPlane(testPoint, testPointPlane, intersectionPoint);
      this.checkPointOtherCutoffPlane = new SidedPlane(thePoint, travelPlane, intersectionPoint);
        
      // Figure out which of the above/below planes are inside vs. outside.  To do this,
      // we look for the point that is within the bounds of the testPointPlane and travelPlane.  The two sides that intersected there are the inside
      // borders.
      final Plane travelAbovePlane = new Plane(travelPlane, true);
      final Plane travelBelowPlane = new Plane(travelPlane, false);
      final GeoPoint[] aboveAbove = travelAbovePlane.findIntersections(planetModel, testPointAbovePlane, testPointCutoffPlane, testPointOtherCutoffPlane, checkPointCutoffPlane, checkPointOtherCutoffPlane);
      assert aboveAbove != null : "Above + above should not be coplanar";
      final GeoPoint[] aboveBelow = travelAbovePlane.findIntersections(planetModel, testPointBelowPlane, testPointCutoffPlane, testPointOtherCutoffPlane, checkPointCutoffPlane, checkPointOtherCutoffPlane);
      assert aboveBelow != null : "Above + below should not be coplanar";
      final GeoPoint[] belowBelow = travelBelowPlane.findIntersections(planetModel, testPointBelowPlane, testPointCutoffPlane, testPointOtherCutoffPlane, checkPointCutoffPlane, checkPointOtherCutoffPlane);
      assert belowBelow != null : "Below + below should not be coplanar";
      final GeoPoint[] belowAbove = travelBelowPlane.findIntersections(planetModel, testPointAbovePlane, testPointCutoffPlane, testPointOtherCutoffPlane, checkPointCutoffPlane, checkPointOtherCutoffPlane);
      assert belowAbove != null : "Below + above should not be coplanar";
      
      assert aboveAbove.length + aboveBelow.length + belowBelow.length + belowAbove.length == 1 : "Can be exactly one inside point";
      
      final GeoPoint insideIntersection;
      if (aboveAbove.length > 0) {
        travelInsidePlane = travelAbovePlane;
        testPointInsidePlane = testPointAbovePlane;
        travelOutsidePlane = travelBelowPlane;
        testPointOutsidePlane = testPointBelowPlane;
        insideIntersection = aboveAbove[0];
      } else if (aboveBelow.length > 0) {
        travelInsidePlane = travelAbovePlane;
        testPointInsidePlane = testPointBelowPlane;
        travelOutsidePlane = travelBelowPlane;
        testPointOutsidePlane = testPointAbovePlane;
        insideIntersection = aboveBelow[0];
      } else if (belowBelow.length > 0) {
        travelInsidePlane = travelBelowPlane;
        testPointInsidePlane = testPointBelowPlane;
        travelOutsidePlane = travelAbovePlane;
        testPointOutsidePlane = testPointAbovePlane;
        insideIntersection = belowBelow[0];
      } else {
        travelInsidePlane = travelBelowPlane;
        testPointInsidePlane = testPointAbovePlane;
        travelOutsidePlane = travelAbovePlane;
        testPointOutsidePlane = testPointBelowPlane;
        insideIntersection = belowAbove[0];
      }
      
      insideTravelCutoffPlane = new SidedPlane(thePoint, travelInsidePlane, insideIntersection);
      insideTestPointCutoffPlane = new SidedPlane(testPoint, testPointInsidePlane, insideIntersection);

    }

    public void setSecondLeg() {
      isSecondLeg = true;
    }
    
    @Override
    public boolean matches(final Edge edge) {
      // Early exit if the point is on the edge.
      if (thePoint != null && edge.plane.evaluateIsZero(thePoint) && edge.startPlane.isWithin(thePoint) && edge.endPlane.isWithin(thePoint)) {
        return false;
      }
      // If the intersection point lies on this edge, we should still be able to consider crossing points only.
      // Even if an intersection point is eliminated because it's not a crossing of one plane, it will have to be a crossing
      // for at least one of the two planes in order to be a legitimate crossing of the combined path.
      final GeoPoint[] crossingPoints;
      if (isSecondLeg) {
        crossingPoints = travelPlane.findCrossings(planetModel, edge.plane, checkPointCutoffPlane, checkPointOtherCutoffPlane, edge.startPlane, edge.endPlane);
      } else {
        crossingPoints = testPointPlane.findCrossings(planetModel, edge.plane, testPointCutoffPlane, testPointOtherCutoffPlane, edge.startPlane, edge.endPlane);
      }
      if (crossingPoints != null) {
        // We need to handle the endpoint case, which is quite tricky.
        for (final GeoPoint crossingPoint : crossingPoints) {
          countCrossingPoint(crossingPoint, edge);
        }
      }
      return true;
    }

    private void countCrossingPoint(final GeoPoint crossingPoint, final Edge edge) {
      // We consider crossing points only in this method.
      // Unlike the linear case, there are additional cases when:
      // (1) The crossing point and the intersection point are the same, but are not the endpoint of an edge;
      // (2) The crossing point and the intersection point are the same, and they *are* the endpoint of an edge.
      // The other logical difference is that crossings of all kinds have to be considered so that:
      // (a) both inside edges are considered together at all times;
      // (b) both outside edges are considered together at all times;
      // (c) inside edge crossings that are between the other leg's inside and outside edge are ignored.
      if (crossingPoint.isNumericallyIdentical(intersectionPoint)) {
        // Intersection point crossing
        
        // MHL to deal with intersection point crossing!!
        
      } else {
        // Standard plane crossing, either first leg or second leg
      
        final Plane plane;
        final Plane insidePlane;
        final Plane outsidePlane;
        final SidedPlane bound1;
        final SidedPlane bound2;
        if (isSecondLeg) {
          plane = travelPlane;
          insidePlane = travelInsidePlane;
          outsidePlane = travelOutsidePlane;
          bound1 = checkPointCutoffPlane;
          bound2 = checkPointOtherCutoffPlane;
        } else {
          plane = testPointPlane;
          insidePlane = testPointInsidePlane;
          outsidePlane = testPointOutsidePlane;
          bound1 = testPointCutoffPlane;
          bound2 = testPointOtherCutoffPlane;
        }
        
        if (crossingPoint.isNumericallyIdentical(edge.startPoint)) {
          // We have to figure out if this crossing should be counted.
          
          // Does the crossing for this edge go up, or down?  Or can't we tell?
          final GeoPoint[] insideTestPointPlaneIntersections = testPointInsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane, insideTestPointCutoffPlane);
          final GeoPoint[] insideTravelPlaneIntersections = travelInsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane, insideTravelCutoffPlane);
          final GeoPoint[] outsideTestPointPlaneIntersections = testPointOutsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
          final GeoPoint[] outsideTravelPlaneIntersections = travelOutsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
          
          assert !(insideTestPointPlaneIntersections.length + insideTravelPlaneIntersections.length > 0 && outsideTestPointPlaneIntersections.length + outsideTravelPlaneIntersections.length > 0) : "edge that ends in a crossing can't both up and down";
          
          if (insideTestPointPlaneIntersections.length + insideTravelPlaneIntersections.length == 0 && outsideTestPointPlaneIntersections.length + outsideTravelPlaneIntersections.length == 0) {
            return;
          }

          final boolean edgeCrossesInside = insideTestPointPlaneIntersections.length + insideTravelPlaneIntersections.length > 0;

          // This depends on the previous edge that first departs from identicalness.
          Edge assessEdge = edge;
          GeoPoint[] assessInsideTestPointIntersections;
          GeoPoint[] assessInsideTravelIntersections;
          GeoPoint[] assessOutsideTestPointIntersections;
          GeoPoint[] assessOutsideTravelIntersections;
          while (true) {
            assessEdge = assessEdge.previous;
            assessInsideTestPointIntersections = testPointInsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane, insideTestPointCutoffPlane);
            assessInsideTravelIntersections = travelInsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane, insideTravelCutoffPlane);
            assessOutsideTestPointIntersections = testPointOutsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);
            assessOutsideTravelIntersections = travelOutsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);

            assert !(assessInsideTestPointIntersections.length + assessInsideTravelIntersections.length > 0 && assessOutsideTestPointIntersections.length + assessOutsideTravelIntersections.length > 0) : "assess edge that ends in a crossing can't both up and down";

            if (assessInsideTestPointIntersections.length + assessInsideTravelIntersections.length == 0 && assessOutsideTestPointIntersections.length + assessOutsideTravelIntersections.length == 0) {
              continue;
            }
            break;
          }

          // Basically, we now want to assess whether both edges that come together at this endpoint leave the plane in opposite
          // directions.  If they do, then we should count it as a crossing; if not, we should not.  We also have to remember that
          // each edge we look at can also be looked at again if it, too, seems to cross the plane.
          
          // To handle the latter situation, we need to know if the other edge will be looked at also, and then we can make
          // a decision whether to count or not based on that.
          
          // Compute the crossing points of this other edge.
          final GeoPoint[] otherCrossingPoints = plane.findCrossings(planetModel, assessEdge.plane, bound1, bound2, assessEdge.startPlane, assessEdge.endPlane);
          
          // Look for a matching endpoint.  If the other endpoint doesn't show up, it is either out of bounds (in which case the
          // transition won't be counted for that edge), or it is not a crossing for that edge (so, same conclusion).
          for (final GeoPoint otherCrossingPoint : otherCrossingPoints) {
            if (otherCrossingPoint.isNumericallyIdentical(assessEdge.endPoint)) {
              // Found it!
              // Both edges will try to contribute to the crossing count.  By convention, we'll only include the earlier one.
              // Since we're the latter point, we exit here in that case.
              return;
            }
          }
          
          // Both edges will not count the same point, so we can proceed.  We need to determine the direction of both edges at the
          // point where they hit the plane.  This may be complicated by the 3D geometry; it may not be safe just to look at the endpoints of the edges
          // and make an assessment that way, since a single edge can intersect the plane at more than one point.
          
          final boolean assessEdgeInside = assessInsideTestPointIntersections.length + assessInsideTravelIntersections.length > 0;
          if (assessEdgeInside != edgeCrossesInside) {
            crossingCount++;
          }
          
        } else if (crossingPoint.isNumericallyIdentical(edge.endPoint)) {
          // Figure out if the crossing should be counted.
          
          // Does the crossing for this edge go up, or down?  Or can't we tell?
          final GeoPoint[] insideTestPointPlaneIntersections = testPointInsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane, insideTestPointCutoffPlane);
          final GeoPoint[] insideTravelPlaneIntersections = travelInsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane, insideTravelCutoffPlane);
          final GeoPoint[] outsideTestPointPlaneIntersections = testPointOutsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
          final GeoPoint[] outsideTravelPlaneIntersections = travelOutsidePlane.findIntersections(planetModel, edge.plane, edge.startPlane, edge.endPlane);
          
          assert !(insideTestPointPlaneIntersections.length + insideTravelPlaneIntersections.length > 0 && outsideTestPointPlaneIntersections.length + outsideTravelPlaneIntersections.length > 0) : "edge that ends in a crossing can't both up and down";
          
          if (insideTestPointPlaneIntersections.length + insideTravelPlaneIntersections.length == 0 && outsideTestPointPlaneIntersections.length + outsideTravelPlaneIntersections.length == 0) {
            return;
          }

          final boolean edgeCrossesInside = insideTestPointPlaneIntersections.length + insideTravelPlaneIntersections.length > 0;

          // This depends on the previous edge that first departs from identicalness.
          Edge assessEdge = edge;
          GeoPoint[] assessInsideTestPointIntersections;
          GeoPoint[] assessInsideTravelIntersections;
          GeoPoint[] assessOutsideTestPointIntersections;
          GeoPoint[] assessOutsideTravelIntersections;
          while (true) {
            assessEdge = assessEdge.next;
            assessInsideTestPointIntersections = testPointInsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane, insideTestPointCutoffPlane);
            assessInsideTravelIntersections = travelInsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane, insideTravelCutoffPlane);
            assessOutsideTestPointIntersections = testPointOutsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);
            assessOutsideTravelIntersections = travelOutsidePlane.findIntersections(planetModel, assessEdge.plane, assessEdge.startPlane, assessEdge.endPlane);

            assert !(assessInsideTestPointIntersections.length + assessInsideTravelIntersections.length > 0 && assessOutsideTestPointIntersections.length + assessOutsideTravelIntersections.length > 0) : "assess edge that ends in a crossing can't both up and down";

            if (assessInsideTestPointIntersections.length + assessInsideTravelIntersections.length == 0 && assessOutsideTestPointIntersections.length + assessOutsideTravelIntersections.length == 0) {
              continue;
            }
            break;
          }
          
          // Basically, we now want to assess whether both edges that come together at this endpoint leave the plane in opposite
          // directions.  If they do, then we should count it as a crossing; if not, we should not.  We also have to remember that
          // each edge we look at can also be looked at again if it, too, seems to cross the plane.
          
          // By definition, we're the earlier plane in this case, so any crossing we detect we must count, by convention.  It is unnecessary
          // to consider what the other edge does, because when we get to it, it will look back and figure out what we did for this one.
          
          // We need to determine the direction of both edges at the
          // point where they hit the plane.  This may be complicated by the 3D geometry; it may not be safe just to look at the endpoints of the edges
          // and make an assessment that way, since a single edge can intersect the plane at more than one point.

          final boolean assessEdgeInside = assessInsideTestPointIntersections.length + assessInsideTravelIntersections.length > 0;
          if (assessEdgeInside != edgeCrossesInside) {
            crossingCount++;
          }
        } else {
          // Not a special case, so we can safely count a crossing.
          crossingCount++;
        }
      }
    }
  }
  
  @Override
  public boolean equals(Object o) {
    // MHL
    return false;
  }

  @Override
  public int hashCode() {
    // MHL
    return 0;
  }

  @Override
  public String toString() {
    return "GeoComplexPolygon: {planetmodel=" + planetModel + "}";
  }
}
  
