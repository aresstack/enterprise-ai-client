package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.EffectiveProjectContext;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ProjectContextResolver;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurn;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatTurnListener;
import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TransientTurnContextFactory;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TurnAnalysisObserver;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.ConversationContext;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.ModelAnswerPlanningService;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.ClarificationPort;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.LiteralEntityPreservationPolicy;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlanContextFactory;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlanningPolicy;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.UnresolvedTermQuestion;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.UserMessage;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;

import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 *   <li><b>Vorprüfung</b> ({@link RetrievalTriage}, wenn eingeschaltet): vor der ersten Suche entscheidet das Modell,
 *       ob die Nachricht Wissen aus den Quellen braucht und mit welcher Anfrage gesucht wird. Braucht sie keins (Gruß,
 *       Frage nach dem Assistenten oder zum Programm, Korrektur, Beschwerde), läuft der Turn ohne Suche und ohne
 *       Kontext ({@link RagChatTurn#retrievalRequested()} ist dann {@code false}). Mit früherem Wissenskontext
 *       übernimmt die Prüfung des {@link FollowUpResearch} diese Entscheidung in derselben Nebenanfrage.</li>
 *   <li><b>Herkunft</b> ({@link MessageOrigin}): stammt die Nachricht aus der Spracherkennung, erfahren Vorprüfung,
 *       Nachrecherche und Antwortturn das als Hinweis (nur für diesen Turn).</li>
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
    private final EvidenceEvaluator evaluator = new EvidenceEvaluator();
    /** The cross-encoder before the evaluator (model category RERANK); off until a reranker is attached. */
    private volatile CrossEncoderRanking crossEncoder = CrossEncoderRanking.off();
    /** Ob vor der ersten Suche geprüft wird, ob und womit gesucht wird. */
    private final RetrievalTriage triage;
    private final LinguisticAnalyzer analyzer;
    private final TurnAnalysisObserver analysisObserver;
    private final TransientTurnContextFactory turnContext = new TransientTurnContextFactory();
    /** The answer planning step before every answer, read per turn; {@code null} = off. */
    private final Supplier<AnswerPlanningPolicy> planning;
    private final AnswerPlanContextFactory planContext = new AnswerPlanContextFactory();
    private final LiteralEntityPreservationPolicy literalTerms = new LiteralEntityPreservationPolicy();
    /** Konversationen, für die gerade ein Retrieval läuft; verhindert doppelte Retrievals bei Doppelklick. */
    private final Set<ChatConversationId> retrieving = new HashSet<ChatConversationId>();
    /**
     * Der zuletzt verwendete Wissenskontext je Konversation (nur mit Nachrecherche), mit dem Projekt-Scope, unter dem
     * er entstand; passt der Scope nicht mehr zur aktuellen Projektzuordnung, gilt er als verworfen (#143).
     */
    private final Map<ChatConversationId, ScopedContext> contexts =
            new ConcurrentHashMap<ChatConversationId, ScopedContext>();
    /** The project scope resolved at the start of the running turn of each conversation. */
    private final Map<ChatConversationId, String> turnScopes = new ConcurrentHashMap<ChatConversationId, String>();
    /** Resolves the project context of every new turn (#143); {@link ProjectContextResolver#none()} without projects. */
    private volatile ProjectContextResolver projects = ProjectContextResolver.none();

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
     * Adaptiver Modus: Je Kandidatenrunde bewertet ein einziger Modellaufruf ({@link EvidenceEvaluator}) die Treffer
     * gegen die Nachricht, ob sie reichen und welche kleinste Menge reicht; nur sie kommt in den Kontext, und die Suche
     * endet sofort. Reicht es nicht, lädt die Live-Suche weitere Treffer ({@link RetrievalPreparation.Stage}) oder es
     * folgt eine genauere Nachsuche; ist der Aufwand erschöpft, antwortet der Turn mit der besten Evidenz und nennt die
     * Lücke. Die Grenzen kommen je Turn aus {@code evidence} (ohne Neustart), die Zahl der Nachsuchen von dort.
     * {@code followUps} prüft vorab den Wissenskontext früherer Nachrichten. Ist das Reranking abgeschaltet
     * ({@link EvidencePolicy#reranking()}), läuft der feste Ablauf mit Nachrecherche.
     *
     * @param evidence Grenzen je Turn; {@code null} = fester Ablauf wie im Konstruktor ohne diesen Parameter
     */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation, FollowUpResearch followUps,
                          Supplier<EvidencePolicy> evidence) {
        this(chat, retrieval, assembler, preparation, followUps, evidence, RetrievalTriage.off());
    }

    /**
     * Wie oben, mit Vorprüfung: vor der ersten Suche entscheidet {@code triage}, ob die Nachricht Wissen aus den
     * Quellen braucht und mit welcher Anfrage gesucht wird.
     *
     * @param triage {@code null} = {@link RetrievalTriage#off()}
     */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation, FollowUpResearch followUps,
                          Supplier<EvidencePolicy> evidence, RetrievalTriage triage) {
        this(chat, retrieval, assembler, preparation, followUps, evidence, triage, null, null);
    }

    /**
     * As above, with a linguistic analysis of every message: {@code analyzer} describes its form (source, sentence
     * type, word classes, diagnostics), and the description reaches triage and answer as a transient
     * {@code <client-turn-analysis>} block of this turn only. It decides nothing; the model decides.
     *
     * @param analyzer {@code null} = no analysis, only the origin note of a dictation
     * @param observer learns each block for a diagnostic log; {@code null} = none
     */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation, FollowUpResearch followUps,
                          Supplier<EvidencePolicy> evidence, RetrievalTriage triage, LinguisticAnalyzer analyzer,
                          TurnAnalysisObserver observer) {
        this(chat, retrieval, assembler, preparation, followUps, evidence, triage, analyzer, observer, null);
    }

    /**
     * As above, with the answer planning step: before the answer, an aside request asks the model what the user most
     * likely wanted to say and achieve ({@link ModelAnswerPlanningService}), from history, source, analysis and message.
     * The answering call receives original and interpretation side by side ({@code <client-answer-plan>}) and
     * chooses its action itself. With sources checked, the reading replaces the triage (one aside request, not two).
     * Nothing of it is stored.
     *
     * @param planning read per turn; {@code null} = off
     */
    public RagChatUseCase(ChatService chat, RetrieveKnowledgeUseCase retrieval, PromptContextAssembler assembler,
                          RetrievalPreparation preparation, FollowUpResearch followUps,
                          Supplier<EvidencePolicy> evidence, RetrievalTriage triage, LinguisticAnalyzer analyzer,
                          TurnAnalysisObserver observer, Supplier<AnswerPlanningPolicy> planning) {
        if (chat == null || retrieval == null) {
            throw new IllegalArgumentException("chat und retrieval sind Pflicht");
        }
        this.chat = chat;
        this.retrieval = retrieval;
        this.assembler = assembler == null ? new PromptContextAssembler(ContextSettings.defaults()) : assembler;
        this.preparation = preparation == null ? RetrievalPreparation.none() : preparation;
        this.followUps = followUps == null ? FollowUpResearch.off() : followUps;
        this.evidence = evidence;
        this.triage = triage == null ? RetrievalTriage.off() : triage;
        this.analyzer = analyzer;
        this.analysisObserver = observer == null ? TurnAnalysisObserver.none() : observer;
        this.planning = planning;
    }

    /**
     * Attach the cross-encoder that narrows each candidate round before the {@link EvidenceEvaluator} (adaptive mode
     * only). Without a selected RERANK model, or when it fails, the candidates pass unchanged.
     *
     * @param ranking {@code null} = {@link CrossEncoderRanking#off()}
     */
    public RagChatUseCase rankCandidatesWith(CrossEncoderRanking ranking) {
        this.crossEncoder = ranking == null ? CrossEncoderRanking.off() : ranking;
        return this;
    }

    /**
     * Resolve the chat project context for every new turn (#143): its instructions go into this turn's
     * system part, and knowledge context is only reused under the scope it was created in.
     *
     * @param resolver {@code null} = {@link ProjectContextResolver#none()}
     */
    public RagChatUseCase withProjectContext(ProjectContextResolver resolver) {
        this.projects = resolver == null ? ProjectContextResolver.none() : resolver;
        return this;
    }

    /** The project context of the conversation's next turn; fails closed if it cannot be resolved. */
    public EffectiveProjectContext projectContext(ChatConversationId id) {
        if (id == null) {
            return EffectiveProjectContext.unresolved();
        }
        try {
            EffectiveProjectContext context = projects.resolve(id);
            return context == null ? EffectiveProjectContext.unresolved() : context;
        } catch (RuntimeException failed) {
            return EffectiveProjectContext.unresolved();
        }
    }

    /**
     * Whether every turn runs the answer planning step, an extra model request before the answer. Such a turn must not
     * start on the UI thread.
     */
    public boolean plansAnswers() {
        return policy().isEnabled();
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
        return send(id, userText, rag, options, listener, extraContext, via, progress, MessageOrigin.TYPED);
    }

    /**
     * Wie {@link #send(ChatConversationId, String, RagOptions, ChatOptions, ChatTurnListener, String,
     * ChatCompletionPort, RetrievalProgress)}; {@code origin} sagt, wie die Nachricht entstand. Eine Abschrift der
     * Spracherkennung bekommt für diesen Turn einen Hinweis an das Modell (vor {@code extraContext}), und Vorprüfung
     * und Nachrecherche lesen sie als möglicherweise fehlerhaft erkannt.
     *
     * @param origin {@code null} = {@link MessageOrigin#TYPED}
     */
    public RagChatTurn send(ChatConversationId id, String userText, RagOptions rag, ChatOptions options,
                            ChatTurnListener listener, String extraContext, ChatCompletionPort via,
                            RetrievalProgress progress, MessageOrigin origin) {
        MessageOrigin from = origin == null ? MessageOrigin.TYPED : origin;
        if (userText == null || userText.trim().isEmpty()) {
            throw new IllegalArgumentException("user text must not be blank");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        // Resolved for every turn from the current assignment, never bound when the chat started (#143).
        EffectiveProjectContext project = projectContext(id);
        turnScopes.put(id, project.scope());
        extraContext = join(project.systemContext(), extraContext);
        AnswerPlanningPolicy policy = policy();
        if (rag == null || !rag.isEnabled()) {
            TurnReading reading = read(id, userText, from, options, policy, policy.isEnabled(), progress);
            extraContext = join(reading.hint(from), extraContext);
            if (reading.clarifies()) {
                return clarify(id, userText, options, listener, extraContext, reading);
            }
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
            TurnReading reading = read(id, userText, from, options, policy, true, steps);
            extraContext = join(reading.hint(from), extraContext);
            if (reading.clarifies()) {
                return clarify(id, userText, options, listener, extraContext, reading);
            }
            EvidencePolicy limits = evidence == null ? null : evidence.get();
            if (limits != null && limits.reranking()) {
                return sendAdaptive(id, userText, rag, options, listener, extraContext, via, steps, limits, from,
                        reading, policy);
            }
            return sendWithRetrieval(id, userText, rag, options, listener, extraContext, via, steps,
                    limits == null ? followUps : FollowUpResearch.upTo(limits.maxFollowUps()), from, reading,
                    policy);
        } finally {
            synchronized (retrieving) {
                retrieving.remove(id);
            }
        }
    }

    private RagChatTurn sendWithRetrieval(ChatConversationId id, String userText, RagOptions rag,
                                          ChatOptions options, ChatTurnListener listener, String extraContext,
                                          ChatCompletionPort via, RetrievalProgress progress,
                                          FollowUpResearch followUps, MessageOrigin origin,
                                          TurnReading reading, AnswerPlanningPolicy policy) {
        String analysis = reading.modelHint();
        List<String> queries = new ArrayList<String>();
        List<Integer> hitCounts = new ArrayList<Integer>();
        List<RetrievedChunk> hits = new ArrayList<RetrievedChunk>();
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        boolean failed = false;
        List<ChatMessage> history = reading.history != null ? reading.history
                : followUps.isEnabled() || triage.isEnabled()
                ? chat.conversation(id).messages() : Collections.<ChatMessage>emptyList();
        int maxSearches = 1 + followUps.maxFollowUps();
        String query = userText;
        // Erst der Wissenskontext früherer Nachrichten: beantwortet er die neue, wird nicht gesucht.
        ConversationKnowledgeContext prior = followUps.isEnabled() ? cachedContext(id) : null;
        List<String> priorQueries = Collections.emptyList();
        int reused = 0;
        // The plan is the only decision before the answer: without a usable plan the answer decides with its tools.
        if (reading.planRan() && !needsKnowledge(reading.answerPlan, policy)) {
            return withoutSearch(id, userText, options, listener, extraContext, via, progress);
        }
        boolean resolving = reading.planned() && reading.answerPlan.resolvesTerm();
        // A term to resolve is searched as said, not answered from what earlier messages found.
        if (prior != null && !prior.isEmpty() && !resolving) {
            FollowUpResearch.PriorVerdict verdict = followUps.checkPrior(chat, history, userText, origin,
                    analysis, prior.queries(), assembler.assemble(prior.hits()).text(), options);
            if (triage.isEnabled() && !verdict.isNeeded()) {
                return withoutSearch(id, userText, options, listener, extraContext, via, progress);
            }
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
        } else if (reading.planned()) {
            query = searchQuery(reading.answerPlan, userText);
        } else if (triage.isEnabled()) {
            progress.triageStarted();
            RetrievalTriage.Decision decision = triage.decide(chat, history, userText, origin, analysis, options);
            if (!decision.isSearch()) {
                return withoutSearch(id, userText, options, listener, extraContext, via, progress);
            }
            if (decision.query() != null) {
                query = decision.query();
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
            query = followUps.nextQuery(chat, history, userText, origin, all(priorQueries, queries),
                    assembler.assemble(hits).text(), options);
            if (resolving && query != null && !reading.answerPlan.keepsResolvedTerm(query)) {
                query = null; // another reading is a question to the user, never a search of its own
            }
        }
        PromptContext context = failed ? PromptContext.empty(0) : assembler.assemble(hits);
        progress.contextReady(context.sources().size());
        List<String> allQueries = all(priorQueries, queries);
        if (followUps.isEnabled() && !context.sources().isEmpty()) {
            // Vor dem Antwortturn gemerkt: scheitert die Antwort, bleibt der Kontext für die nächste Nachricht.
            cacheContext(id, new ConversationKnowledgeContext(allQueries, accepted(hits, context)));
        }
        if (resolving && !failed && context.sources().isEmpty() && !progress.isCancelled()) {
            return unresolved(id, userText, options, listener, extraContext, reading, context, warnings, queries,
                    hitCounts, reused);
        }
        String combined = join(join(context.text(), followUps.isEnabled()
                ? searchNote(allQueries, !context.sources().isEmpty()) : null), extraContext);
        ChatTurn turn = chat.sendWithContext(id, userText, combined, options, listener, via);
        return new RagChatTurn(turn, true, context, warnings, failed, queries, hitCounts, reused);
    }

    /**
     * Der zuletzt verwendete Wissenskontext der Konversation (leer, wenn keiner), etwa zum Speichern mit dem Chat.
     */
    public ConversationKnowledgeContext knowledgeContext(ChatConversationId id) {
        ConversationKnowledgeContext context = id == null ? null : cachedContext(id);
        return context == null ? ConversationKnowledgeContext.empty() : context;
    }

    /** The project scope the knowledge context of the conversation is valid under now (saved with the chat). */
    public String knowledgeScope(ChatConversationId id) {
        return projectContext(id).scope();
    }

    /**
     * Setzt den Wissenskontext einer (geöffneten) Konversation aus gespeicherten Chunk-IDs: die Auszüge kommen aus dem
     * Index, nicht aus der Chat-Datei. Fehlt dort alles, bleibt die Konversation ohne früheren Kontext.
     *
     * <p>Only if {@code savedScope} (the scope saved with the context) is still the conversation's current project
     * scope, checked before the index is read and again before the context is kept: a chat moved to another project
     * (also while the restore runs) gets no chunks of its earlier assignment back (#143).
     */
    public void restoreKnowledgeContext(ChatConversationId id, List<String> queries, List<KnowledgeChunkId> chunkIds,
                                        String savedScope) {
        if (id == null) {
            return;
        }
        if (!projectContext(id).sameScope(savedScope)) {
            contexts.remove(id);
            return;
        }
        List<RetrievedChunk> restored = retrieval.restore(chunkIds);
        EffectiveProjectContext now = projectContext(id);
        if (restored.isEmpty() || !now.sameScope(savedScope)) {
            contexts.remove(id);
        } else {
            contexts.put(id, new ScopedContext(now.scope(), new ConversationKnowledgeContext(queries, restored)));
        }
    }

    /** The cached knowledge context if it is still valid under the current project scope; drops it otherwise. */
    private ConversationKnowledgeContext cachedContext(ChatConversationId id) {
        ScopedContext cached = contexts.get(id);
        if (cached == null) {
            return null;
        }
        if (!projectContext(id).sameScope(cached.scope)) {
            contexts.remove(id);
            return null;
        }
        return cached.context;
    }

    /** Keeps the context under the scope of the running turn, unless the assignment changed meanwhile. */
    private void cacheContext(ChatConversationId id, ConversationKnowledgeContext context) {
        String scope = turnScopes.get(id);
        if (scope != null && projectContext(id).sameScope(scope)) {
            contexts.put(id, new ScopedContext(scope, context));
        } else {
            contexts.remove(id);
        }
    }

    /** A cached knowledge context and the project scope it was created under. */
    private static final class ScopedContext {

        final String scope;
        final ConversationKnowledgeContext context;

        ScopedContext(String scope, ConversationKnowledgeContext context) {
            this.scope = scope;
            this.context = context;
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

    /** Wie die adaptive Suche eines Turns endete. */
    private enum Outcome {
        /** Die Bewertung sagt „reicht“ (und die Mindestzahl ist erfüllt): sofort antworten. */
        SUFFICIENT,
        /** Kein lesbares Urteil: mit den Treffern in Suchreihenfolge antworten, wie ohne Bewertung. */
        NO_VERDICT,
        /** Aufwand erschöpft, ohne dass es reicht: mit der besten Evidenz antworten und die Lücke nennen. */
        INSUFFICIENT
    }

    /** Zustand der adaptiven Suche eines Turns: Kandidaten mit stabiler ID und das letzte Urteil. */
    private final class Evidence {

        /** Alle Kandidaten des Turns unter ihrer stabilen ID ({@code c1}, {@code c2}, …), in der Reihenfolge des Findens. */
        final Map<String, RetrievedChunk> known = new LinkedHashMap<String, RetrievedChunk>();
        final Map<String, String> idsByChunk = new HashMap<String, String>();
        final EvidencePolicy policy;
        List<String> relevant = Collections.emptyList();
        List<RetrievedChunk> selection = Collections.emptyList();
        String missing = "";
        String suggestion;
        List<String> priorQueries = Collections.emptyList();

        Evidence(EvidencePolicy policy) {
            this.policy = policy;
        }

        /** Vergibt IDs für noch unbekannte Treffer und liefert diese, in Suchreihenfolge. */
        List<String> admit(List<RetrievedChunk> hits) {
            List<String> fresh = new ArrayList<String>();
            for (RetrievedChunk hit : hits) {
                String chunkId = hit.chunk().id().value();
                if (!idsByChunk.containsKey(chunkId)) {
                    String id = "c" + (known.size() + 1);
                    idsByChunk.put(chunkId, id);
                    known.put(id, hit);
                    fresh.add(id);
                }
            }
            return fresh;
        }

        /**
         * Was die Bewertung sieht: die bisher relevanten Kandidaten (höchstens {@code retrieval.maxResults}, die
         * hilfreichsten) und die neuen (eine Suche liefert höchstens ebenso viele).
         */
        Map<String, RetrievedChunk> offer(List<String> fresh) {
            Map<String, RetrievedChunk> offered = new LinkedHashMap<String, RetrievedChunk>();
            for (String id : relevant.subList(0, Math.min(relevant.size(), retrieval.maxResults()))) {
                offered.put(id, known.get(id));
            }
            for (String id : fresh) {
                offered.put(id, known.get(id));
            }
            return offered;
        }

        int maxSources() {
            return assembler.settings().maxSources();
        }

        /** Die verlangte Mindestzahl, höchstens die Obergrenze (mehr ließe sich nie erfüllen). */
        int minSources() {
            return Math.min(policy.minSources(), maxSources());
        }

        /**
         * Die Abschnitte für den Kontext: bei „reicht“ die kleinste ausreichende Menge, sonst die relevanten; fehlen
         * unabhängige Seiten zur Mindestzahl, kommen relevante anderer Seiten dazu. Höchstens {@link #maxSources()}
         * und nur, was ins Token-Budget passt.
         */
        List<RetrievedChunk> choose(EvidenceEvaluator.Evaluation evaluation) {
            List<String> base = evaluation.isSufficient() && !evaluation.minimumEvidence().isEmpty()
                    ? evaluation.minimumEvidence() : evaluation.relevant();
            List<String> chosen = new ArrayList<String>(base);
            if (independent(chosen) < minSources()) {
                for (String id : evaluation.relevant()) {
                    if (!chosen.contains(id) && !resources(chosen).contains(resourceOf(id))) {
                        chosen.add(id);
                    }
                }
            }
            List<RetrievedChunk> hits = new ArrayList<RetrievedChunk>();
            for (String id : chosen) {
                hits.add(known.get(id));
            }
            return accepted(hits, assembler.assemble(hits));
        }

        int independent(List<String> ids) {
            return resources(ids).size();
        }

        int independentChunks(List<RetrievedChunk> hits) {
            Set<String> resources = new HashSet<String>();
            for (RetrievedChunk hit : hits) {
                resources.add(hit.resource().id().value());
            }
            return resources.size();
        }

        private Set<String> resources(List<String> ids) {
            Set<String> resources = new HashSet<String>();
            for (String id : ids) {
                resources.add(resourceOf(id));
            }
            return resources;
        }

        private String resourceOf(String id) {
            return known.get(id).resource().id().value();
        }
    }

    private RagChatTurn sendAdaptive(ChatConversationId id, String userText, RagOptions rag, ChatOptions options,
                                     ChatTurnListener listener, String extraContext, ChatCompletionPort via,
                                     RetrievalProgress progress, EvidencePolicy policy, MessageOrigin origin,
                                     TurnReading reading, AnswerPlanningPolicy planning) {
        String analysis = reading.modelHint();
        List<String> queries = new ArrayList<String>();
        List<Integer> hitCounts = new ArrayList<Integer>();
        List<RetrievalWarning> warnings = new ArrayList<RetrievalWarning>();
        boolean failed = false;
        List<ChatMessage> history = reading.history != null ? reading.history : chat.conversation(id).messages();
        Evidence evidence = new Evidence(policy);
        int maxSearches = 1 + policy.maxFollowUps();
        Outcome outcome = null;
        String query = userText;
        // Erst der Wissenskontext früherer Nachrichten: beantwortet er die neue, wird nicht gesucht. Passt er zum
        // Thema, wird er zusammen mit den neuen Treffern bewertet (hinter ihnen).
        List<RetrievedChunk> carried = Collections.emptyList();
        int reused = 0;
        ConversationKnowledgeContext prior = cachedContext(id);
        // The plan is the only decision before the answer: without a usable plan the answer decides with its tools.
        if (reading.planRan() && !needsKnowledge(reading.answerPlan, planning)) {
            return withoutSearch(id, userText, options, listener, extraContext, via, progress);
        }
        boolean resolving = reading.planned() && reading.answerPlan.resolvesTerm();
        // A term to resolve is searched as said, not answered from what earlier messages found.
        if (prior != null && !prior.isEmpty() && !resolving) {
            FollowUpResearch.PriorVerdict verdict = followUps.checkPrior(chat, history, userText, origin,
                    analysis, prior.queries(), assembler.assemble(prior.hits()).text(), options);
            if (triage.isEnabled() && !verdict.isNeeded()) {
                return withoutSearch(id, userText, options, listener, extraContext, via, progress);
            }
            if (verdict.isSufficient()) {
                evidence.selection = prior.hits();
                evidence.priorQueries = prior.queries();
                reused = assembler.assemble(prior.hits()).sources().size();
                query = null;
                outcome = Outcome.SUFFICIENT;
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
        } else if (reading.planned()) {
            query = searchQuery(reading.answerPlan, userText);
        } else if (triage.isEnabled()) {
            progress.triageStarted();
            RetrievalTriage.Decision decision = triage.decide(chat, history, userText, origin, analysis, options);
            if (!decision.isSearch()) {
                return withoutSearch(id, userText, options, listener, extraContext, via, progress);
            }
            if (decision.query() != null) {
                query = decision.query();
            }
        }
        while (query != null && outcome == null && !progress.isCancelled()) {
            queries.add(query);
            evidence.suggestion = null;
            progress.searchStarted(query, queries.size(), maxSearches);
            progress.liveSearchStarted(query);
            RetrievalPreparation.Stage stage = RetrievalPreparation.Stage.done();
            try {
                stage = preparation.begin(query, rag.sources(), progress);
            } catch (RuntimeException e) {
                // Die Live-Suche ist eine Ergänzung; ohne sie antwortet der Turn aus dem vorhandenen Index.
            }
            if (progress.isCancelled()) {
                queries.remove(queries.size() - 1);
                break;
            }
            for (int round = 0; outcome == null; round++) {
                RetrievalResult result;
                try {
                    result = retrieval.retrieve(query, rag.sources(), progress);
                } catch (KnowledgeRetrievalException e) {
                    if (round == 0) {
                        hitCounts.add(-1);
                        progress.searchFailed(query);
                        if (queries.size() == 1 && evidence.known.isEmpty() && carried.isEmpty()) {
                            warnings.addAll(e.warnings());
                            failed = true;
                        } else if (queries.size() == 1 && evidence.known.isEmpty()) {
                            evidence.admit(carried); // die Suche fiel aus, der frühere Kontext bleibt
                        }
                    }
                    outcome = Outcome.NO_VERDICT; // eine ausgefallene Suche lässt das Bisherige stehen
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
                List<String> fresh = evidence.admit(round == 0 && queries.size() == 1
                        ? newestFirst(result.hits(), carried) : result.hits());
                if (round == 0 || !fresh.isEmpty()) {
                    // Cross-encoder first (if a RERANK model is selected): only the best candidates reach the
                    // evaluator, which still decides relevance, sufficiency, minimum evidence and the next search.
                    Map<String, RetrievedChunk> offered = crossEncoder.rank(userText, evidence.offer(fresh))
                            .candidates();
                    progress.rankingStarted(offered.size());
                    EvidenceEvaluator.Evaluation evaluation = evaluator.evaluate(chat, history, userText,
                            all(evidence.priorQueries, queries), offered, evidence.minSources(),
                            evidence.maxSources(), options);
                    if (!evaluation.isKnown()) {
                        outcome = Outcome.NO_VERDICT;
                        break;
                    }
                    evidence.relevant = evaluation.relevant();
                    evidence.selection = evidence.choose(evaluation);
                    evidence.missing = evaluation.missing();
                    evidence.suggestion = evaluation.nextQuery();
                    progress.candidatesRanked(offered.size(), evaluation.relevant().size());
                    boolean enough = evaluation.isSufficient()
                            && evidence.independentChunks(evidence.selection) >= evidence.minSources();
                    progress.evidenceChecked(evidence.selection.size(),
                            evidence.independentChunks(evidence.selection), enough);
                    if (enough) {
                        outcome = Outcome.SUFFICIENT;
                        break;
                    }
                }
                if (progress.isCancelled()) {
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
            if (outcome != null || progress.isCancelled() || queries.size() >= maxSearches) {
                break;
            }
            query = evidence.suggestion;
            if (resolving && query != null && !reading.answerPlan.keepsResolvedTerm(query)) {
                query = null; // another reading is a question to the user, never a search of its own
            }
        }
        List<RetrievedChunk> chosen;
        String gap = null;
        if (outcome == Outcome.NO_VERDICT) {
            chosen = new ArrayList<RetrievedChunk>(evidence.known.values());
        } else if (outcome == Outcome.SUFFICIENT) {
            chosen = evidence.selection;
        } else {
            outcome = Outcome.INSUFFICIENT;
            chosen = evidence.selection;
            gap = gapNote(evidence.missing);
        }
        PromptContext context = failed ? PromptContext.empty(0) : assembler.assemble(chosen);
        progress.contextReady(context.sources().size());
        List<String> allQueries = all(evidence.priorQueries, queries);
        if (!context.sources().isEmpty()) {
            // Vor dem Antwortturn gemerkt: scheitert die Antwort, bleibt der Kontext für die nächste Nachricht.
            cacheContext(id, new ConversationKnowledgeContext(allQueries, accepted(chosen, context)));
        }
        if (resolving && !failed && context.sources().isEmpty() && !progress.isCancelled()) {
            return unresolved(id, userText, options, listener, extraContext, reading, context, warnings, queries,
                    hitCounts, reused);
        }
        String combined = join(join(join(context.text(), gap), searchNote(allQueries, !context.sources().isEmpty())),
                extraContext);
        ChatTurn turn = chat.sendWithContext(id, userText, combined, options, listener, via);
        return new RagChatTurn(turn, true, context, warnings, failed, queries, hitCounts, reused);
    }

    /**
     * Die Nachricht braucht kein Wissen aus den Quellen (Urteil der Vorprüfung): der Turn läuft ohne Suche und ohne
     * Kontextblock, der Wissenskontext früherer Nachrichten bleibt für spätere.
     */
    private RagChatTurn withoutSearch(ChatConversationId id, String userText, ChatOptions options,
                                      ChatTurnListener listener, String extraContext, ChatCompletionPort via,
                                      RetrievalProgress progress) {
        progress.searchSkipped();
        ChatTurn turn = chat.sendWithContext(id, userText, extraContext, options, listener, via);
        return new RagChatTurn(turn, false, PromptContext.empty(0), Collections.<RetrievalWarning>emptyList(),
                false);
    }

    /** Die Suche endete, ohne dass die Evidenz reicht: mit dem Vorhandenen antworten und die Lücke offen nennen. */
    static String gapNote(String missing) {
        StringBuilder note = new StringBuilder("Die gefundenen Abschnitte beantworten die Nachricht womöglich nicht "
                + "vollständig");
        if (missing != null && !missing.trim().isEmpty()) {
            note.append("; es fehlt: ").append(missing.replace('\r', ' ').replace('\n', ' ').trim());
        }
        return note.append(". Antworte mit dem Vorhandenen und nenne die Lücke offen.").toString();
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

    /**
     * Was gesucht wurde, wie es weitergeht, und dass eine Lücke nicht mit der Bitte um Dateien endet. Ohne Treffer
     * steigt die Unsicherheit, nicht die Zahl der Suchen: keine Varianten desselben Begriffs, sondern erst prüfen, ob
     * die Nachricht richtig verstanden ist, und eine öffentliche Bedeutung nur als mögliche Lesart nennen. Eine interne
     * Bedeutung im Kontext hat Vorrang.
     */
    static String searchNote(List<String> queries, boolean found) {
        StringBuilder note = new StringBuilder("In den angehakten Wissensquellen wurde gesucht mit:");
        for (String query : queries) {
            note.append("\n- ").append(query.replace('\r', ' ').replace('\n', ' ').trim());
        }
        if (found) {
            note.append("\nFindet sich im Kontext eine interne Bedeutung eines Begriffs, hat sie Vorrang vor einer "
                    + "öffentlichen Bedeutung, die du kennst.");
            note.append(" Beantwortet der Kontext die Nachricht nicht und braucht sie wirklich Wissen aus den "
                    + "Quellen, suche selbst mit knowledge_search und einer genaueren, anders formulierten Anfrage und "
                    + "lies Treffer mit knowledge_read, sofern dir diese Werkzeuge zur Verfügung stehen. Reicht es "
                    + "danach immer noch nicht, sag das und nenne die Suchanfragen.");
        } else {
            note.append("\nDiese Suche fand nichts. Suche nicht mit Varianten oder Teilen derselben Begriffe weiter. "
                    + "Prüfe stattdessen, ob du die Nachricht richtig verstanden hast (Tipp- oder Erkennungsfehler, "
                    + "Bezug auf den Verlauf). Betrifft sie die Bedienung dieses Programms, nutze die eingebaute "
                    + "Programmhilfe, sofern verfügbar. Ist das Anliegen unklar, frag kurz nach, statt weiter zu "
                    + "suchen. Fragt die Nachricht nach einer Abkürzung, Kennung oder einem Namen, sag ausdrücklich, dass "
                    + "die internen Quellen dazu nichts enthalten. Nennst du danach eine öffentliche Bedeutung, dann nur "
                    + "als mögliche Lesart mit dem Hinweis, dass im fachlichen Kontext etwas anderes gemeint sein kann; "
                    + "wähle sie nicht stillschweigend.");
        }
        note.append(" Bitte den Nutzer nicht, Dateien oder Unterlagen anzuhängen: die Wissensquellen sind angehakt.");
        return note.toString();
    }

    private AnswerPlanningPolicy policy() {
        AnswerPlanningPolicy current = planning == null ? null : planning.get();
        return current == null ? AnswerPlanningPolicy.off() : current;
    }

    /**
     * Whether the answer needs the organization's sources according to the plan: the planned next step is the internal
     * search and the planner is sure enough of its reading; below {@link AnswerPlanningPolicy#askBelow()} the answer
     * asks back instead of searching.
     */
    static boolean needsKnowledge(AnswerPlan plan, AnswerPlanningPolicy policy) {
        if (plan.resolvesTerm()) {
            return true;
        }
        // A literally kept term is searched first, however unsure the plan was about it.
        return plan.nextAction() == NextAction.SEARCH_INTERNAL_KNOWLEDGE
                && (plan.utteranceConfidence() >= policy.askBelow() || !plan.preservedTerms().isEmpty());
    }

    /** The term to resolve as said, else the planned query, else the interpreted message, else the message itself. */
    static String searchQuery(AnswerPlan plan, String userText) {
        if (plan.resolvesTerm()) {
            return plan.resolvedTerm();
        }
        if (!plan.knowledgeQuery().isEmpty()) {
            return plan.knowledgeQuery();
        }
        return plan.interpretedText().trim().isEmpty() ? userText : plan.interpretedText();
    }

    /**
     * Answer with the planned clarifying question instead of starting the main agent: no search, no answering call; the
     * question lands in the history like any answer.
     */
    private RagChatTurn clarify(ChatConversationId id, String userText, ChatOptions options,
                                ChatTurnListener listener, String extraContext, TurnReading reading) {
        ChatTurn turn = chat.sendWithContext(id, userText, extraContext, options, listener,
                new ClarificationPort(reading.answerPlan.clarificationQuestion()));
        return new RagChatTurn(turn, false, PromptContext.empty(0), Collections.<RetrievalWarning>emptyList(),
                false);
    }

    /**
     * The term to resolve was not found in the sources: ask back instead of answering from model knowledge, naming the
     * other readings of the plan as questions. The question lands in the history like any answer.
     */
    private RagChatTurn unresolved(ChatConversationId id, String userText, ChatOptions options,
                                   ChatTurnListener listener, String extraContext, TurnReading reading,
                                   PromptContext context, List<RetrievalWarning> warnings, List<String> queries,
                                   List<Integer> hitCounts, int reused) {
        ChatTurn turn = chat.sendWithContext(id, userText, extraContext, options, listener,
                new ClarificationPort(UnresolvedTermQuestion.of(reading.answerPlan)));
        return new RagChatTurn(turn, true, context, warnings, false, queries, hitCounts, reused);
    }

    /**
     * Analyse the message and, with the answer planning step on, read it. Without RAG and without the step the turn may
     * start on the UI thread, so then no model is loaded and no request is made ({@code mayBlock} false).
     */
    private TurnReading read(ChatConversationId id, String userText, MessageOrigin origin, ChatOptions options,
                             AnswerPlanningPolicy policy, boolean mayBlock, RetrievalProgress progress) {
        LinguisticFrame frame = null;
        String analysis = null;
        if (analyzer != null) {
            try {
                frame = mayBlock ? analyzer.analyze(userText, origin) : analyzer.analyzeWithoutModels(userText, origin);
            } catch (RuntimeException analysisFailed) {
                frame = LinguisticAnalyzer.none().analyze(userText, origin);
            }
            analysis = turnContext.create(frame);
            analysisObserver.analyzed(userText, origin, frame, analysis);
        }
        if (!policy.isEnabled() || !mayBlock) {
            return new TurnReading(analysis, null, null, null);
        }
        if (progress != null) {
            progress.planningStarted();
        }
        List<ChatMessage> history = chat.conversation(id).messages();
        ConversationContext conversation = ConversationContext.of(history);
        AnswerPlan turn = literalTerms.apply(new ModelAnswerPlanningService(chat, options).plan(
                conversation, new UserMessage(userText, origin), frame), frame, conversation);
        String block = turn.isKnown() ? planContext.create(turn, policy) : null;
        analysisObserver.planned(userText, turn, block);
        if (progress != null) {
            progress.planningFinished(turn.isKnown() ? turn.nextAction() : NextAction.UNKNOWN);
        }
        return new TurnReading(analysis, turn, block, history);
    }

    /** What the client learned about the current message before answering; lives for this turn only. */
    static final class TurnReading {

        /** The local analysis block, or {@code null}. */
        final String analysis;
        /** The reading of the answer planning step, or {@code null} when it did not run. */
        final AnswerPlan answerPlan;
        /** The block for the answering call, or {@code null} without a plan. */
        final String planBlock;
        /** The history the step saw, or {@code null}. */
        final List<ChatMessage> history;

        TurnReading(String analysis, AnswerPlan answerPlan, String planBlock, List<ChatMessage> history) {
            this.analysis = analysis;
            this.answerPlan = answerPlan;
            this.planBlock = planBlock;
            this.history = history;
        }

        boolean planned() {
            return answerPlan != null && answerPlan.isKnown();
        }

        /** Whether the planning step ran for this turn, whether or not it produced a plan. */
        boolean planRan() {
            return answerPlan != null;
        }

        /** Whether the plan asks the user back instead of answering. */
        boolean clarifies() {
            return planned() && answerPlan.asksClarification();
        }

        /** What the model reads about the message: the reading, else the analysis, else nothing. */
        String modelHint() {
            return planBlock != null ? planBlock : analysis;
        }

        /** As {@link #modelHint()}, with the origin note of a dictation when there is neither. */
        String hint(MessageOrigin origin) {
            String hint = modelHint();
            return hint != null ? hint : origin.modelNote();
        }
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
