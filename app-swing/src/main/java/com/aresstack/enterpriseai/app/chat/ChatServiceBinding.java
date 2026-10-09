package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.net.ConnectionDiagnosis;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.chat.ChatTurn;
import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;

import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verbindet die Chat-Shell mit genau einer Konversation des {@link ChatService}.
 *
 * <p>Senden hängt die Nutzernachricht und eine leere Assistentenblase an und startet einen Turn; Deltas,
 * Abschluss, Abbruch und Fehler kommen vom Adapter-Thread und werden über {@code uiExecutor} (in der
 * Anwendung {@code SwingUtilities::invokeLater}) in das Model geschrieben. Stop bricht den laufenden Turn ab;
 * die Abbruchmeldung kommt wie jeder Abschluss über den Listener zurück.
 *
 * <p>Der Enterprise-Server streamt teils stark gebündelt (lange keine Deltas, dann viel Text auf einmal): Die
 * Blase zeigt bis zum ersten Delta einen Platzhalter, Annahmen über Token-für-Token-Streaming gibt es nicht.
 * Der RAG-Schalter wird in AP4 nur durchgereicht und ignoriert; AP22 wertet ihn aus.
 */
public final class ChatServiceBinding implements ChatShellActions {

    private static final Logger LOG = Logger.getLogger(ChatServiceBinding.class.getName());

    static final String DETAIL_PREFIX = "Technische Ursache: ";
    static final String HINT_PREFIX = "Hinweis: ";

    private final ChatService chatService;
    private final ChatShellModel model;
    private final Executor uiExecutor;
    private final ChatConversationId conversationId;
    private ChatTurn runningTurn;

    public ChatServiceBinding(ChatService chatService, ChatConversationId conversationId, ChatShellModel model,
                              Executor uiExecutor) {
        if (chatService == null || conversationId == null || model == null || uiExecutor == null) {
            throw new IllegalArgumentException("chatService, conversationId, model and uiExecutor must not be null");
        }
        this.chatService = chatService;
        this.conversationId = conversationId;
        this.model = model;
        this.uiExecutor = uiExecutor;
    }

    public ChatConversationId conversationId() {
        return conversationId;
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
            ChatTurn turn = chatService.send(conversationId, text, listener);
            if (!turn.isDone()) {
                runningTurn = turn;
            }
        } catch (RuntimeException e) {
            // Use Case hat den Turn abgelehnt (z. B. Konversation beschäftigt): sichtbar machen, nicht verschlucken.
            listener.closed = true;
            model.failAssistantMessage("Die Nachricht konnte nicht gesendet werden.");
        }
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void stopRequested() {
        ChatTurn turn = runningTurn;
        if (turn != null) {
            turn.cancel();
        }
    }

    /**
     * Für Menschen lesbare Fehlermeldung: erste Zeile die Fehlerart, dann die technische Ursache (Ursachenkette des
     * Adapters, die laut Port-Vertrag keine Zugangsdaten enthält; Bearer-Token werden zusätzlich maskiert) und,
     * wenn die Ursache bekannt ist, ein Hinweis, was zu tun ist (Zertifikat, Proxy, KeePass). Ohne diese Zeilen
     * stand beim ersten Start gegen die echte API nur "nicht erreichbar" in der Blase, und niemand konnte sehen,
     * ob Java dem Zertifikat misstraut oder den Proxy nicht kennt.
     */
    static String describe(ChatCompletionException error) {
        StringBuilder text = new StringBuilder(headline(error));
        String detail = ConnectionDiagnosis.detail(error);
        if (!detail.isEmpty()) {
            text.append('\n').append(DETAIL_PREFIX).append(detail);
        }
        String hint = ConnectionDiagnosis.hint(error);
        if (hint != null) {
            text.append('\n').append(HINT_PREFIX).append(hint);
        }
        return text.toString();
    }

    /** Die erste Zeile: Fehlerart und Status, ohne technische Details. */
    static String headline(ChatCompletionException error) {
        String reason;
        switch (error.kind()) {
            case AUTHENTICATION:
                reason = "Anmeldung am KI-Dienst fehlgeschlagen.";
                break;
            case RATE_LIMITED:
                reason = "Der KI-Dienst ist ausgelastet. Bitte später erneut versuchen.";
                break;
            case INVALID_REQUEST:
                reason = "Der KI-Dienst hat die Anfrage abgelehnt.";
                break;
            case PROVIDER_ERROR:
                reason = "Fehler im KI-Dienst.";
                break;
            case TRANSPORT:
                reason = "Der KI-Dienst ist nicht erreichbar.";
                break;
            default:
                reason = "Unerwartete Antwort des KI-Dienstes.";
                break;
        }
        if (error.statusCode() > 0) {
            reason = reason + " (Status " + error.statusCode() + ")";
        }
        return reason;
    }

    /** Leitet Callbacks eines Turns auf den UI-Thread; nach dem Abschluss wird nichts mehr weitergereicht. */
    private final class TurnListener implements ChatTurnListener {

        private volatile boolean closed;

        @Override
        public void onDelta(final String text) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    model.appendAssistantDelta(text);
                }
            });
        }

        @Override
        public void onCompleted(ChatResponse response) {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.completeAssistantMessage();
                }
            });
        }

        @Override
        public void onFailed(final ChatCompletionException error) {
            final String message = describe(error);
            LOG.log(Level.WARNING, "Chat-Anfrage fehlgeschlagen (" + error.kind() + "): " + error.getMessage(), error);
            finish(new Runnable() {
                @Override
                public void run() {
                    model.failAssistantMessage(message);
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
