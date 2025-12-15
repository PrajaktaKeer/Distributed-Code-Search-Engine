package com.example.indexer.lucene;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
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
        if(reader != null) reader.close();
    }

    private String highlight(String field, String text, Query query) {
        try {
            QueryScorer scorer = new QueryScorer(query, field);
            Fragmenter fragmenter = new SimpleSpanFragmenter(scorer, 150);

            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<b>", "</b>");
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(fragmenter);

            TokenStream ts = analyzer.tokenStream(field, text);
            return highlighter.getBestFragment(ts, text);
        } catch (Exception e) {
            return null;
        }
    }

    private Query buildQuery(String q) throws Exception {

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // 1️⃣ Multi-field keyword search
        builder.add(parser.parse(q), BooleanClause.Occur.SHOULD);

        // 2️⃣ Phrase query ONLY on code field
        PhraseQuery phrase = new PhraseQuery.Builder()
                .add(new Term("code", q.toLowerCase()))
                .build();

//        builder.add(phrase, BooleanClause.Occur.SHOULD);
        builder.add(new BoostQuery(phrase, 3.0f), BooleanClause.Occur.SHOULD);

        // 3️⃣ Prefix autocomplete on code
        builder.add(new PrefixQuery(new Term("code", q.toLowerCase())),
                BooleanClause.Occur.SHOULD);

        Query symbolQuery = new BoostQuery(
                new QueryParser("symbols", analyzer).parse(q),
                4.0f
        );
        builder.add(symbolQuery, BooleanClause.Occur.SHOULD);

        return builder.build();
    }



    public List<SearchResult> search(String queryText, int topN) throws Exception {

        if (searcher == null) return List.of();

//        Query query = parser.parse(queryText);
        Query query = buildQuery(queryText);
        TopDocs docs = searcher.search(query, topN);

//        List<SearchResult> results = new ArrayList<>();
//
//        for (ScoreDoc sd : docs.scoreDocs) {
//            Document doc = searcher.doc(sd.doc);
//            String code = Files.readString(Paths.get(doc.get("path")));
//            String snippet = highlight("code", code, query);
//
//            results.add(new SearchResult(doc.get("path"), sd.score, snippet));
//        }

        Map<String, Integer> repoCounts = new HashMap<>();
        List<SearchResult> results = new ArrayList<>();

        for (ScoreDoc sd : docs.scoreDocs) {
            Document doc = searcher.doc(sd.doc);

            String repo = doc.get("repo");
            String path = doc.get("path");

            float adjustedScore = sd.score;

            int seenCount = repoCounts.getOrDefault(repo, 0);
            if (seenCount > 0) {
                adjustedScore *= Math.pow(0.85, seenCount); // 🔥 soft demotion
            }

            repoCounts.put(repo, seenCount + 1);

            String code = Files.readString(Path.of(path));
            String snippet = highlight("code", code, query);

            results.add(new SearchResult(
                    path,
                    adjustedScore,
                    snippet,
                    repo,
                    doc.get("hash")
                    ));
        }
        return results;
    }

    public String explainByHash(String queryText, String hash) throws Exception {

        Query query = parser.parse(queryText);

        // Search enough docs to find the target
        TopDocs docs = searcher.search(query, 1000);

        for (ScoreDoc sd : docs.scoreDocs) {
            Document d = searcher.doc(sd.doc);

            if (hash.equals(d.get("hash"))) {
                Explanation explanation = searcher.explain(query, sd.doc);
                return explanation.toString();
            }
        }

        return "No matching document found for hash: " + hash;
    }


}
