package com.example.indexer.api;

import com.example.indexer.lucene.LuceneSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final LuceneSearcher searcher;

    public SearchController(LuceneSearcher searcher) {
        this.searcher = searcher;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String q,
                                 @RequestParam(defaultValue = "20") int n,
                                 @RequestParam(required = false) Integer lastDoc,
                                 @RequestParam(required = false) Float lastScore) throws Exception {
//        return searcher.search(q, n);

        ScoreDoc searchAfter = null;

        if (lastDoc != null && lastScore != null) {
            searchAfter = new ScoreDoc(lastDoc, lastScore);
        }

        SearchPage page = searcher.search(q, n, searchAfter);
        return SearchResponse.from(page);
    }

    @GetMapping("/search/explain")
    public String explain(
            @RequestParam String q,
            @RequestParam String hash
    ) throws Exception {
        return searcher.explainByHash(q, hash);
    }

}

