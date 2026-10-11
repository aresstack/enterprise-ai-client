package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.application.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.app.agent.AgentModeAssembly;
import com.aresstack.enterpriseai.app.ui.research.ResearchOutOfScopeSky;
import com.aresstack.enterpriseai.app.agent.ModeSwitchingChatActions;
import com.aresstack.enterpriseai.app.agent.AgentServiceBinding;
import com.aresstack.enterpriseai.app.agent.AgentModelChoice;
import com.aresstack.enterpriseai.app.agent.ResearchConceptViewAssembly;
import com.aresstack.enterpriseai.app.chat.ChatHistoryBinding;
import com.aresstack.enterpriseai.app.chat.DocumentAttachmentTextExtractor;
import com.aresstack.enterpriseai.app.chat.FileAttachmentStore;
import com.aresstack.enterpriseai.app.chat.FileChatHistoryStore;
import com.aresstack.enterpriseai.app.chat.RagChatBinding;
import com.aresstack.enterpriseai.app.chat.ToolSupport;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.config.ModelsConfig;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourcesController;
import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.app.ui.workspace.SavedChatItem;
import com.aresstack.enterpriseai.app.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.app.ui.workspace.WorkspaceActions;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.history.ChatRecord;
import com.aresstack.enterpriseai.application.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.document.tika.DocumentExtraction;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.security.api.SecretEntry;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Baut die Oberfläche über dem Graphen aus {@link CompositionRoot}: die Arbeitsfläche
 * ({@link ChatWorkspacePanel}: Hamburger, Modus-Pille, Drawer) mit der Chat-Ansicht samt
 * {@link RagChatBinding} (AP22: RAG-Schalter, Quellen, Hinweise) und Statuszeile der Wissensbasis, dazu, falls
 * konfiguriert, der Agent-Ansicht (AP21). „+ Neuer Chat“ eröffnet im Chat-Modus eine neue Unterhaltung am
 * {@link ChatService}, schließt die bisherige dort und leert das Transkript; im Agent-Modus beendet es die
 * ACP-Session samt Agentenprozess
 * ({@link AgentService#endSession()}), damit der nächste Auftrag ohne den alten Kontext startet. Die Chat-Historie
 * ({@link ChatHistoryBinding}) speichert jeden Chat samt Anhängen unter {@code chats/} neben der
 * Konfigurationsdatei; der Drawer listet, öffnet und löscht die gespeicherten Chats. Das Zahnrad reicht
 * die Composition Root als {@link WorkspaceActions#settingsRequested()} an den Einstellungen-Dialog weiter.
 * {@link #createShell} läuft auch headless (Tests); nur {@link #createFrame} braucht ein Display.
 */
public final class ShellAssembly {

    /** Ordner der gespeicherten Chats und ihrer Anhänge, neben der Konfigurationsdatei. */
    static final String CHATS_DIRECTORY = "chats";
    /** Drawer-Reiter des Konzeptpapiers im Agent-Modus (askai: Artefakt-Ansicht „Concept“). */
    static final String CONCEPT_TAB = "Konzept";

    private ShellAssembly() {
    }

    /** Muss auf dem EDT laufen (auch headless möglich). Das Zahnrad ist ohne {@link ShellView#setSettingsAction} stumm. */
    public static ShellView createShell(CompositionRoot root, ComicPalette palette, BubblePalette bubbles) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("createShell must run on the event dispatch thread");
        }
        final ChatService chatService = root.chatService();
        // Einstellungen frisch lesen (Funktion statt Variable): ein geänderter System-Prompt gilt ohne Neustart.
        final java.util.function.Supplier<String> systemPrompt = root.settings().get(c -> c.chat().systemPrompt());
        ChatConversationId conversation = chatService.openConversation(systemPrompt.get());
        final ChatShellModel chatModel = new ChatShellModel(root.clock());
        final RagChatBinding chatActions = new RagChatBinding(root.ragChat(), conversation, chatModel,
                root.uiExecutor(), root.workExecutor(), root.zone(), root.sourceSelection());
        // Chat-Historie neben der Konfigurationsdatei: chats/<chatId>.json, Anhänge im Ordner chats/<chatId>/.
        final Path chatsDirectory = AppPaths.configFile().toAbsolutePath().getParent().resolve(CHATS_DIRECTORY);
        if (root.ports().responses() != null) {
            // Tool-Calling und Dateianhänge: /responses, Ablage im Ordner des Chats, Text über document-tika.
            chatActions.enableTools(new ToolSupport(root.ports().responses(),
                    new FileAttachmentStore(chatsDirectory),
                    new DocumentAttachmentTextExtractor(DocumentExtraction.detector(), DocumentExtraction.registry()),
                    root.config().chat().toolsEnabled()));
        }
        final ChatHistoryBinding history = new ChatHistoryBinding(new FileChatHistoryStore(chatsDirectory),
                chatService, chatActions, chatModel, systemPrompt, root.clock());
        final AgentModeAssembly.AgentView agent;
        final AgentService agentService;
        final ShellModeModel modes = new ShellModeModel(root.hasAgent());
        final ChatShellPanel chatShell;
        if (root.hasAgent()) {
            // Ein Chat, zwei Fähigkeiten (askai arch): die Modus-Pille schaltet nur Verhalten und Erscheinung um.
            agentService = root.agentService();
            AgentServiceBinding agentBinding = AgentModeAssembly.sharedBinding(agentService, chatModel,
                    root.uiExecutor(), root.agentModelChoice());
            // Anhänge auch im Agent-Modus: ihr Text geht mit der Nachricht an den Agenten.
            agentBinding.enableAttachments(new DocumentAttachmentTextExtractor(DocumentExtraction.detector(),
                    DocumentExtraction.registry()), root.workExecutor());
            chatShell = new ChatShellPanel(chatModel,
                    new ModeSwitchingChatActions(chatActions, agentBinding, modes, chatModel),
                    root.knowledgeStatus(), palette, bubbles);
            agent = AgentModeAssembly.sharedView(chatShell, agentBinding);
            if (root.researchHost() != null) {
                final ResearchOutOfScopeSky sky = AgentModeAssembly.attachResearchSky(agent, root.researchHost(),
                        root.uiExecutor());
                // Der Wolkenhimmel gehört zur Erscheinung des Agent-Modus.
                final ShellModeModel.Listener skyByMode = mode -> {
                    if (mode == ShellMode.AGENT && root.usesBuiltInAgent()) {
                        chatShell.transcript().setTranscriptSkyAccessory(sky);
                    } else {
                        chatShell.transcript().clearTranscriptSkyAccessory();
                    }
                };
                modes.addListener(skyByMode);
                skyByMode.modeChanged(modes.getMode());
            }
        } else {
            agentService = null;
            agent = null;
            chatShell = new ChatShellPanel(chatModel, chatActions, root.knowledgeStatus(), palette, bubbles);
        }
        chatShell.composer().setModelName(root.config().chat().model());
        ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, null, palette);
        workspace.setWindowTitle(root.config().windowTitle());
        // Beobachter auf dem Settings-Service: was schon angezeigt wird, übernimmt gespeicherte Werte sofort.
        final ChatShellPanel shell = chatShell;
        final ChatWorkspacePanel titled = workspace;
        root.settings().addListener((previous, current) -> root.uiExecutor().execute(() -> {
            chatService.setSystemPrompt(chatActions.conversationId(), current.chat().systemPrompt());
            shell.composer().setModelName(current.chat().model());
            titled.setWindowTitle(current.windowTitle());
        }));
        if (agent != null && root.researchHost() != null) {
            // askai: die Artefakt-Ansicht „Konzept“ (Konzeptbaum mit Inseln) als Drawer-Reiter im Agent-Modus
            workspace.addAgentTab(CONCEPT_TAB, ResearchConceptViewAssembly.create(root.researchHost(),
                    root.uiExecutor()));
        }
        final KnowledgeSourcesController sources = knowledgeSources(root);
        workspace.setKnowledgeSourceActions(sources);
        final ChatWorkspacePanel drawer = workspace;
        // Kein RAG-Schalter (wie askai arch): gesucht wird, sobald Wissensquellen angebunden sind; welche, sagen
        // die Häkchen im Reiter „Wissensquellen“. Nur konfigurierte, aber nicht angebundene Quellen schalten RAG
        // nicht ein (sonst würde ungefiltert im ganzen Index gesucht).
        final KnowledgeSourceSelection selection = root.sourceSelection();
        sources.attach(items -> {
            drawer.setKnowledgeSources(items);
            chatModel.setRagEnabled(!items.isEmpty() && selection.isRestricted());
        });
        final ShellView view = new ShellView(workspace, chatModel, chatActions, agent, sources, chatShell.composer());
        chatShell.composer().setModelAction(() -> {
            Runnable action = view.settingsAction();
            if (action != null) {
                action.run();
            }
        });
        // Löschen wie askai arch: bis zum Beenden nur vorgemerkt (↩ holt den Chat zurück), erst dann samt Anhängen weg.
        final Set<String> pendingDeletes = Collections.synchronizedSet(new LinkedHashSet<String>());
        root.shutdown().then("chat-history-deletes", () -> {
            synchronized (pendingDeletes) {
                for (String chatId : pendingDeletes) {
                    history.delete(chatId);
                }
                pendingDeletes.clear();
            }
        });
        workspace.setActions(new WorkspaceActions() {
            @Override
            public void newChatRequested(ShellMode mode) {
                // „Neuer Chat“ gilt unabhängig vom Modus: derselbe Chat, frisch (askai arch).
                if (agent != null && agentService.isBusy()) {
                    return; // der Agent arbeitet noch
                }
                if (startNewChat() && agent != null) {
                    agentService.endSession(); // der Agent behält sonst den alten Kontext in seiner Session
                    if (root.researchHost() != null) {
                        root.researchHost().startNewProject(); // neues Research-Projekt, frischer Weidezaun
                    }
                }
            }

            /** Neue Unterhaltung im Chat; {@code false}, solange eine Antwort oder Suche läuft. */
            private boolean startNewChat() {
                if (chatModel.isStreaming()) {
                    return false; // der Knopf ist während einer Antwort deaktiviert; zur Sicherheit auch hier
                }
                ChatConversationId previous = chatActions.conversationId();
                ChatConversationId next = chatService.openConversation(systemPrompt.get());
                try {
                    chatActions.startConversation(next);
                } catch (IllegalStateException stillBusy) {
                    chatService.closeConversation(next); // eine Suche läuft noch; es bleibt alles beim Alten
                    return false;
                }
                chatModel.clear();
                chatService.closeConversation(previous); // gespeichert ist er; der ChatService braucht ihn nicht mehr
                return true;
            }

            @Override
            public List<SavedChatItem> savedChats() {
                List<SavedChatItem> items = new ArrayList<SavedChatItem>();
                for (ChatRecord record : history.savedChats()) {
                    items.add(new SavedChatItem(record.getId(), record.getTitle(), record.getModifiedAt(),
                            record.getMessages().size(), record.attachmentCount()));
                }
                return items;
            }

            @Override
            public String currentChatId() {
                return history.currentChatId();
            }

            @Override
            public void openSavedChatRequested(String chatId) {
                history.open(chatId);
            }

            @Override
            public void deleteSavedChatRequested(String chatId) {
                if (chatId.equals(history.currentChatId()) && !startNewChat()) {
                    return; // der laufende Chat lässt sich gerade nicht verlassen
                }
                pendingDeletes.add(chatId);
            }

            @Override
            public void restoreSavedChatRequested(String chatId) {
                pendingDeletes.remove(chatId);
            }

            @Override
            public void renameChatRequested(String chatId, String title) {
                history.rename(chatId, title);
            }

            @Override
            public String currentChatTitle() {
                return history.currentTitle();
            }

            @Override
            public void settingsRequested() {
                Runnable action = view.settingsAction();
                if (action != null) {
                    action.run();
                }
            }
        });
        return view;
    }

    /** Das rahmenlose Hauptfenster; braucht ein Display. Schließen läuft über {@code WINDOW_CLOSING}. */
    public static JFrame createFrame(String title, ShellView view, ComicPalette palette) {
        return ShellFrame.create(title, view.workspace(), palette);
    }

    /** Der Drawer-Reiter „Wissensquellen“: Häkchen, Indexstand je Quelle, Indexieren und (mit Datei) Bearbeiten. */
    static KnowledgeSourcesController knowledgeSources(CompositionRoot root) {
        final ApplicationPorts ports = root.ports();
        Function<KnowledgeSourceId, Integer> count = sourceId ->
                ports.index().resourceIds(ports.embeddingSpace(), sourceId).size();
        KnowledgeSourcesController controller = new KnowledgeSourcesController(root.config().sources(),
                ports.sources(), root.sourceSelection(), root.indexingBinding(), count,
                new KnowledgeSourceManagement(ports.sourceProviders(), null, ports.index())
                        .withWithdrawal(root.resourceDocuments()::withdraw), root.uiExecutor(),
                root.workExecutor(),
                root.clock(), root.zone());
        controller.setVaultTitles(() -> vaultTitles(ports.secrets()));
        return controller;
    }

    /** Titel der KeePass-Einträge, sortiert; leer, wenn KeePass nicht erreichbar ist. */
    static List<String> vaultTitles(SecretProvider secrets) {
        List<String> titles = new ArrayList<String>();
        if (secrets == null) {
            return titles;
        }
        try {
            for (SecretEntry entry : secrets.entries()) {
                titles.add(entry.title());
            }
        } catch (SecretUnavailableException e) {
            return titles;
        }
        Collections.sort(titles, String.CASE_INSENSITIVE_ORDER);
        return titles;
    }

    /** Die gebaute Oberfläche mit ihren Modellen. */
    public static final class ShellView {

        private final ChatWorkspacePanel workspace;
        private final ChatShellModel chatModel;
        private final RagChatBinding chatActions;
        private final AgentModeAssembly.AgentView agent;
        private final KnowledgeSourcesController sources;
        private final ChatComposerPanel composer;
        private final Object persistLock = new Object();
        private volatile String latestModel;
        private Runnable settingsAction;

        ShellView(ChatWorkspacePanel workspace, ChatShellModel chatModel, RagChatBinding chatActions,
                  AgentModeAssembly.AgentView agent, KnowledgeSourcesController sources, ChatComposerPanel composer) {
            this.composer = composer;
            this.workspace = workspace;
            this.chatModel = chatModel;
            this.chatActions = chatActions;
            this.agent = agent;
            this.sources = sources;
        }

        /** Der Drawer-Reiter „Wissensquellen“; {@link KnowledgeSourcesController#setEditing} hängt die Datei an. */
        public KnowledgeSourcesController knowledgeSources() {
            return sources;
        }

        public JComponent shell() {
            return workspace;
        }

        public ChatWorkspacePanel workspace() {
            return workspace;
        }

        public ChatShellModel chatModel() {
            return chatModel;
        }

        public ChatShellActions chatActions() {
            return chatActions;
        }

        /** Die Unterhaltung, in die der Chat gerade schreibt; wechselt mit „+ Neuer Chat“. */
        public ChatConversationId conversation() {
            return chatActions.conversationId();
        }

        /** {@code null} ohne Agent-Modus. */
        public AgentModeAssembly.AgentView agent() {
            return agent;
        }

        /** Was das Zahnrad im Drawer tut (die Composition Root hängt den Einstellungen-Dialog an). */
        public void setSettingsAction(Runnable action) {
            this.settingsAction = action;
        }

        Runnable settingsAction() {
            return settingsAction;
        }

        /**
         * Die Modellliste des Composers aus dem Modellkatalog: die Chat-Modelle (Kategorie CHAT) aller Quellen aus
         * dem zuletzt abgefragten Stand. Eine Wahl gilt ab der nächsten Nachricht und wird als {@code chat.model}
         * gespeichert ({@code persist}, läuft nicht auf dem EDT). Dazu kommen die installierten lokalen Chat-Modelle
         * ({@code localChats}, Ordnernamen), ausgeführt im Client.
         */
        public void setChatModels(final Supplier<ModelCatalogSnapshot> catalog,
                                  final Supplier<List<String>> localChats, final Consumer<String> persist,
                                  final Executor worker) {
            final Supplier<List<String>> choices = () -> {
                List<String> ids = new ArrayList<String>();
                // Enterprise-Modelle ohne Präfix, alle anderen als <katalog>:<modell>; ausgeführt wird über den
                // Katalog des Eintrags (ChatModelExecutorRegistry).
                for (ModelDescriptor model : catalog.get().modelsFor(ModelCategory.CHAT)) {
                    String id = ModelsConfig.defaultCatalogId().equals(model.catalogId()) ? model.modelId()
                            : model.catalogId() + ":" + model.modelId();
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
                for (String local : localChats.get()) {
                    String id = ModelsConfig.LOCAL_CATALOG_ID + ":" + local;
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
                return ids;
            };
            final Consumer<String> remember = id -> {
                latestModel = id;
                // Schreiben nacheinander und immer die letzte Wahl: so gewinnt nie eine ältere Auswahl.
                worker.execute(() -> {
                    synchronized (persistLock) {
                        persist.accept(latestModel);
                    }
                });
            };
            // Ein Composer für beide Modi (askai arch): die Wahl gilt für Chat und Agent.
            final AgentModelChoice agentChoice = agent == null ? null : agent.binding().modelChoice();
            composer.setModelChoices(choices, id -> {
                chatActions.modelChanged(id);
                if (agentChoice != null) {
                    agentChoice.modelChanged(id);
                }
                remember.accept(id);
            });
        }
    }
}
