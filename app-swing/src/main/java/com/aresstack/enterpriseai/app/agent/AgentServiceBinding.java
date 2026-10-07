package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.application.agent.AgentFailure;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.agent.AgentTurn;
import com.aresstack.enterpriseai.application.agent.AgentTurnListener;

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

    public AgentServiceBinding(AgentService agentService, ChatShellModel model, Executor uiExecutor) {
        if (agentService == null || model == null || uiExecutor == null) {
            throw new IllegalArgumentException("agentService, model and uiExecutor must not be null");
        }
        this.agentService = agentService;
        this.model = model;
        this.uiExecutor = uiExecutor;
    }

    /** Muss auf dem UI-Thread gerufen werden (wie alle Model-Änderungen). */
    @Override
    public void sendRequested(String text, boolean ragEnabled) {
        if (!model.canSend(text)) {
            return;
        }
        model.addUserMessage(text);
        model.beginAssistantMessage();
        TurnListener listener = new TurnListener();
        try {
            AgentTurn turn = agentService.send(text, listener);
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
