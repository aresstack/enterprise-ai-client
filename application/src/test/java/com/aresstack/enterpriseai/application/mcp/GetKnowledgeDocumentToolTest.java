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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GetKnowledgeDocumentToolTest {

    private static final String TOOL = KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT;

    @Test
    public void loadsTheFullDocumentWithHeaderFromItsSource() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

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
        assertEquals(Collections.singletonList("load:Java"), f.wiki.calls());
    }

    @Test
    public void withoutSourceIdTheSourceThatKnowsTheIdAnswersEvenIfItIsNotTheFirst() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        String text = f.ok(TOOL, "id", f.docs.idOf("Urlaub").value());

        assertTrue(text, text.startsWith("Titel: Urlaubsantrag\nId: memory:docs/Urlaub\nQuelle: docs\n"));
        assertFalse(text, text.contains("Übergeordnet: "));
        assertEquals("die erste Quelle lehnt die fremde ID ab, ohne zu laden", Collections.emptyList(), f.wiki.calls());
    }

    @Test
    public void parentIsShownWhenTheResourceHasOne() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        f.wiki.addChild("Java", "Windows", "Java unter Windows", "Installer herunterladen.");

        String text = f.ok(TOOL, "id", f.wiki.idOf("Windows").value());

        assertTrue(text, text.contains("Übergeordnet: memory:wiki/Java\n"));
    }

    @Test
    public void explicitSourceIdLoadsFromThatSourceOnly() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        String ok = f.ok(TOOL, "id", f.javaId().value(), "source_id", "wiki");
        String foreign = f.error(TOOL, "id", f.javaId().value(), "source_id", "docs");

        assertTrue(ok, ok.startsWith("Titel: Java installieren\n"));
        assertEquals("Quelle 'docs' kennt das Dokument 'memory:wiki/Java' nicht.", foreign);
    }

    @Test
    public void unknownDocumentAndUnknownSourceAreShortErrors() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        assertEquals("Dokument 'memory:wiki/Nope' wurde nicht gefunden.", f.error(TOOL, "id", "memory:wiki/Nope"));
        assertEquals("Dokument 'memory:wiki/Nope' wurde in Quelle 'wiki' nicht gefunden.",
                f.error(TOOL, "id", "memory:wiki/Nope", "source_id", "wiki"));
        assertEquals("Keine konfigurierte Quelle kennt das Dokument 'confluence:ABC/1' (konfiguriert: wiki, docs).",
                f.error(TOOL, "id", "confluence:ABC/1"));
        assertEquals("Unbekannte Quelle 'sharepoint'. Konfigurierte Quellen: wiki, docs.",
                f.error(TOOL, "id", "memory:wiki/Java", "source_id", "sharepoint"));
        assertEquals("Ungültige Quell-ID. Konfigurierte Quellen: wiki, docs.",
                f.error(TOOL, "id", "memory:wiki/Java", "source_id", "../x"));
    }

    @Test
    public void missingOrMalformedIdIsAnError() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        assertEquals("Parameter 'id' fehlt oder ist leer.", f.error(TOOL));
        assertEquals("Ungültige Dokument-ID 'Java': erwartet <schema>:<id>.", f.error(TOOL, "id", "Java"));
    }

    @Test
    public void sourceFailuresAreMappedToShortMessagesWithoutDetails() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
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
    public void unexpectedRuntimeFailureIsAGenericErrorWithoutTheCauseText() {
        InMemoryKnowledgeSource broken = new InMemoryKnowledgeSource("broken").add("X", "X", "x");
        KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(), null,
                Collections.singletonList(new KnowledgeSourceRegistration(
                        new KnowledgeToolFixture.DelegatingSource(broken) {
                            @Override
                            public KnowledgeDocument load(KnowledgeResourceId id) {
                                throw new IllegalStateException("http://user:secret@host/api");
                            }
                        }, SourceScope.of("X"))));

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

        String result = f.ok(TOOL, "id", f.wiki.idOf("Lang").value());

        assertTrue(result, result.length() <= KnowledgeToolSettings.MIN_RESPONSE_CHARS);
        assertTrue(result, result.startsWith("Titel: Langer Text\n"));
        assertTrue(result, result.contains("Text:\nZeile 0 mit etwas Inhalt."));
        assertTrue(result, result.matches("(?s).*\n… \\[gekürzt: \\d+ Zeichen ausgelassen]$"));
    }

    @Test
    public void emptyDocumentSaysSo() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        f.wiki.add("Leer", "Leere Seite", "   \n ");

        String text = f.ok(TOOL, "id", f.wiki.idOf("Leer").value());

        assertTrue(text, text.endsWith("\nText: (leer)"));
        assertTrue(text, text.contains("\nZeichen: "));
    }

    @Test
    public void contributionDeclaresIdAsRequiredString() {
        McpToolContribution tool = new KnowledgeToolFixture().tool(TOOL);

        assertEquals("get_knowledge_document", tool.getName());
        assertEquals(2, tool.getParameters().size());
        assertEquals("id", tool.getParameters().get(0).getName());
        assertTrue(tool.getParameters().get(0).isRequired());
        assertEquals(McpToolType.STRING, tool.getParameters().get(0).getType());
        assertEquals("source_id", tool.getParameters().get(1).getName());
        assertFalse(tool.getParameters().get(1).isRequired());
    }
}
