package com.example.indexer.lucene;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

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
