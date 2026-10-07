package com.aresstack.enterpriseai.knowledge.api;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.chunk;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KnowledgeIndexModelTest {

    @Test
    public void entryRequiresChunkOfItsResource() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        KnowledgeResource sameIdOtherSource = resource("wiki:x/A", "wiki-2");
        KnowledgeIndexEntry entry = KnowledgeIndexEntry.of(a, chunk(a, 0, "Text"), vector(SPACE_3D, 1, 0, 0));
        assertEquals(SPACE_3D, entry.space());
        assertEquals("wiki:x/A#chunk-0", entry.chunkId().value());
        try {
            KnowledgeIndexEntry.of(b, chunk(a, 0, "Text"), vector(SPACE_3D, 1, 0, 0));
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        try {
            KnowledgeIndexEntry.of(sameIdOtherSource, chunk(a, 0, "Text"), vector(SPACE_3D, 1, 0, 0));
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
    }

    @Test
    public void queriesApplyDefaultsLimitsAndSourceFilters() {
        KnowledgeKeywordQuery keyword = KnowledgeKeywordQuery.of(SPACE_3D, null, 0);
        assertEquals("", keyword.text());
        assertEquals(10, keyword.maxResults());
        assertEquals(1000, KnowledgeKeywordQuery.of(SPACE_3D, "x", 5000).maxResults());
        assertTrue(keyword.accepts(KnowledgeSourceId.of("beliebig")));

        KnowledgeSemanticQuery semantic = KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 3)
                .restrictedTo(Arrays.asList(KnowledgeSourceId.of("wiki")));
        assertEquals(SPACE_3D, semantic.space());
        assertTrue(semantic.accepts(KnowledgeSourceId.of("wiki")));
        assertFalse(semantic.accepts(KnowledgeSourceId.of("confluence")));
        assertTrue(semantic.restrictedTo(Collections.<KnowledgeSourceId>emptyList())
                .accepts(KnowledgeSourceId.of("confluence")));
        assertFalse("Query-Text erscheint nicht im toString",
                KnowledgeKeywordQuery.of(SPACE_3D, "geheime frage", 1).toString().contains("geheime"));
    }

    @Test
    public void hitsAreOrderedByScoreThenChunkId() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeSearchHit low = new KnowledgeSearchHit(a, chunk(a, 0, "x"), 0.1, KnowledgeSearchMode.SEMANTIC);
        KnowledgeSearchHit highB = new KnowledgeSearchHit(a, chunk(a, 2, "x"), 0.9, KnowledgeSearchMode.SEMANTIC);
        KnowledgeSearchHit highA = new KnowledgeSearchHit(a, chunk(a, 1, "x"), 0.9, KnowledgeSearchMode.SEMANTIC);
        java.util.List<KnowledgeSearchHit> hits = new java.util.ArrayList<KnowledgeSearchHit>(
                Arrays.asList(low, highB, highA));
        Collections.sort(hits, KnowledgeSearchHit.byRelevance());
        assertEquals(Arrays.asList(highA, highB, low), hits);
        try {
            new KnowledgeSearchHit(a, chunk(a, 0, "x"), Double.NaN, KnowledgeSearchMode.KEYWORD);
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
    }
}
