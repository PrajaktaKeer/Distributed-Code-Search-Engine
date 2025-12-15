package com.example.indexer.lucene;

import com.example.indexer.model.IndexDocument;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
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
    public synchronized void updateDocument(IndexDocument doc) throws IOException {

        String oldHash = getExistingHash(doc.getId());

        if (oldHash != null && oldHash.equals(doc.getHash())) {
            System.out.println("⏭️ Skipping unchanged file: " + doc.getPath());
            return;
        }

        addDocument(doc);

        System.out.println("✅ Indexed/Updated Doc: " + doc.getPath());
    }


    private String getExistingHash(String docId) throws IOException {

        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            IndexSearcher tempSearcher = new IndexSearcher(reader);

            Query q = new TermQuery(new Term("id", docId));
            TopDocs hits = tempSearcher.search(q, 1);

            if (hits.totalHits.value == 0) {
                return null;
            }

            Document existing = tempSearcher.doc(hits.scoreDocs[0].doc);
            return existing.get("hash");
        }
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
        luceneDoc.add(new StringField("hash", doc.getHash(), Field.Store.YES));
        luceneDoc.add(new TextField("symbols", extractSymbols(doc.getCode()), Field.Store.NO));

        writer.addDocument(luceneDoc);
        writer.commit();
    }

    private String extractSymbols(String code) {
        return code
                .replaceAll("[^a-zA-Z0-9_]", " ")
                .replaceAll("\\b(class|public|private|void|return)\\b", "");
    }


    public synchronized long getNumDocs() throws IOException {
        return writer.numRamDocs();
    }

    public void close() throws IOException {
        writer.close();
    }
}
