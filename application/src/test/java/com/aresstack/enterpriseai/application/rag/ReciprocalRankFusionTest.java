package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchMode;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.chunk;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReciprocalRankFusionTest {

    private static final KnowledgeResource DOC = resource("wiki:Doc", "wiki");

    private static KnowledgeSearchHit keyword(int ordinal, double score) {
        return new KnowledgeSearchHit(DOC, chunk(DOC, ordinal, "text " + ordinal), score, KnowledgeSearchMode.KEYWORD);
    }

    private static KnowledgeSearchHit semantic(int ordinal, double score) {
        return new KnowledgeSearchHit(DOC, chunk(DOC, ordinal, "text " + ordinal), score,
                KnowledgeSearchMode.SEMANTIC);
    }

    private static int ordinal(RetrievedChunk hit) {
        return hit.chunk().ordinal();
    }

    @Test
    public void chunkFoundByBothPathsOutranksChunksFoundByOne() {
        ReciprocalRankFusion fusion = new ReciprocalRankFusion(RetrievalSettings.defaults());
        List<RetrievedChunk> fused = fusion.fuse(
                Arrays.asList(keyword(1, 9.0), keyword(2, 5.0)),
                Arrays.asList(semantic(3, 0.9), semantic(2, 0.8)));

        assertEquals(3, fused.size());
        assertEquals(2, ordinal(fused.get(0)));
        assertEquals(1.0 / 62 + 1.0 / 62, fused.get(0).fusedScore(), 1e-12);
    }

    @Test
    public void deduplicatesPerChunkAndKeepsBothRawScoresAndRanks() {
        List<RetrievedChunk> fused = new ReciprocalRankFusion(RetrievalSettings.defaults()).fuse(
                Collections.singletonList(keyword(7, 12.5)), Collections.singletonList(semantic(7, 0.42)));

        assertEquals(1, fused.size());
        RetrievedChunk hit = fused.get(0);
        assertEquals(12.5, hit.keywordScore().getAsDouble(), 0.0);
        assertEquals(1, hit.keywordRank().getAsInt());
        assertEquals(0.42, hit.semanticScore().getAsDouble(), 0.0);
        assertEquals(1, hit.semanticRank().getAsInt());
        assertTrue(hit.foundBy(RetrievalPath.KEYWORD));
        assertTrue(hit.foundBy(RetrievalPath.SEMANTIC));
    }

    @Test
    public void missingPathLeavesScoreAndRankEmpty() {
        List<RetrievedChunk> fused = new ReciprocalRankFusion(RetrievalSettings.defaults()).fuse(
                Collections.singletonList(keyword(1, 3.0)), Collections.<KnowledgeSearchHit>emptyList());

        assertFalse(fused.get(0).semanticScore().isPresent());
        assertFalse(fused.get(0).semanticRank().isPresent());
        assertFalse(fused.get(0).foundBy(RetrievalPath.SEMANTIC));
    }

    @Test
    public void rawScoreMagnitudeDoesNotMatterOnlyRank() {
        // Ein riesiger BM25-Wert auf Rang 1 wiegt so viel wie Cosine 0.1 auf Rang 1.
        List<RetrievedChunk> fused = new ReciprocalRankFusion(RetrievalSettings.defaults()).fuse(
                Collections.singletonList(keyword(2, 1000.0)), Collections.singletonList(semantic(1, 0.1)));

        assertEquals(fused.get(0).fusedScore(), fused.get(1).fusedScore(), 0.0);
        assertEquals("Gleichstand nach Chunk-ID", 1, ordinal(fused.get(0)));
    }

    @Test
    public void weightsShiftThePreference() {
        RetrievalSettings semanticHeavy = RetrievalSettings.builder().keywordWeight(0.5).semanticWeight(2.0).build();
        List<RetrievedChunk> fused = new ReciprocalRankFusion(semanticHeavy).fuse(
                Collections.singletonList(keyword(1, 5.0)), Collections.singletonList(semantic(2, 0.5)));

        assertEquals(2, ordinal(fused.get(0)));
        assertEquals(2.0 / 61, fused.get(0).fusedScore(), 1e-12);
        assertEquals(0.5 / 61, fused.get(1).fusedScore(), 1e-12);
    }

    @Test
    public void rankConstantIsConfigurable() {
        RetrievalSettings k1 = RetrievalSettings.builder().rankConstant(1).build();
        List<RetrievedChunk> fused = new ReciprocalRankFusion(k1).fuse(
                Arrays.asList(keyword(1, 5.0), keyword(2, 4.0)), Collections.<KnowledgeSearchHit>emptyList());

        assertEquals(1.0 / 2, fused.get(0).fusedScore(), 1e-12);
        assertEquals(1.0 / 3, fused.get(1).fusedScore(), 1e-12);
    }

    @Test
    public void emptyInputsGiveEmptyResult() {
        assertTrue(new ReciprocalRankFusion(RetrievalSettings.defaults())
                .fuse(Collections.<KnowledgeSearchHit>emptyList(), null).isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsHitsInTheWrongList() {
        new ReciprocalRankFusion(RetrievalSettings.defaults())
                .fuse(Collections.singletonList(semantic(1, 0.5)), Collections.<KnowledgeSearchHit>emptyList());
    }

    @Test
    public void settingsRejectNonsense() {
        assertRejected(new Runnable() {
            public void run() {
                RetrievalSettings.builder().keywordEnabled(false).semanticEnabled(false).build();
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                RetrievalSettings.builder().keywordWeight(Double.NaN);
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                RetrievalSettings.builder().semanticEnabled(false).keywordWeight(0).build();
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                RetrievalSettings.builder().keywordCandidates(0);
            }
        });
        assertRejected(new Runnable() {
            public void run() {
                RetrievalSettings.builder().minSemanticScore(1.5);
            }
        });
    }

    private static void assertRejected(Runnable r) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("IllegalArgumentException erwartet");
    }
}
