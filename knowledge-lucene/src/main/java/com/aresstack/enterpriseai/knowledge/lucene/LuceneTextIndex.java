package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lucene-Hälfte des Wissensindex: ein persistenter Lucene-Index je Namespace ({@code <root>/<fingerprint>/}),
 * BM25 (Lucene-Default) über Titel, Überschriften und Text, {@link StandardAnalyzer} ohne Stemming. Ressource
 * und Chunk liegen als gespeicherte Felder vor; die semantische Suche holt ihre Treffer hier per Chunk-ID ab.
 *
 * <p>Jede Operation öffnet Writer bzw. Reader und schließt sie wieder (Änderungen sind nach Rückkehr committed
 * und sichtbar, es bleiben keine Locks offen). Nicht thread-sicher; der {@link LuceneKnowledgeIndex}
 * synchronisiert.
 *
 * <p>Übernommen und adaptiert aus askai-java8 {@code LuceneTextPassageIndex} (Namespace-Verzeichnisse,
 * Upsert über Term, Replace über Löschterm, Anfrage als OR über analysierte Terme); BM25-Konfiguration und
 * gespeicherte Ressourcenfelder angelehnt an corenth {@code LuceneLexicalIndex}.
 */
final class LuceneTextIndex {

    /** Obergrenze analysierter Anfrageterme (Lucene erlaubt höchstens 1024 Klauseln). */
    private static final int MAX_QUERY_TERMS = 256;

    private final Path root;

    LuceneTextIndex(Path root) {
        this.root = root;
    }

    void upsert(EmbeddingModelIdentity space, Collection<KnowledgeIndexEntry> entries) {
        write(directoryOf(space), null, entries);
    }

    void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                 Collection<KnowledgeIndexEntry> entries) {
        write(directoryOf(space), new Term(LuceneDocuments.RESOURCE_ID, resourceId.value()), entries);
    }

    /** Löscht passende Dokumente in allen Namespaces ({@code null}-Kriterium wird ignoriert). */
    void removeWhere(KnowledgeResourceId resourceId, KnowledgeSourceId sourceId) {
        for (Path directory : IndexFiles.list(root)) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            if (resourceId != null) {
                write(directory, new Term(LuceneDocuments.RESOURCE_ID, resourceId.value()),
                        new ArrayList<KnowledgeIndexEntry>());
            }
            if (sourceId != null) {
                write(directory, new Term(LuceneDocuments.SOURCE_ID, sourceId.value()),
                        new ArrayList<KnowledgeIndexEntry>());
            }
        }
    }

    void removeAll() {
        IndexFiles.deleteRecursively(root);
    }

    /** BM25-Treffer, absteigend nach Score, Gleichstand nach Chunk-ID. */
    List<ScoredStored> search(KnowledgeKeywordQuery query) {
        List<ScoredStored> hits = new ArrayList<ScoredStored>();
        Path path = directoryOf(query.space());
        if (!Files.isDirectory(path)) {
            return hits;
        }
        Analyzer analyzer = new StandardAnalyzer();
        try {
            Query textQuery = toQuery(analyzer, query.text());
            if (textQuery == null) {
                return hits;
            }
            Directory directory = FSDirectory.open(path);
            try {
                if (!DirectoryReader.indexExists(directory)) {
                    return hits;
                }
                DirectoryReader reader = DirectoryReader.open(directory);
                try {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    Sort byRelevance = new Sort(SortField.FIELD_SCORE,
                            new SortField(LuceneDocuments.CHUNK_ID_SORT, SortField.Type.STRING));
                    TopDocs top = searcher.search(withSourceFilter(textQuery, query.sources()),
                            query.maxResults(), byRelevance, true);
                    for (ScoreDoc scoreDoc : top.scoreDocs) {
                        hits.add(new ScoredStored(LuceneDocuments.fromDocument(searcher.doc(scoreDoc.doc)),
                                scoreDoc.score));
                    }
                } finally {
                    reader.close();
                }
            } finally {
                directory.close();
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Volltextsuche fehlgeschlagen", ex);
        } finally {
            analyzer.close();
        }
        return hits;
    }

    /** Gespeicherte Ressource/Chunk je Chunk-ID; fehlende IDs fehlen in der Map. */
    Map<String, LuceneDocuments.Stored> load(EmbeddingModelIdentity space, Collection<String> chunkIds) {
        Map<String, LuceneDocuments.Stored> stored = new LinkedHashMap<String, LuceneDocuments.Stored>();
        Path path = directoryOf(space);
        if (chunkIds.isEmpty() || !Files.isDirectory(path)) {
            return stored;
        }
        List<BytesRef> terms = new ArrayList<BytesRef>();
        for (String chunkId : chunkIds) {
            terms.add(new BytesRef(chunkId));
        }
        try {
            Directory directory = FSDirectory.open(path);
            try {
                if (!DirectoryReader.indexExists(directory)) {
                    return stored;
                }
                DirectoryReader reader = DirectoryReader.open(directory);
                try {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    TopDocs top = searcher.search(new TermInSetQuery(LuceneDocuments.CHUNK_ID, terms), terms.size());
                    for (ScoreDoc scoreDoc : top.scoreDocs) {
                        LuceneDocuments.Stored document = LuceneDocuments.fromDocument(searcher.doc(scoreDoc.doc));
                        stored.put(document.chunk.id().value(), document);
                    }
                } finally {
                    reader.close();
                }
            } finally {
                directory.close();
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Indexeinträge können nicht gelesen werden", ex);
        }
        return stored;
    }

    /** Ein Volltexttreffer. */
    static final class ScoredStored {
        final LuceneDocuments.Stored stored;
        final double score;

        ScoredStored(LuceneDocuments.Stored stored, double score) {
            this.stored = stored;
            this.score = score;
        }
    }

    // ------------------------------------------------------------------ intern

    private Path directoryOf(EmbeddingModelIdentity space) {
        return root.resolve(space.fingerprint());
    }

    private static void write(Path path, Term deleteFirst, Collection<KnowledgeIndexEntry> entries) {
        if (entries.isEmpty() && !Files.isDirectory(path)) {
            return; // nichts zu löschen, kein leeres Verzeichnis anlegen
        }
        Analyzer analyzer = new StandardAnalyzer();
        try {
            Files.createDirectories(path);
            Directory directory = FSDirectory.open(path);
            try {
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                IndexWriter writer = new IndexWriter(directory, config);
                try {
                    if (deleteFirst != null) {
                        writer.deleteDocuments(deleteFirst);
                    }
                    for (KnowledgeIndexEntry entry : entries) {
                        writer.updateDocument(new Term(LuceneDocuments.CHUNK_ID, entry.chunkId().value()),
                                LuceneDocuments.toDocument(entry));
                    }
                    writer.commit();
                } catch (IOException | RuntimeException ex) {
                    writer.rollback();
                    throw ex;
                } finally {
                    if (writer.isOpen()) {
                        writer.close();
                    }
                }
            } finally {
                directory.close();
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Volltextindex kann nicht geschrieben werden", ex);
        } finally {
            analyzer.close();
        }
    }

    private static Query toQuery(Analyzer analyzer, String text) throws IOException {
        Set<String> terms = new LinkedHashSet<String>();
        TokenStream stream = analyzer.tokenStream(LuceneDocuments.CONTENT, text);
        try {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken() && terms.size() < MAX_QUERY_TERMS) {
                terms.add(term.toString());
            }
            stream.end();
        } finally {
            stream.close();
        }
        if (terms.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        for (String term : terms) {
            builder.add(new TermQuery(new Term(LuceneDocuments.CONTENT, term)), BooleanClause.Occur.SHOULD);
        }
        return builder.build();
    }

    private static Query withSourceFilter(Query query, Set<KnowledgeSourceId> sources) {
        if (sources.isEmpty()) {
            return query;
        }
        List<BytesRef> ids = new ArrayList<BytesRef>();
        for (KnowledgeSourceId source : sources) {
            ids.add(new BytesRef(source.value()));
        }
        return new BooleanQuery.Builder()
                .add(query, BooleanClause.Occur.MUST)
                .add(new TermInSetQuery(LuceneDocuments.SOURCE_ID, ids), BooleanClause.Occur.FILTER)
                .build();
    }
}
