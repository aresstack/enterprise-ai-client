package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.settings.SettingsService;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import java.util.Properties;
import com.aresstack.enterpriseai.astu.acropolis.attachment.SearchAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.attachment.ReadAttachmentTool;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ToolOverviewEntry;
import com.aresstack.enterpriseai.proasteion.exedra.settings.CatalogToolOverview;
import java.util.Map;
import java.util.LinkedHashMap;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.composition.AppHelpMcpTools;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.StartupNotices;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalResult;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.io.Closeable;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Der ganze Graph mit Fakes statt Adaptern, headless: Konfiguration → Ports → Use Cases → Shell → Shutdown.
 * Kein Netz, kein Fenster, keine Datei außer dem leeren Index-Verzeichnis der Konfiguration.
 */
public class ApplicationCompositionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final FakeChatCompletionPort chatPort = new FakeChatCompletionPort(0L);
    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Urlaub", "Urlaubsregelung", "Urlaub wird im Portal beantragt. Resturlaub verfällt am 31. März.")
            .add("Reisen", "Reisekosten", "Reisekosten werden über das Formular RK-1 abgerechnet.")
            // Kennt die Quelle, liegt aber außerhalb des Crawl-Umfangs (kein Startpunkt, nicht verlinkt):
            .add("Geheim", "Interner Entwurf", "Entwurfstext, der nicht in der Wissensbasis landet.");
    private final AtomicBoolean indexClosed = new AtomicBoolean();
    private final FakeAgentBackend agent = new FakeAgentBackend();

    private ApplicationPorts ports(boolean withAgent) {
        ApplicationPorts.Builder builder = ApplicationPorts.builder()
                .chat(chatPort)
                .embeddings(embeddings, embeddings.modelIdentity())
                .index(index)
                .sources(new KnowledgeSourceCatalog(Arrays.asList(
                        new KnowledgeSourceRegistration(wiki, SourceScope.of("Urlaub", "Reisen")))))
                .secrets(new UnavailableSecretProvider("Test ohne KeePass"))
                .closing("knowledge-index", new Closeable() {
                    @Override
                    public void close() {
                        indexClosed.set(true);
                    }
                });
        if (withAgent) {
            // Wie in AdapterAssembly: der MCP-Server wird vor dem Index geschlossen.
            builder.agent(agent.backend()).closing("mcp-server", new Closeable() {
                @Override
                public void close() {
                    agent.registry.shutdown();
                }
            });
        }
        return builder.build();
    }

    private CompositionRoot compose(boolean withAgent) {
        AppConfig config = withAgent ? TestConfigs.withAgent(temp.getRoot().toPath())
                : TestConfigs.withoutAgent(temp.getRoot().toPath());
        return CompositionRoot.compose(config, ports(withAgent), SwingUtilities::invokeLater,
                System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
    }

    /** Füllt den Index ausdrücklich (wie „Alles indexieren“); beim Start indexiert nichts mehr. */
    private static void indexAll(CompositionRoot root) {
        for (KnowledgeSourceRegistration registration : root.ports().sources().registrations()) {
            assertTrue(root.indexing().indexSource(registration.port(), registration.scope(), null).isComplete());
        }
    }

    @Test
    public void searchFillsTheIndexAndRetrievalFindsIt() throws Exception {
        CompositionRoot root = compose(false);
        root.startBackgroundWork();
        assertTrue("beim Start wird nichts indexiert", root.retrieval().retrieve("Resturlaub").isEmpty());
        root.searchIndexing().searchAndIndex(root.ports().sources(), "Resturlaub", 4, 4);

        RetrievalResult result = root.retrieval().retrieve("Wann verfällt der Resturlaub?");
        assertFalse(result.isEmpty());
        assertEquals("Urlaubsregelung", result.hits().get(0).resource().title());
        assertEquals(5, root.retrieval().settings().maxResults());
    }

    @Test
    public void chatRoundTripThroughTheShell() throws Exception {
        final CompositionRoot root = compose(false);
        chatPort.enqueueAnswer("Hallo ", "aus dem Fake.");
        final AtomicReference<ShellAssembly.ShellView> view = new AtomicReference<ShellAssembly.ShellView>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                view.set(ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                        BubblePalette.windowsPhoneInspired()));
                view.get().chatActions().sendRequested("Hi", false);
            }
        });
        assertNotNull("ohne Agent-Kommando der eingebaute Recherche-Agent", view.get().agent());
        assertFalse(view.get().workspace().chatShell() == null);
        TranscriptEntry answer = awaitAnswer(view.get());
        assertEquals("Hallo aus dem Fake.", answer.getText());
        assertEquals("Antworte kurz.", chatPort.lastRequest().messages().get(0).content());
        assertEquals(1, root.chatService().conversationIds().size());
        assertFalse(answer.hasSources());

        // „+ Neuer Chat“: neue Unterhaltung, die alte verschwindet aus dem ChatService (sonst sammelt er Verläufe)
        final ChatConversationId first = view.get().conversation();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                view.get().workspace().newChatButton().doClick();
            }
        });
        assertFalse(first.equals(view.get().conversation()));
        assertEquals(Collections.singletonList(view.get().conversation()), root.chatService().conversationIds());
        assertTrue(view.get().chatModel().getEntries().isEmpty());
    }

    @Test
    public void ragQuestionCarriesSourcesAndContextAfterIndexing() throws Exception {
        final CompositionRoot root = compose(false);
        indexAll(root);
        // Erst die Vorprüfung (Nebenanfrage): die Frage braucht Wissen und wird mit sich selbst gesucht.
        chatPort.enqueueAnswer("{\"search\": true, \"query\": \"Wann verfällt der Resturlaub?\"}");
        // Ein Bewerter-Aufruf je Kandidatenrunde (Nebenanfrage): der beste Treffer reicht, dann kommt die Antwort.
        chatPort.enqueueAnswer("{\"ranked\": [{\"id\": \"c1\", \"relevant\": true}], \"minimumEvidence\": [\"c1\"], "
                + "\"sufficient\": true, \"missing\": \"\", \"nextQuery\": \"\"}");
        chatPort.enqueueAnswer("Am 31. März.");
        final AtomicReference<ShellAssembly.ShellView> view = new AtomicReference<ShellAssembly.ShellView>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                view.set(ShellAssembly.createShell(root, ComicPalette.defaultPalette(),
                        BubblePalette.windowsPhoneInspired()));
                view.get().chatActions().sendRequested("Wann verfällt der Resturlaub?", true);
            }
        });
        TranscriptEntry answer = awaitAnswer(view.get());
        assertEquals("Am 31. März.", answer.getText());
        // Ein schnelles Backend ist vor den Quellen fertig; die Binding hängt sie nachträglich an (AP22).
        answer = awaitSources(view.get());
        assertEquals("Urlaubsregelung", answer.getSources().get(0).getTitle());
        String system = chatPort.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("Antworte kurz."));
        assertTrue("Kontextblock im System-Anteil: " + system, system.contains("Resturlaub"));
        assertTrue(system, system.contains("- Wann verfällt der Resturlaub?"));
        assertEquals("Vorprüfung, Bewertung und Antwort", 3, chatPort.requests().size());
        assertEquals("die Faktfrage endet nach der ersten ausreichenden Quelle", 1, answer.getSources().size());
    }

    @Test
    public void agentModeGetsTheKnowledgeToolsFromAp20() throws Exception {
        CompositionRoot root = compose(true);
        assertTrue(root.hasAgent());
        assertNotNull(root.agentService());
        List<String> names = new ArrayList<String>();
        for (McpToolContribution tool : root.agentTools()) {
            names.add(tool.getName());
        }
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ), names);
        assertEquals(3, root.knowledgeTools().settings().defaultMaxResults());
        final AtomicReference<ShellAssembly.ShellView> view = new AtomicReference<ShellAssembly.ShellView>();
        final CompositionRoot composed = root;
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                view.set(ShellAssembly.createShell(composed, ComicPalette.defaultPalette(),
                        BubblePalette.windowsPhoneInspired()));
            }
        });
        assertNotNull(view.get().agent());
        assertSame(view.get().workspace().chatShell(), view.get().agent().shell()); // ein Chat, zwei Modi
    }

    @Test
    public void toolsTabShowsTheCentralRegistryWithCorrectAvailability() throws Exception {
        CompositionRoot root = compose(true);
        Map<String, ToolOverviewEntry> shown = new LinkedHashMap<String, ToolOverviewEntry>();
        for (ToolOverviewEntry tool : new CatalogToolOverview(root.toolCatalog()).tools()) {
            assertNull("doppelt: " + tool.name(), shown.put(tool.name(), tool));
            assertFalse(tool.name(), tool.description().isEmpty());
            assertFalse(tool.name(), tool.origin().isEmpty());
        }
        // Wissenswerkzeuge: Name und Beschreibung aus der McpToolContribution, Verfügbarkeit aus der Registrierung.
        assertEquals("Chat + Agent", shown.get(KnowledgeMcpTools.KNOWLEDGE_SEARCH).availability());
        assertEquals("Chat + Agent", shown.get(KnowledgeMcpTools.KNOWLEDGE_READ).availability());
        assertEquals(root.knowledgeTools().knowledgeRead().getDescription(),
                shown.get(KnowledgeMcpTools.KNOWLEDGE_READ).description());
        assertTrue(shown.get(KnowledgeMcpTools.KNOWLEDGE_READ).origin().contains("KnowledgeMcpTools"));
        // Anhang-Werkzeuge nur im Chat, Recherche-Steuerung nur am eingebauten Agenten.
        assertEquals("Chat", shown.get(ReadAttachmentTool.NAME).availability());
        assertEquals("Chat", shown.get(SearchAttachmentTool.NAME).availability());
        assertEquals("Recherche-Agent", shown.get("research_status").availability());
        assertEquals("Recherche-Agent", shown.get("main_model_complete").availability());
        // Die Anzeige und die tatsächlich angebotenen Sätze stammen aus derselben Registry.
        List<String> agent = new ArrayList<String>();
        for (McpToolContribution tool : root.agentTools()) {
            agent.add(tool.getName());
        }
        List<String> shownForAgent = new ArrayList<String>();
        for (ToolOverviewEntry tool : shown.values()) {
            if (tool.availability().contains("Agent") && !tool.availability().contains("Recherche-Agent")) {
                shownForAgent.add(tool.name());
            }
        }
        assertEquals(shownForAgent, agent);
        List<String> chat = new ArrayList<String>();
        // Der Chat bekommt den Wissens-Werkzeugsatz aus derselben Registry (Bug 1: auch knowledge_search).
        for (AiTool tool : root.knowledgeChatTools()) {
            chat.add(tool.name());
        }
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ), chat);
    }

    @Test
    public void disabledToolsLeaveChatAndAgentAndComeBackWithoutRestart() throws Exception {
        final AtomicReference<Properties> file = new AtomicReference<Properties>(
                TestConfigs.base(temp.getRoot().toPath()));
        file.get().setProperty("tools.disabled", KnowledgeMcpTools.KNOWLEDGE_SEARCH + "," + ReadAttachmentTool.NAME);
        SettingsService settings = new SettingsService(AppConfigLoader.fromProperties(file.get()),
                () -> AppConfigLoader.fromProperties(file.get()));
        CompositionRoot root = CompositionRoot.compose(settings, ports(false), SwingUtilities::invokeLater,
                System::currentTimeMillis, ZoneId.of("Europe/Berlin"));

        // Abgeschaltet: weder Chat noch Agent bekommen es, die Registry zeigt es weiter (als aus).
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ), names(root.agentTools()));
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ), chatNames(root.knowledgeChatTools()));
        assertFalse(root.toolCatalog().isEnabled(ReadAttachmentTool.NAME));
        assertTrue(root.toolCatalog().isEnabled(SearchAttachmentTool.NAME));
        Map<String, ToolOverviewEntry> shown = new LinkedHashMap<String, ToolOverviewEntry>();
        for (ToolOverviewEntry tool : new CatalogToolOverview(root.toolCatalog()).tools()) {
            shown.put(tool.name(), tool);
        }
        assertFalse(shown.get(KnowledgeMcpTools.KNOWLEDGE_SEARCH).enabled());
        assertFalse(shown.get(ReadAttachmentTool.NAME).enabled());
        assertTrue(shown.get(KnowledgeMcpTools.KNOWLEDGE_READ).enabled());
        assertTrue(shown.get("research_status").enabled()); // nicht genannt = aktiv

        // knowledge_read zusätzlich aus, knowledge_search wieder an: gilt nach dem Neuladen, ohne neue Komposition.
        file.get().setProperty("tools.disabled", KnowledgeMcpTools.KNOWLEDGE_READ);
        settings.reload();
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ), names(root.agentTools()));
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ),
                chatNames(root.knowledgeChatTools()));
        assertTrue(root.toolCatalog().isEnabled(ReadAttachmentTool.NAME));

        file.get().remove("tools.disabled");
        settings.reload();
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ),
                names(root.agentTools()));
        assertEquals(Arrays.asList(KnowledgeMcpTools.KNOWLEDGE_SEARCH, KnowledgeMcpTools.KNOWLEDGE_READ,
                KnowledgeMcpTools.KNOWLEDGE_ATTACHMENTS, AppHelpMcpTools.APP_HELP_SEARCH, AppHelpMcpTools.APP_HELP_READ),
                chatNames(root.knowledgeChatTools()));
    }

    private static List<String> names(List<McpToolContribution> tools) {
        List<String> names = new ArrayList<String>();
        for (McpToolContribution tool : tools) {
            names.add(tool.getName());
        }
        return names;
    }

    private static List<String> chatNames(List<AiTool> tools) {
        List<String> names = new ArrayList<String>();
        for (AiTool tool : tools) {
            names.add(tool.name());
        }
        return names;
    }

    @Test
    public void knowledgeReadLoadsAndIndexesOnDemand() throws Exception {
        CompositionRoot root = compose(false);
        KnowledgeResourceId page = wiki.idOf("Urlaub");
        McpToolResult read = invokeTool(root, KnowledgeMcpTools.KNOWLEDGE_READ, page.value());
        assertFalse(read.getText(), read.isError());
        assertTrue(read.getText(), read.getText().contains("Resturlaub"));
        boolean indexed = false;
        for (long deadline = System.currentTimeMillis() + 10_000L; !indexed && System.currentTimeMillis() < deadline; ) {
            indexed = root.ports().index().resourceIds(root.indexing().space(), wiki.sourceId()).contains(page);
            if (!indexed) {
                Thread.sleep(20); // indexiert wird im eigenen Index-Thread
            }
        }
        assertTrue("gelesen heißt indexiert", indexed);
        root.shutdown().run();
    }

    private static McpToolResult invokeTool(CompositionRoot root, String toolName, String id) {
        for (McpToolContribution tool : root.knowledgeTools().contributions()) {
            if (tool.getName().equals(toolName)) {
                return tool.getHandler().invoke(new McpToolCall(toolName,
                        java.util.Collections.<String, Object>singletonMap("id", id)));
            }
        }
        throw new AssertionError("Werkzeug nicht registriert: " + toolName);
    }

    @Test
    public void shutdownRunsInOrderOnceAndClosesEverything() throws Exception {
        CompositionRoot root = compose(true);
        root.startBackgroundWork();
        root.shutdown().run();
        assertEquals(Arrays.asList("stop-prefetch", "cancel-chat-turns", "end-agent-mode", "stop-indexing", "close-ports",
                "stop-executors"), root.shutdown().executedSteps());
        assertTrue(indexClosed.get());
        assertTrue(root.knowledgeTools().isShutdown());
        Thread hook = root.shutdown().asShutdownHook();
        hook.start();
        hook.join();
        assertEquals(6, root.shutdown().executedSteps().size());
        try {
            agent.registry.registerEndpoint(agent.endpoint);
            assertTrue("Registry nach Shutdown nimmt keine Endpoints mehr an", false);
        } catch (RuntimeException expected) {
            // ok
        }
    }

    @Test
    public void startupNoticesExplainMissingKeePass() {
        List<String> notices = StartupNotices.of(TestConfigs.withoutAgent(temp.getRoot().toPath()));
        assertEquals(2, notices.size());
        assertTrue(notices.get(0), notices.get(0).contains("KeePassRPC ist deaktiviert"));
        assertTrue(notices.get(0), notices.get(0).contains("API-Key"));
        assertTrue(notices.get(1), notices.get(1).contains("chat.apiKeyRef"));
    }

    private static TranscriptEntry awaitSources(final ShellAssembly.ShellView view) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        TranscriptEntry answer = null;
        while (System.currentTimeMillis() < deadline) {
            answer = awaitAnswer(view);
            if (answer.hasSources()) {
                return answer;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("keine Quellen an der Antwort: " + answer);
    }

    private static TranscriptEntry awaitAnswer(final ShellAssembly.ShellView view) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        final AtomicReference<TranscriptEntry> last = new AtomicReference<TranscriptEntry>();
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    for (TranscriptEntry entry : view.chatModel().getEntries()) {
                        if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                            last.set(entry);
                        }
                    }
                }
            });
            TranscriptEntry entry = last.get();
            if (entry != null && entry.getAuthor() == TranscriptEntry.Author.ASSISTANT
                    && entry.getState() == TranscriptEntry.State.COMPLETE) {
                return entry;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("keine vollständige Antwort: " + last.get());
    }
}
