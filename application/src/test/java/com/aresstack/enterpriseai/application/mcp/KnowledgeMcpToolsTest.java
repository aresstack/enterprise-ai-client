package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class KnowledgeMcpToolsTest {

    @Test
    public void contributionsComeInFixedOrderWithValidNames() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();

        List<McpToolContribution> contributions = f.tools.contributions();

        assertEquals(3, contributions.size());
        assertEquals(KnowledgeMcpTools.SEARCH_KNOWLEDGE, contributions.get(0).getName());
        assertEquals(KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT, contributions.get(1).getName());
        assertEquals(KnowledgeMcpTools.REFRESH_KNOWLEDGE_SOURCE, contributions.get(2).getName());
        assertEquals(Arrays.asList("search_knowledge", "get_knowledge_document", "refresh_knowledge_source"),
                Arrays.asList(KnowledgeMcpTools.SEARCH_KNOWLEDGE, KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT,
                        KnowledgeMcpTools.REFRESH_KNOWLEDGE_SOURCE));
        assertSame(contributions.get(0).getHandler(), f.tools.searchKnowledge().getHandler());
        assertFalse(f.tools.isShutdown());
    }

    @Test
    public void nullSettingsMeanDefaults() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        KnowledgeMcpTools tools = new KnowledgeMcpTools(f.retrieval, f.documents, f.refresh, null);
        assertEquals(KnowledgeToolSettings.DEFAULT_MAX_RESPONSE_CHARS, tools.settings().maxResponseChars());
    }

    @Test
    public void missingUseCasesAreRejected() {
        KnowledgeToolFixture f = new KnowledgeToolFixture();
        try {
            new KnowledgeMcpTools(null, f.documents, f.refresh, null);
            throw new AssertionError("retrieval fehlt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("retrieval"));
        }
        try {
            new KnowledgeMcpTools(f.retrieval, null, f.refresh, null);
            throw new AssertionError("documents fehlt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("documents"));
        }
        try {
            new KnowledgeMcpTools(f.retrieval, f.documents, null, null);
            throw new AssertionError("refresh fehlt");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("refresh"));
        }
    }

    @Test
    public void searchAndDocumentStayAvailableAfterShutdown() {
        KnowledgeToolFixture f = new KnowledgeToolFixture().indexed();
        f.tools.shutdown();

        assertTrue(f.ok(KnowledgeMcpTools.SEARCH_KNOWLEDGE, "query", "openjdk").contains("[1] "));
        assertTrue(f.ok(KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT, "id", "memory:wiki/Java").startsWith("Titel: "));
        assertTrue(f.error(KnowledgeMcpTools.REFRESH_KNOWLEDGE_SOURCE, "source_id", "wiki").contains("beendet"));
    }
}
