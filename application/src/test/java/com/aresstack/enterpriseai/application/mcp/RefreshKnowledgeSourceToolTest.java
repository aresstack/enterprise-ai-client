package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RefreshKnowledgeSourceToolTest {

    private static final String TOOL = KnowledgeMcpTools.REFRESH_KNOWLEDGE_SOURCE;

    @Test
    public void indexesTheSourceInItsScopeAndSummarisesTheReport() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        String text = f.ok(TOOL, "source_id", "wiki");

        assertEquals("Quelle: wiki\n"
                + "Status: vollständig\n"
                + "Gefunden: 3\n"
                + "Indexiert: 3 (Chunks: 3)\n"
                + "Unverändert: 0\n"
                + "Leer: 0\n"
                + "Entfernt: 0\n"
                + "Verschwunden: 0\n"
                + "Duplikate: 0\n"
                + "Fehler: 0", text);
        assertEquals(1, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "entkalkt", 5)).size());
        assertEquals("nur die Quelle aus dem Aufruf wird indexiert",
                0, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "Urlaub", 5)).size());
    }

    /** Discovery liefert alle drei Seiten, das Laden scheitert je Seite anders (die Fake-Quelle allein kann das nicht). */
    private static KnowledgeToolFixture withFlakySource(KnowledgeToolSettings settings, final InMemoryKnowledgeSource flaky,
                                                        final Map<String, KnowledgeSourceException.Kind> loadFailures) {
        return new KnowledgeToolFixture(settings, null, Collections.singletonList(new KnowledgeSourceRegistration(
                new KnowledgeToolFixture.DelegatingSource(flaky) {
                    @Override
                    public KnowledgeDocument load(KnowledgeResourceId id) throws KnowledgeSourceException {
                        String key = id.schemeSpecificPart().substring(id.schemeSpecificPart().indexOf('/') + 1);
                        KnowledgeSourceException.Kind kind = loadFailures.get(key);
                        if (kind != null) {
                            throw new KnowledgeSourceException(kind, "simulated " + kind + " for " + key);
                        }
                        return delegate.load(id);
                    }
                }, SourceScope.of("A", "B", "C"))));
    }

    @Test
    public void reportsRemovedEmptyAndFailedResourcesPerStage() {
        InMemoryKnowledgeSource flaky = new InMemoryKnowledgeSource("flaky")
                .add("A", "Seite A", "Alpha Inhalt").add("B", "Seite B", "Beta Inhalt").add("C", "Seite C", "Gamma Inhalt");
        Map<String, KnowledgeSourceException.Kind> loadFailures = new HashMap<String, KnowledgeSourceException.Kind>();
        KnowledgeToolFixture f = withFlakySource(KnowledgeToolSettings.defaults(), flaky, loadFailures);
        assertTrue(f.indexing.indexSource(flaky, SourceScope.of("A", "B", "C"), null).isComplete());
        // Alle drei geändert, sonst würden sie als unverändert übersprungen und gar nicht erst geladen.
        flaky.update("A", "Alpha Inhalt, überarbeitet");
        flaky.update("B", "   ");
        flaky.update("C", "Gamma Inhalt, überarbeitet");
        loadFailures.put("A", KnowledgeSourceException.Kind.UNAVAILABLE);
        loadFailures.put("C", KnowledgeSourceException.Kind.NOT_FOUND);

        String text = f.ok(TOOL, "source_id", "flaky");

        assertTrue(text, text.contains("Status: abgeschlossen mit Fehlern\n"));
        assertTrue(text, text.contains("Gefunden: 3\n"));
        assertTrue(text, text.contains("Indexiert: 0 (Chunks: 0)\n"));
        assertTrue(text, text.contains("Unverändert: 0\n"));
        assertTrue(text, text.contains("Leer: 1\n"));
        assertTrue(text, text.contains("Entfernt: 1\n"));
        assertTrue(text, text.contains("Verschwunden: 0\n"));
        assertTrue(text, text.contains("Fehler: 1 (Laden: 1, Embedding: 0, Index: 0)\n"));
        assertTrue(text, text.contains("  - memory:flaky/A: Laden aus der Quelle fehlgeschlagen"));
        assertFalse("die Port-Meldung bleibt dem Modell verborgen", text.contains("simulated"));
        assertEquals("entfernt und leer räumen den Index, der Fehler lässt den alten Stand stehen",
                1, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "Alpha", 5)).size());
        assertEquals(0, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "Beta", 5)).size());
        assertEquals(0, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "Gamma", 5)).size());
    }

    @Test
    public void secondRefreshSkipsUnchangedPagesAndRemovesVanishedOnes() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        int before = f.wiki.calls().size();
        f.wiki.update("Drucker", "Drucker werden jetzt zentral per IPP verteilt.");
        f.wiki.remove("Kaffee");

        String text = f.ok(TOOL, "source_id", "wiki");

        assertEquals("Quelle: wiki\n"
                + "Status: vollständig\n"
                + "Gefunden: 2\n"
                + "Indexiert: 1 (Chunks: 1)\n"
                + "Unverändert: 1\n"
                + "Leer: 0\n"
                + "Entfernt: 0\n"
                + "Verschwunden: 1\n"
                + "Duplikate: 0\n"
                + "Fehler: 0", text);
        List<String> calls = f.wiki.calls().subList(before, f.wiki.calls().size());
        assertEquals("nur die geänderte Seite wird geladen", Arrays.asList("discover:[Java, Drucker, Kaffee]", "load:Drucker"),
                calls);
        assertEquals(1, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "IPP", 5)).size());
        assertEquals(0, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "CUPS", 5)).size());
        assertEquals("unverändert bleibt", 1, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "openjdk", 5)).size());
        assertEquals("verschwunden ist weg", 0, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "entkalkt", 5)).size());
    }

    @Test
    public void failedPruneIsReportedWithoutTheIndexMessageAndRemovesNothing() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.wiki.remove("Kaffee");
        f.index.failResourceIds = true;

        String text = f.ok(TOOL, "source_id", "wiki");

        assertTrue(text, text.contains("Status: abgeschlossen mit Fehlern\n"));
        assertTrue(text, text.contains("Unverändert: 2\n"));
        assertTrue(text, text.contains("Verschwunden: 0\n"));
        assertTrue(text, text.contains("Fehler: 0\n"));
        assertTrue(text, text.endsWith("Bereinigung: fehlgeschlagen, verschwundene Dokumente wurden nicht entfernt"));
        assertFalse("Indexpfade bleiben dem Modell verborgen", text.contains("/var/lib"));
        assertEquals(1, f.index.keywordSearch(KnowledgeKeywordQuery.of(f.space, "entkalkt", 5)).size());
    }

    @Test
    public void failureListIsCappedBySettings() {
        InMemoryKnowledgeSource flaky = new InMemoryKnowledgeSource("flaky")
                .add("A", "Seite A", "Alpha").add("B", "Seite B", "Beta").add("C", "Seite C", "Gamma");
        Map<String, KnowledgeSourceException.Kind> loadFailures = new HashMap<String, KnowledgeSourceException.Kind>();
        loadFailures.put("A", KnowledgeSourceException.Kind.ACCESS_DENIED);
        loadFailures.put("B", KnowledgeSourceException.Kind.ACCESS_DENIED);
        KnowledgeToolFixture f = withFlakySource(KnowledgeToolSettings.defaults().withMaxFailuresListed(1), flaky,
                loadFailures);

        String text = f.ok(TOOL, "source_id", "flaky");

        assertTrue(text, text.contains("Indexiert: 1 (Chunks: 1)\n"));
        assertTrue(text, text.contains("Fehler: 2 (Laden: 2, Embedding: 0, Index: 0)\n"));
        assertTrue(text, text.contains("  - memory:flaky/A: Laden aus der Quelle fehlgeschlagen"));
        assertFalse(text, text.contains("memory:flaky/B"));
        assertTrue(text, text.endsWith("  … 1 weitere Fehler"));
    }

    @Test
    public void discoveryFailureIsReportedWithoutProcessingAnything() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        f.wiki.failWith("Java", KnowledgeSourceException.Kind.UNAVAILABLE);

        String text = f.ok(TOOL, "source_id", "wiki");

        assertTrue(text, text.contains(
                "Status: Discovery fehlgeschlagen (Quelle nicht erreichbar oder Zugriff verweigert), nichts verarbeitet\n"));
        assertTrue(text, text.contains("Gefunden: 0\n"));
        assertTrue(text, text.endsWith("Fehler: 0"));
        assertFalse("die Port-Meldung bleibt dem Modell verborgen", text.contains("simulated"));
    }

    @Test
    public void indexFailuresNameTheStageButNotTheIndexMessage() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        f.index.failReplaceWith = "Indexverzeichnis kann nicht gelesen werden: /var/lib/eai/index/text/abc";

        String text = f.ok(TOOL, "source_id", "docs");

        assertTrue(text, text.contains("Status: abgeschlossen mit Fehlern\n"));
        assertTrue(text, text.contains("Fehler: 1 (Laden: 0, Embedding: 0, Index: 1)\n"));
        assertTrue(text, text.endsWith("  - memory:docs/Urlaub: Schreiben in den Index fehlgeschlagen"));
        assertFalse("Indexpfade bleiben dem Modell verborgen", text.contains("/var/lib"));
        assertFalse(text, text.contains("Indexverzeichnis"));
    }

    @Test
    public void unknownInvalidOrMissingSourceIdIsAnError() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        assertEquals("Parameter 'source_id' fehlt oder ist leer.", f.error(TOOL));
        assertEquals("Unbekannte Quelle 'sharepoint'. Konfigurierte Quellen: wiki, docs.",
                f.error(TOOL, "source_id", "sharepoint"));
        assertEquals("Ungültige Quell-ID. Konfigurierte Quellen: wiki, docs.", f.error(TOOL, "source_id", "a b"));
    }

    @Test
    public void shutdownCancelsARunningRefreshBetweenResourcesAndRefusesNewOnes() {
        final KnowledgeToolFixture[] holder = new KnowledgeToolFixture[1];
        InMemoryKnowledgeSource slow = new InMemoryKnowledgeSource("slow")
                .add("A", "A", "Text A").add("B", "B", "Text B").add("C", "C", "Text C");
        KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(), null,
                Collections.singletonList(new KnowledgeSourceRegistration(
                        new KnowledgeToolFixture.DelegatingSource(slow) {
                            @Override
                            public KnowledgeDocument load(KnowledgeResourceId id) throws KnowledgeSourceException {
                                KnowledgeDocument document = delegate.load(id);
                                holder[0].tools.shutdown(); // z. B. Endpoint abgemeldet, während der Lauf läuft
                                return document;
                            }
                        }, SourceScope.of("A", "B", "C"))));
        holder[0] = f;

        String text = f.ok(TOOL, "source_id", "slow");

        assertTrue(text, text.contains("Status: abgebrochen (Werkzeuge beendet), bereits indexierte Dokumente bleiben\n"));
        assertTrue(text, text.contains("Gefunden: 3\n"));
        assertTrue(text, text.contains("Indexiert: 1 (Chunks: 1)\n"));
        assertTrue(f.tools.isShutdown());
        assertEquals("Die Wissenswerkzeuge wurden beendet; keine Aktualisierung mehr möglich.",
                f.error(TOOL, "source_id", "wiki"));
        f.tools.shutdown();
        assertTrue("idempotent", f.tools.isShutdown());
    }

    @Test
    public void secondRefreshOfTheSameSourceIsRefusedWhileTheFirstRuns() throws Exception {
        final CountDownLatch discovering = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        InMemoryKnowledgeSource blocking = new InMemoryKnowledgeSource("blocking").add("A", "A", "Text A");
        final KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(), null,
                Collections.singletonList(new KnowledgeSourceRegistration(
                        new KnowledgeToolFixture.DelegatingSource(blocking) {
                            @Override
                            public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
                                discovering.countDown();
                                try {
                                    release.await(10, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return delegate.discover(scope);
                            }
                        }, SourceScope.of("A"))));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<McpToolResult> first = executor.submit(new java.util.concurrent.Callable<McpToolResult>() {
                @Override
                public McpToolResult call() {
                    return f.call(TOOL, "source_id", "blocking");
                }
            });
            assertTrue(discovering.await(10, TimeUnit.SECONDS));

            String refused = f.error(TOOL, "source_id", "blocking");
            String otherSource = f.ok(TOOL, "source_id", "docs");
            release.countDown();
            McpToolResult result = first.get(10, TimeUnit.SECONDS);

            assertEquals("Die Quelle 'blocking' wird bereits aktualisiert.", refused);
            assertTrue(otherSource, otherSource.startsWith("Quelle: docs\nStatus: vollständig\n"));
            assertFalse(result.getText(), result.isError());
            assertTrue(result.getText(), result.getText().contains("Indexiert: 1 (Chunks: 1)\n"));
            assertTrue("Sperre wieder frei", f.ok(TOOL, "source_id", "blocking").contains("Status: vollständig"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void unexpectedRuntimeFailureIsAGenericError() {
        InMemoryKnowledgeSource broken = new InMemoryKnowledgeSource("broken").add("X", "X", "x");
        KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(), null,
                Collections.singletonList(new KnowledgeSourceRegistration(
                        new KnowledgeToolFixture.DelegatingSource(broken) {
                            @Override
                            public List<KnowledgeResource> discover(SourceScope scope) {
                                throw new IllegalStateException("token=abc123");
                            }
                        }, SourceScope.of("X"))));

        String text = f.error(TOOL, "source_id", "broken");

        assertEquals("Aktualisierung der Quelle 'broken' fehlgeschlagen.", text);
        assertEquals("Sperre nach Fehler wieder frei", text, f.error(TOOL, "source_id", "broken"));
    }

    @Test
    public void contributionDeclaresSourceIdAsRequiredString() {
        McpToolContribution tool = new KnowledgeToolFixture().tool(TOOL);

        assertEquals("refresh_knowledge_source", tool.getName());
        assertEquals(1, tool.getParameters().size());
        assertEquals("source_id", tool.getParameters().get(0).getName());
        assertTrue(tool.getParameters().get(0).isRequired());
        assertTrue(tool.getParameters().get(0).getDescription().contains("wiki, docs"));
    }
}
