package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagTestData.ScriptedEmbeddingPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Adaptiver Modus des {@link RagChatUseCase}: Reranking gegen die Frage, Evidenz Quelle für Quelle, Ende, sobald sie
 * reicht; die Obergrenze gilt immer, die Mindestzahl unabhängiger Quellen 0, 1 und n.
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
    private RecordingListener listener;

    @Before
    public void setUp() {
        index = new RagTestData.ObservedIndex(new InMemoryKnowledgeIndex());
        add(entry(QUARK, 0, "Das Quark-Verfahren prüft Lieferscheine automatisch.", vector(SPACE_3D, 1, 0, 0)));
        add(entry(DETAIL, 0, "Quark-Verfahren Ablauf: erfassen, prüfen, buchen.", vector(SPACE_3D, 1, 0.1f, 0)));
        add(entry(CHEESE, 0, "Käse wird im Keller gelagert.", vector(SPACE_3D, 1, 0.2f, 0)));
        add(entry(MILK, 0, "Milch kommt aus dem Stall.", vector(SPACE_3D, 1, 0.3f, 0)));
        embeddings = new ScriptedEmbeddingPort(SPACE_3D).on(QUESTION, 1f, 0f, 0f)
                .on("Quark Hintergrund", 0f, 1f, 0f).on("Quark weitere Quellen", 0f, 0f, 1f);
        model = new ScriptedModel();
        chat = new ChatService(model);
    }

    @Test
    public void factQuestionEndsAfterOneSufficientSource() throws Exception {
        model.score(QUARK, 3).score(DETAIL, 2);
        model.sufficientWhen(context -> context.contains("Lieferscheine"));

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults(), RetrievalPreparation.none());

        assertEquals(1, turn.sources().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals("eine Bewertung", 1, model.rankings);
        assertEquals("eine Prüfung", 1, model.checks);
        assertFalse(model.lastAnswerSystem.contains("Käse"));
    }

    @Test
    public void complexQuestionTakesMoreSourcesAndSearchesFurther() throws Exception {
        model.score(QUARK, 3).score(DETAIL, 2).score(HISTORY, 2);
        model.sufficientWhen(context -> context.contains("gegründet"));
        model.nextQuery("Quark Hintergrund");
        RetrievalPreparation liveSearch = (query, sources) -> {
            if (query.equals("Quark Hintergrund")) {
                add(entry(HISTORY, 0, "Das Quark-Verfahren wurde 1999 gegründet.", vector(SPACE_3D, 0, 1, 0)));
            }
        };

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults(), liveSearch);

        assertEquals(java.util.Arrays.asList(QUESTION, "Quark Hintergrund"), turn.queries());
        assertEquals(3, turn.sources().size());
        assertEquals("neue Quelle hinten angehängt", HISTORY, turn.sources().get(2).resource());
        assertEquals("nach jeder Quelle geprüft", 3, model.checks);
        assertEquals("je Suche eine Bewertung der neuen Treffer", 2, model.rankings);
        assertTrue(model.lastAnswerSystem.contains("gegründet"));
        assertFalse("irrelevante Treffer fliegen raus", model.lastAnswerSystem.contains("Käse"));
    }

    @Test
    public void maxSourcesIsNeverExceeded() throws Exception {
        model.score(QUARK, 3).score(DETAIL, 2).score(CHEESE, 2).score(MILK, 2);
        model.sufficientWhen(context -> false);
        model.nextQuery("Quark Hintergrund");
        final int[] full = {-1};
        RetrievalProgress progress = new RetrievalProgress() {
            @Override
            public void evidenceFull(int sources) {
                full[0] = sources;
            }
        };

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults().withMaxSources(2),
                RetrievalPreparation.none(), progress);

        assertEquals(2, turn.sources().size());
        assertEquals(2, full[0]);
        assertEquals("Obergrenze erreicht: keine Nachsuche", 1, turn.queries().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
    }

    @Test
    public void minimumOfOneChecksAfterTheFirstSource() throws Exception {
        add(entry(QUARK, 1, "Quark-Verfahren gilt für alle Lieferscheine.", vector(SPACE_3D, 1, 0, 0)));
        model.score(QUARK, 3).score(DETAIL, 2);
        model.sufficientWhen(context -> true);

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(1), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals(1, turn.sources().size());
        assertEquals(1, model.checks);
    }

    @Test
    public void minimumOfTwoNeedsTwoIndependentPagesBeforeTheFirstCheck() throws Exception {
        add(entry(QUARK, 1, "Quark-Verfahren gilt für alle Lieferscheine.", vector(SPACE_3D, 1, 0, 0)));
        model.score(QUARK, 3).score(DETAIL, 2);
        model.sufficientWhen(context -> true);

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(2), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals("zwei Abschnitte derselben Seite zählen als eine Quelle", 3, turn.sources().size());
        assertEquals(DETAIL, turn.sources().get(2).resource());
        assertEquals("erst mit der zweiten Seite geprüft", 1, model.checks);
    }

    @Test
    public void unmetMinimumAsksForASearchForFurtherSources() throws Exception {
        model.score(QUARK, 3);
        model.sufficientWhen(context -> true);
        model.nextQuery("Quark weitere Quellen");

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(2), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals(1, turn.sources().size());
        assertTrue(model.lastCheck, model.lastCheck.contains("Verlangt sind Belege aus 1 weiteren"));
    }

    @Test
    public void minimumOfZeroLeavesTheDecisionToTheCheck() throws Exception {
        model.sufficientWhen(context -> true);

        RagChatTurn turn = send(EvidencePolicy.defaults().withMinSources(0), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertTrue("nichts relevant: kein Kontext", turn.sources().isEmpty());
        assertEquals(1, model.checks);
        assertFalse(model.lastCheck.contains("Verlangt sind Belege"));
        assertEquals(1, turn.queries().size());
    }

    @Test
    public void unreadableRankingKeepsAllCandidatesInSearchOrder() throws Exception {
        model.rankingAnswer = "kaputt";
        model.sufficientWhen(context -> true);

        RagChatTurn turn = send(EvidencePolicy.defaults(), ContextSettings.defaults(), RetrievalPreparation.none());

        assertEquals(1, turn.sources().size());
        assertEquals(1, model.rankings);
    }

    @Test
    public void withoutRerankingNoRankingRequestIsMade() throws Exception {
        model.sufficientWhen(context -> true);

        RagChatTurn turn = send(EvidencePolicy.defaults().withReranking(false), ContextSettings.defaults(),
                RetrievalPreparation.none());

        assertEquals(0, model.rankings);
        assertEquals(1, turn.sources().size());
    }

    @Test
    public void stagedLiveSearchWaitsForMorePagesOnlyWhenTheEvidenceIsNotEnough() throws Exception {
        model.score(QUARK, 3).score(DETAIL, 2).score(HISTORY, 3);
        model.sufficientWhen(context -> context.contains("gegründet"));
        StagedSearch staged = new StagedSearch();

        RagChatTurn turn = send(EvidencePolicy.defaults().withMaxFollowUps(0), ContextSettings.defaults(), staged);

        assertEquals(1, staged.awaited);
        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals(3, turn.sources().size());
        assertEquals("die nachgeladene Seite steht als beste Note vorn im neuen Bündel", HISTORY,
                turn.sources().get(2).resource());

        setUp();
        model.score(QUARK, 3);
        model.sufficientWhen(context -> context.contains("Lieferscheine"));
        StagedSearch fast = new StagedSearch();
        send(EvidencePolicy.defaults(), ContextSettings.defaults(), fast);
        assertEquals("Faktfrage wartet auf keine weitere Seite", 0, fast.awaited);
    }

    @Test
    public void followUpAnsweredByEarlierEvidenceNeedsNoSearch() throws Exception {
        model.score(QUARK, 3);
        model.sufficientWhen(context -> context.contains("Lieferscheine"));
        RagChatUseCase rag = adaptive(EvidencePolicy.defaults(), ContextSettings.defaults(),
                RetrievalPreparation.none());
        ChatConversationId id = chat.openConversation("System");
        send(rag, id, QUESTION);
        int searches = index.semanticSearches;
        model.priorAnswer = "{\"sufficient\": true, \"related\": true, \"query\": \"\"}";

        RagChatTurn turn = send(rag, id, "Und wofür genau?");

        assertEquals("nicht neu gesucht", searches, index.semanticSearches);
        assertEquals(1, turn.sources().size());
        assertEquals(QUARK, turn.sources().get(0).resource());
        assertEquals("nur die erste Nachricht wurde bewertet", 1, model.rankings);
    }

    @Test
    public void rankingParserReadsScoresAndIgnoresUnknownIds() {
        Map<Integer, Integer> scores = RelevanceRanking.parse(
                "{\"scores\": [{\"id\": 1, \"score\": 3}, {\"id\": \"2\", \"score\": 0}, {\"id\": 9, \"score\": 2}]}",
                2);
        assertNotNull(scores);
        assertEquals(Integer.valueOf(3), scores.get(1));
        assertEquals(Integer.valueOf(0), scores.get(2));
        assertEquals(2, scores.size());
        assertNull(RelevanceRanking.parse("keine Ahnung", 3));
    }

    private RagChatTurn send(EvidencePolicy policy, ContextSettings context, RetrievalPreparation preparation)
            throws Exception {
        return send(policy, context, preparation, RetrievalProgress.none());
    }

    private RagChatTurn send(final EvidencePolicy policy, ContextSettings context, RetrievalPreparation preparation,
                             RetrievalProgress progress) throws Exception {
        return send(adaptive(policy, context, preparation), chat.openConversation("System"), QUESTION, progress);
    }

    private RagChatUseCase adaptive(final EvidencePolicy policy, ContextSettings context,
                                    RetrievalPreparation preparation) {
        return new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null),
                new PromptContextAssembler(context), preparation,
                FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS), () -> policy);
    }

    private RagChatTurn send(RagChatUseCase rag, ChatConversationId id, String text) throws Exception {
        return send(rag, id, text, RetrievalProgress.none());
    }

    private RagChatTurn send(RagChatUseCase rag, ChatConversationId id, String text, RetrievalProgress progress)
            throws Exception {
        listener = new RecordingListener();
        RagChatTurn turn = rag.send(id, text, RagOptions.enabled(), null, listener, null, null, progress);
        listener.await();
        return turn;
    }

    /** Fügt beim ersten Nachladen die Historienseite in den Index ein, wie der Vorlader im Hintergrund. */
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
     * Das Modell: Nebenanfragen nach ihrer Anweisung beantwortet (Bewertung, Prüfung), die Antwort gestreamt.
     */
    private static final class ScriptedModel implements ChatCompletionPort {

        private static final Pattern NUMBERED = Pattern.compile("^\\[(\\d+)] Titel (\\S+)$", Pattern.MULTILINE);

        private final FakeChatCompletionPort answers = new FakeChatCompletionPort();
        private final Map<String, Integer> scores = new HashMap<String, Integer>();
        private Function<String, Boolean> sufficient = context -> true;
        private String query = "";
        volatile int rankings;
        volatile int checks;
        volatile String lastCheck = "";
        volatile String lastAnswerSystem = "";
        volatile String rankingAnswer;
        volatile String priorAnswer = "kein Urteil";

        ScriptedModel score(KnowledgeResource resource, int score) {
            scores.put(resource.id().value(), score);
            return this;
        }

        void sufficientWhen(Function<String, Boolean> rule) {
            sufficient = rule;
        }

        void nextQuery(String next) {
            query = next;
        }

        @Override
        public synchronized ChatResponse complete(ChatRequest request) {
            String instruction = request.messages().get(0).content();
            String user = request.messages().get(1).content();
            String answer;
            if (instruction.equals(FollowUpResearch.PRIOR_INSTRUCTION)) {
                answer = priorAnswer;
            } else if (instruction.equals(RelevanceRanking.INSTRUCTION)) {
                rankings++;
                answer = rankingAnswer != null ? rankingAnswer : ranking(user);
            } else {
                checks++;
                lastCheck = user;
                String context = user.substring(user.indexOf("Gefundener Kontext:"));
                answer = Boolean.TRUE.equals(sufficient.apply(context))
                        ? "{\"sufficient\": true, \"query\": \"\"}"
                        : "{\"sufficient\": false, \"query\": \"" + query + "\"}";
            }
            return new ChatResponse(ChatMessage.assistant(answer), ChatFinishReason.STOP, ChatUsage.notReported(),
                    "fake");
        }

        private String ranking(String user) {
            StringBuilder json = new StringBuilder("{\"scores\": [");
            Matcher numbered = NUMBERED.matcher(user);
            boolean first = true;
            while (numbered.find()) {
                Integer score = scores.get(numbered.group(2));
                json.append(first ? "" : ", ").append("{\"id\": ").append(numbered.group(1)).append(", \"score\": ")
                        .append(score == null ? 0 : score).append('}');
                first = false;
            }
            return json.append("]}").toString();
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
            lastAnswerSystem = request.messages().get(0).content();
            answers.enqueueAnswer("Antwort [1].");
            return answers.stream(request, listener);
        }
    }

    /** Legt einen Eintrag in den Index, auch während der Suche (wie der Vorlader). */
    private void add(KnowledgeIndexEntry entry) {
        index.index(Collections.singletonList(entry));
    }
}
