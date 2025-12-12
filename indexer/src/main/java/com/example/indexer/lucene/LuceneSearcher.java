package com.example.indexer.lucene;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component
public class LuceneSearcher {

    private IndexSearcher searcher;
    private DirectoryReader reader;
    private final MultiFieldQueryParser parser;
    private final Analyzer analyzer;
    private final Path indexPath = Paths.get("lucene-index");

//    private LuceneSearcher() {
//        try {
//            reader = DirectoryReader.open(
//                    FSDirectory.open(Paths.get("lucene-index"))
//            );
//        } catch (IOException e) {
//            System.out.println("Error while reading Lucene Index. " + e.getMessage());
//        }
//
//        if(reader != null) searcher = new IndexSearcher(reader);
//        analyzer = new StandardAnalyzer();
//
//        Map<String, Float> boosts = new HashMap<>();
//        boosts.put("path", 2.0f);
//        boosts.put("repo", 1.5f);
//        boosts.put("code", 1.0f);
//        boosts.put("lang", 0.5f);
//
//        parser = new MultiFieldQueryParser(
//                new String[]{"code", "path", "repo", "lang"},
//                analyzer,
//                boosts
//        );
//        parser.setDefaultOperator(QueryParser.Operator.AND);
//    }

    public LuceneSearcher() throws IOException {
        analyzer = new StandardAnalyzer();

        Map<String, Float> boosts = new HashMap<>();
        boosts.put("path", 2.0f);
        boosts.put("repo", 1.5f);
        boosts.put("code", 1.0f);
        boosts.put("lang", 0.5f);

        parser = new MultiFieldQueryParser(
                new String[]{"code", "path", "repo", "lang"},
                analyzer,
                boosts
        );
        parser.setDefaultOperator(QueryParser.Operator.AND);

        // initialize reader + searcher
        initReader();

        // start auto-refresh background thread
        startAutoRefresher();
    }

    private void initReader() throws IOException {
        if (!Files.exists(indexPath)) {
            Files.createDirectories(indexPath);
        }

        if (!DirectoryReader.indexExists(FSDirectory.open(indexPath))) {
            System.out.println("⚠️ No index found yet. Waiting for writer to create segments...");
            return;
        }

        this.reader = DirectoryReader.open(FSDirectory.open(indexPath));
        this.searcher = new IndexSearcher(reader);
        System.out.println("✅ LuceneSearcher initialized with index.");
    }

    private void startAutoRefresher() {
        Thread refresher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(2000);

                    FSDirectory dir = FSDirectory.open(indexPath);

                    // CASE 1: reader not initialized yet
                    if (reader == null) {
                        if (DirectoryReader.indexExists(dir)) {
                            System.out.println("🔄 Index created — initializing reader...");
                            initReader();
                        }
                        continue;
                    }

                    // CASE 2: reader exists → look for updated segments
                    DirectoryReader newReader = DirectoryReader.openIfChanged(reader);
                    if (newReader != null) {
                        reader.close();
                        reader = newReader;
                        searcher = new IndexSearcher(reader);
                        System.out.println("🔄 Lucene index refreshed.");
                    }

                } catch (Exception e) {
                    System.out.println("Index refresh error: " + e.getMessage());
                }
            }
        });

        refresher.setDaemon(true);
        refresher.start();
    }


    @PreDestroy
    public void shutdown() throws IOException {
        System.out.println("🔻 Closing Lucene reader...");
        reader.close();
    }

    private String highlight(String field, String text, Query query) {
        try {
            QueryScorer scorer = new QueryScorer(query, field);
            Fragmenter fragmenter = new SimpleSpanFragmenter(scorer, 120);

            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<b>", "</b>");
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(fragmenter);

            TokenStream ts = analyzer.tokenStream(field, text);
            return highlighter.getBestFragment(ts, text);
        } catch (Exception e) {
            return null;
        }
    }


    public List<SearchResult> search(String queryText, int topN) throws Exception {

//        QueryParser parser = new QueryParser("code", new StandardAnalyzer());

        Query query = parser.parse(queryText);
        TopDocs docs = searcher.search(query, topN);

        List<SearchResult> results = new ArrayList<>();

        for (ScoreDoc sd : docs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);
            // read file contents for highlighting
            String code = Files.readString(Paths.get(doc.get("path")));

            String snippet = highlight("code", code, query);

            results.add(new SearchResult(doc.get("path"), sd.score, snippet));
//            results.add(new SearchResult(d.get("path"), sd.score));
        }

        return results;

    }
}
