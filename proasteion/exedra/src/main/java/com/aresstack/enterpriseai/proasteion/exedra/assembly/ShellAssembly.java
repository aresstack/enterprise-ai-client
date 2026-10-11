package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.ConfluenceEntryCheck;

import com.aresstack.enterpriseai.astu.acropolis.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.proasteion.exedra.agent.AgentModeAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchOutOfScopeSky;
import com.aresstack.enterpriseai.proasteion.exedra.agent.AgentTurnChatMirror;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ConversationLifecycle;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ModeSwitchingChatActions;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ResearchPhaseAttribution;
import com.aresstack.enterpriseai.proasteion.exedra.agent.SavedChatHistory;
import com.aresstack.enterpriseai.proasteion.exedra.agent.AgentServiceBinding;
import com.aresstack.enterpriseai.proasteion.application.agent.AgentModelChoice;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ResearchConceptViewAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.agent.ResearchWorkflowAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.chat.ChatHistoryBinding;
import com.aresstack.enterpriseai.proasteion.exedra.chat.FileAttachmentStore;
import com.aresstack.enterpriseai.proasteion.exedra.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.proasteion.exedra.chat.FileChatHistoryStore;
import com.aresstack.enterpriseai.proasteion.exedra.chat.RagChatBinding;
import com.aresstack.enterpriseai.proasteion.exedra.chat.ToolSupport;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.proasteion.application.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.FavoritesStore;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.KnowledgeReadingController;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.KnowledgeSourcesController;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.ReaderChatPages;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentReader;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchKnowledgeSourcesUseCase;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.SavedChatItem;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.WorkspaceActions;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Baut die Oberfläche über dem Graphen aus {@link CompositionRoot}: die Arbeitsfläche
 * ({@link ChatWorkspacePanel}: Hamburger, Modus-Pille, Drawer) mit der Chat-Ansicht samt
 * {@link RagChatBinding} (AP22: RAG-Schalter, Quellen, Hinweise) und Statuszeile der Wissensbasis, dazu, falls
 * konfiguriert, der Agent-Ansicht (AP21). Chat, Agentensitzung und Research-Projekt gehören zusammen
 * ({@link ConversationLifecycle}): „+ Neuer Chat“ eröffnet eine neue Unterhaltung am {@link ChatService}, schließt
 * die bisherige dort, leert das Transkript, beendet die ACP-Session samt Agentenprozess
 * ({@link AgentService#endSession()}) und beginnt ein neues Research-Projekt; ein gespeicherter Chat öffnet sich mit
 * seinem Research-Projekt in einer frischen Agentensitzung. Die Chat-Historie
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
    /** Die Favoriten des Lesebereichs, neben der Konfigurationsdatei. */
    static final String FAVORITES_FILE = "favorites.json";
    /** Bildbeschreibungen des Lesebereichs (je Bild und Version eine Datei), neben den Favoriten. */
    static final String IMAGE_DESCRIPTIONS = "image-descriptions";

    private static final Logger LOG = Logger.getLogger(ShellAssembly.class.getName());

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
        // Statuszeile der Wissensbasis (AP22) und die Vollindexierung dahinter gehören zur Schale; beim Beenden hält
        // der Kompositionspunkt eine laufende Indexierung über diese Statuszeile an.
        final KnowledgeStatusModel knowledgeStatus = new KnowledgeStatusModel();
        final KnowledgeIndexingBinding indexingBinding = new KnowledgeIndexingBinding(root.indexing(),
                knowledgeStatus, root.uiExecutor(), root.workExecutor(), root.clock(), root.zone());
        root.onStopIndexing(() -> root.uiExecutor().execute(knowledgeStatus::requestCancel));
        final RagChatBinding chatActions = new RagChatBinding(root.ragChat(), conversation, chatModel,
                root.uiExecutor(), root.workExecutor(), root.zone(), root.sourceSelection());
        // Chat-Historie neben der Konfigurationsdatei: chats/<chatId>.json, Anhänge im Ordner chats/<chatId>/.
        final Path chatsDirectory = AppPaths.configFile().toAbsolutePath().getParent().resolve(CHATS_DIRECTORY);
        if (root.ports().chat() != null) {
            // Tool-Calling, Dateianhänge und Seiten: /chat/completions mit tools, Ablage im Ordner des Chats, Text
            // über document-tika, Suche und Seiten über dieselben knowledge_search/knowledge_read wie im Agent-Modus; welche
            // davon aktiv sind, sagt je Anfrage die zentrale Werkzeug-Registry (Reiter „Tools“).
            chatActions.enableTools(new ToolSupport(root.ports().chat(),
                    new FileAttachmentStore(chatsDirectory),
                    root.attachmentText(),
                    root::knowledgeChatTools,
                    root.toolCatalog()::isEnabled,
                    root.config().chat().toolsEnabled()));
        }
        final FileChatHistoryStore chatStore = new FileChatHistoryStore(chatsDirectory);
        final ChatHistoryBinding history = new ChatHistoryBinding(chatStore, chatService, chatActions, chatModel,
                systemPrompt, root.clock());
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
            agentBinding.enableAttachments(root.attachmentText(), root.workExecutor());
            chatShell = new ChatShellPanel(chatModel,
                    new ModeSwitchingChatActions(chatActions, agentBinding, modes, chatModel),
                    knowledgeStatus, palette, bubbles);
            agent = AgentModeAssembly.sharedView(chatShell, agentBinding);
            // Ein Verlauf: abgeschlossene Agenten-Turns gehören auch zur Unterhaltung des Chats (askai arch).
            new AgentTurnChatMirror(chatService, chatActions::conversationId, modes, chatModel);
            if (root.researchHost() != null) {
                // askai chat_history: der gespeicherte Verlauf des Chats, nach Phasen des Projekts gegliedert.
                root.researchHost().setChatHistoryReader(new SavedChatHistory(chatStore));
                new ResearchPhaseAttribution(root.researchHost(), modes, chatModel, root.clock());
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
            chatShell = new ChatShellPanel(chatModel, chatActions, knowledgeStatus, palette, bubbles);
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
        final KnowledgeSourcesController sources = knowledgeSources(root, indexingBinding);
        workspace.setKnowledgeSourceActions(sources);
        final ChatWorkspacePanel drawer = workspace;
        // Kein RAG-Schalter (wie askai arch): gesucht wird, sobald Wissensquellen angebunden sind; welche, sagen
        // die Häkchen im Reiter „Wissensquellen“. Nur konfigurierte, aber nicht angebundene Quellen schalten RAG
        // nicht ein (sonst würde ungefiltert im ganzen Index gesucht).
        final KnowledgeSourceSelection selection = root.sourceSelection();
        sources.attach(items -> {
            // Chat und Agent sehen neue oder entfernte Quellen sofort, ohne Neustart.
            root.publishSources(sources.liveRegistrations());
            drawer.setKnowledgeSources(items);
            chatModel.setRagEnabled(!items.isEmpty() && selection.isRestricted());
        });
        // Suchleiste, Lesebereich und Favoriten (wie globale Suche, Lese-Tab und Lesezeichen in MainframeMate).
        KnowledgeReadingController reading = new KnowledgeReadingController(workspace, sources::enabledRegistrations,
                sources::registration,
                new SearchKnowledgeSourcesUseCase(root.ports().index(), root.ports().embeddingSpaceSource(),
                        root.workExecutor()).withIndexedHits(),
                new BrowseKnowledgeSourceUseCase(KnowledgeDocumentReader.direct()), root.prefetch(),
                new FavoritesStore(chatsDirectory.resolveSibling(FAVORITES_FILE)), root.documentNavigation(),
                root.uiExecutor(), root.workExecutor());
        reading.describeImagesWith(root.readerImageDescriptions(chatsDirectory.resolveSibling(IMAGE_DESCRIPTIONS)));
        sources.setReading(reading);
        // Rechtsklick auf einen Reiter: „Zum aktuellen Chat hinzufügen“ legt nur Titel und Bookmark in den Composer.
        workspace.reader().addTabMenuContributor(new ReaderChatPages(chatShell.composer(), workspace));
        reading.attach();
        if (agent != null && root.researchHost() != null) {
            // Geführter Recherche-Workflow: Phasenleiste über dem Composer, Reiter Quellen/Gliederung/Dokument.
            final KnowledgeReadingController reader = reading;
            final ResearchWorkflowAssembly.Parts research = ResearchWorkflowAssembly.create(root.researchHost(),
                    agent.binding()::sendAction, chatModel, root.uiExecutor(),
                    (resourceId, title) -> reader.open(new ReaderLink(researchSourceId(root, sources, resourceId),
                            resourceId, title, "", resourceId), true),
                    drawer::showSidebarTab);
            workspace.addAgentTab(ResearchWorkflowAssembly.SOURCES_TAB, research.sources());
            workspace.addAgentTab(ResearchWorkflowAssembly.OUTLINE_TAB, research.outline());
            workspace.addAgentTab(ResearchWorkflowAssembly.DOCUMENT_TAB, research.document());
            final ShellModeModel.Listener stripByMode = mode -> {
                if (mode == ShellMode.AGENT && root.usesBuiltInAgent()) {
                    shell.setComposerAccessory(research.strip());
                } else {
                    shell.setComposerAccessory(null);
                }
            };
            modes.addListener(stripByMode);
            stripByMode.modeChanged(modes.getMode());
        }
        final ShellView view = new ShellView(workspace, chatModel, chatActions, agent, sources, chatShell.composer(),
                knowledgeStatus);
        chatShell.composer().setModelAction(() -> {
            Runnable action = view.settingsAction();
            if (action != null) {
                action.run();
            }
        });
        // Ein Chat, eine Agentensitzung, ein Research-Projekt (askai arch): Neuer Chat, Öffnen und Löschen schalten
        // alle drei gemeinsam um. Löschen wie askai arch: bis zum Beenden nur vorgemerkt (↩ holt den Chat zurück),
        // erst dann samt Anhängen und Research-Projekt weg.
        final ConversationLifecycle lifecycle = new ConversationLifecycle(new ConversationLifecycle.Chats() {
            @Override
            public String currentChatId() {
                return history.currentChatId();
            }

            @Override
            public List<String> savedChatIds() {
                List<String> ids = new ArrayList<String>();
                for (ChatRecord record : history.savedChats()) {
                    ids.add(record.getId());
                }
                return ids;
            }

            @Override
            public boolean startNewConversation() {
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
            public boolean open(String chatId) {
                return history.open(chatId);
            }

            @Override
            public void delete(String chatId) {
                history.delete(chatId);
            }
        }, agentService == null ? null : new ConversationLifecycle.AgentSessions() {
            @Override
            public boolean isBusy() {
                return agentService.isBusy();
            }

            @Override
            public void endSession() {
                agentService.endSession();
            }
        }, root.researchHost());
        lifecycle.start();
        view.setLifecycle(lifecycle);
        root.shutdown().then("chat-history-deletes", lifecycle::purgeDeleted);
        workspace.setActions(new WorkspaceActions() {
            @Override
            public void newChatRequested(ShellMode mode) {
                // „Neuer Chat“ gilt unabhängig vom Modus: derselbe Chat, frisch (askai arch).
                lifecycle.newChat();
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
                lifecycle.openChat(chatId);
            }

            @Override
            public void deleteSavedChatRequested(String chatId) {
                lifecycle.deleteChat(chatId); // der laufende Chat lässt sich während einer Antwort nicht verlassen
            }

            @Override
            public void restoreSavedChatRequested(String chatId) {
                lifecycle.restoreChat(chatId);
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
    static KnowledgeSourcesController knowledgeSources(CompositionRoot root,
                                                       KnowledgeIndexingBinding indexingBinding) {
        final ApplicationPorts ports = root.ports();
        Function<SourceAddress, Integer> count = sourceId ->
                ports.index().resourceIds(ports.embeddingSpace(), sourceId).size();
        KnowledgeSourcesController controller = new KnowledgeSourcesController(root.config().sources(),
                ports.sources(), root.sourceSelection(), indexingBinding, count,
                new KnowledgeSourceManagement(ports.sourceProviders(), null, ports.index())
                        .withWithdrawal(root.resourceDocuments()::withdraw), root.uiExecutor(),
                root.workExecutor(),
                root.clock(), root.zone());
        final KeePassAccess vault = ports.keePassAccess();
        controller.setVaultTitles(() -> vaultTitles(vault));
        controller.setPasswordStore(new com.aresstack.enterpriseai.proasteion.exedra.settings.VaultPasswordStore(
                () -> vault, root.workExecutor(),
                root.uiExecutor(), (url, user, password, alias) ->
                        ConfluenceEntryCheck.check(root.config().network(), url, user, password, alias)));
        controller.setVaultCertificateAliases(vault::certificateAlias);
        controller.setVaultCertificateAliasWriter(vault::storeCertificateAlias);
        return controller;
    }

    /** Titel der KeePass-Einträge, sortiert; leer, wenn KeePass nicht erreichbar ist. */
    static List<String> vaultTitles(KeePassAccess vault) {
        try {
            return vault.titles();
        } catch (KeePassAccess.Failure e) {
            return new ArrayList<String>();
        }
    }

    /** Die gebaute Oberfläche mit ihren Modellen. */
    public static final class ShellView {

        private final ChatWorkspacePanel workspace;
        private final ChatShellModel chatModel;
        private final RagChatBinding chatActions;
        private final AgentModeAssembly.AgentView agent;
        private final KnowledgeSourcesController sources;
        private final ChatComposerPanel composer;
        private final KnowledgeStatusModel knowledgeStatus;
        private final Object persistLock = new Object();
        private volatile String latestModel;
        private Runnable settingsAction;
        private ConversationLifecycle lifecycle;

        ShellView(ChatWorkspacePanel workspace, ChatShellModel chatModel, RagChatBinding chatActions,
                  AgentModeAssembly.AgentView agent, KnowledgeSourcesController sources, ChatComposerPanel composer,
                  KnowledgeStatusModel knowledgeStatus) {
            this.composer = composer;
            this.knowledgeStatus = knowledgeStatus;
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

        /** Statuszeile der Wissensbasis (AP22). */
        public KnowledgeStatusModel knowledgeStatus() {
            return knowledgeStatus;
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

        /** Neuer Chat, Öffnen und Löschen samt Agentensitzung und Research-Projekt. */
        public ConversationLifecycle lifecycle() {
            return lifecycle;
        }

        void setLifecycle(ConversationLifecycle lifecycle) {
            this.lifecycle = lifecycle;
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

    /**
     * Die Wissensquelle einer Projektquelle für den Lesebereich: aus dem Index (die Ressource trägt ihre Quelle),
     * sonst die erste angebundene Quelle derselben Art (gleiches Präfix der Adresse).
     */
    static String researchSourceId(CompositionRoot root, KnowledgeSourcesController sources, String resourceId) {
        try {
            for (com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry entry : root.ports().index()
                    .entriesOf(root.ports().embeddingSpaceSource().get(),
                            com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId.of(resourceId))) {
                return entry.resource().sourceId().value();
            }
        } catch (Exception notIndexed) {
            // weiter mit der Art der Adresse
        }
        int colon = resourceId.indexOf(':');
        String kind = colon < 0 ? resourceId : resourceId.substring(0, colon + 1);
        for (com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration registration
                : sources.enabledRegistrations()) {
            if (registration.sourceId().value().startsWith(kind)) {
                return registration.sourceId().value();
            }
        }
        return "";
    }
}
