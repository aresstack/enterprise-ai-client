package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Vorladen wie MainframeMate: Abbruch, Fenster ab Cursor, Priority-Lesen, veraltete Revision, Leerlauf. */
public class KnowledgePrefetchServiceTest {

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("A", "A", "Seite A über Drucker.")
            .add("B", "B", "Seite B über Kaffee.")
            .add("C", "C", "Seite C über Urlaub.")
            .add("D", "D", "Seite D über Server.")
            .add("E", "E", "Seite E über Netze.");
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final List<String> loaded = new CopyOnWriteArrayList<String>();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile String blocked = "";
    private KnowledgePrefetchService prefetch;

    /** Liest direkt aus der Quelle, protokolliert jeden Abruf und hält die Seite {@link #blocked} an. */
    private final KnowledgeDocumentReader reader = new KnowledgeDocumentReader() {
        @Override
        public KnowledgeDocument read(SourceAddress owner, KnowledgeSourcePort source, KnowledgeResourceId id)
                throws KnowledgeSourceException {
            loaded.add(id.value());
            if (id.value().equals(blocked)) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return source.load(id);
        }
    };

    private KnowledgePrefetchService service(int maxItems, int concurrency, long idleMillis) {
        final KnowledgePrefetchService.Settings settings = KnowledgePrefetchService.Settings.of(maxItems, concurrency,
                10);
        prefetch = new KnowledgePrefetchService(indexing, reader, () -> settings, idleMillis);
        return prefetch;
    }

    @After
    public void tearDown() {
        release.countDown();
        if (prefetch != null) {
            prefetch.shutdown();
        }
    }

    private List<KnowledgePrefetchService.Candidate> candidates(String... keys) throws KnowledgeSourceException {
        List<KnowledgePrefetchService.Candidate> list = new ArrayList<KnowledgePrefetchService.Candidate>();
        for (String key : keys) {
            KnowledgeResourceId id = wiki.idOf(key);
            list.add(new KnowledgePrefetchService.Candidate(wiki, id, wiki.load(id).resource().revision()));
        }
        return list;
    }

    @Test
    public void loadsOnlyTheWindowFromTheCursor() throws Exception {
        service(2, 1, 0);
        KnowledgePrefetchService.Series series = prefetch.prefetch("drawer", candidates("A", "B", "C", "D", "E"), 2);
        assertEquals(2, series.size());
        series.awaitIndexed(2, 10_000L);
        Collections.sort(loaded);
        assertEquals(java.util.Arrays.asList(wiki.idOf("C").value(), wiki.idOf("D").value()), loaded);
    }

    @Test
    public void newPrefetchOfTheSameChannelCancelsTheOldSeries() throws Exception {
        service(5, 1, 0);
        blocked = wiki.idOf("A").value();
        prefetch.prefetch("drawer", candidates("A", "B", "C"), 0);
        waitUntil(() -> loaded.contains(blocked));
        KnowledgePrefetchService.Series fresh = prefetch.prefetch("drawer", candidates("D"), 0);
        release.countDown();
        fresh.awaitIndexed(1, 10_000L);
        assertFalse("abgebrochene Serie lädt nicht weiter", loaded.contains(wiki.idOf("B").value()));
        assertFalse(loaded.contains(wiki.idOf("C").value()));
        assertTrue(loaded.contains(wiki.idOf("D").value()));
    }

    @Test
    public void selectionIsLoadedOnThePriorityThreadNotBehindThePrefetchPool() throws Exception {
        service(5, 1, 0);
        blocked = wiki.idOf("A").value();
        prefetch.prefetch("drawer", candidates("A", "B"), 0);
        waitUntil(() -> loaded.contains(blocked));
        long start = System.nanoTime();
        KnowledgeDocument selected = prefetch.openSelected(wiki, wiki.idOf("E"));
        assertTrue("Auswahl wartet nicht auf den belegten Pool",
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 5_000L);
        assertTrue(selected.text().contains("Netze"));
    }

    @Test
    public void staleCachedRevisionIsNeverDeliveredAfterTheSearchSawANewOne() throws Exception {
        service(5, 1, 0);
        KnowledgeResourceId a = wiki.idOf("A");
        assertTrue(prefetch.openSelected(wiki, a).text().contains("Drucker"));
        wiki.update("A", "Seite A über Drucker, jetzt mit IPP.");
        // Die Suche meldet die neue Revision; das Vorladefenster erreicht den Treffer nicht (Cursor hinter der Liste).
        List<KnowledgePrefetchService.Candidate> found = candidates("A");
        prefetch.prefetch("drawer", found, found.size());
        assertEquals(found.get(0).revision(), prefetch.seenRevision(a));
        assertTrue("Klick bekommt die neue Fassung, nicht den Cache",
                prefetch.openSelected(wiki, a).text().contains("IPP"));
        assertTrue("auch der Agent", prefetch.read(a, () -> wiki.load(a)).text().contains("IPP"));
    }

    @Test
    public void prefetchedPagesAreProcessedOnIdleUnlessSomeoneWaits() throws Exception {
        service(5, 2, 60_000L);
        KnowledgePrefetchService.Series series = prefetch.prefetch("drawer", candidates("A", "B"), 0);
        waitUntil(() -> loaded.size() == 2 && prefetch.pendingIndexJobs() == 2);
        Thread.sleep(300);
        assertEquals("ohne Leerlauf nur Rohcache", 2, prefetch.pendingIndexJobs());
        assertTrue(index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).isEmpty());

        List<ResourceIndexingOutcome> first = series.awaitIndexed(1, 10_000L);
        assertEquals("wer wartet, bekommt sofort", 1, first.size());
        assertTrue(index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).contains(wiki.idOf("A")));
        assertFalse(index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).contains(wiki.idOf("B")));
    }

    @Test
    public void idleProcessingStartsOnceNoLoadIsRunning() throws Exception {
        service(5, 2, 200L);
        prefetch.prefetch("drawer", candidates("A"), 0);
        waitUntil(() -> index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).contains(wiki.idOf("A")));
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (!condition.getAsBoolean()) {
            assertTrue("Bedingung nicht erreicht", System.currentTimeMillis() < deadline);
            Thread.sleep(20);
        }
    }
}
