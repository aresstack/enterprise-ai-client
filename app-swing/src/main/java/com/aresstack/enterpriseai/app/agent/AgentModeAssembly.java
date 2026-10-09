package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * Baut die Agent-Ansicht: eigenes {@link ChatShellModel}, {@link AgentServiceBinding} und eine
 * {@link ChatShellPanel} ohne RAG-Schalter. Für die Composition Root (AP23) und die Demo; zusammen mit
 * {@code ChatWorkspacePanel} (Modus-Pille Chat/Agent) ergibt das die Arbeitsfläche.
 */
public final class AgentModeAssembly {

    private AgentModeAssembly() {
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    public static AgentView create(AgentService agentService, Executor uiExecutor, LongSupplier clock,
                                   ComicPalette comicPalette, BubblePalette bubblePalette) {
        ChatShellModel model = new ChatShellModel(clock);
        AgentServiceBinding binding = new AgentServiceBinding(agentService, model, uiExecutor);
        ChatShellPanel shell = new ChatShellPanel(model, binding, comicPalette, bubblePalette);
        shell.composer().setRagToggleVisible(false);
        return new AgentView(shell, model, binding);
    }

    /** Die gebaute Agent-Ansicht mit ihrem eigenen Model und ihrer Anbindung. */
    public static final class AgentView {

        private final ChatShellPanel shell;
        private final ChatShellModel model;
        private final AgentServiceBinding binding;

        AgentView(ChatShellPanel shell, ChatShellModel model, AgentServiceBinding binding) {
            this.shell = shell;
            this.model = model;
            this.binding = binding;
        }

        public ChatShellPanel shell() {
            return shell;
        }

        public ChatShellModel model() {
            return model;
        }

        public AgentServiceBinding binding() {
            return binding;
        }
    }
}
