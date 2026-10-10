package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.agent.AgentModeAssembly;
import com.aresstack.enterpriseai.app.chat.ChatHistoryBinding;
import com.aresstack.enterpriseai.app.chat.DocumentAttachmentTextExtractor;
import com.aresstack.enterpriseai.app.chat.FileAttachmentStore;
import com.aresstack.enterpriseai.app.chat.FileChatHistoryStore;
import com.aresstack.enterpriseai.app.chat.RagChatBinding;
import com.aresstack.enterpriseai.app.chat.ToolSupport;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.app.knowledge.KnowledgeSourcesController;
import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
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
import com.aresstack.enterpriseai.document.tika.DocumentExtraction;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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

    private ShellAssembly() {
    }

    /** Muss auf dem EDT laufen (auch headless möglich). Das Zahnrad ist ohne {@link ShellView#setSettingsAction} stumm. */
    public static ShellView createShell(CompositionRoot root, ComicPalette palette, BubblePalette bubbles) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("createShell must run on the event dispatch thread");
        }
        final ChatService chatService = root.chatService();
        final String systemPrompt = root.config().chat().systemPrompt();
        ChatConversationId conversation = chatService.openConversation(systemPrompt);
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
        ChatShellPanel chatShell = new ChatShellPanel(chatModel, chatActions, root.knowledgeStatus(), palette, bubbles);
        chatShell.composer().setModelName(root.config().chat().model());

        final AgentModeAssembly.AgentView agent;
        final AgentService agentService;
        if (root.hasAgent()) {
            agentService = root.agentService();
            agent = AgentModeAssembly.create(agentService, root.uiExecutor(), root.clock(), palette, bubbles);
        } else {
            agentService = null;
            agent = null;
        }
        ShellModeModel modes = new ShellModeModel(agent != null);
        ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, agent == null ? null : agent.shell(),
                palette);
        workspace.setWindowTitle(root.config().windowTitle());
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
        final ShellView view = new ShellView(workspace, chatModel, chatActions, agent, sources);
        chatShell.composer().setModelAction(() -> {
            Runnable action = view.settingsAction();
            if (action != null) {
                action.run();
            }
        });
        workspace.setActions(new WorkspaceActions() {
            @Override
            public void newChatRequested(ShellMode mode) {
                if (mode == ShellMode.AGENT && agent != null) {
                    if (!agent.model().isStreaming() && !agentService.isBusy()) {
                        agentService.endSession(); // der Agent behält sonst den alten Kontext in seiner Session
                        agent.model().clear();
                    }
                    return;
                }
                startNewChat();
            }

            /** Neue Unterhaltung im Chat; {@code false}, solange eine Antwort oder Suche läuft. */
            private boolean startNewChat() {
                if (chatModel.isStreaming()) {
                    return false; // der Knopf ist während einer Antwort deaktiviert; zur Sicherheit auch hier
                }
                ChatConversationId previous = chatActions.conversationId();
                ChatConversationId next = chatService.openConversation(systemPrompt);
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
                history.delete(chatId);
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
        return new KnowledgeSourcesController(root.config().sources(), ports.sources(), root.sourceSelection(),
                root.indexingBinding(), count, ports.sourceFactory(), root.uiExecutor(), root.workExecutor(),
                root.clock(), root.zone());
    }

    /** Die gebaute Oberfläche mit ihren Modellen. */
    public static final class ShellView {

        private final ChatWorkspacePanel workspace;
        private final ChatShellModel chatModel;
        private final RagChatBinding chatActions;
        private final AgentModeAssembly.AgentView agent;
        private final KnowledgeSourcesController sources;
        private Runnable settingsAction;

        ShellView(ChatWorkspacePanel workspace, ChatShellModel chatModel, RagChatBinding chatActions,
                  AgentModeAssembly.AgentView agent, KnowledgeSourcesController sources) {
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
    }
}
