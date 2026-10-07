package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.agent.AgentModeAssembly;
import com.aresstack.enterpriseai.app.chat.RagChatBinding;
import com.aresstack.enterpriseai.app.ui.agent.ModalShellPanel;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Baut die Oberfläche über dem Graphen aus {@link CompositionRoot}: Chat-Shell mit {@link RagChatBinding}
 * (AP22: RAG-Schalter, Quellen, Hinweise) und Statuszeile der Wissensbasis, dazu, falls konfiguriert, die
 * Agent-Ansicht mit Modus-Umschaltung (AP21). {@link #createShell} läuft auch headless (Tests); nur
 * {@link #createFrame} braucht ein Display.
 */
public final class ShellAssembly {

    private ShellAssembly() {
    }

    /** Muss auf dem EDT laufen (auch headless möglich). */
    public static ShellView createShell(CompositionRoot root, ComicPalette palette, BubblePalette bubbles) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("createShell must run on the event dispatch thread");
        }
        ChatService chatService = root.chatService();
        ChatConversationId conversation = chatService.openConversation(root.config().chat().systemPrompt());
        ChatShellModel chatModel = new ChatShellModel(root.clock());
        ChatShellActions chatActions = new RagChatBinding(root.ragChat(), conversation, chatModel, root.uiExecutor(),
                root.workExecutor(), root.zone());
        ChatShellPanel chatShell = new ChatShellPanel(chatModel, chatActions, root.knowledgeStatus(), palette, bubbles);

        AgentModeAssembly.AgentView agent = null;
        if (root.hasAgent()) {
            agent = AgentModeAssembly.create(root.agentService(), root.uiExecutor(), root.clock(), palette, bubbles);
        }
        ShellModeModel modes = new ShellModeModel(agent != null);
        ModalShellPanel shell = new ModalShellPanel(modes, chatShell, agent == null ? null : agent.shell(), palette);
        return new ShellView(shell, chatModel, chatActions, conversation, agent);
    }

    /** Das Hauptfenster; braucht ein Display. Schließen löst den mitgegebenen Runnable aus. */
    public static JFrame createFrame(String title, ShellView view, ComicPalette palette) {
        ComicTheme.installMenuDefaults(palette);
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.roundedBorder(palette, 6));
        content.add(view.shell(), BorderLayout.CENTER);
        frame.setContentPane(content);
        frame.setMinimumSize(new Dimension(480, 420));
        frame.setSize(new Dimension(860, 700));
        frame.setLocationRelativeTo(null);
        return frame;
    }

    /** Die gebaute Oberfläche mit ihren Modellen. */
    public static final class ShellView {

        private final ModalShellPanel shell;
        private final ChatShellModel chatModel;
        private final ChatShellActions chatActions;
        private final ChatConversationId conversation;
        private final AgentModeAssembly.AgentView agent;

        ShellView(ModalShellPanel shell, ChatShellModel chatModel, ChatShellActions chatActions,
                  ChatConversationId conversation, AgentModeAssembly.AgentView agent) {
            this.shell = shell;
            this.chatModel = chatModel;
            this.chatActions = chatActions;
            this.conversation = conversation;
            this.agent = agent;
        }

        public JComponent shell() {
            return shell;
        }

        public ModalShellPanel modalShell() {
            return shell;
        }

        public ChatShellModel chatModel() {
            return chatModel;
        }

        public ChatShellActions chatActions() {
            return chatActions;
        }

        public ChatConversationId conversation() {
            return conversation;
        }

        /** {@code null} ohne Agent-Modus. */
        public AgentModeAssembly.AgentView agent() {
            return agent;
        }
    }
}
