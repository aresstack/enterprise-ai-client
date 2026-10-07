package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;

/**
 * Ein Chunk, wie er dem {@link KnowledgeIndexPort} übergeben wird: der Chunk selbst, die beschreibende Ressource
 * (Quellenmetadaten für Treffer) und sein Embedding. Der Index ist eine wiederherstellbare Projektion; deshalb
 * trägt der Eintrag alles, was ein Treffer braucht – nichts wird später in einer Quelle nachgeschlagen.
 *
 * <p>Die {@link EmbeddingModelIdentity} des Vektors bestimmt den Namespace: Einträge verschiedener
 * Embedding-Welten landen in getrennten Namespaces und werden nie miteinander verglichen.
 *
 * <p>Übernommen aus askai-java8 {@code PassageIndexDocument}, hier mit Domain-Typen statt loser Strings.
 */
public final class KnowledgeIndexEntry {

    private final KnowledgeResource resource;
    private final KnowledgeChunk chunk;
    private final EmbeddingVector embedding;

    private KnowledgeIndexEntry(KnowledgeResource resource, KnowledgeChunk chunk, EmbeddingVector embedding) {
        this.resource = resource;
        this.chunk = chunk;
        this.embedding = embedding;
    }

    /**
     * @throws IllegalArgumentException wenn ein Teil fehlt oder Chunk und Ressource nicht zusammengehören
     */
    public static KnowledgeIndexEntry of(KnowledgeResource resource, KnowledgeChunk chunk, EmbeddingVector embedding) {
        if (resource == null || chunk == null || embedding == null) {
            throw new IllegalArgumentException("resource, chunk und embedding sind Pflicht");
        }
        if (!chunk.resourceId().equals(resource.id())) {
            throw new IllegalArgumentException("Chunk " + chunk.id() + " gehört nicht zur Ressource " + resource.id());
        }
        if (!chunk.sourceId().equals(resource.sourceId())) {
            throw new IllegalArgumentException("Chunk " + chunk.id() + " hat Quelle " + chunk.sourceId()
                    + ", die Ressource aber " + resource.sourceId());
        }
        return new KnowledgeIndexEntry(resource, chunk, embedding);
    }

    public KnowledgeResource resource() {
        return resource;
    }

    public KnowledgeChunk chunk() {
        return chunk;
    }

    public KnowledgeChunkId chunkId() {
        return chunk.id();
    }

    public EmbeddingVector embedding() {
        return embedding;
    }

    /** Der Namespace dieses Eintrags. */
    public EmbeddingModelIdentity space() {
        return embedding.identity();
    }

    @Override
    public String toString() {
        return "KnowledgeIndexEntry{" + chunk.id() + ", " + embedding + "}";
    }
}
