package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchMode;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Produktiver {@link KnowledgeIndexPort}: persistenter Lucene-BM25-Index ({@link LuceneTextIndex}) für die
 * Volltextsuche plus persistenter Vektorindex mit exakter Cosine-Suche ({@link FileVectorIndex}), beide je
 * Embedding-Namespace und beide wiederherstellbare Projektionen der kanonischen Wissensdaten.
 *
 * <pre>
 * &lt;indexDirectory&gt;/text/&lt;fingerprint&gt;/      Lucene-Index des Namespaces
 * &lt;indexDirectory&gt;/vectors/&lt;fingerprint&gt;.vec  Vektoren des Namespaces
 * </pre>
 *
 * <p>Ressource und Chunk eines Eintrags liegen einmal als gespeicherte Lucene-Felder vor; semantische Treffer
 * werden dort per Chunk-ID nachgeladen, und {@link #resourceIds}/{@link #revisionOf} lesen dort (der Textindex
 * ist die Bestandsliste; Vektoren ohne passenden Text zählen nicht als indexiert).
 * Schreibreihenfolge: erst Vektoren, dann Text; gelöscht wird umgekehrt. Jeder Vektor trägt einen
 * Stempel (SHA-256 über Text, Überschriften und Revision des eingebetteten Chunks); ein semantischer Treffer zählt
 * nur, wenn das Textdokument denselben Stempel ergibt. Scheitert das Text-Schreiben mit einer Exception, wird der
 * Vektorstand zurückgesetzt. Bricht der Prozess selbst zwischen beiden Schreibvorgängen ab,
 * werden die betroffenen Vektoren daher übersprungen, nie mit einer anderen Revision gepaart, bis der nächste
 * Upsert oder {@link #rebuild} die Hälften wieder angleicht.
 *
 * <p>Konfiguration ist nur das Verzeichnis (per Konstruktor, keine globalen Settings). Alle Operationen sind
 * synchronisiert; eine Instanz je Verzeichnis und Prozess. Nach {@link #close()} werfen Operationen
 * {@link IllegalStateException}; {@code close} ist idempotent.
 *
 * <p>Übernommen und adaptiert aus askai-java8 {@code CompositeSemanticKnowledgeIndex}.
 */
public final class LuceneKnowledgeIndex implements KnowledgeIndexPort, Closeable {

    private final LuceneTextIndex text;
    private final FileVectorIndex vectors;
    private boolean closed;

    public LuceneKnowledgeIndex(Path indexDirectory) {
        if (indexDirectory == null) {
            throw new IllegalArgumentException("indexDirectory fehlt");
        }
        Path root = indexDirectory.toAbsolutePath().normalize();
        this.text = new LuceneTextIndex(root.resolve("text"));
        this.vectors = new FileVectorIndex(root.resolve("vectors"));
    }

    @Override
    public synchronized void index(Collection<KnowledgeIndexEntry> entries) {
        requireOpen();
        for (Map.Entry<EmbeddingModelIdentity, List<KnowledgeIndexEntry>> namespace : bySpace(entries).entrySet()) {
            final EmbeddingModelIdentity space = namespace.getKey();
            final List<KnowledgeIndexEntry> spaceEntries = namespace.getValue();
            writeBoth(space, new Runnable() {
                @Override
                public void run() {
                    vectors.upsert(space, spaceEntries);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    text.upsert(space, spaceEntries);
                }
            });
        }
    }

    @Override
    public synchronized void replace(final EmbeddingModelIdentity space, final KnowledgeResourceId resourceId,
                                     Collection<KnowledgeIndexEntry> entries) {
        requireOpen();
        if (space == null || resourceId == null) {
            throw new IllegalArgumentException("space und resourceId sind Pflicht");
        }
        List<KnowledgeIndexEntry> checked = requireEntries(entries);
        for (KnowledgeIndexEntry entry : checked) {
            if (!entry.space().equals(space)) {
                throw new IllegalArgumentException(entry + " gehört nicht zum Namespace " + space);
            }
            if (!entry.resource().id().equals(resourceId)) {
                throw new IllegalArgumentException(entry + " gehört nicht zur Ressource " + resourceId);
            }
        }
        final List<KnowledgeIndexEntry> replacement = checked;
        writeBoth(space, new Runnable() {
            @Override
            public void run() {
                vectors.replace(space, resourceId, replacement);
            }
        }, new Runnable() {
            @Override
            public void run() {
                text.replace(space, resourceId, replacement);
            }
        });
    }

    /**
     * Schreibt erst Vektoren, dann Text. Scheitert der Text-Schreibvorgang, wird der Vektorstand zurückgesetzt
     * (Lucene rollt seine Hälfte selbst zurück), sodass ein fehlgeschlagener Aufruf nichts ändert.
     */
    private void writeBoth(EmbeddingModelIdentity space, Runnable vectorWrite, Runnable textWrite) {
        Map<String, FileVectorIndex.VectorEntry> before = vectors.snapshot(space);
        vectorWrite.run();
        try {
            textWrite.run();
        } catch (RuntimeException failure) {
            try {
                vectors.restore(space, before);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    @Override
    public synchronized List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
        requireOpen();
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        for (LuceneTextIndex.ScoredStored hit : text.search(query)) {
            hits.add(new KnowledgeSearchHit(hit.stored.resource, hit.stored.chunk, hit.score,
                    KnowledgeSearchMode.KEYWORD));
        }
        return hits;
    }

    @Override
    public synchronized List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
        requireOpen();
        List<FileVectorIndex.ScoredChunk> scored = vectors.search(query);
        List<String> chunkIds = new ArrayList<String>();
        for (FileVectorIndex.ScoredChunk chunk : scored) {
            chunkIds.add(chunk.chunkId);
        }
        Map<String, LuceneDocuments.Stored> stored = text.load(query.space(), chunkIds);
        List<KnowledgeSearchHit> hits = new ArrayList<KnowledgeSearchHit>();
        for (FileVectorIndex.ScoredChunk chunk : scored) {
            LuceneDocuments.Stored document = stored.get(chunk.chunkId);
            // Nur Paare derselben Revision: Ein Vektor, dessen Text (noch) nicht oder in anderer Fassung im
            // Textindex liegt, wird übersprungen statt fremden Text zu zitieren.
            if (document != null && chunk.stamp.equals(EntryStamp.of(document.resource, document.chunk))) {
                hits.add(new KnowledgeSearchHit(document.resource, document.chunk, chunk.score,
                        KnowledgeSearchMode.SEMANTIC));
            }
        }
        return hits;
    }

    @Override
    public synchronized Set<KnowledgeResourceId> resourceIds(EmbeddingModelIdentity space,
                                                             KnowledgeSourceId sourceId) {
        requireOpen();
        if (space == null || sourceId == null) {
            throw new IllegalArgumentException("space und sourceId sind Pflicht");
        }
        return text.resourceIds(space, sourceId);
    }

    @Override
    public synchronized Optional<KnowledgeRevision> revisionOf(EmbeddingModelIdentity space,
                                                               KnowledgeResourceId resourceId) {
        requireOpen();
        if (space == null || resourceId == null) {
            throw new IllegalArgumentException("space und resourceId sind Pflicht");
        }
        return text.revisionOf(space, resourceId);
    }

    @Override
    public synchronized void remove(KnowledgeResourceId resourceId) {
        requireOpen();
        if (resourceId == null) {
            throw new IllegalArgumentException("resourceId fehlt");
        }
        // Löschen in umgekehrter Schreibreihenfolge: erst Text, dann Vektoren. Bricht der Prozess dazwischen ab,
        // bleiben nur Vektoren ohne Text übrig, die weder Treffer noch Revision liefern und beim nächsten
        // replace/rebuild verschwinden; der umgekehrte Fall (Text ohne Vektoren) würde eine Revision melden.
        text.removeWhere(resourceId, null);
        vectors.removeWhere(resourceId, null);
    }

    @Override
    public synchronized void removeSource(KnowledgeSourceId sourceId) {
        requireOpen();
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId fehlt");
        }
        text.removeWhere(null, sourceId);
        vectors.removeWhere(null, sourceId);
    }

    @Override
    public synchronized void rebuild(Collection<KnowledgeIndexEntry> entries) {
        requireOpen();
        Map<EmbeddingModelIdentity, List<KnowledgeIndexEntry>> namespaces = bySpace(entries);
        vectors.removeAll();
        text.removeAll();
        for (Map.Entry<EmbeddingModelIdentity, List<KnowledgeIndexEntry>> namespace : namespaces.entrySet()) {
            vectors.upsert(namespace.getKey(), namespace.getValue());
            text.upsert(namespace.getKey(), namespace.getValue());
        }
    }

    /** Anzahl gespeicherter Vektoren eines Namespaces (Diagnose). */
    public synchronized int vectorCount(EmbeddingModelIdentity space) {
        requireOpen();
        return vectors.size(space);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Wissensindex ist geschlossen");
        }
    }

    private static Map<EmbeddingModelIdentity, List<KnowledgeIndexEntry>> bySpace(
            Collection<KnowledgeIndexEntry> entries) {
        Map<EmbeddingModelIdentity, List<KnowledgeIndexEntry>> bySpace =
                new LinkedHashMap<EmbeddingModelIdentity, List<KnowledgeIndexEntry>>();
        for (KnowledgeIndexEntry entry : requireEntries(entries)) {
            List<KnowledgeIndexEntry> namespace = bySpace.get(entry.space());
            if (namespace == null) {
                namespace = new ArrayList<KnowledgeIndexEntry>();
                bySpace.put(entry.space(), namespace);
            }
            namespace.add(entry);
        }
        return bySpace;
    }

    private static List<KnowledgeIndexEntry> requireEntries(Collection<KnowledgeIndexEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries darf nicht null sein");
        }
        List<KnowledgeIndexEntry> copy = new ArrayList<KnowledgeIndexEntry>(entries);
        if (copy.contains(null)) {
            throw new IllegalArgumentException("entries enthält null");
        }
        return copy;
    }
}
