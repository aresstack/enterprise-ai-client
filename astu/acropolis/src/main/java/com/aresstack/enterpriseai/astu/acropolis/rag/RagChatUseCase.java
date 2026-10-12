package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurn;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurnListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;

import com.aresstack.enterpriseai.astu.chat.ChatMessage;

import java.util.ArrayList;
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
 *   <li><b>Nachrecherche</b> ({@link FollowUpResearch}, wenn eingeschaltet): reicht der Kontext für die
 *       Nachricht samt Verlauf nicht, wird mit einer genaueren Anfrage erneut gesucht (Live-Suche und Index), die
 *       neuen Treffer stehen vorn. Danach nennt der Kontext die Suchanfragen und verbietet die Bitte um Dateien.</li>
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
    private final RetrievalPreparation preparation;
    private final FollowUpResearch followUps;
    /** Konversationen, für die gerade ein Retrieval läuft; verhindert doppelte Retrievals bei Doppelklick. */
    private final Set<ChatConversationId> retrieving = new HashSet<ChatConversationId>();

    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler) {
        this(chat, retrieval, assembler, RetrievalPreparation.none());
    }

    /** @param preparation läuft vor jedem Retrieval (z. B. Live-Suche der Quellen, die Treffer indexiert) */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation) {
        this(chat, retrieval, assembler, preparation, FollowUpResearch.off());
    }

    /** @param followUps ob und wie oft nachgesucht wird, wenn der Kontext die Nachricht nicht beantwortet */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation, FollowUpResearch followUps) {
        if (chat == null || retrieval == null) {
            throw new IllegalArgumentException("chat und retrieval sind Pflicht");
        }
        this.chat = chat;
        this.retrieval = retrieval;
        this.assembler = assembler == null ? new PromptContextAssembler(ContextSettings.defaults()) : assembler;
        this.preparation = preparation == null ? RetrievalPreparation.none() : preparation;
        this.followUps = followUps == null ? FollowUpResearch.off() : followUps;
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
        return send(id, userText, rag, options, listener, null, null);
    }

    /**
     * Wie {@link #send(ChatConversationId, String, RagOptions, ChatOptions, ChatTurnListener)}, mit einem
     * zusätzlichen System-Anteil (z. B. die Liste der Anhänge) und einem eigenen Port für diesen Turn (z. B. die
     * Werkzeug-Schleife). Beides gelangt nicht in die Historie.
     *
     * @param extraContext zusätzlicher System-Anteil oder {@code null}; steht hinter dem RAG-Kontext
     * @param via          Port nur für diesen Turn oder {@code null} für den Standard
     */
    public RagChatTurn send(ChatConversationId id, String userText, RagOptions rag, ChatOptions options,
                            ChatTurnListener listener, String extraContext, ChatCompletionPort via) {
        return send(id, userText, rag, options, listener, extraContext, via, RetrievalProgress.none());
    }

    /**
     * Wie {@link #send(ChatConversationId, String, RagOptions, ChatOptions, ChatTurnListener, String,
     * ChatCompletionPort)}; die Wissenssuche meldet ihren Fortschritt an {@code progress}, während sie läuft. Meldet
     * {@code progress} einen Abbruch, entfallen weitere Suchschritte; der Turn startet trotzdem (die Nachricht kommt in
     * die Historie) und wird vom Aufrufer abgebrochen. Ohne RAG meldet {@code progress} nichts.
     */
    public RagChatTurn send(ChatConversationId id, String userText, RagOptions rag, ChatOptions options,
                            ChatTurnListener listener, String extraContext, ChatCompletionPort via,
                            RetrievalProgress progress) {
        if (userText == null || userText.trim().isEmpty()) {
            throw new IllegalArgumentException("user text must not be blank");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (rag == null || !rag.isEnabled()) {
            ChatTurn turn = extraContext == null && via == null
                    ? chat.send(id, userText, options, listener)
                    : chat.sendWithContext(id, userText, extraContext, options, listener, via);
            return new RagChatTurn(turn, false, PromptContext.empty(0), Collections.<RetrievalWarning>emptyList(),
                    false);
        }
        synchronized (retrieving) {
            if (chat.isBusy(id) || !retrieving.add(id)) {
                throw new IllegalStateException("conversation " + id + " is busy");
            }
        }
        try {
            return sendWithRetrieval(id, userText, rag, options, listener, extraContext, via,
                    progress == null ? RetrievalProgress.none() : progress);
        } finally {
            synchronized (retrieving) {
                retrieving.remove(id);
            }
        }
    }

    private RagChatTurn sendWithRetrieval(ChatConversationId id, String userText, RagOptions rag,
                                          ChatOptions options, ChatTurnListener listener, String extraContext,
                                          ChatCompletionPort via, RetrievalProgress progress) {
        List<String> queries = new ArrayList<String>();
        List<Integer> hitCounts = new ArrayList<Integer>();
        List<RetrievedChunk> hits = new ArrayList<RetrievedChunk>();
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        boolean failed = false;
        List<ChatMessage> history = followUps.isEnabled()
                ? chat.conversation(id).messages() : Collections.<ChatMessage>emptyList();
        int maxSearches = 1 + followUps.maxFollowUps();
        String query = userText;
        while (query != null && !progress.isCancelled()) {
            queries.add(query);
            progress.searchStarted(query, queries.size(), maxSearches);
            progress.liveSearchStarted(query);
            try {
                preparation.prepare(query, rag.sources());
            } catch (RuntimeException e) {
                // Die Live-Suche ist eine Ergänzung; ohne sie antwortet der Turn aus dem vorhandenen Index.
            }
            if (progress.isCancelled()) {
                queries.remove(queries.size() - 1); // abgebrochen vor der Indexsuche: keine Suche mit Trefferzahl
                break;
            }
            try {
                RetrievalResult result = retrieval.retrieve(query, rag.sources(), progress);
                hitCounts.add(result.hits().size());
                hits = newestFirst(result.hits(), hits);
                warnings.addAll(result.warnings());
                progress.hitsFound(query, result.hits().size());
            } catch (KnowledgeRetrievalException e) {
                hitCounts.add(-1);
                progress.searchFailed(query);
                if (queries.size() == 1) {
                    warnings.addAll(e.warnings());
                    failed = true;
                }
                break; // eine ausgefallene Nachsuche lässt den bisherigen Kontext stehen
            }
            if (queries.size() > followUps.maxFollowUps() || progress.isCancelled()) {
                break;
            }
            progress.contextCheckStarted();
            query = followUps.nextQuery(chat, history, userText, queries, assembler.assemble(hits).text(),
                    options);
        }
        PromptContext context = failed ? PromptContext.empty(0) : assembler.assemble(hits);
        progress.contextReady(context.sources().size());
        String combined = join(join(context.text(), followUps.isEnabled() ? searchNote(queries) : null),
                extraContext);
        ChatTurn turn = chat.sendWithContext(id, userText, combined, options, listener, via);
        return new RagChatTurn(turn, true, context, warnings, failed, queries, hitCounts);
    }

    /** Die Treffer der neuesten Suche zuerst, danach die älteren, die sie nicht schon enthält. */
    private static List<RetrievedChunk> newestFirst(List<RetrievedChunk> latest, List<RetrievedChunk> earlier) {
        List<RetrievedChunk> merged = new ArrayList<RetrievedChunk>(latest);
        Set<String> seen = new HashSet<String>();
        for (RetrievedChunk hit : latest) {
            seen.add(hit.chunk().id().value());
        }
        for (RetrievedChunk hit : earlier) {
            if (seen.add(hit.chunk().id().value())) {
                merged.add(hit);
            }
        }
        return merged;
    }

    /** Was gesucht wurde, und dass eine Lücke nicht mit der Bitte um Dateien endet. */
    static String searchNote(List<String> queries) {
        StringBuilder note = new StringBuilder("In den angehakten Wissensquellen wurde gesucht mit:");
        for (String query : queries) {
            note.append("\n- ").append(query.replace('\r', ' ').replace('\n', ' ').trim());
        }
        note.append("\nBeantwortet der Kontext die Nachricht nicht, suche selbst mit knowledge_search und einer "
                + "genaueren Anfrage und lies Treffer mit knowledge_read, sofern dir diese Werkzeuge zur Verfügung "
                + "stehen. Reicht es danach immer noch nicht, sag das und nenne die Suchanfragen. Bitte den Nutzer "
                + "nicht, Dateien oder Unterlagen anzuhängen: die Wissensquellen sind angehakt.");
        return note.toString();
    }

    private static String join(String first, String second) {
        boolean hasFirst = first != null && !first.trim().isEmpty();
        boolean hasSecond = second != null && !second.trim().isEmpty();
        if (hasFirst && hasSecond) {
            return first + "\n\n" + second;
        }
        return hasFirst ? first : hasSecond ? second : null;
    }
}
