package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.app.chat.fakeapi.FakeChatCompletionsServer;
import com.aresstack.enterpriseai.app.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.app.composition.AdapterAssembly;
import com.aresstack.enterpriseai.app.composition.ApplicationPorts;
import com.aresstack.enterpriseai.app.composition.CompositionRoot;
import com.aresstack.enterpriseai.app.composition.ShellAssembly;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.net.ProxyPolicy;
import com.aresstack.enterpriseai.app.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.SourceReference;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.security.keepassrpc.FakeKeePassRpcServer;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.source.confluence.FakeConfluence;
import com.aresstack.enterpriseai.source.confluence.FakeConfluenceServer;
import com.aresstack.enterpriseai.source.mediawiki.FakeMediaWikiServer;
import com.aresstack.enterpriseai.source.mediawiki.FakeMediaWikiTransport;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecret;
import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecretInTranscript;
import static com.aresstack.enterpriseai.integration.SliceSupport.awaitIdle;
import static com.aresstack.enterpriseai.integration.SliceSupport.lastEntry;
import static com.aresstack.enterpriseai.integration.SliceSupport.onEdt;
import static com.aresstack.enterpriseai.integration.SliceSupport.runOnEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Slices A bis D zusammen, so wie die Anwendung startet: eine Properties-Konfiguration → {@code AdapterAssembly}
 * (echte Adapter: Chat, Embeddings, MediaWiki, Confluence, KeePassRPC, Lucene) → {@code CompositionRoot} →
 * Startindexierung → RAG-Frage über die Shell. Alle Gegenstellen sind lokale Fakes; der API-Key und beide
 * Quell-Zugangsdaten kommen ausschließlich aus dem Fake-KeePass.
 */
public class ConfiguredApplicationSliceTest {

    private static final String API_KEY = "kp-api-key-0d4e-vertraulich";
    private static final String WIKI_USER = "wikibot";
    private static final String WIKI_PASSWORD = "kp-wiki-pw-7a21";
    private static final String CONFLUENCE_USER = "alice";
    private static final String CONFLUENCE_PASSWORD = "kp-conf-pw-9c0f";
    private static final String QUESTION = "Wie lange ist die Kündigungsfrist?";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeChatCompletionsServer chatServer;
    private FakeEmbeddingsServer embeddingsServer;
    private FakeKeePassRpcServer keePass;
    private FakeMediaWikiTransport wiki;
    private FakeMediaWikiServer wikiServer;
    private FakeConfluence confluence;
    private FakeConfluenceServer confluenceServer;
    private final AtomicInteger pairings = new AtomicInteger();
    private ApplicationPorts ports;
    private CompositionRoot root;

    @Before
    public void setUp() throws Exception {
        chatServer = new FakeChatCompletionsServer();
        embeddingsServer = new FakeEmbeddingsServer(16);

        keePass = new FakeKeePassRpcServer().startAndWait();
        keePass.addEntry("Enterprise AI API", "api", API_KEY, false);
        keePass.addEntry("Intranet Wiki", WIKI_USER, WIKI_PASSWORD, false);
        keePass.addEntry("Confluence Prod", CONFLUENCE_USER, CONFLUENCE_PASSWORD, true);

        wiki = new FakeMediaWikiTransport();
        wiki.page("Hauptseite", "<p>Willkommen im <a href=\"/wiki/Handbuch\">Handbuch</a>.</p>", 1, 101, "Handbuch");
        wiki.page("Handbuch", "<h2>Installation</h2><p>Die Installation erfolgt per Gradle.</p>", 2, 102);
        wiki.requiredUser = WIKI_USER;
        wiki.requiredPassword = WIKI_PASSWORD;
        wikiServer = new FakeMediaWikiServer(wiki);

        confluence = new FakeConfluence();
        confluence.page("100", "Start", "DEV", "<p>Willkommen im <b>DEV</b>-Space.</p>");
        confluence.page("200", "Betriebsvereinbarung", "DEV", "<h2>Kündigungsfrist</h2>"
                + "<p>Die Kündigungsfrist beträgt drei Monate zum Quartalsende.</p>");
        confluence.child("100", "200");
        confluence.homepage("DEV", "100");
        confluenceServer = new FakeConfluenceServer(confluence);
    }

    @After
    public void tearDown() throws Exception {
        if (root != null) {
            root.shutdown().run();
        } else if (ports != null) {
            ports.close();
        }
        if (confluenceServer != null) {
            confluenceServer.close();
        }
        if (wikiServer != null) {
            wikiServer.close();
        }
        if (keePass != null) {
            keePass.stop(1000);
        }
        if (embeddingsServer != null) {
            embeddingsServer.close();
        }
        if (chatServer != null) {
            chatServer.close();
        }
    }

    private AppConfig config() {
        Properties p = new Properties();
        p.setProperty("ui.windowTitle", "Enterprise AI Client (Slice-Test)");
        p.setProperty("chat.baseUrl", chatServer.baseUrl().toString());
        p.setProperty("chat.model", "openai/gpt-oss-120b");
        p.setProperty("chat.apiKeyRef", "keepass:Enterprise AI API");
        p.setProperty("chat.systemPrompt", "Antworte knapp und nenne die Quelle.");
        p.setProperty("embedding.baseUrl", embeddingsServer.baseUrl());
        p.setProperty("embedding.model", "danielheinz/e5-base-sts-en-de");
        p.setProperty("embedding.dimension", "16");
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toPath().resolve("index").toString());
        p.setProperty("knowledge.indexOnStartup", "true");
        p.setProperty("retrieval.maxResults", "5");
        p.setProperty("sources", "wiki,confluence");
        p.setProperty("source.wiki.type", "mediawiki");
        p.setProperty("source.wiki.apiUrl", wikiServer.apiUrl());
        p.setProperty("source.wiki.siteKey", "intranet");
        p.setProperty("source.wiki.startPoints", "Hauptseite");
        p.setProperty("source.wiki.maxDepth", "1");
        p.setProperty("source.wiki.credentialRef", "keepass:Intranet Wiki");
        p.setProperty("source.confluence.type", "confluence");
        p.setProperty("source.confluence.baseUrl", confluenceServer.baseUrl().toString());
        p.setProperty("source.confluence.allowInsecureHttp", "true");
        p.setProperty("source.confluence.startPoints", "space:DEV");
        p.setProperty("source.confluence.maxDepth", "2");
        p.setProperty("source.confluence.credentialRef", "keepass:Confluence Prod");
        p.setProperty("security.keepass.enabled", "true");
        p.setProperty("security.keepass.port", String.valueOf(keePass.boundPort()));
        p.setProperty("security.keepass.pairingKeyStore", "memory");
        p.setProperty("security.keepass.timeoutMillis", "10000");
        p.setProperty("network.proxy.mode", "NONE");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertTrue(config.warnings().toString(), config.warnings().isEmpty());
        return config;
    }

    @Test
    public void configuredApplicationIndexesBothSourcesWithKeePassCredentialsAndAnswersWithSources() throws Exception {
        AppConfig config = config();
        ports = AdapterAssembly.create(config, new ProxyPolicy(config.network()), clientDisplayName -> {
            pairings.incrementAndGet();
            return keePass.pairingPassword().toCharArray();
        }, new InMemoryPairingKeyStore());
        assertEquals(2, ports.sources().size());
        assertFalse(ports.hasAgent());

        root = CompositionRoot.compose(config, ports, SwingUtilities::invokeLater, System::currentTimeMillis,
                ZoneId.of("Europe/Berlin"));
        root.startBackgroundWork();
        assertTrue("Startindexierung beendet", root.startupIndexing().awaitTermination(60, TimeUnit.SECONDS));

        // Beide Quellen wurden mit Zugangsdaten aus KeePass gelesen und über den echten Embedding-Adapter indexiert.
        List<IndexingReport> reports = root.startupIndexing().reports();
        assertEquals(reports.toString(), 2, reports.size());
        int discovered = 0;
        for (IndexingReport report : reports) {
            assertTrue(report.toString(), report.isComplete());
            assertFalse(report.toString(), report.discoveryFailed());
            discovered += report.discovered();
        }
        assertEquals("2 Wiki-Seiten + 2 Confluence-Seiten", 4, discovered);
        assertEquals("ein Pairing für alle Secrets", 1, pairings.get());
        synchronized (wiki) {
            assertEquals(1, wiki.logins);
        }
        String basic = "Basic " + Base64.getEncoder().encodeToString(
                (CONFLUENCE_USER + ":" + CONFLUENCE_PASSWORD).getBytes(StandardCharsets.UTF_8));
        assertFalse(confluence.requestHeaders().isEmpty());
        for (Map<String, String> headers : confluence.requestHeaders()) {
            assertEquals(basic, headers.get("Authorization"));
        }
        assertTrue(embeddingsServer.requestCount() > 0);
        for (FakeEmbeddingsServer.Recorded request : embeddingsServer.requests()) {
            assertEquals("Bearer " + API_KEY, request.authorization());
        }
        String status = onEdt(() -> root.knowledgeStatus().getText());
        assertTrue(status, status.startsWith("Wissensbasis: "));

        // RAG-Frage über die Shell: Kontext aus dem Index, Antwort mit Quellenangabe.
        final ShellAssembly.ShellView view = onEdt(() -> ShellAssembly.createShell(root,
                ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired()));
        final ChatShellModel model = view.chatModel();
        final ChatComposerPanel composer = view.modalShell().chatShell().composer();
        chatServer.answerWith("Drei Monate ", "zum Quartalsende [1].");
        runOnEdt(() -> {
            model.setRagEnabled(true);
            composer.editor().setText(QUESTION);
            composer.sendButton().doClick();
        });
        awaitIdle(model);

        TranscriptEntry answer = lastEntry(model);
        assertEquals(answer.getFailureMessage(), TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals("Drei Monate zum Quartalsende [1].", answer.getText());
        assertTrue("Quellen an der Antwort", answer.hasSources());
        List<String> titles = new ArrayList<String>();
        for (SourceReference source : answer.getSources()) {
            titles.add(source.getTitle());
        }
        assertTrue(titles.toString(), titles.contains("Betriebsvereinbarung"));

        FakeChatCompletionsServer.Recorded request = chatServer.lastRequest();
        assertEquals("Bearer " + API_KEY, request.authorization());
        assertTrue(request.systemContent(), request.systemContent().startsWith("Antworte knapp und nenne die Quelle."));
        assertTrue(request.systemContent(), request.systemContent().contains("--- KONTEXT ---"));
        assertTrue(request.systemContent(), request.systemContent().contains("drei Monate zum Quartalsende"));

        // Kein Secret in Oberfläche, Statuszeile, Anfrage oder toString() der Ports.
        assertNoSecretInTranscript(model, API_KEY, WIKI_PASSWORD, CONFLUENCE_PASSWORD);
        assertNoSecret(status, API_KEY, WIKI_PASSWORD, CONFLUENCE_PASSWORD);
        assertNoSecret(request.systemContent(), API_KEY, WIKI_PASSWORD, CONFLUENCE_PASSWORD);
        assertNoSecret(ports.toString(), API_KEY, WIKI_PASSWORD, CONFLUENCE_PASSWORD);
        assertNoSecret(config.toString(), API_KEY, WIKI_PASSWORD, CONFLUENCE_PASSWORD);
    }
}
