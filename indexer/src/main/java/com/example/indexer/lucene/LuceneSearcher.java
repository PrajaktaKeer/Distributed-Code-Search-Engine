package com.example.indexer.lucene;

import com.example.indexer.api.RankSignals;
import com.example.indexer.api.SearchPage;
import com.example.indexer.api.SearchResult;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
import org.apache.lucene.util.Counter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
public class LuceneSearcher {

    private IndexSearcher searcher;
    private DirectoryReader reader;
    private final MultiFieldQueryParser parser;
    private final Analyzer analyzer;
    private final Path indexPath = Paths.get("lucene-index");

    private enum QueryIntent {
        REPO,
        ENDPOINT,
        CODE
    }

    public LuceneSearcher(SearchAnalyzer analyzerBuilder) throws IOException {
        this.analyzer = analyzerBuilder.build();

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

    private QueryIntent detectIntent(String q) {
        String lq = q.toLowerCase();

        if (lq.contains("/") ||
                lq.contains("@get") ||
                lq.contains("@post") ||
                lq.contains("controller") ||
                lq.contains("mapping")) {
            return QueryIntent.ENDPOINT;
        }

        if (lq.matches("[a-z0-9\\-]+") && lq.length() < 20) {
            return QueryIntent.REPO;
        }

        return QueryIntent.CODE;
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
            Fragmenter fragmenter = new SimpleSpanFragmenter(scorer, 200);

            SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<b>", "</b>");
            Highlighter highlighter = new Highlighter(formatter, scorer);
            highlighter.setTextFragmenter(fragmenter);

            TokenStream ts = analyzer.tokenStream(field, text);
            String fragment = highlighter.getBestFragment(ts, text);

            // 1️⃣ If Lucene found a good fragment, use it
            if (fragment != null && !fragment.contains("Copyright")) {
                return fragment;
            }

            // 2️⃣ Prefer annotation lines
            String annotationSnippet = extractImportantLines(text,
                    "@RequestMapping",
                    "@GetMapping",
                    "@PostMapping",
                    "@PutMapping",
                    "@DeleteMapping",
                    "@RestController",
                    "@Controller"
            );

            if (annotationSnippet != null) {
                return annotationSnippet;
            }

            // 3️⃣ Fallback: first meaningful code (skip comments)
            return firstCodeLines(text, 8);

        } catch (Exception e) {
            return null;
        }
    }

    private String extractImportantLines(String text, String... markers) {
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            for (String marker : markers) {
                if (lines[i].contains(marker)) {
                    return joinLines(lines, i, 5);
                }
            }
        }
        return null;
    }

    private String firstCodeLines(String text, int maxLines) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // Skip license & comments
            if (trimmed.startsWith("/*") ||
                    trimmed.startsWith("*") ||
                    trimmed.startsWith("//") ||
                    trimmed.isEmpty()) {
                continue;
            }

            sb.append(line).append("\n");
            count++;

            if (count >= maxLines) break;
        }

        return sb.toString();
    }

    private String joinLines(String[] lines, int start, int range) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < Math.min(lines.length, start + range); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }



    private Query buildQuery(String q) throws Exception {

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        QueryIntent intent = detectIntent(q);

        if (intent == QueryIntent.ENDPOINT) {
            builder.add(new TermQuery(new Term("is_controller", "true")),
                    BooleanClause.Occur.MUST);
        }

        String lq = q.toLowerCase();

        if (lq.contains("api") || lq.contains("rest")) {
            builder.add(
                    new BoostQuery(
                            new TermQuery(new Term("is_controller", "true")),
                            6.0f
                    ),
                    BooleanClause.Occur.SHOULD
            );

            builder.add(
                    new BoostQuery(
                            new TermQuery(new Term("has_mapping", "true")),
                            7.0f
                    ),
                    BooleanClause.Occur.SHOULD
            );
        }

        if (lq.contains("request")) {
            builder.add(
                    new BoostQuery(
                            new TermQuery(new Term("code", "request")),
                            4.0f
                    ),
                    BooleanClause.Occur.SHOULD
            );
        }

        // 1️⃣ Multi-field keyword search
        builder.add(parser.parse(q), BooleanClause.Occur.SHOULD);

        // 2️⃣ Phrase query ONLY on code field
        PhraseQuery phrase = new PhraseQuery.Builder()
                .add(new Term("code", q.toLowerCase()))
                .build();

        builder.add(new BoostQuery(phrase, 3.0f), BooleanClause.Occur.SHOULD);

        Query symbolQuery = new BoostQuery(
                new QueryParser("symbols", analyzer).parse(q),
                4.0f
        );
        builder.add(symbolQuery, BooleanClause.Occur.SHOULD);

        return builder.build();
    }

//    public SearchPage search(String queryText, int topN, ScoreDoc searchAfter) throws Exception {
//
//        if (searcher == null) {
//            throw new IllegalStateException("Lucene index not ready");
//        }
//
//        Query query = buildQuery(queryText);
//        QueryIntent intent = detectIntent(queryText);
//
//        TopScoreDocCollector topCollector =
//                TopScoreDocCollector.create(topN, searchAfter, topN);
//
//        Counter counter = Counter.newCounter();
//        TimeLimitingCollector timeLimiter =
//                new TimeLimitingCollector(topCollector, counter, 200);
//
//        try {
//            searcher.search(query, timeLimiter);
//        } catch (TimeLimitingCollector.TimeExceededException e) {
//            throw new RuntimeException("Search timeout exceeded");
//        }
//
//        TopDocs docs = topCollector.topDocs();
//
//        Map<String, Integer> repoCounts = new HashMap<>();
//        List<SearchResult> results = new ArrayList<>();
//
//        for (ScoreDoc sd : docs.scoreDocs) {
//            Document doc = searcher.doc(sd.doc);
//
//            String repo = doc.get("repo");
//            String path = doc.get("path");
//
//            float adjustedScore = sd.score;
//
//            String isController = doc.get("is_controller");
//            String hasMapping = doc.get("has_mapping");
//
//            if (intent == QueryIntent.ENDPOINT) {
//                if ("true".equals(isController) && "true".equals(hasMapping)) {
//                    adjustedScore *= 4.0f; // strong boost
//                }
//            }
//
//            if (intent == QueryIntent.REPO) {
//                // Penalize controllers for repo searches
//                if ("true".equals(isController)) {
//                    adjustedScore *= 0.3f;
//                }
//
//                // Boost repo entry points
//                if (path.endsWith("README.md") ||
//                        path.endsWith("pom.xml") ||
//                        path.endsWith("build.gradle") ||
//                        path.contains("PetClinicApplication")) {
//                    adjustedScore *= 4.0f;
//                }
//
//                if (path.contains("/test/") ||
//                        path.contains("/db/") ||
//                        path.contains("/k8s/") ||
//                        path.endsWith(".sql")) {
//                    adjustedScore *= 0.2f;
//                }
//            }
//
//            // 🔥 DEMOTE TEST FILES
//            if (path.contains("/test/") || path.contains("Tests")) {
//                adjustedScore *= 0.3f;
//            }
//
//            int seenCount = repoCounts.getOrDefault(repo, 0);
//            if (seenCount > 0) {
//                adjustedScore *= Math.pow(0.85, seenCount); // 🔥 soft demotion
//            }
//
//            repoCounts.put(repo, seenCount + 1);
//
//            String code = safeReadFile(path);
//            String snippet = null;
//
//            if (code != null) {
//                snippet = highlight("code", code, query);
//            }
//
//            results.add(new SearchResult(
//                    path,
//                    adjustedScore,
//                    snippet,
//                    repo,
//                    doc.get("hash")
//                    ));
//        }
//
//        ScoreDoc nextCursor =
//                docs.scoreDocs.length == 0 ? null : docs.scoreDocs[docs.scoreDocs.length - 1];
//
//        return new SearchPage(
//                results,
//                nextCursor,
//                docs.totalHits.value,
//                10
//        );
//    }

    public SearchPage search(String queryText, int pageSize, ScoreDoc searchAfter) throws Exception {

        if (searcher == null) {
            throw new IllegalStateException("Lucene index not ready");
        }

        QueryIntent intent = detectIntent(queryText);
        Query query = buildQuery(queryText);

        // =========================
        // PHASE 1 — CANDIDATE FETCH
        // =========================
        int CANDIDATE_POOL = 200;

        TopScoreDocCollector collector =
                TopScoreDocCollector.create(CANDIDATE_POOL, searchAfter, CANDIDATE_POOL);

        Counter counter = Counter.newCounter();
        TimeLimitingCollector timeLimiter =
                new TimeLimitingCollector(collector, counter, 200);

        try {
            searcher.search(query, timeLimiter);
        } catch (TimeLimitingCollector.TimeExceededException e) {
            log.warn("Search timeout — returning partial results");
        }

        TopDocs candidates = collector.topDocs();

        // =========================
        // PHASE 2 — RE-RANKING
        // =========================
        Map<String, Integer> repoSeenCount = new HashMap<>();
        List<SearchResult> reranked = new ArrayList<>();

        for (ScoreDoc sd : candidates.scoreDocs) {

            Document doc = searcher.doc(sd.doc);
            String path = doc.get("path");
            String repo = doc.get("repo");

            float score = sd.score;

            boolean isController = "true".equals(doc.get("is_controller"));
            boolean hasMapping = "true".equals(doc.get("has_mapping"));
            boolean isTest = path.contains("/test/") || path.endsWith("Test.java") || path.endsWith("Tests.java");

            boolean isConfig = path.endsWith(".yml") ||
                            path.endsWith(".yaml") ||
                            path.endsWith(".properties") ||
                            path.endsWith(".sql") ||
                            path.contains("/k8s/");

            int repoFreq = repoSeenCount.getOrDefault(repo, 0);

            RankSignals signals = new RankSignals(
                    isController,
                    hasMapping,
                    isTest,
                    isConfig,
                    repoFreq
            );

            float finalScore = rerank(score, signals, intent, path);

            repoSeenCount.put(repo, repoFreq + 1);

            String snippet = safeSnippet(path, query);

            reranked.add(new SearchResult(
                    path,
                    finalScore,
                    snippet,
                    repo,
                    doc.get("hash")
            ));
        }

        // =========================
        // FINAL SORT + PAGINATION
        // =========================
        reranked.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));

        List<SearchResult> page =
                reranked.stream()
                        .limit(pageSize)
                        .toList();

        ScoreDoc nextCursor =
                candidates.scoreDocs.length == 0
                        ? null
                        : candidates.scoreDocs[Math.min(pageSize - 1, candidates.scoreDocs.length - 1)];

        return new SearchPage(
                page,
                nextCursor,
                candidates.totalHits.value,
                pageSize
        );
    }

    private float rerank(float baseScore, RankSignals s, QueryIntent intent, String path) {
        float score = baseScore;

        // =====================
        // INTENT-AWARE LOGIC
        // =====================
        if (intent == QueryIntent.ENDPOINT) {
            if (s.isController() && s.isHasMapping()) score *= 4.0f;
            if (!s.isController()) score *= 0.2f;
        }

        if (intent == QueryIntent.CODE) {
            if (s.isTest()) score *= 0.3f;
            if (s.isConfig()) score *= 0.4f;
        }

        if (intent == QueryIntent.REPO) {
            if (s.isController()) score *= 0.3f;

            if (path.endsWith("README.md") || path.endsWith("pom.xml") || path.endsWith("build.gradle")) {
                score *= 5.0f;
            }
        }

        // =====================
        // REPO DIVERSITY
        // =====================
        score *= Math.pow(0.85, s.getRepoFrequency());

        return score;
    }

    private String safeSnippet(String path, Query query) {
        try {
            String code = Files.readString(Path.of(path));
            return highlight("code", code, query);
        } catch (Exception e) {
            return null;
        }
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
