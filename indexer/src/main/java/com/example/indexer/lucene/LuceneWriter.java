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

    /**
     * Idempotent update: replace document with same 'id' term.
     */
    public synchronized void updateDocument(IndexDocument d) throws IOException {
        Document luceneDoc = new Document();

        luceneDoc.add(new StringField("id", d.getId(), Field.Store.YES));
        luceneDoc.add(new TextField("path", d.getPath(), Field.Store.YES));
        if (d.getRepo() != null) luceneDoc.add(new StringField("repo", d.getRepo(), Field.Store.YES));
        if (d.getLang() != null) luceneDoc.add(new StringField("lang", d.getLang(), Field.Store.YES));
        // index code as text (not stored to keep index small), but you can store if you want
        if (d.getCode() != null) luceneDoc.add(new TextField("code", d.getCode(), Field.Store.NO));

        // updateDocument will delete existing doc with the Term("id", ...) and add this one atomically
        writer.updateDocument(new Term("id", d.getId()), luceneDoc);
        writer.commit();
    }

    public void addDocument(IndexDocument doc) throws IOException {
        Document luceneDoc = new Document();

        // CODE: phrase + highlight
        FieldType codeType = new FieldType();
        codeType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        codeType.setStored(true);
        codeType.setTokenized(true);
        codeType.freeze();

        // PATH: keyword-ish, no phrase
        FieldType pathType = new FieldType();
        pathType.setIndexOptions(IndexOptions.DOCS);
        pathType.setStored(true);
        pathType.setTokenized(false);
        pathType.freeze();

        // REPO: searchable but no phrase
        FieldType repoType = new FieldType();
        repoType.setIndexOptions(IndexOptions.DOCS);
        repoType.setStored(true);
        repoType.setTokenized(false);
        repoType.freeze();


        luceneDoc.add(new StringField("id", doc.getId(), Field.Store.YES));
//        luceneDoc.add(new TextField("path", doc.getPath(), Field.Store.YES));
//        luceneDoc.add(new TextField("repo", doc.getRepo(), Field.Store.YES));
//        luceneDoc.add(new TextField("code", doc.getCode(), Field.Store.YES));
        luceneDoc.add(new Field("path", doc.getPath(), pathType));
        luceneDoc.add(new Field("repo", doc.getRepo(), repoType));
        luceneDoc.add(new Field("code", doc.getCode(), codeType));
        luceneDoc.add(new TextField("lang", doc.getLang(), Field.Store.YES));

        writer.addDocument(luceneDoc);
        writer.commit();
    }

    public synchronized long getNumDocs() throws IOException {
        return writer.numRamDocs();
    }

    public void close() throws IOException {
        writer.close();
    }
}
