package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentReader;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.passage.VocabularyEmbeddingPort;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Abnahme: Analyse über die Passagen der Index-Pipeline (FIFO, ohne Vorrang), unveränderte Fassung wird nicht erneut
 * analysiert, fehlendes Satzmodell oder Embedding-Fehler fallen auf die Originalüberschriften zurück.
 */
public class BuildDocumentNavigationUseCaseTest {

    private static final String TEXT = "## Import\n\nDer Import prüft jeden Beleg.\n\n## Urlaub\n\n"
            + "Der Antrag auf Urlaub geht an die Personalstelle.\n";

    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki").add("A", "Handbuch", TEXT);
    private final List<KnowledgePrefetchService> services = new java.util.ArrayList<KnowledgePrefetchService>();
    private final MemoryStore store = new MemoryStore();

    private BuildDocumentNavigationUseCase useCase(EmbeddingPort embeddings, boolean sentenceModel) {
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(new InMemoryKnowledgeIndex(), embeddings,
                embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        KnowledgeDocumentReader reader = (owner, source, id) -> source.load(id);
        KnowledgePrefetchService prefetch = new KnowledgePrefetchService(indexing, reader,
                () -> KnowledgePrefetchService.Settings.of(5, 1, 10));
        services.add(prefetch);
        return new BuildDocumentNavigationUseCase(prefetch, () -> sentenceModel, AnalysisLabels.KEEP, Runnable::run,
                store);
    }

    @After
    public void tearDown() {
        for (KnowledgePrefetchService prefetch : services) {
            prefetch.shutdown();
        }
    }

    private KnowledgeDocument document() throws Exception {
        return wiki.load(wiki.idOf("A"));
    }

    @Test
    public void unchangedRevisionIsNotAnalyzedAgain() throws Exception {
        BuildDocumentNavigationUseCase navigation = useCase(new VocabularyEmbeddingPort("beleg", "urlaub"), true);
        KnowledgeDocument document = document();

        DocumentNavigation first = navigation.analyze(document).get(10, TimeUnit.SECONDS);
        assertEquals(Arrays.asList("Import", "Urlaub"), Arrays.asList(first.chapters().get(0).label(),
                first.chapters().get(1).label()));
        assertEquals(1, navigation.analysisCount());

        DocumentNavigation again = navigation.analyze(document()).get(10, TimeUnit.SECONDS);
        assertEquals(1, navigation.analysisCount());
        assertEquals(first, again);
        assertEquals("outline liefert die schon analysierte Fassung sofort", first, navigation.outline(document));
        assertEquals("Original unverändert", TEXT, document.text());

        wiki.update("A", TEXT + "\n## Neu\n\nNoch ein Beleg.\n");
        DocumentNavigation changed = navigation.analyze(document()).get(10, TimeUnit.SECONDS);
        assertEquals(2, navigation.analysisCount());
        assertEquals(3, changed.chapters().size());
    }

    @Test
    public void unchangedRevisionIsNotAnalyzedAgainAfterRestart() throws Exception {
        DocumentNavigation first = useCase(new VocabularyEmbeddingPort("beleg", "urlaub"), true).analyze(document())
                .get(10, TimeUnit.SECONDS);
        assertEquals(1, store.saves);

        // Neustart: neuer Use Case, neuer Index, leerer Speicher im Prozess; nur der Navigationsspeicher bleibt.
        BuildDocumentNavigationUseCase restarted = useCase(new VocabularyEmbeddingPort("beleg", "urlaub"), true);
        DocumentNavigation again = restarted.analyze(document()).get(10, TimeUnit.SECONDS);
        assertEquals("keine neue Analyse, kein neuer Benenner-Aufruf", 0, restarted.analysisCount());
        assertEquals(first.toString(), again.toString());
        assertEquals(1, store.saves);
    }

    @Test
    public void fallbacksAreNotPersisted() throws Exception {
        useCase(new VocabularyEmbeddingPort("beleg"), false).analyze(document()).get(10, TimeUnit.SECONDS);
        assertEquals("ohne Satzmodell nichts merken: nach der Installation wird analysiert", 0, store.saves);
    }

    @Test
    public void missingSentenceModelFallsBackToHeadings() throws Exception {
        BuildDocumentNavigationUseCase navigation = useCase(new VocabularyEmbeddingPort("beleg"), false);
        DocumentNavigation result = navigation.analyze(document()).get(10, TimeUnit.SECONDS);
        assertEquals(DocumentNavigation.Analysis.HEADINGS, result.analysis());
        assertEquals(BuildDocumentNavigationUseCase.NO_SENTENCE_MODEL, result.fallbackReason());
        assertEquals(2, result.chapters().size());
        assertEquals(0, navigation.analysisCount());
    }

    @Test
    public void embeddingFailureFallsBackToHeadings() throws Exception {
        final EmbeddingModelIdentity identity = EmbeddingModelIdentity.of("down", 4);
        EmbeddingPort broken = new EmbeddingPort() {
            @Override
            public EmbeddingModelIdentity modelIdentity() {
                return identity;
            }

            @Override
            public EmbeddingBatch embed(List<String> texts) {
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "Dienst nicht erreichbar");
            }
        };
        BuildDocumentNavigationUseCase navigation = useCase(broken, true);
        DocumentNavigation result = navigation.analyze(document()).get(10, TimeUnit.SECONDS);
        assertEquals(DocumentNavigation.Analysis.HEADINGS, result.analysis());
        assertFalse(result.fallbackReason().isEmpty());
        assertEquals(Arrays.asList("Import", "Urlaub"), Arrays.asList(result.chapters().get(0).label(),
                result.chapters().get(1).label()));
    }

    /** Navigationsspeicher, der einen Neustart überlebt (eine Instanz für mehrere Use Cases). */
    private static final class MemoryStore implements DocumentNavigationStore {
        private final java.util.Map<String, DocumentNavigation> byResource =
                new java.util.HashMap<String, DocumentNavigation>();
        int saves;

        @Override
        public synchronized DocumentNavigation load(String resourceId, String captureId) {
            DocumentNavigation stored = byResource.get(resourceId);
            return stored != null && stored.captureId().equals(captureId) ? stored : null;
        }

        @Override
        public synchronized void save(DocumentNavigation navigation) {
            saves++;
            byResource.put(navigation.resourceId(), navigation);
        }
    }
}
