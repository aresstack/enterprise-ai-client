package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.SourceReference;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.application.chat.ChatTurnListener;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.rag.PromptContextAssembler;
import com.aresstack.enterpriseai.application.rag.RagChatUseCase;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.aresstack.enterpriseai.app.chat.UiTestSupport.EDT;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.awaitActivity;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.awaitIdle;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.entries;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * AP22: Die RAG-Anbindung der Shell gegen die Fakes der Stränge A, C, D und E über die echten Use Cases aus
 * {@code application}: Quellen an der Antwort, RAG aus als bisheriger Weg, Stop während des Retrievals,
 * ausgefallene Suche ganz oder teilweise, keine Treffer, abgelehnter Turn.
 */
public class RagChatBindingTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    private final ExecutorService workers = UiTestSupport.workers("rag-binding-test");
    private final FakeChatCompletionPort port = new FakeChatCompletionPort();
    private final ChatService chat = new ChatService(port);
    private final ChatConversationId conversation = chat.openConversation("Antworte knapp.");
    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final EmbeddingModelIdentity space = embeddings.modelIdentity();
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource source = new InMemoryKnowledgeSource("handbuch")
            .add("urlaub", "Urlaubsregelung", "Mitarbeiter haben dreißig Tage Urlaub im Jahr. Resturlaub verfällt "
                    + "am 31. März des Folgejahres.")
            .add("kuendigung", "Kündigungsfrist", "Die Kündigungsfrist beträgt drei Monate zum Quartalsende. Sie "
                    + "gilt für beide Seiten.");

    @After
    public void tearDown() {
        workers.shutdownNow();
    }

    @Test
    public void ragOnAttachesSourcesAndSendsTheContextOnlyForThisTurn() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei", " Monate [1].");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(2, entries.size());
        TranscriptEntry answer = entries.get(1);
        assertEquals(TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals("Drei Monate [1].", answer.getText());
        assertTrue("Quellen an der Antwort", answer.hasSources());
        SourceReference first = answer.getSources().get(0);
        assertEquals(1, first.getNumber());
        assertEquals("Kündigungsfrist", first.getTitle());
        assertEquals(source.idOf("kuendigung").value(), first.getLocation());
        assertTrue(first.getRevision(), first.getRevision().startsWith("Version 1, 2023-11-14"));
        assertTrue(first.getKeywordRank() >= 1);

        List<ChatMessage> sent = port.lastRequest().messages();
        assertEquals(ChatRole.SYSTEM, sent.get(0).role());
        assertTrue(sent.get(0).content().startsWith("Antworte knapp.\n\n"));
        assertTrue(sent.get(0).content().contains("--- KONTEXT ---"));
        assertTrue(sent.get(0).content().contains("[1] Kündigungsfrist"));
        assertEquals("Wie lange ist die Kündigungsfrist?", sent.get(sent.size() - 1).content());

        List<ChatMessage> history = chat.conversation(conversation).messages();
        assertEquals("Kontext nicht in der Historie", 2, history.size());
        assertEquals(ChatRole.USER, history.get(0).role());
        assertEquals("Drei Monate [1].", history.get(1).content());
    }

    @Test
    public void ragOffIsExactlyThePlainChatPath() throws Exception {
        indexAll();
        int embeddingCalls = embeddings.calls().size();
        port.enqueueAnswer("Hallo");
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", false);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(2, entries.size());
        assertFalse(entries.get(1).hasSources());
        assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
        assertEquals("kein Embedding-Aufruf", embeddingCalls, embeddings.calls().size());
    }

    @Test
    public void stopDuringRetrievalCancelsTheTurnAsSoonAsItExists() throws Exception {
        indexAll();
        final GatedIndex gated = new GatedIndex(index);
        // Die Antwort bleibt offen wie ein echter Stream; ein Backend, das vor dem Stop fertig ist, hat gewonnen.
        port.enqueueHanging("Zu spät");
        final Fixture fixture = fixture(gated, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitActivity(fixture.model, RagChatBinding.RETRIEVING_ACTIVITY);
        assertTrue(gated.awaitSearch());
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertTrue(fixture.model.canStop());
                fixture.binding.stopRequested();
            }
        });
        gated.release();
        awaitIdle(fixture.model);

        TranscriptEntry answer = entries(fixture.model).get(1);
        assertEquals(TranscriptEntry.State.CANCELLED, answer.getState());
        assertEquals("", answer.getActivity());
        List<ChatMessage> history = chat.conversation(conversation).messages();
        assertEquals("Nutzerfrage bleibt, Antwort nicht", 1, history.size());
        assertEquals(ChatRole.USER, history.get(0).role());
        assertFalse(chat.isBusy(conversation));
        assertTrue("Senden wieder möglich", onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return fixture.model.canSend("weiter");
            }
        }));
    }

    @Test
    public void retrievalFailureBecomesANoticeAndTheAnswerGoesWithoutContext() throws Exception {
        port.enqueueAnswer("Ohne Wissen");
        final Fixture fixture = fixture(new FailingIndex(), embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Frage", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertEquals(TranscriptEntry.Author.NOTICE, entries.get(2).getAuthor());
        assertEquals(RagChatBinding.RETRIEVAL_FAILED_NOTICE, entries.get(2).getText());
        assertEquals("Ohne Wissen", entries.get(1).getText());
        assertFalse(entries.get(1).hasSources());
        assertEquals("Antworte knapp.", port.lastRequest().messages().get(0).content());
    }

    @Test
    public void failedSemanticPathBecomesANoticeWhileKeywordSourcesRemain() throws Exception {
        indexAll();
        port.enqueueAnswer("Drei Monate");
        final Fixture fixture = fixture(index, new FailingEmbeddings(embeddings));
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_NOTICE, entries.get(2).getText());
        assertTrue(entries.get(1).hasSources());
        assertEquals(0, entries.get(1).getSources().get(0).getSemanticRank());
        assertTrue(entries.get(1).getSources().get(0).getKeywordRank() >= 1);
    }

    @Test
    public void noHitsBecomeANotice() throws Exception {
        port.enqueueAnswer("Weiß ich nicht");
        final Fixture fixture = fixture(index, embeddings); // leerer Index
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Frage", true);
            }
        });
        awaitIdle(fixture.model);

        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertEquals(RagChatBinding.NO_SOURCES_NOTICE, entries.get(2).getText());
        assertFalse(entries.get(1).hasSources());
    }

    @Test
    public void rejectedTurnBecomesAnErrorBubbleWithoutDetails() throws Exception {
        indexAll();
        port.enqueueHanging("beschäftigt");
        chat.send(conversation, "direkt", new ChatTurnListener() {
            @Override
            public void onDelta(String text) {
            }

            @Override
            public void onCompleted(ChatResponse response) {
            }

            @Override
            public void onFailed(ChatCompletionException error) {
            }

            @Override
            public void onCancelled() {
            }
        });
        final Fixture fixture = fixture(index, embeddings);
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Frage", true);
            }
        });
        awaitIdle(fixture.model);
        TranscriptEntry answer = entries(fixture.model).get(1);
        assertEquals(TranscriptEntry.State.FAILED, answer.getState());
        assertEquals(RagChatBinding.SEND_REJECTED, answer.getFailureMessage());
        chat.cancel(conversation);
    }

    @Test
    public void revisionAndLocationAreHumanReadable() throws Exception {
        RagChatBinding binding = fixture(index, embeddings).binding;
        assertEquals("", binding.revision(KnowledgeRevision.unknown()));
        assertEquals("Version 42", binding.revision(KnowledgeRevision.version("42")));
        assertEquals("2026-10-01 08:00", binding.revision(KnowledgeRevision.modifiedAt(
                Instant.parse("2026-10-01T08:00:00Z"))));
        assertEquals("Version 7, 2026-10-01 10:30", binding.revision(KnowledgeRevision.of(
                Instant.parse("2026-10-01T10:30:00Z"), "7")));
    }

    @Test
    public void lateSourcesOfAFastAnswerLeaveTheNextRetrievalUntouched() throws Exception {
        // Regression (Copilot-Finding): Antwort A ist fertig, bevor attach(A) den UI-Thread erreicht; Frage B
        // sucht schon. attach(A) darf den Suchzustand von B nicht löschen, sonst geht ein Stop während B verloren.
        indexAll();
        final ManualExecutor ui = new ManualExecutor();
        final ManualExecutor work = new ManualExecutor();
        final CompletionAwaitingPort gate = new CompletionAwaitingPort(port);
        ChatService gatedChat = new ChatService(gate);
        ChatConversationId id = gatedChat.openConversation("Antworte knapp.");
        RagChatUseCase rag = new RagChatUseCase(gatedChat,
                new RetrieveKnowledgeUseCase(index, embeddings, space, null), new PromptContextAssembler(null));
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        RagChatBinding binding = new RagChatBinding(rag, id, model, ui, work, UTC);

        port.enqueueAnswer("Schnell.");
        gate.awaitCompletionOnNextStream();
        binding.sendRequested("Frage A", true);
        work.runAll(); // Suche A und Turn A; der Fake ist fertig, bevor send() zurückkehrt
        ui.runWhile(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return model.getEntries().get(1).getState() == TranscriptEntry.State.STREAMING;
            }
        });
        List<TranscriptEntry> entries = model.getEntries();
        assertEquals(TranscriptEntry.State.COMPLETE, entries.get(1).getState());
        assertFalse("Quellen von A sind noch unterwegs", entries.get(1).hasSources());
        assertEquals("attach(A) wartet noch auf dem UI-Thread", 1, ui.pending());

        port.enqueueHanging("Langsam");
        binding.sendRequested("Frage B", true); // B beginnt die Suche
        ui.runAll();                             // attach(A): Quellen an A, Zustand von B unberührt
        entries = model.getEntries();
        assertEquals(4, entries.size());
        assertTrue("späte Quellen landen an der fertigen Antwort", entries.get(1).hasSources());
        assertEquals(TranscriptEntry.State.STREAMING, entries.get(3).getState());

        binding.stopRequested(); // während der Suche von B
        work.runAll();           // Suche B und Turn B (hängt)
        ui.runAll();             // attach(B) löst den Stop ein
        ui.runUntil("Antwort B abgebrochen", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return model.getEntries().get(3).getState() != TranscriptEntry.State.STREAMING;
            }
        });
        entries = model.getEntries();
        assertEquals(TranscriptEntry.State.CANCELLED, entries.get(3).getState());
        assertTrue(entries.get(3).hasSources());
        assertTrue(model.canSend("Nochmal"));
    }

    @Test
    public void rejectedWorkExecutorFailsTheAnswerInsteadOfLeavingItOpen() throws Exception {
        indexAll();
        Executor rejecting = new Executor() {
            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("shut down");
            }
        };
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final RagChatBinding binding = new RagChatBinding(new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(index, embeddings, space, null), new PromptContextAssembler(null)),
                conversation, model, EDT, rejecting, UTC);
        onEdt(new Runnable() {
            @Override
            public void run() {
                binding.sendRequested("Frage", true);
                List<TranscriptEntry> entries = model.getEntries();
                assertEquals(2, entries.size());
                assertEquals(TranscriptEntry.State.FAILED, entries.get(1).getState());
                assertEquals(RagChatBinding.SEND_REJECTED, entries.get(1).getFailureMessage());
                assertTrue("die Shell ist wieder frei", model.canSend("Nochmal"));
                binding.stopRequested(); // nichts mehr abzubrechen, kein Fehler
            }
        });
        assertFalse(chat.isBusy(conversation));
    }

    @Test
    public void failedSemanticPathWithoutKeywordHitsNamesBothInTheNotice() throws Exception {
        // Leerer Index: der Volltext findet nichts, die Embeddings fallen aus – beides muss im Hinweis stehen.
        port.enqueueAnswer("Ohne Kontext.");
        final Fixture fixture = fixture(index, new FailingEmbeddings(embeddings));
        onEdt(new Runnable() {
            @Override
            public void run() {
                fixture.binding.sendRequested("Wie lange ist die Kündigungsfrist?", true);
            }
        });
        awaitIdle(fixture.model);
        List<TranscriptEntry> entries = entries(fixture.model);
        assertEquals(3, entries.size());
        assertFalse(entries.get(1).hasSources());
        assertEquals(TranscriptEntry.Author.NOTICE, entries.get(2).getAuthor());
        assertEquals(RagChatBinding.SEMANTIC_PATH_FAILED_NOTICE + " " + RagChatBinding.NO_SOURCES_NOTICE,
                entries.get(2).getText());
    }

    private void indexAll() {
        new IndexKnowledgeUseCase(index, embeddings, space, new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()))
                .indexSource(source, SourceScope.of("urlaub", "kuendigung"), null);
    }

    private Fixture fixture(KnowledgeIndexPort indexPort, EmbeddingPort embeddingPort) throws Exception {
        final RagChatUseCase rag = new RagChatUseCase(chat,
                new RetrieveKnowledgeUseCase(indexPort, embeddingPort, space, null),
                new PromptContextAssembler(null));
        return onEdt(new Callable<Fixture>() {
            @Override
            public Fixture call() {
                return new Fixture(rag);
            }
        });
    }

    private final class Fixture {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final RagChatBinding binding;

        Fixture(RagChatUseCase rag) {
            binding = new RagChatBinding(rag, conversation, model, EDT, workers, UTC);
        }
    }

    /**
     * Lässt {@code stream()} auf Wunsch erst zurückkehren, wenn der Fake die Antwort abgeschlossen hat: so liegt
     * der Abschluss vor den Quellen auf dem UI-Thread, wie bei einem Backend, das schneller ist als der Umweg.
     */
    private static final class CompletionAwaitingPort implements ChatCompletionPort {
        private final ChatCompletionPort delegate;
        private volatile boolean awaitNext;

        CompletionAwaitingPort(ChatCompletionPort delegate) {
            this.delegate = delegate;
        }

        void awaitCompletionOnNextStream() {
            awaitNext = true;
        }

        @Override
        public ChatResponse complete(ChatRequest request) {
            return delegate.complete(request);
        }

        @Override
        public ChatTask stream(ChatRequest request, final ChatStreamListener listener) {
            if (!awaitNext) {
                return delegate.stream(request, listener);
            }
            awaitNext = false;
            final CountDownLatch finished = new CountDownLatch(1);
            ChatTask task = delegate.stream(request, new ChatStreamListener() {
                @Override
                public void onStart() {
                    listener.onStart();
                }

                @Override
                public void onDelta(String text) {
                    listener.onDelta(text);
                }

                @Override
                public void onComplete(ChatResponse response) {
                    listener.onComplete(response);
                    finished.countDown();
                }

                @Override
                public void onError(ChatCompletionException error) {
                    listener.onError(error);
                    finished.countDown();
                }

                @Override
                public void onCancelled() {
                    listener.onCancelled();
                    finished.countDown();
                }
            });
            try {
                assertTrue("Fake-Antwort abgeschlossen",
                        finished.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return task;
        }
    }

    /** Hält jede Suche an, bis der Test sie freigibt: simuliert ein langsames Retrieval. */
    private static final class GatedIndex implements KnowledgeIndexPort {
        private final KnowledgeIndexPort delegate;
        private final CountDownLatch searching = new CountDownLatch(1);
        private final CountDownLatch gate = new CountDownLatch(1);

        GatedIndex(KnowledgeIndexPort delegate) {
            this.delegate = delegate;
        }

        boolean awaitSearch() throws InterruptedException {
            return searching.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        void release() {
            gate.countDown();
        }

        private void hold() {
            searching.countDown();
            try {
                gate.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void index(Collection<KnowledgeIndexEntry> entries) {
            delegate.index(entries);
        }

        @Override
        public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                            Collection<KnowledgeIndexEntry> entries) {
            delegate.replace(space, resourceId, entries);
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
            hold();
            return delegate.keywordSearch(query);
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            hold();
            return delegate.semanticSearch(query);
        }

        @Override
        public void remove(KnowledgeResourceId resourceId) {
            delegate.remove(resourceId);
        }

        @Override
        public void removeSource(KnowledgeSourceId sourceId) {
            delegate.removeSource(sourceId);
        }

        @Override
        public void rebuild(Collection<KnowledgeIndexEntry> entries) {
            delegate.rebuild(entries);
        }
    }

    /** Beide Suchpfade fallen aus (z. B. beschädigtes Indexverzeichnis). */
    private static final class FailingIndex implements KnowledgeIndexPort {
        @Override
        public void index(Collection<KnowledgeIndexEntry> entries) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void replace(EmbeddingModelIdentity space, KnowledgeResourceId resourceId,
                            Collection<KnowledgeIndexEntry> entries) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public List<KnowledgeSearchHit> keywordSearch(KnowledgeKeywordQuery query) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void remove(KnowledgeResourceId resourceId) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void removeSource(KnowledgeSourceId sourceId) {
            throw new KnowledgeIndexException("index unavailable");
        }

        @Override
        public void rebuild(Collection<KnowledgeIndexEntry> entries) {
            throw new KnowledgeIndexException("index unavailable");
        }
    }

    /** Der Embedding-Dienst ist nicht erreichbar: die semantische Suche fällt aus, die Volltextsuche bleibt. */
    private static final class FailingEmbeddings implements EmbeddingPort {
        private final EmbeddingPort identityOf;

        FailingEmbeddings(EmbeddingPort identityOf) {
            this.identityOf = identityOf;
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return identityOf.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "embedding service down");
        }
    }

    @SuppressWarnings("unused")
    private static void assertNoSources(TranscriptEntry entry) {
        assertNull(entry.hasSources() ? entry.getSources() : null);
    }
}
