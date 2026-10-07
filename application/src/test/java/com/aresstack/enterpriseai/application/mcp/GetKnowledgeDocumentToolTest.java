package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolType;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Der Index führt: Gelesen wird nur, was indexiert ist; der Text kommt frisch aus der Quelle. */
public class GetKnowledgeDocumentToolTest {

    private static final String TOOL = KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT;

    private static String notIndexed(String id) {
        return notIndexed(id, null);
    }

    private static String notIndexed(String id, String sourceId) {
        return "Dokument '" + id + "' ist nicht in der Wissensbasis indexiert"
                + (sourceId == null ? "" : " (Quelle '" + sourceId + "')")
                + "; lesbar sind nur indexierte Dokumente, etwa aus Treffern von search_knowledge. Liegt es im "
                + "Bereich einer konfigurierten Quelle, holt refresh_knowledge_source es nach.";
    }

    /** Die Aufrufe einer Quelle ab einem Stand (die Indexierung davor hat selbst entdeckt und geladen). */
    private static List<String> callsSince(InMemoryKnowledgeSource source, int from) {
        List<String> calls = source.calls();
        return calls.subList(from, calls.size());
    }

    @Test
    public void loadsTheFullIndexedDocumentWithHeaderFromItsSource() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        int before = f.wiki.calls().size();

        String text = f.ok(TOOL, "id", f.javaId().value());

        String normalised = "# Linux\n\nJava installiert man mit apt install openjdk-8-jdk.";
        assertEquals("Titel: Java installieren\n"
                + "Id: memory:wiki/Java\n"
                + "Quelle: wiki\n"
                + "Typ: text/markdown\n"
                + "Stand: 2023-11-14T22:13:21Z, Version 1\n"
                + "Bereich: wiki\n"
                + "Zeichen: " + normalised.length() + "\n"
                + "Text:\n"
                + normalised, text);
        assertEquals(Collections.singletonList("load:Java"), callsSince(f.wiki, before));
    }

    @Test
    public void unindexedDocumentIsRefusedWithoutAskingTheSource() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(); // nichts indexiert

        String text = f.error(TOOL, "id", f.javaId().value());

        assertEquals(notIndexed("memory:wiki/Java"), text);
        assertEquals("die Quelle wird nicht gefragt, obwohl sie die Seite kennt", Collections.emptyList(),
                f.wiki.calls());
    }

    @Test
    public void theGateFollowsTheIndexNotTheSource() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.index.remove(f.javaId());

        assertEquals(notIndexed("memory:wiki/Java"), f.error(TOOL, "id", f.javaId().value()));
        assertTrue(f.ok(TOOL, "id", f.wiki.idOf("Drucker").value()).startsWith("Titel: Drucker einrichten\n"));
    }

    @Test
    public void withoutSourceIdTheIndexNamesTheSourceEvenIfItIsNotTheFirst() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        int wikiBefore = f.wiki.calls().size();
        int docsBefore = f.docs.calls().size();

        String text = f.ok(TOOL, "id", f.docs.idOf("Urlaub").value());

        assertTrue(text, text.startsWith("Titel: Urlaubsantrag\nId: memory:docs/Urlaub\nQuelle: docs\n"));
        assertFalse(text, text.contains("Übergeordnet: "));
        assertEquals("die erste Quelle wird nicht gefragt, der Index kennt die Quelle", Collections.emptyList(),
                callsSince(f.wiki, wikiBefore));
        assertEquals(Collections.singletonList("load:Urlaub"), callsSince(f.docs, docsBefore));
    }

    @Test
    public void parentIsShownWhenTheResourceHasOne() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.wiki.addChild("Java", "Windows", "Java unter Windows", "Installer herunterladen.");
        f.indexing.indexResources(f.wiki, Collections.singletonList(f.wiki.idOf("Windows")), null);

        String text = f.ok(TOOL, "id", f.wiki.idOf("Windows").value());

        assertTrue(text, text.contains("Übergeordnet: memory:wiki/Java\n"));
    }

    @Test
    public void explicitSourceIdLoadsFromThatSourceOnly() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();

        String ok = f.ok(TOOL, "id", f.javaId().value(), "source_id", "wiki");
        String foreign = f.error(TOOL, "id", f.javaId().value(), "source_id", "docs");

        assertTrue(ok, ok.startsWith("Titel: Java installieren\n"));
        assertEquals(notIndexed("memory:wiki/Java", "docs"), foreign);
    }

    @Test
    public void unknownDocumentAndUnknownSourceAreShortErrors() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();

        assertEquals(notIndexed("memory:wiki/Nope"), f.error(TOOL, "id", "memory:wiki/Nope"));
        assertEquals(notIndexed("memory:wiki/Nope", "wiki"), f.error(TOOL, "id", "memory:wiki/Nope", "source_id", "wiki"));
        assertEquals(notIndexed("confluence:ABC/1"), f.error(TOOL, "id", "confluence:ABC/1"));
        assertEquals("Unbekannte Quelle 'sharepoint'. Konfigurierte Quellen: wiki, docs.",
                f.error(TOOL, "id", "memory:wiki/Java", "source_id", "sharepoint"));
        assertEquals("Ungültige Quell-ID. Konfigurierte Quellen: wiki, docs.",
                f.error(TOOL, "id", "memory:wiki/Java", "source_id", "../x"));
    }

    @Test
    public void indexedDocumentGoneFromItsSourceIsNotFound() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.wiki.remove("Kaffee"); // verschwunden, aber noch nicht per refresh_knowledge_source bereinigt

        assertEquals("Dokument 'memory:wiki/Kaffee' wurde nicht gefunden.", f.error(TOOL, "id", "memory:wiki/Kaffee"));
        assertEquals("Dokument 'memory:wiki/Kaffee' wurde in Quelle 'wiki' nicht gefunden.",
                f.error(TOOL, "id", "memory:wiki/Kaffee", "source_id", "wiki"));
    }

    @Test
    public void missingOrMalformedIdIsAnError() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        assertEquals("Parameter 'id' fehlt oder ist leer.", f.error(TOOL));
        assertEquals("Ungültige Dokument-ID 'Java': erwartet <schema>:<id>.", f.error(TOOL, "id", "Java"));
    }

    @Test
    public void sourceFailuresAreMappedToShortMessagesWithoutDetails() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.wiki.failWith("Java", KnowledgeSourceException.Kind.ACCESS_DENIED);
        f.wiki.failWith("Drucker", KnowledgeSourceException.Kind.UNAVAILABLE);
        f.wiki.failWith("Kaffee", KnowledgeSourceException.Kind.INVALID_RESPONSE);

        assertEquals("Zugriff auf das Dokument 'memory:wiki/Java' wurde verweigert.",
                f.error(TOOL, "id", "memory:wiki/Java"));
        assertEquals("Die Quelle des Dokuments 'memory:wiki/Drucker' ist derzeit nicht erreichbar.",
                f.error(TOOL, "id", "memory:wiki/Drucker"));
        assertEquals("Die Quelle lieferte für das Dokument 'memory:wiki/Kaffee' eine ungültige Antwort.",
                f.error(TOOL, "id", "memory:wiki/Kaffee"));
    }

    @Test
    public void indexFailureIsAGenericErrorWithoutTheIndexMessage() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.index.failResourceIds = true;

        String text = f.error(TOOL, "id", f.javaId().value());

        assertEquals("Laden des Dokuments 'memory:wiki/Java' fehlgeschlagen.", text);
        assertFalse("Indexpfade bleiben dem Modell verborgen", text.contains("/var/lib"));
    }

    @Test
    public void unexpectedRuntimeFailureIsAGenericErrorWithoutTheCauseText() {
        InMemoryKnowledgeSource broken = new InMemoryKnowledgeSource("broken").add("X", "X", "x");
        final boolean[] explode = {false};
        KnowledgeToolFixture.DelegatingSource source = new KnowledgeToolFixture.DelegatingSource(broken) {
            @Override
            public KnowledgeDocument load(KnowledgeResourceId id) throws KnowledgeSourceException {
                if (explode[0]) {
                    throw new IllegalStateException("http://user:secret@host/api");
                }
                return delegate.load(id);
            }
        };
        KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(), null,
                Collections.singletonList(new KnowledgeSourceRegistration(source, SourceScope.of("X"))));
        assertTrue(f.indexing.indexSource(source, SourceScope.of("X"), null).isComplete());
        explode[0] = true;

        String text = f.error(TOOL, "id", "memory:broken/X", "source_id", "broken");

        assertEquals("Laden des Dokuments 'memory:broken/X' fehlgeschlagen.", text);
        assertFalse(text.contains("secret"));
    }

    @Test
    public void longDocumentIsCutAtTheResponseLimitWithAMarker() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(
                KnowledgeToolSettings.defaults().withMaxResponseChars(KnowledgeToolSettings.MIN_RESPONSE_CHARS), null);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            text.append("Zeile ").append(i).append(" mit etwas Inhalt.\n");
        }
        f.wiki.add("Lang", "Langer Text", text.toString());
        f.indexing.indexResources(f.wiki, Collections.singletonList(f.wiki.idOf("Lang")), null);

        String result = f.ok(TOOL, "id", f.wiki.idOf("Lang").value());

        assertTrue(result, result.length() <= KnowledgeToolSettings.MIN_RESPONSE_CHARS);
        assertTrue(result, result.startsWith("Titel: Langer Text\n"));
        assertTrue(result, result.contains("Text:\nZeile 0 mit etwas Inhalt."));
        assertTrue(result, result.matches("(?s).*\n… \\[gekürzt: \\d+ Zeichen ausgelassen]$"));
    }

    @Test
    public void documentEmptiedSinceIndexingSaysSo() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        f.wiki.add("Leer", "Leere Seite", "Noch mit Inhalt.");
        f.indexing.indexResources(f.wiki, Collections.singletonList(f.wiki.idOf("Leer")), null);
        f.wiki.update("Leer", "   \n ");

        String text = f.ok(TOOL, "id", f.wiki.idOf("Leer").value());

        assertTrue(text, text.endsWith("\nText: (leer)"));
        assertTrue(text, text.contains("\nZeichen: "));
    }

    @Test
    public void blankPageIsNeverIndexedAndThereforeNotReadable() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        f.wiki.add("Leer", "Leere Seite", "   \n ");
        f.indexing.indexResources(f.wiki, Collections.singletonList(f.wiki.idOf("Leer")), null); // EMPTY

        assertEquals(notIndexed("memory:wiki/Leer"), f.error(TOOL, "id", f.wiki.idOf("Leer").value()));
    }

    @Test
    public void contributionDeclaresIdAsRequiredString() {
        McpToolContribution tool = new KnowledgeToolFixture().tool(TOOL);

        assertEquals("get_knowledge_document", tool.getName());
        assertTrue(tool.getDescription(), tool.getDescription().contains("indexiertes Dokument"));
        assertEquals(2, tool.getParameters().size());
        assertEquals("id", tool.getParameters().get(0).getName());
        assertTrue(tool.getParameters().get(0).isRequired());
        assertEquals(McpToolType.STRING, tool.getParameters().get(0).getType());
        assertEquals("source_id", tool.getParameters().get(1).getName());
        assertFalse(tool.getParameters().get(1).isRequired());
    }
}
