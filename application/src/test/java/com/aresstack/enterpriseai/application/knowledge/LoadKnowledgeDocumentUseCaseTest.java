package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LoadKnowledgeDocumentUseCaseTest {

    private InMemoryKnowledgeSource wiki;
    private InMemoryKnowledgeSource docs;
    private KnowledgeSourceCatalog catalog;
    private LoadKnowledgeDocumentUseCase useCase;

    private DeterministicEmbeddingPort embeddings;
    private EmbeddingModelIdentity space;
    private InMemoryKnowledgeIndex index;
    private IndexKnowledgeUseCase indexing;
    /** Der Index führt: nur indexierte Ressourcen sind lesbar. */
    private LoadKnowledgeDocumentUseCase gated;

    @Before
    public void setUp() {
        wiki = new InMemoryKnowledgeSource("wiki").add("Java", "Java installieren", "apt install openjdk-8-jdk");
        docs = new InMemoryKnowledgeSource("docs").add("Urlaub", "Urlaubsantrag", "Im Portal beantragen.");
        catalog = KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(wiki, SourceScope.of("Java")),
                new KnowledgeSourceRegistration(docs, SourceScope.of("Urlaub")));
        useCase = new LoadKnowledgeDocumentUseCase(catalog);

        embeddings = DeterministicEmbeddingPort.withDimension(16);
        space = embeddings.modelIdentity();
        index = new InMemoryKnowledgeIndex();
        indexing = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        gated = new LoadKnowledgeDocumentUseCase(catalog, index, space);
    }

    private static List<String> callsSince(InMemoryKnowledgeSource source, int from) {
        List<String> calls = source.calls();
        return calls.subList(from, calls.size());
    }

    @Test
    public void gatedLoadRefusesAnUnindexedIdWithoutAskingAnySource() {
        assertTrue(gated.isIndexGated());
        assertFalse(useCase.isIndexGated());
        try {
            gated.load(docs.idOf("Urlaub")); // die Quelle kennt die Seite, der Index nicht
            fail("KnowledgeDocumentNotIndexedException erwartet");
        } catch (KnowledgeDocumentNotIndexedException e) {
            assertEquals(docs.idOf("Urlaub"), e.resourceId());
            assertNull(e.sourceId());
        } catch (KnowledgeSourceException e) {
            fail("keine Quelle darf gefragt werden: " + e);
        }
        assertEquals(Collections.emptyList(), wiki.calls());
        assertEquals(Collections.emptyList(), docs.calls());
    }

    @Test
    public void gatedLoadReadsAnIndexedIdDirectlyFromTheSourceItIsIndexedUnder()
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        assertTrue(indexing.indexSource(docs, SourceScope.of("Urlaub"), null).isComplete());
        int wikiCalls = wiki.calls().size();
        int docsCalls = docs.calls().size();

        KnowledgeDocument document = gated.load(docs.idOf("Urlaub"));

        assertEquals("Urlaubsantrag", document.resource().title());
        assertEquals("ohne Abtasten der ersten Quelle", Collections.emptyList(), callsSince(wiki, wikiCalls));
        assertEquals(Collections.singletonList("load:Urlaub"), callsSince(docs, docsCalls));
    }

    @Test
    public void gatedLoadFollowsTheIndexAndTheSourceIsStillAskedForTheText() throws Exception {
        indexing.indexSource(wiki, SourceScope.of("Java"), null);
        assertEquals("apt install openjdk-8-jdk", gated.load(wiki.idOf("Java")).text());

        wiki.update("Java", "sdk install java"); // Index veraltet, Text kommt trotzdem frisch
        assertEquals("sdk install java", gated.load(wiki.idOf("Java")).text());

        index.remove(wiki.idOf("Java"));
        try {
            gated.load(wiki.idOf("Java"));
            fail("KnowledgeDocumentNotIndexedException erwartet");
        } catch (KnowledgeDocumentNotIndexedException expected) {
            assertEquals(wiki.idOf("Java"), expected.resourceId());
        }
    }

    @Test
    public void gatedLoadWithExplicitSourceChecksTheIndexOfThatSource() throws Exception {
        indexing.indexSource(wiki, SourceScope.of("Java"), null);
        int docsCalls = docs.calls().size();

        assertEquals("Java installieren", gated.load(wiki.idOf("Java"), KnowledgeSourceId.of("wiki")).resource().title());
        try {
            gated.load(wiki.idOf("Java"), KnowledgeSourceId.of("docs"));
            fail("KnowledgeDocumentNotIndexedException erwartet");
        } catch (KnowledgeDocumentNotIndexedException e) {
            assertEquals(KnowledgeSourceId.of("docs"), e.sourceId());
        }
        assertEquals("die fremde Quelle wird nicht gefragt", Collections.emptyList(), callsSince(docs, docsCalls));
        try {
            gated.load(wiki.idOf("Java"), KnowledgeSourceId.of("sharepoint"));
            fail("NOT_FOUND erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals("unbekannte Quelle bleibt NOT_FOUND", KnowledgeSourceException.Kind.NOT_FOUND, e.kind());
        }
    }

    @Test
    public void gatedLoadPropagatesSourceFailuresOfIndexedResources() {
        indexing.indexSource(wiki, SourceScope.of("Java"), null);
        wiki.failWith("Java", KnowledgeSourceException.Kind.UNAVAILABLE);
        try {
            gated.load(wiki.idOf("Java"));
            fail("UNAVAILABLE erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNAVAILABLE, e.kind());
        } catch (KnowledgeDocumentNotIndexedException e) {
            fail("indexiert, darf nicht abgewiesen werden");
        }
    }

    @Test
    public void gatedConstructorNeedsIndexAndSpace() {
        try {
            new LoadKnowledgeDocumentUseCase(catalog, null, space);
            fail("IllegalArgumentException erwartet");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            new LoadKnowledgeDocumentUseCase(catalog, index, null);
            fail("IllegalArgumentException erwartet");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void notIndexedExceptionNamesResourceAndOptionalSourceWithoutSecrets() {
        KnowledgeDocumentNotIndexedException plain =
                new KnowledgeDocumentNotIndexedException(wiki.idOf("Java"), null);
        KnowledgeDocumentNotIndexedException scoped =
                new KnowledgeDocumentNotIndexedException(wiki.idOf("Java"), KnowledgeSourceId.of("docs"));

        assertEquals("memory:wiki/Java ist nicht indexiert", plain.getMessage());
        assertEquals("memory:wiki/Java ist in Quelle docs nicht indexiert", scoped.getMessage());
        try {
            new KnowledgeDocumentNotIndexedException(null, null);
            fail("IllegalArgumentException erwartet");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void loadsFromTheSourceThatKnowsTheIdSkippingForeignSources() throws Exception {
        KnowledgeDocument document = useCase.load(docs.idOf("Urlaub"));

        assertEquals("Urlaubsantrag", document.resource().title());
        assertEquals("Im Portal beantragen.", document.text());
        assertEquals("die Wiki-Quelle lehnt die fremde ID ab (UNSUPPORTED) und wird übersprungen",
                Collections.emptyList(), wiki.calls());
        assertEquals(Collections.singletonList("load:Urlaub"), docs.calls());
        assertSame(catalog, useCase.catalog());
    }

    @Test
    public void unknownIdInTheResponsibleSourceIsNotFoundAndStopsTheSearch() throws KnowledgeDocumentNotIndexedException {
        try {
            useCase.load(wiki.idOf("Nope"));
            fail("NOT_FOUND erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.NOT_FOUND, e.kind());
        }
        assertEquals("die zweite Quelle wird nicht mehr gefragt", Collections.emptyList(), docs.calls());
    }

    @Test
    public void idNoSourceKnowsIsUnsupported() throws KnowledgeDocumentNotIndexedException {
        try {
            useCase.load(KnowledgeResourceId.of("confluence:ABC/1"));
            fail("UNSUPPORTED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNSUPPORTED, e.kind());
        }
        assertEquals(Arrays.asList(Collections.emptyList(), Collections.emptyList()),
                Arrays.asList(wiki.calls(), docs.calls()));
    }

    @Test
    public void otherSourceFailuresPropagateUnchanged() throws KnowledgeDocumentNotIndexedException {
        wiki.failWith("Java", KnowledgeSourceException.Kind.ACCESS_DENIED);
        try {
            useCase.load(wiki.idOf("Java"));
            fail("ACCESS_DENIED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.ACCESS_DENIED, e.kind());
        }
    }

    @Test
    public void explicitSourceIsAskedDirectly() throws Exception {
        KnowledgeDocument document = useCase.load(wiki.idOf("Java"), KnowledgeSourceId.of("wiki"));
        assertEquals("Java installieren", document.resource().title());

        try {
            useCase.load(wiki.idOf("Java"), KnowledgeSourceId.of("docs"));
            fail("UNSUPPORTED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNSUPPORTED, e.kind());
        }
        try {
            useCase.load(wiki.idOf("Java"), KnowledgeSourceId.of("sharepoint"));
            fail("NOT_FOUND erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.NOT_FOUND, e.kind());
        }
    }

    @Test
    public void emptyCatalogKnowsNothing() throws KnowledgeDocumentNotIndexedException {
        LoadKnowledgeDocumentUseCase empty = new LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog.empty());
        try {
            empty.load(wiki.idOf("Java"));
            fail("UNSUPPORTED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNSUPPORTED, e.kind());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void catalogIsMandatory() {
        new LoadKnowledgeDocumentUseCase(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resourceIdIsMandatory() throws Exception {
        useCase.load(null);
    }
}
