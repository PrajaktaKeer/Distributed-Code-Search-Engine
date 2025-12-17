package com.example.indexer.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.util.CharsRef;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.lucene.analysis.synonym.SynonymMap;

@Component
public class SearchAnalyzer {

    public Analyzer build() throws IOException {
        SynonymMap synonymMap = loadSynonyms();

        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String field) {
                Tokenizer tokenizer = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(tokenizer);
                stream = new SynonymGraphFilter(stream, synonymMap, true);
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    private SynonymMap loadSynonyms() throws IOException {
        Path path = Paths.get("src/main/resources/synonyms.txt");

        SynonymMap.Builder builder = new SynonymMap.Builder(true);
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=>");
                String from = parts[0].trim();
                for (String to : parts[1].split(",")) {
                    builder.add(
                            new CharsRef(from),
                            new CharsRef(to.trim()),
                            true
                    );
                }
            }
        }
        return builder.build();
    }
}

