package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.rag.RetrievalResult;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.keepassrpc.FakeKeePassRpcServer;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcConfig;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.confluence.ConfluenceConfig;
import com.aresstack.enterpriseai.source.confluence.ConfluenceKnowledgeSource;
import com.aresstack.enterpriseai.source.confluence.FakeConfluence;
import com.aresstack.enterpriseai.source.confluence.FakeConfluenceServer;
import com.aresstack.enterpriseai.source.confluence.UrlConnectionConfluenceTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecret;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Slice D – Confluence + KeePass: {@code SecretRef} → echter KeePassRPC-Adapter (WebSocket, SRP-Pairing,
 * verschlüsselte Nachrichten) gegen einen Fake-KeePass → echter Confluence-Adapter (HTTP, Basic Auth) gegen ein
 * Fake-Confluence hinter einem lokalen HTTP-Server → Knowledge-Pipeline. Das Passwort verlässt die Kette nur als
 * Authorization-Header; in Berichten, Index und Ausnahmen taucht es nicht auf.
 */
public class SliceDConfluenceKeePassTest {

    private static final String ENTRY_TITLE = "Confluence Prod";
    private static final String USER = "alice";
    private static final String PASSWORD = "conf-pw-51e7-streng-geheim";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeKeePassRpcServer keePass;
    private FakeConfluence confluence;
    private FakeConfluenceServer server;
    private LuceneKnowledgeIndex index;
    private DeterministicEmbeddingPort embeddings;
    private IndexKnowledgeUseCase indexing;
    private RetrieveKnowledgeUseCase retrieval;
    private final AtomicInteger pairings = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        keePass = new FakeKeePassRpcServer().startAndWait();
        keePass.addEntry(ENTRY_TITLE, USER, PASSWORD, false);

        confluence = new FakeConfluence();
        confluence.page("100", "Start", "DEV", "<p>Willkommen im <b>DEV</b>-Space.</p>");
        confluence.page("200", "Betriebsvereinbarung", "DEV", "<h2>Kündigungsfrist</h2>"
                + "<p>Die Kündigungsfrist beträgt drei Monate zum Quartalsende.</p>");
        confluence.child("100", "200");
        confluence.homepage("DEV", "100");
        confluence.searchResults("200");
        server = new FakeConfluenceServer(confluence);

        embeddings = DeterministicEmbeddingPort.withDimension(8);
        index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());
        indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        retrieval = new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null);
    }

    @After
    public void tearDown() throws Exception {
        if (index != null) {
            index.close();
        }
        if (server != null) {
            server.close();
        }
        if (keePass != null) {
            keePass.stop(1000);
        }
    }

    private SecretProvider keePassProvider(int port) {
        KeePassRpcConfig config = KeePassRpcConfig.builder().port(port).timeoutMillis(10000).build();
        return new KeePassRpcSecretProvider(config, new InMemoryPairingKeyStore(), clientDisplayName -> {
            pairings.incrementAndGet();
            return keePass.pairingPassword().toCharArray();
        });
    }

    private ConfluenceKnowledgeSource source(SecretProvider secrets, String credentialRef) {
        ConfluenceConfig config = ConfluenceConfig.builder(server.baseUrl())
                .credentialRef(SecretRef.of(credentialRef))
                .allowInsecureHttp(true)
                .build();
        UrlConnectionConfluenceTransport transport = UrlConnectionConfluenceTransport.builder()
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(10000)
                .build();
        return new ConfluenceKnowledgeSource(KnowledgeSourceId.of("confluence"), config, transport, secrets);
    }

    private static SourceScope devSpace() {
        return SourceScope.builder().startPoint("space:DEV").maxDepth(2).build();
    }

    @Test
    public void secretRefResolvesThroughKeePassIntoBasicAuthAndThePipeline() throws Exception {
        ConfluenceKnowledgeSource source = source(keePassProvider(keePass.boundPort()), "keepass:" + ENTRY_TITLE);

        IndexingReport report = indexing.indexSource(source, devSpace(), IndexingListener.none());
        assertTrue(report.toString(), report.isComplete());
        assertEquals(2, report.discovered());
        assertEquals(2, report.count(IndexingStatus.INDEXED));
        assertEquals("einmal gepairt, danach gespeicherter Sitzungsschlüssel", 1, pairings.get());

        // Jede Confluence-Anfrage trug die Zugangsdaten aus KeePass als Basic Auth.
        String expected = "Basic " + Base64.getEncoder().encodeToString(
                (USER + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertFalse(confluence.requestHeaders().isEmpty());
        for (Map<String, String> headers : confluence.requestHeaders()) {
            assertEquals(expected, headers.get("Authorization"));
        }

        RetrievalResult result = retrieval.retrieve("Wie lange ist die Kündigungsfrist?");
        assertFalse(result.isEmpty());
        assertEquals("Betriebsvereinbarung", result.hits().get(0).resource().title());
        assertTrue(result.hits().get(0).chunk().text().contains("drei Monate zum Quartalsende"));

        // Kein Passwort in Berichten, Treffern oder toString() der Beteiligten.
        assertNoSecret(report.toString(), PASSWORD);
        assertNoSecret(result.toString(), PASSWORD);
        assertNoSecret(source.toString(), PASSWORD);
        for (String message : keePass.receivedMessages()) {
            assertNoSecret(message, PASSWORD);
        }
    }

    @Test
    public void missingKeePassEntryIsAccessDeniedWithoutLeakingAnything() throws Exception {
        ConfluenceKnowledgeSource source = source(keePassProvider(keePass.boundPort()), "keepass:Gibt es nicht");
        try {
            source.discover(devSpace());
            fail("fehlender Eintrag muss ACCESS_DENIED liefern");
        } catch (KnowledgeSourceException expected) {
            assertEquals(KnowledgeSourceException.Kind.ACCESS_DENIED, expected.kind());
        }
        assertEquals(0, confluence.requestCount("/rest/api/"));
        assertTrue(retrieval.retrieve("Kündigungsfrist").isEmpty());
    }

    @Test
    public void unreachableKeePassMakesTheSourceUnavailableInsteadOfHanging() throws Exception {
        int closedPort;
        ServerSocket probe = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
        try {
            closedPort = probe.getLocalPort();
        } finally {
            probe.close();
        }
        ConfluenceKnowledgeSource source = source(keePassProvider(closedPort), "keepass:" + ENTRY_TITLE);
        try {
            source.discover(devSpace());
            fail("ohne KeePass muss die Quelle UNAVAILABLE melden");
        } catch (KnowledgeSourceException expected) {
            assertEquals(KnowledgeSourceException.Kind.UNAVAILABLE, expected.kind());
        }
        assertEquals(0, pairings.get());
    }
}
