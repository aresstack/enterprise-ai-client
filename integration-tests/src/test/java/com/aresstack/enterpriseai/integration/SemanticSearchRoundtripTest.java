package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.pinakes.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingListener;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexingReport;
import com.aresstack.enterpriseai.astu.acropolis.passage.PassageSegmentation;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalObserver;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalPath;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalResult;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalWarning;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.proasteion.application.composition.SemanticPassageSplitter;
import com.aresstack.enterpriseai.proasteion.exedra.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_PHRASE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_TITLE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.QUESTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Semantische Suche Ende zu Ende über HTTP, so wie die Anwendung sie verdrahtet: echter OpenAI-kompatibler
 * Embedding-Adapter gegen einen Fake-{@code /v1/embeddings} im OpenAI-Antwortformat → Passagen wie askai arch
 * ({@code PassageSegmentation}, Satz- und Passage-Embeddings) → Lucene-Index mit Vektoren → Frage → Embedding der
 * Frage (echter Roundtrip) → Vektorsuche → Fusion. Geprüft wird der semantische Trefferpfad selbst, nicht nur das
 * Ausbleiben einer Warnung.
 *
 * <p>Der zweite Fall bildet nach, was der Chat bei jeder Frage auslöst: Die Live-Suche reiht Treffer ein, der
 * Index-Thread bettet deren Sätze und Passagen ein (ein HTTP-Request je Text), und genau dann geht das Embedding
 * der Frage hinaus. Das Gateway des Dienstes nimmt je Schlüssel nur eine Anfrage gleichzeitig an und weist jede
 * weitere mit 429 ab, wie es Unternehmens-Gateways vor einem Embedding-Modell tun.
 */
public class SemanticSearchRoundtripTest {

    private static final int DIMENSION = 64;
    private static final String MODEL = "danielheinz/e5-base-sts-en-de";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeEmbeddingsServer server;
    private OpenAiCompatibleEmbeddingAdapter embeddings;
    private LuceneKnowledgeIndex index;
    private final List<RetrievalWarning> logged = Collections.synchronizedList(new ArrayList<RetrievalWarning>());

    @Before
    public void setUp() throws Exception {
        server = new FakeEmbeddingsServer(DIMENSION);
        embeddings = new OpenAiCompatibleEmbeddingAdapter(
                OpenAiCompatibleEmbeddingConfiguration.builder(server.baseUrl(), MODEL, DIMENSION).build(),
                () -> "roundtrip-token".toCharArray());
        index = new LuceneKnowledgeIndex(temp.newFolder("index").toPath());
    }

    @After
    public void tearDown() {
        if (index != null) {
            index.close();
        }
        if (server != null) {
            server.close();
        }
    }

    private IndexKnowledgeUseCase indexing() {
        return new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()))
                .withPassages(new SemanticPassageSplitter(new PassageSegmentation(embeddings)));
    }

    /** Wie die Composition Root: die Suche meldet Ausfälle an den Beobachter (dort: LoggingRetrievalObserver). */
    private RetrieveKnowledgeUseCase retrieval() {
        return new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null)
                .withObserver(new RetrievalObserver() {
                    @Override
                    public void pathFailed(RetrievalWarning warning) {
                        logged.add(warning);
                    }
                });
    }

    private void assertSemanticHit(RetrievalResult result) {
        assertTrue("keine Warnung, keine Ausfallmeldung: " + result.warnings(), result.warnings().isEmpty());
        assertFalse(result.isDegraded());
        assertFalse(result.isEmpty());
        RetrievedChunk top = result.hits().get(0);
        assertEquals(EXPECTED_TITLE, top.resource().title());
        assertTrue(top.chunk().text(), top.chunk().text().contains(EXPECTED_PHRASE));
        assertTrue("semantischer Trefferpfad", top.foundBy(RetrievalPath.SEMANTIC));
        assertTrue(top.semanticRank().isPresent());
    }

    @Test
    public void queryEmbeddingRoundtripAndSemanticIndexSearchSucceed() {
        IndexingReport report = indexing().indexSource(SampleKnowledge.handbuch(), SampleKnowledge.scope(),
                IndexingListener.none());
        assertTrue(report.toString(), report.isComplete());
        int beforeQuestion = server.requestCount();

        RetrievalResult result = retrieval().retrieve(QUESTION);

        // Echter Roundtrip: genau eine weitere Anfrage an /v1/embeddings, mit der Frage als Eingabe.
        assertEquals(beforeQuestion + 1, server.requestCount());
        List<FakeEmbeddingsServer.Recorded> requests = server.requests();
        assertEquals(Collections.singletonList(QUESTION), requests.get(requests.size() - 1).inputs());
        assertSemanticHit(result);
        assertTrue(logged.toString(), logged.isEmpty());
    }

    @Test
    public void questionDuringBackgroundIndexingStillSearchesSemantically() throws Exception {
        IndexingReport base = indexing().indexSource(SampleKnowledge.handbuch(), SampleKnowledge.scope(),
                IndexingListener.none());
        assertTrue(base.toString(), base.isComplete());
        // Ab jetzt wie das Gateway des Unternehmensdienstes: eine Anfrage gleichzeitig, 25 ms Rechenzeit.
        server.gateway(1, 1, 25L);

        // Zwei Aufbereitungen zugleich, wie Index-Thread der Vorladung und knowledge_read des Agenten (eigener Thread).
        ExecutorService indexThreads = Executors.newFixedThreadPool(2);
        try {
            List<Future<IndexingReport>> background = new ArrayList<Future<IndexingReport>>();
            for (final String name : new String[] {"vorladung", "agent"}) {
                background.add(indexThreads.submit(() -> indexing().indexSource(largeSource(name, 4),
                        SourceScope.of("seite-0", "seite-1", "seite-2", "seite-3"), IndexingListener.none())));
            }
            // Warten, bis wirklich eingebettet wird, dann fragen wie der Chat.
            assertTrue(server.awaitRequests(server.requestCount() + 5, 10, TimeUnit.SECONDS));
            List<RetrievalResult> answers = new ArrayList<RetrievalResult>();
            for (int i = 0; i < 8; i++) {
                answers.add(retrieval().retrieve(QUESTION));
            }
            // Erst die Suche: sie ist der Befund des Nutzers („semantische Suche ist ausgefallen“).
            assertTrue("Suchpfad ausgefallen (LoggingRetrievalObserver): " + logged, logged.isEmpty());
            for (RetrievalResult answer : answers) {
                assertSemanticHit(answer);
            }
            for (Future<IndexingReport> job : background) {
                IndexingReport report = job.get(120, TimeUnit.SECONDS);
                assertTrue("die Aufbereitung scheitert ebenso wenig: " + report.outcomes(), report.isComplete());
            }
        } finally {
            indexThreads.shutdownNow();
        }
        assertEquals("kein selbst verursachtes 429", 0, server.rejectedCount());
    }

    /** Seiten mit langen Absätzen: viele Sätze, also viele Embedding-Anfragen beim Indexieren. */
    private static InMemoryKnowledgeSource largeSource(String sourceId, int pages) {
        InMemoryKnowledgeSource source = new InMemoryKnowledgeSource(sourceId);
        for (int page = 0; page < pages; page++) {
            StringBuilder text = new StringBuilder();
            for (int sentence = 0; sentence < 24; sentence++) {
                text.append("Abschnitt ").append(page).append(" beschreibt Baustein ").append(sentence)
                        .append(" der Plattform mit Schnittstellen und Betriebsdetails. ");
                if (sentence % 6 == 5) {
                    text.append("\n\n");
                }
            }
            source.add("seite-" + page, "Seite " + page, text.toString());
        }
        return source;
    }
}
