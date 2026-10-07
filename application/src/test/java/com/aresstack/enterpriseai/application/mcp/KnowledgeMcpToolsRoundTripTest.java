package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.testkit.InProcessMcpToolClientFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * AP20-Abnahme: MCP-Client → Registry → Wissenswerkzeug → Application-Use-Case → Fake-Index/Fake-Quelle und
 * zurück, über die In-Process-Registry und den In-Process-Client aus den Fixtures von mcp-runtime-api.
 */
public class KnowledgeMcpToolsRoundTripTest {

    private KnowledgeToolFixture fixture;
    private InProcessMcpServerRegistry registry;
    private McpEndpointHandle handle;
    private McpToolClient client;

    @Before
    public void registerToolsAndConnect() {
        fixture = new KnowledgeToolFixture().indexed();
        registry = new InProcessMcpServerRegistry();
        handle = registry.registerEndpoint(new McpEndpointDefinition("agent", "Agent-Endpoint"));
        registry.updateTools(handle, fixture.tools.contributions());
        client = new InProcessMcpToolClientFactory(registry).connect(registry.endpointUrl(handle), null);
    }

    @After
    public void shutdown() {
        client.close();
        registry.shutdown();
        fixture.tools.shutdown();
    }

    @Test
    public void toolsListShowsTheThreeKnowledgeToolsWithDescriptions() throws McpToolCallException {
        Map<String, String> tools = client.listTools();

        assertEquals(Arrays.asList("search_knowledge", "get_knowledge_document", "refresh_knowledge_source"),
                new java.util.ArrayList<String>(tools.keySet()));
        for (Map.Entry<String, String> tool : tools.entrySet()) {
            assertTrue(tool.getKey(), McpToolContribution.isValidToolName(tool.getKey()));
            assertFalse(tool.getKey() + " ohne Beschreibung", tool.getValue().trim().isEmpty());
        }
        assertEquals(tools, registry.toolCatalog(handle));
    }

    @Test
    public void searchThenReadThenRefreshThroughTheClient() throws McpToolCallException {
        String hits = client.callTool("search_knowledge", KnowledgeToolFixture.arguments(
                "query", "openjdk installieren", "max_results", "1"));
        assertTrue(hits, hits.contains("[1] Java installieren – Linux\n"));
        assertTrue(hits, hits.contains("Id: memory:wiki/Java\n"));

        String document = client.callTool("get_knowledge_document", KnowledgeToolFixture.arguments(
                "id", "memory:wiki/Java"));
        assertTrue(document, document.startsWith("Titel: Java installieren\n"));
        assertTrue(document, document.endsWith("Java installiert man mit apt install openjdk-8-jdk."));

        fixture.wiki.update("Java", "Java installiert man jetzt mit sdkman.");
        String refresh = client.callTool("refresh_knowledge_source", KnowledgeToolFixture.arguments(
                "source_id", "wiki"));
        assertTrue(refresh, refresh.contains("Status: vollständig\n"));
        assertTrue(refresh, refresh.contains("Indexiert: 3 (Chunks: 3)\n"));

        String updated = client.callTool("search_knowledge", KnowledgeToolFixture.arguments(
                "query", "sdkman", "max_results", 1, "source_ids", "wiki"));
        assertTrue(updated, updated.contains("Text:\nJava installiert man jetzt mit sdkman."));
        assertFalse(updated, updated.contains("openjdk"));
    }

    @Test
    public void toolErrorsArriveAsToolFailuresNotAsUnavailableEndpoint() {
        try {
            client.callTool("get_knowledge_document", KnowledgeToolFixture.arguments("id", "memory:wiki/Nope"));
            fail("Fehler erwartet");
        } catch (McpToolCallException e) {
            assertFalse(e.isEndpointUnavailable());
            assertEquals("Dokument 'memory:wiki/Nope' wurde nicht gefunden.", e.getMessage());
        }
        try {
            client.callTool("search_knowledge", KnowledgeToolFixture.arguments());
            fail("Fehler erwartet");
        } catch (McpToolCallException e) {
            assertFalse(e.isEndpointUnavailable());
            assertEquals("Parameter 'query' fehlt oder ist leer.", e.getMessage());
        }
    }

    @Test
    public void unregisteredEndpointMakesTheToolsUnreachable() {
        registry.unregisterEndpoint(handle);
        try {
            client.callTool("search_knowledge", KnowledgeToolFixture.arguments("query", "openjdk"));
            fail("Endpoint abgemeldet");
        } catch (McpToolCallException e) {
            assertTrue(e.isEndpointUnavailable());
        }
    }
}
