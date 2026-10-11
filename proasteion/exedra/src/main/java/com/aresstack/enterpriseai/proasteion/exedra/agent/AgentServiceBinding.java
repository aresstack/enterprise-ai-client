package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentFailure;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentTurn;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentTurnListener;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Verbindet eine Agent-Ansicht mit dem {@link AgentService}, nach demselben Muster wie {@code ChatServiceBinding}
 * für den Chat. Das {@link ChatShellModel} ist eine eigene Instanz nur für den Agenten; der Chat-Verlauf wird
 * nie berührt.
 *
 * <p>Senden hängt Auftrag und leere Agentenblase an; die Blase zeigt bis zur ersten Antwortzeile den
 * Platzhalter (auch während der Agent startet). Überlegungen des Agenten erscheinen nicht in der Blase, sie
 * bleiben im Transkript des Service. Stop bricht den Auftrag ab; die Blase endet, wenn der Agent den Abbruch
 * bestätigt. Der RAG-Schalter spielt im Agent-Modus keine Rolle.
 */
public final class AgentServiceBinding implements ChatShellActions {

    private final AgentService agentService;
    private final ChatShellModel model;
    private final Executor uiExecutor;
    private AgentTurn runningTurn;
    private final AgentModelChoice choice;
    private AttachmentTextExtractor extractor;
    private Executor workExecutor;
    /** Stop, während die Anhänge noch gelesen werden (nur UI-Thread). */
    private boolean cancelBeforeStart;

    /** Höchstens so viele Zeichen Text je Anhang gehen an den Agenten. */
    static final int MAX_ATTACHMENT_CHARS = 30000;

    public AgentServiceBinding(AgentService agentService, ChatShellModel model, Executor uiExecutor) {
        this(agentService, model, uiExecutor, null);
    }

    /** @param choice Modell und Denkaufwand des eingebauten Agenten; {@code null} bei einem externen Agenten */
    public AgentServiceBinding(AgentService agentService, ChatShellModel model, Executor uiExecutor,
                               AgentModelChoice choice) {
        if (agentService == null || model == null || uiExecutor == null) {
            throw new IllegalArgumentException("agentService, model and uiExecutor must not be null");
        }
        this.agentService = agentService;
        this.model = model;
        this.uiExecutor = uiExecutor;
        this.choice = choice;
    }

    /**
     * Anhänge im Agent-Modus: der Text jeder angehängten Datei (über denselben Extraktor wie im Chat) geht mit der
     * Nachricht an den Agenten; gelesen wird auf {@code workExecutor}, nie auf dem UI-Thread.
     */
    public void enableAttachments(AttachmentTextExtractor extractor, Executor workExecutor) {
        this.extractor = extractor;
        this.workExecutor = workExecutor;
    }

    @Override
    public boolean supportsAttachments() {
        return extractor != null && workExecutor != null;
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void sendRequested(final String text, boolean ragEnabled, final List<Path> attachments) {
        if (attachments == null || attachments.isEmpty() || !supportsAttachments()) {
            sendRequested(text, ragEnabled);
            return;
        }
        if (!model.canSend(text)) {
            return;
        }
        List<String> names = new ArrayList<String>(attachments.size());
        for (Path file : attachments) {
            names.add(file.getFileName().toString());
        }
        model.addUserMessage(text, names);
        model.beginAssistantMessage();
        final TurnListener listener = new TurnListener();
        final List<Path> files = new ArrayList<Path>(attachments);
        cancelBeforeStart = false;
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final String prompt = withAttachments(text, files, extractor);
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (cancelBeforeStart) {
                            listener.onCancelled();
                            return;
                        }
                        start(prompt, listener);
                    }
                });
            }
        });
    }

    /** Die Nachricht an den Agenten: der Text des Nutzers, danach der Inhalt jeder angehängten Datei. */
    static String withAttachments(String text, List<Path> files, AttachmentTextExtractor extractor) {
        StringBuilder prompt = new StringBuilder(text);
        prompt.append("\n\nAngehängte Dateien:");
        for (Path file : files) {
            String name = file.getFileName().toString();
            prompt.append("\n\n--- ").append(name).append(" ---\n");
            try {
                String content = extractor.extractText(name, Files.readAllBytes(file));
                if (content == null || content.trim().isEmpty()) {
                    prompt.append("(kein Text enthalten)");
                } else if (content.length() > MAX_ATTACHMENT_CHARS) {
                    prompt.append(content, 0, MAX_ATTACHMENT_CHARS).append("\n(gekürzt, ")
                            .append(content.length()).append(" Zeichen insgesamt)");
                } else {
                    prompt.append(content);
                }
            } catch (IOException | RuntimeException unreadable) {
                prompt.append("(nicht lesbar: ").append(unreadable.getMessage()).append(')');
            }
        }
        return prompt.toString();
    }

    /** Der Denkaufwand aus dem Composer gilt ab der nächsten Modellanfrage des eingebauten Agenten. */
    @Override
    public void reasoningChanged(String effort) {
        if (choice != null) {
            choice.reasoningChanged(effort);
        }
    }

    /** Das im Composer gewählte Modell; {@code null} bei einem externen Agenten. */
    public AgentModelChoice modelChoice() {
        return choice;
    }

    /** Muss auf dem UI-Thread gerufen werden (wie alle Model-Änderungen). */
    @Override
    public void sendRequested(String text, boolean ragEnabled) {
        if (!model.canSend(text)) {
            return;
        }
        model.addUserMessage(text);
        model.beginAssistantMessage();
        start(text, new TurnListener());
    }

    private void start(String prompt, TurnListener listener) {
        try {
            AgentTurn turn = agentService.send(prompt, listener);
            if (!turn.isDone()) {
                runningTurn = turn;
            }
        } catch (RuntimeException e) {
            // Agent beschäftigt oder Agent-Modus geschlossen: sichtbar machen, nicht verschlucken.
            listener.closed = true;
            model.failAssistantMessage(agentService.isBusy()
                    ? "Der Agent arbeitet noch am vorherigen Auftrag."
                    : "Der Agent-Modus ist beendet.");
        }
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void stopRequested() {
        cancelBeforeStart = true;
        AgentTurn turn = runningTurn;
        if (turn != null) {
            turn.cancel();
        }
    }

    /** Für Menschen lesbare Meldung; enthält bewusst keine Details aus Agentenprozess oder SDK. */
    static String describe(AgentFailure failure) {
        if (failure == null) {
            return "Der Agent hat den Auftrag nicht abgeschlossen.";
        }
        switch (failure) {
            case START_FAILED:
                return "Der Agent konnte nicht gestartet werden.";
            case SESSION_FAILED:
                return "Der Agent hat keine Sitzung eröffnet.";
            case AGENT_TERMINATED:
                return "Der Agent wurde unerwartet beendet. Der nächste Auftrag startet ihn neu.";
            case CLOSED:
                return "Der Agent-Modus ist beendet.";
            case PROMPT_FAILED:
            default:
                return "Der Agent hat den Auftrag nicht abgeschlossen.";
        }
    }

    /** Leitet Callbacks eines Auftrags auf den UI-Thread; nach dem Abschluss wird nichts mehr weitergereicht. */
    private final class TurnListener implements AgentTurnListener {

        private volatile boolean closed;

        @Override
        public void onMessage(final String text) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    model.appendAssistantDelta(text);
                }
            });
        }

        @Override
        public void onThought(String text) {
            // Überlegungen stehen im Transkript des AgentService, nicht in der Antwortblase.
        }

        @Override
        public void onCompleted() {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.completeAssistantMessage();
                }
            });
        }

        @Override
        public void onCancelled() {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.cancelAssistantMessage();
                }
            });
        }

        @Override
        public void onFailed(AgentFailure failure) {
            final String message = describe(failure);
            finish(new Runnable() {
                @Override
                public void run() {
                    model.failAssistantMessage(message);
                }
            });
        }

        private void finish(final Runnable terminal) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    closed = true;
                    runningTurn = null;
                    terminal.run();
                }
            });
        }

        private void onUi(final Runnable update) {
            uiExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!closed) {
                        update.run();
                    }
                }
            });
        }
    }
}
