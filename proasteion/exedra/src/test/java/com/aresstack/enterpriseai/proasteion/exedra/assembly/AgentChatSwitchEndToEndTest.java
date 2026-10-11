package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.AgentBackend;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.sidebar.ChatHistoryRow;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.Closeable;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aresstack.enterpriseai.proasteion.exedra.assembly.AgentModeEndToEndTest.onEdt;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Abnahmeszenario 7 über die ganze Anwendung: Composition Root mit dem eingebauten Recherche-Agenten als echtem
 * Kindprozess (ACP, MCP), {@link ShellAssembly} mit Modus-Pille, Composer, Chat-Drawer und Chat-Historie auf Platte.
 * Zwei Agent-Chats mit verschiedenen Themen entstehen; danach öffnet die Nutzerin den gespeicherten Chat A und dann
 * den gespeicherten Chat B über die Zeilen des Drawers. In B darf nichts aus A ankommen: kein Verlauf, kein Konzept,
 * kein Weidezaun, nichts im Kontext des Agenten.
 */
public class AgentChatSwitchEndToEndTest {

    private static final String TOPIC_A = "IAR";
    private static final String TOPIC_B = "Gleitzeit";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public final ErrorCollector expected = new ErrorCollector();

    private String previousConfig;
    private CompositionRoot root;
    private ShellAssembly.ShellView view;
    private TopicModel script;

    @Before
    public void composeApplication() throws Exception {
        String classPath = System.getProperty("research.agent.classpath");
        assumeTrue("research agent class path not set (run via Gradle)", classPath != null);
        // Chat-Historie und Research-Projekte liegen neben der Konfigurationsdatei: hier im Temp-Verzeichnis
        previousConfig = System.getProperty(AppPaths.CONFIG_PROPERTY);
        System.setProperty(AppPaths.CONFIG_PROPERTY,
                temp.getRoot().toPath().resolve("enterprise-ai-client.properties").toString());

        final SolonMcpServerRuntime mcp = new SolonMcpServerRuntime();
        AgentBackend research = AdapterAssembly.researchAgent(mcp);
        List<String> args = new ArrayList<String>(research.launchSpec().getArgs());
        args.set(args.indexOf("-cp") + 1, classPath);
        AgentBackend backend = new AgentBackend(research.connector(),
                new com.aresstack.enterpriseai.acp.api.AgentLaunchSpec(research.launchSpec().getCommand(), args,
                        null), research.registry(), research.endpoint());
        script = new TopicModel();
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        ApplicationPorts ports = ApplicationPorts.builder()
                .chat(script)
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(new InMemoryKnowledgeIndex())
                .secrets(new UnavailableSecretProvider("Test ohne KeePass"))
                .researchAgent(backend)
                .closing("mcp-server", new Closeable() {
                    @Override
                    public void close() {
                        mcp.shutdown();
                    }
                })
                .build();
        root = CompositionRoot.compose(TestConfigs.withoutAgent(temp.newFolder("index").toPath()), ports,
                SwingUtilities::invokeLater, System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
        assertTrue("eingebauter Recherche-Agent", root.hasAgent() && root.usesBuiltInAgent());
        view = onEdt(() -> ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired()));
    }

    @After
    public void tearDown() {
        if (root != null) {
            root.shutdown().run();
        }
        if (previousConfig == null) {
            System.clearProperty(AppPaths.CONFIG_PROPERTY);
        } else {
            System.setProperty(AppPaths.CONFIG_PROPERTY, previousConfig);
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    @Test
    public void scenario7_openingSavedChatAThenChatBCarriesNoAgentOrResearchContextFromAIntoB() throws Exception {
        final ChatWorkspacePanel workspace = view.workspace();

        // Chat B: Agent-Modus, Thema Gleitzeit
        sendInAgentMode("Thema ist " + TOPIC_B);
        assertTrue("Konzept von B", hasCard(TOPIC_B));
        // „+ Neuer Chat“, dann Chat A: Agent-Modus, Thema IAR
        onEdt(() -> {
            workspace.newChatButton().doClick();
            return null;
        });
        sendInAgentMode("Thema ist " + TOPIC_A);
        assertTrue("Konzept von A", hasCard(TOPIC_A));
        // „+ Neuer Chat“: beide Chats liegen jetzt gespeichert im Drawer
        onEdt(() -> {
            workspace.newChatButton().doClick();
            return null;
        });

        // gespeicherten Chat A öffnen: Verlauf und Agentenkontext von A
        openSavedChat("Thema ist " + TOPIC_A);
        assertTrue(transcript().contains(TOPIC_A));
        expected.checkThat("Chat A öffnet sein Research-Projekt (Konzept IAR)", hasCard(TOPIC_A), is(true));

        // danach gespeicherten Chat B öffnen
        openSavedChat("Thema ist " + TOPIC_B);
        String shown = transcript();
        assertTrue(shown, shown.contains(TOPIC_B));
        assertFalse("Verlauf von A in B: " + shown, shown.contains(TOPIC_A));
        assertFalse("Konzept von A im Projekt von B", hasCard(TOPIC_A));
        assertFalse("Weidezaun/Auftrag von A im Projekt von B: " + root.researchHost().renderedFence(),
                root.researchHost().renderedFence().contains(TOPIC_A));
        expected.checkThat("Chat B öffnet sein Research-Projekt (Konzept Gleitzeit)", hasCard(TOPIC_B), is(true));

        // ein Agent-Turn in B: nichts aus A im Kontext des Agenten (Konzept, Auftrag, Verlauf)
        script.prompts.clear();
        sendInAgentMode("Worum geht es in diesem Chat?");
        assertFalse(script.prompts.isEmpty());
        for (String prompt : script.prompts) {
            assertFalse("Kontext von A im Agenten-Turn von B: " + prompt, prompt.contains(TOPIC_A));
        }
        shown = transcript();
        assertFalse("Antwort in B mit Kontext von A: " + shown, shown.contains(TOPIC_A));
        assertFalse("Konzept von A nach dem Turn in B", hasCard(TOPIC_A));
    }

    @Test
    public void scenario7_switchingFromRunningAgentChatAToSavedChatBCarriesNoContextFromA() throws Exception {
        final ChatWorkspacePanel workspace = view.workspace();
        sendInAgentMode("Thema ist " + TOPIC_B);
        onEdt(() -> {
            workspace.newChatButton().doClick();
            return null;
        });
        // Chat A ist der zuletzt benutzte Agent-Chat; die Nutzerin wechselt direkt in den gespeicherten Chat B
        sendInAgentMode("Thema ist " + TOPIC_A);
        assertTrue("Konzept von A", hasCard(TOPIC_A));
        openSavedChat("Thema ist " + TOPIC_B);
        String shown = transcript();
        assertFalse("Verlauf von A in B: " + shown, shown.contains(TOPIC_A));
        assertFalse("Konzept von A im Projekt von B", hasCard(TOPIC_A));

        script.prompts.clear();
        sendInAgentMode("Worum geht es in diesem Chat?");
        for (String prompt : script.prompts) {
            assertFalse("Kontext von A im Agenten-Turn von B: " + prompt, prompt.contains(TOPIC_A));
        }
        expected.checkThat("Chat B arbeitet in seinem Research-Projekt (Konzept Gleitzeit)", hasCard(TOPIC_B),
                is(true));
    }

    private void sendInAgentMode(final String text) throws Exception {
        onEdt(() -> {
            view.workspace().modes().select(ShellMode.AGENT);
            ChatComposerPanel composer = view.workspace().chatShell().composer();
            composer.editor().setText(text);
            assertTrue("Senden muss möglich sein", composer.sendButton().isEnabled());
            composer.sendButton().doClick();
            return null;
        });
        awaitIdle(view.chatModel());
    }

    private void openSavedChat(final String title) throws Exception {
        onEdt(() -> {
            ChatWorkspacePanel workspace = view.workspace();
            workspace.closeDrawer();
            workspace.openDrawer(); // baut die Chat-Zeilen neu
            List<String> seen = new ArrayList<String>();
            for (ChatHistoryRow row : workspace.chatRows()) {
                seen.add(row.getTitle());
                if (title.equals(row.getTitle()) && !row.isSelected()) {
                    row.open();
                    return null;
                }
            }
            throw new AssertionError("kein gespeicherter Chat " + title + " in " + seen);
        });
        onEdt(() -> null);
    }

    private boolean hasCard(String name) {
        ResearchAgentHost host = root.researchHost();
        ConceptBranchService concept = host.existingConceptService();
        return concept != null && concept.nodeIdAtPath(Arrays.asList(name)) != null;
    }

    private String transcript() throws Exception {
        final ChatShellModel model = view.chatModel();
        return onEdt(() -> {
            StringBuilder text = new StringBuilder();
            for (TranscriptEntry entry : model.getEntries()) {
                text.append(entry.getAuthor()).append(": ").append(entry.getText()).append(" | ")
                        .append(entry.getActivityTitle()).append('\n');
            }
            return text.toString();
        });
    }

    private static void awaitIdle(final ChatShellModel model) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        boolean streaming;
        do {
            Thread.sleep(100L);
            streaming = onEdt(() -> model.isStreaming());
        } while (streaming && System.nanoTime() < deadline);
        assertFalse("agent turn did not end", streaming);
        onEdt(() -> null);
    }

    /**
     * Das Hauptmodell als Skript: sucht nie in Wissensquellen, legt für „Thema ist X“ die Konzeptkarte X an und
     * zeichnet jeden Prompt auf (System und Nutzer), damit sichtbar wird, was der Agent als Kontext mitschickt.
     */
    static final class TopicModel implements ChatCompletionPort {
        private static final Pattern TOPIC = Pattern.compile("Thema ist (\\p{L}+)");
        final List<String> prompts = new CopyOnWriteArrayList<String>();
        private final Set<String> added = new LinkedHashSet<String>();

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            StringBuilder all = new StringBuilder();
            String system = "";
            String user = "";
            for (ChatMessage message : request.messages()) {
                all.append(message.role()).append(": ").append(message.content()).append('\n');
                if (message.role() == ChatRole.SYSTEM && system.isEmpty()) {
                    system = message.content();
                } else if (message.role() == ChatRole.USER) {
                    user = message.content();
                }
            }
            prompts.add(all.toString());
            String answer = "{\"assistantMessage\":\"Notiert.\"}";
            if (system.contains("Entscheide, ob die Nachricht")) {
                answer = "{\"search\": false, \"queries\": []}";
            } else {
                Matcher topic = TOPIC.matcher(user);
                String last = null;
                while (topic.find()) {
                    last = topic.group(1);
                }
                if (last != null && added.add(last)) {
                    answer = "{\"assistantMessage\":\"Ich lege " + last + " an.\",\"conceptAction\":{\"type\":"
                            + "\"add_cards\",\"parent\":[],\"names\":[\"" + last + "\"]}}";
                }
            }
            return new ChatResponse(ChatMessage.assistant(answer), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "fake");
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            // der normale Chat ist hier nicht Gegenstand
            throw new UnsupportedOperationException("chat mode is not part of this scenario");
        }
    }
}
