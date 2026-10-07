package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistenter Vektorindex mit exakter (Brute-Force-)Cosine-Suche – bewusst ohne ANN: Für kleine bis mittlere
 * Wissensbestände ist der lineare Scan korrekt, wiederherstellbar und leicht nachvollziehbar. Eine Binärdatei je
 * Namespace ({@code <sha256(fingerprint)>.vec}); der Kopf beschreibt die vollständige
 * {@link EmbeddingModelIdentity}, beim Laden wird sie gegen den Namespace geprüft. Vektoren verschiedener
 * Namespaces liegen in verschiedenen Dateien und werden nie verglichen.
 *
 * <p>Geladene Namespaces werden im Speicher gehalten; diese Instanz ist der einzige Schreiber ihres
 * Verzeichnisses (nicht thread-sicher, der {@link LuceneKnowledgeIndex} synchronisiert).
 *
 * <p>Übernommen und adaptiert aus askai-java8 {@code FileVectorPassageIndex}: gleiche Struktur (eine Datei je
 * Fingerprint, atomares Schreiben, Upsert je ID, Replace je Capture/Ressource), hier mit selbstbeschreibendem
 * Kopf, Source-ID für Filter und {@link EmbeddingVector} statt roher Arrays.
 */
final class FileVectorIndex {

    private static final int MAGIC = 0x45414956; // "EAIV"
    private static final int FORMAT_VERSION = 2;
    private static final String SUFFIX = ".vec";

    private final Path root;
    private final Map<String, Map<String, VectorEntry>> cache = new HashMap<String, Map<String, VectorEntry>>();

    FileVectorIndex(Path root) {
        this.root = root;
    }

    /** Ein gespeicherter Vektor samt der Angaben, die Filter und Löschen brauchen. */
    static final class VectorEntry {
        final String chunkId;
        final KnowledgeResourceId resourceId;
        final KnowledgeSourceId sourceId;
        final String stamp;
        final EmbeddingVector vector;

        VectorEntry(String chunkId, KnowledgeResourceId resourceId, KnowledgeSourceId sourceId, String stamp,
                    EmbeddingVector vector) {
            this.chunkId = chunkId;
            this.resourceId = resourceId;
            this.sourceId = sourceId;
            this.stamp = stamp;
            this.vector = vector;
        }

        static VectorEntry of(KnowledgeIndexEntry entry) {
            return new VectorEntry(entry.chunkId().value(), entry.resource().id(), entry.resource().sourceId(),
                    EntryStamp.of(entry.resource(), entry.chunk()), entry.embedding());
        }
    }

    /** Ein Treffer: Chunk-ID, Stempel des eingebetteten Inhalts und Cosine-Score. */
    static final class ScoredChunk {
        final String chunkId;
        final String stamp;
        final double score;

        ScoredChunk(String chunkId, String stamp, double score) {
            this.chunkId = chunkId;
            this.stamp = stamp;
            this.score = score;
        }
    }

    void upsert(EmbeddingModelIdentity space, Collection<KnowledgeIndexEntry> entries) {
        Map<String, VectorEntry> namespace = new LinkedHashMap<String, VectorEntry>(load(space));
        for (KnowledgeIndexEntry entry : entries) {
            namespace.put(entry.chunkId().value(), VectorEntry.of(entry));
        }
        write(space, namespace);
    }

    void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                 Collection<KnowledgeIndexEntry> entries) {
        Map<String, VectorEntry> namespace = new LinkedHashMap<String, VectorEntry>(load(space));
        for (Iterator<VectorEntry> it = namespace.values().iterator(); it.hasNext(); ) {
            if (it.next().resourceId.equals(resourceId)) {
                it.remove();
            }
        }
        for (KnowledgeIndexEntry entry : entries) {
            namespace.put(entry.chunkId().value(), VectorEntry.of(entry));
        }
        write(space, namespace);
    }

    /** Top-{@code maxResults} nach Cosine absteigend, Gleichstand nach Chunk-ID. */
    List<ScoredChunk> search(KnowledgeSemanticQuery query) {
        List<ScoredChunk> hits = new ArrayList<ScoredChunk>();
        for (VectorEntry entry : load(query.space()).values()) {
            if (query.accepts(entry.sourceId)) {
                // cosineSimilarity verweigert Vektoren einer anderen Embedding-Welt (zweite Sicherung neben der Datei).
                hits.add(new ScoredChunk(entry.chunkId, entry.stamp,
                        query.vector().cosineSimilarity(entry.vector)));
            }
        }
        Collections.sort(hits, new Comparator<ScoredChunk>() {
            @Override
            public int compare(ScoredChunk a, ScoredChunk b) {
                int byScore = Double.compare(b.score, a.score);
                return byScore != 0 ? byScore : a.chunkId.compareTo(b.chunkId);
            }
        });
        return hits.size() > query.maxResults() ? new ArrayList<ScoredChunk>(hits.subList(0, query.maxResults())) : hits;
    }

    /** Entfernt passende Einträge aus allen Namespaces ({@code null}-Kriterium wird ignoriert). */
    void removeWhere(KnowledgeResourceId resourceId, KnowledgeSourceId sourceId) {
        for (Path file : IndexFiles.list(root)) {
            if (!file.getFileName().toString().endsWith(SUFFIX)) {
                continue;
            }
            EmbeddingModelIdentity space = readIdentity(file);
            Map<String, VectorEntry> namespace = new LinkedHashMap<String, VectorEntry>(load(space));
            boolean changed = false;
            for (Iterator<VectorEntry> it = namespace.values().iterator(); it.hasNext(); ) {
                VectorEntry entry = it.next();
                if (entry.resourceId.equals(resourceId) || entry.sourceId.equals(sourceId)) {
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                write(space, namespace);
            }
        }
    }

    /** Aktueller Stand eines Namespaces; bleibt unverändert, weil Schreibvorgänge stets eine Kopie ablegen. */
    Map<String, VectorEntry> snapshot(EmbeddingModelIdentity space) {
        return load(space);
    }

    /** Stellt einen mit {@link #snapshot} gesicherten Stand wieder her (Rollback nach gescheitertem Text-Schreiben). */
    void restore(EmbeddingModelIdentity space, Map<String, VectorEntry> snapshot) {
        write(space, new LinkedHashMap<String, VectorEntry>(snapshot));
    }

    void removeAll() {
        cache.clear();
        IndexFiles.deleteRecursively(root);
    }

    /** Anzahl Vektoren eines Namespaces (Diagnose/Tests). */
    int size(EmbeddingModelIdentity space) {
        return load(space).size();
    }

    // ------------------------------------------------------------------ Datei-I/O

    Path fileOf(EmbeddingModelIdentity space) {
        return root.resolve(space.fingerprint() + SUFFIX);
    }

    private Map<String, VectorEntry> load(EmbeddingModelIdentity space) {
        Map<String, VectorEntry> cached = cache.get(space.fingerprint());
        if (cached != null) {
            return cached;
        }
        Map<String, VectorEntry> namespace = new LinkedHashMap<String, VectorEntry>();
        Path file = fileOf(space);
        if (Files.isRegularFile(file)) {
            try {
                DataInputStream in = open(file);
                try {
                    EmbeddingModelIdentity stored = readHeader(in, file);
                    if (!stored.equals(space)) {
                        throw new KnowledgeIndexException("Vektordatei " + file.getFileName()
                                + " gehört zu einer anderen Embedding-Welt: " + stored);
                    }
                    int count = in.readInt();
                    if (count < 0) {
                        throw new KnowledgeIndexException("Vektordatei ist beschädigt (Anzahl " + count + "): "
                                + file.getFileName());
                    }
                    for (int i = 0; i < count; i++) {
                        String chunkId = in.readUTF();
                        KnowledgeResourceId resourceId = KnowledgeChunkId.parse(chunkId).resourceId();
                        KnowledgeSourceId sourceId = KnowledgeSourceId.of(in.readUTF());
                        String stamp = in.readUTF();
                        float[] values = new float[space.dimension()];
                        for (int d = 0; d < values.length; d++) {
                            values[d] = in.readFloat();
                        }
                        namespace.put(chunkId, new VectorEntry(chunkId, resourceId, sourceId, stamp,
                                EmbeddingVector.of(space, values)));
                    }
                } finally {
                    in.close();
                }
            } catch (IOException ex) {
                throw new KnowledgeIndexException("Vektordatei kann nicht gelesen werden: " + file.getFileName(), ex);
            } catch (IllegalArgumentException ex) {
                throw new KnowledgeIndexException("Vektordatei ist beschädigt: " + file.getFileName(), ex);
            }
        }
        cache.put(space.fingerprint(), namespace);
        return namespace;
    }

    private void write(EmbeddingModelIdentity space, Map<String, VectorEntry> namespace) {
        Path file = fileOf(space);
        if (namespace.isEmpty()) {
            IndexFiles.deleteRecursively(file);
            cache.put(space.fingerprint(), namespace);
            return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeUTF(space.modelId());
            out.writeInt(space.dimension());
            out.writeInt(space.attributes().size());
            for (Map.Entry<String, String> attribute : space.attributes().entrySet()) {
                out.writeUTF(attribute.getKey());
                out.writeUTF(attribute.getValue());
            }
            out.writeInt(namespace.size());
            for (VectorEntry entry : namespace.values()) {
                out.writeUTF(entry.chunkId);
                out.writeUTF(entry.sourceId.value());
                out.writeUTF(entry.stamp);
                for (int d = 0; d < entry.vector.dimension(); d++) {
                    out.writeFloat(entry.vector.valueAt(d));
                }
            }
            out.flush();
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Vektorindex kann nicht serialisiert werden", ex);
        }
        IndexFiles.atomicWrite(file, bytes.toByteArray());
        cache.put(space.fingerprint(), namespace);
    }

    private static EmbeddingModelIdentity readIdentity(Path file) {
        try {
            DataInputStream in = open(file);
            try {
                return readHeader(in, file);
            } finally {
                in.close();
            }
        } catch (IOException ex) {
            throw new KnowledgeIndexException("Vektordatei kann nicht gelesen werden: " + file.getFileName(), ex);
        }
    }

    private static EmbeddingModelIdentity readHeader(DataInputStream in, Path file) throws IOException {
        if (in.readInt() != MAGIC) {
            throw new KnowledgeIndexException("Keine Vektordatei: " + file.getFileName());
        }
        int version = in.readInt();
        if (version != FORMAT_VERSION) {
            throw new KnowledgeIndexException("Nicht unterstützte Vektordatei-Version " + version + ": "
                    + file.getFileName());
        }
        EmbeddingModelIdentity identity = EmbeddingModelIdentity.of(in.readUTF(), in.readInt());
        int attributes = in.readInt();
        if (attributes < 0) {
            throw new KnowledgeIndexException("Vektordatei ist beschädigt (Attribute " + attributes + "): "
                    + file.getFileName());
        }
        for (int i = 0; i < attributes; i++) {
            identity = identity.withAttribute(in.readUTF(), in.readUTF());
        }
        return identity;
    }

    private static DataInputStream open(Path file) throws IOException {
        InputStream stream = Files.newInputStream(file);
        return new DataInputStream(new BufferedInputStream(stream));
    }
}
