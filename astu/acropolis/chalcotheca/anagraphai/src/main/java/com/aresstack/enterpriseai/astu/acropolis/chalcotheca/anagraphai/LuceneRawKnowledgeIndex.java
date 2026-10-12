package com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexException;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeDocument;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawTextWindows;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Persistenter Rohindex ({@link RawKnowledgeIndexPort}) in Lucene: ein Dokument je Ressource mit Titel, Volltext,
 * Inhalts-Hash, Snippet und den Ressourcenfeldern; BM25 über Titel und Text, {@link StandardAnalyzer} wie der
 * Passagenindex. Unabhängig vom Embedding-Modell (kein Namespace): ein Dokument ist durchsuchbar, sobald es geladen
 * ist.
 *
 * <p>Anders als der Passagenindex hält er einen {@link IndexWriter} offen und liest über einen
 * {@link SearcherManager}: {@link #put} aus mehreren Threads gleichzeitig ist erlaubt (Lucene serialisiert selbst nur
 * den Commit), Suchen sehen jedes {@code put} sofort nach seiner Rückkehr. Nach {@link #close()} werfen Operationen
 * {@link IllegalStateException}.
 *
 * <pre>&lt;indexDirectory&gt;/raw/   Lucene-Index der Rohdokumente</pre>
 */
public final class LuceneRawKnowledgeIndex implements RawKnowledgeIndexPort, Closeable {

    /** Unterverzeichnis neben {@code text/} und {@code vectors/} des Passagenindex. */
    public static final String DIRECTORY = "raw";
    /** Obergrenze analysierter Anfrageterme (Lucene erlaubt höchstens 1024 Klauseln). */
    private static final int MAX_QUERY_TERMS = 256;

    private static final String RESOURCE_ID = LuceneDocuments.RESOURCE_ID;
    private static final String SOURCE_ID = LuceneDocuments.SOURCE_ID;
    private static final String CONTENT = LuceneDocuments.CONTENT;
    private static final String RAW_TEXT = "rawText";
    private static final String CONTENT_HASH = "contentHash";
    private static final String SNIPPET = "snippet";

    private final Directory directory;
    private final Analyzer analyzer = new StandardAnalyzer();
    private final IndexWriter writer;
    private final SearcherManager searchers;
    private volatile boolean closed;

    /** @param indexDirectory das Indexverzeichnis; der Rohindex liegt in {@value #DIRECTORY} darunter */
    public LuceneRawKnowledgeIndex(Path indexDirectory) {
        if (indexDirectory == null) {
            throw new IllegalArgumentException("indexDirectory fehlt");
        }
        Path root = indexDirectory.toAbsolutePath().normalize().resolve(DIRECTORY);
        try {
            Files.createDirectories(root);
            this.directory = FSDirectory.open(root);
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(directory, config);
            this.searchers = new SearcherManager(writer, null);
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Rohindex kann nicht geöffnet werden: " + root, ex);
        }
    }

    @Override
    public void put(RawKnowledgeDocument document) {
        requireOpen();
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        KnowledgeResource resource = document.resource();
        Document doc = new Document();
        doc.add(new StringField(RESOURCE_ID, resource.id().value(), Field.Store.YES));
        doc.add(new StringField(SOURCE_ID, resource.sourceId().value(), Field.Store.YES));
        doc.add(new TextField(CONTENT, resource.title() + "\n" + document.text(), Field.Store.NO));
        doc.add(new StoredField(RAW_TEXT, document.text()));
        doc.add(new StoredField(CONTENT_HASH, document.contentHash()));
        doc.add(new StoredField(SNIPPET, document.snippet()));
        LuceneDocuments.storeResource(doc, resource);
        try {
            writer.updateDocument(new Term(RESOURCE_ID, resource.id().value()), doc);
            writer.commit();
            searchers.maybeRefreshBlocking();
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Rohindex kann nicht geschrieben werden", ex);
        }
    }

    @Override
    public List<RawKnowledgeHit> search(RawKnowledgeQuery query) {
        requireOpen();
        List<String> terms = terms(query.text());
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }
        BooleanQuery.Builder text = new BooleanQuery.Builder();
        int added = 0;
        for (String term : new LinkedHashSet<String>(terms)) {
            if (added++ == MAX_QUERY_TERMS) {
                break;
            }
            text.add(new TermQuery(new Term(CONTENT, term)), BooleanClause.Occur.SHOULD);
        }
        Query search = withSourceFilter(text.build(), query.sources());
        List<RawKnowledgeHit> hits = new ArrayList<RawKnowledgeHit>();
        Function<String, List<String>> analyzed = new Function<String, List<String>>() {
            @Override
            public List<String> apply(String value) {
                return terms(value);
            }
        };
        try {
            IndexSearcher searcher = searchers.acquire();
            try {
                TopDocs top = searcher.search(search, query.maxDocuments());
                for (ScoreDoc scoreDoc : top.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String rawText = doc.get(RAW_TEXT);
                    hits.add(new RawKnowledgeHit(LuceneDocuments.readResource(doc), scoreDoc.score,
                            doc.get(CONTENT_HASH), doc.get(SNIPPET), RawTextWindows.best(rawText, query.text(),
                            query.windowsPerDocument(), query.windowChars(), analyzed)));
                }
            } finally {
                searchers.release(searcher);
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Suche im Rohindex fehlgeschlagen", ex);
        }
        return hits;
    }

    @Override
    public Optional<RawKnowledgeDocument> get(KnowledgeResourceId id) {
        Document doc = load(id);
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(RawKnowledgeDocument.stored(LuceneDocuments.readResource(doc), doc.get(RAW_TEXT),
                doc.get(CONTENT_HASH), doc.get(SNIPPET)));
    }

    @Override
    public Optional<String> contentHashOf(KnowledgeResourceId id) {
        Document doc = load(id);
        return doc == null ? Optional.<String>empty() : Optional.ofNullable(doc.get(CONTENT_HASH));
    }

    @Override
    public Optional<KnowledgeRevision> revisionOf(KnowledgeResourceId id) {
        Document doc = load(id);
        return doc == null ? Optional.<KnowledgeRevision>empty() : Optional.of(LuceneDocuments.revisionOf(doc));
    }

    @Override
    public void remove(KnowledgeResourceId id) {
        delete(new Term(RESOURCE_ID, id.value()));
    }

    @Override
    public void removeSource(SourceAddress sourceId) {
        delete(new Term(SOURCE_ID, sourceId.value()));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            searchers.close();
            writer.close();
            directory.close();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            analyzer.close();
        }
    }

    private Document load(KnowledgeResourceId id) {
        requireOpen();
        try {
            IndexSearcher searcher = searchers.acquire();
            try {
                TopDocs top = searcher.search(new TermQuery(new Term(RESOURCE_ID, id.value())), 1);
                return top.scoreDocs.length == 0 ? null : searcher.doc(top.scoreDocs[0].doc);
            } finally {
                searchers.release(searcher);
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Rohindex kann nicht gelesen werden", ex);
        }
    }

    private void delete(Term term) {
        requireOpen();
        try {
            writer.deleteDocuments(term);
            writer.commit();
            searchers.maybeRefreshBlocking();
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Rohindex kann nicht geschrieben werden", ex);
        }
    }

    /** Analysierte Terme wie im Index (für Anfrage und Textfenster). */
    private List<String> terms(String text) {
        List<String> terms = new ArrayList<String>();
        try {
            TokenStream stream = analyzer.tokenStream(CONTENT, text == null ? "" : text);
            try {
                CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
                stream.reset();
                while (stream.incrementToken()) {
                    terms.add(term.toString());
                }
                stream.end();
            } finally {
                stream.close();
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Text kann nicht analysiert werden", ex);
        }
        return terms;
    }

    private static Query withSourceFilter(Query query, Set<SourceAddress> sources) {
        if (sources.isEmpty()) {
            return query;
        }
        List<BytesRef> ids = new ArrayList<BytesRef>();
        for (SourceAddress source : sources) {
            ids.add(new BytesRef(source.value()));
        }
        return new BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.MUST)
                .add(new TermInSetQuery(SOURCE_ID, ids), BooleanClause.Occur.FILTER)
                .build();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Rohindex ist geschlossen");
        }
    }
}
