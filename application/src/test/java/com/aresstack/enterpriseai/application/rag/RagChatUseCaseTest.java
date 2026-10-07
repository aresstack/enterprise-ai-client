package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.rag.RagTestData.ObservedIndex;
import com.aresstack.enterpriseai.application.rag.RagTestData.ScriptedEmbeddingPort;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RagChatUseCaseTest {

    private static final String QUESTION = "Wie installiere ich Java unter Linux?";
    private static final KnowledgeResource JAVA = resource("wiki:Java", "wiki");

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
                .restrictedTo(Collections.singleton(KnowledgeSourceId.of("confluence"))), listener());
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
                    public com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity modelIdentity() {
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
