package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.application.rag.RetrievalResult;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.EmbeddingException;
import com.aresstack.enterpriseai.embedding.api.EmbeddingFailureKind;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IndexKnowledgeUseCaseTest {

    private DeterministicEmbeddingPort embeddings;
    private EmbeddingModelIdentity space;
    private InMemoryKnowledgeIndex index;
    private InMemoryKnowledgeSource wiki;
    private IndexKnowledgeUseCase useCase;

    @Before
    public void setUp() {
        embeddings = DeterministicEmbeddingPort.withDimension(16);
        space = embeddings.modelIdentity();
        index = new InMemoryKnowledgeIndex();
        wiki = new InMemoryKnowledgeSource("wiki")
                .add("Java", "Java installieren", "# Linux\n\nJava installiert man mit apt install openjdk-8-jdk.",
                        "Drucker")
                .add("Drucker", "Drucker einrichten", "Den Drucker richtet man über CUPS ein.")
                .add("Kaffee", "Kaffeemaschine", "Die Kaffeemaschine wird monatlich entkalkt.");
        useCase = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    }

    private List<KnowledgeSearchHit> keyword(String text) {
        return index.keywordSearch(KnowledgeKeywordQuery.of(space, text, 10));
    }

    /** AP11-Abnahmekriterium: die Fake-Quelle läuft über discover und load bis in den Index. */
    @Test
    public void fakeSourceRunsThroughDiscoverLoadChunkEmbedAndIndex() {
        IndexingReport report = useCase.indexSource(wiki, SourceScope.builder().startPoint("Java").maxDepth(1).build(),
                null);

        assertTrue(report.toString(), report.isComplete());
        assertEquals(2, report.discovered());
        assertEquals(2, report.count(IndexingStatus.INDEXED));
        assertEquals(Arrays.asList("discover:[Java]", "load:Java", "load:Drucker"), wiki.calls());

        List<KnowledgeSearchHit> hits = keyword("openjdk");
        assertEquals(1, hits.size());
        assertEquals(wiki.idOf("Java"), hits.get(0).resource().id());
        assertEquals("Java installieren", hits.get(0).resource().title());
        assertEquals(Collections.singletonList("Linux"), hits.get(0).chunk().headingPath());
        assertTrue("nicht entdeckt, nicht indexiert", keyword("entkalkt").isEmpty());

        // Und das Retrieval findet es über beide Pfade im selben Namespace.
        RetrievalResult result = new RetrieveKnowledgeUseCase(index, embeddings, space, null)
                .retrieve("openjdk installieren");
        assertEquals(wiki.idOf("Java"), result.hits().get(0).resource().id());
        assertFalse(result.isDegraded());
    }

    @Test
    public void reindexingReplacesTheChunksOfAChangedResource() {
        SourceScope scope = SourceScope.of("Drucker");
        useCase.indexSource(wiki, scope, null);
        wiki.update("Drucker", "Drucker werden jetzt zentral per IPP verteilt.");

        IndexingReport report = useCase.indexSource(wiki, scope, null);

        assertEquals(1, report.count(IndexingStatus.INDEXED));
        assertTrue(keyword("CUPS").isEmpty());
        assertEquals(1, keyword("IPP").size());
        assertEquals(1, index.size());
    }

    @Test
    public void embedsInBatchesAndKeepsChunkOrder() {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 7; i++) {
            text.append("Satz Nummer ").append(i).append(" erklärt Schritt ").append(i).append(". ");
        }
        InMemoryKnowledgeSource source = new InMemoryKnowledgeSource("docs").add("Lang", "Lang", text.toString());
        IndexKnowledgeUseCase batched = new IndexKnowledgeUseCase(index, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.of(8, 0)), 3);

        IndexingReport report = batched.indexSource(source, SourceScope.of("Lang"), null);

        int chunks = report.chunkCount();
        assertTrue("genug Chunks für mehrere Batches: " + chunks, chunks > 3);
        List<List<String>> calls = embeddings.calls();
        assertEquals((chunks + 2) / 3, calls.size());
        int seen = 0;
        for (List<String> call : calls) {
            assertTrue(call.size() <= 3);
            seen += call.size();
        }
        assertEquals(chunks, seen);
        assertTrue(calls.get(0).get(0).contains("Nummer 1"));
    }

    @Test
    public void resourceMissingAtLoadIsRemovedFromTheIndex() {
        useCase.indexSource(wiki, SourceScope.of("Kaffee"), null);
        assertEquals(1, keyword("entkalkt").size());
        wiki.remove("Kaffee");

        IndexingReport report = useCase.indexResources(wiki, Collections.singletonList(wiki.idOf("Kaffee")), null);

        assertEquals(IndexingStatus.REMOVED, report.outcomes().get(0).status());
        assertTrue(keyword("entkalkt").isEmpty());
    }

    @Test
    public void loadFailureAffectsOnlyItsResourceAndKeepsItsOldChunks() {
        useCase.indexSource(wiki, SourceScope.of("Drucker"), null);
        wiki.update("Drucker", "Drucker werden jetzt zentral per IPP verteilt."); // sonst unverändert → übersprungen
        ScriptedSource flaky = new ScriptedSource(wiki)
                .failLoad(wiki.idOf("Drucker"), KnowledgeSourceException.Kind.UNAVAILABLE);

        IndexingReport report = useCase.indexSource(flaky, SourceScope.of("Java", "Drucker", "Kaffee"), null);

        assertEquals(2, report.count(IndexingStatus.INDEXED));
        ResourceIndexingOutcome failed = report.outcomes().get(1);
        assertEquals(IndexingStatus.FAILED, failed.status());
        assertEquals(IndexingStage.LOADING, failed.stage());
        assertTrue(failed.message().startsWith("UNAVAILABLE"));
        assertFalse(report.isComplete());
        assertEquals("alter Stand bleibt", 1, keyword("CUPS").size());
    }

    @Test
    public void embeddingFailureKeepsThePreviousIndexState() {
        useCase.indexSource(wiki, SourceScope.of("Drucker"), null);
        wiki.update("Drucker", "Neuer Text über IPP.");
        IndexKnowledgeUseCase failing = new IndexKnowledgeUseCase(index, new FailingEmbeddingPort(space), space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));

        IndexingReport report = failing.indexSource(wiki, SourceScope.of("Drucker"), null);

        ResourceIndexingOutcome outcome = report.outcomes().get(0);
        assertEquals(IndexingStage.EMBEDDING, outcome.stage());
        assertTrue(outcome.message().startsWith("RATE_LIMITED"));
        assertEquals(1, keyword("CUPS").size());
        assertTrue(keyword("IPP").isEmpty());
    }

    @Test
    public void discoveryFailureEndsTheRunInTheReport() {
        wiki.failWith("Java", KnowledgeSourceException.Kind.ACCESS_DENIED);

        IndexingReport report = useCase.indexSource(wiki, SourceScope.of("Java"), null);

        assertTrue(report.discoveryFailed());
        assertTrue(report.discoveryFailure().startsWith("ACCESS_DENIED"));
        assertTrue(report.outcomes().isEmpty());
        assertEquals(0, index.size());
    }

    @Test
    public void listenerSeesProgressAndCanCancel() {
        final List<String> events = new ArrayList<String>();
        IndexingReport report = useCase.indexSource(wiki, SourceScope.of("Java", "Drucker", "Kaffee"),
                new IndexingListener() {
                    @Override
                    public void onDiscovered(List<KnowledgeResource> resources) {
                        events.add("discovered:" + resources.size());
                    }

                    @Override
                    public void onResource(ResourceIndexingOutcome outcome) {
                        events.add(outcome.title() + ":" + outcome.status());
                    }

                    @Override
                    public boolean isCancelled() {
                        return events.size() >= 2;
                    }
                });

        assertEquals(Arrays.asList("discovered:3", "Java installieren:INDEXED"), events);
        assertTrue(report.isCancelled());
        assertEquals(1, report.outcomes().size());
        assertTrue(keyword("CUPS").isEmpty());
    }

    @Test
    public void redirectIndexesTheTargetOnceAndDropsTheRequestedId() {
        KnowledgeResourceId alias = KnowledgeResourceId.of("inmemory:wiki/JavaAlias");
        ScriptedSource redirecting = new ScriptedSource(wiki).redirect(alias, wiki.idOf("Java"));

        IndexingReport report = useCase.indexResources(redirecting, Arrays.asList(alias, wiki.idOf("Java")), null);

        assertEquals(IndexingStatus.INDEXED, report.outcomes().get(0).status());
        assertEquals(wiki.idOf("Java"), report.outcomes().get(0).resourceId());
        assertEquals(IndexingStatus.DUPLICATE, report.outcomes().get(1).status());
        assertEquals(1, keyword("openjdk").size());
    }

    @Test
    public void failedRedirectTargetKeepsTheAliasChunksAndIsRetriedByTheNextAlias() {
        KnowledgeResourceId alias = KnowledgeResourceId.of("inmemory:wiki/JavaAlias");
        KnowledgeResourceId other = KnowledgeResourceId.of("inmemory:wiki/JavaOther");
        KnowledgeResource aliasResource = KnowledgeResource.builder(alias, KnowledgeSourceId.of("wiki"))
                .title("Alt").build();
        index.replace(space, alias, Collections.singletonList(
                KnowledgeIndexEntry.of(aliasResource,
                        new KnowledgeChunk(
                                KnowledgeChunkId.of(alias, 0),
                                KnowledgeSourceId.of("wiki"), Collections.<String>emptyList(), "altes Wissen", 2),
                        embeddings.vectorFor("altes Wissen"))));
        ScriptedSource redirecting = new ScriptedSource(wiki).redirect(alias, wiki.idOf("Java"))
                .redirect(other, wiki.idOf("Java"));
        FlakyEmbeddingPort flaky = new FlakyEmbeddingPort(embeddings);
        IndexKnowledgeUseCase flakyUseCase = new IndexKnowledgeUseCase(index, flaky, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));

        IndexingReport report = flakyUseCase.indexResources(redirecting, Arrays.asList(alias, other), null);

        assertEquals(IndexingStatus.FAILED, report.outcomes().get(0).status());
        assertEquals("Ziel noch nicht indexiert, also kein DUPLICATE", IndexingStatus.INDEXED,
                report.outcomes().get(1).status());
        assertEquals("Alias bleibt, weil sein Ziel beim ersten Versuch scheiterte", 1, keyword("altes").size());
        assertEquals(1, keyword("openjdk").size());
    }

    @Test
    public void blankDocumentRemovesOldChunks() {
        useCase.indexSource(wiki, SourceScope.of("Kaffee"), null);
        wiki.update("Kaffee", "   ");

        IndexingReport report = useCase.indexSource(wiki, SourceScope.of("Kaffee"), null);

        assertEquals(IndexingStatus.EMPTY, report.outcomes().get(0).status());
        assertEquals(0, index.size());
    }

    private static final SourceScope ALL = SourceScope.of("Java", "Drucker", "Kaffee");

    @Test
    public void secondRunSkipsUnchangedResourcesWithoutLoadingOrEmbedding() {
        assertTrue(useCase.indexSource(wiki, ALL, null).isComplete());
        int sourceCalls = wiki.calls().size();
        int embeddingCalls = embeddings.calls().size();
        final List<String> events = new ArrayList<String>();

        IndexingReport report = useCase.indexSource(wiki, ALL, new IndexingListener() {
            @Override
            public void onDiscovered(List<KnowledgeResource> resources) {
            }

            @Override
            public void onResource(ResourceIndexingOutcome outcome) {
                events.add(outcome.title() + ":" + outcome.status() + ":" + outcome.chunkCount());
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });

        assertTrue(report.toString(), report.isComplete());
        assertEquals(3, report.discovered());
        assertEquals(3, report.count(IndexingStatus.UNCHANGED));
        assertEquals(0, report.count(IndexingStatus.INDEXED));
        assertEquals(0, report.chunkCount());
        assertEquals(Arrays.asList("Java installieren:UNCHANGED:0", "Drucker einrichten:UNCHANGED:0",
                "Kaffeemaschine:UNCHANGED:0"), events);
        assertEquals("nur die Discovery, kein Laden", Collections.singletonList("discover:[Java, Drucker, Kaffee]"),
                wiki.calls().subList(sourceCalls, wiki.calls().size()));
        assertEquals("kein Embedding", embeddingCalls, embeddings.calls().size());
        assertEquals("die Chunks bleiben", 3, index.size());
        assertEquals(1, keyword("entkalkt").size());
    }

    @Test
    public void onlyTheChangedResourceIsReloaded() {
        useCase.indexSource(wiki, ALL, null);
        wiki.update("Drucker", "Drucker werden jetzt zentral per IPP verteilt.");
        int sourceCalls = wiki.calls().size();

        IndexingReport report = useCase.indexSource(wiki, ALL, null);

        assertEquals(1, report.count(IndexingStatus.INDEXED));
        assertEquals(2, report.count(IndexingStatus.UNCHANGED));
        assertEquals(Arrays.asList("discover:[Java, Drucker, Kaffee]", "load:Drucker"),
                wiki.calls().subList(sourceCalls, wiki.calls().size()));
        assertTrue(keyword("CUPS").isEmpty());
        assertEquals(1, keyword("IPP").size());
    }

    @Test
    public void unknownRevisionOnEitherSideCountsAsChanged() {
        // Discovery ohne Revision: jeder Lauf lädt neu, auch wenn der Index eine kennt.
        RevisionStrippingSource noDiscoveryRevision = new RevisionStrippingSource(wiki, true, false);
        useCase.indexSource(noDiscoveryRevision, SourceScope.of("Java"), null);
        IndexingReport report = useCase.indexSource(noDiscoveryRevision, SourceScope.of("Java"), null);
        assertEquals(1, report.count(IndexingStatus.INDEXED));
        assertEquals(0, report.count(IndexingStatus.UNCHANGED));

        // Index ohne Revision (Quelle liefert sie nur bei der Discovery): ebenfalls neu laden.
        RevisionStrippingSource noLoadRevision = new RevisionStrippingSource(wiki, false, true);
        useCase.indexSource(noLoadRevision, SourceScope.of("Kaffee"), null);
        assertEquals(Optional.of(KnowledgeRevision.unknown()), index.revisionOf(space, wiki.idOf("Kaffee")));
        report = useCase.indexSource(noLoadRevision, SourceScope.of("Kaffee"), null);
        assertEquals(1, report.count(IndexingStatus.INDEXED));
        assertEquals(0, report.count(IndexingStatus.UNCHANGED));
    }

    @Test
    public void vanishedResourcesArePrunedAtTheEndOfIndexSource() {
        useCase.indexSource(wiki, ALL, null);
        wiki.remove("Kaffee");
        final List<String> events = new ArrayList<String>();

        IndexingReport report = useCase.indexSource(wiki, ALL, new IndexingListener() {
            @Override
            public void onDiscovered(List<KnowledgeResource> resources) {
            }

            @Override
            public void onResource(ResourceIndexingOutcome outcome) {
                events.add(outcome.resourceId().value() + ":" + outcome.status());
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        });

        assertTrue(report.toString(), report.isComplete());
        assertEquals(2, report.discovered());
        assertEquals(2, report.count(IndexingStatus.UNCHANGED));
        assertEquals(1, report.count(IndexingStatus.PRUNED));
        assertEquals(3, report.outcomes().size());
        ResourceIndexingOutcome pruned = report.outcomes().get(2);
        assertEquals(wiki.idOf("Kaffee"), pruned.resourceId());
        assertEquals(IndexingStatus.PRUNED, pruned.status());
        assertEquals("memory:wiki/Kaffee:PRUNED", events.get(2));
        assertTrue(keyword("entkalkt").isEmpty());
        assertEquals(2, index.size());
    }

    @Test
    public void pruneSparesFailedResourcesAndRedirectTargets() {
        useCase.indexSource(wiki, ALL, null);
        wiki.remove("Kaffee");
        wiki.update("Drucker", "Drucker werden jetzt zentral per IPP verteilt.");
        KnowledgeResourceId alias = KnowledgeResourceId.of("inmemory:wiki/JavaAlias");
        ScriptedSource scripted = new ScriptedSource(wiki)
                .failLoad(wiki.idOf("Drucker"), KnowledgeSourceException.Kind.UNAVAILABLE)
                .redirect(alias, wiki.idOf("Java"))
                .discoverAlso(KnowledgeResource.builder(alias, KnowledgeSourceId.of("wiki")).title("Alias").build());

        // Scope ohne Java: Java wird nur als Weiterleitungsziel des Alias geladen.
        IndexingReport report = useCase.indexSource(scripted, SourceScope.of("Drucker", "Kaffee"), null);

        assertEquals(1, report.count(IndexingStatus.FAILED));
        assertEquals("Alias → Java neu indexiert", 1, report.count(IndexingStatus.INDEXED));
        assertEquals(1, report.count(IndexingStatus.PRUNED));
        assertEquals("gescheiterte Ressource behält ihren alten Stand", 1, keyword("CUPS").size());
        assertEquals("Weiterleitungsziel bleibt", 1, keyword("openjdk").size());
        assertTrue("nur die verschwundene Seite ist weg", keyword("entkalkt").isEmpty());
    }

    @Test
    public void cancelledRunDoesNotPrune() {
        useCase.indexSource(wiki, ALL, null);
        wiki.remove("Kaffee");

        IndexingReport report = useCase.indexSource(wiki, ALL, new IndexingListener() {
            private int seen;

            @Override
            public void onDiscovered(List<KnowledgeResource> resources) {
            }

            @Override
            public void onResource(ResourceIndexingOutcome outcome) {
                seen++;
            }

            @Override
            public boolean isCancelled() {
                return seen >= 1;
            }
        });

        assertTrue(report.isCancelled());
        assertEquals(0, report.count(IndexingStatus.PRUNED));
        assertEquals("ohne vollständigen Lauf bleibt alles stehen", 1, keyword("entkalkt").size());
    }

    @Test
    public void emptyButSuccessfulDiscoveryPrunesTheWholeSource() {
        useCase.indexSource(wiki, ALL, null);

        IndexingReport report = useCase.indexSource(new ScriptedSource(wiki).emptyDiscovery(), ALL, null);

        assertTrue(report.isComplete());
        assertEquals(0, report.discovered());
        assertEquals(3, report.count(IndexingStatus.PRUNED));
        assertEquals(0, index.size());
    }

    @Test
    public void indexResourcesNeitherSkipsNorPrunes() {
        useCase.indexSource(wiki, ALL, null);
        wiki.remove("Kaffee");

        IndexingReport report = useCase.indexResources(wiki, Collections.singletonList(wiki.idOf("Java")), null);

        assertEquals(IndexingStatus.INDEXED, report.outcomes().get(0).status());
        assertEquals(1, report.outcomes().size());
        assertEquals("verschwundene Seite bleibt, bis indexSource läuft", 1, keyword("entkalkt").size());
    }

    @Test
    public void failedPruneQueryIsReportedAndRemovesNothing() {
        useCase.indexSource(wiki, ALL, null);
        wiki.remove("Kaffee");
        ResourceIdsFailingIndex failing = new ResourceIdsFailingIndex(index);
        IndexKnowledgeUseCase onFailingIndex = new IndexKnowledgeUseCase(failing, embeddings, space,
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));

        IndexingReport report = onFailingIndex.indexSource(wiki, ALL, null);

        assertTrue(report.pruneFailed());
        assertEquals("Indexverzeichnis nicht lesbar: /var/lib/eai/index", report.pruneFailure());
        assertFalse(report.isComplete());
        assertEquals(0, report.count(IndexingStatus.FAILED));
        assertEquals(2, report.count(IndexingStatus.UNCHANGED));
        assertEquals(0, report.count(IndexingStatus.PRUNED));
        assertEquals(1, keyword("entkalkt").size());
        assertTrue(report.toString(), report.toString().contains("pruneFailure="));
    }

    @Test
    public void removeSourceDropsAllItsChunks() {
        useCase.indexSource(wiki, SourceScope.of("Java", "Kaffee"), null);

        useCase.removeSource(KnowledgeSourceId.of("wiki"));

        assertEquals(0, index.size());
    }

    @Test
    public void rejectsAnEmbeddingPortOfAnotherWorld() {
        try {
            new IndexKnowledgeUseCase(index, DeterministicEmbeddingPort.withDimension(8), space,
                    new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
            fail("IllegalArgumentException erwartet");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("konfiguriert"));
        }
    }

    /** Delegiert an eine Quelle, kann aber einzelne Ladevorgänge scheitern lassen oder umleiten. */
    private static final class ScriptedSource implements KnowledgeSourcePort {

        private final KnowledgeSourcePort delegate;
        private final Map<KnowledgeResourceId, KnowledgeSourceException.Kind> failures =
                new HashMap<KnowledgeResourceId, KnowledgeSourceException.Kind>();
        private final Map<KnowledgeResourceId, KnowledgeResourceId> redirects =
                new HashMap<KnowledgeResourceId, KnowledgeResourceId>();
        private final List<KnowledgeResource> extraDiscoveries = new ArrayList<KnowledgeResource>();
        private boolean emptyDiscovery;

        ScriptedSource(KnowledgeSourcePort delegate) {
            this.delegate = delegate;
        }

        /** Die Discovery liefert zusätzlich diese Ressource (z. B. einen Alias, der auf eine Seite umleitet). */
        ScriptedSource discoverAlso(KnowledgeResource resource) {
            extraDiscoveries.add(resource);
            return this;
        }

        /** Die Discovery gelingt, findet aber nichts. */
        ScriptedSource emptyDiscovery() {
            emptyDiscovery = true;
            return this;
        }

        ScriptedSource failLoad(KnowledgeResourceId id, KnowledgeSourceException.Kind kind) {
            failures.put(id, kind);
            return this;
        }

        ScriptedSource redirect(KnowledgeResourceId from, KnowledgeResourceId to) {
            redirects.put(from, to);
            return this;
        }

        @Override
        public KnowledgeSourceId sourceId() {
            return delegate.sourceId();
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            if (emptyDiscovery) {
                return Collections.emptyList();
            }
            List<KnowledgeResource> result = new ArrayList<KnowledgeResource>(delegate.discover(scope));
            result.addAll(extraDiscoveries);
            return result;
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            KnowledgeSourceException.Kind kind = failures.get(resourceId);
            if (kind != null) {
                throw new KnowledgeSourceException(kind, "Quelle antwortet nicht für " + resourceId);
            }
            KnowledgeResourceId target = redirects.get(resourceId);
            return delegate.load(target == null ? resourceId : target);
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return delegate.discoverLinks(resourceId);
        }
    }

    /** Liefert Ressourcen ohne Revision, wahlweise bei der Discovery oder beim Laden. */
    private static final class RevisionStrippingSource implements KnowledgeSourcePort {

        private final KnowledgeSourcePort delegate;
        private final boolean stripDiscovery;
        private final boolean stripLoad;

        RevisionStrippingSource(KnowledgeSourcePort delegate, boolean stripDiscovery, boolean stripLoad) {
            this.delegate = delegate;
            this.stripDiscovery = stripDiscovery;
            this.stripLoad = stripLoad;
        }

        private static KnowledgeResource strip(KnowledgeResource resource) {
            return resource.toBuilder().revision(KnowledgeRevision.unknown()).build();
        }

        @Override
        public KnowledgeSourceId sourceId() {
            return delegate.sourceId();
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
            List<KnowledgeResource> resources = delegate.discover(scope);
            if (!stripDiscovery) {
                return resources;
            }
            List<KnowledgeResource> stripped = new ArrayList<KnowledgeResource>(resources.size());
            for (KnowledgeResource resource : resources) {
                stripped.add(strip(resource));
            }
            return stripped;
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            KnowledgeDocument document = delegate.load(resourceId);
            return stripLoad ? KnowledgeDocument.of(strip(document.resource()), document.text()) : document;
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            return delegate.discoverLinks(resourceId);
        }
    }

    /** Delegiert an einen Index, dessen Abfrage der indexierten Ressourcen scheitert (z. B. Lucene-Verzeichnis). */
    private static final class ResourceIdsFailingIndex implements KnowledgeIndexPort {

        private final KnowledgeIndexPort delegate;

        ResourceIdsFailingIndex(KnowledgeIndexPort delegate) {
            this.delegate = delegate;
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
            return delegate.keywordSearch(query);
        }

        @Override
        public List<KnowledgeSearchHit> semanticSearch(KnowledgeSemanticQuery query) {
            return delegate.semanticSearch(query);
        }

        @Override
        public Set<KnowledgeResourceId> resourceIds(EmbeddingModelIdentity space, KnowledgeSourceId sourceId) {
            throw new KnowledgeIndexException("Indexverzeichnis nicht lesbar: /var/lib/eai/index");
        }

        @Override
        public Optional<KnowledgeRevision> revisionOf(EmbeddingModelIdentity space, KnowledgeResourceId resourceId) {
            return delegate.revisionOf(space, resourceId);
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

    /** Scheitert beim ersten Aufruf, danach delegiert er. */
    private static final class FlakyEmbeddingPort implements EmbeddingPort {

        private final EmbeddingPort delegate;
        private boolean failed;

        FlakyEmbeddingPort(EmbeddingPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return delegate.modelIdentity();
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            if (!failed) {
                failed = true;
                throw new EmbeddingException(EmbeddingFailureKind.UNAVAILABLE, "kurz weg");
            }
            return delegate.embed(texts);
        }
    }

    private static final class FailingEmbeddingPort implements EmbeddingPort {

        private final EmbeddingModelIdentity identity;

        FailingEmbeddingPort(EmbeddingModelIdentity identity) {
            this.identity = identity;
        }

        @Override
        public EmbeddingModelIdentity modelIdentity() {
            return identity;
        }

        @Override
        public EmbeddingBatch embed(List<String> texts) {
            throw new EmbeddingException(EmbeddingFailureKind.RATE_LIMITED, "zu viele Anfragen");
        }
    }
}
