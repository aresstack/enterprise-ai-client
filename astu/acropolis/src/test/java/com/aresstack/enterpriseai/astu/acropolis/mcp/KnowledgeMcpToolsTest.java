package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Drei kleine Werkzeuge; die Suche füllt den Index, Unverändertes wird nicht neu verarbeitet. */
public class KnowledgeMcpToolsTest {

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Drucker", "Drucker einrichten", "Den Drucker richtet man über CUPS ein.")
            .add("Kaffee", "Kaffeemaschine", "Die Kaffeemaschine wird monatlich entkalkt.");
    private final KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
            new KnowledgeSourceRegistration(wiki, SourceScope.of("Drucker", "Kaffee")));
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final SearchDrivenIndexingUseCase live = new SearchDrivenIndexingUseCase(indexing);
    private final KnowledgeMcpTools tools = new KnowledgeMcpTools(
            new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null),
            new LoadKnowledgeDocumentUseCase(catalog), live, null);

    private McpToolResult call(McpToolContribution tool, String name, Object value) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        if (name != null) {
            arguments.put(name, value);
        }
        return tool.getHandler().invoke(new McpToolCall(tool.getName(), arguments));
    }

    @Test
    public void threeSmallToolsWithExamples() {
        assertEquals(6, tools.contributions().size());
        assertEquals("knowledge_search", tools.knowledgeSearch().getName());
        assertEquals("knowledge_tree", tools.knowledgeTree().getName());
        assertEquals("knowledge_read", tools.knowledgeRead().getName());
        assertEquals("knowledge_read_many", tools.knowledgeReadMany().getName());
        assertEquals("knowledge_attachments", tools.knowledgeAttachments().getName());
        assertEquals("knowledge_resolve_url", tools.knowledgeResolveUrl().getName());
        for (McpToolContribution tool : tools.contributions()) {
            assertTrue(tool.getName(), tool.getDescription().contains("Example:"));
        }
        McpToolResult missing = call(tools.knowledgeSearch(), null, null);
        assertTrue(missing.isError());
        assertTrue(missing.getText(), missing.getText().contains("example: query="));
        assertTrue(call(tools.knowledgeRead(), null, null).getText().contains("example: id="));
    }

    @Test
    public void attachmentsOfASourceWithoutFilesAreAnError() {
        McpToolResult result = call(tools.knowledgeAttachments(), "id", "wiki:Drucker");
        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("No knowledge source lists attachments"));
        assertTrue(call(tools.knowledgeAttachments(), null, null).getText().contains("example: id="));
    }

    @Test
    public void searchIndexesHitsAndSkipsUnchangedPages() {
        String text = call(tools.knowledgeSearch(), "query", "Drucker").getText();
        assertTrue(text, text.contains("Drucker einrichten"));
        assertTrue(text, text.contains(wiki.idOf("Drucker").value()));
        assertTrue("Treffer wird im Hintergrund vorgeladen und indexiert", indexedSoon("Drucker"));
        assertFalse("nur Treffer, kein Vollindex",
                index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).contains(wiki.idOf("Kaffee")));

        SearchDrivenIndexingUseCase.Result again = live.searchAndIndex(catalog, "Drucker", 4, 4);
        assertEquals("gleicher Inhalts-Hash: nicht neu zerlegt", 1, again.count(IndexingStatus.UNCHANGED));

        wiki.update("Drucker", "Den Drucker richtet man jetzt über IPP ein.");
        assertEquals(1, live.searchAndIndex(catalog, "Drucker", 4, 4).count(IndexingStatus.INDEXED));
    }

    @Test
    public void indexSearchAnswersFromTheIndexWithoutAskingTheSource() throws Exception {
        assertEquals("knowledge_index_search", tools.knowledgeIndexSearch().getName());
        assertFalse("agent only, not among the common tools",
                tools.contributions().contains(tools.knowledgeIndexSearch()));
        String empty = call(tools.knowledgeIndexSearch(), "query", "Drucker").getText();
        assertTrue(empty, empty.contains("No hits"));
        indexing.indexDocumentIfChanged(wiki.load(wiki.idOf("Drucker")));
        int before = wiki.calls().size();

        String text = call(tools.knowledgeIndexSearch(), "query", "Wie richte ich den Drucker ein?").getText();

        assertTrue(text, text.contains("Drucker einrichten"));
        assertTrue(text, text.contains("id: " + wiki.idOf("Drucker").value()));
        assertTrue(text, text.contains("source: wiki"));
        for (String sourceCall : wiki.calls().subList(before, wiki.calls().size())) {
            assertFalse("no live search: " + sourceCall, sourceCall.startsWith("search:"));
        }
    }

    @Test
    public void projectFilesOnlyForTheProjectsOwnScopedTools() {
        com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId planId =
                com.aresstack.enterpriseai.astu.knowledge.ProjectKnowledge.resource("pA", "att-0000000a");
        indexing.indexDocumentIfChanged(com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument.of(
                com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource.builder(planId,
                        com.aresstack.enterpriseai.astu.knowledge.ProjectKnowledge.source("pA"))
                        .title("plan.txt").revision(com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision.version("1"))
                        .build(), "Der Zielwert der Projektplanung ist 42."));
        KnowledgeMcpTools inA = tools.scopedTo(
                com.aresstack.enterpriseai.astu.knowledge.ProjectKnowledge.source("pA"));
        KnowledgeMcpTools inB = tools.scopedTo(
                com.aresstack.enterpriseai.astu.knowledge.ProjectKnowledge.source("pB"));

        String found = call(inA.knowledgeIndexSearch(), "query", "Zielwert Projektplanung").getText();
        assertTrue(found, found.contains(planId.value()));
        McpToolResult read = call(inA.knowledgeRead(), "id", planId.value());
        assertFalse(read.getText(), read.isError());
        assertTrue(read.getText(), read.getText().contains("Zielwert"));
        assertEquals("chatproject://pA", read.contextResources().get(0).sourceId());

        // the general tools (agent/MCP, chats without project) and another project's chats never see it, not even
        // with the exact id a previous turn returned
        for (KnowledgeMcpTools other : new KnowledgeMcpTools[]{tools, inB}) {
            String search = call(other.knowledgeIndexSearch(), "query", "Zielwert Projektplanung").getText();
            assertFalse(search, search.contains(planId.value()));
            McpToolResult denied = call(other.knowledgeRead(), "id", planId.value());
            assertTrue(denied.getText(), denied.isError());
            assertFalse(denied.getText(), denied.getText().contains("Zielwert"));
            String many = call(other.knowledgeReadMany(), "ids", java.util.Collections.singletonList(planId.value()))
                    .getText();
            assertFalse(many, many.contains("Zielwert"));
        }
        inA.shutdown();
        inB.shutdown();
    }

    @Test
    public void readLoadsAndIndexesOnDemand() {
        McpToolResult read = call(tools.knowledgeRead(), "id", wiki.idOf("Kaffee").value());
        assertFalse(read.getText(), read.isError());
        assertTrue(read.getText().contains("monatlich entkalkt"));
        assertTrue(indexedSoon("Kaffee"));
    }

    @Test
    public void readResolvesFromIndexAndByPassageId() {
        String id = wiki.idOf("Kaffee").value();
        String first = call(tools.knowledgeRead(), "id", id).getText();
        assertTrue(first, first.contains("[passage " + id + "@"));
        assertTrue(first, first.contains("#p0@"));
        String again = call(tools.knowledgeRead(), "id", id).getText();
        assertTrue("zweites Lesen ohne Quelle: " + again, again.contains("from: index"));
        int start = again.indexOf("[passage ") + "[passage ".length();
        String passage = again.substring(start, again.indexOf(']', start));
        String one = call(tools.knowledgeRead(), "id", passage).getText();
        assertTrue(one, one.contains("passage: " + passage));
    }

    @Test
    public void readResolvesBookmarkUri() {
        String bookmark = KnowledgeBookmarks.of(wiki.idOf("Kaffee")).toString();
        assertTrue(bookmark, bookmark.contains("://"));
        McpToolResult read = call(tools.knowledgeRead(), "id", bookmark);
        assertFalse(read.getText(), read.isError());
        assertTrue(read.getText(), read.getText().contains("id: " + wiki.idOf("Kaffee").value()));
        assertTrue(read.getText(), read.getText().contains("monatlich entkalkt"));
    }

    /** Vorladen und Indexieren laufen im Hintergrund; höchstens 10 s warten. */
    private boolean indexedSoon(String page) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            if (index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).contains(wiki.idOf(page))) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
