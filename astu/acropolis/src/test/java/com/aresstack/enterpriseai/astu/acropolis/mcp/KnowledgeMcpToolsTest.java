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

/** Zwei kleine Werkzeuge; die Suche füllt den Index, Unverändertes wird nicht neu verarbeitet. */
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
    public void onlyTwoToolsWithExamples() {
        assertEquals(2, tools.contributions().size());
        assertEquals("knowledge_search", tools.knowledgeSearch().getName());
        assertEquals("knowledge_read", tools.knowledgeRead().getName());
        for (McpToolContribution tool : tools.contributions()) {
            assertTrue(tool.getName(), tool.getDescription().contains("Example:"));
        }
        McpToolResult missing = call(tools.knowledgeSearch(), null, null);
        assertTrue(missing.isError());
        assertTrue(missing.getText(), missing.getText().contains("example: query="));
        assertTrue(call(tools.knowledgeRead(), null, null).getText().contains("example: id="));
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
        assertTrue(first, first.contains("[passage " + id + "#p0@"));
        String again = call(tools.knowledgeRead(), "id", id).getText();
        assertTrue("zweites Lesen ohne Quelle: " + again, again.contains("from: index"));
        int start = again.indexOf("[passage ") + "[passage ".length();
        String passage = again.substring(start, again.indexOf(']', start));
        String one = call(tools.knowledgeRead(), "id", passage).getText();
        assertTrue(one, one.contains("passage: " + passage));
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
