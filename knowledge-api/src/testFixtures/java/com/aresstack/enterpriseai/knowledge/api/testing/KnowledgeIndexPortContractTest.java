package com.aresstack.enterpriseai.knowledge.api.testing;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingWorldMismatchException;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchMode;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.OTHER_SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_2D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.chunk;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.richResource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Vertrag des {@link KnowledgeIndexPort}. Jede Implementierung erbt diesen Test und liefert in
 * {@link #createIndex()} einen leeren Index. Persistente Implementierungen überschreiben {@link #reopen}, damit
 * jede Prüfung zusätzlich nach Schließen und Wiederöffnen läuft.
 */
public abstract class KnowledgeIndexPortContractTest {

    private KnowledgeIndexPort index;

    /** Ein neuer, leerer Index. */
    protected abstract KnowledgeIndexPort createIndex();

    /**
     * Schließt den Index und öffnet denselben Bestand neu (persistente Adapter); Default: derselbe Index.
     */
    protected KnowledgeIndexPort reopen(KnowledgeIndexPort current) {
        return current;
    }

    @Before
    public void createEmptyIndex() {
        index = createIndex();
    }

    /** Index nach dem Schreiben, ggf. wiedergeöffnet. */
    private KnowledgeIndexPort written() {
        index = reopen(index);
        return index;
    }

    private static List<String> ids(List<KnowledgeSearchHit> hits) {
        List<String> ids = new ArrayList<String>();
        for (KnowledgeSearchHit hit : hits) {
            ids.add(hit.chunk().id().value());
        }
        return ids;
    }

    private List<String> keyword(String text) {
        return ids(written().keywordSearch(KnowledgeKeywordQuery.of(SPACE_3D, text, 10)));
    }

    private List<String> semantic(float... query) {
        return ids(written().semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, query), 10)));
    }

    @Test
    public void emptyIndexFindsNothing() {
        assertEquals(Collections.<String>emptyList(), keyword("irgendwas"));
        assertEquals(Collections.<String>emptyList(), semantic(1, 0, 0));
    }

    @Test
    public void keywordSearchFindsMatchingChunksOnly() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "Lucene ist eine Volltextsuche.", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "Cosine misst Winkel zwischen Vektoren.", vector(SPACE_3D, 0, 1, 0))));

        List<KnowledgeSearchHit> hits = written().keywordSearch(KnowledgeKeywordQuery.of(SPACE_3D, "volltextsuche", 10));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), ids(hits));
        assertEquals(KnowledgeSearchMode.KEYWORD, hits.get(0).mode());
        assertTrue(hits.get(0).score() > 0);
        assertEquals(Collections.<String>emptyList(), keyword("kartoffel"));
        assertEquals(Collections.<String>emptyList(), keyword("  "));
    }

    @Test
    public void keywordSearchHandlesUmlautsCaseInsensitively() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Collections.singletonList(
                entry(a, 0, "Die Größe der Übergabe prüfen.", vector(SPACE_3D, 1, 0, 0))));

        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), keyword("GRÖSSE größe"));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), keyword("übergabe"));
    }

    @Test
    public void semanticSearchRanksByCosine() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "x-Achse", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "diagonal", vector(SPACE_3D, 1, 1, 0)),
                entry(a, 2, "z-Achse", vector(SPACE_3D, 0, 0, 5))));

        List<KnowledgeSearchHit> hits =
                written().semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 2, 0, 0), 10));
        assertEquals(Arrays.asList("wiki:x/A#chunk-0", "wiki:x/A#chunk-1", "wiki:x/A#chunk-2"), ids(hits));
        assertEquals(1.0, hits.get(0).score(), 1e-6);
        assertEquals(Math.sqrt(0.5), hits.get(1).score(), 1e-6);
        assertEquals(0.0, hits.get(2).score(), 1e-6);
        assertEquals(KnowledgeSearchMode.SEMANTIC, hits.get(0).mode());
    }

    @Test
    public void resultsAreLimitedAndTiesOrderedByChunkId() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        List<KnowledgeIndexEntry> entries = new ArrayList<KnowledgeIndexEntry>();
        for (int i = 0; i < 12; i++) {
            entries.add(entry(a, i, "gleich", vector(SPACE_3D, 1, 1, 1)));
        }
        index.index(entries);

        List<KnowledgeSearchHit> hits =
                written().semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 1, 1), 3));
        assertEquals(Arrays.asList("wiki:x/A#chunk-0", "wiki:x/A#chunk-1", "wiki:x/A#chunk-10"), ids(hits));
        List<KnowledgeSearchHit> keywordHits =
                written().keywordSearch(KnowledgeKeywordQuery.of(SPACE_3D, "gleich", 5));
        assertEquals(Arrays.asList("wiki:x/A#chunk-0", "wiki:x/A#chunk-1", "wiki:x/A#chunk-10", "wiki:x/A#chunk-11",
                "wiki:x/A#chunk-2"), ids(keywordHits));
    }

    @Test
    public void hitsCarryResourceAndChunkLosslessly() {
        KnowledgeResource rich = richResource("confluence:dc/page/4711", "confluence-dc");
        KnowledgeIndexEntry entry = KnowledgeIndexEntry.of(rich,
                chunk(rich, 3, "Inhalt mit Größe und Überschrift.", "Kapitel", "Größe"), vector(SPACE_3D, 0, 1, 0));
        index.index(Collections.singletonList(entry));

        for (KnowledgeSearchHit hit : Arrays.asList(
                written().keywordSearch(KnowledgeKeywordQuery.of(SPACE_3D, "inhalt", 1)).get(0),
                written().semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 0, 1, 0), 1)).get(0))) {
            assertEquals(rich, hit.resource());
            assertEquals(entry.chunk(), hit.chunk());
            assertEquals("https://wiki.example/pages/" + Math.abs("confluence:dc/page/4711".hashCode()),
                    hit.resource().location().get().toString());
        }
    }

    @Test
    public void indexingIsIdempotentAndUpsertsByChunkId() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeIndexEntry first = entry(a, 0, "alter Inhalt", vector(SPACE_3D, 1, 0, 0));
        index.index(Collections.singletonList(first));
        index.index(Collections.singletonList(first));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), keyword("inhalt"));

        index.index(Collections.singletonList(entry(a, 0, "neuer Text", vector(SPACE_3D, 0, 1, 0))));
        assertEquals(Collections.<String>emptyList(), keyword("alter"));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), keyword("neuer"));
        List<KnowledgeSearchHit> hits =
                written().semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 0, 1, 0), 10));
        assertEquals(1, hits.size());
        assertEquals(1.0, hits.get(0).score(), 1e-6);
    }

    @Test
    public void replaceSupersedesAllChunksOfOneResourceInOneNamespace() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "alpha eins", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "alpha zwei", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 2, "alpha drei", vector(SPACE_3D, 1, 0, 0)),
                entry(b, 0, "alpha bravo", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 0, "alpha anderer namespace", vector(OTHER_SPACE_3D, 1, 0, 0))));

        index.replace(SPACE_3D, a.id(), Collections.singletonList(entry(a, 0, "alpha neu", vector(SPACE_3D, 1, 0, 0))));

        assertEquals(Arrays.asList("wiki:x/A#chunk-0", "wiki:x/B#chunk-0"), sorted(keyword("alpha")));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), keyword("neu"));
        assertEquals("anderer Namespace bleibt unberührt", 1,
                written().keywordSearch(KnowledgeKeywordQuery.of(OTHER_SPACE_3D, "namespace", 10)).size());

        index.replace(SPACE_3D, a.id(), Collections.<KnowledgeIndexEntry>emptyList());
        assertEquals(Collections.singletonList("wiki:x/B#chunk-0"), keyword("alpha"));
    }

    @Test
    public void replaceRejectsEntriesOfAnotherResourceOrNamespace() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        index.index(Collections.singletonList(entry(a, 0, "bleibt", vector(SPACE_3D, 1, 0, 0))));
        try {
            index.replace(SPACE_3D, a.id(), Collections.singletonList(entry(b, 0, "x", vector(SPACE_3D, 1, 0, 0))));
            fail("fremde Ressource");
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        try {
            index.replace(SPACE_3D, a.id(),
                    Collections.singletonList(entry(a, 0, "x", vector(OTHER_SPACE_3D, 1, 0, 0))));
            fail("fremder Namespace");
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        assertEquals("abgelehnter Replace ändert nichts", Collections.singletonList("wiki:x/A#chunk-0"),
                keyword("bleibt"));
    }

    @Test
    public void namespacesAreNeverMixed() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "drei dimensionen", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "anderes modell", vector(OTHER_SPACE_3D, 1, 0, 0)),
                entry(a, 2, "zwei dimensionen", vector(SPACE_2D, 1, 0))));

        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), semantic(1, 0, 0));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-1"), ids(written().semanticSearch(
                KnowledgeSemanticQuery.of(vector(OTHER_SPACE_3D, 1, 0, 0), 10))));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-2"), ids(written().semanticSearch(
                KnowledgeSemanticQuery.of(vector(SPACE_2D, 1, 0), 10))));
        assertEquals(Collections.<String>emptyList(), keyword("modell"));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), keyword("dimensionen"));
    }

    @Test
    public void semanticQueryOfAnotherWorldSeesNothing() {
        // Gleiche Dimension, anderer Fingerprint: Wissen aus OTHER_SPACE_3D bleibt für eine SPACE_3D-Anfrage
        // unsichtbar, statt mit einer bedeutungslosen Ähnlichkeit zu erscheinen.
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Collections.singletonList(entry(a, 0, "fremd", vector(OTHER_SPACE_3D, 1, 0, 0))));
        assertEquals(Collections.<String>emptyList(), semantic(1, 0, 0));
        try {
            vector(SPACE_3D, 1, 0, 0).cosineSimilarity(vector(OTHER_SPACE_3D, 1, 0, 0));
            fail();
        } catch (EmbeddingWorldMismatchException expected) {
            // Domain-Regel, auf die sich der Vertrag stützt
        }
    }

    @Test
    public void removeDropsAResourceFromAllNamespaces() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "weg", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 0, "weg", vector(OTHER_SPACE_3D, 1, 0, 0)),
                entry(b, 0, "weg bleibt", vector(SPACE_3D, 1, 0, 0))));

        index.remove(a.id());

        assertEquals(Collections.singletonList("wiki:x/B#chunk-0"), keyword("weg"));
        assertEquals(Collections.<KnowledgeSearchHit>emptyList(),
                written().keywordSearch(KnowledgeKeywordQuery.of(OTHER_SPACE_3D, "weg", 10)));
    }

    @Test
    public void removeSourceAndSourceFilter() {
        KnowledgeResource wiki = resource("wiki:x/A", "wiki");
        KnowledgeResource confluence = resource("confluence:dc/page/1", "confluence-dc");
        index.index(Arrays.asList(
                entry(wiki, 0, "gemeinsam", vector(SPACE_3D, 1, 0, 0)),
                entry(confluence, 0, "gemeinsam", vector(SPACE_3D, 1, 0, 0))));

        List<KnowledgeSourceId> onlyWiki = Collections.singletonList(KnowledgeSourceId.of("wiki"));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), ids(written().keywordSearch(
                KnowledgeKeywordQuery.of(SPACE_3D, "gemeinsam", 10).restrictedTo(onlyWiki))));
        assertEquals(Collections.singletonList("wiki:x/A#chunk-0"), ids(written().semanticSearch(
                KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 10).restrictedTo(onlyWiki))));

        index.removeSource(KnowledgeSourceId.of("wiki"));
        assertEquals(Collections.singletonList("confluence:dc/page/1#chunk-0"), keyword("gemeinsam"));
    }

    @Test
    public void resourceIdsListIndexedResourcesOfOneSourceInOneNamespace() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        KnowledgeResource c = resource("confluence:dc/1", "confluence");
        index.index(Arrays.asList(
                entry(a, 0, "eins", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "zwei", vector(SPACE_3D, 0, 1, 0)),
                entry(b, 0, "drei", vector(SPACE_3D, 0, 0, 1)),
                entry(c, 0, "vier", vector(SPACE_3D, 1, 1, 0)),
                entry(b, 0, "drei andere Welt", vector(OTHER_SPACE_3D, 0, 0, 1))));

        assertEquals(resourceIds("wiki:x/A", "wiki:x/B"),
                written().resourceIds(SPACE_3D, KnowledgeSourceId.of("wiki")));
        assertEquals(resourceIds("confluence:dc/1"),
                written().resourceIds(SPACE_3D, KnowledgeSourceId.of("confluence")));
        assertEquals(resourceIds("wiki:x/B"), written().resourceIds(OTHER_SPACE_3D, KnowledgeSourceId.of("wiki")));
        assertEquals(resourceIds(), written().resourceIds(SPACE_2D, KnowledgeSourceId.of("wiki")));
        assertEquals(resourceIds(), written().resourceIds(SPACE_3D, KnowledgeSourceId.of("unbekannt")));
        try {
            written().resourceIds(SPACE_3D, KnowledgeSourceId.of("wiki")).clear();
            fail("unveränderlich erwartet");
        } catch (UnsupportedOperationException expected) {
            // erwartet
        }
    }

    @Test
    public void resourceIdsFollowReplaceAndRemove() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        KnowledgeSourceId wiki = KnowledgeSourceId.of("wiki");
        index.index(Arrays.asList(
                entry(a, 0, "eins", vector(SPACE_3D, 1, 0, 0)),
                entry(b, 0, "zwei", vector(SPACE_3D, 0, 1, 0))));

        written().replace(SPACE_3D, a.id(), Collections.<KnowledgeIndexEntry>emptyList());
        assertEquals("replace mit leerer Liste entfernt die Ressource", resourceIds("wiki:x/B"),
                written().resourceIds(SPACE_3D, wiki));
        written().replace(SPACE_3D, a.id(), Collections.singletonList(entry(a, 0, "neu", vector(SPACE_3D, 1, 0, 0))));
        assertEquals(resourceIds("wiki:x/A", "wiki:x/B"), written().resourceIds(SPACE_3D, wiki));
        written().remove(b.id());
        assertEquals(resourceIds("wiki:x/A"), written().resourceIds(SPACE_3D, wiki));
        written().removeSource(wiki);
        assertEquals(resourceIds(), written().resourceIds(SPACE_3D, wiki));
    }

    @Test
    public void revisionOfReportsTheStoredRevisionPerNamespace() {
        KnowledgeResource v1 = resource("wiki:x/A", "wiki").toBuilder()
                .revision(KnowledgeRevision.of(Instant.parse("2026-10-01T08:30:00Z"), "1")).build();
        KnowledgeResource v2 = v1.toBuilder().revision(KnowledgeRevision.version("2")).build();
        KnowledgeResource unknown = resource("wiki:x/B", "wiki");
        index.index(Arrays.asList(
                entry(v1, 0, "eins", vector(SPACE_3D, 1, 0, 0)),
                entry(v1, 1, "zwei", vector(SPACE_3D, 0, 1, 0)),
                entry(unknown, 0, "ohne Revision", vector(SPACE_3D, 0, 0, 1))));

        assertEquals(Optional.of(v1.revision()), written().revisionOf(SPACE_3D, v1.id()));
        assertEquals("unbekannte Revision ist trotzdem indexiert", Optional.of(KnowledgeRevision.unknown()),
                written().revisionOf(SPACE_3D, unknown.id()));
        assertEquals(Optional.empty(), written().revisionOf(OTHER_SPACE_3D, v1.id()));
        assertEquals(Optional.empty(), written().revisionOf(SPACE_3D, KnowledgeResourceId.of("wiki:x/fehlt")));

        written().replace(SPACE_3D, v2.id(),
                Collections.singletonList(entry(v2, 0, "eins neu", vector(SPACE_3D, 1, 0, 0))));
        assertEquals(Optional.of(KnowledgeRevision.version("2")), written().revisionOf(SPACE_3D, v2.id()));
        written().remove(v2.id());
        assertFalse(written().revisionOf(SPACE_3D, v2.id()).isPresent());
    }

    @Test
    public void revisionOfIsEmptyWhenChunksDisagree() {
        KnowledgeResource v1 = resource("wiki:x/A", "wiki").toBuilder()
                .revision(KnowledgeRevision.version("1")).build();
        KnowledgeResource v2 = v1.toBuilder().revision(KnowledgeRevision.version("2")).build();
        index.index(Arrays.asList(
                entry(v1, 0, "eins", vector(SPACE_3D, 1, 0, 0)),
                entry(v2, 1, "zwei", vector(SPACE_3D, 0, 1, 0))));

        assertEquals("gemischte Revisionen gelten als nicht sauber indexiert", Optional.empty(),
                written().revisionOf(SPACE_3D, v1.id()));
    }

    private static Set<KnowledgeResourceId> resourceIds(String... values) {
        Set<KnowledgeResourceId> ids = new HashSet<KnowledgeResourceId>();
        for (String value : values) {
            ids.add(KnowledgeResourceId.of(value));
        }
        return ids;
    }

    @Test
    public void rebuildReplacesTheWholeIndex() {
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        KnowledgeResource b = resource("wiki:x/B", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "veraltet", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "veraltet", vector(OTHER_SPACE_3D, 1, 0, 0))));

        index.rebuild(Collections.singletonList(entry(b, 0, "aktuell", vector(SPACE_3D, 0, 1, 0))));

        assertEquals(Collections.<String>emptyList(), keyword("veraltet"));
        assertEquals(Collections.<KnowledgeSearchHit>emptyList(),
                written().keywordSearch(KnowledgeKeywordQuery.of(OTHER_SPACE_3D, "veraltet", 10)));
        assertEquals(Collections.singletonList("wiki:x/B#chunk-0"), keyword("aktuell"));
        assertEquals(Collections.singletonList("wiki:x/B#chunk-0"), semantic(0, 1, 0));

        index.rebuild(Collections.<KnowledgeIndexEntry>emptyList());
        assertEquals(Collections.<String>emptyList(), keyword("aktuell"));
    }

    private static List<String> sorted(List<String> values) {
        List<String> copy = new ArrayList<String>(values);
        Collections.sort(copy);
        return copy;
    }
}
