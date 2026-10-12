package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.proasteion.application.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.proasteion.application.agent.ChatPortMainModelChat;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchControlTools;
import com.aresstack.enterpriseai.proasteion.application.agent.PhaseToolSetController;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.AgentBackend;
import com.aresstack.enterpriseai.proasteion.exedra.agent.AgentServiceBinding;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.FileConceptStore;
import com.aresstack.enterpriseai.research.store.FileResearchScopeDraftStore;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Akzeptanzszenario „Agent wie askai arch“ über die echte Prozessgrenze: der eingebaute Recherche-Agent läuft als
 * Kindprozess über ACP, der Host bietet seine Werkzeuge über den echten MCP-Endpoint, die Wissensquelle ist eine
 * Fake-Quelle (knowledge_search/knowledge_read), das Hauptmodell ein Skript. Der Verlauf ist das ChatShellModel des
 * Agent-Modus, wie die Oberfläche es zeichnet.
 */
public class AgentAskaiAcceptanceTest {

    private static final String QUESTION = "Kannst du mir was über IAR aus Confluence finden?";
    private static final String PAGE = "confluence:wiki/page/1001";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private SolonMcpServerRuntime mcp;
    private ExecutorService starts;
    private AgentService service;
    private final List<String> knowledgeCalls = new CopyOnWriteArrayList<String>();

    @After
    public void tearDown() {
        if (service != null) {
            service.close();
        }
        if (starts != null) {
            starts.shutdownNow();
        }
        if (mcp != null) {
            mcp.shutdown();
        }
    }

    @AfterClass
    public static void stopSolon() {
        SolonMcpServerRuntime.stopSharedServer();
    }

    /** Das Hauptmodell als Skript: Suchplan, Konzeptkarte, Antwort aus den Auszügen, sonst eine kurze Nachricht. */
    private static final class ScriptedModel implements ChatCompletionPort {
        final List<String> systems = new CopyOnWriteArrayList<String>();
        private boolean conceptAdded;

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            String system = "";
            String user = "";
            for (ChatMessage message : request.messages()) {
                if (message.role() == ChatRole.SYSTEM && system.isEmpty()) {
                    system = message.content();
                } else if (message.role() == ChatRole.USER) {
                    user = message.content();
                }
            }
            systems.add(system);
            String answer;
            if (system.contains("Antworte NUR mit JSON")) {
                answer = "{\"search\": true, \"queries\": [\"IAR\"]}";
            } else if (system.contains("Beantworte die Frage des Nutzers")) {
                answer = "IAR bündelt die Auswertung der Anträge. [P1] Zuständig ist das Team Auswertung. [P1]";
            } else if (!conceptAdded && user.contains("IAR")) {
                conceptAdded = true;
                answer = "{\"assistantMessage\":\"Ich lege IAR an.\",\"conceptAction\":{\"type\":\"add_cards\","
                        + "\"parent\":[],\"names\":[\"IAR\"]}}";
            } else {
                answer = "{\"assistantMessage\":\"Notiert.\"}";
            }
            return new ChatResponse(ChatMessage.assistant(answer), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "fake");
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            throw new UnsupportedOperationException("the agent asks the host model without streaming");
        }
    }

    private List<McpToolContribution> fakeSource() {
        McpToolContribution search = McpToolContribution.of("knowledge_search", "Volltextsuche", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                knowledgeCalls.add("knowledge_search " + call.getString("query"));
                return McpToolResult.ok("[1] IAR Übersicht\nid: " + PAGE + "\nurl: \n"
                        + "IAR bündelt die Auswertung der Anträge.");
            }
        }, McpToolParameter.string("query", true, "Suchbegriffe"), McpToolParameter.integer("limit", false, "max"));
        McpToolContribution read = McpToolContribution.of("knowledge_read", "Seite lesen", new McpToolHandler() {
            @Override
            public McpToolResult invoke(McpToolCall call) {
                knowledgeCalls.add("knowledge_read " + call.getString("id"));
                return McpToolResult.ok("IAR Übersicht\n\nIAR bündelt die Auswertung der Anträge.\n\n"
                        + "Zuständig ist das Team Auswertung; der Pilot läuft im Frühjahr.");
            }
        }, McpToolParameter.string("id", true, "Dokument-ID"));
        return Arrays.asList(search, read);
    }

    @Test
    public void directQuestionInAgentModeSearchesShowsCardNotesSourcesAndAnswers() throws Exception {
        String classPath = System.getProperty("research.agent.classpath");
        assumeTrue("research agent class path not set (run via Gradle)", classPath != null);
        mcp = new SolonMcpServerRuntime();
        AgentBackend backend = AdapterAssembly.researchAgent(mcp);
        List<String> args = new ArrayList<String>(backend.launchSpec().getArgs());
        args.set(args.indexOf("-cp") + 1, classPath);

        final ResearchAgentHost host = new ResearchAgentHost(folder.newFolder("research"),
                FileResearchScopeDraftStore::new, directory -> new ConceptBranchService(new FileConceptStore(directory)));
        final ChatPortMainModelChat model = new ChatPortMainModelChat(new ScriptedModel(), ChatOptions.defaults());
        // wie die Composition Root: der Werkzeugsatz folgt dem Zustand des Hosts
        final PhaseToolSetController toolSet = new PhaseToolSetController(() -> {
            List<McpToolContribution> tools = new ArrayList<McpToolContribution>(
                    ResearchControlTools.toolsFor(host, model));
            tools.addAll(fakeSource());
            return tools;
        });
        host.addStateListener(toolSet::refresh);
        AcpAgentLauncher launcher = AcpAgentLauncher.withPhaseToolSet(backend.connector(),
                new AgentLaunchSpec(backend.launchSpec().getCommand(), args, null), backend.registry(),
                backend.endpoint(), toolSet);
        starts = Executors.newSingleThreadExecutor();
        service = new AgentService(launcher, starts);

        // 1. Agent-Modus: der Verlauf des Agent-Modus mit seiner Bindung
        final AtomicReference<ChatShellModel> chat = new AtomicReference<ChatShellModel>();
        final AgentModeEndToEndTest.CardRecorder cards = new AgentModeEndToEndTest.CardRecorder();
        SwingUtilities.invokeAndWait(() -> {
            chat.set(new ChatShellModel(System::currentTimeMillis));
            chat.get().addListener(cards);
            // 2. die Frage, ohne Konzept vorab
            new AgentServiceBinding(service, chat.get(), SwingUtilities::invokeLater).sendRequested(QUESTION, false);
        });
        awaitIdle(chat.get());
        List<TranscriptEntry> entries = snapshot(chat.get());

        // 3. das Konzept wird nebenbei ergänzt
        assertNotNull("Konzeptkarte IAR", host.existingConceptService().nodeIdAtPath(Arrays.asList("IAR")));

        // 4. die Recherche startet sichtbar: als Gedanke, nicht als Antwort; danach bleiben Frage und Antwort
        String text = allText(entries);
        assertTrue(cards.all().toString(), AgentModeEndToEndTest.anyContains(cards.all(),
                "Ich suche in den aktivierten Wissensquellen nach"));
        assertFalse(text, text.contains("Ich suche in den aktivierten Wissensquellen nach"));
        AgentModeEndToEndTest.assertOnlyQuestionAndAnswer(entries, QUESTION);
        List<TranscriptEntry> clouds = cards.seen();

        // 5. knowledge_search und knowledge_read laufen gegen die Quelle
        assertTrue(knowledgeCalls.toString(), knowledgeCalls.contains("knowledge_search IAR"));
        assertTrue(knowledgeCalls.toString(), knowledgeCalls.contains("knowledge_read " + PAGE));

        // 6. Werkzeugaufrufe und Zwischenschritte stehen im Verlauf, abgeschlossen mit Ergebnis
        TranscriptEntry card = activity(clouds, "Recherche in Confluence");
        assertEquals("abgeschlossen: 1 Seite gelesen, 1 Treffer, 1 Quelle übernommen", card.getActivitySummary());
        // die Art „research“ übersteht die ACP-Leitung (kind: other + _meta), sonst zeichnet der Verlauf keine Karte
        assertEquals("research", card.getActivityKind());
        // neben der Karte: Suche und Lesen als Werkzeugwolken, das Notieren zählt die Karte
        assertTrue(card.getText(), card.getText().contains("IAR"));
        boolean search = false;
        boolean read = false;
        for (TranscriptEntry entry : clouds) {
            String title = entry.getActivityTitle();
            search |= "Suche in den Wissensquellen".equals(title);
            read |= "Lese Seite".equals(title);
            assertFalse(title, "Quelle notieren".equals(title));
        }
        assertTrue("Suchwolke", search);
        assertTrue("Lesewolke", read);
        assertTrue("Denkschritte als grüne Wolke", activity(clouds, "Denke nach").isThinking());

        // 7. die gelesene Quelle ist im Projekt übernommen
        String sources = call(toolSet.current(), "research_sources");
        assertTrue(sources, sources.contains(PAGE) && sources.contains("IAR Übersicht"));

        // 8. inhaltliche Antwort mit Beleg
        assertTrue(text, text.contains("IAR bündelt die Auswertung der Anträge. [1]"));
        assertTrue(text, text.contains("[1] IAR Übersicht"));

        // 9. keine falsche Behauptung, es gebe keinen Zugriff
        assertFalse(text, text.toLowerCase(Locale.ROOT).contains("keinen zugriff"));
        assertFalse(text, text.toLowerCase(Locale.ROOT).contains("kein zugriff"));

        // 10. keine Mojibake in Nachrichten, Titeln, Eingaben und Ergebnissen
        for (TranscriptEntry entry : entries) {
            String all = entry.getText() + entry.getActivityTitle() + entry.getActivitySummary();
            assertFalse(all, all.contains("Ã") || all.contains("â€"));
        }
        assertTrue(text, text.contains("Anträge") && text.contains("Übersicht"));
    }

    private static String call(List<McpToolContribution> tools, String name) {
        for (McpToolContribution tool : tools) {
            if (tool.getName().equals(name)) {
                return tool.getHandler().invoke(new McpToolCall(name, new java.util.HashMap<String, Object>()))
                        .getText();
            }
        }
        throw new AssertionError("tool " + name + " missing");
    }

    private static TranscriptEntry activity(List<TranscriptEntry> entries, String title) {
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY && title.equals(entry.getActivityTitle())) {
                return entry;
            }
        }
        StringBuilder seen = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            seen.append(entry.getAuthor()).append(':').append(entry.getActivityTitle()).append(' ');
        }
        throw new AssertionError("keine Wolke " + title + " in " + seen);
    }

    private static String allText(List<TranscriptEntry> entries) {
        StringBuilder text = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                text.append(entry.getText()).append('\n');
            }
        }
        return text.toString();
    }

    private static List<TranscriptEntry> snapshot(final ChatShellModel model) throws Exception {
        final AtomicReference<List<TranscriptEntry>> entries = new AtomicReference<List<TranscriptEntry>>();
        SwingUtilities.invokeAndWait(() -> entries.set(new ArrayList<TranscriptEntry>(model.getEntries())));
        return entries.get();
    }

    private static void awaitIdle(final ChatShellModel model) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(180);
        final boolean[] streaming = new boolean[1];
        do {
            Thread.sleep(100L);
            SwingUtilities.invokeAndWait(() -> streaming[0] = model.isStreaming());
        } while (streaming[0] && System.nanoTime() < deadline);
        assertFalse("agent turn did not end", streaming[0]);
    }
}
