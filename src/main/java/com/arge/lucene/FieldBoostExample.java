package com.arge.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.ClassicAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.RAMDirectory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FieldBoostExample implements CommandLineRunner {

  @Override
  public void run(String... args) throws Exception {
    System.out.println("\nstage 1\n");
    createIndexWithoutBoost();
    searchIndex("samsung");

    ramDirectory = new RAMDirectory();

    System.out.println("\nstage 2\n");
    createIndexWithoutBoost();
    searchIndex("samsung", 2.0f);

  }

  private enum Columns {
    TITLE,
    NAME;
  }

  private enum Titles {
    TELEFON,
    BILGISAYAR,
    MONITOR;
  }


  private Analyzer analyzer = new ClassicAnalyzer();
  private RAMDirectory ramDirectory = new RAMDirectory();
  private IndexWriter indexWriter;

  private void createIndexWithoutBoost() throws IOException {
    IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
    indexWriter = new IndexWriter(ramDirectory, indexWriterConfig);
    createDoc(Titles.TELEFON.name(), "Samsung Galaxy S1");
    createDoc(Titles.TELEFON.name(), "Samsung Galaxy S2");
    createDoc(Titles.TELEFON.name(), "Sony XPeria L1");
    createDoc(Titles.TELEFON.name(), "Samsung Galaxy S4");
    createDoc(Titles.BILGISAYAR.name(), "Lenovo L1");
    createDoc(Titles.BILGISAYAR.name(), "Apple Macbook");
    createDoc(Titles.BILGISAYAR.name(), "Samsung L3");
    createDoc(Titles.MONITOR.name(), "Philips 22");
    createDoc(Titles.MONITOR.name(), "Samsung 22");
    createDoc(Titles.MONITOR.name(), "LG 22");
    indexWriter.close();
  }

  private void createDoc(String title, String name) throws IOException {
    Document doc = new Document();

    Field titleField = new TextField(Columns.TITLE.name(), title, Field.Store.YES);
    doc.add(titleField);

    Field nameField = new TextField(Columns.NAME.name(), name, Field.Store.YES);
    doc.add(nameField);

    indexWriter.addDocument(doc);
  }

  private void searchIndex(String term, Float boostValue) throws IOException {
    IndexReader indexReader = DirectoryReader.open(ramDirectory);
    IndexSearcher indexSearcher = new IndexSearcher(indexReader);

    TermQuery nameTermQuery = new TermQuery(new Term(Columns.NAME.name(), term));
    TopDocs topDocs;

    if (boostValue != null) {
      TermQuery titleTermQuery = new TermQuery(new Term(Columns.TITLE.name(), "telefon"));
      titleTermQuery.createWeight(indexSearcher, true, boostValue);

      BooleanQuery nameAndTitleQuery = new BooleanQuery
          .Builder()
          .add(nameTermQuery, BooleanClause.Occur.MUST) // NAME alanında sadece {text} eşleşmeleri gelsin. diğer kayıtlar kesinlikle gelmesin
          .add(titleTermQuery, BooleanClause.Occur.SHOULD) // başlığı "TELEFON" olanlar gelebilir. TELEFON başlıklarına boost uygulanmıştır.
          .build();

      topDocs = indexSearcher.search(nameAndTitleQuery, 10);
    } else {
      topDocs = indexSearcher.search(nameTermQuery, 10);
    }

    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
      Document currentDocument = indexSearcher.doc(scoreDoc.doc);
      String resultString = "ScoreDoc";
      resultString += ": ";
      resultString += String.valueOf(scoreDoc.doc);
      resultString += "\t";
      resultString += currentDocument.get(Columns.TITLE.name());
      resultString += "\t";
      resultString += currentDocument.get(Columns.NAME.name());
      resultString += "\n---------------------\n";
      System.out.println(resultString);
    }
    ramDirectory.close();
  }

  private void searchIndex(String term) throws IOException {
    searchIndex(term, null);
  }
}
