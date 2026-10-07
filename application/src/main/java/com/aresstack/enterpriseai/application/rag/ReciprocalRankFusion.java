package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Führt die Trefferlisten von Volltext- und semantischer Suche per Reciprocal Rank Fusion zusammen und
 * dedupliziert je Chunk-ID. Reine Funktion ohne Zustand; deterministisch (Gleichstand nach Chunk-ID).
 *
 * <p>Ersetzt die lineare Score-Normalisierung aus MainframeMate {@code HybridRetriever.mergeAndScore}: dort
 * wurden BM25 durch das Maximum geteilt und mit Cosine gewichtet addiert, was die Reihenfolge von der
 * Score-Verteilung der jeweiligen Anfrage abhängig macht. RRF braucht nur Ränge.
 */
public final class ReciprocalRankFusion {

    private final double keywordWeight;
    private final double semanticWeight;
    private final int rankConstant;

    public ReciprocalRankFusion(RetrievalSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.keywordWeight = settings.keywordWeight();
        this.semanticWeight = settings.semanticWeight();
        this.rankConstant = settings.rankConstant();
    }

    /**
     * @param keywordHits  Volltexttreffer in Relevanzreihenfolge (Rang = Position + 1), nur {@code KEYWORD}
     * @param semanticHits semantische Treffer in Relevanzreihenfolge, nur {@code SEMANTIC}
     * @return alle Chunks beider Listen genau einmal, absteigend nach RRF-Score, ungekürzt
     * @throws IllegalArgumentException wenn ein Treffer im falschen Pfad steht
     */
    public List<RetrievedChunk> fuse(List<KnowledgeSearchHit> keywordHits, List<KnowledgeSearchHit> semanticHits) {
        Map<KnowledgeChunkId, Candidate> byChunk = new LinkedHashMap<KnowledgeChunkId, Candidate>();
        collect(byChunk, keywordHits, KnowledgeSearchMode.KEYWORD);
        collect(byChunk, semanticHits, KnowledgeSearchMode.SEMANTIC);

        List<RetrievedChunk> result = new ArrayList<RetrievedChunk>(byChunk.size());
        for (Candidate c : byChunk.values()) {
            double fused = contribution(keywordWeight, c.keywordRank) + contribution(semanticWeight, c.semanticRank);
            result.add(new RetrievedChunk(c.hit.resource(), c.hit.chunk(), fused, c.keywordRank, c.keywordScore,
                    c.semanticRank, c.semanticScore));
        }
        Collections.sort(result, RetrievedChunk.byFusedScore());
        return result;
    }

    private double contribution(double weight, int rank) {
        return rank > 0 ? weight / (rankConstant + rank) : 0.0;
    }

    private static void collect(Map<KnowledgeChunkId, Candidate> byChunk, List<KnowledgeSearchHit> hits,
                                KnowledgeSearchMode mode) {
        if (hits == null) {
            return;
        }
        int rank = 0;
        for (KnowledgeSearchHit hit : hits) {
            if (hit.mode() != mode) {
                throw new IllegalArgumentException("Treffer " + hit + " steht in der " + mode + "-Liste");
            }
            Candidate candidate = byChunk.get(hit.chunk().id());
            if (candidate == null) {
                candidate = new Candidate(hit);
                byChunk.put(hit.chunk().id(), candidate);
            }
            // Ein Chunk zählt je Pfad nur mit seinem besten (ersten) Rang; Ränge sind dicht über die eindeutigen Chunks.
            if (mode == KnowledgeSearchMode.KEYWORD && candidate.keywordRank == 0) {
                candidate.keywordRank = ++rank;
                candidate.keywordScore = hit.score();
            } else if (mode == KnowledgeSearchMode.SEMANTIC && candidate.semanticRank == 0) {
                candidate.semanticRank = ++rank;
                candidate.semanticScore = hit.score();
            }
        }
    }

    private static final class Candidate {
        final KnowledgeSearchHit hit;
        int keywordRank;
        double keywordScore;
        int semanticRank;
        double semanticScore;

        Candidate(KnowledgeSearchHit hit) {
            this.hit = hit;
        }
    }
}
