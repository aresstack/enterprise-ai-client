package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;

import java.util.Comparator;

/**
 * Ein Suchtreffer: der gefundene Chunk, seine Ressource (Titel, Ort, Quelle, Metadaten für Quellenangaben) und
 * der Relevanz-Score (höher ist besser; Bedeutung je {@link #mode()}). Chunk und Ressource gehören zusammen
 * (gleiche Ressourcen- und Source-ID); Keyword-Scores sind positiv, semantische liegen in [-1, 1] (Rundungsfehler
 * der Float-Arithmetik bis {@value #COSINE_TOLERANCE} werden auf die Grenze gezogen).
 *
 * <p>Übernommen aus askai-java8 {@code PassageSearchHit}, hier mit vollständigen Domain-Objekten.
 */
public final class KnowledgeSearchHit {

    static final double COSINE_TOLERANCE = 1e-4;

    private final KnowledgeResource resource;
    private final KnowledgeChunk chunk;
    private final double score;
    private final KnowledgeSearchMode mode;

    public KnowledgeSearchHit(KnowledgeResource resource, KnowledgeChunk chunk, double score,
                              KnowledgeSearchMode mode) {
        if (resource == null || chunk == null || mode == null) {
            throw new IllegalArgumentException("resource, chunk und mode sind Pflicht");
        }
        if (!chunk.id().resourceId().equals(resource.id()) || !chunk.sourceId().equals(resource.sourceId())) {
            throw new IllegalArgumentException("Chunk " + chunk.id() + " gehört nicht zu Ressource " + resource.id()
                    + " (" + resource.sourceId() + ")");
        }
        this.resource = resource;
        this.chunk = chunk;
        this.score = checkedScore(score, mode);
        this.mode = mode;
    }

    private static double checkedScore(double score, KnowledgeSearchMode mode) {
        if (Double.isNaN(score) || Double.isInfinite(score)) {
            throw new IllegalArgumentException("score muss endlich sein: " + score);
        }
        if (mode == KnowledgeSearchMode.KEYWORD) {
            if (score <= 0) {
                throw new IllegalArgumentException("Keyword-Score muss positiv sein: " + score);
            }
            return score;
        }
        if (Math.abs(score) > 1 + COSINE_TOLERANCE) {
            throw new IllegalArgumentException("Cosine-Score muss in [-1, 1] liegen: " + score);
        }
        return Math.max(-1, Math.min(1, score));
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
