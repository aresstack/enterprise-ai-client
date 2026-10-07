package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;

import java.util.Comparator;

/**
 * Ein Suchtreffer: der gefundene Chunk, seine Ressource (Titel, Ort, Quelle, Metadaten für Quellenangaben) und
 * der Relevanz-Score (höher ist besser; Bedeutung je {@link #mode()}).
 *
 * <p>Übernommen aus askai-java8 {@code PassageSearchHit}, hier mit vollständigen Domain-Objekten.
 */
public final class KnowledgeSearchHit {

    private final KnowledgeResource resource;
    private final KnowledgeChunk chunk;
    private final double score;
    private final KnowledgeSearchMode mode;

    public KnowledgeSearchHit(KnowledgeResource resource, KnowledgeChunk chunk, double score,
                              KnowledgeSearchMode mode) {
        if (resource == null || chunk == null || mode == null) {
            throw new IllegalArgumentException("resource, chunk und mode sind Pflicht");
        }
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score muss endlich sein: " + score);
        }
        this.resource = resource;
        this.chunk = chunk;
        this.score = score;
        this.mode = mode;
    }

    /** Absteigend nach Score, bei Gleichstand aufsteigend nach Chunk-ID – die Ordnung jeder Trefferliste. */
    public static Comparator<KnowledgeSearchHit> byRelevance() {
        return new Comparator<KnowledgeSearchHit>() {
            @Override
            public int compare(KnowledgeSearchHit a, KnowledgeSearchHit b) {
                int byScore = Double.compare(b.score, a.score);
                return byScore != 0 ? byScore : a.chunk.id().value().compareTo(b.chunk.id().value());
            }
        };
    }

    public KnowledgeResource resource() {
        return resource;
    }

    public KnowledgeChunk chunk() {
        return chunk;
    }

    public double score() {
        return score;
    }

    public KnowledgeSearchMode mode() {
        return mode;
    }

    @Override
    public String toString() {
        return "KnowledgeSearchHit{" + chunk.id() + ", " + mode + ", score=" + score + "}";
    }
}
