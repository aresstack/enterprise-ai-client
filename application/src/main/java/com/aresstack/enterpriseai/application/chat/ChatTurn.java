package com.aresstack.enterpriseai.application.chat;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;

/**
 * Ein laufender oder abgeschlossener Turn: eine Nutzernachricht und die (gestreamte) Antwort darauf.
 *
 * <p>Der Turn sorgt dafür, dass der {@link ChatTurnListener} genau einen Abschluss sieht, auch wenn
 * Abbruch und Adapter-Callbacks um die Wette laufen. Zustandsänderung und Listener-Aufruf geschehen
 * unter dem Monitor des Turns; der Service-Lock wird nur innerhalb davon genommen, nie umgekehrt.
 */
public final class ChatTurn {

    private final ChatService service;
    private final ChatConversationId conversationId;
    private final ChatTurnListener listener;

    private ChatTurnState state = ChatTurnState.RUNNING;
    private ChatTask task;

    ChatTurn(ChatService service, ChatConversationId conversationId, ChatTurnListener listener) {
        this.service = service;
        this.conversationId = conversationId;
        this.listener = listener;
    }

    public ChatConversationId conversationId() {
        return conversationId;
    }

    public synchronized ChatTurnState state() {
        return state;
    }

    public synchronized boolean isDone() {
        return state.isTerminal();
    }

    /**
     * Bricht den Turn ab. Idempotent; nach Abschluss wirkungslos. Die Nutzernachricht bleibt in der
     * Historie, eine Teilantwort wird nicht übernommen.
     */
    public void cancel() {
        ChatTask toCancel;
        synchronized (this) {
            if (state.isTerminal()) {
                return;
            }
            state = ChatTurnState.CANCELLED;
            service.turnFinished(this, null);
            listener.onCancelled();
            toCancel = task;
        }
        if (toCancel != null) {
            toCancel.cancel();
        }
    }

    /** Übernimmt das Task-Handle des Ports; bricht es sofort ab, falls der Turn schon abgebrochen wurde. */
    void attach(ChatTask newTask) {
        boolean cancelNow;
        synchronized (this) {
            task = newTask;
            cancelNow = state == ChatTurnState.CANCELLED;
        }
        if (cancelNow && newTask != null) {
            newTask.cancel();
        }
    }

    /** Der Port hat schon beim Start geworfen. */
    synchronized void failedToStart(ChatCompletionException error) {
        fail(error);
    }

    ChatStreamListener portListener() {
        return new ChatStreamListener() {
            @Override
            public void onStart() {
                // Der Turn läuft ab send(); der Start beim Provider ändert für den Use Case nichts.
            }

            @Override
            public void onDelta(String text) {
                synchronized (ChatTurn.this) {
                    if (!state.isTerminal() && text != null && !text.isEmpty()) {
                        listener.onDelta(text);
                    }
                }
            }

            @Override
            public void onComplete(ChatResponse response) {
                synchronized (ChatTurn.this) {
                    if (state.isTerminal()) {
                        return;
                    }
                    state = ChatTurnState.COMPLETED;
                    service.turnFinished(ChatTurn.this, response.message());
                    listener.onCompleted(response);
                }
            }

            @Override
            public void onError(ChatCompletionException error) {
                synchronized (ChatTurn.this) {
                    fail(error);
                }
            }

            @Override
            public void onCancelled() {
                synchronized (ChatTurn.this) {
                    if (state.isTerminal()) {
                        return;
                    }
                    state = ChatTurnState.CANCELLED;
                    service.turnFinished(ChatTurn.this, null);
                    listener.onCancelled();
                }
            }
        };
    }

    private void fail(ChatCompletionException error) {
        if (state.isTerminal()) {
            return;
        }
        state = ChatTurnState.FAILED;
        service.turnFinished(this, null);
        listener.onFailed(error);
    }

    @Override
    public synchronized String toString() {
        return "ChatTurn[" + conversationId + ", " + state + "]";
    }
}
