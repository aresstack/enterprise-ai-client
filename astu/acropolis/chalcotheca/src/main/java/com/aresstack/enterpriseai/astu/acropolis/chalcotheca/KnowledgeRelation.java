package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;

/**
 * Eine gerichtete Kante {@code from --kind--> to} zwischen zwei Chunks. Das Ziel kommt vollständig mit (Ressource
 * und Chunk), damit ein Konsument es ohne weiteren Indexzugriff in den Kontext nehmen kann.
 *
 * <p>{@link #score()} ist bei {@link KnowledgeRelationKind#SIMILAR_TO} der Cosine-Wert in [-1, 1], bei allen
 * strukturellen Arten {@code 1.0}.
 */
public final class KnowledgeRelation {

    private final KnowledgeRelationKind kind;
    private final KnowledgeChunkId from;
    private final KnowledgeResource resource;
    private final KnowledgeChunk chunk;
    private final double score;

    public KnowledgeRelation(KnowledgeRelationKind kind, KnowledgeChunkId from, KnowledgeResource resource,
                             KnowledgeChunk chunk, double score) {
        if (kind == null || from == null || resource == null || chunk == null) {
            throw new IllegalArgumentException("kind, from, resource und chunk sind Pflicht");
        }
        if (!chunk.resourceId().equals(resource.id())) {
            throw new IllegalArgumentException("Chunk " + chunk.id() + " gehört nicht zur Ressource " + resource.id());
        }
        if (Double.isNaN(score)) {
            throw new IllegalArgumentException("score ist NaN");
        }
        this.kind = kind;
        this.from = from;
        this.resource = resource;
        this.chunk = chunk;
        this.score = score;
    }

    public KnowledgeRelationKind kind() {
        return kind;
    }

    public KnowledgeChunkId from() {
        return from;
    }

    public KnowledgeChunkId to() {
        return chunk.id();
    }

    /** Ressource des Ziels. */
    public KnowledgeResource resource() {
        return resource;
    }

    /** Der Ziel-Chunk. */
    public KnowledgeChunk chunk() {
        return chunk;
    }

    public double score() {
        return score;
    }

    @Override
    public String toString() {
        return "KnowledgeRelation{" + from + " -" + kind + "-> " + chunk.id()
                + (kind == KnowledgeRelationKind.SIMILAR_TO ? ", score=" + score : "") + "}";
    }
}
