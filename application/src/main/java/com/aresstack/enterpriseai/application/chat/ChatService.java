package com.aresstack.enterpriseai.application.chat;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.domain.chat.ChatConversation;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use Case "Chatten": verwaltet mehrere Konversationen mit eigener Historie und führt je Konversation
 * höchstens einen Turn gleichzeitig aus. Verschiedene Konversationen dürfen parallel streamen.
 *
 * <p>Historienregeln:
 * <ul>
 *   <li>Die Nutzernachricht steht ab {@link #send} in der Historie, auch wenn der Turn scheitert oder
 *   abgebrochen wird.</li>
 *   <li>Die Antwort wird erst bei vollständigem Abschluss übernommen; abgebrochene oder gescheiterte
 *   Teilantworten gelangen nicht in die Historie.</li>
 *   <li>Der System-Prompt wird getrennt gehalten und jeder Anfrage vorangestellt.</li>
 * </ul>
 *
 * <p>Threadsicher. Abhängigkeiten kommen über den Konstruktor; die Klasse hält keinen globalen Zustand.
 */
public final class ChatService {

    private final ChatCompletionPort chatPort;
    private final ChatOptions defaultOptions;
    private final Object lock = new Object();
    private final Map<ChatConversationId, Session> sessions = new LinkedHashMap<ChatConversationId, Session>();

    public ChatService(ChatCompletionPort chatPort) {
        this(chatPort, ChatOptions.defaults());
    }

    /**
     * @param defaultOptions Optionen für jeden Turn, soweit {@link #send(ChatConversationId, String,
     *                       ChatOptions, ChatTurnListener)} sie nicht überschreibt
     */
    public ChatService(ChatCompletionPort chatPort, ChatOptions defaultOptions) {
        if (chatPort == null) {
            throw new IllegalArgumentException("chatPort must not be null");
        }
        this.chatPort = chatPort;
        this.defaultOptions = defaultOptions == null ? ChatOptions.defaults() : defaultOptions;
    }

    public ChatConversationId openConversation() {
        return openConversation(null);
    }

    /** Legt eine neue, leere Konversation an. */
    public ChatConversationId openConversation(String systemPrompt) {
        ChatConversationId id = ChatConversationId.random();
        synchronized (lock) {
            sessions.put(id, new Session(ChatConversation.empty(id).withSystemPrompt(systemPrompt)));
        }
        return id;
    }

    /** @return die Kennungen aller offenen Konversationen in Anlagereihenfolge */
    public List<ChatConversationId> conversationIds() {
        synchronized (lock) {
            return new ArrayList<ChatConversationId>(sessions.keySet());
        }
    }

    /** @return unveränderlicher Stand der Konversation */
    public ChatConversation conversation(ChatConversationId id) {
        synchronized (lock) {
            return session(id).conversation;
        }
    }

    /** Ändert den System-Prompt; wirkt ab dem nächsten Turn. {@code null} oder leer entfernt ihn. */
    public void setSystemPrompt(ChatConversationId id, String systemPrompt) {
        synchronized (lock) {
            Session session = session(id);
            session.conversation = session.conversation.withSystemPrompt(systemPrompt);
        }
    }

    /**
     * Leert die Historie; der System-Prompt bleibt.
     *
     * @throws IllegalStateException wenn gerade ein Turn läuft
     */
    public void clearHistory(ChatConversationId id) {
        synchronized (lock) {
            Session session = session(id);
            if (session.running != null) {
                throw new IllegalStateException("conversation " + id + " is busy");
            }
            session.conversation = session.conversation.cleared();
        }
    }

    /** Schließt die Konversation; ein laufender Turn wird abgebrochen. Unbekannte Kennungen sind kein Fehler. */
    public void closeConversation(ChatConversationId id) {
        ChatTurn running;
        synchronized (lock) {
            Session session = sessions.remove(id);
            running = session == null ? null : session.running;
        }
        if (running != null) {
            running.cancel();
        }
    }

    public boolean isBusy(ChatConversationId id) {
        synchronized (lock) {
            return session(id).running != null;
        }
    }

    /** Bricht den laufenden Turn der Konversation ab, falls es einen gibt. */
    public void cancel(ChatConversationId id) {
        ChatTurn running;
        synchronized (lock) {
            running = session(id).running;
        }
        if (running != null) {
            running.cancel();
        }
    }

    public ChatTurn send(ChatConversationId id, String userText, ChatTurnListener listener) {
        return send(id, userText, null, listener);
    }

    /**
     * Hängt {@code userText} als Nutzernachricht an und streamt die Antwort.
     *
     * @param options überschreibt einzelne Standardoptionen dieses Service; {@code null} für keine
     * @throws IllegalStateException    wenn in der Konversation schon ein Turn läuft
     * @throws IllegalArgumentException bei leerem Text oder unbekannter Konversation
     */
    public ChatTurn send(ChatConversationId id, String userText, ChatOptions options, ChatTurnListener listener) {
        return start(id, userText, null, options, listener);
    }

    /**
     * Wie {@link #send(ChatConversationId, String, ChatOptions, ChatTurnListener)}, gibt dem Modell aber für
     * genau diesen Turn zusätzlich {@code context} mit, z. B. abgerufenes Wissen (RAG).
     *
     * <p>Der Kontext wird als System-Anteil an den System-Prompt angehängt (durch eine Leerzeile getrennt) bzw.
     * bildet ohne System-Prompt die einzige System-Nachricht; er steht damit vor der Historie und getrennt von
     * der Nutzerfrage. Er gelangt nicht in die Historie: Dort stehen wie bei {@code send} nur die unveränderte
     * Nutzerfrage und die Antwort, spätere Turns sehen den Kontext nicht mehr. Die Rolle {@code developer} wird
     * nie verwendet.
     *
     * @param context zusätzlicher System-Anteil; {@code null} oder leer verhält sich wie {@code send}
     */
    public ChatTurn sendWithContext(ChatConversationId id, String userText, String context, ChatOptions options,
                                    ChatTurnListener listener) {
        return start(id, userText, context == null || context.trim().isEmpty() ? null : context, options, listener,
                chatPort);
    }

    /**
     * Wie {@link #sendWithContext(ChatConversationId, String, String, ChatOptions, ChatTurnListener)}, aber dieser
     * eine Turn läuft über {@code via} statt über den Standard-Port, z. B. über die Werkzeug-Schleife
     * (Tool-Calling). Historienregeln und Abbruch bleiben gleich.
     *
     * @param via Port nur für diesen Turn; {@code null} heißt der Standard-Port
     */
    public ChatTurn sendWithContext(ChatConversationId id, String userText, String context, ChatOptions options,
                                    ChatTurnListener listener, ChatCompletionPort via) {
        return start(id, userText, context == null || context.trim().isEmpty() ? null : context, options, listener,
                via == null ? chatPort : via);
    }

    private ChatTurn start(ChatConversationId id, String userText, String context, ChatOptions options,
                           ChatTurnListener listener) {
        return start(id, userText, context, options, listener, chatPort);
    }

    private ChatTurn start(ChatConversationId id, String userText, String context, ChatOptions options,
                           ChatTurnListener listener, ChatCompletionPort port) {
        if (userText == null || userText.trim().isEmpty()) {
            throw new IllegalArgumentException("user text must not be blank");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        ChatOptions effective = options == null ? defaultOptions : options.withFallback(defaultOptions);
        ChatTurn turn = new ChatTurn(this, id, listener);
        ChatRequest request;
        synchronized (lock) {
            Session session = session(id);
            if (session.running != null) {
                throw new IllegalStateException("conversation " + id + " is busy");
            }
            session.conversation = session.conversation.append(ChatMessage.user(userText));
            session.running = turn;
            request = context == null
                    ? ChatRequest.of(session.conversation, effective)
                    : new ChatRequest(withContext(session.conversation, context), effective);
        }
        ChatTask task;
        try {
            task = port.stream(request, turn.portListener());
        } catch (ChatCompletionException e) {
            turn.failedToStart(e);
            return turn;
        } catch (RuntimeException e) {
            turn.failedToStart(new ChatCompletionException(
                    ChatErrorKind.PROTOCOL,
                    "chat port failed to start the request", e));
            return turn;
        }
        turn.attach(task);
        return turn;
    }

    /** Rückmeldung eines Turns beim Abschluss; {@code answer} ist nur bei Erfolg gesetzt. */
    void turnFinished(ChatTurn turn, ChatMessage answer) {
        synchronized (lock) {
            Session session = sessions.get(turn.conversationId());
            if (session == null || session.running != turn) {
                return;
            }
            session.running = null;
            if (answer != null) {
                session.conversation = session.conversation.append(answer);
            }
        }
    }

    private static List<ChatMessage> withContext(ChatConversation conversation, String context) {
        String system = conversation.systemPrompt() == null ? context : conversation.systemPrompt() + "\n\n" + context;
        List<ChatMessage> messages = new ArrayList<ChatMessage>(conversation.messages().size() + 1);
        messages.add(ChatMessage.system(system));
        messages.addAll(conversation.messages());
        return messages;
    }

    private Session session(ChatConversationId id) {
        Session session = sessions.get(id);
        if (session == null) {
            throw new IllegalArgumentException("unknown conversation " + id);
        }
        return session;
    }

    private static final class Session {
        ChatConversation conversation;
        ChatTurn running;

        Session(ChatConversation conversation) {
            this.conversation = conversation;
        }
    }
}
