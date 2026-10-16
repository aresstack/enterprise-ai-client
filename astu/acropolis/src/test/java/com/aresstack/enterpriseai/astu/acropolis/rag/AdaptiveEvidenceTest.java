package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.InMemoryRawKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawTextWindows;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagTestData.ScriptedEmbeddingPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Adaptiver Modus des {@link RagChatUseCase}: ein Evidenz-Bewerter je Kandidatenrunde, Ende, sobald die Evidenz reicht;
 * die Grenzen (Quellen, Mindestzahl, Nachrecherchen) gelten immer und ändern sich ohne Neustart.
 */
public class AdaptiveEvidenceTest {

    private static final String QUESTION = "Was ist das Quark-Verfahren?";
    private static final KnowledgeResource QUARK = resource("wiki:Quark", "wiki");
    private static final KnowledgeResource DETAIL = resource("wiki:QuarkAblauf", "wiki");
    private static final KnowledgeResource CHEESE = resource("wiki:Kaese", "wiki");
    private static final KnowledgeResource MILK = resource("wiki:Milch", "wiki");
    private static final KnowledgeResource HISTORY = resource("wiki:QuarkHistorie", "wiki");

    private RagTestData.ObservedIndex index;
    private ScriptedEmbeddingPort embeddings;
    private ScriptedModel model;
    private ChatService chat;

    @Before
    public void setUp() {
        index = new RagTestData.ObservedIndex(new InMemoryKnowledgeIndex());
        add(entry(QUARK, 0, "Das Quark-Verfahren prüft Lieferscheine automatisch.", vector(SPACE_3D, 1, 0, 0)));
        add(entry(DETAIL, 0, "Quark-Verfahren Ablauf: erfassen, prüfen, buchen.", vector(SPACE_3D, 1, 0.1f, 0)));
        add(entry(CHEESE, 0, "Käse wird im Keller gelagert.", vector(SPACE_3D, 1, 0.2f, 0)));
        add(entry(MILK, 0, "Milch kommt aus dem Stall.", vector(SPACE_3D, 1, 0.3f, 0)));
        embeddings = new ScriptedEmbeddingPort(SPACE_3D).on(QUESTION, 1f, 0f, 0f)
                .on("Quark Hintergrund", 0f, 1f, 0f).on("Quark weitere Quellen", 0f, 0f, 1f);
        for (int i = 1; i <= 9; i++) {
            embeddings.on("Quark Suche " + i, 0f, 1f, 1f);
        }
        model = new ScriptedModel();
        chat = new ChatService(model);
    }

    @Test
    public void factQuestionEndsWithOneEvaluationAndOneSource() throws Exception {
        model.relevant(QUARK, DETAIL).sufficientWith(QUARK);

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults(), RetrievalPreparation.none());

        assertEquals(1, turn.sources().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals("ein Bewerter-Aufruf, keine Prüfung je Quelle", 1, model.evaluations);
        assertEquals("nur der Bewerter, keine weitere Nebenanfrage", 1, model.requests);
        assertFalse(model.lastAnswerSystem.contains("Käse"));
        assertFalse(model.lastAnswerSystem.contains("Lücke"));
    }

    @Test
    public void turnFunnelCountsWhatRetrievalEvaluatorAndAnswerActuallyDid() throws Exception {
        model.relevant(QUARK, DETAIL).sufficientWith(QUARK);
        MemoryFunnelLog log = new MemoryFunnelLog();
        final SearchRunId run = log.run("wiki", 1, "wiki:Neu", "wiki:Alt");
        RetrievalPreparation live = new RetrievalPreparation() {
            @Override
            public void prepare(String query, Collection<SourceAddress> sources) {
            }

            @Override
            public Stage begin(String query, Collection<SourceAddress> sources, RetrievalProgress progress) {
                progress.liveSearchRecorded(run.value());
                return Stage.done();
            }
        };
        RagChatUseCase rag = adaptive(EvidencePolicy::defaults, new PromptContextAssembler(ContextSettings.defaults()),
                live).recordFunnelsIn(log);
        final List<KnowledgeFunnel> seen = new java.util.concurrent.CopyOnWriteArrayList<KnowledgeFunnel>();
        RecordingListener listener = new RecordingListener();

        RagChatTurn turn = rag.send(chat.openConversation("System"), QUESTION, RagOptions.enabled(), null, listener,
                null, null, new RetrievalProgress() {
                    @Override
                    public void funnelChanged(KnowledgeFunnel funnel) {
                        seen.add(funnel);
                    }
                });
        listener.await();

        KnowledgeFunnel funnel = turn.funnel().get();
        assertEquals("the run's candidates, never a provider total", 2, funnel.discovered());
        assertTrue("the reranked excerpt", funnel.examined().contains(new FunnelResource("wiki", "wiki:Neu")));
        assertFalse("not reranked, only discovered", funnel.examined().contains(new FunnelResource("wiki", "wiki:Alt")));
        assertTrue("indexed content scored by retrieval, no reload", funnel.examined().containsAll(
                Arrays.asList(key(QUARK), key(DETAIL), key(CHEESE))));
        assertEquals(new HashSet<FunnelResource>(Arrays.asList(key(QUARK), key(DETAIL))),
                new HashSet<FunnelResource>(funnel.relevant()));
        assertEquals("the provenance of the answer", Collections.singletonList(key(QUARK)), funnel.used());
        assertTrue(funnel.complete());
        assertEquals(FunnelPhase.DONE, funnel.phase());
        assertTrue("stored for later", log.funnel(funnel.id()).isPresent());
        List<FunnelPhase> phases = new ArrayList<FunnelPhase>();
        for (KnowledgeFunnel step : seen) {
            if (phases.isEmpty() || phases.get(phases.size() - 1) != step.phase()) {
                phases.add(step.phase());
            }
        }
        assertEquals(Arrays.asList(FunnelPhase.DISCOVERY, FunnelPhase.HARVESTING, FunnelPhase.RETRIEVAL,
                FunnelPhase.EVIDENCE, FunnelPhase.ANSWER, FunnelPhase.DONE), phases);
    }

    private static FunnelResource key(KnowledgeResource resource) {
        return FunnelRecorder.resource(resource.sourceId(), resource.id());
    }

    @Test
    public void complexQuestionTakesSeveralSourcesAndSearchesFurther() throws Exception {
        model.relevant(QUARK, DETAIL, HISTORY).sufficientWith(QUARK, DETAIL, HISTORY).nextQuery("Quark Hintergrund");
        RetrievalPreparation liveSearch = (query, sources) -> {
            if (query.equals("Quark Hintergrund")) {
                add(entry(HISTORY, 0, "Das Quark-Verfahren wurde 1999 gegründet.", vector(SPACE_3D, 0, 1, 0)));
            }
        };

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults(), liveSearch);

        assertEquals(Arrays.asList(QUESTION, "Quark Hintergrund"), turn.queries());
        assertEquals(3, turn.sources().size());
        assertEquals("je Kandidatenrunde ein Aufruf", 2, model.evaluations);
        assertTrue("die zweite Runde sieht die bisher relevanten mit", model.lastEvaluation.contains("wiki:QuarkAblauf"));
        assertTrue(model.lastAnswerSystem.contains("gegründet"));
        assertFalse("irrelevante Treffer fliegen raus", model.lastAnswerSystem.contains("Käse"));
    }

    @Test
    public void maxSourcesTwoMeansAtMostTwo() throws Exception {
        model.relevant(QUARK, DETAIL, CHEESE, MILK).sufficientWith(QUARK, DETAIL, CHEESE, MILK);

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults().withMaxSources(2),
                RetrievalPreparation.none());

        assertEquals(2, turn.sources().size());
        assertTrue(model.lastEvaluation, model.lastEvaluation.contains("höchstens 2 Abschnitte"));
    }

    @Test
    public void maxSourcesTenAllowsUpToTen() throws Exception {
        List<KnowledgeResource> pages = new ArrayList<KnowledgeResource>();
        for (int i = 0; i < 10; i++) {
            KnowledgeResource page = resource("wiki:Quark" + i, "wiki");
            pages.add(page);
            add(entry(page, 0, "Quark-Verfahren Teil " + i, vector(SPACE_3D, 1, 0.05f * i, 0)));
        }
        List<KnowledgeResource> all = new ArrayList<KnowledgeResource>(pages);
        all.addAll(Arrays.asList(QUARK, DETAIL, CHEESE, MILK));
        model.relevant(all.toArray(new KnowledgeResource[0])).sufficientWith(HISTORY);

        RagChatTurn turn = send(EvidencePolicy.defaults().withMaxFollowUps(0), ContextSettings.defaults().withMaxSources(10)
                .withMaxContextTokens(100000), RetrievalPreparation.none());

        assertEquals("mehr relevante Treffer als erlaubt: gedeckelt", 10, turn.sources().size());
    }

    @Test
    public void minimumZeroLeavesTheDecisionToTheEvaluation() throws Exception {
        model.sufficientWith();

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(0), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertTrue("nichts relevant, aber ausreichend: kein Kontext", turn.sources().isEmpty());
        assertEquals(1, model.evaluations);
        assertFalse(model.lastEvaluation.contains("verschiedene Seiten"));
        assertEquals(1, turn.queries().size());
    }

    @Test
    public void minimumTwoNeedsTwoDifferentResources() throws Exception {
        add(entry(QUARK, 1, "Quark-Verfahren gilt für alle Lieferscheine.", vector(SPACE_3D, 1, 0, 0)));
        model.relevant(QUARK, DETAIL).sufficientWith(QUARK);

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(2), ContextSettings.defaults(),
                RetrievalPreparation.none());

        Set<String> resources = new HashSet<String>();
        for (RagSource source : turn.sources()) {
            resources.add(source.resource().id().value());
        }
        assertEquals("zwei Abschnitte derselben Seite zählen als eine", 2, resources.size());
        assertTrue(resources.contains(DETAIL.id().value()));
        assertTrue(model.lastEvaluation.contains("mindestens 2 verschiedene Seiten"));
    }

    @Test
    public void unmetMinimumEndsWithTheBestEvidenceAndNamesTheGap() throws Exception {
        model.relevant(QUARK).sufficientWith(QUARK).nextQuery("Quark weitere Quellen").missing("zweite Quelle");

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(2), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals(1, turn.sources().size());
        assertEquals("die wiederholte Anfrage endet die Suche", Arrays.asList(QUESTION, "Quark weitere Quellen"),
                turn.queries());
        assertTrue(model.lastAnswerSystem, model.lastAnswerSystem.contains("es fehlt: zweite Quelle"));
        assertTrue(model.lastAnswerSystem.contains("nenne die Lücke offen"));
    }

    @Test
    public void maxFollowUpsZeroMeansNoFollowUpSearch() throws Exception {
        model.relevant(QUARK).nextQuery("Quark Hintergrund");

        RagChatTurn turn = send(EvidencePolicy.defaults().withMaxFollowUps(0), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals(1, turn.sources().size());
        assertTrue(model.lastAnswerSystem.contains("nenne die Lücke offen"));
    }

    @Test
    public void maxFollowUpsFiveMeansAtMostFive() throws Exception {
        model.relevant(QUARK).numberedQueries();

        RagChatTurn turn = send(EvidencePolicy.defaults().withMaxFollowUps(5), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals("die Nachricht und fünf Nachrecherchen", 6, turn.queries().size());
    }

    @Test
    public void unreadableEvaluationKeepsTheCandidatesInSearchOrder() throws Exception {
        model.evaluationAnswer = "kaputt";

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults(), RetrievalPreparation.none());

        assertEquals(4, turn.sources().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
        assertEquals(1, model.evaluations);
    }

    @Test
    public void withoutRerankingTheFixedFlowRuns() throws Exception {
        RagChatTurn turn = send(EvidencePolicy.defaults().withReranking(false), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals(0, model.evaluations);
        assertEquals("Nachrecherche-Prüfung", 1, model.checks);
        assertEquals(4, turn.sources().size());
    }

    @Test
    public void stagedLiveSearchWaitsForMorePagesOnlyWhenTheEvidenceIsNotEnough() throws Exception {
        model.relevant(QUARK, DETAIL, HISTORY).sufficientWith(QUARK, HISTORY);
        StagedSearch staged = new StagedSearch();

        RagChatTurn turn = send(EvidencePolicy.defaults().withMaxFollowUps(0), ContextSettings.defaults(), staged);

        assertEquals(1, staged.awaited);
        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals(2, turn.sources().size());
        assertEquals(2, model.evaluations);

        setUp();
        model.relevant(QUARK).sufficientWith(QUARK);
        StagedSearch fast = new StagedSearch();
        send(EvidencePolicy.defaults(), ContextSettings.defaults(), fast);
        assertEquals("Faktfrage wartet auf keine weitere Seite", 0, fast.awaited);
    }

    @Test
    public void followUpAnsweredByEarlierEvidenceNeedsNoSearch() throws Exception {
        model.relevant(QUARK).sufficientWith(QUARK);
        RagChatUseCase rag = adaptive(() -> EvidencePolicy.defaults(), PromptContextAssembler.live(
                ContextSettings::defaults), RetrievalPreparation.none());
        ChatConversationId id = chat.openConversation("System");
        send(rag, id, QUESTION);
        int searches = index.semanticSearches;
        model.priorAnswer = "{\"sufficient\": true, \"related\": true, \"query\": \"\"}";

        RagChatTurn turn = send(rag, id, "Und wofür genau?");

        assertEquals("nicht neu gesucht", searches, index.semanticSearches);
        assertEquals(1, turn.sources().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
        assertEquals("nur die erste Nachricht wurde bewertet", 1, model.evaluations);
    }

    @Test
    public void changedLimitsApplyToTheNextTurnWithoutRestart() throws Exception {
        model.relevant(QUARK, DETAIL, CHEESE, MILK).sufficientWith(QUARK, DETAIL, CHEESE, MILK);
        AtomicReference<EvidencePolicy> policy = new AtomicReference<EvidencePolicy>(EvidencePolicy.defaults());
        AtomicReference<ContextSettings> context = new AtomicReference<ContextSettings>(
                ContextSettings.defaults().withMaxSources(3));
        RagChatUseCase rag = adaptive(policy::get, PromptContextAssembler.live(context::get),
                RetrievalPreparation.none());

        assertEquals(3, send(rag, chat.openConversation("System"), QUESTION).sources().size());

        context.set(ContextSettings.defaults().withMaxSources(2));
        assertEquals(2, send(rag, chat.openConversation("System"), QUESTION).sources().size());

        model.relevant(QUARK).sufficientWith(HISTORY).numberedQueries();
        policy.set(EvidencePolicy.defaults().withMaxFollowUps(1));
        assertEquals(2, send(rag, chat.openConversation("System"), QUESTION).queries().size());
        policy.set(EvidencePolicy.defaults().withMaxFollowUps(0));
        assertEquals(1, send(rag, chat.openConversation("System"), QUESTION).queries().size());
    }

    @Test
    public void parserKeepsOnlyOfferedIds() {
        EvidenceEvaluator.Evaluation evaluation = EvidenceEvaluator.parse("{\"ranked\": [{\"id\": \"c2\", "
                + "\"relevant\": true}, {\"id\": \"c9\", \"relevant\": true}, {\"id\": \"c1\", \"relevant\": false}], "
                + "\"minimumEvidence\": [\"c2\", \"c7\"], \"sufficient\": true, \"missing\": \"\", \"nextQuery\": \"\"}",
                new HashSet<String>(Arrays.asList("c1", "c2", "c3")), Collections.<String>emptyList());
        assertTrue(evaluation.isSufficient());
        assertEquals(Collections.singletonList("c2"), evaluation.relevant());
        assertEquals(Collections.singletonList("c2"), evaluation.minimumEvidence());
        assertNull(evaluation.nextQuery());
        assertFalse(EvidenceEvaluator.parse("keine Ahnung", Collections.<String>emptySet(),
                Collections.<String>emptyList()).isKnown());
    }

    /**
     * Abnahme der zweistufigen Pipeline: bei leerem semantischen Index beantwortet der Chat „Was ist IAR?“ aus der
     * gerade geladenen Seite (Rohindex, Textfenster), während Passagen und Embeddings noch hängen; die zweite
     * gleiche Frage nutzt danach den semantischen Index.
     */
    @Test
    public void simpleQuestionIsAnsweredBeforeTheSemanticPreparationAndTheNextTurnUsesIt() throws Exception {
        String question = "Was ist IAR?";
        DeterministicEmbeddingPort vectors = DeterministicEmbeddingPort.withDimension(16);
        InMemoryKnowledgeIndex semantic = new InMemoryKnowledgeIndex();
        InMemoryRawKnowledgeIndex raw = new InMemoryRawKnowledgeIndex();
        final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
                .add("IAR", "IAR", "IAR ist ein Beispielsystem für Testzwecke. Es prüft Belege.")
                .add("Kaffee", "Kaffee", "Kaffee gibt es im zweiten Stock.");
        final CountDownLatch release = new CountDownLatch(1);
        EmbeddingPort blocked = new EmbeddingPort() {
            @Override
            public EmbeddingModelIdentity modelIdentity() {
                return vectors.modelIdentity();
            }

            @Override
            public EmbeddingBatch embed(List<String> texts) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return vectors.embed(texts);
            }
        };
        IndexKnowledgeUseCase enrichment = new IndexKnowledgeUseCase(semantic, blocked, vectors.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        final KnowledgePrefetchService prefetch = new KnowledgePrefetchService(enrichment, raw,
                (owner, source, id) -> source.load(id), () -> KnowledgePrefetchService.Settings.of(5, 2, 10));
        try {
            KnowledgeResourceId iar = wiki.idOf("IAR");
            final List<KnowledgePrefetchService.Candidate> found = Collections.singletonList(
                    new KnowledgePrefetchService.Candidate(wiki, iar, wiki.load(iar).resource().revision()));
            RetrievalPreparation live = (query, sources) ->
                    prefetch.prefetch("chat", found, 0, true).awaitAvailable(1, 10_000L, count -> { });
            KnowledgeResource page = wiki.load(iar).resource();
            model.relevant(page).sufficientWith(page);
            RagChatUseCase rag = new RagChatUseCase(chat,
                    new RetrieveKnowledgeUseCase(semantic, vectors, vectors.modelIdentity(), null).withRawDocuments(raw),
                    new PromptContextAssembler(ContextSettings.defaults()), live,
                    FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS), EvidencePolicy::defaults);

            RagChatTurn first = send(rag, chat.openConversation("System"), question);

            assertEquals(1, first.sources().size());
            assertEquals(iar, first.sources().get(0).resource().id());
            assertTrue("Antwort aus dem Textfenster", model.lastAnswerSystem.contains("Beispielsystem"));
            assertTrue(RawTextWindows.isRawWindow(first.sources().get(0).chunk().id()));
            assertTrue("Embeddings noch nicht fertig",
                    semantic.resourceIds(vectors.modelIdentity(), wiki.sourceId()).isEmpty());

            release.countDown();
            long deadline = System.currentTimeMillis() + 10_000L;
            while (!semantic.resourceIds(vectors.modelIdentity(), wiki.sourceId()).contains(iar)) {
                assertTrue("Aufbereitung fertig", System.currentTimeMillis() < deadline);
                Thread.sleep(20);
            }
            RagChatTurn second = send(rag, chat.openConversation("System"), question);

            assertEquals(iar, second.sources().get(0).resource().id());
            assertFalse("zweiter Turn aus dem semantischen Index",
                    RawTextWindows.isRawWindow(second.sources().get(0).chunk().id()));
        } finally {
            release.countDown();
            prefetch.shutdown();
        }
    }

    @Test
    public void crossEncoderNarrowsTheCandidatesBeforeTheEvaluator() throws Exception {
        model.relevant(QUARK, DETAIL).sufficientWith(QUARK);
        final List<String> rankedQueries = new ArrayList<String>();
        CrossEncoderRanking.Scorer scores = (modelId, query, documents) -> {
            rankedQueries.add(modelId + "|" + query);
            double[] result = new double[documents.size()];
            for (int i = 0; i < documents.size(); i++) {
                String text = documents.get(i);
                result[i] = text.contains("Ablauf") ? 5 : text.contains("Quark") ? 4 : text.contains("Käse") ? -3 : -5;
            }
            return result;
        };
        RagChatUseCase rag = adaptive(() -> EvidencePolicy.defaults().withRerankTopK(2),
                new PromptContextAssembler(ContextSettings.defaults()), RetrievalPreparation.none())
                .rankCandidatesWith(new CrossEncoderRanking(scores, () -> "bge-reranker",
                        () -> EvidencePolicy.defaults().withRerankTopK(2).rerankTopK()));

        RagChatTurn turn = send(rag, chat.openConversation("System"), QUESTION);

        assertEquals("the selected model ranks against the user's question",
                Collections.singletonList("bge-reranker|" + QUESTION), rankedQueries);
        assertTrue(model.lastEvaluation, model.lastEvaluation.contains("wiki:QuarkAblauf"));
        assertTrue(model.lastEvaluation, model.lastEvaluation.contains("wiki:Quark "));
        assertFalse("below top-K never reaches the evaluator", model.lastEvaluation.contains("wiki:Kaese"));
        assertFalse(model.lastEvaluation.contains("wiki:Milch"));
        assertTrue("best first", model.lastEvaluation.indexOf("wiki:QuarkAblauf")
                < model.lastEvaluation.indexOf("wiki:Quark "));
        assertEquals("the evaluator still decides the evidence", 1, model.evaluations);
        assertEquals(1, turn.sources().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
    }

    @Test
    public void failingOrMissingCrossEncoderKeepsTheFormerPath() throws Exception {
        model.relevant(QUARK, DETAIL).sufficientWith(QUARK);
        CrossEncoderRanking.Scorer failing = (modelId, query, documents) -> {
            throw new java.io.IOException("not reachable");
        };
        RagChatUseCase rag = adaptive(EvidencePolicy::defaults, new PromptContextAssembler(ContextSettings.defaults()),
                RetrievalPreparation.none()).rankCandidatesWith(new CrossEncoderRanking(failing, () -> "bge-reranker",
                () -> 1));

        RagChatTurn turn = send(rag, chat.openConversation("System"), QUESTION);

        assertTrue("all candidates as before: " + model.lastEvaluation, model.lastEvaluation.contains("wiki:Milch"));
        assertEquals(QUARK, turn.sources().get(0).resource());

        final int[] calls = {0};
        CrossEncoderRanking.Scorer counting = (modelId, query, documents) -> {
            calls[0]++;
            return new double[documents.size()];
        };
        RagChatUseCase withoutModel = adaptive(EvidencePolicy::defaults,
                new PromptContextAssembler(ContextSettings.defaults()), RetrievalPreparation.none())
                .rankCandidatesWith(new CrossEncoderRanking(counting, () -> null, () -> 1));
        send(withoutModel, chat.openConversation("System"), QUESTION);
        assertEquals("no RERANK model selected: the reranker is never asked", 0, calls[0]);
        assertTrue(model.lastEvaluation.contains("wiki:Milch"));
    }

    private RagChatTurn send(EvidencePolicy policy, ContextSettings context, RetrievalPreparation preparation)
            throws Exception {
        return send(adaptive(() -> policy, new PromptContextAssembler(context), preparation),
                chat.openConversation("System"), QUESTION);
    }

    private RagChatUseCase adaptive(java.util.function.Supplier<EvidencePolicy> policy, PromptContextAssembler assembler,
                                    RetrievalPreparation preparation) {
        return new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null), assembler,
                preparation, FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS), policy);
    }

    private RagChatTurn send(RagChatUseCase rag, ChatConversationId id, String text) throws Exception {
        RecordingListener listener = new RecordingListener();
        RagChatTurn turn = rag.send(id, text, RagOptions.enabled(), null, listener, null, null,
                RetrievalProgress.none());
        listener.await();
        return turn;
    }

    /** Legt einen Eintrag in den Index, auch während der Suche (wie der Vorlader). */
    private void add(KnowledgeIndexEntry entry) {
        index.index(Collections.singletonList(entry));
    }

    /** Legt beim ersten Nachladen die Historienseite in den Index, wie der Vorlader im Hintergrund. */
    private final class StagedSearch implements RetrievalPreparation {

        int awaited;

        @Override
        public void prepare(String query, Collection<SourceAddress> sources) {
        }

        @Override
        public Stage begin(String query, Collection<SourceAddress> sources) {
            return () -> {
                if (awaited > 0) {
                    return false;
                }
                awaited++;
                add(entry(HISTORY, 0, "Das Quark-Verfahren wurde 1999 gegründet.", vector(SPACE_3D, 1, 0, 0)));
                return true;
            };
        }
    }

    /**
     * Das Modell: Nebenanfragen nach ihrer Anweisung beantwortet (Evidenz-Bewertung, Nachrecherche-Prüfung, früherer
     * Kontext), die Antwort gestreamt.
     */
    private static final class ScriptedModel implements ChatCompletionPort {

        private static final Pattern CANDIDATE = Pattern.compile("^\\[(c\\d+)] resource: (\\S+) ", Pattern.MULTILINE);

        private final FakeChatCompletionPort answers = new FakeChatCompletionPort();
        private final Set<String> relevant = new HashSet<String>();
        private List<String> sufficientWith;
        private String nextQuery = "";
        private String missing = "";
        private boolean numbered;
        volatile int requests;
        volatile int evaluations;
        volatile int checks;
        volatile String lastEvaluation = "";
        volatile String lastAnswerSystem = "";
        volatile String evaluationAnswer;
        volatile String priorAnswer = "kein Urteil";

        ScriptedModel relevant(KnowledgeResource... resources) {
            relevant.clear();
            for (KnowledgeResource resource : resources) {
                relevant.add(resource.id().value());
            }
            return this;
        }

        /** Reicht, sobald alle genannten Seiten angeboten sind; die kleinste Menge sind genau sie. */
        ScriptedModel sufficientWith(KnowledgeResource... resources) {
            sufficientWith = new ArrayList<String>();
            for (KnowledgeResource resource : resources) {
                sufficientWith.add(resource.id().value());
            }
            return this;
        }

        ScriptedModel nextQuery(String query) {
            nextQuery = query;
            return this;
        }

        ScriptedModel missing(String text) {
            missing = text;
            return this;
        }

        /** Jede Bewertung schlägt eine neue Anfrage vor („Quark Suche n“). */
        ScriptedModel numberedQueries() {
            numbered = true;
            return this;
        }

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            requests++;
            String instruction = request.messages().get(0).content();
            String user = request.messages().get(1).content();
            String answer;
            if (instruction.equals(FollowUpResearch.PRIOR_INSTRUCTION)) {
                answer = priorAnswer;
            } else if (instruction.equals(EvidenceEvaluator.INSTRUCTION)) {
                evaluations++;
                lastEvaluation = user;
                answer = evaluationAnswer != null ? evaluationAnswer : evaluation(user);
            } else {
                checks++;
                answer = "{\"sufficient\": true, \"query\": \"\"}";
            }
            return new ChatResponse(ChatMessage.assistant(answer), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "fake");
        }

        private String evaluation(String user) {
            Map<String, String> offered = new LinkedHashMap<String, String>();
            Matcher candidate = CANDIDATE.matcher(user);
            while (candidate.find()) {
                offered.put(candidate.group(1), candidate.group(2));
            }
            StringBuilder ranked = new StringBuilder();
            Set<String> offeredRelevant = new HashSet<String>();
            for (Map.Entry<String, String> entry : offered.entrySet()) {
                boolean isRelevant = relevant.contains(entry.getValue());
                if (isRelevant) {
                    offeredRelevant.add(entry.getValue());
                }
                ranked.append(ranked.length() == 0 ? "" : ", ").append("{\"id\": \"").append(entry.getKey())
                        .append("\", \"relevant\": ").append(isRelevant).append('}');
            }
            boolean sufficient = sufficientWith != null && offeredRelevant.containsAll(sufficientWith);
            StringBuilder minimum = new StringBuilder();
            if (sufficient) {
                Set<String> taken = new HashSet<String>();
                for (Map.Entry<String, String> entry : offered.entrySet()) {
                    if (sufficientWith.contains(entry.getValue()) && taken.add(entry.getValue())) {
                        minimum.append(minimum.length() == 0 ? "" : ", ").append('"').append(entry.getKey())
                                .append('"');
                    }
                }
            }
            String query = numbered ? "Quark Suche " + evaluations : nextQuery;
            return "{\"ranked\": [" + ranked + "], \"minimumEvidence\": [" + minimum + "], \"sufficient\": "
                    + sufficient + ", \"missing\": \"" + missing + "\", \"nextQuery\": \""
                    + query + "\"}";
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            lastAnswerSystem = request.messages().get(0).content();
            answers.enqueueAnswer("Antwort [1].");
            return answers.stream(request, listener);
        }
    }
}
