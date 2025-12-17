package com.example.indexer.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.apache.lucene.search.ScoreDoc;

import java.util.List;

@Data
@AllArgsConstructor
@Getter
@Setter
public class SearchResult {
    private String path;
    private float score;
    private String snippet;
    private String repo;

    @JsonIgnore
    private String hash;
}

