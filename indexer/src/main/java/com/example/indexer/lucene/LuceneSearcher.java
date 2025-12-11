package com.example.indexer.lucene;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;


@Component
public class LuceneSearcher {

    private IndexSearcher searcher;
    private DirectoryReader reader;

    private LuceneSearcher() throws IOException {
        reader = DirectoryReader.open(
                FSDirectory.open(Paths.get("lucene-index"))
        );

        searcher = new IndexSearcher(reader);

//        System.out.println("Reading from index");

    }

    @PreDestroy
    public void shutdown() throws IOException {
        System.out.println("🔻 Closing Lucene reader...");
        reader.close();
    }

    public List<SearchResult> search(String queryText, int topN) throws Exception {

        QueryParser parser = new QueryParser("code", new StandardAnalyzer());

        Query query = parser.parse(queryText);
        TopDocs docs = searcher.search(query, topN);

        List<SearchResult> results = new ArrayList<>();

        for (ScoreDoc sd : docs.scoreDocs) {
            Document d = searcher.doc(sd.doc);
            results.add(new SearchResult(d.get("path"), sd.score));
        }

        return results;

    }
}
