package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.RefreshKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.application.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.application.mcp.KnowledgeToolSettings;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolClientFactory;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpToolClientFactory;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_PHRASE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_TITLE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.QUESTION;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.SOURCE_ID;
import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecret;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Slice F – MCP: MCP-Client (Port {@code McpToolClientFactory}, Adapter Solon) → MCP-Runtime (Solon-Server auf
 * 127.0.0.1, Token im Pfad) → Wissenswerkzeuge aus AP20 → Use Cases (Retrieval, Dokument laden, Quelle
 * auffrischen) → Lucene-Index. Solon ist prozessglobal; diese Klasse läuft in eigener JVM ({@code forkEvery 1}).
 */
public class SliceFMcpTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private SolonMcpServerRuntime runtime;
    private LuceneKnowledgeIndex index;
    private InMemoryKnowledgeSource source;
    private IndexKnowledgeUseCase indexing;
    private KnowledgeMcpTools tools;
    private McpEndpointHandle handle;
    private String url;
    private SolonMcpToolClientFactory clients;
    private McpToolClient client;

    @Before
    public void setUp() throws Exception {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        EmbeddingModelIdentity space = embeddings.modelIdentity();
        index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());
        source = SampleKnowledge.handbuch();
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
                new KnowledgeSourceRegistration(source, SampleKnowledge.scope()));
        indexing = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        RetrieveKnowledgeUseCase retrieval = new RetrieveKnowledgeUseCase(index, embeddings, space, null);
        LoadKnowledgeDocumentUseCase documents = new LoadKnowledgeDocumentUseCase(catalog, index, space);
        RefreshKnowledgeSourceUseCase refresh = new RefreshKnowledgeSourceUseCase(indexing, catalog);
        tools = new KnowledgeMcpTools(retrieval, documents, refresh, KnowledgeToolSettings.defaults());
        indexing.indexSource(source, SampleKnowledge.scope(), IndexingListener.none());

        runtime = new SolonMcpServerRuntime();
        handle = runtime.registerEndpoint(new McpEndpointDefinition("knowledge", "Wissenswerkzeuge"));
        runtime.updateTools(handle, tools.contributions());
        url = runtime.endpointUrl(handle);
        clients = new SolonMcpToolClientFactory(Duration.ofSeconds(15), Duration.ofSeconds(30));
        client = clients.connect(url, McpToolClientFactory.STREAMABLE_HTTP);
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.close();
        }
        if (tools != null) {
            tools.shutdown();
        }
        if (runtime != null) {
            runtime.shutdown();
        }
        if (index != null) {
            index.close();
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    private static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    @Test
    public void knowledgeToolsAreListedAndAnswerFromTheIndexedCorpus() throws Exception {
        assertTrue(url, url.startsWith("http://127.0.0.1:"));
        assertTrue(url, url.contains(handle.getToken()));

        Map<String, String> listed = client.listTools();
        assertTrue(listed.toString(), listed.keySet().containsAll(java.util.Arrays.asList(
                KnowledgeMcpTools.SEARCH_KNOWLEDGE, KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT,
                KnowledgeMcpTools.REFRESH_KNOWLEDGE_SOURCE)));

        String search = client.callTool(KnowledgeMcpTools.SEARCH_KNOWLEDGE, args("query", QUESTION, "max_results", 1));
        assertTrue(search, search.startsWith("Treffer: 1"));
        assertTrue(search, search.contains("[1] " + EXPECTED_TITLE));
        assertTrue(search, search.contains("Id: " + source.idOf("kuendigung").value()));
        assertTrue(search, search.contains("Quelle: " + SOURCE_ID));
        assertTrue(search, search.contains(EXPECTED_PHRASE));

        String document = client.callTool(KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT,
                args("id", source.idOf("kuendigung").value()));
        assertTrue(document, document.contains("Titel: " + EXPECTED_TITLE));
        assertTrue(document, document.contains("Quelle: " + SOURCE_ID));
        assertTrue(document, document.contains(EXPECTED_PHRASE));
    }

    @Test
    public void onlyIndexedDocumentsAreReadable() throws Exception {
        source.add("intern", "Internes", "Nur intern, nicht im Umfang der Quelle.");
        try {
            client.callTool(KnowledgeMcpTools.GET_KNOWLEDGE_DOCUMENT, args("id", source.idOf("intern").value()));
            fail("nicht indexierte Dokumente dürfen nicht lesbar sein (Index führt, AP20)");
        } catch (McpToolCallException expected) {
            assertFalse("ein Werkzeugfehler, kein Ausfall des Endpoints", expected.isEndpointUnavailable());
            assertTrue(expected.getMessage(), expected.getMessage().contains("nicht in der Wissensbasis indexiert"));
        }
    }

    @Test
    public void refreshPicksUpAChangedPageAndSearchSeesIt() throws Exception {
        source.update("kuendigung", "Die Kündigungsfrist beträgt neuerdings sechs Wochen zum Monatsende. "
                + "Eine Kündigung muss schriftlich erfolgen.");

        String refresh = client.callTool(KnowledgeMcpTools.REFRESH_KNOWLEDGE_SOURCE, args("source_id", SOURCE_ID));
        assertTrue(refresh, refresh.contains("Quelle: " + SOURCE_ID));
        assertTrue(refresh, refresh.contains("Indexiert: 1"));
        assertTrue(refresh, refresh.contains("Unverändert: 2"));

        // Alle drei Chunks sind Treffer (der Semantikpfad liefert die nächsten Nachbarn der deterministischen
        // Vektoren); entscheidend ist, dass der Index den neuen Text trägt und den alten nicht mehr.
        String search = client.callTool(KnowledgeMcpTools.SEARCH_KNOWLEDGE,
                args("query", QUESTION, "max_results", 3, "source_ids", SOURCE_ID));
        assertTrue(search, search.contains("Id: " + source.idOf("kuendigung").value()));
        assertTrue(search, search.contains("sechs Wochen zum Monatsende"));
        assertFalse(search, search.contains(EXPECTED_PHRASE));
    }

    @Test
    public void unregisteredEndpointRejectsCallsWithoutRevealingTheToken() throws Exception {
        runtime.unregisterEndpoint(handle);
        McpToolClient stale = clients.connect(url, McpToolClientFactory.STREAMABLE_HTTP);
        try {
            stale.listTools();
            fail("nach dem Abmelden des Endpoints darf der Token nichts mehr öffnen");
        } catch (McpToolCallException expected) {
            assertTrue(expected.isEndpointUnavailable());
            assertNoSecret(expected.getMessage(), handle.getToken());
        } finally {
            stale.close();
        }
    }
}
