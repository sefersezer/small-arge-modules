package com.arge.lucene;


import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.taxonomy.*;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Shows example usage of category associations. */
public class AssociationsFacetsExample {

  private final Directory indexDir = new RAMDirectory();
  private final Directory taxoDir = new RAMDirectory();
  private final FacetsConfig config;

  /** Empty constructor */
  public AssociationsFacetsExample() {
    config = new FacetsConfig();
    config.setMultiValued("tags", true);
    config.setIndexFieldName("tags", "$tags");
    config.setMultiValued("genre", true);
    config.setIndexFieldName("genre", "$genre");
  }

  /** Build the example index. */
  private void index() throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE);
    IndexWriter indexWriter = new IndexWriter(indexDir, iwc);

    // Writes facet ords to a separate directory from the main index
    DirectoryTaxonomyWriter taxoWriter = new DirectoryTaxonomyWriter(taxoDir);

    Document doc = new Document();
    // 3 occurrences for tag 'lucene'
    doc.add(new IntAssociationFacetField(3, "tags", "lucene"));
    // 87% confidence level of genre 'computing'
    doc.add(new FloatAssociationFacetField(0.87f, "genre", "computing"));
    indexWriter.addDocument(config.build(taxoWriter, doc));

    doc = new Document();
    // 1 occurrence for tag 'lucene'
    doc.add(new IntAssociationFacetField(1, "tags", "lucene"));
    // 2 occurrence for tag 'solr'
    doc.add(new IntAssociationFacetField(2, "tags", "solr"));
    // 75% confidence level of genre 'computing'
    doc.add(new FloatAssociationFacetField(0.75f, "genre", "computing"));
    // 34% confidence level of genre 'software'
    doc.add(new FloatAssociationFacetField(0.34f, "genre", "software"));
    indexWriter.addDocument(config.build(taxoWriter, doc));

    indexWriter.close();
    taxoWriter.close();
  }

  /** User runs a query and aggregates facets by summing their association values. */
  private List<FacetResult> sumAssociations() throws IOException {
    DirectoryReader indexReader = DirectoryReader.open(indexDir);
    IndexSearcher searcher = new IndexSearcher(indexReader);
    TaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);

    FacetsCollector fc = new FacetsCollector();

    // MatchAllDocsQuery is for "browsing" (counts facets
    // for all non-deleted docs in the index); normally
    // you'd use a "normal" query:
    FacetsCollector.search(searcher, new MatchAllDocsQuery(), 10, fc);

    Facets tags = new TaxonomyFacetSumIntAssociations("$tags", taxoReader, config, fc);
    Facets genre = new TaxonomyFacetSumFloatAssociations("$genre", taxoReader, config, fc);

    // Retrieve results
    List<FacetResult> results = new ArrayList<>();
    results.add(tags.getTopChildren(10, "tags"));
    results.add(genre.getTopChildren(10, "genre"));

    indexReader.close();
    taxoReader.close();

    return results;
  }


  /** Runs summing association example. */
  public List<FacetResult> runSumAssociations() throws IOException {
    index();
    return sumAssociations();
  }

  /** Runs the sum int/float associations examples and prints the results. */
  public static void main(String[] args) throws Exception {
    System.out.println("Sum associations example:");
    System.out.println("-------------------------");
    List<FacetResult> results = new AssociationsFacetsExample().runSumAssociations();
    System.out.println("tags: " + results.get(0));
    System.out.println("genre: " + results.get(1));
  }
}