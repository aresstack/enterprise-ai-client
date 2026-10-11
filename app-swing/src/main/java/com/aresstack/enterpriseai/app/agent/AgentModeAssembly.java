package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.research.ResearchOutOfScopeSky;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
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
        shell.composer().setModelControlsVisible(false);
        return new AgentView(shell, model, binding);
    }

    /**
     * Hängt den Wolkenhimmel (Blacklist des Weidezauns, askai arch {@code ResearchOutOfScopeSky}) über das
     * Transkript des eingebauten Recherche-Agenten: „+ Hinzufügen“ und ✕ gehen über denselben Scope-Pfad wie ein
     * Scoping-Turn, jede Änderung am Weidezaun (auch eine vom Modell notierte) zeichnet die Wolken neu. Abgelehnte
     * Änderungen erscheinen als Hinweiszeile. Muss auf dem UI-Thread gerufen werden.
     */
    public static ResearchOutOfScopeSky attachResearchSky(final AgentView view, final ResearchAgentHost host,
                                                          final Executor uiExecutor) {
        final ResearchOutOfScopeSky sky = new ResearchOutOfScopeSky();
        final Consumer<String> notice = new Consumer<String>() {
            @Override
            public void accept(final String message) {
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        view.model().addNotice(message);
                    }
                });
            }
        };
        sky.setAddAction(new Consumer<String>() {
            @Override
            public void accept(String text) {
                String rejected = host.addScopeExclusion(text);
                if (rejected != null) {
                    notice.accept("Ausschluss nicht gespeichert: " + rejected);
                }
            }
        });
        sky.setRemoveAction(new Consumer<String>() {
            @Override
            public void accept(String text) {
                String rejected = host.removeScopeExclusion(text);
                if (rejected != null) {
                    notice.accept("Ausschluss nicht entfernt: " + rejected);
                }
            }
        });
        host.setProblemSink(notice);
        host.addStateListener(new Runnable() {
            @Override
            public void run() {
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        sky.setExclusions(host.exclusionsProjection());
                    }
                });
            }
        });
        sky.setExclusions(host.exclusionsProjection());
        view.shell().transcript().setTranscriptSkyAccessory(sky);
        return sky;
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
