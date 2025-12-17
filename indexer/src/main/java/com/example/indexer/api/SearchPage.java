package com.example.indexer.api;

import org.apache.lucene.search.ScoreDoc;
import java.util.List;

public class SearchPage {

    public List<SearchResult> results;
    public ScoreDoc lastScoreDoc;
    public long totalHits;
    public int pageSize;

    public SearchPage(
            List<SearchResult> results,
            ScoreDoc lastScoreDoc,
            long totalHits,
            int pageSize
    ) {
        this.results = results;
        this.lastScoreDoc = lastScoreDoc;
        this.totalHits = totalHits;
        this.pageSize = pageSize;
    }
}
