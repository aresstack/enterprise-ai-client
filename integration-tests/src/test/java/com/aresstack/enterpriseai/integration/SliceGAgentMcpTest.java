package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpEndpointDescriptor;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.agent.AgentMcpEnvironment;
import com.aresstack.enterpriseai.app.composition.AgentBackend;
import com.aresstack.enterpriseai.app.composition.ApplicationPorts;
import com.aresstack.enterpriseai.app.composition.CompositionRoot;
import com.aresstack.enterpriseai.app.composition.ShellAssembly;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.app.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.agent.AgentExchange;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.agent.AgentStatus;
import com.aresstack.enterpriseai.application.agent.AgentTurnState;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.integration.agent.KnowledgeDemoAgentMain;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpToolClientFactory;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_PHRASE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_TITLE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.QUESTION;
import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecret;
import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecretInTranscript;
import static com.aresstack.enterpriseai.integration.SliceSupport.awaitIdle;
import static com.aresstack.enterpriseai.integration.SliceSupport.entries;
import static com.aresstack.enterpriseai.integration.SliceSupport.onEdt;
import static com.aresstack.enterpriseai.integration.SliceSupport.runOnEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Slice G – Agent + MCP: Chat-UI im Agent-Modus (Composer der Agent-Karte) → {@code AgentService} →
 * {@code AcpAgentLauncher} (registriert den MCP-Endpoint, übergibt ihn per Umgebung) → ACP-Kindprozess
 * {@link KnowledgeDemoAgentMain} → MCP {@code search_knowledge} über Port und Solon-Adapter → Wissensindex
 * (Lucene) → Antwort des Agenten in der Sprechblase. Zusammengesetzt über {@code CompositionRoot} und
 * {@code ShellAssembly} aus AP23; nur der Agentenprozess ist der Testagent dieses Moduls.
 */
public class SliceGAgentMcpTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final List<String> agentStderr = Collections.synchronizedList(new ArrayList<String>());
    private SolonMcpServerRuntime mcp;
    private RecordingConnector connector;
    private CompositionRoot root;
    private ShellAssembly.ShellView view;
    private boolean shutDown;

    private void compose(boolean withMcp) throws Exception {
        AgentLaunchSpec spec = SliceSupport.knowledgeAgentLaunchSpec(temp.newFolder("agent").toPath());
        connector = new RecordingConnector(new SolonAcpAgentConnector(Duration.ofSeconds(60), agentStderr::add));
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        final LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());
        ApplicationPorts.Builder ports = ApplicationPorts.builder()
                .chat(new FakeChatCompletionPort(0L))
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(index)
                .sources(KnowledgeSourceCatalog.of(
                        new KnowledgeSourceRegistration(SampleKnowledge.handbuch(), SampleKnowledge.scope())))
                .secrets(new UnavailableSecretProvider("Slice G läuft ohne KeePass"));
        if (withMcp) {
            mcp = new SolonMcpServerRuntime();
            ports.agent(new AgentBackend(connector, spec, mcp, new McpEndpointDefinition("agent-tools", "Wissenswerkzeuge")));
            // Reihenfolge wie in AdapterAssembly: erst der MCP-Server, dann der Index.
            ports.closing("mcp-server", () -> mcp.shutdown());
        } else {
            ports.agent(new AgentBackend(connector, spec, null, null));
        }
        ports.closing("knowledge-index", index);

        Properties p = new Properties();
        p.setProperty("chat.baseUrl", "http://127.0.0.1:9/v1");
        p.setProperty("chat.model", "test-chat");
        p.setProperty("chat.apiKeyRef", "Enterprise AI API");
        p.setProperty("embedding.model", "test-embedding");
        p.setProperty("embedding.dimension", "8");
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toString());
        p.setProperty("knowledge.indexOnStartup", "true");
        p.setProperty("security.keepass.enabled", "false");
        p.setProperty("network.proxy.mode", "DISABLED");
        p.setProperty("agent.enabled", "true");
        p.setProperty("agent.command", spec.getCommand());
        p.setProperty("agent.mcpEndpointId", "agent-tools");
        p.setProperty("agent.tools.defaultMaxResults", "3");
        root = CompositionRoot.compose(AppConfigLoader.fromProperties(p), ports.build(), SwingUtilities::invokeLater,
                System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
        root.startBackgroundWork();
        assertTrue("Startindexierung beendet", root.startupIndexing().awaitTermination(60, TimeUnit.SECONDS));
        assertEquals(3, root.startupIndexing().reports().get(0).discovered());
        view = onEdt(() -> ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired()));
        assertNotNull("Agent-Karte vorhanden", view.agent());
    }

    @After
    public void tearDown() {
        shutdown();
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    private void shutdown() {
        if (root != null && !shutDown) {
            shutDown = true;
            root.shutdown().run();
        }
    }

    /** Wie eine Nutzerin im Agent-Modus: Frage in den Composer der Agent-Karte, Senden. */
    private void ask(final String question) throws Exception {
        final ChatComposerPanel composer = view.agent().shell().composer();
        runOnEdt(() -> {
            composer.editor().setText(question);
            assertTrue(composer.sendButton().isEnabled());
            composer.sendButton().doClick();
        });
    }

    private List<String> stderrSnapshot() {
        synchronized (agentStderr) {
            return new ArrayList<String>(agentStderr);
        }
    }

    @Test
    public void agentAnswersTheQuestionFromTheKnowledgeIndexOverMcp() throws Exception {
        compose(true);
        ChatShellModel agentModel = view.agent().model();
        ask(QUESTION);
        awaitIdle(agentModel);

        // Die Sprechblase trägt die Antwort des Agenten, zusammengesetzt aus dem MCP-Suchergebnis.
        List<TranscriptEntry> transcript = entries(agentModel);
        assertEquals(transcript.toString(), 2, transcript.size());
        assertEquals(QUESTION, transcript.get(0).getText());
        TranscriptEntry reply = transcript.get(1);
        assertEquals(reply.getFailureMessage(), TranscriptEntry.State.COMPLETE, reply.getState());
        assertTrue(reply.getText(), reply.getText().startsWith(KnowledgeDemoAgentMain.FOUND_PREFIX));
        assertTrue(reply.getText(), reply.getText().contains("[1] " + EXPECTED_TITLE));
        assertTrue(reply.getText(), reply.getText().contains(EXPECTED_PHRASE));

        AgentService service = root.agentService();
        assertEquals(AgentStatus.READY, service.status());
        AgentExchange exchange = service.transcript().get(0);
        assertEquals(AgentTurnState.COMPLETED, exchange.state());
        assertTrue(exchange.thoughts(), exchange.thoughts().contains(KnowledgeDemoAgentMain.THOUGHT_PREFIX + QUESTION));
        assertFalse("Überlegungen bleiben aus der Blase", reply.getText().contains(KnowledgeDemoAgentMain.THOUGHT_PREFIX));

        // Der Chat-Verlauf ist unberührt; der Agent hat seine eigene Karte.
        assertTrue(entries(view.chatModel()).isEmpty());

        // Der Endpoint kam per Umgebung (AP21) und erscheint nirgends: nicht in der Blase, nicht im Transkript,
        // nicht im STDERR des Agenten, den der Host mitliest.
        AcpEndpointDescriptor endpoint = AgentMcpEnvironment.read(connector.spec.get().getEnv());
        assertNotNull(endpoint);
        assertTrue(endpoint.getUrl(), endpoint.getUrl().startsWith("http://127.0.0.1:"));
        assertFalse(endpoint.getToken().isEmpty());
        assertNoSecretInTranscript(agentModel, endpoint.getToken(), endpoint.getUrl());
        for (AgentExchange x : service.transcript()) {
            assertNoSecret(x.toString(), endpoint.getToken(), endpoint.getUrl());
        }
        SliceSupport.await("Agent-Log auf STDERR beim Host", () -> {
            for (String line : stderrSnapshot()) {
                if (line.contains("[knowledge-agent] search_knowledge returned")) {
                    return true;
                }
            }
            return false;
        });
        for (String line : stderrSnapshot()) {
            assertNoSecret(line, endpoint.getToken(), endpoint.getUrl());
        }

        // Ende des Agent-Modus über die Shutdown-Sequenz: Prozess tot, Token ungültig.
        AcpConnection connection = connector.connection.get();
        assertTrue(connection.getProcess().isAlive());
        shutdown();
        assertFalse("Agentenprozess endet mit der Anwendung", connection.getProcess().isAlive());
        SolonMcpToolClientFactory clients = new SolonMcpToolClientFactory(Duration.ofSeconds(10), Duration.ofSeconds(10));
        McpToolClient stale = clients.connect(endpoint.getUrl(), endpoint.getTransport());
        try {
            stale.listTools();
            fail("der Endpoint-Token muss nach dem Ende ungültig sein");
        } catch (McpToolCallException expected) {
            assertNoSecret(expected.getMessage(), endpoint.getToken());
        } finally {
            stale.close();
        }
    }

    @Test
    public void withoutAnMcpEndpointTheAgentSaysSo() throws Exception {
        compose(false);
        ChatShellModel agentModel = view.agent().model();
        ask(QUESTION);
        awaitIdle(agentModel);

        TranscriptEntry reply = entries(agentModel).get(1);
        assertEquals(TranscriptEntry.State.COMPLETE, reply.getState());
        assertEquals(KnowledgeDemoAgentMain.NO_KNOWLEDGE_BASE, reply.getText());
        assertEquals(null, AgentMcpEnvironment.read(connector.spec.get().getEnv() == null
                ? Collections.<String, String>emptyMap() : connector.spec.get().getEnv()));
    }
}
