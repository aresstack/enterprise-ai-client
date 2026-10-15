package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingListener;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeToolSettings;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.pinakes.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.proasteion.katagogion.McpEndpointDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.McpEndpointHandle;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCallException;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolClient;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolClientFactory;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpToolClientFactory;
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
 * 127.0.0.1, Token im Pfad) → knowledge_search/knowledge_read → Use Cases (Live-Suche, die Treffer indexiert,
 * Retrieval, Dokument laden) → Lucene-Index. Solon ist prozessglobal; diese Klasse läuft in eigener JVM ({@code forkEvery 1}).
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
        LoadKnowledgeDocumentUseCase documents = new LoadKnowledgeDocumentUseCase(catalog);
        tools = new KnowledgeMcpTools(retrieval, documents, new SearchDrivenIndexingUseCase(indexing),
                KnowledgeToolSettings.defaults());
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
        assertEquals(listed.toString(), new java.util.HashSet<String>(java.util.Arrays.asList(
                KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_TREE, KnowledgeMcpTools.KNOWLEDGE_READ, KnowledgeMcpTools.KNOWLEDGE_READ_MANY,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, KnowledgeMcpTools.KNOWLEDGE_RESOLVE_URL)), listed.keySet());

        String search = client.callTool(KnowledgeMcpTools.KNOWLEDGE_SEARCH, args("query", QUESTION, "limit", 1));
        assertTrue(search, search.contains("[1] " + EXPECTED_TITLE));
        assertTrue(search, search.contains("id: " + source.idOf("kuendigung").value()));
        assertTrue(search, search.contains("source: " + SOURCE_ID));
        assertTrue(search, search.contains(EXPECTED_PHRASE));

        String document = client.callTool(KnowledgeMcpTools.KNOWLEDGE_READ, KnowledgeMcpTools.KNOWLEDGE_READ_MANY,
                args("id", source.idOf("kuendigung").value()));
        assertTrue(document, document.contains("title: " + EXPECTED_TITLE));
        assertTrue(document, document.contains("source: " + SOURCE_ID));
        assertTrue(document, document.contains(EXPECTED_PHRASE));
    }

    @Test
    public void readingAnUnindexedPageIndexesIt() throws Exception {
        source.add("intern", "Internes", "Nur intern, bisher nicht im Index.");
        String document = client.callTool(KnowledgeMcpTools.KNOWLEDGE_READ, args("id", source.idOf("intern").value()));
        assertTrue(document, document.contains("bisher nicht im Index"));
        assertTrue(index.resourceIds(indexing.space(), source.sourceId()).contains(source.idOf("intern")));
        try {
            client.callTool(KnowledgeMcpTools.KNOWLEDGE_READ, args("id", ""));
            fail("leere id ist ein Werkzeugfehler");
        } catch (McpToolCallException expected) {
            assertFalse("ein Werkzeugfehler, kein Ausfall des Endpoints", expected.isEndpointUnavailable());
            assertTrue(expected.getMessage(), expected.getMessage().contains("example: id="));
        }
    }

    @Test
    public void searchPicksUpAChangedPage() throws Exception {
        source.update("kuendigung", "Die Kündigungsfrist beträgt neuerdings sechs Wochen zum Monatsende. "
                + "Eine Kündigung muss schriftlich erfolgen.");

        // Die Live-Suche findet die geänderte Seite; das Vorladen holt sie im Hintergrund, der Inhalts-Hash weicht ab:
        // neu zerlegt und eingebettet. knowledge_read liefert danach die neuen Passagen aus dem Index.
        String search = client.callTool(KnowledgeMcpTools.KNOWLEDGE_SEARCH, args("query", "Kündigung", "limit", 3));
        assertTrue(search, search.contains("id: " + source.idOf("kuendigung").value()));
        String document = "";
        for (long deadline = System.currentTimeMillis() + 10_000L; System.currentTimeMillis() < deadline; ) {
            document = client.callTool(KnowledgeMcpTools.KNOWLEDGE_READ, args("id", source.idOf("kuendigung").value()));
            if (document.contains("sechs Wochen zum Monatsende")) {
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(document, document.contains("sechs Wochen zum Monatsende"));
        assertFalse(document, document.contains(EXPECTED_PHRASE));
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
