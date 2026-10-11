package com.aresstack.enterpriseai.astu.acropolis.agent;

import com.aresstack.enterpriseai.acp.api.AcpPlanEntry;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpToolCall;
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
                if (state.isTerminal()) {
                    return;
                }
                if (update.getKind() == AcpUpdate.Kind.TOOL_CALL && update.getToolCall() != null) {
                    listener.onToolActivity(activityOf(update.getToolCall()));
                    return;
                }
                if (update.getKind() == AcpUpdate.Kind.OTHER) {
                    // Pläne und sonstige Ereignisse des Agenten werden wie in askai zur Aktivität, nicht verworfen.
                    AgentToolActivity activity = otherActivity(update);
                    if (activity != null) {
                        listener.onToolActivity(activity);
                    }
                    return;
                }
                if (update.getText().isEmpty()) {
                    return;
                }
                if (update.getKind() == AcpUpdate.Kind.MESSAGE) {
                    if (update.startsUtterance() && reply.length() > 0) {
                        listener.onUtteranceStarted(); // askai: jede Äußerung eine eigene Blase
                    }
                    reply.append(update.getText());
                    listener.onMessage(update.getText());
                } else if (update.getKind() == AcpUpdate.Kind.THOUGHT) {
                    thoughts.append(update.getText());
                    listener.onThought(update.getText());
                }
            }
        }

        /**
         * Ein {@code plan} wird eine Plan-Wolke mit fester Kennung je Auftrag (jedes Update trägt den ganzen Plan und
         * ersetzt ihren Inhalt), fertig, sobald alle Schritte erledigt sind. Jedes andere Ereignis wird eine kurze
         * Aktivität je Ereignisart; ohne Inhalt entfällt es.
         */
        private AgentToolActivity otherActivity(AcpUpdate update) {
            if (update.isPlan()) {
                boolean done = true;
                StringBuilder text = new StringBuilder();
                for (AcpPlanEntry entry : update.getPlan()) {
                    done &= entry.isCompleted();
                    text.append(text.length() == 0 ? "" : "\n")
                            .append(entry.isCompleted() ? "[x] " : entry.isInProgress() ? "[>] " : "[ ] ")
                            .append(entry.getContent());
                }
                return new AgentToolActivity(AgentToolActivity.PLAN_ID, AgentToolActivity.PLAN_TITLE,
                        AgentToolActivity.KIND_PLAN,
                        done ? AgentToolActivity.Status.COMPLETED : AgentToolActivity.Status.RUNNING,
                        text.toString(), done ? AgentToolActivity.PLAN_DONE : "");
            }
            if (update.getText().trim().isEmpty()) {
                return null;
            }
            String type = update.getType().isEmpty() ? "event" : update.getType();
            return new AgentToolActivity("agent-event-" + type, AgentToolActivity.EVENT_TITLE_PREFIX + type,
                    AgentToolActivity.KIND_EVENT, AgentToolActivity.Status.COMPLETED, update.getText(), "");
        }

        private AgentToolActivity activityOf(AcpToolCall call) {
            AgentToolActivity.Status status = null;
            if (call.getStatus() == AcpToolCall.Status.COMPLETED) {
                status = AgentToolActivity.Status.COMPLETED;
            } else if (call.getStatus() == AcpToolCall.Status.FAILED) {
                status = AgentToolActivity.Status.FAILED;
            }
            return new AgentToolActivity(call.getId(), call.getTitle(), call.getKind(), status, call.getInput(),
                    call.getOutput());
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
