package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.chat.ChatTurn;
import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Use Case "Chatten mit Wissen" (RAG): legt vor einen normalen Chat-Turn optional Retrieval und Kontextaufbau.
 *
 * <ul>
 *   <li><b>RAG aus</b>: exakt der normale {@link ChatService}-Pfad, ohne Index- oder Embedding-Aufruf.</li>
 *   <li><b>RAG an</b>: die unveränderte Nutzerfrage wird über {@link RetrieveKnowledgeUseCase} gesucht, die
 *       Treffer baut der {@link PromptContextAssembler} zum Kontextblock, und
 *       {@link ChatService#sendWithContext} gibt ihn für genau diesen Turn als System-Anteil mit. In die
 *       Historie gelangen nur Nutzerfrage und Antwort.</li>
 *   <li>Keine Treffer: Turn ohne Kontext, {@link RagChatTurn#sources()} ist leer.</li>
 *   <li>Retrieval ganz ausgefallen ({@link KnowledgeRetrievalException}): Turn ohne Kontext,
 *       {@link RagChatTurn#retrievalFailed()} ist gesetzt, damit die Oberfläche das anzeigen kann. Fällt nur ein
 *       Suchpfad aus, stehen die Warnungen in {@link RagChatTurn#warnings()}.</li>
 * </ul>
 *
 * <p>Konversationen (öffnen, schließen, System-Prompt, Abbruch) verwaltet weiterhin der gemeinsame
 * {@link ChatService}. {@code send} blockiert für das Retrieval (Embedding-Aufruf, Indexsuche) und kehrt
 * zurück, sobald das Streaming gestartet ist; Aufrufer aus der Oberfläche rufen es nicht auf dem Event-Thread
 * auf. Ein Abbruch über {@link ChatTurn#cancel()} wirkt ab Streamingbeginn.
 */
public final class RagChatUseCase {

    private final ChatService chat;
    private final RetrieveKnowledgeUseCase retrieval;
    private final PromptContextAssembler assembler;
    /** Konversationen, für die gerade ein Retrieval läuft; verhindert doppelte Retrievals bei Doppelklick. */
    private final Set<ChatConversationId> retrieving = new HashSet<ChatConversationId>();

    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler) {
        if (chat == null || retrieval == null) {
            throw new IllegalArgumentException("chat und retrieval sind Pflicht");
        }
        this.chat = chat;
        this.retrieval = retrieval;
        this.assembler = assembler == null ? new PromptContextAssembler(ContextSettings.defaults()) : assembler;
    }

    /** Der gemeinsame Chat-Use-Case, über den Konversationen verwaltet werden. */
    public ChatService chatService() {
        return chat;
    }

    public RagChatTurn send(ChatConversationId id, String userText, RagOptions rag, ChatTurnListener listener) {
        return send(id, userText, rag, null, listener);
    }

    /**
     * @param rag     RAG an/aus und Quellenfilter; {@code null} heißt aus
     * @param options wie bei {@link ChatService#send(ChatConversationId, String, ChatOptions, ChatTurnListener)}
     * @throws IllegalStateException    wenn in der Konversation schon ein Turn oder ein RAG-Retrieval läuft
     *                                  (geprüft vor dem Retrieval; ein gleichzeitiger normaler {@code send} während
     *                                  des Retrievals lässt diesen Aufruf erst beim Start des Turns scheitern)
     * @throws IllegalArgumentException bei leerem Text oder unbekannter Konversation
     */
    public RagChatTurn send(ChatConversationId id, String userText, RagOptions rag, ChatOptions options,
                            ChatTurnListener listener) {
        if (userText == null || userText.trim().isEmpty()) {
            throw new IllegalArgumentException("user text must not be blank");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (rag == null || !rag.isEnabled()) {
            ChatTurn turn = chat.send(id, userText, options, listener);
            return new RagChatTurn(turn, false, PromptContext.empty(0), Collections.<RetrievalWarning>emptyList(),
                    false);
        }
        synchronized (retrieving) {
            if (chat.isBusy(id) || !retrieving.add(id)) {
                throw new IllegalStateException("conversation " + id + " is busy");
            }
        }
        try {
            return sendWithRetrieval(id, userText, rag, options, listener);
        } finally {
            synchronized (retrieving) {
                retrieving.remove(id);
            }
        }
    }

    private RagChatTurn sendWithRetrieval(ChatConversationId id, String userText, RagOptions rag,
                                          ChatOptions options, ChatTurnListener listener) {
        PromptContext context;
        List<RetrievalWarning> warnings;
        boolean failed = false;
        try {
            RetrievalResult result = retrieval.retrieve(userText, rag.sources());
            context = assembler.assemble(result.hits());
            warnings = result.warnings();
        } catch (KnowledgeRetrievalException e) {
            context = PromptContext.empty(0);
            warnings = e.warnings();
            failed = true;
        }
        ChatTurn turn = chat.sendWithContext(id, userText, context.text(), options, listener);
        return new RagChatTurn(turn, true, context, warnings, failed);
    }
}
