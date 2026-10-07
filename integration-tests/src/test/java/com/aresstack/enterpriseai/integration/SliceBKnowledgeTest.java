package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.app.chat.fakeapi.FakeEmbeddingsServer;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.IndexingListener;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.rag.RetrievalPath;
import com.aresstack.enterpriseai.application.rag.RetrievalResult;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.application.rag.RetrievedChunk;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingAdapter;
import com.aresstack.enterpriseai.embedding.openai.OpenAiCompatibleEmbeddingConfiguration;
import com.aresstack.enterpriseai.knowledge.lucene.LuceneKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_PHRASE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.EXPECTED_TITLE;
import static com.aresstack.enterpriseai.integration.SampleKnowledge.QUESTION;
import static com.aresstack.enterpriseai.integration.SliceSupport.assertNoSecret;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Slice B – Knowledge: Fake-Quelle ({@code InMemoryKnowledgeSource}) → Chunking ({@code KnowledgeChunker}) →
 * Embedding über den echten OpenAI-kompatiblen Adapter gegen einen Fake-{@code /embeddings} → Lucene-Index
 * (Volltext und Vektoren auf Platte) → hybrides Retrieval mit Rank-Fusion. Dazu das Verhalten aus AP20
 * ("Index führt": Unverändertes überspringen, Verschwundenes entfernen) und der Teilausfall des Embedding-Dienstes.
 */
public class SliceBKnowledgeTest {

    private static final int DIMENSION = 16;
    private static final String TOKEN = "slice-b-embedding-token-77c1";
    private static final String MODEL = "danielheinz/e5-base-sts-en-de";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private FakeEmbeddingsServer embeddingsServer;
    private OpenAiCompatibleEmbeddingAdapter embeddings;
    private EmbeddingModelIdentity space;
    private Path indexDirectory;
    private LuceneKnowledgeIndex index;
    private InMemoryKnowledgeSource source;

    @Before
    public void setUp() throws Exception {
        embeddingsServer = new FakeEmbeddingsServer(DIMENSION);
        embeddings = new OpenAiCompatibleEmbeddingAdapter(
                OpenAiCompatibleEmbeddingConfiguration.builder(embeddingsServer.baseUrl(), MODEL, DIMENSION).build(),
                () -> TOKEN.toCharArray());
        space = embeddings.modelIdentity();
        indexDirectory = temp.newFolder("index").toPath();
        index = new LuceneKnowledgeIndex(indexDirectory);
        source = SampleKnowledge.handbuch();
    }

    @After
    public void tearDown() {
        if (index != null) {
            index.close();
        }
        if (embeddingsServer != null) {
            embeddingsServer.close();
        }
    }

    private IndexKnowledgeUseCase indexingFor(LuceneKnowledgeIndex target) {
        return new IndexKnowledgeUseCase(target, embeddings, space, new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    }

    private RetrieveKnowledgeUseCase retrievalFor(LuceneKnowledgeIndex target) {
        return new RetrieveKnowledgeUseCase(target, embeddings, space, null);
    }

    private IndexingReport indexAll() {
        return indexingFor(index).indexSource(source, SampleKnowledge.scope(), IndexingListener.none());
    }

    @Test
    public void fakeSourceIsChunkedEmbeddedIndexedAndRetrievedOverBothPaths() throws Exception {
        IndexingReport report = indexAll();
        assertTrue(report.toString(), report.isComplete());
        assertEquals(3, report.discovered());
        assertEquals(3, report.count(IndexingStatus.INDEXED));
        assertTrue(report.toString(), report.chunkCount() >= 3);

        // Jeder Chunk ging als eigene Anfrage mit Bearer-Token an /embeddings (Default: ein Text je Request).
        assertTrue(embeddingsServer.requestCount() >= report.chunkCount());
        boolean sawTheAnswerText = false;
        for (FakeEmbeddingsServer.Recorded request : embeddingsServer.requests()) {
            assertEquals("Bearer " + TOKEN, request.authorization());
            assertEquals(1, request.inputs().size());
            sawTheAnswerText |= request.inputs().get(0).contains(EXPECTED_PHRASE);
        }
        assertTrue("der Chunk mit der Antwort wurde vektorisiert", sawTheAnswerText);
        int afterIndexing = embeddingsServer.requestCount();

        RetrievalResult result = retrievalFor(index).retrieve(QUESTION);
        assertFalse(result.isEmpty());
        assertFalse(result.isDegraded());
        RetrievedChunk top = result.hits().get(0);
        assertEquals(EXPECTED_TITLE, top.resource().title());
        assertTrue(top.chunk().text(), top.chunk().text().contains(EXPECTED_PHRASE));
        assertTrue("Volltextpfad", top.foundBy(RetrievalPath.KEYWORD));
        assertTrue("Semantikpfad", top.foundBy(RetrievalPath.SEMANTIC));
        assertTrue(top.keywordRank().isPresent());
        assertTrue(top.semanticRank().isPresent());
        assertEquals("die Frage wurde genau einmal eingebettet", afterIndexing + 1, embeddingsServer.requestCount());
    }

    @Test
    public void indexSurvivesReopeningFromDisk() throws Exception {
        indexAll();
        index.close();

        index = new LuceneKnowledgeIndex(indexDirectory);
        RetrievalResult result = retrievalFor(index).retrieve(QUESTION);
        assertFalse(result.isEmpty());
        assertEquals(EXPECTED_TITLE, result.hits().get(0).resource().title());
    }

    @Test
    public void embeddingOutageDegradesToKeywordOnlyRetrieval() throws Exception {
        indexAll();
        embeddingsServer.failWith(503, "{\"error\":\"maintenance, token " + TOKEN + "\"}");

        RetrievalResult result = retrievalFor(index).retrieve(QUESTION);
        assertTrue(result.toString(), result.isDegraded());
        assertEquals(1, result.warnings().size());
        assertEquals(RetrievalPath.SEMANTIC, result.warnings().get(0).path());
        assertFalse("der Volltextpfad trägt weiter", result.isEmpty());
        RetrievedChunk top = result.hits().get(0);
        assertEquals(EXPECTED_TITLE, top.resource().title());
        assertTrue(top.keywordRank().isPresent());
        assertFalse(top.semanticRank().isPresent());
        assertNoSecret(result.toString(), TOKEN);
        assertNoSecret(result.warnings().get(0).message(), TOKEN);

        embeddingsServer.recover();
        assertFalse(retrievalFor(index).retrieve(QUESTION).isDegraded());
    }

    @Test
    public void secondRunSkipsUnchangedPagesAndPrunesVanishedOnes() throws Exception {
        indexAll();
        int afterFirstRun = embeddingsServer.requestCount();

        IndexingReport again = indexAll();
        assertEquals(3, again.count(IndexingStatus.UNCHANGED));
        assertEquals(0, again.count(IndexingStatus.INDEXED));
        assertEquals("Unverändertes wird nicht neu vektorisiert", afterFirstRun, embeddingsServer.requestCount());

        source.remove("gleitzeit");
        IndexingReport pruned = indexAll();
        assertEquals(2, pruned.count(IndexingStatus.UNCHANGED));
        assertEquals(1, pruned.count(IndexingStatus.PRUNED));
        // Der Semantikpfad liefert immer die nächsten Nachbarn; entscheidend ist, dass die Seite weg ist.
        for (RetrievedChunk hit : retrievalFor(index).retrieve("Kernarbeitszeit Gleitzeit").hits()) {
            assertFalse(hit.resource().title(), "Gleitzeit".equals(hit.resource().title()));
        }
        assertEquals(EXPECTED_TITLE, retrievalFor(index).retrieve(QUESTION).hits().get(0).resource().title());
    }
}
