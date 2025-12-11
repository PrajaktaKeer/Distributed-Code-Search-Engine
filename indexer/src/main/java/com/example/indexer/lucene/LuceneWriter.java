package com.example.indexer.lucene;

import com.example.indexer.model.IndexDocument;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Paths;

@Component
public class LuceneWriter {

    private static final String INDEX_DIR = "lucene-index";

    private final IndexWriter writer;

    public LuceneWriter() throws IOException {
        FSDirectory dir = FSDirectory.open(Paths.get(INDEX_DIR));
        StandardAnalyzer analyzer = new StandardAnalyzer();

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        this.writer = new IndexWriter(dir, config);
    }

    public void addDocument(IndexDocument doc) throws IOException {
        Document luceneDoc = new Document();

        luceneDoc.add(new StringField("id", doc.getId(), Field.Store.YES));
        luceneDoc.add(new TextField("path", doc.getPath(), Field.Store.YES));
        luceneDoc.add(new TextField("repo", doc.getRepo(), Field.Store.YES));
        luceneDoc.add(new TextField("code", doc.getCode(), Field.Store.YES));
        luceneDoc.add(new TextField("lang", doc.getLang(), Field.Store.YES));

        writer.addDocument(luceneDoc);
        writer.commit();
    }
}
