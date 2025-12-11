package com.example.indexer.api;

import com.example.indexer.lucene.LuceneSearcher;
import com.example.indexer.lucene.SearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final LuceneSearcher searcher;

    public SearchController(LuceneSearcher searcher) {
        this.searcher = searcher;
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam String q,
                                     @RequestParam(defaultValue = "20") int n) throws Exception {
        return searcher.search(q, n);
    }
}

