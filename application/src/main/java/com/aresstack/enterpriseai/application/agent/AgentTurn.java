package com.aresstack.enterpriseai.application.agent;

import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpUpdate;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.PromptHandle;

/**
 * Ein laufender oder abgeschlossener Agent-Auftrag.
 *
 * <p>Der Auftrag sorgt dafür, dass der {@link AgentTurnListener} genau einen Abschluss sieht, auch wenn Abbruch,
 * Start und ACP-Callbacks um die Wette laufen. Zustandsänderung und Listener-Aufruf geschehen unter dem Monitor
 * des Auftrags; der Service-Lock wird nur innerhalb davon genommen, nie umgekehrt.
 *
 * <p>Abbruch: Solange der Auftrag den Agenten noch nicht erreicht hat (Agent startet noch), endet er sofort
 * lokal. Danach wird er {@link AgentTurnState#CANCELLING}, bis der Agent den Abbruch bestätigt; erst dann ist
 * der Agent-Modus wieder frei. So schickt der Nutzer keinen neuen Auftrag in eine noch belegte ACP-Session.
 */
public final class AgentTurn {

    private final AgentService service;
    private final String prompt;
    private final AgentTurnListener listener;
    private final StringBuilder reply = new StringBuilder();
    private final StringBuilder thoughts = new StringBuilder();

    private AgentTurnState state = AgentTurnState.RUNNING;
    private PromptHandle handle;

    AgentTurn(AgentService service, String prompt, AgentTurnListener listener) {
        this.service = service;
        this.prompt = prompt;
        this.listener = listener;
    }

    public synchronized AgentTurnState state() {
        return state;
    }

    public synchronized boolean isDone() {
        return state.isTerminal();
    }

    /** Bricht den Auftrag ab. Idempotent; nach Abschluss wirkungslos. */
    public void cancel() {
        PromptHandle toCancel;
        synchronized (this) {
            if (state != AgentTurnState.RUNNING) {
                return;
            }
            if (handle == null) {
                finish(AgentTurnState.CANCELLED, null);
                return;
            }
            state = AgentTurnState.CANCELLING;
            toCancel = handle;
        }
        toCancel.cancel();
    }

    /** Schickt den Auftrag an die Session, falls er nicht schon vor dem Start abgebrochen wurde. */
    synchronized void dispatch(AcpSession session) {
        if (state.isTerminal()) {
            return;
        }
        try {
            handle = session.prompt(prompt, new AcpListener());
        } catch (RuntimeException e) {
            // Der Adapter hat den Prompt nicht angenommen; ohne Terminal bliebe der Agent-Modus belegt.
            if (!state.isTerminal()) {
                finish(AgentTurnState.FAILED, AgentFailure.PROMPT_FAILED);
            }
        }
    }

    /** Start oder Session-Anlage sind gescheitert, bevor der Auftrag den Agenten erreicht hat. */
    synchronized void fail(AgentFailure failure) {
        if (!state.isTerminal()) {
            finish(AgentTurnState.FAILED, failure);
        }
    }

    private void finish(AgentTurnState terminal, AgentFailure failure) {
        state = terminal;
        service.turnFinished(this, new AgentExchange(prompt, reply.toString(), thoughts.toString(), terminal, failure));
        switch (terminal) {
            case COMPLETED:
                listener.onCompleted();
                break;
            case CANCELLED:
                listener.onCancelled();
                break;
            default:
                listener.onFailed(failure);
                break;
        }
    }

    @Override
    public synchronized String toString() {
        return "AgentTurn[" + state + "]";
    }

    /** Übersetzt den Stream genau eines ACP-Prompts; nach dem Abschluss wird nichts mehr weitergereicht. */
    private final class AcpListener implements AcpUpdateListener {

        @Override
        public void onUpdate(AcpUpdate update) {
            synchronized (AgentTurn.this) {
                if (state.isTerminal() || update.getText().isEmpty()) {
                    return;
                }
                if (update.getKind() == AcpUpdate.Kind.MESSAGE) {
                    reply.append(update.getText());
                    listener.onMessage(update.getText());
                } else if (update.getKind() == AcpUpdate.Kind.THOUGHT) {
                    thoughts.append(update.getText());
                    listener.onThought(update.getText());
                }
                // OTHER (Tool-Aufrufe, Pläne ...) zeigt der Agent-Modus in AP21 noch nicht an.
            }
        }

        @Override
        public void onTerminal(String promptId, AcpPromptState terminal, String detail) {
            synchronized (AgentTurn.this) {
                if (state.isTerminal()) {
                    return;
                }
                if (state == AgentTurnState.CANCELLING || terminal == AcpPromptState.CANCELLED) {
                    // Nach einem Abbruchwunsch zählt jeder Abschluss als Abbruch, auch ein Fehler beim Schließen.
                    finish(AgentTurnState.CANCELLED, null);
                } else if (terminal == AcpPromptState.COMPLETED) {
                    finish(AgentTurnState.COMPLETED, null);
                } else {
                    // detail stammt aus Agent/SDK und bleibt draußen (kann Pfade oder Endpunkte enthalten).
                    finish(AgentTurnState.FAILED, service.failureAfterPrompt());
                }
            }
        }
    }
}
