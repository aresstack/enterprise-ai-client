package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.agent.AgentModeAssembly;
import com.aresstack.enterpriseai.app.chat.RagChatBinding;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.app.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.app.ui.workspace.WorkspaceActions;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/**
 * Baut die Oberfläche über dem Graphen aus {@link CompositionRoot}: die Arbeitsfläche
 * ({@link ChatWorkspacePanel}: Hamburger, Modus-Pille, Drawer) mit der Chat-Ansicht samt
 * {@link RagChatBinding} (AP22: RAG-Schalter, Quellen, Hinweise) und Statuszeile der Wissensbasis, dazu, falls
 * konfiguriert, der Agent-Ansicht (AP21). „+ Neuer Chat“ eröffnet im Chat-Modus eine neue Unterhaltung am
 * {@link ChatService} und leert das Transkript; im Agent-Modus leert es nur das Transkript. Das Zahnrad reicht
 * die Composition Root als {@link WorkspaceActions#settingsRequested()} an den Einstellungen-Dialog weiter.
 * {@link #createShell} läuft auch headless (Tests); nur {@link #createFrame} braucht ein Display.
 */
public final class ShellAssembly {

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
                root.uiExecutor(), root.workExecutor(), root.zone());
        ChatShellPanel chatShell = new ChatShellPanel(chatModel, chatActions, root.knowledgeStatus(), palette, bubbles);

        final AgentModeAssembly.AgentView agent;
        if (root.hasAgent()) {
            agent = AgentModeAssembly.create(root.agentService(), root.uiExecutor(), root.clock(), palette, bubbles);
        } else {
            agent = null;
        }
        ShellModeModel modes = new ShellModeModel(agent != null);
        ChatWorkspacePanel workspace = new ChatWorkspacePanel(modes, chatShell, agent == null ? null : agent.shell(),
                palette);
        workspace.setWindowTitle(root.config().windowTitle());
        workspace.setKnowledgeSources(knowledgeSources(root.config()));
        final ShellView view = new ShellView(workspace, chatModel, chatActions, agent);
        workspace.setActions(new WorkspaceActions() {
            @Override
            public void newChatRequested(ShellMode mode) {
                if (mode == ShellMode.AGENT && agent != null) {
                    if (!agent.model().isStreaming()) {
                        agent.model().clear();
                    }
                    return;
                }
                if (chatModel.isStreaming()) {
                    return; // der Knopf ist während einer Antwort deaktiviert; zur Sicherheit auch hier
                }
                chatActions.startConversation(chatService.openConversation(systemPrompt));
                chatModel.clear();
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

    /** Die konfigurierten Quellen, wie der Drawer sie zeigt: Kennung als Name, Typ und Umfang als Beschreibung. */
    static List<KnowledgeSourceItem> knowledgeSources(AppConfig config) {
        List<KnowledgeSourceItem> items = new ArrayList<KnowledgeSourceItem>();
        for (SourceConfig source : config.sources()) {
            items.add(new KnowledgeSourceItem(source.sourceId().value(), describe(source)));
        }
        return items;
    }

    private static String describe(SourceConfig source) {
        String type = source.type();
        if (source.scope() == null || source.scope().startPoints().isEmpty()) {
            return type;
        }
        return type + " · " + String.join(", ", source.scope().startPoints());
    }

    /** Die gebaute Oberfläche mit ihren Modellen. */
    public static final class ShellView {

        private final ChatWorkspacePanel workspace;
        private final ChatShellModel chatModel;
        private final RagChatBinding chatActions;
        private final AgentModeAssembly.AgentView agent;
        private Runnable settingsAction;

        ShellView(ChatWorkspacePanel workspace, ChatShellModel chatModel, RagChatBinding chatActions,
                  AgentModeAssembly.AgentView agent) {
            this.workspace = workspace;
            this.chatModel = chatModel;
            this.chatActions = chatActions;
            this.agent = agent;
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
