package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Die Startindexierung reiht die Quellen über die AP22-Binding auf; Abbruch stoppt die Kette. */
public class StartupIndexingTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final KnowledgeStatusModel status = new KnowledgeStatusModel();
    private final List<String> statusTexts = new ArrayList<String>();
    private final KnowledgeIndexingBinding binding = new KnowledgeIndexingBinding(indexing, status, DIRECT, DIRECT,
            new java.util.function.LongSupplier() {
                @Override
                public long getAsLong() {
                    return 0L;
                }
            }, ZoneId.of("UTC"));

    private void recordStatus() {
        status.addListener(new KnowledgeStatusModel.Listener() {
            @Override
            public void statusChanged() {
                statusTexts.add(status.getText());
            }
        });
    }

    @Test
    public void indexesAllSourcesInOrderThroughTheStatusLine() throws Exception {
        recordStatus();
        InMemoryKnowledgeSource a = new InMemoryKnowledgeSource("a").add("1", "Eins", "Erster Text.");
        InMemoryKnowledgeSource b = new InMemoryKnowledgeSource("b").add("2", "Zwei", "Zweiter Text.")
                .add("3", "Drei", "Dritter Text.");
        KnowledgeSourceCatalog catalog = new KnowledgeSourceCatalog(Arrays.asList(
                new KnowledgeSourceRegistration(a, SourceScope.of("1")),
                new KnowledgeSourceRegistration(b, SourceScope.of("2", "3"))));
        StartupIndexing run = new StartupIndexing(binding, status, catalog, DIRECT);
        run.start();
        run.start();
        assertTrue(run.awaitTermination(1, TimeUnit.SECONDS));
        assertEquals(2, run.reports().size());
        assertEquals("a", run.reports().get(0).sourceId().value());
        assertEquals("b", run.reports().get(1).sourceId().value());
        assertTrue(statusTexts.toString(), statusTexts.contains("Indexierung von a …"));
        assertTrue(statusTexts.toString(), statusTexts.contains("Indexierung: 2 von 2 Seiten · Drei"));
        assertTrue(statusTexts.toString(), status.getText().startsWith("Wissensbasis: 2 Seiten"));
        assertFalse(status.isRunning());
    }

    @Test
    public void cancelFromTheStatusLineEndsTheChain() throws Exception {
        InMemoryKnowledgeSource a = new InMemoryKnowledgeSource("a")
                .add("1", "Eins", "Erster Text.").add("2", "Zwei", "Zweiter Text.");
        InMemoryKnowledgeSource b = new InMemoryKnowledgeSource("b").add("9", "Neun", "Neunter Text.");
        KnowledgeSourceCatalog catalog = new KnowledgeSourceCatalog(Arrays.asList(
                new KnowledgeSourceRegistration(a, SourceScope.of("1", "2")),
                new KnowledgeSourceRegistration(b, SourceScope.of("9"))));
        status.addListener(new KnowledgeStatusModel.Listener() {
            @Override
            public void statusChanged() {
                if (status.isRunning() && status.getText().startsWith("Indexierung: 1 von")) {
                    status.requestCancel();
                }
            }
        });
        StartupIndexing run = new StartupIndexing(binding, status, catalog, DIRECT);
        run.start();
        assertTrue(run.awaitTermination(1, TimeUnit.SECONDS));
        assertTrue(run.isCancelled());
        assertEquals("nur die erste Quelle hat einen Bericht", 1, run.reports().size());
        assertTrue(run.reports().get(0).isCancelled());
        assertTrue(status.getText(), status.getText().startsWith("Indexierung abgebrochen"));
    }

    @Test
    public void cancelBeforeStartSkipsEverything() throws Exception {
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(
                new InMemoryKnowledgeSource("a").add("1", "Eins", "Text."), SourceScope.of("1")));
        StartupIndexing run = new StartupIndexing(binding, status, catalog, DIRECT);
        assertTrue(run.awaitTermination(1, TimeUnit.MILLISECONDS));
        run.cancel();
        run.start();
        assertTrue(run.awaitTermination(1, TimeUnit.SECONDS));
        assertTrue(run.reports().isEmpty());
    }
}
