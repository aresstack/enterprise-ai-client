package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.rag.RetrievalSettings;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SearchKnowledgeToolTest {

    private static final String TOOL = KnowledgeMcpTools.SEARCH_KNOWLEDGE;

    @Test
    public void findsIndexedChunkAndReportsTitleIdsSourceRevisionScoresAndText() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();

        String text = f.ok(TOOL, "query", "openjdk installieren", "max_results", 1);

        assertTrue(text, text.startsWith("Treffer: 1 von "));
        assertTrue(text, text.contains("Anfrage: openjdk installieren\n"));
        assertTrue(text, text.contains("[1] Java installieren – Linux\n"));
        assertTrue(text, text.contains("Id: " + f.javaId().value() + "\n"));
        assertTrue(text, text.contains("Chunk: " + f.javaId().value() + "#chunk-0\n"));
        assertTrue(text, text.contains("Quelle: wiki\n"));
        assertTrue(text, text.contains("Stand: 2023-11-14T22:13:21Z, Version 1\n"));
        assertTrue(text, text.contains("Score: 0."));
        assertTrue(text, text.contains("Volltext #1: "));
        assertTrue(text, text.contains("Text:\nJava installiert man mit apt install openjdk-8-jdk."));
        assertFalse("kein Hinweis ohne Ausfall", text.contains("Hinweis"));
    }

    @Test
    public void maxResultsLimitsTheHitsAndIsCappedByTheRetrievalSettings() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(),
                RetrievalSettings.builder().maxResults(2).build()).indexed();

        String two = f.ok(TOOL, "query", "Drucker", "max_results", 100);
        String one = f.ok(TOOL, "query", "Drucker", "max_results", "1");

        assertTrue(two, two.startsWith("Treffer: 2\n"));
        assertTrue(two, two.contains("\n[2] "));
        assertTrue(one, one.startsWith("Treffer: 1 von 2\n"));
        assertFalse(one, one.contains("\n[2] "));
    }

    @Test
    public void defaultMaxResultsComesFromTheSettings() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(
                KnowledgeToolSettings.defaults().withDefaultMaxResults(1), null).indexed();

        String text = f.ok(TOOL, "query", "Drucker");

        assertTrue(text, text.startsWith("Treffer: 1 von "));
    }

    @Test
    public void sourceIdsRestrictTheSearchToTheNamedSources() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();

        String docsOnly = f.ok(TOOL, "query", "Drucker", "source_ids", "docs");
        String both = f.ok(TOOL, "query", "Drucker", "source_ids", "docs, wiki");

        assertTrue(docsOnly, docsOnly.contains("Quellen: docs\n"));
        assertTrue(docsOnly, docsOnly.contains("Quelle: docs\n"));
        assertFalse(docsOnly, docsOnly.contains("Quelle: wiki\n"));
        assertTrue(both, both.contains("Quellen: docs, wiki\n"));
        assertTrue(both, both.contains("Quelle: wiki\n"));
    }

    @Test
    public void unknownOrInvalidSourceIdIsAnErrorNamingTheConfiguredSources() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();

        String unknown = f.error(TOOL, "query", "Drucker", "source_ids", "sharepoint");
        String invalid = f.error(TOOL, "query", "Drucker", "source_ids", "wiki,/etc/passwd");

        assertEquals("Unbekannte Quelle 'sharepoint'. Konfigurierte Quellen: wiki, docs.", unknown);
        assertTrue(invalid, invalid.contains("ungültige Quell-ID"));
        assertTrue(invalid, invalid.contains("wiki, docs"));
    }

    @Test
    public void missingQueryOrNonPositiveMaxResultsIsAnError() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();

        assertEquals("Parameter 'query' fehlt oder ist leer.", f.error(TOOL));
        assertEquals("Parameter 'query' fehlt oder ist leer.", f.error(TOOL, "query", "   "));
        assertEquals("Parameter 'max_results' muss mindestens 1 sein.", f.error(TOOL, "query", "x", "max_results", 0));
    }

    @Test
    public void noHitsIsAnOkResultSayingSo() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(KnowledgeToolSettings.defaults(),
                RetrievalSettings.builder().semanticEnabled(false).build()).indexed();

        String text = f.ok(TOOL, "query", "Quantenphysik");

        assertEquals("Treffer: 0\nAnfrage: Quantenphysik\nKeine Treffer.", text);
    }

    @Test
    public void failedSearchPathIsReportedAsHintAndTheOtherPathStillAnswers() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.index.failSemantic = true;

        String text = f.ok(TOOL, "query", "openjdk", "max_results", 1);

        assertTrue(text, text.contains("Hinweis (SEMANTIC): nur ein Suchpfad hat geliefert – "));
        assertTrue(text, text.contains("Vektorindex nicht lesbar"));
        assertTrue(text, text.contains("[1] Java installieren"));
        assertFalse(text, text.contains("Semantik #"));
    }

    @Test
    public void allPathsFailingIsAnErrorWithoutStackTrace() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.index.failKeyword = true;
        f.index.failSemantic = true;

        String text = f.error(TOOL, "query", "openjdk");

        assertTrue(text, text.startsWith("Wissenssuche nicht möglich, alle Suchpfade sind ausgefallen."));
        assertTrue(text, text.contains("Volltextindex beschädigt"));
        assertFalse(text, text.contains("Exception"));
        assertFalse(text, text.contains("\n\tat "));
    }

    @Test
    public void hitsBeyondTheResponseLimitAreDroppedAndCounted() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(
                KnowledgeToolSettings.defaults().withMaxResponseChars(KnowledgeToolSettings.MIN_RESPONSE_CHARS),
                RetrievalSettings.builder().maxResults(20).build()).indexed();

        String text = f.ok(TOOL, "query", "Drucker Kaffee Java Urlaub", "max_results", 20);

        assertTrue(text, text.length() <= KnowledgeToolSettings.MIN_RESPONSE_CHARS);
        assertTrue(text, text.contains("weitere Treffer wegen der Antwortgröße ausgelassen."));
        assertTrue("mindestens der erste Treffer passt", text.contains("\n[1] "));
    }

    @Test
    public void snippetIsCutToTheConfiguredLength() {
        KnowledgeToolFixture f = new KnowledgeToolFixture(
                KnowledgeToolSettings.defaults().withSnippetChars(KnowledgeToolSettings.MIN_SNIPPET_CHARS), null);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            text.append("Wort").append(i).append(' ');
        }
        f.wiki.add("Lang", "Langer Text", text.toString());
        f.indexing.indexResources(f.wiki, java.util.Collections.singleton(f.wiki.idOf("Lang")), null);

        String result = f.ok(TOOL, "query", "Wort7", "max_results", 1, "source_ids", "wiki");

        int at = result.indexOf("Text:\n") + "Text:\n".length();
        String snippet = result.substring(at).trim();
        assertTrue(snippet, snippet.endsWith("…"));
        assertTrue(snippet, snippet.length() <= KnowledgeToolSettings.MIN_SNIPPET_CHARS);
    }

    @Test
    public void contributionDeclaresNameDescriptionAndFlatParameters() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        McpToolContribution tool = f.tool(TOOL);

        assertEquals("search_knowledge", tool.getName());
        assertTrue(McpToolContribution.isValidToolName(tool.getName()));
        assertTrue(tool.getDescription(), tool.getDescription().contains("get_knowledge_document"));
        assertEquals(3, tool.getParameters().size());
        McpToolParameter query = tool.getParameters().get(0);
        assertEquals("query", query.getName());
        assertTrue(query.isRequired());
        assertEquals(McpToolType.STRING, query.getType());
        McpToolParameter max = tool.getParameters().get(1);
        assertEquals("max_results", max.getName());
        assertFalse(max.isRequired());
        assertEquals(McpToolType.INTEGER, max.getType());
        McpToolParameter sources = tool.getParameters().get(2);
        assertEquals("source_ids", sources.getName());
        assertFalse(sources.isRequired());
        assertTrue(sources.getDescription(), sources.getDescription().contains("wiki, docs"));
    }
}
