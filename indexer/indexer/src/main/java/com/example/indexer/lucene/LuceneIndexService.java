package com.example.indexer.lucene;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@Service
public class LuceneIndexService {

    private IndexWriter indexWriter;

    @PostConstruct
    public void init() throws Exception {
        Path indexPath = Path.of("/home/prajakta/dcse_index");  // store index here
        FSDirectory directory = FSDirectory.open(indexPath);

        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        indexWriter = new IndexWriter(directory, config);

        System.out.println("🔥 Lucene IndexWriter initialized at: " + indexPath);
    }

    public void indexDocument(Map<String, Object> doc) throws IOException {
        Document luceneDoc = new Document();

        luceneDoc.add(new StringField("id", doc.get("id").toString(), Field.Store.YES));
        luceneDoc.add(new StringField("path", doc.get("path").toString(), Field.Store.YES));

        luceneDoc.add(new TextField("code_snippet", doc.get("code_snippet").toString(), Field.Store.YES));
        luceneDoc.add(new StringField("lang", doc.get("lang").toString(), Field.Store.YES));

        indexWriter.addDocument(luceneDoc);
        indexWriter.commit();

        System.out.println("📝 Indexed: " + doc.get("path"));
    }
}
