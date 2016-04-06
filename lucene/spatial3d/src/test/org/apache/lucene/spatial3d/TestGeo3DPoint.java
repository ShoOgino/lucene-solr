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
package org.apache.lucene.spatial3d;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PointsFormat;
import org.apache.lucene.codecs.PointsReader;
import org.apache.lucene.codecs.PointsWriter;
import org.apache.lucene.codecs.lucene60.Lucene60PointsReader;
import org.apache.lucene.codecs.lucene60.Lucene60PointsWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.spatial3d.geom.GeoArea;
import org.apache.lucene.spatial3d.geom.GeoAreaFactory;
import org.apache.lucene.spatial3d.geom.GeoBBoxFactory;
import org.apache.lucene.spatial3d.geom.GeoCircleFactory;
import org.apache.lucene.spatial3d.geom.GeoPathFactory;
import org.apache.lucene.spatial3d.geom.GeoPoint;
import org.apache.lucene.spatial3d.geom.GeoPolygonFactory;
import org.apache.lucene.spatial3d.geom.GeoShape;
import org.apache.lucene.spatial3d.geom.PlanetModel;
import org.apache.lucene.spatial3d.geom.XYZBounds;
import org.apache.lucene.spatial3d.geom.SidedPlane;
import org.apache.lucene.spatial3d.geom.Plane;
import org.apache.lucene.spatial3d.geom.GeoPolygon;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.junit.BeforeClass;
import org.junit.Ignore;

import com.carrotsearch.randomizedtesting.generators.RandomInts;

public class TestGeo3DPoint extends LuceneTestCase {

  private static boolean smallBBox;
  
  @BeforeClass
  public static void beforeClass() {
    smallBBox = random().nextBoolean();
    if (VERBOSE) {
      System.err.println("TEST: smallBBox=" + smallBBox);
    }
  }

  private static Codec getCodec() {
    if (Codec.getDefault().getName().equals("Lucene60")) {
      int maxPointsInLeafNode = TestUtil.nextInt(random(), 16, 2048);
      double maxMBSortInHeap = 3.0 + (3*random().nextDouble());
      if (VERBOSE) {
        System.out.println("TEST: using Lucene60PointsFormat with maxPointsInLeafNode=" + maxPointsInLeafNode + " and maxMBSortInHeap=" + maxMBSortInHeap);
      }

      return new FilterCodec("Lucene60", Codec.getDefault()) {
        @Override
        public PointsFormat pointsFormat() {
          return new PointsFormat() {
            @Override
            public PointsWriter fieldsWriter(SegmentWriteState writeState) throws IOException {
              return new Lucene60PointsWriter(writeState, maxPointsInLeafNode, maxMBSortInHeap);
            }

            @Override
            public PointsReader fieldsReader(SegmentReadState readState) throws IOException {
              return new Lucene60PointsReader(readState);
            }
          };
        }
      };
    } else {
      return Codec.getDefault();
    }
  }

  public void testBasic() throws Exception {
    Directory dir = getDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);
    Document doc = new Document();
    doc.add(new Geo3DPoint("field", 50.7345267, -97.5303555));
    w.addDocument(doc);
    IndexReader r = DirectoryReader.open(w);
    // We can't wrap with "exotic" readers because the query must see the BKD3DDVFormat:
    IndexSearcher s = newSearcher(r, false);
    assertEquals(1, s.search(Geo3DPoint.newShapeQuery("field",
                                                      GeoCircleFactory.makeGeoCircle(PlanetModel.WGS84, toRadians(50), toRadians(-97), Math.PI/180.)), 1).totalHits);
    w.close();
    r.close();
    dir.close();
  }

  private static double toRadians(double degrees) {
    return Math.PI*(degrees/180.0);
  }

  private static PlanetModel getPlanetModel() {
    if (random().nextBoolean()) {
      // Use one of the earth models:
      if (random().nextBoolean()) {
        return PlanetModel.WGS84;
      } else {
        return PlanetModel.SPHERE;
      }
    } else {
      // Make a randomly squashed planet:
      double oblateness = random().nextDouble() * 0.5 - 0.25;
      return new PlanetModel(1.0 + oblateness, 1.0 - oblateness);
    }
  }

  private static class Cell {
    static int nextCellID;

    final Cell parent;
    final int cellID;
    final int xMinEnc, xMaxEnc;
    final int yMinEnc, yMaxEnc;
    final int zMinEnc, zMaxEnc;
    final int splitCount;

    public Cell(Cell parent,
                int xMinEnc, int xMaxEnc,
                int yMinEnc, int yMaxEnc,
                int zMinEnc, int zMaxEnc,
                int splitCount) {
      this.parent = parent;
      this.xMinEnc = xMinEnc;
      this.xMaxEnc = xMaxEnc;
      this.yMinEnc = yMinEnc;
      this.yMaxEnc = yMaxEnc;
      this.zMinEnc = zMinEnc;
      this.zMaxEnc = zMaxEnc;
      this.cellID = nextCellID++;
      this.splitCount = splitCount;
    }

    /** Returns true if the quantized point lies within this cell, inclusive on all bounds. */
    public boolean contains(double planetMax, GeoPoint point) {
      int docX = Geo3DUtil.encodeValue(planetMax, point.x);
      int docY = Geo3DUtil.encodeValue(planetMax, point.y);
      int docZ = Geo3DUtil.encodeValue(planetMax, point.z);

      return docX >= xMinEnc && docX <= xMaxEnc &&
        docY >= yMinEnc && docY <= yMaxEnc && 
        docZ >= zMinEnc && docZ <= zMaxEnc;
    }

    @Override
    public String toString() {
      return "cell=" + cellID + (parent == null ? "" : " parentCellID=" + parent.cellID) + " x: " + xMinEnc + " TO " + xMaxEnc + ", y: " + yMinEnc + " TO " + yMaxEnc + ", z: " + zMinEnc + " TO " + zMaxEnc + ", splits: " + splitCount;
    }
  }

  private static GeoPoint quantize(double planetMax, GeoPoint point) {
    return new GeoPoint(Geo3DUtil.decodeValueCenter(planetMax, Geo3DUtil.encodeValue(planetMax, point.x)),
                        Geo3DUtil.decodeValueCenter(planetMax, Geo3DUtil.encodeValue(planetMax, point.y)),
                        Geo3DUtil.decodeValueCenter(planetMax, Geo3DUtil.encodeValue(planetMax, point.z)));
  }

  /** Tests consistency of GeoArea.getRelationship vs GeoShape.isWithin */
  public void testGeo3DRelations() throws Exception {

    PlanetModel planetModel = getPlanetModel();

    int numDocs = atLeast(1000);
    if (VERBOSE) {
      System.out.println("TEST: " + numDocs + " docs");
    }

    GeoPoint[] docs = new GeoPoint[numDocs];
    for(int docID=0;docID<numDocs;docID++) {
      docs[docID] = new GeoPoint(planetModel, toRadians(randomLat()), toRadians(randomLon()));
      if (VERBOSE) {
        System.out.println("  doc=" + docID + ": " + docs[docID]);
      }
    }

    double planetMax = planetModel.getMaximumMagnitude();

    int iters = atLeast(10);

    int recurseDepth = RandomInts.randomIntBetween(random(), 5, 15);

    iters = atLeast(50);
    
    for(int iter=0;iter<iters;iter++) {
      GeoShape shape = randomShape(planetModel);

      StringWriter sw = new StringWriter();
      PrintWriter log = new PrintWriter(sw, true);

      if (VERBOSE) {
        log.println("TEST: iter=" + iter + " shape=" + shape);
      }

      XYZBounds bounds = new XYZBounds();
      shape.getBounds(bounds);

      // Start with the root cell that fully contains the shape:
      Cell root = new Cell(null,
                           Geo3DUtil.encodeValueLenient(planetMax, bounds.getMinimumX()),
                           Geo3DUtil.encodeValueLenient(planetMax, bounds.getMaximumX()),
                           Geo3DUtil.encodeValueLenient(planetMax, bounds.getMinimumY()),
                           Geo3DUtil.encodeValueLenient(planetMax, bounds.getMaximumY()),
                           Geo3DUtil.encodeValueLenient(planetMax, bounds.getMinimumZ()),
                           Geo3DUtil.encodeValueLenient(planetMax, bounds.getMaximumZ()),
                           0);

      if (VERBOSE) {
        log.println("  root cell: " + root);
      }

      List<Cell> queue = new ArrayList<>();
      queue.add(root);
      Set<Integer> hits = new HashSet<>();

      while (queue.size() > 0) {
        Cell cell = queue.get(queue.size()-1);
        queue.remove(queue.size()-1);
        if (VERBOSE) {
          log.println("  cycle: " + cell + " queue.size()=" + queue.size());
        }

        if (random().nextInt(10) == 7 || cell.splitCount > recurseDepth) {
          if (VERBOSE) {
            log.println("    leaf");
          }
          // Leaf cell: brute force check all docs that fall within this cell:
          for(int docID=0;docID<numDocs;docID++) {
            GeoPoint point = docs[docID];
            if (cell.contains(planetMax, point)) {
              if (shape.isWithin(quantize(planetMax, point))) {
                if (VERBOSE) {
                  log.println("    check doc=" + docID + ": match!");
                }
                hits.add(docID);
              } else {
                if (VERBOSE) {
                  log.println("    check doc=" + docID + ": no match");
                }
              }
            }
          }
        } else {
          
          GeoArea xyzSolid = GeoAreaFactory.makeGeoArea(planetModel,
                                                        Geo3DUtil.decodeValueMin(planetMax, cell.xMinEnc), Geo3DUtil.decodeValueMax(planetMax, cell.xMaxEnc),
                                                        Geo3DUtil.decodeValueMin(planetMax, cell.yMinEnc), Geo3DUtil.decodeValueMax(planetMax, cell.yMaxEnc),
                                                        Geo3DUtil.decodeValueMin(planetMax, cell.zMinEnc), Geo3DUtil.decodeValueMax(planetMax, cell.zMaxEnc));

          if (VERBOSE) {
            log.println("    minx="+Geo3DUtil.decodeValueMin(planetMax, cell.xMinEnc)+" maxx="+Geo3DUtil.decodeValueMax(planetMax, cell.xMaxEnc)+
              " miny="+Geo3DUtil.decodeValueMin(planetMax, cell.yMinEnc)+" maxy="+Geo3DUtil.decodeValueMax(planetMax, cell.yMaxEnc)+
              " minz="+Geo3DUtil.decodeValueMin(planetMax, cell.zMinEnc)+" maxz="+Geo3DUtil.decodeValueMax(planetMax, cell.zMaxEnc));
          }

          switch (xyzSolid.getRelationship(shape)) {          
          case GeoArea.CONTAINS:
            // Shape fully contains the cell: blindly add all docs in this cell:
            if (VERBOSE) {
              log.println("    GeoArea.CONTAINS: now addAll");
            }
            for(int docID=0;docID<numDocs;docID++) {
              if (cell.contains(planetMax, docs[docID])) {
                if (VERBOSE) {
                  log.println("    addAll doc=" + docID);
                }
                hits.add(docID);
              }
            }
            continue;
          case GeoArea.OVERLAPS:
            if (VERBOSE) {
              log.println("    GeoArea.OVERLAPS: keep splitting");
            }
            // They do overlap but neither contains the other:
            //log.println("    crosses1");
            break;
          case GeoArea.WITHIN:
            if (VERBOSE) {
              log.println("    GeoArea.WITHIN: keep splitting");
            }
            // Cell fully contains the shape:
            //log.println("    crosses2");
            break;
          case GeoArea.DISJOINT:
            // They do not overlap at all: don't recurse on this cell
            //log.println("    outside");
            if (VERBOSE) {
              log.println("    GeoArea.DISJOINT: drop this cell");
              for(int docID=0;docID<numDocs;docID++) {
                if (cell.contains(planetMax, docs[docID])) {
                  if (VERBOSE) {
                    log.println("    skip doc=" + docID);
                  }
                }
              }
            }
            continue;
          default:
            assert false;
          }

          // Randomly split:
          switch(random().nextInt(3)) {

          case 0:
            // Split on X:
            {
              int splitValue = RandomInts.randomIntBetween(random(), cell.xMinEnc, cell.xMaxEnc);
              if (VERBOSE) {
                log.println("    now split on x=" + splitValue);
              }
              Cell cell1 = new Cell(cell,
                                 cell.xMinEnc, splitValue,
                                 cell.yMinEnc, cell.yMaxEnc,
                                 cell.zMinEnc, cell.zMaxEnc,
                                 cell.splitCount+1);
              Cell cell2 = new Cell(cell,
                                 splitValue, cell.xMaxEnc,
                                 cell.yMinEnc, cell.yMaxEnc,
                                 cell.zMinEnc, cell.zMaxEnc,
                                 cell.splitCount+1);
              if (VERBOSE) {
                log.println("    split cell1: " + cell1);
                log.println("    split cell2: " + cell2);
              }
              queue.add(cell1);
              queue.add(cell2);
            }
            break;

          case 1:
            // Split on Y:
            {
              int splitValue = RandomInts.randomIntBetween(random(), cell.yMinEnc, cell.yMaxEnc);
              if (VERBOSE) {
                log.println("    now split on y=" + splitValue);
              }
              Cell cell1 = new Cell(cell,
                                 cell.xMinEnc, cell.xMaxEnc,
                                 cell.yMinEnc, splitValue,
                                 cell.zMinEnc, cell.zMaxEnc,
                                 cell.splitCount+1);
              Cell cell2 = new Cell(cell,
                                 cell.xMinEnc, cell.xMaxEnc,
                                 splitValue, cell.yMaxEnc,
                                 cell.zMinEnc, cell.zMaxEnc,
                                 cell.splitCount+1);
              if (VERBOSE) {
                log.println("    split cell1: " + cell1);
                log.println("    split cell2: " + cell2);
              }
              queue.add(cell1);
              queue.add(cell2);
            }
            break;

          case 2:
            // Split on Z:
            {
              int splitValue = RandomInts.randomIntBetween(random(), cell.zMinEnc, cell.zMaxEnc);
              if (VERBOSE) {
                log.println("    now split on z=" + splitValue);
              }
              Cell cell1 = new Cell(cell,
                                 cell.xMinEnc, cell.xMaxEnc,
                                 cell.yMinEnc, cell.yMaxEnc,
                                 cell.zMinEnc, splitValue,
                                 cell.splitCount+1);
              Cell cell2 = new Cell(cell,
                                 cell.xMinEnc, cell.xMaxEnc,
                                 cell.yMinEnc, cell.yMaxEnc,
                                 splitValue, cell.zMaxEnc,
                                 cell.splitCount+1);
              if (VERBOSE) {
                log.println("    split cell1: " + cell1);
                log.println("    split cell2: " + cell2);
              }
              queue.add(cell1);
              queue.add(cell2);
            }
            break;
          }
        }
      }

      if (VERBOSE) {
        log.println("  " + hits.size() + " hits");
      }

      // Done matching, now verify:
      boolean fail = false;
      for(int docID=0;docID<numDocs;docID++) {
        GeoPoint point = docs[docID];
        GeoPoint quantized = quantize(planetMax, point);
        boolean expected = shape.isWithin(quantized);

        if (expected != shape.isWithin(point)) {
          // Quantization changed the result; skip testing this doc:
          continue;
        }

        boolean actual = hits.contains(docID);
        if (actual != expected) {
          if (actual) {
            log.println("doc=" + docID + " matched but should not");
          } else {
            log.println("doc=" + docID + " did not match but should");
          }
          log.println("  point=" + docs[docID]);
          log.println("  quantized=" + quantize(planetMax, docs[docID]));
          fail = true;
        }
      }

      if (fail) {
        System.out.print(sw.toString());
        fail("invalid hits for shape=" + shape);
      }
    }
  }

  public void testRandomTiny() throws Exception {
    // Make sure single-leaf-node case is OK:
    doTestRandom(10);
  }

  public void testRandomMedium() throws Exception {
    doTestRandom(10000);
  }

  @Nightly
  public void testRandomBig() throws Exception {
    doTestRandom(200000);
  }

  private void doTestRandom(int count) throws Exception {
    int numPoints = atLeast(count);

    if (VERBOSE) {
      System.err.println("TEST: numPoints=" + numPoints);
    }

    double[] lats = new double[numPoints];
    double[] lons = new double[numPoints];

    boolean haveRealDoc = false;

    for (int docID=0;docID<numPoints;docID++) {
      int x = random().nextInt(20);
      if (x == 17) {
        // Some docs don't have a point:
        lats[docID] = Double.NaN;
        if (VERBOSE) {
          System.err.println("  doc=" + docID + " is missing");
        }
        continue;
      }

      if (docID > 0 && x < 3 && haveRealDoc) {
        int oldDocID;
        while (true) {
          oldDocID = random().nextInt(docID);
          if (Double.isNaN(lats[oldDocID]) == false) {
            break;
          }
        }
            
        if (x == 0) {
          // Identical lat to old point
          lats[docID] = lats[oldDocID];
          lons[docID] = randomLon();
          if (VERBOSE) {
            System.err.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID] + " (same lat as doc=" + oldDocID + ")");
          }
        } else if (x == 1) {
          // Identical lon to old point
          lats[docID] = randomLat();
          lons[docID] = lons[oldDocID];
          if (VERBOSE) {
            System.err.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID] + " (same lon as doc=" + oldDocID + ")");
          }
        } else {
          assert x == 2;
          // Fully identical point:
          lats[docID] = lats[oldDocID];
          lons[docID] = lons[oldDocID];
          if (VERBOSE) {
            System.err.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID] + " (same lat/lon as doc=" + oldDocID + ")");
          }
        }
      } else {
        lats[docID] = randomLat();
        lons[docID] = randomLon();
        haveRealDoc = true;
        if (VERBOSE) {
          System.err.println("  doc=" + docID + " lat=" + lats[docID] + " lon=" + lons[docID]);
        }
      }
    }

    verify(lats, lons);
  }

  private static double randomLat() {
    if (smallBBox) {
      return 2.0 * (random().nextDouble()-0.5);
    } else {
      return -90 + 180.0 * random().nextDouble();
    }
  }

  private static double randomLon() {
    if (smallBBox) {
      return 2.0 * (random().nextDouble()-0.5);
    } else {
      return -180 + 360.0 * random().nextDouble();
    }
  }

  // Poached from Geo3dRptTest.randomShape:
  private static GeoShape randomShape(PlanetModel planetModel) {
    while (true) {
      final int shapeType = random().nextInt(4);
      switch (shapeType) {
      case 0: {
        // Polygons
        final int vertexCount = random().nextInt(3) + 3;
        final List<GeoPoint> geoPoints = new ArrayList<>();
        while (geoPoints.size() < vertexCount) {
          final GeoPoint gPt = new GeoPoint(planetModel, toRadians(randomLat()), toRadians(randomLon()));
          geoPoints.add(gPt);
        }
        final int convexPointIndex = random().nextInt(vertexCount);       //If we get this wrong, hopefully we get IllegalArgumentException
        try {
          return GeoPolygonFactory.makeGeoPolygon(planetModel, geoPoints, convexPointIndex);
        } catch (IllegalArgumentException e) {
          // This is what happens when we create a shape that is invalid.  Although it is conceivable that there are cases where
          // the exception is thrown incorrectly, we aren't going to be able to do that in this random test.
          continue;
        }
      }

      case 1: {
        // Circles

        double lat = toRadians(randomLat());
        double lon = toRadians(randomLon());

        double angle;
        if (smallBBox) {
          angle = random().nextDouble() * Math.PI/360.0;
        } else {
          angle = random().nextDouble() * Math.PI/2.0;
        }

        try {
          return GeoCircleFactory.makeGeoCircle(planetModel, lat, lon, angle);
        } catch (IllegalArgumentException iae) {
          // angle is too small; try again:
          continue;
        }
      }

      case 2: {
        // Rectangles
        double lat0 = toRadians(randomLat());
        double lat1 = toRadians(randomLat());
        if (lat1 < lat0) {
          double x = lat0;
          lat0 = lat1;
          lat1 = x;
        }
        double lon0 = toRadians(randomLon());
        double lon1 = toRadians(randomLon());
        if (lon1 < lon0) {
          double x = lon0;
          lon0 = lon1;
          lon1 = x;
        }

        return GeoBBoxFactory.makeGeoBBox(planetModel, lat1, lat0, lon0, lon1);
      }

      case 3: {
        // Paths
        final int pointCount = random().nextInt(5) + 1;
        final double width = toRadians(random().nextInt(89)+1);
        final GeoPoint[] points = new GeoPoint[pointCount];
        for (int i = 0; i < pointCount; i++) {
          points[i] = new GeoPoint(planetModel, toRadians(randomLat()), toRadians(randomLon()));
        }
        try {
          return GeoPathFactory.makeGeoPath(planetModel, width, points);
        } catch (IllegalArgumentException e) {
          // This is what happens when we create a shape that is invalid.  Although it is conceivable that there are cases where
          // the exception is thrown incorrectly, we aren't going to be able to do that in this random test.
          continue;
        }
      }

      default:
        throw new IllegalStateException("Unexpected shape type");
      }
    }
  }

  private static void verify(double[] lats, double[] lons) throws Exception {
    IndexWriterConfig iwc = newIndexWriterConfig();

    // Else we can get O(N^2) merging:
    int mbd = iwc.getMaxBufferedDocs();
    if (mbd != -1 && mbd < lats.length/100) {
      iwc.setMaxBufferedDocs(lats.length/100);
    }
    iwc.setCodec(getCodec());
    Directory dir;
    if (lats.length > 100000) {
      dir = newFSDirectory(createTempDir("TestBKDTree"));
    } else {
      dir = getDirectory();
    }
    Set<Integer> deleted = new HashSet<>();
    // RandomIndexWriter is too slow here:
    IndexWriter w = new IndexWriter(dir, iwc);
    for(int id=0;id<lats.length;id++) {
      Document doc = new Document();
      doc.add(newStringField("id", ""+id, Field.Store.NO));
      doc.add(new NumericDocValuesField("id", id));
      if (Double.isNaN(lats[id]) == false) {
        doc.add(new Geo3DPoint("point", lats[id], lons[id]));
      }
      w.addDocument(doc);
      if (id > 0 && random().nextInt(100) == 42) {
        int idToDelete = random().nextInt(id);
        w.deleteDocuments(new Term("id", ""+idToDelete));
        deleted.add(idToDelete);
        if (VERBOSE) {
          System.err.println("  delete id=" + idToDelete);
        }
      }
    }
    if (random().nextBoolean()) {
      w.forceMerge(1);
    }
    final IndexReader r = DirectoryReader.open(w);
    w.close();

    // We can't wrap with "exotic" readers because the geo3d query must see the Geo3DDVFormat:
    IndexSearcher s = newSearcher(r, false);

    int numThreads = TestUtil.nextInt(random(), 2, 5);

    List<Thread> threads = new ArrayList<>();
    final int iters = atLeast(100);

    final CountDownLatch startingGun = new CountDownLatch(1);
    final AtomicBoolean failed = new AtomicBoolean();

    for(int i=0;i<numThreads;i++) {
      Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              _run();
            } catch (Exception e) {
              failed.set(true);
              throw new RuntimeException(e);
            }
          }

          private void _run() throws Exception {
            startingGun.await();

            NumericDocValues docIDToID = MultiDocValues.getNumericValues(r, "id");

            for (int iter=0;iter<iters && failed.get() == false;iter++) {

              GeoShape shape = randomShape(PlanetModel.WGS84);

              if (VERBOSE) {
                System.err.println("\n" + Thread.currentThread() + ": TEST: iter=" + iter + " shape="+shape);
              }
              
              Query query = Geo3DPoint.newShapeQuery("point", shape);

              if (VERBOSE) {
                System.err.println("  using query: " + query);
              }

              final FixedBitSet hits = new FixedBitSet(r.maxDoc());

              s.search(query, new SimpleCollector() {

                  private int docBase;

                  @Override
                  public boolean needsScores() {
                    return false;
                  }

                  @Override
                  protected void doSetNextReader(LeafReaderContext context) throws IOException {
                    docBase = context.docBase;
                  }

                  @Override
                  public void collect(int doc) {
                    hits.set(docBase+doc);
                  }
                });

              if (VERBOSE) {
                System.err.println("  hitCount: " + hits.cardinality());
              }
      
              for(int docID=0;docID<r.maxDoc();docID++) {
                int id = (int) docIDToID.get(docID);
                if (Double.isNaN(lats[id]) == false) {

                  // Accurate point:
                  GeoPoint point1 = new GeoPoint(PlanetModel.WGS84, toRadians(lats[id]), toRadians(lons[id]));

                  // Quantized point (32 bits per dim):
                  GeoPoint point2 = quantize(PlanetModel.WGS84.getMaximumMagnitude(), point1);

                  if (shape.isWithin(point1) != shape.isWithin(point2)) {
                    if (VERBOSE) {
                      System.out.println("  skip checking docID=" + docID + " quantization changed the expected result from " + shape.isWithin(point1) + " to " + shape.isWithin(point2));
                    }
                    continue;
                  }

                  boolean expected = ((deleted.contains(id) == false) && shape.isWithin(point2));
                  if (hits.get(docID) != expected) {
                    fail(Thread.currentThread().getName() + ": iter=" + iter + " id=" + id + " docID=" + docID + " lat=" + lats[id] + " lon=" + lons[id] + " expected " + expected + " but got: " + hits.get(docID) + " deleted?=" + deleted.contains(id) + "\n  point1=" + point1 + ", iswithin="+shape.isWithin(point1)+"\n  point2=" + point2 + ", iswithin="+shape.isWithin(point2) + "\n  query=" + query);
                  }
                } else {
                  assertFalse(hits.get(docID));
                }

              }
            }
          }
        };
      thread.setName("T" + i);
      thread.start();
      threads.add(thread);
    }
    startingGun.countDown();
    for(Thread thread : threads) {
      thread.join();
    }
    IOUtils.close(r, dir);
  }

  public void testToString() {
    Geo3DPoint point = new Geo3DPoint("point", 44.244272, 7.769736);
    assertEquals("Geo3DPoint <point: x=0.709426313149037 y=0.09679758908863707 z=0.6973564619509093>", point.toString());
  }

  public void testShapeQueryToString() {
    assertEquals("PointInGeo3DShapeQuery: field=point: Shape: GeoStandardCircle: {planetmodel=PlanetModel.WGS84, center=[lat=0.7722082215479366, lon=0.13560747521073413], radius=0.1(5.729577951308232)}",
                 Geo3DPoint.newShapeQuery("point", GeoCircleFactory.makeGeoCircle(PlanetModel.WGS84, toRadians(44.244272), toRadians(7.769736), 0.1)).toString());
  }

  private static Directory getDirectory() {     
    return newDirectory();
  }

  public void testEquals() {
    GeoShape shape = randomShape(PlanetModel.WGS84);
    Query q = Geo3DPoint.newShapeQuery("point", shape);
    assertEquals(q, Geo3DPoint.newShapeQuery("point", shape));
    assertFalse(q.equals(Geo3DPoint.newShapeQuery("point2", shape)));
    
    // make a different random shape:
    GeoShape shape2;
    do {
      shape2 = randomShape(PlanetModel.WGS84);
    } while (shape.equals(shape2));

    assertFalse(q.equals(Geo3DPoint.newShapeQuery("point", shape2)));
  }
  
  public void testComplexPolygons() {
    final PlanetModel pm = PlanetModel.WGS84;
    // Pick a random pole
    final GeoPoint randomPole = new GeoPoint(pm, Math.toRadians(randomLat()), Math.toRadians(randomLon()));
    // Create a polygon that's less than 180 degrees
    final Polygon clockWise = makePoly(pm, randomPole, true, true);
    // Create a polygon that's greater than 180 degrees
    final Polygon counterClockWise = makePoly(pm, randomPole, false, true);
    
  }
  
  protected static double MINIMUM_EDGE_ANGLE = Math.toRadians(5.0);
  protected static double MINIMUM_ARC_ANGLE = Math.toRadians(1.0);
  
  /** Cook up a random Polygon that makes sense, with possible nested polygon within.
    * This is part of testing more complex polygons with nested holes.  Picking random points
    * doesn't do it because it's almost impossible to come up with nested ones of the proper 
    * clockwise/counterclockwise rotation that way.
    */
  protected Polygon makePoly(final PlanetModel pm, final GeoPoint pole, final boolean clockwiseDesired, final boolean createHoles) {
    // Polygon edges will be arranged around the provided pole, and holes will each have a pole selected within the parent
    // polygon.
    final int pointCount = TestUtil.nextInt(random(), 3, 10);
    // The point angles we pick next.  The only requirement is that they are not all on one side of the pole.
    // We arrange that by picking the next point within what's left of the remaining angle, but never more than 180 degrees,
    // and never less than what is needed to insure that the remaining point choices are less than 180 degrees always.
    // These are all picked in the context of the pole,
    final double[] angles = new double[pointCount];
    final double[] arcDistance = new double[pointCount];
    // Pick a set of points
    while (true) {
      double accumulatedAngle = 0.0;
      for (int i = 0; i < pointCount; i++) {
        final int remainingEdgeCount = pointCount - i;
        final double remainingAngle = 2.0 * Math.PI - accumulatedAngle;
        if (remainingEdgeCount == 1) {
          angles[i] = remainingAngle;
        } else {
          // The maximum angle is 180 degrees, or what's left when you give a minimal amount to each edge.
          double maximumAngle = remainingAngle - (remainingEdgeCount-1) * MINIMUM_EDGE_ANGLE;
          if (maximumAngle > Math.PI) {
            maximumAngle = Math.PI;
          }
          // The minimum angle is MINIMUM_EDGE_ANGLE, or enough to be sure nobody afterwards needs more than
          // 180 degrees.  And since we have three points to start with, we already know that.
          final double minimumAngle = MINIMUM_EDGE_ANGLE;
          // Pick the angle
          final double angle = random().nextDouble() * (maximumAngle - minimumAngle) + minimumAngle;
          angles[i] = angle;
          accumulatedAngle += angle;
        }
        // Pick the arc distance randomly
        arcDistance[i] = random().nextDouble() * (Math.PI - MINIMUM_ARC_ANGLE) + MINIMUM_ARC_ANGLE;
      }
      if (clockwiseDesired) {
        // Reverse the signs
        for (int i = 0; i < pointCount; i++) {
          angles[i] = -angles[i];
        }
      }
      
      // Now, use the pole's information plus angles and arcs to create GeoPoints in the right order.
      final List<GeoPoint> polyPoints = convertToPoints(pm, pole, angles, arcDistance);
      
      // Create the geo3d polygon, so we can test out our poles.
      final GeoPolygon poly;
      try {
        poly = GeoPolygonFactory.makeGeoPolygon(pm, polyPoints, null);
      } catch (IllegalArgumentException e) {
        // This is what happens when three adjacent points are colinear, so try again.
        continue;
      }
      
      // Next, do some holes.  No more than 2 of these.  The poles for holes must always be within the polygon, so we're
      // going to use Geo3D to help us select those given the points we just made.
      
      final int holeCount = createHoles?TestUtil.nextInt(random(), 0, 2):0;
      
      final List<Polygon> holeList = new ArrayList<>();
      
      for (int i = 0; i < holeCount; i++) {
        // Choose a pole.  The poly has to be within the polygon, but it also cannot be on the polygon edge.
        // If we can't find a good pole we have to give it up and not do the hole.
        for (int k = 0; k < 500; k++) {
          final GeoPoint poleChoice = new GeoPoint(pm, toRadians(randomLat()), toRadians(randomLon()));
          if (!poly.isWithin(poleChoice)) {
            continue;
          }
          // We have a pole within the polygon.  Now try 100 times to build a polygon that does not intersect the outside ring.
          // After that we give up and pick a new pole.
          boolean foundOne = false;
          for (int j = 0; j < 100; j++) {
            final Polygon insidePoly = makePoly(pm, poleChoice, !clockwiseDesired, false);
            // Verify that the inside polygon is OK.  If not, discard and repeat.
            if (!verifyPolygon(pm, insidePoly, poly)) {
              continue;
            }
            holeList.add(insidePoly);
            foundOne = true;
          }
          if (foundOne) {
            break;
          }
        }
      }

      final Polygon[] holes = holeList.toArray(new Polygon[0]);
      
      // Finally, build the polygon and return it
      final double[] lats = new double[polyPoints.size() + 1];
      final double[] lons = new double[polyPoints.size() + 1];
        
      for (int i = 0; i < polyPoints.size(); i++) {
        lats[i] = polyPoints.get(i).getLatitude() * 180.0 / Math.PI;
        lons[i] = polyPoints.get(i).getLongitude() * 180.0 / Math.PI;
      }
      lats[polyPoints.size()] = lats[0];
      lons[polyPoints.size()] = lons[0];
      return new Polygon(lats, lons, holes);
    }
  }
  
  protected static List<GeoPoint> convertToPoints(final PlanetModel pm, final GeoPoint pole, final double[] angles, final double[] arcDistances) {
    // To do the point rotations, we need the sine and cosine of the pole latitude and longitude.  Get it here for performance.
    final double sinLatitude = Math.sin(pole.getLatitude());
    final double cosLatitude = Math.cos(pole.getLatitude());
    final double sinLongitude = Math.sin(pole.getLongitude());
    final double cosLongitude = Math.cos(pole.getLongitude());
    final List<GeoPoint> rval = new ArrayList<>();
    for (int i = 0; i < angles.length; i++) {
      rval.add(createPoint(pm, angles[i], arcDistances[i], sinLatitude, cosLatitude, sinLongitude, cosLongitude));
    }
    return rval;
  }
  
  protected static GeoPoint createPoint(final PlanetModel pm,
    final double angle,
    final double arcDistance,
    final double sinLatitude,
    final double cosLatitude,
    final double sinLongitude,
    final double cosLongitude) {
    // From the angle and arc distance, convert to (x,y,z) in unit space.
    // We want the perspective to be looking down the x axis.  The "angle" measurement is thus in the Y-Z plane.
    // The arcdistance is in X.
    final double x = Math.cos(arcDistance);
    final double yzScale = Math.sin(arcDistance);
    final double y = Math.cos(angle) * yzScale;
    final double z = Math.sin(angle) * yzScale;
    // Now, rotate coordinates so that we shift everything from pole = x-axis to actual coordinates.
    // This transformation should take the point (1,0,0) and transform it to the pole's actual (x,y,z) coordinates.
    // Coordinate rotation formula:
    // x1 = x0 cos T - y0 sin T
    // y1 = x0 sin T + y0 cos T
    // We're in essence undoing the following transformation (from GeoPolygonFactory):
    // x1 = x0 cos az - y0 sin az
    // y1 = x0 sin az + y0 cos az
    // z1 = z0
    // x2 = x1 cos al - z1 sin al
    // y2 = y1
    // z2 = x1 sin al + z1 cos al
    // So, we reverse the order of the transformations, AND we transform backwards.
    // Transforming backwards means using these identities: sin(-angle) = -sin(angle), cos(-angle) = cos(angle)
    // So:
    // x1 = x0 cos al + z0 sin al
    // y1 = y0
    // z1 = - x0 sin al + z0 cos al
    // x2 = x1 cos az + y1 sin az
    // y2 = - x1 sin az + y1 cos az
    // z2 = z1
    final double x1 = x * cosLatitude + z * sinLatitude;
    final double y1 = y;
    final double z1 = - x * sinLatitude + z * cosLatitude;
    final double x2 = x1 * cosLongitude + y1 * sinLongitude;
    final double y2 = - x1 * sinLongitude + y1 * cosLongitude;
    final double z2 = z1;

    // Scale final (x,y,z) to land on planet surface
    // Equation of ellipsoid:  x^2 / a^2 + y^2 / b^2 + z^2 / c^2 - 1 = 0
    // Use a parameterization, e.g. x = t * x2, y = t * y2, z = t * z2, and find t.
    // t^2 ( x2^2 / a^2 + y2^2 / b^2 + z2^2 / c^2 ) = 1
    // t = +/- sqrt( 1 / ( x2^2 / a^2 + y2^2 / b^2 + z2^2 / c^2 ) )
    // We want the + variant because we're scaling in the same direction as the original vector.
    final double t = Math.sqrt( 1.0 / (x2 * x2 * pm.inverseAbSquared + y2 * y2 * pm.inverseAbSquared + z2 * z2 * pm.inverseCSquared));
    return new GeoPoint(x2 * t, y2 * t, z2 * t);
  }
  
  protected static boolean verifyPolygon(final PlanetModel pm, final Polygon polygon, final GeoPolygon outsidePolygon) {
    // Each point in the new poly should be inside the outside poly, and each edge should not intersect the outside poly edge
    final double[] lats = polygon.getPolyLats();
    final double[] lons = polygon.getPolyLons();
    final List<GeoPoint> polyPoints = new ArrayList<>(lats.length-1);
    for (int i = 0; i < lats.length - 1; i++) {
      final GeoPoint newPoint = new GeoPoint(pm, Math.toRadians(lats[i]), Math.toRadians(lons[i]));
      if (!outsidePolygon.isWithin(newPoint)) {
        return false;
      }
      polyPoints.add(newPoint);
    }
    // We don't need to construct the world to find intersections -- just the bordering planes. 
    for (int planeIndex = 0; planeIndex < polyPoints.size(); planeIndex++) {
      final GeoPoint startPoint = polyPoints.get(planeIndex);
      final GeoPoint endPoint = polyPoints.get(legalIndex(planeIndex + 1, polyPoints.size()));
      final GeoPoint beforeStartPoint = polyPoints.get(legalIndex(planeIndex - 1, polyPoints.size()));
      final GeoPoint afterEndPoint = polyPoints.get(legalIndex(planeIndex + 2, polyPoints.size()));
      final SidedPlane beforePlane = new SidedPlane(endPoint, beforeStartPoint, startPoint);
      final SidedPlane afterPlane = new SidedPlane(startPoint, endPoint, afterEndPoint);
      final Plane plane = new Plane(startPoint, endPoint);
      
      // Check for intersections!!
      if (outsidePolygon.intersects(plane, null, beforePlane, afterPlane)) {
        return false;
      }
    }
    return true;
  }
  
  protected static int legalIndex(int index, int size) {
    if (index >= size) {
      index -= size;
    }
    if (index < 0) {
      index += size;
    }
    return index;
  }
}
