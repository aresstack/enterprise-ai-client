package com.aresstack.enterpriseai.astu.acropolis.rag;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagTestData.ObservedIndex;
import com.aresstack.enterpriseai.astu.acropolis.rag.RagTestData.ScriptedEmbeddingPort;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import org.junit.Before;
import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.Diagnostic;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.SentenceType;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TurnAnalysisObserver;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.NextAction;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlanningPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RagChatUseCaseTest {

    private static final String QUESTION = "Wie installiere ich Java unter Linux?";
    private static final KnowledgeResource JAVA = resource("wiki:Java", "wiki");
    private static final KnowledgeResource NIZZA = resource("wiki:NIZZA", "wiki");
    private static final String FOLLOW_UP = "Aber welches Format haben sie genau??";

    private FakeChatCompletionPort chatPort;
    private ChatService chat;
    private ObservedIndex index;
    private ScriptedEmbeddingPort embeddings;
    private RagChatUseCase rag;
    private RecordingListener last;

    @Before
    public void setUp() {
        chatPort = new FakeChatCompletionPort();
        chat = new ChatService(chatPort);
        index = new ObservedIndex(new InMemoryKnowledgeIndex());
        index.index(Collections.singletonList(
                entry(JAVA, 0, "Java Installation unter Linux mit apt install openjdk-8-jdk", vector(SPACE_3D, 1, 0, 0))));
        embeddings = new ScriptedEmbeddingPort(SPACE_3D).on(QUESTION, 1f, 0f, 0f).on("Danke", 0f, 1f, 0f);
        rag = new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null),
                new PromptContextAssembler(ContextSettings.defaults()));
    }

    @Test
    public void ragOffIsThePlainChatPathWithoutRetrieval() throws Exception {
        ChatConversationId id = chat.openConversation("Du bist hilfsbereit.");
        chatPort.enqueueAnswer("Antwort");

        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.disabled(), listener());
        awaitDone(turn);

        assertFalse(turn.retrievalRequested());
        assertTrue(turn.sources().isEmpty());
        assertEquals(0, index.keywordSearches + index.semanticSearches);
        assertTrue(embeddings.calls().isEmpty());
        assertEquals(Arrays.asList(ChatMessage.system("Du bist hilfsbereit."), ChatMessage.user(QUESTION)),
                chatPort.lastRequest().messages());
    }

    @Test
    public void ragOnPutsContextIntoTheSystemPartAndLeavesTheQuestionUnchanged() throws Exception {
        ChatConversationId id = chat.openConversation("Du bist hilfsbereit.");
        chatPort.enqueueAnswer("Mit apt [1].");

        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);

        assertEquals(1, turn.sources().size());
        assertEquals(JAVA, turn.sources().get(0).resource());
        List<ChatMessage> sent = chatPort.lastRequest().messages();
        assertEquals(2, sent.size());
        assertEquals(ChatRole.SYSTEM, sent.get(0).role());
        assertEquals("Du bist hilfsbereit.\n\n" + turn.context().text(), sent.get(0).content());
        assertTrue(sent.get(0).content().contains("apt install openjdk-8-jdk"));
        assertEquals(ChatMessage.user(QUESTION), sent.get(1));
        assertNoDeveloperRole(chatPort.requests());
    }

    @Test
    public void historyHoldsOnlyQuestionAndAnswerAndLaterTurnsDoNotSeeTheContext() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("Mit apt [1].");
        awaitDone(rag.send(id, QUESTION, RagOptions.enabled(), listener()));

        assertEquals(Arrays.asList(ChatMessage.user(QUESTION), ChatMessage.assistant("Mit apt [1].")),
                chat.conversation(id).messages());
        assertEquals(ChatRole.SYSTEM, chatPort.lastRequest().messages().get(0).role());

        chatPort.enqueueAnswer("Gern.");
        awaitDone(rag.send(id, "Danke", RagOptions.disabled(), listener()));

        ChatRequest second = chatPort.lastRequest();
        assertEquals(Arrays.asList(ChatMessage.user(QUESTION), ChatMessage.assistant("Mit apt [1]."),
                ChatMessage.user("Danke")), second.messages());
    }

    @Test
    public void withoutSystemPromptTheContextIsTheOnlySystemMessage() throws Exception {
        ChatConversationId id = chat.openConversation();
        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);

        List<ChatMessage> sent = chatPort.lastRequest().messages();
        assertEquals(ChatMessage.system(turn.context().text()), sent.get(0));
        assertEquals(1, countRole(sent, ChatRole.SYSTEM));
    }

    @Test
    public void noHitsMeansNoContext() throws Exception {
        ChatConversationId id = chat.openConversation("System");
        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.enabled()
                .restrictedTo(Collections.singleton(SourceAddress.of("confluence"))), listener());
        awaitDone(turn);

        assertTrue(turn.retrievalRequested());
        assertTrue(turn.sources().isEmpty());
        assertEquals(Arrays.asList(ChatMessage.system("System"), ChatMessage.user(QUESTION)),
                chatPort.lastRequest().messages());
    }

    @Test
    public void totalRetrievalFailureAnswersWithoutContextAndSaysSo() throws Exception {
        index.failKeyword = true;
        embeddings.failWith(new EmbeddingException(EmbeddingFailureKind.AUTHENTICATION, "nicht berechtigt"));
        ChatConversationId id = chat.openConversation();

        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.enabled(), listener());
        RecordingListener listener = awaitDone(turn);

        assertEquals("completed", listener.outcome());
        assertTrue(turn.retrievalFailed());
        assertEquals(2, turn.warnings().size());
        assertEquals(Collections.singletonList(ChatMessage.user(QUESTION)), chatPort.lastRequest().messages());
    }

    @Test
    public void partialFailureKeepsContextAndReportsTheWarning() throws Exception {
        embeddings.failWith(new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Timeout"));
        ChatConversationId id = chat.openConversation();

        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);

        assertFalse(turn.retrievalFailed());
        assertEquals(1, turn.sources().size());
        assertEquals(RetrievalPath.SEMANTIC, turn.warnings().get(0).path());
    }

    @Test
    public void busyConversationIsRejectedBeforeRetrieval() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueHanging("…");
        RagChatTurn first = rag.send(id, QUESTION, RagOptions.disabled(), listener());
        try {
            rag.send(id, QUESTION, RagOptions.enabled(), listener());
            fail("IllegalStateException erwartet");
        } catch (IllegalStateException expected) {
            assertEquals(0, index.keywordSearches);
        } finally {
            first.turn().cancel();
        }
    }

    @Test
    public void secondRagSendDuringRetrievalIsRejected() throws Exception {
        final java.util.concurrent.CountDownLatch inRetrieval = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        com.aresstack.enterpriseai.embedding.api.EmbeddingPort blocking =
                new com.aresstack.enterpriseai.embedding.api.EmbeddingPort() {
                    @Override
                    public com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity modelIdentity() {
                        return SPACE_3D;
                    }

                    @Override
                    public com.aresstack.enterpriseai.embedding.api.EmbeddingBatch embed(List<String> texts) {
                        inRetrieval.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return embeddings.embed(texts);
                    }
                };
        final RagChatUseCase slow = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(index, blocking, SPACE_3D, null), null);
        final ChatConversationId id = chat.openConversation();
        final RecordingListener first = new RecordingListener();
        Thread sender = new Thread(new Runnable() {
            @Override
            public void run() {
                slow.send(id, QUESTION, RagOptions.enabled(), first);
            }
        });
        sender.start();
        assertTrue(inRetrieval.await(5, java.util.concurrent.TimeUnit.SECONDS));
        try {
            slow.send(id, QUESTION, RagOptions.enabled(), new RecordingListener());
            fail("IllegalStateException erwartet");
        } catch (IllegalStateException expected) {
            assertEquals("nur ein Retrieval", 1, index.keywordSearches);
        } finally {
            release.countDown();
        }
        sender.join(5000);
        assertEquals("completed", first.await().outcome());
    }

    @Test
    public void cancelWorksOnARagTurn() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueHanging("Teil");
        RecordingListener listener = new RecordingListener();
        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.enabled(), listener);

        turn.turn().cancel();

        assertEquals("cancelled", listener.await().outcome());
        assertEquals(Collections.singletonList(ChatMessage.user(QUESTION)), chat.conversation(id).messages());
    }

    @Test
    public void sendWithBlankContextBehavesLikeSend() throws Exception {
        ChatConversationId id = chat.openConversation("S");
        RecordingListener listener = new RecordingListener();
        chat.sendWithContext(id, "Hallo", "  ", null, listener);
        listener.await();

        assertEquals(Arrays.asList(ChatMessage.system("S"), ChatMessage.user("Hallo")),
                chatPort.lastRequest().messages());
        assertNotEquals(0, chat.conversation(id).messages().size());
    }

    /**
     * Realfehler: Auf die Rückfrage „Welches Format haben sie genau?“ suchte der Chat nur mit diesem Text, fand
     * Fremdes und antwortete „steht nicht in den Quellen“. Mit Nachrecherche prüft das Modell den Kontext, nennt eine
     * genauere Anfrage aus dem Verlauf, und erst nach der zweiten Suche wird geantwortet.
     */
    @Test
    public void contextWithoutAnswerTriggersASecondSearchWithAPreciserQuery() throws Exception {
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS);
        ChatConversationId id = chat.openConversation("System");
        chat.restoreConversation(id, "System", Arrays.asList(ChatMessage.user("Was ist NIZZA?"),
                ChatMessage.assistant("NIZZA ist ein Fachsystem.")));
        chatPort.enqueueAnswer("{\"sufficient\": false, \"query\": \"NIZZA Dateiformat\"}");
        chatPort.enqueueAnswer("{\"sufficient\": true, \"query\": \"\"}");
        chatPort.enqueueAnswer("Zeilen fester Länge [1].");

        RagChatTurn turn = research.send(id, FOLLOW_UP, RagOptions.enabled(), listener());
        awaitDone(turn);

        assertEquals(Arrays.asList(FOLLOW_UP, "NIZZA Dateiformat"), turn.queries());
        assertEquals(2, turn.hitCounts().size());
        assertTrue(turn.hitCounts().get(1) > 0);
        assertEquals(2, index.semanticSearches);
        assertEquals("die neueste Suche steht vorn", NIZZA, turn.sources().get(0).resource());
        List<ChatRequest> requests = chatPort.requests();
        assertEquals("zwei Prüfungen, eine Antwort", 3, requests.size());
        String firstCheck = requests.get(0).messages().get(1).content();
        assertTrue(firstCheck, firstCheck.contains("Nutzer: Was ist NIZZA?"));
        assertTrue(firstCheck, firstCheck.contains("Aktuelle Nachricht: " + FOLLOW_UP));
        assertTrue(firstCheck, firstCheck.contains("openjdk"));
        assertTrue(requests.get(1).messages().get(1).content().contains("- NIZZA Dateiformat"));
        String system = chatPort.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("120 Zeichen"));
        assertTrue(system, system.contains("- NIZZA Dateiformat"));
        assertTrue(system, system.contains("Bitte den Nutzer nicht, Dateien oder Unterlagen anzuhängen"));
        assertEquals("die Prüfungen stehen nicht in der Historie", Arrays.asList(
                ChatMessage.user("Was ist NIZZA?"), ChatMessage.assistant("NIZZA ist ein Fachsystem."),
                ChatMessage.user(FOLLOW_UP), ChatMessage.assistant("Zeilen fester Länge [1].")),
                chat.conversation(id).messages());
    }

    /** Der Fortschritt kommt während der Suche, Schritt für Schritt, mit den Nachsuchen als weiteren Suchen. */
    @Test
    public void progressReportsEachSearchStepWhileItRuns() throws Exception {
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS);
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("{\"sufficient\": false, \"query\": \"NIZZA Dateiformat\"}");
        chatPort.enqueueAnswer("{\"sufficient\": true, \"query\": \"\"}");
        chatPort.enqueueAnswer("Zeilen fester Länge [1].");
        final List<String> steps = new java.util.ArrayList<String>();
        RetrievalProgress progress = new RetrievalProgress() {
            @Override
            public void searchStarted(String query, int number, int max) {
                steps.add("search " + number + "/" + max + " " + query);
            }

            @Override
            public void liveSearchStarted(String query) {
                steps.add("live");
            }

            @Override
            public void pathStarted(RetrievalPath path) {
                steps.add(path.name());
            }

            @Override
            public void hitsFound(String query, int hits) {
                steps.add("hits");
            }

            @Override
            public void contextCheckStarted() {
                steps.add("check");
            }

            @Override
            public void contextReady(int sources) {
                steps.add("ready " + sources);
            }
        };

        RagChatTurn turn = research.send(id, FOLLOW_UP, RagOptions.enabled(), null, listener(), null, null,
                progress);
        awaitDone(turn);

        assertEquals(Arrays.asList("search 1/3 " + FOLLOW_UP, "live", "KEYWORD", "SEMANTIC", "hits", "check",
                "search 2/3 NIZZA Dateiformat", "live", "KEYWORD", "SEMANTIC", "hits", "check",
                "ready " + turn.sources().size()), steps);
        assertEquals("Exportdaten unverändert", Arrays.asList(FOLLOW_UP, "NIZZA Dateiformat"), turn.queries());
    }

    /** Ein Abbruch während der Suche lässt die weiteren Suchschritte aus; der Turn startet trotzdem. */
    @Test
    public void cancelledProgressSkipsFurtherSearches() throws Exception {
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS);
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("Antwort");
        final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        RetrievalProgress progress = new RetrievalProgress() {
            @Override
            public void hitsFound(String query, int hits) {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        RagChatTurn turn = research.send(id, FOLLOW_UP, RagOptions.enabled(), null, listener(), null, null,
                progress);
        awaitDone(turn);

        assertEquals(Collections.singletonList(FOLLOW_UP), turn.queries());
        assertEquals("keine Kontextprüfung, nur die Antwort", 1, chatPort.requests().size());
    }

    @Test
    public void sufficientContextAnswersAfterOneSearch() throws Exception {
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("Mit apt [1].");

        RagChatTurn turn = research.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);

        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals(1, index.semanticSearches);
        assertEquals(2, chatPort.requests().size());
        assertEquals(JAVA, turn.sources().get(0).resource());
    }

    @Test
    public void followUpsStopAtTheLimitAndOnARepeatedQuery() throws Exception {
        embeddings.on("NIZZA Dateiformat", 0f, 0f, 1f).on("NIZZA Format Zeilen", 0f, 0f, 1f);
        RagChatUseCase research = withFollowUps(2);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"sufficient\": false, \"query\": \"NIZZA Dateiformat\"}");
        chatPort.enqueueAnswer("{\"sufficient\": false, \"query\": \"NIZZA Format Zeilen\"}");
        chatPort.enqueueAnswer("Antwort");

        RagChatTurn turn = research.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);

        assertEquals("höchstens zwei Nachsuchen", 3, turn.queries().size());
        assertEquals(3, chatPort.requests().size());
        assertEquals("Antwort", chat.conversation(id).messages().get(1).content());

        ChatConversationId other = chat.openConversation();
        chatPort.enqueueAnswer("{\"sufficient\": false, \"query\": \"wie installiere ich java unter linux\"}");
        chatPort.enqueueAnswer("Antwort");
        RagChatTurn repeated = research.send(other, QUESTION, RagOptions.enabled(), listener());
        awaitDone(repeated);
        assertEquals("dieselbe Anfrage noch einmal sucht nicht", Collections.singletonList(QUESTION),
                repeated.queries());
    }

    @Test
    public void unreadableOrFailedCheckAnswersFromTheFirstSearch() throws Exception {
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("Ich denke, das reicht.");
        chatPort.enqueueAnswer("Mit apt [1].");
        RagChatTurn turn = research.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);
        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals("Mit apt [1].", chat.conversation(id).messages().get(1).content());

        ChatConversationId other = chat.openConversation();
        chatPort.enqueueError(new com.aresstack.enterpriseai.chat.api.ChatCompletionException(
                com.aresstack.enterpriseai.chat.api.ChatErrorKind.TRANSPORT, "weg"));
        chatPort.enqueueAnswer("Mit apt [1].");
        RagChatTurn failed = research.send(other, QUESTION, RagOptions.enabled(), listener());
        awaitDone(failed);
        assertEquals(Collections.singletonList(QUESTION), failed.queries());
        assertEquals("Mit apt [1].", chat.conversation(other).messages().get(1).content());
    }

    /**
     * Abnahme Realfehler: Turn 1 findet Quellen, die Antwort scheitert danach. Turn 2 („die Quellen hattest du
     * schon …“) antwortet aus diesen Quellen ohne Live-Suche, ohne Indexsuche und ohne Embedding.
     */
    @Test
    public void afterAFailedAnswerTheNextQuestionReusesTheFoundSourcesWithoutSearching() throws Exception {
        CountingPreparation live = new CountingPreparation();
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS, live);
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueError(new com.aresstack.enterpriseai.chat.api.ChatCompletionException(
                com.aresstack.enterpriseai.chat.api.ChatErrorKind.PROTOCOL, "nach 8 Werkzeugrunden"));
        RagChatTurn first = research.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(first);
        assertEquals("failed", last.outcome());
        int sources = first.sources().size();
        assertTrue(sources > 0);
        assertEquals(sources, research.knowledgeContext(id).hits().size());
        int searches = index.keywordSearches + index.semanticSearches;
        int embedded = embeddings.calls().size();
        assertEquals(1, live.calls);

        RecordingProgress progress = new RecordingProgress();
        chatPort.enqueueAnswer("{\"sufficient\": true, \"related\": true, \"query\": \"\"}");
        chatPort.enqueueAnswer("Mit apt [1].");
        String again = "Die Quellen hattest du schon. Wie installiere ich Java?";
        RagChatTurn second = research.send(id, again, RagOptions.enabled(), null, listener(), null, null, progress);
        awaitDone(second);

        assertEquals("keine neue Indexsuche", searches, index.keywordSearches + index.semanticSearches);
        assertEquals("kein neues Embedding", embedded, embeddings.calls().size());
        assertEquals("keine neue Live-Suche", 1, live.calls);
        assertTrue(second.queries().isEmpty());
        assertEquals(sources, second.reusedSources());
        assertEquals(sources, second.sources().size());
        assertEquals(JAVA, second.sources().get(0).resource());
        assertEquals(Collections.singletonList("reuse " + sources), progress.events);
        String system = chatPort.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("apt install openjdk-8-jdk"));
        String check = chatPort.requests().get(chatPort.requests().size() - 2).messages().get(0).content();
        assertEquals(FollowUpResearch.PRIOR_INSTRUCTION, check);
    }

    /** Reichen die vorhandenen Quellen nicht, sucht der Chat gezielt nach, statt den ersten Suchlauf zu wiederholen. */
    @Test
    public void insufficientEarlierSourcesLeadToATargetedSearchNotARepeat() throws Exception {
        CountingPreparation live = new CountingPreparation();
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS, live);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("Mit apt [1].");
        awaitDone(research.send(id, QUESTION, RagOptions.enabled(), listener()));
        int semantic = index.semanticSearches;

        RecordingProgress progress = new RecordingProgress();
        chatPort.enqueueAnswer("{\"sufficient\": false, \"related\": true, \"query\": \"NIZZA Dateiformat\"}");
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("120 Zeichen [1].");
        RagChatTurn turn = research.send(id, "Und welches Format gilt dabei genau?", RagOptions.enabled(), null,
                listener(), null, null, progress);
        awaitDone(turn);

        assertEquals("nur die gezielte Nachsuche", Collections.singletonList("NIZZA Dateiformat"), turn.queries());
        assertEquals(semantic + 1, index.semanticSearches);
        assertEquals(2, live.calls);
        assertEquals(0, turn.reusedSources());
        assertEquals("die neue Suche steht vorn", NIZZA, turn.sources().get(0).resource());
        assertEquals(1, progress.events.size());
        assertTrue(progress.events.get(0), progress.events.get(0).startsWith("insufficient NIZZA Dateiformat"));
        String system = chatPort.lastRequest().messages().get(0).content();
        assertTrue("frühere und neue Anfrage im Suchhinweis", system.contains("- " + QUESTION)
                && system.contains("- NIZZA Dateiformat"));
    }

    /** Nach dem Öffnen eines gespeicherten Chats kommt der Kontext über die Chunk-IDs aus dem Index zurück. */
    @Test
    public void restoredContextFromChunkIdsAnswersWithoutSearching() throws Exception {
        RagChatUseCase research = withFollowUps(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("Mit apt [1].");
        awaitDone(research.send(id, QUESTION, RagOptions.enabled(), listener()));
        java.util.List<com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId> ids =
                new java.util.ArrayList<com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId>();
        for (RetrievedChunk hit : research.knowledgeContext(id).hits()) {
            ids.add(com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId.parse(hit.chunk().id().value()));
        }

        RagChatUseCase reopened = new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D,
                null), new PromptContextAssembler(ContextSettings.defaults()), null,
                FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS));
        reopened.restoreKnowledgeContext(id, Collections.singletonList(QUESTION), ids);
        assertEquals(ids.size(), reopened.knowledgeContext(id).hits().size());
        int searches = index.keywordSearches + index.semanticSearches;
        chatPort.enqueueAnswer("{\"sufficient\": true, \"related\": true}");
        chatPort.enqueueAnswer("Mit apt [1].");
        RagChatTurn turn = reopened.send(id, "Nochmal kurz: wie ging das?", RagOptions.enabled(), listener());
        awaitDone(turn);

        assertEquals(searches, index.keywordSearches + index.semanticSearches);
        assertEquals(JAVA, turn.sources().get(0).resource());
        assertTrue(turn.sources().get(0).hit().isRestored());
    }

    /** Ohne Nachrecherche bleibt alles wie bisher: jede Nachricht sucht, nichts wird gemerkt. */
    @Test
    public void withoutFollowUpsNothingIsKept() throws Exception {
        ChatConversationId id = chat.openConversation();
        awaitDone(rag.send(id, QUESTION, RagOptions.enabled(), listener()));
        assertTrue(rag.knowledgeContext(id).isEmpty());
    }

    private static final class CountingPreparation implements RetrievalPreparation {
        volatile int calls;

        @Override
        public void prepare(String query, java.util.Collection<SourceAddress> sources) {
            calls++;
        }
    }

    private static final class RecordingProgress implements RetrievalProgress {
        final List<String> events = new java.util.concurrent.CopyOnWriteArrayList<String>();

        @Override
        public void reusingSources(int sources) {
            events.add("reuse " + sources);
        }

        @Override
        public void reuseInsufficient(String query, int sources) {
            events.add("insufficient " + query + " " + sources);
        }
    }

    /**
     * Real case: a greeting with checked sources was searched verbatim. With the triage the model decides that the
     * message needs no knowledge: no search, no context block, no "no hits" turn.
     */
    @Test
    public void triageSkipsTheSearchForMessagesThatNeedNoKnowledge() throws Exception {
        RagChatUseCase triaged = withTriage(FollowUpResearch.off());
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("{\"search\": false, \"query\": \"\"}");
        chatPort.enqueueAnswer("Ich kann dir helfen.");

        RagChatTurn turn = triaged.send(id, "Hallo, was kannst du denn so alles für mich tun?",
                RagOptions.enabled(), listener());
        awaitDone(turn);

        assertFalse(turn.retrievalRequested());
        assertTrue(turn.queries().isEmpty());
        assertEquals(0, index.keywordSearches + index.semanticSearches);
        List<ChatRequest> requests = chatPort.requests();
        assertEquals("one triage, one answer", 2, requests.size());
        assertEquals(RetrievalTriage.INSTRUCTION, requests.get(0).messages().get(0).content());
        assertEquals(Arrays.asList(ChatMessage.system("System"),
                ChatMessage.user("Hallo, was kannst du denn so alles für mich tun?")),
                chatPort.lastRequest().messages());
    }

    @Test
    public void triageQueryReplacesTheVerbatimMessage() throws Exception {
        RagChatUseCase triaged = withTriage(FollowUpResearch.off());
        embeddings.on("Java Installation Linux", 1f, 0f, 0f);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"search\": true, \"query\": \"Java Installation Linux\"}");
        chatPort.enqueueAnswer("Mit apt [1].");

        RagChatTurn turn = triaged.send(id, "und wie geht das jetzt unter linux", RagOptions.enabled(), listener());
        awaitDone(turn);

        assertTrue(turn.retrievalRequested());
        assertEquals(Collections.singletonList("Java Installation Linux"), turn.queries());
        assertEquals(JAVA, turn.sources().get(0).resource());
    }

    @Test
    public void unreadableTriageSearchesWithTheMessageAsBefore() throws Exception {
        RagChatUseCase triaged = withTriage(FollowUpResearch.off());
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("keine Ahnung");
        chatPort.enqueueAnswer("Mit apt [1].");

        RagChatTurn turn = triaged.send(id, QUESTION, RagOptions.enabled(), listener());
        awaitDone(turn);

        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertEquals(1, turn.sources().size());
    }

    /**
     * Real case: dictation turned "Erklär mir" into an unknown word that was then searched four times. The origin
     * reaches triage and answer turn, so the model can resolve the recognition error from the sentence.
     */
    @Test
    public void speechTranscriptOriginReachesTriageAndAnswer() throws Exception {
        RagChatUseCase triaged = withTriage(FollowUpResearch.off());
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("{\"search\": false}");
        chatPort.enqueueAnswer("Gern, das Programm ...");

        RagChatTurn turn = triaged.send(id, "Aklima bitte erstmal das Programm", RagOptions.enabled(), null,
                listener(), null, null, RetrievalProgress.none(),
                com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin.SPEECH_TRANSCRIPT);
        awaitDone(turn);

        String note = com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin.SPEECH_TRANSCRIPT.modelNote();
        String triage = chatPort.requests().get(0).messages().get(1).content();
        assertTrue(triage, triage.contains(note));
        assertTrue(triage, triage.endsWith("Aktuelle Nachricht: Aklima bitte erstmal das Programm"));
        assertEquals(ChatMessage.system("System\n\n" + note), chatPort.lastRequest().messages().get(0));
        assertEquals("the note stays out of the history",
                ChatMessage.user("Aklima bitte erstmal das Programm"), chat.conversation(id).messages().get(0));
    }

    @Test
    public void typedMessagesWithoutRagCarryNoOriginNote() throws Exception {
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("Antwort");

        RagChatTurn turn = rag.send(id, QUESTION, RagOptions.disabled(), null, listener(), null, null,
                RetrievalProgress.none(), com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin.TYPED);
        awaitDone(turn);

        assertEquals(Arrays.asList(ChatMessage.system("System"), ChatMessage.user(QUESTION)),
                chatPort.lastRequest().messages());
    }

    /** After a search without hits the model is told not to try variants of the same terms. */
    @Test
    public void searchWithoutHitsAsksForUnderstandingInsteadOfVariants() throws Exception {
        RagChatUseCase research = withFollowUps(1);
        embeddings.on("Aklima", 0f, 1f, 0f);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("Was meinst du?");

        RagChatTurn turn = research.send(id, "Aklima", RagOptions.enabled()
                .restrictedTo(Collections.singleton(SourceAddress.of("confluence"))), listener());
        awaitDone(turn);

        assertTrue(turn.sources().isEmpty());
        String system = chatPort.lastRequest().messages().get(0).content();
        assertTrue(system, system.contains("Suche nicht mit Varianten oder Teilen derselben Begriffe weiter"));
        assertFalse(system, system.contains("suche selbst mit knowledge_search"));
    }

    @Test
    public void priorCheckCanDecideThatNoKnowledgeIsNeeded() throws Exception {
        RagChatUseCase triaged = withTriage(FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS));
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"search\": true, \"query\": \"" + QUESTION + "\"}");
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("Mit apt [1].");
        awaitDone(triaged.send(id, QUESTION, RagOptions.enabled(), listener()));
        int searches = index.keywordSearches + index.semanticSearches;

        chatPort.enqueueAnswer("{\"needed\": false, \"sufficient\": false, \"related\": false, \"query\": \"\"}");
        chatPort.enqueueAnswer("Gern.");
        RagChatTurn thanks = triaged.send(id, "Danke dir, das hat geholfen", RagOptions.enabled(), listener());
        awaitDone(thanks);

        assertFalse(thanks.retrievalRequested());
        assertEquals(searches, index.keywordSearches + index.semanticSearches);
        assertEquals(ChatRole.USER, chatPort.lastRequest().messages().get(0).role());
        assertFalse("the earlier context stays for later messages",
                triaged.knowledgeContext(id).isEmpty());
    }

    /**
     * Real case: an internal acronym asked mid-conversation was declined by the triage because the model knew a public
     * meaning. The principles ask whether a term is resolved in the context, not whether some meaning is known; triage
     * and the check of earlier context share them, and the client analysis is only a hint.
     */
    @Test
    public void principlesAskWhetherATermIsResolvedNotWhetherItIsKnown() {
        String routing = RetrievalTriage.ROUTING;
        assertTrue(routing, routing.contains("nicht, ob du irgendeine Bedeutung kennst, sondern ob der Begriff hier "
                + "eindeutig aufgelöst ist"));
        assertTrue(routing, routing.contains("<client-turn-analysis>"));
        assertTrue(routing, routing.contains("als Hinweis zur Form der Nachricht, nicht als Wahrheit"));
        assertTrue(routing, routing.indexOf("Anwendungshilfe") < routing.indexOf("Organisationswissen"));
        assertTrue(routing, routing.contains("Ausdrücklicher Suchauftrag (search = true)"));
        assertTrue(RetrievalTriage.INSTRUCTION.contains(routing));
        assertTrue(FollowUpResearch.PRIOR_INSTRUCTION.contains(routing));
    }

    /** Mode "knowledge": an acronym asked in passing after an earlier turn is searched with the acronym. */
    @Test
    public void acronymAskedMidConversationIsSearched() throws Exception {
        RagChatUseCase triaged = withTriage(FollowUpResearch.upTo(FollowUpResearch.DEFAULT_MAX_FOLLOW_UPS));
        embeddings.on("QZX", 1f, 0f, 0f);
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"search\": true, \"query\": \"" + QUESTION + "\"}");
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("Mit apt [1].");
        awaitDone(triaged.send(id, QUESTION, RagOptions.enabled(), listener()));

        chatPort.enqueueAnswer("{\"needed\": true, \"sufficient\": false, \"related\": false, \"query\": \"QZX\"}");
        chatPort.enqueueAnswer("{\"sufficient\": true}");
        chatPort.enqueueAnswer("QZX ist [1].");
        RagChatTurn turn = triaged.send(id, "Okay, aber vorher muss ich wissen, was QZX ist.", RagOptions.enabled(),
                listener());
        awaitDone(turn);

        assertTrue(turn.retrievalRequested());
        assertTrue(turn.queries().toString(), turn.queries().contains("QZX"));
    }

    /** Without hits for an acronym the answer names the gap instead of silently picking a public meaning. */
    @Test
    public void searchWithoutHitsKeepsAPublicMeaningAsMereReading() {
        String note = RagChatUseCase.searchNote(Collections.singletonList("QZX"), false);
        assertTrue(note, note.contains("dass die internen Quellen dazu nichts enthalten"));
        assertTrue(note, note.contains("wähle sie nicht stillschweigend"));
        String found = RagChatUseCase.searchNote(Collections.singletonList("QZX"), true);
        assertTrue(found, found.contains("hat sie Vorrang vor einer öffentlichen Bedeutung"));
    }

    /**
     * The analysis reaches triage and answer as a transient block of this turn; the stored message stays the user's
     * text, and the observer sees the same block.
     */
    @Test
    public void analysisBlockReachesTriageAndAnswerButNotTheHistory() throws Exception {
        RecordingAnalyzer analyzer = new RecordingAnalyzer();
        final List<String> observed = new ArrayList<String>();
        RagChatUseCase analyzed = withAnalyzer(analyzer, new TurnAnalysisObserver() {
            @Override
            public void analyzed(String userText, MessageOrigin origin, LinguisticFrame frame, String block) {
                observed.add(block);
            }
        });
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("{\"search\": false}");
        chatPort.enqueueAnswer("Meinst du „Erklär mir“?");

        awaitDone(analyzed.send(id, "Aklima bitte erstmal das Programm", RagOptions.enabled(), null, listener(), null,
                null, RetrievalProgress.none(), MessageOrigin.SPEECH_TRANSCRIPT));

        assertEquals(Collections.singletonList("analyze"), analyzer.calls);
        assertEquals(1, observed.size());
        String block = observed.get(0);
        assertTrue(block, block.startsWith("<client-turn-analysis>") && block.endsWith("</client-turn-analysis>"));
        assertTrue(block, block.contains("suspected-tokens: Aklima"));
        String triage = chatPort.requests().get(0).messages().get(1).content();
        assertTrue(triage, triage.contains(block + "\n\nAktuelle Nachricht: Aklima bitte erstmal das Programm"));
        assertEquals(ChatMessage.system("System\n\n" + block), chatPort.lastRequest().messages().get(0));
        assertEquals(ChatMessage.user("Aklima bitte erstmal das Programm"), chat.conversation(id).messages().get(0));
    }

    /** Without RAG the turn may start on the UI thread: the analysis then loads no models. */
    @Test
    public void withoutRagTheAnalysisLoadsNoModels() throws Exception {
        RecordingAnalyzer analyzer = new RecordingAnalyzer();
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("Hallo!");

        awaitDone(withAnalyzer(analyzer, null).send(id, "Hallo", RagOptions.disabled(), null, listener(), null, null,
                RetrievalProgress.none(), MessageOrigin.TYPED));

        assertEquals(Collections.singletonList("withoutModels"), analyzer.calls);
        assertTrue(chatPort.lastRequest().messages().get(0).content().contains("source: TEXT"));
    }

    /**
     * The answer planning step runs before every answer, even without sources: it sees source and analysis, and the
     * answering call sees original and interpretation side by side. Only the original message is stored.
     */
    @Test
    public void planningReadsTheTurnAndTheAnswerSeesOriginalAndInterpretation() throws Exception {
        RecordingAnalyzer analyzer = new RecordingAnalyzer();
        final List<String> planned = new ArrayList<String>();
        RagChatUseCase reading = withPlanning(analyzer, new TurnAnalysisObserver() {
            @Override
            public void analyzed(String userText, MessageOrigin origin, LinguisticFrame frame, String block) {
            }

            @Override
            public void planned(String userText, AnswerPlan turn, String block) {
                planned.add(block);
            }
        });
        ChatConversationId id = chat.openConversation("System");
        chatPort.enqueueAnswer("{\"interpretedText\": \"Erklär mir bitte erstmal das Programm\", "
                + "\"intent\": \"REQUEST_EXPLANATION\", \"target\": \"dieses Programm\", "
                + "\"targetKind\": \"CURRENT_APPLICATION\", \"informationNeed\": \"APPLICATION_HELP\", "
                + "\"asrErrorLikely\": true, \"corrections\": [{\"original\": \"Aklima\", "
                + "\"interpretedAs\": \"Erklär mir\", \"reason\": \"no predicate\"}], \"ambiguity\": \"LOW\", "
                + "\"confidence\": 0.93, \"nextAction\": \"USE_APPLICATION_HELP\", \"knowledgeQuery\": \"\"}");
        chatPort.enqueueAnswer("Gern, das Programm ...");

        assertTrue(reading.plansAnswers());
        awaitDone(reading.send(id, "Aklima bitte erstmal das Programm", RagOptions.disabled(), null, listener(), null,
                null, RetrievalProgress.none(), MessageOrigin.SPEECH_TRANSCRIPT));

        assertEquals("the step may load models: it never runs on the UI thread",
                Collections.singletonList("analyze"), analyzer.calls);
        assertEquals(2, chatPort.requests().size());
        List<ChatMessage> step = chatPort.requests().get(0).messages();
        assertTrue(step.get(0).content(), step.get(0).content().startsWith("Du planst die Antwort"));
        assertTrue(step.get(1).content(), step.get(1).content().contains("SPEECH (Diktat)"));
        assertTrue(step.get(1).content(), step.get(1).content().contains("suspected-tokens: Aklima"));
        String answer = chatPort.lastRequest().messages().get(0).content();
        assertTrue(answer, answer.contains("original-user-message: Aklima bitte erstmal das Programm"));
        assertTrue(answer, answer.contains("likely-intended-meaning: Erklär mir bitte erstmal das Programm"));
        assertTrue(answer, answer.contains("correction: \"Aklima\" -> \"Erklär mir\""));
        assertTrue(answer, answer.contains("information-need: APPLICATION_HELP"));
        assertTrue(answer, answer.contains("planned-next-action: USE_APPLICATION_HELP"));
        assertTrue(answer, answer.contains("sag kurz, wie du die Nachricht verstanden hast"));
        assertFalse("the answer reads the interpretation, not the raw analysis", answer.contains("<client-turn-analysis>"));
        assertEquals(Collections.singletonList(answer.substring(answer.indexOf("<client-answer-plan>"))),
                planned);
        assertEquals(ChatMessage.user("Aklima bitte erstmal das Programm"), chat.conversation(id).messages().get(0));
        assertEquals(2, chat.conversation(id).messages().size());
    }

    /** With sources checked the reading replaces the triage: one aside request, then the search with its query. */
    @Test
    public void withSourcesTheReadingReplacesTheTriage() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"interpretedText\": \"" + QUESTION + "\", \"intent\": \"QUESTION\", "
                + "\"informationNeed\": \"INTERNAL_KNOWLEDGE\", \"confidence\": 0.9, "
                + "\"nextAction\": \"SEARCH_INTERNAL_KNOWLEDGE\", \"knowledgeQuery\": \"" + QUESTION + "\"}");
        chatPort.enqueueAnswer("Mit apt.");

        RagChatTurn turn = withPlanning(null, null).send(id, "wie installier ich jawa unter linux",
                RagOptions.enabled(), null, listener(), null, null, RetrievalProgress.none(), MessageOrigin.TYPED);
        awaitDone(turn);

        assertEquals(2, chatPort.requests().size());
        assertEquals(Collections.singletonList(QUESTION), turn.queries());
        assertFalse(turn.sources().isEmpty());
    }

    /** A statement about an unknown term needs no information: no search, the answer reacts directly. */
    @Test
    public void aStatementIsNotResearched() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"intent\": \"ASSERTION\", \"target\": \"QZX\", \"targetKind\": \"ACRONYM\", "
                + "\"informationNeed\": \"NONE\", \"organizationSpecificMeaningPossible\": true, "
                + "\"ambiguity\": \"HIGH\", \"confidence\": 0.95, \"nextAction\": \"ANSWER_DIRECTLY\", "
                + "\"knowledgeQuery\": \"\"}");
        chatPort.enqueueAnswer("Schön, dass es dir gefällt.");

        RagChatTurn turn = withPlanning(null, null).send(id, "QZX ist ein super Tool.", RagOptions.enabled(),
                null, listener(), null, null, RetrievalProgress.none(), MessageOrigin.TYPED);
        awaitDone(turn);

        assertFalse(turn.retrievalRequested());
        assertEquals(2, chatPort.requests().size());
        String answer = chatPort.lastRequest().messages().get(0).content();
        assertTrue(answer, answer.contains("information-need: NO_INFORMATION_NEEDED"));
        assertTrue(answer, answer.contains("organization-specific-meaning-possible: yes"));
    }

    /** Below the ask threshold the answer asks back instead of searching with an unsure reading. */
    @Test
    public void anUnsureReadingAsksBackInsteadOfSearching() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"intent\": \"QUESTION\", \"informationNeed\": \"TERM_RESOLUTION\", "
                + "\"confidence\": 0.4, \"nextAction\": \"SEARCH_INTERNAL_KNOWLEDGE\", "
                + "\"knowledgeQuery\": \"" + QUESTION + "\"}");
        chatPort.enqueueAnswer("Was meinst du genau?");

        RagChatTurn turn = withPlanning(null, null).send(id, "jawa dings", RagOptions.enabled(), null,
                listener(), null, null, RetrievalProgress.none(), MessageOrigin.TYPED);
        awaitDone(turn);

        assertFalse(turn.retrievalRequested());
        String answer = chatPort.lastRequest().messages().get(0).content();
        assertTrue(answer, answer.contains("Frag kurz und konkret nach"));
    }

    /** Without a planned query the search uses the interpreted message. */
    @Test
    public void aPlannedSearchWithoutQueryUsesTheInterpretedMessage() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"interpretedText\": \"" + QUESTION + "\", \"intent\": \"QUESTION\", "
                + "\"informationNeed\": \"INTERNAL_KNOWLEDGE\", \"confidence\": 0.7, "
                + "\"nextAction\": \"SEARCH_INTERNAL_KNOWLEDGE\"}");
        chatPort.enqueueAnswer("Mit apt.");

        RagChatTurn turn = withPlanning(null, null).send(id, "wie installier ich jawa unter linux",
                RagOptions.enabled(), null, listener(), null, null, RetrievalProgress.none(), MessageOrigin.TYPED);
        awaitDone(turn);

        assertEquals(Collections.singletonList(QUESTION), turn.queries());
    }

    /**
     * When plausible readings lead to substantially different actions the plan asks back: the question is the answer,
     * the main agent never starts, nothing is searched, and the question stays in the history.
     */
    @Test
    public void aClarifyingPlanAnswersWithTheQuestionWithoutTheMainAgent() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"intent\": \"TASK\", \"target\": \"es\", "
                + "\"targetKind\": \"CONVERSATION_REFERENCE\", \"informationNeed\": \"UNKNOWN\", "
                + "\"ambiguity\": \"HIGH\", \"confidence\": 0.5, \"nextAction\": \"ASK_CLARIFICATION\", "
                + "\"clarificationQuestion\": \"Was soll ich löschen: den Entwurf oder den ganzen Chat?\"}");
        final List<String> steps = new ArrayList<String>();
        RetrievalProgress progress = new RetrievalProgress() {
            @Override
            public void planningStarted() {
                steps.add("planning");
            }

            @Override
            public void planningFinished(NextAction next) {
                steps.add(next.name());
            }

            @Override
            public void searchStarted(String query, int number, int max) {
                steps.add("search");
            }
        };

        RagChatTurn turn = withPlanning(null, null).send(id, "lösch es", RagOptions.enabled(), null, listener(),
                null, null, progress, MessageOrigin.TYPED);
        awaitDone(turn);

        assertEquals("only the planning request reached the model", 1, chatPort.requests().size());
        assertFalse(turn.retrievalRequested());
        assertEquals(Arrays.asList("planning", "ASK_CLARIFICATION"), steps);
        List<ChatMessage> history = chat.conversation(id).messages();
        assertEquals(ChatMessage.user("lösch es"), history.get(0));
        assertEquals(ChatMessage.assistant("Was soll ich löschen: den Entwurf oder den ganzen Chat?"),
                history.get(1));
    }

    /** An unknown term is no reason to ask back: the plan searches, and the progress reports the planning first. */
    @Test
    public void planningIsTheFirstStepOfEveryTurn() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("{\"intent\": \"REQUEST_DEFINITION\", \"target\": \"QZX\", "
                + "\"targetKind\": \"ACRONYM\", \"informationNeed\": \"TERM_RESOLUTION\", "
                + "\"organizationSpecificMeaningPossible\": true, \"confidence\": 0.8, "
                + "\"nextAction\": \"SEARCH_INTERNAL_KNOWLEDGE\", \"knowledgeQuery\": \"QZX\"}");
        chatPort.enqueueAnswer("QZX ist ...");
        final List<String> steps = new ArrayList<String>();
        RetrievalProgress progress = new RetrievalProgress() {
            @Override
            public void planningStarted() {
                steps.add("planning");
            }

            @Override
            public void planningFinished(NextAction next) {
                steps.add(next.name());
            }

            @Override
            public void triageStarted() {
                steps.add("triage");
            }

            @Override
            public void searchStarted(String query, int number, int max) {
                steps.add("search " + query);
            }
        };

        awaitDone(withPlanning(null, null).send(id, "Was ist QZX?", RagOptions.enabled(), null, listener(), null,
                null, progress, MessageOrigin.TYPED));

        assertEquals(Arrays.asList("planning", "SEARCH_INTERNAL_KNOWLEDGE", "search QZX"), steps);
    }

    /** An unreadable reading changes nothing: the triage decides as before, and the answer gets no understanding. */
    @Test
    public void anUnreadableReadingFallsBackToTheTriage() throws Exception {
        ChatConversationId id = chat.openConversation();
        chatPort.enqueueAnswer("Das kann ich nicht sagen.");
        chatPort.enqueueAnswer("{\"search\": false}");
        chatPort.enqueueAnswer("Hallo!");

        RagChatTurn turn = withPlanning(null, null).send(id, "Hallo", RagOptions.enabled(), null, listener(),
                null, null, RetrievalProgress.none(), MessageOrigin.TYPED);
        awaitDone(turn);

        assertEquals(3, chatPort.requests().size());
        assertFalse(turn.retrievalRequested());
        assertFalse(chatPort.lastRequest().messages().get(0).content().contains("<client-answer-plan>"));
    }

    private RagChatUseCase withPlanning(LinguisticAnalyzer analyzer, TurnAnalysisObserver observer) {
        return new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null),
                new PromptContextAssembler(ContextSettings.defaults()), null, FollowUpResearch.off(), null,
                RetrievalTriage.byModel(), analyzer, observer, () -> AnswerPlanningPolicy.defaults());
    }

    private RagChatUseCase withAnalyzer(LinguisticAnalyzer analyzer, TurnAnalysisObserver observer) {
        return new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null),
                new PromptContextAssembler(ContextSettings.defaults()), null, FollowUpResearch.off(), null,
                RetrievalTriage.byModel(), analyzer, observer);
    }

    /** Describes every message as a dictation with a suspected first word and records which method ran. */
    private static final class RecordingAnalyzer implements LinguisticAnalyzer {
        final List<String> calls = new ArrayList<String>();

        @Override
        public LinguisticFrame analyze(String text, MessageOrigin origin) {
            calls.add("analyze");
            return LinguisticFrame.builder(origin).language("de").sentenceType(SentenceType.REQUEST)
                    .suspectedToken(text.split(" ")[0]).diagnostic(Diagnostic.POSSIBLE_ASR_ERROR).build();
        }

        @Override
        public LinguisticFrame analyzeWithoutModels(String text, MessageOrigin origin) {
            calls.add("withoutModels");
            return LinguisticFrame.builder(origin).build();
        }
    }

    private RagChatUseCase withTriage(FollowUpResearch followUps) {
        return new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null),
                new PromptContextAssembler(ContextSettings.defaults()), null, followUps, null,
                RetrievalTriage.byModel());
    }

    private RagChatUseCase withFollowUps(int maxFollowUps) {
        return withFollowUps(maxFollowUps, null);
    }

    private RagChatUseCase withFollowUps(int maxFollowUps, RetrievalPreparation preparation) {
        index.index(Collections.singletonList(entry(NIZZA, 0,
                "NIZZA Dateiformat: jede Zeile hat eine feste Länge von 120 Zeichen", vector(SPACE_3D, 0, 0, 1))));
        embeddings.on(FOLLOW_UP, 1f, 0f, 0f).on("NIZZA Dateiformat", 0f, 0f, 1f);
        return new RagChatUseCase(chat, new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, null),
                new PromptContextAssembler(ContextSettings.defaults()), preparation,
                FollowUpResearch.upTo(maxFollowUps));
    }

    private RecordingListener listener() {
        last = new RecordingListener();
        return last;
    }

    /** Wartet auf den Abschluss des zuletzt gestarteten Turns. */
    private RecordingListener awaitDone(RagChatTurn turn) throws InterruptedException {
        last.await();
        assertTrue(turn.turn().isDone());
        return last;
    }

    private static void assertNoDeveloperRole(List<ChatRequest> requests) {
        for (ChatRequest request : requests) {
            assertEquals(0, countRole(request.messages(), ChatRole.DEVELOPER));
        }
    }

    private static int countRole(List<ChatMessage> messages, ChatRole role) {
        int n = 0;
        for (ChatMessage m : messages) {
            if (m.role() == role) {
                n++;
            }
        }
        return n;
    }
}
