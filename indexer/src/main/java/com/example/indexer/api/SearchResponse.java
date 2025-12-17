package com.example.indexer.api;

import com.example.indexer.api.SearchPage;

import java.util.List;

public class SearchResponse {

    public List<SearchResult> results;

    // Cursor for next page
    public Integer lastDoc;
    public Float lastScore;

    public long totalHits;
    public int pageSize;

    public static SearchResponse from(SearchPage page) {
        SearchResponse r = new SearchResponse();
        r.results = page.results;
        r.totalHits = page.totalHits;
        r.pageSize = page.pageSize;

        if (page.lastScoreDoc != null) {
            r.lastDoc = page.lastScoreDoc.doc;
            r.lastScore = page.lastScoreDoc.score;
        }

        return r;
    }
}
