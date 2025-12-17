package com.example.indexer.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RankSignals {
    boolean isController;
    boolean hasMapping;
    boolean isTest;
    boolean isConfig;
    int repoFrequency;
}

