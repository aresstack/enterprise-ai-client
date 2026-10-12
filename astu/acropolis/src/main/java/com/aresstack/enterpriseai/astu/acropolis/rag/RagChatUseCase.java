package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurn;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurnListener;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;

import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

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
 *   <li><b>Wissenskontext der Unterhaltung</b> (mit Nachrecherche): Die Treffer, die in den Kontextblock kamen, bleiben
 *       je Konversation ({@link ConversationKnowledgeContext}), auch wenn die Antwort danach scheitert. Vor der
 *       nächsten Suche prüft das Modell, ob sie die neue Nachricht schon beantworten; dann wird nicht gesucht. Sonst
 *       folgt eine gezielte Nachsuche; zum Thema passende frühere Treffer bleiben hinter den neuen, bei Themenwechsel
 *       fallen sie weg. {@code context.maxSources} und {@code context.maxTokens} gelten wie immer.</li>
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
    /** Adaptiver Modus (Reranking, progressive Evidenz); {@code null} = feste Treffermenge wie bisher. */
    private final Supplier<EvidencePolicy> evidence;
    private final RelevanceRanking ranking = RelevanceRanking.byChatModel();
    /** Konversationen, für die gerade ein Retrieval läuft; verhindert doppelte Retrievals bei Doppelklick. */
    private final Set<ChatConversationId> retrieving = new HashSet<ChatConversationId>();
    /** Der zuletzt verwendete Wissenskontext je Konversation (nur mit Nachrecherche). */
    private final Map<ChatConversationId, ConversationKnowledgeContext> contexts =
            new ConcurrentHashMap<ChatConversationId, ConversationKnowledgeContext>();

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
        this(chat, retrieval, assembler, preparation, followUps, null);
    }

    /**
     * Adaptiver Modus: Treffer werden gegen die Nachricht bewertet ({@link RelevanceRanking}) und einzeln in den
     * Kontext übernommen, beste zuerst; nach jeder neuen Quelle prüft {@code followUps}, ob die Evidenz reicht, und die
     * Suche endet, sobald sie reicht. Die Grenzen kommen je Turn aus {@code evidence} (ohne Neustart); die Zahl der
     * Nachsuchen von dort, nicht aus {@code followUps}.
     *
     * @param evidence Grenzen je Turn; {@code null} = fester Ablauf wie im Konstruktor ohne diesen Parameter
     */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation, FollowUpResearch followUps,
                          Supplier<EvidencePolicy> evidence) {
        if (chat == null || retrieval == null) {
            throw new IllegalArgumentException("chat und retrieval sind Pflicht");
        }
        this.chat = chat;
        this.retrieval = retrieval;
        this.assembler = assembler == null ? new PromptContextAssembler(ContextSettings.defaults()) : assembler;
        this.preparation = preparation == null ? RetrievalPreparation.none() : preparation;
        this.followUps = followUps == null ? FollowUpResearch.off() : followUps;
        this.evidence = evidence;
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
            RetrievalProgress steps = progress == null ? RetrievalProgress.none() : progress;
            EvidencePolicy policy = evidence == null ? null : evidence.get();
            if (policy != null) {
                return sendAdaptive(id, userText, rag, options, listener, extraContext, via, steps, policy);
            }
            return sendWithRetrieval(id, userText, rag, options, listener, extraContext, via, steps);
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
        // Erst der Wissenskontext früherer Nachrichten: beantwortet er die neue, wird nicht gesucht.
        ConversationKnowledgeContext prior = followUps.isEnabled() ? contexts.get(id) : null;
        List<String> priorQueries = Collections.emptyList();
        int reused = 0;
        if (prior != null && !prior.isEmpty()) {
            FollowUpResearch.PriorVerdict verdict = followUps.checkPrior(chat, history, userText, prior.queries(),
                    assembler.assemble(prior.hits()).text(), options);
            if (verdict.isSufficient()) {
                hits = new ArrayList<RetrievedChunk>(prior.hits());
                priorQueries = prior.queries();
                reused = assembler.assemble(hits).sources().size();
                query = null;
                progress.reusingSources(reused);
            } else if (verdict.isKnown()) {
                if (verdict.isRelated()) {
                    hits = new ArrayList<RetrievedChunk>(prior.hits());
                    priorQueries = prior.queries();
                }
                if (verdict.query() != null) {
                    query = verdict.query();
                }
                progress.reuseInsufficient(query, hits.size());
            }
        }
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
                if (queries.size() == 1 && hits.isEmpty()) {
                    warnings.addAll(e.warnings());
                    failed = true;
                }
                break; // eine ausgefallene Nachsuche lässt den bisherigen Kontext stehen
            }
            if (queries.size() > followUps.maxFollowUps() || progress.isCancelled()) {
                break;
            }
            progress.contextCheckStarted();
            query = followUps.nextQuery(chat, history, userText, all(priorQueries, queries),
                    assembler.assemble(hits).text(), options);
        }
        PromptContext context = failed ? PromptContext.empty(0) : assembler.assemble(hits);
        progress.contextReady(context.sources().size());
        List<String> allQueries = all(priorQueries, queries);
        if (followUps.isEnabled() && !context.sources().isEmpty()) {
            // Vor dem Antwortturn gemerkt: scheitert die Antwort, bleibt der Kontext für die nächste Nachricht.
            contexts.put(id, new ConversationKnowledgeContext(allQueries, accepted(hits, context)));
        }
        String combined = join(join(context.text(), followUps.isEnabled() ? searchNote(allQueries) : null),
                extraContext);
        ChatTurn turn = chat.sendWithContext(id, userText, combined, options, listener, via);
        return new RagChatTurn(turn, true, context, warnings, failed, queries, hitCounts, reused);
    }

    /**
     * Der zuletzt verwendete Wissenskontext der Konversation (leer, wenn keiner), etwa zum Speichern mit dem Chat.
     */
    public ConversationKnowledgeContext knowledgeContext(ChatConversationId id) {
        ConversationKnowledgeContext context = id == null ? null : contexts.get(id);
        return context == null ? ConversationKnowledgeContext.empty() : context;
    }

    /**
     * Setzt den Wissenskontext einer (geöffneten) Konversation aus gespeicherten Chunk-IDs: die Auszüge kommen aus dem
     * Index, nicht aus der Chat-Datei. Fehlt dort alles, bleibt die Konversation ohne früheren Kontext.
     */
    public void restoreKnowledgeContext(ChatConversationId id, List<String> queries, List<KnowledgeChunkId> chunkIds) {
        if (id == null) {
            return;
        }
        List<RetrievedChunk> restored = retrieval.restore(chunkIds);
        if (restored.isEmpty()) {
            contexts.remove(id);
        } else {
            contexts.put(id, new ConversationKnowledgeContext(queries, restored));
        }
    }

    /** Vergisst den Wissenskontext einer geschlossenen Konversation. */
    public void forgetKnowledgeContext(ChatConversationId id) {
        if (id != null) {
            contexts.remove(id);
        }
    }

    /** Die Treffer, die der Kontextblock aufnahm: er nimmt in Reihenfolge auf und bricht beim ersten ab. */
    private static List<RetrievedChunk> accepted(List<RetrievedChunk> hits, PromptContext context) {
        return new ArrayList<RetrievedChunk>(hits.subList(0, Math.min(hits.size(), context.sources().size())));
    }

    private static List<String> all(List<String> earlier, List<String> latest) {
        List<String> all = new ArrayList<String>(earlier);
        for (String query : latest) {
            if (!all.contains(query)) {
                all.add(query);
            }
        }
        return all;
    }

    /** Wie die Übernahme eines Trefferbündels endete. */
    private enum Intake {
        /** Die Prüfung sagt „reicht“: sofort antworten. */
        SUFFICIENT,
        /** Die Obergrenze der Quellen ist erreicht. */
        FULL,
        /** Kein lesbares Urteil: mit dem Bisherigen antworten. */
        NO_VERDICT,
        /** Noch nicht genug; weitere Treffer oder eine Nachsuche können helfen. */
        OPEN
    }

    /** Zustand der adaptiven Suche eines Turns. */
    private final class Evidence {

        final List<RetrievedChunk> accepted = new ArrayList<RetrievedChunk>();
        final Set<String> seen = new HashSet<String>();
        final EvidencePolicy policy;
        final ChatService chatService;
        final List<ChatMessage> history;
        final String message;
        final List<String> queries;
        final ChatOptions options;
        final RetrievalProgress progress;
        int checkedAt = -1;
        String suggestion;
        /** Suchanfragen des früheren Wissenskontexts, den diese Nachricht weiterführt. */
        List<String> priorQueries = Collections.emptyList();

        Evidence(EvidencePolicy policy, List<ChatMessage> history, String message, List<String> queries,
                 ChatOptions options, RetrievalProgress progress) {
            this.policy = policy;
            this.chatService = chat;
            this.history = history;
            this.message = message;
            this.queries = queries;
            this.options = options;
            this.progress = progress;
        }

        /** Die noch nicht bewerteten Treffer, in Suchreihenfolge; merkt sie als gesehen. */
        List<RetrievedChunk> fresh(List<RetrievedChunk> hits) {
            List<RetrievedChunk> fresh = new ArrayList<RetrievedChunk>();
            for (RetrievedChunk hit : hits) {
                if (seen.add(hit.chunk().id().value())) {
                    fresh.add(hit);
                }
            }
            return fresh;
        }

        int maxSources() {
            return assembler.settings().maxSources();
        }

        /** Die verlangte Mindestzahl, höchstens die Obergrenze (mehr ließe sich nie erfüllen). */
        int minSources() {
            return Math.min(policy.minSources(), maxSources());
        }

        int missingSources() {
            return Math.max(0, minSources() - independentSources());
        }

        /** Verschiedene Seiten unter den übernommenen Abschnitten. */
        int independentSources() {
            Set<String> resources = new HashSet<String>();
            for (RetrievedChunk hit : accepted) {
                resources.add(hit.resource().id().value());
            }
            return resources.size();
        }

        /**
         * Übernimmt relevante Treffer, beste zuerst, und prüft nach jeder neuen Quelle (bis {@link #STEPWISE_CHECKS}
         * Quellen, danach am Ende des Bündels), ob die Evidenz reicht.
         */
        Intake take(List<RetrievedChunk> relevant, String query) {
            for (RetrievedChunk hit : relevant) {
                if (accepted.size() >= maxSources()) {
                    progress.evidenceFull(accepted.size());
                    return Intake.FULL;
                }
                List<RetrievedChunk> candidate = new ArrayList<RetrievedChunk>(accepted);
                candidate.add(hit);
                if (assembler.assemble(candidate).sources().size() < candidate.size()) {
                    continue; // passt nicht mehr ins Token-Budget; ein kürzerer Abschnitt vielleicht schon
                }
                accepted.add(hit);
                if (missingSources() > 0 || accepted.size() > STEPWISE_CHECKS) {
                    continue;
                }
                Intake intake = check(query);
                if (intake != Intake.OPEN) {
                    return intake;
                }
            }
            if (accepted.size() >= maxSources()) {
                progress.evidenceFull(accepted.size());
                return Intake.FULL;
            }
            if (missingSources() == 0 && accepted.size() > checkedAt && !accepted.isEmpty()) {
                return check(query);
            }
            return Intake.OPEN;
        }

        /** Prüft die bisherige Evidenz; merkt sich die vorgeschlagene Nachsuche. */
        Intake check(String query) {
            progress.contextCheckStarted();
            int missing = missingSources();
            FollowUpResearch.Verdict verdict = followUps.check(chatService, history, message,
                    all(priorQueries, queries), assembler.assemble(accepted).text(), missing, options);
            checkedAt = accepted.size();
            if (!verdict.isKnown()) {
                return Intake.NO_VERDICT;
            }
            progress.evidenceChecked(accepted.size(), independentSources(), verdict.isSufficient());
            if (verdict.isSufficient()) {
                return Intake.SUFFICIENT;
            }
            suggestion = verdict.query();
            return Intake.OPEN;
        }
    }

    /** Bis zu so vielen Quellen wird nach jeder neuen geprüft, danach nur am Ende eines Trefferbündels (kippbar). */
    static final int STEPWISE_CHECKS = 3;

    private RagChatTurn sendAdaptive(ChatConversationId id, String userText, RagOptions rag, ChatOptions options,
                                     ChatTurnListener listener, String extraContext, ChatCompletionPort via,
                                     RetrievalProgress progress, EvidencePolicy policy) {
        List<String> queries = new ArrayList<String>();
        List<Integer> hitCounts = new ArrayList<Integer>();
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        boolean failed = false;
        List<ChatMessage> history = chat.conversation(id).messages();
        Evidence evidence = new Evidence(policy, history, userText, queries, options, progress);
        RelevanceRanking judge = policy.reranking() ? ranking : RelevanceRanking.off();
        int maxSearches = 1 + policy.maxFollowUps();
        Intake intake = Intake.OPEN;
        String query = userText;
        // Erst der Wissenskontext früherer Nachrichten: beantwortet er die neue, wird nicht gesucht. Passt er zum
        // Thema, wird er zusammen mit den neuen Treffern gegen die Nachricht bewertet (hinter ihnen).
        List<RetrievedChunk> carried = Collections.emptyList();
        int reused = 0;
        ConversationKnowledgeContext prior = contexts.get(id);
        if (prior != null && !prior.isEmpty()) {
            FollowUpResearch.PriorVerdict verdict = followUps.checkPrior(chat, history, userText, prior.queries(),
                    assembler.assemble(prior.hits()).text(), options);
            if (verdict.isSufficient()) {
                evidence.accepted.addAll(prior.hits());
                evidence.priorQueries = prior.queries();
                reused = assembler.assemble(prior.hits()).sources().size();
                query = null;
                intake = Intake.SUFFICIENT;
                progress.reusingSources(reused);
            } else if (verdict.isKnown()) {
                if (verdict.isRelated()) {
                    carried = prior.hits();
                    evidence.priorQueries = prior.queries();
                }
                if (verdict.query() != null) {
                    query = verdict.query();
                }
                progress.reuseInsufficient(query, carried.size());
            }
        }
        while (query != null && intake == Intake.OPEN && !progress.isCancelled()) {
            queries.add(query);
            evidence.suggestion = null;
            progress.searchStarted(query, queries.size(), maxSearches);
            progress.liveSearchStarted(query);
            RetrievalPreparation.Stage stage = RetrievalPreparation.Stage.DONE;
            try {
                stage = preparation.begin(query, rag.sources());
            } catch (RuntimeException e) {
                // Die Live-Suche ist eine Ergänzung; ohne sie antwortet der Turn aus dem vorhandenen Index.
            }
            if (progress.isCancelled()) {
                queries.remove(queries.size() - 1);
                break;
            }
            int checkedBefore = evidence.checkedAt;
            for (int round = 0; ; round++) {
                RetrievalResult result;
                try {
                    result = retrieval.retrieve(query, rag.sources(), progress);
                } catch (KnowledgeRetrievalException e) {
                    if (round == 0) {
                        hitCounts.add(-1);
                        progress.searchFailed(query);
                        if (queries.size() == 1 && evidence.accepted.isEmpty() && carried.isEmpty()) {
                            warnings.addAll(e.warnings());
                            failed = true;
                        } else if (queries.size() == 1 && evidence.accepted.isEmpty()) {
                            evidence.accepted.addAll(carried); // die Suche fiel aus, der frühere Kontext bleibt
                        }
                    }
                    intake = Intake.NO_VERDICT; // eine ausgefallene Suche lässt das Bisherige stehen
                    break;
                }
                if (round == 0) {
                    warnings.addAll(result.warnings()); // dieselbe Anfrage fällt beim Nachladen gleich aus
                    hitCounts.add(result.hits().size());
                    progress.hitsFound(query, result.hits().size());
                } else {
                    hitCounts.set(hitCounts.size() - 1, Math.max(hitCounts.get(hitCounts.size() - 1),
                            result.hits().size()));
                }
                List<RetrievedChunk> fresh = evidence.fresh(round == 0 && queries.size() == 1
                        ? newestFirst(result.hits(), carried) : result.hits());
                if (!fresh.isEmpty()) {
                    if (judge.isEnabled()) {
                        progress.rankingStarted(fresh.size());
                    }
                    RelevanceRanking.Ranked ranked = judge.rank(chat, history, userText, query, fresh, options);
                    progress.candidatesRanked(ranked.candidates(), ranked.relevant().size());
                    intake = evidence.take(ranked.relevant(), query);
                }
                if (intake != Intake.OPEN || progress.isCancelled()) {
                    break;
                }
                progress.morePagesStarted();
                boolean more;
                try {
                    more = stage.awaitMore();
                } catch (RuntimeException e) {
                    more = false;
                }
                if (!more || progress.isCancelled()) {
                    break;
                }
            }
            if (intake != Intake.OPEN || progress.isCancelled() || queries.size() >= maxSearches) {
                break;
            }
            if (evidence.checkedAt == checkedBefore || evidence.checkedAt < evidence.accepted.size()) {
                // Diese Suche brachte keine geprüfte Evidenz (nichts Relevantes, Mindestzahl offen): das Urteil
                // liefert die genauere Anfrage für die Nachsuche.
                intake = evidence.check(query);
                if (intake != Intake.OPEN) {
                    break;
                }
            }
            query = evidence.suggestion;
        }
        PromptContext context = failed ? PromptContext.empty(0) : assembler.assemble(evidence.accepted);
        progress.contextReady(context.sources().size());
        List<String> allQueries = all(evidence.priorQueries, queries);
        if (!context.sources().isEmpty()) {
            // Vor dem Antwortturn gemerkt: scheitert die Antwort, bleibt der Kontext für die nächste Nachricht.
            contexts.put(id, new ConversationKnowledgeContext(allQueries, accepted(evidence.accepted, context)));
        }
        String combined = join(join(context.text(), searchNote(allQueries)), extraContext);
        ChatTurn turn = chat.sendWithContext(id, userText, combined, options, listener, via);
        return new RagChatTurn(turn, true, context, warnings, failed, queries, hitCounts, reused);
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
