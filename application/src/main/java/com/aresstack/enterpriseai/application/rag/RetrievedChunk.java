package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;

import java.util.Comparator;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Ein fusionierter Treffer: Chunk und Ressource (Titel, Ort, Revision, Metadaten für Quellenangaben), der
 * RRF-Score und die Rohdaten beider Suchpfade. Fand ein Pfad den Chunk nicht, sind dessen Score und Rang leer.
 *
 * <p>Die Rohscores sind nur innerhalb ihres Pfades und ihrer Suche vergleichbar (BM25 nach oben offen, Cosine in
 * [-1, 1]); sie werden zur Anzeige und Diagnose mitgeführt, die Reihenfolge bestimmt allein {@link #fusedScore()}.
 */
public final class RetrievedChunk {

    private final KnowledgeResource resource;
    private final KnowledgeChunk chunk;
    private final double fusedScore;
    private final int keywordRank;
    private final double keywordScore;
    private final int semanticRank;
    private final double semanticScore;

    RetrievedChunk(KnowledgeResource resource, KnowledgeChunk chunk, double fusedScore, int keywordRank,
                   double keywordScore, int semanticRank, double semanticScore) {
        if (resource == null || chunk == null) {
            throw new IllegalArgumentException("resource und chunk sind Pflicht");
        }
        if (keywordRank <= 0 && semanticRank <= 0) {
            throw new IllegalArgumentException("mindestens ein Suchpfad muss den Chunk gefunden haben");
        }
        this.resource = resource;
        this.chunk = chunk;
        this.fusedScore = fusedScore;
        this.keywordRank = keywordRank;
        this.keywordScore = keywordScore;
        this.semanticRank = semanticRank;
        this.semanticScore = semanticScore;
    }

    /** Absteigend nach RRF-Score, bei Gleichstand aufsteigend nach Chunk-ID – die Ordnung jedes Ergebnisses. */
    public static Comparator<RetrievedChunk> byFusedScore() {
        return new Comparator<RetrievedChunk>() {
            @Override
            public int compare(RetrievedChunk a, RetrievedChunk b) {
                int byScore = Double.compare(b.fusedScore, a.fusedScore);
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

    /** RRF-Score; höher ist besser, nur innerhalb eines Ergebnisses vergleichbar. */
    public double fusedScore() {
        return fusedScore;
    }

    public boolean foundBy(RetrievalPath path) {
        return path == RetrievalPath.KEYWORD ? keywordRank > 0 : semanticRank > 0;
    }

    /** Rang (ab 1) in der Volltextsuche. */
    public OptionalInt keywordRank() {
        return keywordRank > 0 ? OptionalInt.of(keywordRank) : OptionalInt.empty();
    }

    /** Roher Volltext-Score (BM25 bzw. Score des Index-Adapters). */
    public OptionalDouble keywordScore() {
        return keywordRank > 0 ? OptionalDouble.of(keywordScore) : OptionalDouble.empty();
    }

    /** Rang (ab 1) in der semantischen Suche. */
    public OptionalInt semanticRank() {
        return semanticRank > 0 ? OptionalInt.of(semanticRank) : OptionalInt.empty();
    }

    /** Roher Cosine-Wert in [-1, 1]. */
    public OptionalDouble semanticScore() {
        return semanticRank > 0 ? OptionalDouble.of(semanticScore) : OptionalDouble.empty();
    }

    @Override
    public String toString() {
        return "RetrievedChunk{" + chunk.id() + ", fused=" + fusedScore
                + (keywordRank > 0 ? ", keyword=#" + keywordRank + "/" + keywordScore : "")
                + (semanticRank > 0 ? ", semantic=#" + semanticRank + "/" + semanticScore : "") + "}";
    }
}
