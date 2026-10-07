package com.aresstack.enterpriseai.application.rag;

import com.aresstack.enterpriseai.application.rag.RagTestData.ObservedIndex;
import com.aresstack.enterpriseai.application.rag.RagTestData.ScriptedEmbeddingPort;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.OTHER_SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RetrieveKnowledgeUseCaseTest {

    private static final KnowledgeResource JAVA = resource("wiki:Java", "wiki");
    private static final KnowledgeResource PRINTER = resource("wiki:Drucker", "wiki");
    private static final KnowledgeResource COFFEE = resource("confluence:42", "confluence");

    private ObservedIndex index;
    private ScriptedEmbeddingPort embeddings;

    @Before
    public void fillIndex() {
        index = new ObservedIndex(new InMemoryKnowledgeIndex());
        index.index(Arrays.asList(
                entry(JAVA, 0, "Java Installation unter Linux", vector(SPACE_3D, 1, 0, 0)),
                entry(PRINTER, 0, "Drucker einrichten", vector(SPACE_3D, 0, 1, 0)),
                entry(COFFEE, 0, "Kaffeemaschine entkalken", vector(SPACE_3D, 0, 0, 1)),
                // Gleiche Ressource in einer anderen Embedding-Welt: darf nie auftauchen.
                entry(PRINTER, 1, "Linux Drucker in fremder Welt", vector(OTHER_SPACE_3D, 0, 1, 0))));
        embeddings = new ScriptedEmbeddingPort(SPACE_3D)
                .on("Linux", 0.1f, 0.9f, 0f)
                .on("Kaffeemaschine", 0f, 0f, 1f);
    }

    private RetrieveKnowledgeUseCase useCase(RetrievalSettings settings) {
        return new RetrieveKnowledgeUseCase(index, embeddings, SPACE_3D, settings);
    }

    private static List<String> resourceIds(RetrievalResult result) {
        List<String> ids = new ArrayList<String>();
        for (RetrievedChunk hit : result.hits()) {
            ids.add(hit.resource().id().value());
        }
        return ids;
    }

    @Test
    public void fusesKeywordAndSemanticHits() {
        RetrievalResult result = useCase(null).retrieve("Linux");

        // Java: Keyword-Rang 1 und Semantik-Rang 2; Drucker nur Semantik-Rang 1; Kaffee Semantik-Rang 3.
        assertEquals(Arrays.asList("wiki:Java", "wiki:Drucker", "confluence:42"), resourceIds(result));
        RetrievedChunk java = result.hits().get(0);
        assertTrue(java.foundBy(RetrievalPath.KEYWORD));
        assertTrue(java.foundBy(RetrievalPath.SEMANTIC));
        assertEquals(1.0 / 61 + 1.0 / 62, java.fusedScore(), 1e-12);
        assertFalse(result.isDegraded());
        assertEquals(Collections.singletonList(Collections.singletonList("Linux")), embeddings.calls());
    }

    @Test
    public void hitsCarrySourceMetadata() {
        RetrievedChunk hit = useCase(null).retrieve("Kaffeemaschine").hits().get(0);

        assertEquals("confluence:42", hit.resource().id().value());
        assertEquals("Titel confluence:42", hit.resource().title());
        assertEquals(0, hit.chunk().ordinal());
        assertEquals(1.0, hit.semanticScore().getAsDouble(), 1e-6);
    }

    @Test
    public void neverSearchesAnotherEmbeddingWorld() {
        for (RetrievedChunk hit : useCase(null).retrieve("Linux").hits()) {
            assertFalse("fremde Welt: " + hit, hit.chunk().ordinal() == 1);
        }
    }

    @Test
    public void sourceFilterRestrictsBothPaths() {
        RetrievalResult result = useCase(null).retrieve("Kaffeemaschine",
                Collections.singleton(KnowledgeSourceId.of("wiki")));

        assertFalse(resourceIds(result).contains("confluence:42"));
    }

    @Test
    public void limitsTheFusedResult() {
        RetrievalResult result = useCase(RetrievalSettings.builder().maxResults(1).build()).retrieve("Linux");

        assertEquals(Collections.singletonList("wiki:Java"), resourceIds(result));
    }

    @Test
    public void blankQueryCallsNoPort() {
        assertTrue(useCase(null).retrieve("  ").isEmpty());
        assertTrue(useCase(null).retrieve(null).isEmpty());
        assertEquals(0, index.keywordSearches + index.semanticSearches);
        assertTrue(embeddings.calls().isEmpty());
    }

    @Test
    public void keywordOnlyNeedsNoEmbeddingPort() {
        RetrieveKnowledgeUseCase keywordOnly = new RetrieveKnowledgeUseCase(index, null, SPACE_3D,
                RetrievalSettings.builder().semanticEnabled(false).build());

        assertEquals(Collections.singletonList("wiki:Java"), resourceIds(keywordOnly.retrieve("Linux")));
        assertEquals(0, index.semanticSearches);
    }

    @Test
    public void semanticOnlyDoesNoKeywordSearch() {
        RetrievalResult result = useCase(RetrievalSettings.builder().keywordEnabled(false).build()).retrieve("Linux");

        assertEquals("wiki:Drucker", resourceIds(result).get(0));
        assertEquals(0, index.keywordSearches);
    }

    @Test
    public void minSemanticScoreDropsWeakSemanticHits() {
        RetrievalResult result = useCase(RetrievalSettings.builder().minSemanticScore(0.5).build()).retrieve("Linux");

        // Kaffee (Cosine 0) fällt weg; Java (0.11) zählt nur noch über Keyword, gleichauf mit Drucker -> Chunk-ID.
        assertEquals(Arrays.asList("wiki:Drucker", "wiki:Java"), resourceIds(result));
        assertFalse("Java nur noch über Keyword", result.hits().get(1).foundBy(RetrievalPath.SEMANTIC));
    }

    @Test
    public void embeddingFailureDegradesToKeywordWithWarning() {
        embeddings.failWith(new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Endpunkt antwortet nicht"));

        RetrievalResult result = useCase(null).retrieve("Linux");

        assertEquals(Collections.singletonList("wiki:Java"), resourceIds(result));
        assertTrue(result.isDegraded());
        assertEquals(RetrievalPath.SEMANTIC, result.warnings().get(0).path());
        assertTrue(result.warnings().get(0).message().contains("UNAVAILABLE"));
        assertEquals(0, index.semanticSearches);
    }

    @Test
    public void keywordIndexFailureDegradesToSemanticWithWarning() {
        index.failKeyword = true;

        RetrievalResult result = useCase(null).retrieve("Linux");

        assertEquals("wiki:Drucker", resourceIds(result).get(0));
        assertEquals(RetrievalPath.KEYWORD, result.warnings().get(0).path());
    }

    @Test
    public void failureOfAllActivePathsThrows() {
        index.failKeyword = true;
        index.failSemantic = true;
        try {
            useCase(null).retrieve("Linux");
            fail("KnowledgeRetrievalException erwartet");
        } catch (KnowledgeRetrievalException e) {
            assertEquals(2, e.warnings().size());
        }
    }

    @Test
    public void rejectsAnEmbeddingPortOfAnotherWorld() {
        try {
            new RetrieveKnowledgeUseCase(index, new ScriptedEmbeddingPort(OTHER_SPACE_3D), SPACE_3D, null);
            fail("IllegalArgumentException erwartet");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("konfiguriert"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void semanticSearchNeedsAnEmbeddingPort() {
        new RetrieveKnowledgeUseCase(index, null, SPACE_3D, RetrievalSettings.defaults());
    }
}
