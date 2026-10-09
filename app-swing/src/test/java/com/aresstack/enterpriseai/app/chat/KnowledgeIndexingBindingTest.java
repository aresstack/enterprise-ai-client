package com.aresstack.enterpriseai.app.chat;

import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.aresstack.enterpriseai.app.chat.UiTestSupport.EDT;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.await;
import static com.aresstack.enterpriseai.app.chat.UiTestSupport.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** AP22: Indexierungsfortschritt in der Statuszeile, Abbruch über die Statuszeile, Zusammenfassung danach. */
public class KnowledgeIndexingBindingTest {

    private static final long NOW = 1791131400000L; // 2026-10-07T16:30:00Z

    private final ExecutorService workers = UiTestSupport.workers("indexing-binding-test");
    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final EmbeddingModelIdentity space = embeddings.modelIdentity();
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final KnowledgeStatusModel status = new KnowledgeStatusModel();
    private final List<String> texts = new ArrayList<String>();
    private final InMemoryKnowledgeSource source = new InMemoryKnowledgeSource("handbuch")
            .add("urlaub", "Urlaubsregelung", "Mitarbeiter haben dreißig Tage Urlaub im Jahr.")
            .add("kuendigung", "Kündigungsfrist", "Die Kündigungsfrist beträgt drei Monate zum Quartalsende.")
            .add("gleitzeit", "Gleitzeit", "Die Kernarbeitszeit liegt zwischen neun und fünfzehn Uhr.");

    @After
    public void tearDown() {
        workers.shutdownNow();
    }

    @Test
    public void progressAndSummaryReachTheStatusLine() throws Exception {
        final KnowledgeIndexingBinding binding = binding(embeddings);
        final AtomicReference<IndexingReport> done = new AtomicReference<IndexingReport>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertTrue(binding.indexSource(source, SourceScope.of("urlaub", "kuendigung", "gleitzeit"),
                        new Consumer<IndexingReport>() {
                            @Override
                            public void accept(IndexingReport report) {
                                done.set(report);
                            }
                        }));
                assertTrue(binding.isRunning());
                assertFalse("zweiter Lauf wird abgewiesen", binding.indexSource(source, SourceScope.of("urlaub"), null));
                assertEquals("Indexierung von handbuch …", status.getText());
            }
        });
        awaitFinished();

        assertTrue(done.get().isComplete());
        assertEquals(3, done.get().count(IndexingStatus.INDEXED));
        assertTrue(index.size() > 0);
        assertTrue(texts.toString(), texts.contains("Indexierung: 0 von 3 Seiten"));
        assertTrue(texts.toString(), texts.contains("Indexierung: 1 von 3 Seiten · Urlaubsregelung"));
        assertTrue(texts.toString(), texts.contains("Indexierung: 3 von 3 Seiten · Gleitzeit"));
        assertEquals("Wissensbasis: 3 Seiten, " + done.get().chunkCount() + " Abschnitte (Stand 16:30)",
                status.getText());
        assertFalse(onEdt(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return binding.isRunning();
            }
        }));
    }

    @Test
    public void cancelFromTheStatusLineStopsAfterTheCurrentPage() throws Exception {
        final GatedEmbeddings gated = new GatedEmbeddings(embeddings);
        final KnowledgeIndexingBinding binding = binding(gated);
        final AtomicReference<IndexingReport> done = new AtomicReference<IndexingReport>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                binding.indexSource(source, SourceScope.of("urlaub", "kuendigung", "gleitzeit"),
                        new Consumer<IndexingReport>() {
                            @Override
                            public void accept(IndexingReport report) {
                                done.set(report);
                            }
                        });
            }
        });
        assertTrue(gated.awaitFirstCall());
        onEdt(new Runnable() {
            @Override
            public void run() {
                status.requestCancel();
            }
        });
        gated.release();
        awaitFinished();

        assertTrue(done.get().isCancelled());
        assertEquals("die laufende Seite wird noch fertig", 1, done.get().outcomes().size());
        assertEquals("Indexierung abgebrochen: 1 von 3 Seiten indexiert (Stand 16:30)", status.getText());
        assertFalse(status.isCancelRequested());
    }

    @Test
    public void failedPagesAreCountedAndDiscoveryFailureHidesDetails() throws Exception {
        final KnowledgeIndexingBinding binding = binding(new FailingFor("Kernarbeitszeit", embeddings));
        onEdt(new Runnable() {
            @Override
            public void run() {
                binding.indexSource(source, SourceScope.of("urlaub", "kuendigung", "gleitzeit"), null);
            }
        });
        awaitFinished();
        assertTrue(status.getText(), status.getText().startsWith("Wissensbasis: 2 Seiten, "));
        assertTrue(status.getText(), status.getText().endsWith(", 1 fehlgeschlagen (Stand 16:30)"));

        source.failWith("urlaub", KnowledgeSourceException.Kind.UNAVAILABLE);
        onEdt(new Runnable() {
            @Override
            public void run() {
                binding.indexSource(source, SourceScope.of("urlaub"), null);
            }
        });
        awaitFinished();
        assertEquals(KnowledgeIndexingBinding.discoveryFailed("handbuch"), status.getText());
        assertEquals("Indexierung von handbuch fehlgeschlagen: Die Quelle konnte nicht gelesen werden "
                + "(Ursache im Protokoll).", status.getText());
    }

    @Test
    public void rejectedExecutorRestoresTheIdleStateAndPropagates() throws Exception {
        // Regression (Copilot-Finding): nimmt der Arbeits-Executor nichts an, darf kein Lauf als aktiv gelten.
        final Executor rejecting = new Executor() {
            @Override
            public void execute(Runnable command) {
                throw new RejectedExecutionException("shut down");
            }
        };
        final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        final KnowledgeIndexingBinding binding = new KnowledgeIndexingBinding(indexing, status, EDT, rejecting,
                () -> NOW, ZoneOffset.UTC);
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    binding.indexSource(source, SourceScope.of("urlaub"), null);
                    fail("RejectedExecutionException erwartet");
                } catch (RejectedExecutionException expected) {
                    // der Aufrufer erfährt es, die Oberfläche bleibt konsistent
                }
                assertFalse(binding.isRunning());
                assertFalse(status.isRunning());
                assertFalse(status.isCancelRequested());
                assertEquals(KnowledgeIndexingBinding.START_FAILED, status.getText());
            }
        });
    }

    private KnowledgeIndexingBinding binding(EmbeddingPort embeddingPort) throws Exception {
        final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddingPort, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        return onEdt(new Callable<KnowledgeIndexingBinding>() {
            @Override
            public KnowledgeIndexingBinding call() {
                status.addListener(new KnowledgeStatusModel.Listener() {
                    @Override
                    public void statusChanged() {
                        texts.add(status.getText());
                    }
                });
                return new KnowledgeIndexingBinding(indexing, status, EDT, workers, () -> NOW, ZoneOffset.UTC);
            }
        });
    }

    private void awaitFinished() throws Exception {
        await("Indexierung beendet", new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return !status.isRunning() && status.isVisible();
            }
        });
    }

    /** Hält den ersten Embedding-Aufruf an, bis der Test ihn freigibt. */
    private static final class GatedEmbeddings implements EmbeddingPort {
        private final EmbeddingPort delegate;
        private final CountDownLatch called = new CountDownLatch(1);
        private final CountDownLatch gate = new CountDownLatch(1);

        GatedEmbeddings(EmbeddingPort delegate) {
            this.delegate = delegate;
        }

        boolean awaitFirstCall() throws InterruptedException {
            return called.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        void release() {
            gate.countDown();
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return delegate.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            called.countDown();
            try {
                gate.await(UiTestSupport.TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return delegate.embed(texts);
        }
    }

    /** Lässt das Embedding für Texte mit {@code marker} scheitern (eine Seite fällt aus). */
    private static final class FailingFor implements EmbeddingPort {
        private final String marker;
        private final EmbeddingPort delegate;

        FailingFor(String marker, EmbeddingPort delegate) {
            this.marker = marker;
            this.delegate = delegate;
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return delegate.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            for (String text : texts) {
                if (text.contains(marker)) {
                    throw new EmbeddingException(EmbeddingFailureKind.PROVIDER_ERROR, "fake failure");
                }
            }
            return delegate.embed(texts);
        }
    }
}
