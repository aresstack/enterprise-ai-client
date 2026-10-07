package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RefreshKnowledgeSourceUseCaseTest {

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final EmbeddingModelIdentity space = embeddings.modelIdentity();
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private InMemoryKnowledgeSource wiki;
    private InMemoryKnowledgeSource docs;
    private KnowledgeSourceCatalog catalog;
    private RefreshKnowledgeSourceUseCase useCase;

    @Before
    public void setUp() {
        wiki = new InMemoryKnowledgeSource("wiki")
                .add("Java", "Java installieren", "apt install openjdk-8-jdk")
                .add("Drucker", "Drucker einrichten", "Den Drucker richtet man über CUPS ein.");
        docs = new InMemoryKnowledgeSource("docs").add("Urlaub", "Urlaubsantrag", "Im Portal beantragen.");
        // Der Scope der Wiki-Quelle umfasst absichtlich nur eine der beiden Seiten.
        catalog = KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(wiki, SourceScope.of("Java")),
                new KnowledgeSourceRegistration(docs, SourceScope.of("Urlaub")));
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        useCase = new RefreshKnowledgeSourceUseCase(indexing, catalog);
    }

    @Test
    public void refreshesTheConfiguredSourceInItsConfiguredScope() throws KnowledgeSourceException {
        IndexingReport report = useCase.refresh(KnowledgeSourceId.of("wiki"), null);

        assertTrue(report.toString(), report.isComplete());
        assertEquals(KnowledgeSourceId.of("wiki"), report.sourceId());
        assertEquals(1, report.discovered());
        assertEquals(1, report.count(IndexingStatus.INDEXED));
        assertEquals("nur der konfigurierte Scope, nicht die ganze Quelle",
                0, index.keywordSearch(KnowledgeKeywordQuery.of(space, "CUPS", 5)).size());
        assertEquals(1, index.keywordSearch(KnowledgeKeywordQuery.of(space, "openjdk", 5)).size());
        assertEquals("die andere Quelle bleibt unberührt", Collections.emptyList(), docs.calls());
        assertEquals(Arrays.asList("discover:[Java]", "load:Java"), wiki.calls());
        assertSame(catalog, useCase.catalog());
    }

    @Test
    public void unknownSourceIsNotFound() {
        try {
            useCase.refresh(KnowledgeSourceId.of("sharepoint"), null);
            fail("NOT_FOUND erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.NOT_FOUND, e.kind());
            assertTrue(e.getMessage(), e.getMessage().contains("sharepoint"));
        }
        assertEquals(Collections.emptyList(), wiki.calls());
        assertEquals(Collections.emptyList(), docs.calls());
    }

    @Test
    public void listenerCanCancelTheRun() throws KnowledgeSourceException {
        IndexingReport report = useCase.refresh(KnowledgeSourceId.of("wiki"), new IndexingListener() {
            @Override
            public boolean isCancelled() {
                return true;
            }
        });

        assertTrue(report.isCancelled());
        assertEquals(0, report.outcomes().size());
    }

    @Test
    public void missingArgumentsAreRejected() {
        try {
            new RefreshKnowledgeSourceUseCase(null, catalog);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("indexing"));
        }
        try {
            useCase.refresh(null, null);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("sourceId"));
        } catch (KnowledgeSourceException e) {
            fail(e.toString());
        }
    }
}
