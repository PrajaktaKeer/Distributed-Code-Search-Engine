package com.example.indexer.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IndexDocument {

    private String id;
    private String repo;
    private String path;
    private String code;
    private String lang;
}
