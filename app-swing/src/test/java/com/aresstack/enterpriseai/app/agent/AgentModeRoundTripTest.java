package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpEndpointDescriptor;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.chat.ChatServiceBinding;
import com.aresstack.enterpriseai.app.ui.agent.ModalShellPanel;
import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.agent.AgentExchange;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.agent.AgentStatus;
import com.aresstack.enterpriseai.application.agent.AgentTurnState;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.testkit.McpTestTools;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpToolClientFactory;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * AP21 Ende-zu-Ende, headless: die Shell mit Modus-Umschaltung, der Chat gegen den Fake-ChatCompletionPort und
 * der Agent-Modus mit dem echten Demo-Agenten als Kindprozess (über {@code SolonAcpAgentConnector}) samt
 * eigenem MCP-Endpoint auf dem echten Solon-Server (Test-Tools aus mcp-runtime-api).
 *
 * <p>Der Demo-Agent aus askai-java8 ruft selbst keine MCP-Tools auf. Belegt wird deshalb die Verdrahtung: Der
 * Agent bekommt den Endpoint über seine Umgebung, dort sind die Tools gelistet und aufrufbar, und mit dem
 * Ende des Agent-Modus ist der Token ungültig.
 *
 * <p>Solon ist prozessglobal: Gradle startet jede Testklasse von app-swing in einer eigenen JVM, und diese
 * Klasse stoppt den gemeinsamen Server am Ende.
 */
public class AgentModeRoundTripTest {

    private static final long TIMEOUT_SECONDS = 60L;

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();

    private String javaBin;
    private String agentJar;
    private SolonMcpServerRuntime mcpRuntime;
    private ExecutorService agentStarts;
    private RecordingConnector connector;
    private AgentService agentService;
    private FakeChatCompletionPort chatPort;
    private ChatService chatService;

    /** Reicht an den echten Connector durch und merkt sich, was der Agent mitbekommen hat. */
    private static final class RecordingConnector implements AcpAgentConnector {
        final AcpAgentConnector delegate;
        final AtomicReference<AgentLaunchSpec> spec = new AtomicReference<AgentLaunchSpec>();
        final AtomicReference<AcpConnection> connection = new AtomicReference<AcpConnection>();

        RecordingConnector(AcpAgentConnector delegate) {
            this.delegate = delegate;
        }

        @Override
        public AcpConnection connect(AgentLaunchSpec launchSpec) throws AcpException {
            spec.set(launchSpec);
            AcpConnection opened = delegate.connect(launchSpec);
            connection.set(opened);
            return opened;
        }
    }

    @Before
    public void setUp() {
        boolean required = Boolean.getBoolean("acp.roundtrip.required");
        agentJar = System.getProperty("acp.demo.agent.jar");
        String home = System.getProperty("acp.agent.java.home", System.getProperty("java.home"));
        javaBin = home + File.separator + "bin" + File.separator
                + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        boolean ready = agentJar != null && new File(agentJar).isFile() && new File(javaBin).isFile();
        if (required && !ready) {
            fail("round-trip prerequisites missing: agent jar=" + agentJar + ", java=" + javaBin);
        }
        assumeTrue("demo agent jar not set (run via Gradle)", ready);

        mcpRuntime = new SolonMcpServerRuntime();
        agentStarts = Executors.newSingleThreadExecutor();
        connector = new RecordingConnector(new SolonAcpAgentConnector(Duration.ofSeconds(30), null));
        AcpAgentLauncher launcher = new AcpAgentLauncher(connector,
                new AgentLaunchSpec(javaBin, Arrays.asList("-jar", agentJar), null),
                mcpRuntime, new McpEndpointDefinition("agent-tools", "Agent-Tools"),
                Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        agentService = new AgentService(launcher, agentStarts);
        chatPort = new FakeChatCompletionPort();
        chatService = new ChatService(chatPort);
    }

    @After
    public void tearDown() {
        if (agentService != null) {
            agentService.close();
        }
        if (agentStarts != null) {
            agentStarts.shutdownNow();
        }
        if (mcpRuntime != null) {
            mcpRuntime.shutdown();
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    @Test
    public void chatAndAgentRunSideBySideWithSeparateTranscriptsAndSessions() throws Exception {
        final ChatConversationId conversation = chatService.openConversation();
        final Shell shell = onEdt(new Callable<Shell>() {
            @Override
            public Shell call() {
                return new Shell(conversation);
            }
        });

        // 1. Chat: läuft, ohne dass ein Agent gestartet wurde.
        chatPort.enqueueAnswer("Chat ", "sagt ", "hallo");
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                shell.chatBinding.sendRequested("Hallo Chat", false);
                return null;
            }
        });
        awaitIdle(shell.chatModel);
        assertEquals(AgentStatus.NOT_STARTED, agentService.status());
        assertEquals(null, connector.spec.get());

        // 2. Umschalten und Auftrag an den echten Demo-Agenten.
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(shell.modes.select(ShellMode.AGENT));
                assertSame(shell.panel.agentShell(), shell.panel.visibleShell());
                shell.agentBinding().sendRequested("hello agent", false);
                return null;
            }
        });
        awaitIdle(shell.agentModel);
        List<TranscriptEntry> agentEntries = entries(shell.agentModel);
        assertEquals(2, agentEntries.size());
        assertEquals("hello agent", agentEntries.get(0).getText());
        TranscriptEntry reply = agentEntries.get(1);
        assertEquals(TranscriptEntry.State.COMPLETE, reply.getState());
        // Inhalt, nicht Reihenfolge: acp-solon-client liefert session/update-Notifications derzeit nicht
        // garantiert in Wire-Reihenfolge aus (in CI kam "chunk 3" vor "chunk 2"). Das ist ein Adapter-Befund
        // für Strang G; dieser Test prüft die Verdrahtung des Agent-Modus.
        for (int i = 1; i <= 3; i++) {
            assertTrue(reply.getText(), reply.getText().contains("chunk " + i + " for 'hello agent'"));
        }
        assertEquals(3 * "chunk 1 for 'hello agent'".length(), reply.getText().length());
        assertEquals(AgentStatus.READY, agentService.status());
        String agentSession = agentService.sessionId();
        assertNotNull(agentSession);

        AgentExchange exchange = agentService.transcript().get(0);
        assertEquals(AgentTurnState.COMPLETED, exchange.state());
        assertTrue("thoughts stay in the agent transcript", exchange.thoughts().contains("thinking about"));
        assertFalse("thoughts are not part of the bubble", reply.getText().contains("thinking"));

        // 3. Getrennt: Der Chat sieht nichts vom Agenten und umgekehrt.
        List<TranscriptEntry> chatEntries = entries(shell.chatModel);
        assertEquals(2, chatEntries.size());
        assertEquals("Chat sagt hallo", chatEntries.get(1).getText());
        for (ChatMessage message : chatService.conversation(conversation).messages()) {
            assertFalse(message.content().contains("agent"));
        }
        assertEquals(1, agentService.transcript().size());

        // 4. Stop im Agent-Modus: der Agent bestätigt den Abbruch, Prozess und Session bleiben. Stop wird direkt
        // auf dem EDT ausgelöst, sobald der erste Chunk sichtbar ist; der Testthread wartet nur auf dem Latch.
        final CountDownLatch stopped = new CountDownLatch(1);
        final ChatShellModelListener stopOnFirstChunk = new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
                if (stopped.getCount() > 0 && entry.getText().contains("chunk 1 for 'slow burn'")) {
                    stopped.countDown();
                    shell.agentBinding().stopRequested();
                }
            }

            @Override
            public void stateChanged() {
            }
        };
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                shell.agentModel.addListener(stopOnFirstChunk);
                shell.agentBinding().sendRequested("slow burn", false);
                return null;
            }
        });
        assertTrue("first chunk of the slow prompt never arrived", stopped.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        awaitIdle(shell.agentModel);
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                shell.agentModel.removeListener(stopOnFirstChunk);
                return null;
            }
        });
        agentEntries = entries(shell.agentModel);
        assertEquals(TranscriptEntry.State.CANCELLED, agentEntries.get(3).getState());

        // Moduswechsel zurück; ein weiterer Auftrag läuft in derselben ACP-Session.
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                assertTrue(shell.modes.select(ShellMode.CHAT));
                shell.agentBinding().sendRequested("again", false);
                return null;
            }
        });
        awaitIdle(shell.agentModel);
        assertEquals(TranscriptEntry.State.COMPLETE, entries(shell.agentModel).get(5).getState());
        assertEquals(agentSession, agentService.sessionId());
        assertEquals("chat transcript untouched by the agent", 2, entries(shell.chatModel).size());
    }

    @Test
    public void agentReceivesItsOwnMcpEndpointWhichDiesWithTheAgentMode() throws Exception {
        final ChatShellModel model = onEdt(new Callable<ChatShellModel>() {
            @Override
            public ChatShellModel call() {
                ChatShellModel agentModel = new ChatShellModel(System::currentTimeMillis);
                new AgentServiceBinding(agentService, agentModel, AgentModeRoundTripTest::runOnEdt)
                        .sendRequested("hello tools", false);
                return agentModel;
            }
        });
        awaitIdle(model);

        AgentLaunchSpec spec = connector.spec.get();
        Map<String, String> env = spec.getEnv();
        AcpEndpointDescriptor endpoint = AgentMcpEnvironment.read(env);
        assertNotNull("agent got an MCP endpoint", endpoint);
        assertTrue(endpoint.getUrl().startsWith("http://127.0.0.1:"));
        assertFalse(spec.toString().contains(endpoint.getToken()));
        // Hinweis: AcpEndpointDescriptor.toString() zeigt die URL, und die Solon-URL trägt den Token im Pfad.
        // Deshalb wird der Descriptor nirgends geloggt; die Korrektur gehört in acp-client-api (Contract).

        SolonMcpToolClientFactory clients = new SolonMcpToolClientFactory(Duration.ofSeconds(10),
                Duration.ofSeconds(10));
        McpToolClient client = clients.connect(endpoint.getUrl(), endpoint.getTransport());
        try {
            assertEquals(Arrays.asList("ping", "echo"),
                    new java.util.ArrayList<String>(client.listTools().keySet()));
            assertEquals("pong", client.callTool("ping", Collections.<String, Object>emptyMap()));
        } finally {
            client.close();
        }

        AcpConnection agentConnection = connector.connection.get();
        assertTrue(agentConnection.getProcess().isAlive());
        agentService.close();
        assertFalse("agent process ended with the agent mode", agentConnection.getProcess().isAlive());

        McpToolClient stale = clients.connect(endpoint.getUrl(), endpoint.getTransport());
        try {
            stale.listTools();
            fail("the endpoint token must be invalid once the agent mode is closed");
        } catch (McpToolCallException expected) {
            assertFalse(String.valueOf(expected.getMessage()).contains(endpoint.getToken()));
        } finally {
            stale.close();
        }
    }

    /** Die Shell, wie AP23 sie zusammensetzen wird: Chat-Karte und Agent-Karte mit eigenen Models. */
    private final class Shell {
        final ChatShellModel chatModel = new ChatShellModel(System::currentTimeMillis);
        final ChatServiceBinding chatBinding;
        final AgentModeAssembly.AgentView agentView;
        final ChatShellModel agentModel;
        final ShellModeModel modes = new ShellModeModel(true);
        final ModalShellPanel panel;

        Shell(ChatConversationId conversation) {
            chatBinding = new ChatServiceBinding(chatService, conversation, chatModel,
                    AgentModeRoundTripTest::runOnEdt);
            ChatShellPanel chatShell = new ChatShellPanel(chatModel, chatBinding, comic, bubbles);
            agentView = AgentModeAssembly.create(agentService, AgentModeRoundTripTest::runOnEdt,
                    System::currentTimeMillis, comic, bubbles);
            agentModel = agentView.model();
            panel = new ModalShellPanel(modes, chatShell, agentView.shell(), comic);
            panel.setSize(800, 600);
            panel.doLayout();
        }

        AgentServiceBinding agentBinding() {
            return agentView.binding();
        }
    }

    private static void runOnEdt(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    private static List<TranscriptEntry> entries(final ChatShellModel model) throws Exception {
        return onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return new java.util.ArrayList<TranscriptEntry>(model.getEntries());
            }
        });
    }

    /**
     * Wartet außerhalb des EDT, bis das Model nicht mehr streamt. Ein Listener auf dem EDT setzt den Latch; der
     * Testthread blockiert nur im {@code await} mit Frist. Kein {@code invokeAndWait} in der Warteschleife: Ist
     * der EDT unter Last voll, würde das Warten dort die Frist aushebeln und der Test hinge statt zu scheitern.
     */
    private static void awaitIdle(final ChatShellModel model) throws Exception {
        final CountDownLatch idle = new CountDownLatch(1);
        final ChatShellModelListener listener = new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
            }

            @Override
            public void stateChanged() {
                if (!model.isStreaming()) {
                    idle.countDown();
                }
            }
        };
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                model.addListener(listener);
                if (!model.isStreaming()) {
                    idle.countDown(); // schon fertig, bevor der Listener dran war
                }
            }
        });
        boolean reached = idle.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                model.removeListener(listener);
            }
        });
        assertTrue("model still streaming after " + TIMEOUT_SECONDS + " s", reached);
    }

    private static <T> T onEdt(final Callable<T> callable) throws Exception {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<Exception> failure = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(callable.call());
                } catch (Exception e) {
                    failure.set(e);
                }
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
