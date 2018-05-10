package com.arge.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.*;
import org.apache.lucene.spatial.SpatialStrategy;
import org.apache.lucene.spatial.prefix.RecursivePrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.GeohashPrefixTree;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.spatial.query.SpatialArgs;
import org.apache.lucene.spatial.query.SpatialArgsParser;
import org.apache.lucene.spatial.query.SpatialOperation;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.distance.DistanceUtils;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Shape;

import java.io.IOException;
import java.text.ParseException;

public class SpatialExample {

  public static void main(String[] args) throws IOException, ParseException {
    new SpatialExample().test();
  }

  private void test() throws IOException, ParseException {
    init();
    indexPoints();
    search();
  }

  /**
   * The Spatial4j {@link SpatialContext} is a sort of global-ish singleton
   * needed by Lucene spatial.  It's a facade to the rest of Spatial4j, acting
   * as a factory for {@link Shape}s and provides access to reading and writing
   * them from Strings.
   */
  private SpatialContext spatialContext;//"spatialContext" is the conventional variable NAME

  /**
   * The Lucene spatial {@link SpatialStrategy} encapsulates an approach to
   * indexing and searching shapes, and providing distance values for them.
   * It's a simple API to unify different approaches. You might use more than
   * one spatialStrategy for a shape as each spatialStrategy has its strengths and weaknesses.
   * <p/>
   * Note that these are initialized with a field NAME.
   */
  private SpatialStrategy spatialStrategy;

  private Directory directory;

  private void init() {
    //Typical geospatial context
    //  These can also be constructed from SpatialContextFactory
    this.spatialContext = SpatialContext.GEO;

    int maxLevels = 11;//results in sub-meter precision for geohash
    //TODO demo lookup by detail distance
    //  This can also be constructed from SpatialPrefixTreeFactory
    SpatialPrefixTree grid = new GeohashPrefixTree(spatialContext, maxLevels);

    this.spatialStrategy = new RecursivePrefixTreeStrategy(grid, "myGeoField");

    this.directory = new RAMDirectory();
  }

  private void indexPoints() throws IOException, ParseException {
    IndexWriterConfig iwConfig = new IndexWriterConfig(null);
    IndexWriter indexWriter = new IndexWriter(directory, iwConfig);

    //Spatial4j is x-y order for arguments
    indexWriter.addDocument(newSampleDocument(2, spatialContext.makePoint(-80.93, 33.77)));

    //Spatial4j has a WKT parser which is also "x y" order
    indexWriter.addDocument(newSampleDocument(4, spatialContext.readShapeFromWkt("POINT(60.9289094 -50.7693246)")));

    indexWriter.addDocument(newSampleDocument(20, spatialContext.makePoint(0.1, 0.1), spatialContext.makePoint(0, 0)));

    indexWriter.close();
  }

  private Document newSampleDocument(int id, Shape... shapes) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    doc.add(new NumericDocValuesField("id", id));
    //Potentially more than one shape in this field is supported by some
    // strategies; see the javadocs of the SpatialStrategy impl to see.
    for (Shape shape : shapes) {
      for (Field f : spatialStrategy.createIndexableFields(shape)) {
        doc.add(f);
      }
      //store it too; the format is up to you
      //  (assume point in this example)
      Point pt = (Point) shape;
      doc.add(new StoredField(spatialStrategy.getFieldName(), pt.getX() + " " + pt.getY()));
    }

    return doc;
  }

  private void search() throws IOException, ParseException {
    IndexReader indexReader = DirectoryReader.open(directory);
    IndexSearcher indexSearcher = new IndexSearcher(indexReader);
    Sort idSort = new Sort(new SortField("id", SortField.Type.INT));

    //--Filter by circle (<= distance from a point)
    {
      //Search with circle
      //note: SpatialArgs can be parsed from a string
      SpatialArgs spatialArgs = new SpatialArgs(SpatialOperation.Intersects, spatialContext.makeCircle(-80.0, 33.0, DistanceUtils.dist2Degrees(200, DistanceUtils.EARTH_MEAN_RADIUS_KM)));
      Query query = spatialStrategy.makeQuery(spatialArgs);
      TopDocs docs = indexSearcher.search(query, 10, idSort);
      assertDocMatchedIds(indexSearcher, docs, 2);
      //Now, lets get the distance for the 1st doc via computing from stored point value:
      // (this computation is usually not redundant)
      Document doc1 = indexSearcher.doc(docs.scoreDocs[0].doc);
      String doc1Str = doc1.getField(spatialStrategy.getFieldName()).stringValue();
      //assume doc1Str is "x y" as written in newSampleDocument()
      int spaceIdx = doc1Str.indexOf(' ');
      double x = Double.parseDouble(doc1Str.substring(0, spaceIdx));
      double y = Double.parseDouble(doc1Str.substring(spaceIdx + 1));
      double doc1DistDEG = spatialContext.calcDistance(spatialArgs.getShape().getCenter(), x, y);
      print("Equals", "121.6d", (doc1DistDEG * DistanceUtils.DEG_TO_KM));
    }
    //--Match all, order by distance ascending
    {
      Point pt = spatialContext.makePoint(60, -50);
      DoubleValuesSource valueSource = spatialStrategy.makeDistanceValueSource(pt, DistanceUtils.DEG_TO_KM);//the distance (in km)
      Sort distSort = new Sort(valueSource.getSortField(false)).rewrite(indexSearcher);//false=asc dist
      TopDocs docs = indexSearcher.search(new MatchAllDocsQuery(), 10, distSort);
      assertDocMatchedIds(indexSearcher, docs, 4, 20, 2);
      //To get the distance, we could compute from stored values like earlier.
      // However in this example we sorted on it, and the distance will get
      // computed redundantly.  If the distance is only needed for the top-X
      // search results then that's not a big deal. Alternatively, try wrapping
      // the ValueSource with CachingDoubleValueSource then retrieve the value
      // from the ValueSource now. See lucene-4541 for an example.
    }
    //demo arg parsing
    {
      SpatialArgs args = new SpatialArgs(SpatialOperation.Intersects,
          spatialContext.makeCircle(-80.0, 33.0, 1));
      SpatialArgs args2 = new SpatialArgsParser().parse("Intersects(BUFFER(POINT(-80 33),1))", spatialContext);
      print("Equals", args, args2);
    }

    indexReader.close();
  }

  private void assertDocMatchedIds(IndexSearcher indexSearcher, TopDocs docs, int... ids) throws IOException {
    int[] gotIds = new int[Math.toIntExact(docs.totalHits)];
    for (int i = 0; i < gotIds.length; i++) {
      gotIds[i] = indexSearcher.doc(docs.scoreDocs[i].doc).getField("id").numericValue().intValue();
    }
    print("Equals", ids.length, gotIds.length);
  }

  private void print(String key) {
    System.out.println(key.toUpperCase());
  }

  private void print(String key, Object v1, Object v2) {
    System.out.println(key + "\t" + String.valueOf(v1) + "\t" + String.valueOf(v2));
  }
}
