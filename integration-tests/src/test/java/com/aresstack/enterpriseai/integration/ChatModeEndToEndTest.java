package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingReport;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPath;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalResult;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.FakeConfluence;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence.FakeConfluenceServer;
import com.aresstack.enterpriseai.proasteion.exedra.assembly.ShellAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.chat.fakeapi.FakeChatCompletionsServer;
import com.aresstack.enterpriseai.proasteion.exedra.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.FakeKeePassRpcServer;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

import static com.aresstack.enterpriseai.integration.SliceSupport.awaitIdle;
import static com.aresstack.enterpriseai.integration.SliceSupport.entries;
import static com.aresstack.enterpriseai.integration.SliceSupport.lastEntry;
import static com.aresstack.enterpriseai.integration.SliceSupport.onEdt;
import static com.aresstack.enterpriseai.integration.SliceSupport.runOnEdt;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Ende-zu-Ende-Abnahme des Chat-Modus über die ganze Anwendung: Properties-Konfiguration → {@code AdapterAssembly}
 * (echter OpenAI-kompatibler Chat- und Embedding-Adapter, Confluence-Adapter, Lucene) → {@code CompositionRoot} →
 * {@link ShellAssembly} (Composer, {@code RagChatBinding}) gegen lokale Fake-Server für {@code /chat/completions},
 * {@code /embeddings}, Confluence und KeePass.
 *
 * <p>Abnahmeszenarien 1 (Small Talk ohne Wissenssuche und ohne RAG-Warnung) und 8 (Semantische Suche: echter
 * Embedding-Aufruf, semantischer Trefferpfad trägt).
 */
public class ChatModeEndToEndTest {

    private static final String API_KEY = "kp-api-key-e2e-chat";
    private static final String CONFLUENCE_USER = "alice";
    private static final String CONFLUENCE_PASSWORD = "kp-conf-pw-e2e";
    private static final String QUESTION = "Wie lange ist die Kündigungsfrist?";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** Alle Erwartungen an Small Talk auf einmal melden (Abnahmeliste), statt beim ersten Fehler abzubrechen. */
    @Rule
    public ErrorCollector expected = new ErrorCollector();

    private FakeChatCompletionsServer chatServer;
    private FakeEmbeddingsServer embeddingsServer;
    private FakeKeePassRpcServer keePass;
    private FakeConfluence confluence;
    private FakeConfluenceServer confluenceServer;
    private CompositionRoot root;
    private ShellAssembly.ShellView view;
    private ChatShellModel model;

    @Before
    public void setUp() throws Exception {
        chatServer = new FakeChatCompletionsServer();
        embeddingsServer = new FakeEmbeddingsServer(16);
        keePass = new FakeKeePassRpcServer().startAndWait();
        keePass.addEntry("Enterprise AI API", "api", API_KEY, false);
        keePass.addEntry("Confluence Prod", CONFLUENCE_USER, CONFLUENCE_PASSWORD, true);
        confluence = new FakeConfluence();
        confluence.page("100", "Start", "DEV", "<p>Willkommen im <b>DEV</b>-Space.</p>");
        confluence.page("200", "Betriebsvereinbarung", "DEV", "<h2>Kündigungsfrist</h2>"
                + "<p>Die Kündigungsfrist beträgt drei Monate zum Quartalsende.</p>");
        confluence.child("100", "200");
        confluence.homepage("DEV", "100");
        confluenceServer = new FakeConfluenceServer(confluence);

        AppConfig config = config();
        ApplicationPorts ports = AdapterAssembly.create(config, NetworkServices.from(config.network()),
                clientDisplayName -> keePass.pairingPassword().toCharArray(), new InMemoryPairingKeyStore());
        root = CompositionRoot.compose(config, ports, SwingUtilities::invokeLater, System::currentTimeMillis,
                ZoneId.of("Europe/Berlin"));
        view = onEdt(() -> ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired()));
        model = view.chatModel();
        // wie die Shell bei angehakter Quelle: gesucht wird, sobald Wissensquellen angebunden sind
        runOnEdt(() -> model.setRagEnabled(true));
    }

    @After
    public void tearDown() throws Exception {
        if (root != null) {
            root.shutdown().run();
        }
        if (confluenceServer != null) {
            confluenceServer.close();
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
        p.setProperty("ui.windowTitle", "Enterprise AI Client (E2E Chat)");
        p.setProperty("chat.baseUrl", chatServer.baseUrl().toString());
        p.setProperty("chat.model", "openai/gpt-oss-120b");
        p.setProperty("chat.apiKeyRef", "keepass:Enterprise AI API");
        p.setProperty("chat.systemPrompt", "Antworte knapp.");
        p.setProperty("embedding.baseUrl", embeddingsServer.baseUrl());
        p.setProperty("embedding.model", "danielheinz/e5-base-sts-en-de");
        p.setProperty("embedding.dimension", "16");
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toPath().resolve("index").toString());
        p.setProperty("retrieval.maxResults", "5");
        p.setProperty("source.1.type", "confluence");
        p.setProperty("source.1.baseUrl", confluenceServer.baseUrl().toString());
        p.setProperty("source.1.allowInsecureHttp", "true");
        p.setProperty("source.1.startPoints", "space:DEV");
        p.setProperty("source.1.maxDepth", "2");
        p.setProperty("source.1.credentialRef", "keepass:Confluence Prod");
        p.setProperty("security.keepass.enabled", "true");
        p.setProperty("security.keepass.port", String.valueOf(keePass.boundPort()));
        p.setProperty("security.keepass.pairingKeyStore", "memory");
        p.setProperty("security.keepass.timeoutMillis", "10000");
        p.setProperty("network.proxy.mode", "DISABLED");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertTrue(config.warnings().toString(), config.warnings().isEmpty());
        return config;
    }

    /** Die Wissensbasis wie nach „Alles indexieren“. */
    private void indexAll() {
        for (KnowledgeSourceRegistration registration : root.ports().sources().registrations()) {
            IndexingReport report = root.indexing().indexSource(registration.port(), registration.scope(), null);
            assertTrue(report.toString(), report.isComplete());
        }
    }

    /** Wie eine Nutzerin: Text in den Composer, Senden-Knopf, warten bis die Antwort steht. */
    private void send(final String text) throws Exception {
        final ChatComposerPanel composer = view.workspace().chatShell().composer();
        runOnEdt(() -> {
            composer.editor().setText(text);
            assertTrue("Senden muss möglich sein", composer.sendButton().isEnabled());
            composer.sendButton().doClick();
        });
        awaitIdle(model);
    }

    @Test
    public void scenario1_smallTalkSearchesNoKnowledgeAndShowsNoRagWarning() throws Exception {
        indexAll();
        int embeddingsBefore = embeddingsServer.requestCount();
        int confluenceBefore = confluence.requestHeaders().size();
        chatServer.answerWith("Hallo! ", "Wie kann ich helfen?");
        chatServer.answerAsidesWith("{\"search\": false, \"query\": \"\"}"); // the model's triage: no search

        send("Hi");

        TranscriptEntry answer = lastEntry(model);
        assertEquals(answer.getFailureMessage(), TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals("Hallo! Wie kann ich helfen?", answer.getText());
        // keine Wissenssuche: weder Einbettung der Frage noch Live-Suche in der Quelle noch Kontextblock
        expected.checkThat("keine Einbettung für Small Talk (Anfragen an /embeddings)",
                embeddingsServer.requestCount(), is(embeddingsBefore));
        expected.checkThat("keine Live-Suche in Confluence für Small Talk (Anfragen an Confluence)",
                confluence.requestHeaders().size(), is(confluenceBefore));
        String system = chatServer.lastRequest().systemContent();
        expected.checkThat("kein Kontextblock bei Small Talk: " + system, system.contains("--- KONTEXT ---"),
                is(false));
        expected.checkThat("keine Quellen an einer Begrüßung", answer.hasSources(), is(false));
        // keine RAG-Warnung im Verlauf
        List<TranscriptEntry> transcript = entries(model);
        for (TranscriptEntry entry : transcript) {
            expected.checkThat("RAG-Hinweis bei Small Talk: " + entry.getText(),
                    entry.getAuthor() == TranscriptEntry.Author.NOTICE, is(false));
        }
    }

    /**
     * The answer planning step reads every message before the answer; a greeting needs no information, so nothing is
     * searched, and the answer sees original and interpretation.
     */
    @Test
    public void scenario1_answerPlanningKeepsSmallTalkAwayFromTheSources() throws Exception {
        indexAll();
        int embeddingsBefore = embeddingsServer.requestCount();
        chatServer.answerWith("Hallo!");
        chatServer.answerAsidesWith("{\"interpretedText\": \"Hi\", \"intent\": \"GREETING\", "
                + "\"informationNeed\": \"NO_INFORMATION_NEEDED\", \"ambiguity\": \"LOW\", \"confidence\": 0.99, "
                + "\"nextAction\": \"ANSWER_DIRECTLY\"}");

        send("Hi");

        TranscriptEntry answer = lastEntry(model);
        assertEquals(answer.getFailureMessage(), TranscriptEntry.State.COMPLETE, answer.getState());
        expected.checkThat("keine Einbettung für einen Gruß", embeddingsServer.requestCount(), is(embeddingsBefore));
        String system = chatServer.lastRequest().systemContent();
        expected.checkThat(system, system.contains("<client-answer-plan>"), is(true));
        expected.checkThat(system, system.contains("original-user-message: Hi"), is(true));
        expected.checkThat(system, system.contains("intent: GREETING"), is(true));
        expected.checkThat("Antwortplan und Antwort", chatServer.requests().size(), is(2));
    }

    @Test
    public void scenario1_smallTalkOnAnEmptyKnowledgeBaseShowsNoRagWarning() throws Exception {
        chatServer.answerWith("Hallo!");
        chatServer.answerAsidesWith("{\"search\": false, \"query\": \"\"}"); // the model's triage: no search

        send("Hi");

        TranscriptEntry answer = lastEntry(model);
        assertEquals(answer.getFailureMessage(), TranscriptEntry.State.COMPLETE, answer.getState());
        for (TranscriptEntry entry : entries(model)) {
            expected.checkThat("RAG-Hinweis bei Small Talk: " + entry.getText(),
                    entry.getAuthor() == TranscriptEntry.Author.NOTICE, is(false));
        }
        expected.checkThat("keine Einbettung für Small Talk", embeddingsServer.requestCount(), is(0));
    }

    @Test
    public void scenario8_semanticSearchCallsTheRealEmbeddingEndpointAndTheSemanticPathCarries() throws Exception {
        indexAll();
        int embeddingsBefore = embeddingsServer.requestCount();
        chatServer.answerWith("Drei Monate ", "zum Quartalsende [1].");

        send(QUESTION);

        // echter Embedding-Aufruf der Frage über den konfigurierten Adapter, mit Zugangsdaten aus KeePass
        assertTrue("die Frage wurde eingebettet", embeddingsServer.requestCount() > embeddingsBefore);
        boolean questionEmbedded = false;
        for (FakeEmbeddingsServer.Recorded request : embeddingsServer.requests()) {
            assertEquals("Bearer " + API_KEY, request.authorization());
            questionEmbedded |= request.inputs().contains(QUESTION);
        }
        assertTrue("die Nutzerfrage selbst ging an /embeddings", questionEmbedded);

        // sichtbar: Antwort mit Quelle, kein Hinweis auf einen ausgefallenen Suchpfad
        TranscriptEntry answer = lastEntry(model);
        assertEquals(answer.getFailureMessage(), TranscriptEntry.State.COMPLETE, answer.getState());
        assertTrue("Quellen an der Antwort", answer.hasSources());
        for (TranscriptEntry entry : entries(model)) {
            assertFalse("Hinweis zur Wissensbeschaffung: " + entry.getText(),
                    entry.getAuthor() == TranscriptEntry.Author.NOTICE);
        }
        String system = chatServer.lastRequest().systemContent();
        assertTrue(system, system.contains("drei Monate zum Quartalsende"));

        // der semantische Trefferpfad derselben Retrieval-Instanz trägt (nicht degradiert)
        RetrievalResult result = root.retrieval().retrieve(QUESTION);
        assertFalse(result.toString(), result.isDegraded());
        assertTrue(result.warnings().toString(), result.warnings().isEmpty());
        boolean semantic = false;
        for (RetrievedChunk hit : result.hits()) {
            if ("Betriebsvereinbarung".equals(hit.resource().title())) {
                semantic |= hit.foundBy(RetrievalPath.SEMANTIC) && hit.semanticRank().isPresent();
            }
        }
        assertTrue("Betriebsvereinbarung über den semantischen Pfad gefunden: " + result, semantic);
    }
}
