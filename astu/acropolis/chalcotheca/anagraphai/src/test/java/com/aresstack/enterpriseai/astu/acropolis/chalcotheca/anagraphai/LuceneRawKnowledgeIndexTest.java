package com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeDocument;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeHit;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Rohindex in Lucene: geladene Seite sofort durchsuchbar, Textfenster statt ganzer Seite, Fassung überlebt Neustart. */
public class LuceneRawKnowledgeIndexTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static RawKnowledgeDocument page(String id, String source, String text) {
        KnowledgeResource resource = KnowledgeIndexTestData.richResource(id, source);
        return RawKnowledgeDocument.of(KnowledgeDocument.of(resource, text), "Snippet " + id);
    }

    private static String longText(String fact) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append("Allgemeiner Absatz ").append(i).append(" ohne Bezug zur Frage. ");
        }
        text.append(fact).append(' ');
        for (int i = 0; i < 40; i++) {
            text.append("Weiterer Absatz ").append(i).append(" mit anderem Thema. ");
        }
        return text.toString();
    }

    @Test
    public void storedPageIsFoundWithWindowsAroundTheMatch() throws Exception {
        try (LuceneRawKnowledgeIndex raw = new LuceneRawKnowledgeIndex(folder.getRoot().toPath())) {
            raw.put(page("wiki:iar", "wiki", longText("IAR ist ein Beispielsystem für Testzwecke.")));
            raw.put(page("wiki:other", "wiki", longText("Kaffee gibt es im zweiten Stock.")));

            List<RawKnowledgeHit> hits = raw.search(RawKnowledgeQuery.of("Was ist IAR", 5, 2, 300));

            assertEquals("wiki:iar", hits.get(0).resource().id().value());
            String best = hits.get(0).windows().get(0).text();
            assertTrue(best, best.contains("IAR ist ein Beispielsystem"));
            assertTrue("ein Fenster, nicht die ganze Seite", best.length() <= 400);
            assertEquals("Snippet wiki:iar", hits.get(0).snippet());
        }
    }

    @Test
    public void revisionAndHashSurviveAReopen() throws Exception {
        RawKnowledgeDocument stored = page("wiki:iar", "wiki", "IAR in kurz.");
        try (LuceneRawKnowledgeIndex raw = new LuceneRawKnowledgeIndex(folder.getRoot().toPath())) {
            raw.put(stored);
        }
        try (LuceneRawKnowledgeIndex raw = new LuceneRawKnowledgeIndex(folder.getRoot().toPath())) {
            assertEquals(stored.contentHash(), raw.contentHashOf(stored.id()).get());
            assertEquals(stored.resource().revision(), raw.revisionOf(stored.id()).get());
            assertEquals("IAR in kurz.", raw.get(stored.id()).get().text());
        }
    }

    @Test
    public void newerCaptureReplacesTheOlderOneAndSourcesFilter() throws Exception {
        try (LuceneRawKnowledgeIndex raw = new LuceneRawKnowledgeIndex(folder.getRoot().toPath())) {
            raw.put(page("wiki:iar", "wiki", "IAR alt."));
            raw.put(page("wiki:iar", "wiki", "IAR neu."));
            raw.put(page("other:iar", "other", "IAR woanders."));

            List<RawKnowledgeHit> hits = raw.search(RawKnowledgeQuery.of("IAR", 5, 1, 200)
                    .restrictedTo(Collections.singleton(SourceAddress.of("wiki"))));

            assertEquals(1, hits.size());
            assertTrue(hits.get(0).windows().get(0).text().contains("neu"));
            raw.removeSource(SourceAddress.of("wiki"));
            assertFalse(raw.get(hits.get(0).resource().id()).isPresent());
        }
    }
}
