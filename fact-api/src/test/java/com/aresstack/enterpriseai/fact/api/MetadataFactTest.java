package com.aresstack.enterpriseai.fact.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class MetadataFactTest {

    private static final KnowledgeResourceId PAGE = KnowledgeResourceId.of("wiki:wiki.example/w/Glossar");

    @Test
    public void typeIdsAreUpperCaseNames() {
        assertEquals("BUSINESS_RULE", FactTypeId.of("BUSINESS_RULE").value());
        for (String invalid : new String[] {null, "", "acronym", "1ACRONYM", "ACRO NYM", repeat('A', 65)}) {
            try {
                FactTypeId.of(invalid);
                fail("accepted " + invalid);
            } catch (IllegalArgumentException expected) {
                // rejected
            }
        }
    }

    @Test
    public void payloadMustBeAJsonObject() {
        assertEquals("{\"a\":1}", FactPayload.json("  {\"a\":1} ").json());
        for (String invalid : new String[] {null, "", "[1]", "\"text\"", "{"}) {
            try {
                FactPayload.json(invalid);
                fail("accepted " + invalid);
            } catch (IllegalArgumentException expected) {
                // rejected
            }
        }
    }

    @Test
    public void newFactsAreUnreviewedWithoutTimestamps() {
        MetadataFact fact = acronym(FactProvenance.automatic("acronyms", "1"));

        assertEquals(FactReview.UNREVIEWED, fact.review());
        assertEquals(1, fact.schemaVersion());
        assertNull(fact.createdAt());
        assertNull(fact.anchor());
        assertEquals(fact, fact.toBuilder().build());
        assertNotEquals(fact.id(), acronym(FactProvenance.automatic("acronyms", "1")).id());
    }

    @Test
    public void contentHashDecidesFreshnessBeforeRevision() {
        MetadataFact fact = acronym(FactProvenance.automatic("acronyms", "1")
                .withSourceRevision(KnowledgeRevision.version("7")).withContentHash("sha256:aaa"));

        assertEquals(FactFreshness.CURRENT, fact.freshness(KnowledgeRevision.version("8"), "sha256:aaa"));
        assertEquals(FactFreshness.STALE, fact.freshness(KnowledgeRevision.version("7"), "sha256:bbb"));
        assertEquals(FactFreshness.CURRENT, fact.freshness(KnowledgeRevision.version("7"), null));
        assertEquals(FactFreshness.STALE, fact.freshness(KnowledgeRevision.version("8"), ""));
        assertEquals(FactFreshness.UNKNOWN, fact.freshness(KnowledgeRevision.unknown(), null));
        // The analysed state never changes the identity the fact is bound to.
        assertEquals(PAGE, fact.resourceId());
    }

    @Test
    public void factWithoutAnalysedStateHasUnknownFreshness() {
        MetadataFact fact = acronym(FactProvenance.manual("manual"));

        assertEquals(FactFreshness.UNKNOWN,
                fact.freshness(KnowledgeRevision.of(Instant.parse("2026-10-01T10:00:00Z"), "3"), "sha256:aaa"));
    }

    @Test
    public void anchorNeedsAPartAndValidRanges() {
        FactAnchor anchor = FactAnchor.builder().section("Glossar").lines(3, 3).quote("IAR").build();
        assertEquals(Integer.valueOf(3), anchor.lineEnd());
        assertEquals("", anchor.structurePath());
        for (FactAnchor.Builder invalid : new FactAnchor.Builder[] {
                FactAnchor.builder(),
                FactAnchor.builder().lines(5, 4),
                FactAnchor.builder().characters(null, 4),
                FactAnchor.builder().quote(repeat('x', FactAnchor.MAX_QUOTE_LENGTH + 1))}) {
            try {
                invalid.build();
                fail("accepted an invalid anchor");
            } catch (IllegalArgumentException expected) {
                // rejected
            }
        }
    }

    @Test
    public void typeVersionStartsAtOne() {
        assertEquals(1, FactType.of(FactTypeId.of("ACRONYM"), "Acronym").schemaVersion());
        try {
            FactType.of(FactTypeId.of("ACRONYM"), "Acronym", 0, null);
            fail("accepted version 0");
        } catch (IllegalArgumentException expected) {
            // rejected
        }
    }

    private static MetadataFact acronym(FactProvenance provenance) {
        return MetadataFact.builder(PAGE, FactTypeId.of("ACRONYM"), FactPayload.json("{\"acronym\":\"IAR\"}"),
                provenance).build();
    }

    private static String repeat(char c, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < count; i++) {
            text.append(c);
        }
        return text.toString();
    }
}
