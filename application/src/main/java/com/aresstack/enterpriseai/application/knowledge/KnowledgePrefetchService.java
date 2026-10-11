package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Lädt Suchtreffer vorab in einen Speicher-Cache und nimmt jede geladene Seite in den Index (Kopie von MainframeMate
 * {@code ConfluencePrefetchService}, quellenneutral über {@link KnowledgeSourcePort}):
 * <ul>
 *   <li><b>Prefetch-Pool</b>: begrenzter Pool ({@link Settings#concurrency()}, Standard 4) lädt ab der Cursorposition
 *       höchstens {@link Settings#maxItems()} Treffer voraus. Ein neuer {@link #prefetch} desselben Kanals bricht die
 *       laufende Serie ab (neue Suche, Auswahl oder Scrollen weiter unten).</li>
 *   <li><b>Priority-Thread</b>: eigener Thread für Lesen auf Anforderung (Klick im Drawer, {@code knowledge_read}),
 *       wartet nie hinter der Prefetch-Warteschlange.</li>
 *   <li><b>Index-Thread</b>: eigener Single-Thread-Executor für die Indexierung, damit Lucene und Embeddings nicht mit
 *       den HTTP-Abrufen konkurrieren.</li>
 * </ul>
 * Indexiert wird für Vorladen und Lesen über denselben Weg: ist die Revision laut Suche bekannt und so indexiert,
 * passiert nichts; sonst entscheidet der Inhalts-Hash ({@link IndexKnowledgeUseCase#indexDocumentIfChanged}), ob
 * zerlegt und eingebettet wird oder nur die Revision nachgezogen. Vorladen ist reine Optimierung: ohne Vorladen
 * liest {@link #read} dieselbe Seite direkt.
 *
 * <p>Kanäle trennen die Serien der Aufrufer wie die Instanzen je Reiter in MainframeMate (Drawer, Chat, Agent); Pool,
 * Cache, Priority- und Index-Thread sind gemeinsam. Threadsicher.
 */
public final class KnowledgePrefetchService {

    /** Wie lange ein Lesen auf Anforderung höchstens wartet (MainframeMate {@code loadPriority}). */
    static final long PRIORITY_TIMEOUT_SECONDS = 30;
    /** Kleine Pause zwischen zwei Indexierungen, damit Suchen nicht verhungern (MainframeMate). */
    private static final long INDEX_PAUSE_MILLIS = 100;

    /** Wie eine Seite auf Anforderung geladen wird (z. B. {@code documents.load(id)}). */
    public interface DocumentLoad {
        KnowledgeDocument load() throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException;
    }

    private final IndexKnowledgeUseCase indexing;
    private final KnowledgeDocumentReader reader;
    private final Supplier<Settings> settings;
    private final ExecutorService priorityThread;
    private final ExecutorService indexThread;
    private final Map<KnowledgeResourceId, KnowledgeDocument> cache =
            new LinkedHashMap<KnowledgeResourceId, KnowledgeDocument>();
    private final Map<String, Series> running = new HashMap<String, Series>();
    private long cachedBytes;
    private ExecutorService prefetchPool;
    private int poolSize;
    private volatile boolean shutdown;

    /**
     * @param indexing indexiert jede geladene Seite, falls neu oder geändert
     * @param reader   liest einen Treffer aus seiner Quelle (produktiv der vermittelte Zugriff)
     * @param settings Fenster, Worker und Cache-Größe; je Aufruf gelesen (Einstellungen ohne Neustart)
     */
    public KnowledgePrefetchService(IndexKnowledgeUseCase indexing, KnowledgeDocumentReader reader,
                                    Supplier<Settings> settings) {
        if (indexing == null || reader == null || settings == null) {
            throw new IllegalArgumentException("indexing, reader und settings sind Pflicht");
        }
        this.indexing = indexing;
        this.reader = reader;
        this.settings = settings;
        this.priorityThread = Executors.newSingleThreadExecutor(threads("enterprise-ai-prefetch-priority"));
        this.indexThread = Executors.newSingleThreadExecutor(threads("enterprise-ai-prefetch-index"));
    }

    // ------------------------------------------------------------------ Prefetch (Hintergrund, aus Suchtreffern)

    /**
     * Lädt die Treffer ab {@code fromIndex} im Hintergrund vor (höchstens {@link Settings#maxItems()}) und bricht die
     * noch laufende Serie desselben Kanals ab.
     *
     * @param channel   wessen Serie (z. B. "drawer", "chat", "agent")
     * @param ranked    alle Treffer in Rangfolge
     * @param fromIndex Startposition, typischerweise die Cursor- oder Scrollposition
     * @return die neue Serie; ihre Einträge werden fertig, sobald die Seite indexiert (oder unverändert) ist
     */
    public Series prefetch(String channel, List<Candidate> ranked, int fromIndex) {
        if (channel == null || ranked == null) {
            throw new IllegalArgumentException("channel und ranked sind Pflicht");
        }
        Settings current = currentSettings();
        Series series = new Series();
        synchronized (this) {
            Series previous = running.remove(channel);
            if (previous != null) {
                previous.cancel();
            }
            if (shutdown) {
                return series;
            }
            int start = Math.max(0, fromIndex);
            int end = Math.min(ranked.size(), start + current.maxItems());
            ExecutorService pool = pool(current.concurrency());
            for (int i = start; i < end; i++) {
                final Candidate candidate = ranked.get(i);
                if (candidate == null) {
                    continue;
                }
                final CompletableFuture<ResourceIndexingOutcome> indexed =
                        new CompletableFuture<ResourceIndexingOutcome>();
                Future<?> task = pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (Thread.currentThread().isInterrupted()) {
                            indexed.cancel(false);
                            return;
                        }
                        prefetchOne(candidate, indexed);
                    }
                });
                series.add(task, indexed);
            }
            running.put(channel, series);
        }
        return series;
    }

    private void prefetchOne(Candidate candidate, CompletableFuture<ResourceIndexingOutcome> indexed) {
        try {
            KnowledgeDocument cached = cached(candidate.id());
            if (cached != null && !isSameRevision(cached, candidate.revision())) {
                evict(candidate.id());
                cached = null;
            }
            if (indexing.isCurrent(candidate.id(), candidate.revision())) {
                // Revision bekannt und so indexiert: weder laden noch indexieren.
                indexed.complete(ResourceIndexingOutcome.done(candidate.id(), "", IndexingStatus.UNCHANGED, 0));
                return;
            }
            KnowledgeDocument document = cached != null ? cached
                    : remember(reader.read(candidate.source().sourceId(), candidate.source(), candidate.id()));
            indexLater(document, indexed);
        } catch (KnowledgeSourceException | RuntimeException e) {
            indexed.completeExceptionally(e); // nur ein Vorladen; Lesen auf Anforderung versucht es erneut
        }
    }

    /** Der Cache gilt nur bei bekannter, gleicher Revision; ohne Revision wird neu geladen (der Hash spart dann). */
    private static boolean isSameRevision(KnowledgeDocument cached, KnowledgeRevision found) {
        KnowledgeRevision revision = cached.resource().revision();
        return found != null && found.isKnown() && revision.equals(found);
    }

    // ------------------------------------------------------------------ Lesen auf Anforderung (Priority-Thread)

    /**
     * Liest eine Seite sofort auf dem Priority-Thread, an der Prefetch-Warteschlange vorbei; aus dem Cache, wenn
     * schon vorgeladen. Die gelesene Seite wird gecacht und im Hintergrund indexiert (falls neu oder geändert).
     *
     * @throws KnowledgeSourceException {@code UNAVAILABLE} nach {@value #PRIORITY_TIMEOUT_SECONDS} s; sonst der Fehler
     *                                  der Quelle
     */
    public KnowledgeDocument read(KnowledgeResourceId id, final DocumentLoad load)
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        if (id == null || load == null) {
            throw new IllegalArgumentException("id und load sind Pflicht");
        }
        KnowledgeDocument cached = cached(id);
        if (cached != null) {
            return cached;
        }
        Future<KnowledgeDocument> task;
        try {
            task = priorityThread.submit(() -> {
                KnowledgeDocument document = remember(load.load());
                indexLater(document, null);
                return document;
            });
        } catch (RuntimeException rejected) { // beendet: direkt lesen
            KnowledgeDocument document = remember(load.load());
            return document;
        }
        try {
            return task.get(PRIORITY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof KnowledgeSourceException) {
                throw (KnowledgeSourceException) cause;
            }
            if (cause instanceof KnowledgeDocumentNotIndexedException) {
                throw (KnowledgeDocumentNotIndexedException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE,
                    "Laden fehlgeschlagen: " + id, cause);
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE,
                    "Zeitüberschreitung beim Laden: " + id, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE,
                    "Laden unterbrochen: " + id, e);
        }
    }

    // ------------------------------------------------------------------ Cache

    /** Die vorgeladene Seite oder {@code null}. */
    public KnowledgeDocument cached(KnowledgeResourceId id) {
        synchronized (cache) {
            return cache.get(id);
        }
    }

    private KnowledgeDocument remember(KnowledgeDocument document) {
        if (document == null) {
            return null;
        }
        long limit = currentSettings().cacheMaxMb() * 1024L * 1024L;
        long size = bytes(document);
        synchronized (cache) {
            KnowledgeDocument old = cache.remove(document.id());
            if (old != null) {
                cachedBytes -= bytes(old);
            }
            // Älteste zuerst verdrängen (MainframeMate evictOldestVolatile).
            Iterator<Map.Entry<KnowledgeResourceId, KnowledgeDocument>> oldest = cache.entrySet().iterator();
            while (cachedBytes + size > limit && oldest.hasNext()) {
                cachedBytes -= bytes(oldest.next().getValue());
                oldest.remove();
            }
            if (size <= limit) {
                cache.put(document.id(), document);
                cachedBytes += size;
            }
        }
        return document;
    }

    private void evict(KnowledgeResourceId id) {
        synchronized (cache) {
            KnowledgeDocument old = cache.remove(id);
            if (old != null) {
                cachedBytes -= bytes(old);
            }
        }
    }

    private static long bytes(KnowledgeDocument document) {
        return 2L * document.text().length();
    }

    // ------------------------------------------------------------------ Indexierung (eigener Thread)

    private void indexLater(final KnowledgeDocument document,
                            final CompletableFuture<ResourceIndexingOutcome> indexed) {
        if (document == null) {
            if (indexed != null) {
                indexed.complete(null);
            }
            return;
        }
        try {
            indexThread.execute(() -> {
                try {
                    ResourceIndexingOutcome outcome = indexing.indexDocumentIfChanged(document);
                    if (indexed != null) {
                        indexed.complete(outcome);
                    }
                    Thread.sleep(INDEX_PAUSE_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    if (indexed != null) {
                        indexed.completeExceptionally(e); // Lesen gelingt auch ohne Index; die nächste Suche versucht es
                    }
                }
            });
        } catch (RuntimeException rejected) {
            if (indexed != null) {
                indexed.cancel(false);
            }
        }
    }

    // ------------------------------------------------------------------ Lebenszyklus

    /** Beendet alle Threads (Programmende). Idempotent. */
    public void shutdown() {
        synchronized (this) {
            shutdown = true;
            for (Series series : running.values()) {
                series.cancel();
            }
            running.clear();
            if (prefetchPool != null) {
                prefetchPool.shutdownNow();
            }
        }
        priorityThread.shutdownNow();
        indexThread.shutdownNow();
        synchronized (cache) {
            cache.clear();
            cachedBytes = 0;
        }
    }

    private Settings currentSettings() {
        Settings current = settings.get();
        return current == null ? Settings.defaults() : current;
    }

    /** Der Pool mit der eingestellten Größe; bei geänderter Workerzahl ein neuer (laufende Abrufe enden noch). */
    private ExecutorService pool(int size) {
        if (prefetchPool == null || poolSize != size) {
            if (prefetchPool != null) {
                prefetchPool.shutdown();
            }
            prefetchPool = Executors.newFixedThreadPool(size, threads("enterprise-ai-prefetch"));
            poolSize = size;
        }
        return prefetchPool;
    }

    private static ThreadFactory threads(final String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    // ------------------------------------------------------------------ Typen

    /** Ein Treffer zum Vorladen: Quelle, Ressource und die Revision laut Suche (unbekannt erlaubt). */
    public static final class Candidate {

        private final KnowledgeSourcePort source;
        private final KnowledgeResourceId id;
        private final KnowledgeRevision revision;

        public Candidate(KnowledgeSourcePort source, KnowledgeResourceId id, KnowledgeRevision revision) {
            if (source == null || id == null) {
                throw new IllegalArgumentException("source und id sind Pflicht");
            }
            this.source = source;
            this.id = id;
            this.revision = revision == null ? KnowledgeRevision.unknown() : revision;
        }

        public KnowledgeSourcePort source() {
            return source;
        }

        public KnowledgeResourceId id() {
            return id;
        }

        public KnowledgeRevision revision() {
            return revision;
        }
    }

    /** Eine Vorlade-Serie in Rangfolge; abgebrochen, sobald derselbe Kanal neu vorlädt. */
    public static final class Series {

        private final List<Future<?>> tasks = new ArrayList<Future<?>>();
        private final List<CompletableFuture<ResourceIndexingOutcome>> indexed =
                new ArrayList<CompletableFuture<ResourceIndexingOutcome>>();

        synchronized void add(Future<?> task, CompletableFuture<ResourceIndexingOutcome> done) {
            tasks.add(task);
            indexed.add(done);
        }

        synchronized void cancel() {
            for (Future<?> task : tasks) {
                task.cancel(true);
            }
            for (CompletableFuture<ResourceIndexingOutcome> done : indexed) {
                done.cancel(false);
            }
        }

        /** Wie viele Treffer die Serie vorlädt. */
        public synchronized int size() {
            return tasks.size();
        }

        /**
         * Wartet höchstens {@code timeoutMillis}, bis die ersten {@code count} Treffer indexiert (oder unverändert,
         * gescheitert, abgebrochen) sind; z. B. damit der Chat seine Frage danach im Index findet.
         *
         * @return die fertigen Ergebnisse in Rangfolge (ohne gescheiterte)
         */
        public List<ResourceIndexingOutcome> awaitIndexed(int count, long timeoutMillis) {
            List<CompletableFuture<ResourceIndexingOutcome>> first;
            synchronized (this) {
                first = new ArrayList<CompletableFuture<ResourceIndexingOutcome>>(
                        indexed.subList(0, Math.min(Math.max(0, count), indexed.size())));
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
            List<ResourceIndexingOutcome> outcomes = new ArrayList<ResourceIndexingOutcome>();
            for (CompletableFuture<ResourceIndexingOutcome> done : first) {
                long left = deadline - System.nanoTime();
                try {
                    ResourceIndexingOutcome outcome = done.get(Math.max(0, left), TimeUnit.NANOSECONDS);
                    if (outcome != null) {
                        outcomes.add(outcome);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException | CancellationException e) {
                    // gescheitert oder abgebrochen: zählt als fertig
                } catch (TimeoutException e) {
                    break;
                }
            }
            return Collections.unmodifiableList(outcomes);
        }
    }

    /** Fenster, Worker und Cache wie MainframeMate ({@code confluencePrefetchMaxItems/Concurrency/CacheMaxMb}). */
    public static final class Settings {

        public static final int DEFAULT_MAX_ITEMS = 100;
        public static final int MAX_ITEMS_LIMIT = 1000;
        public static final int DEFAULT_CONCURRENCY = 4;
        public static final int MAX_CONCURRENCY = 8;
        public static final int DEFAULT_CACHE_MAX_MB = 50;
        public static final int CACHE_MAX_MB_LIMIT = 500;

        private final int maxItems;
        private final int concurrency;
        private final int cacheMaxMb;

        private Settings(int maxItems, int concurrency, int cacheMaxMb) {
            this.maxItems = maxItems;
            this.concurrency = concurrency;
            this.cacheMaxMb = cacheMaxMb;
        }

        /** Werte werden in ihre Grenzen gelegt (1–1000 Seiten, 1–8 Worker, 1–500 MB). */
        public static Settings of(int maxItems, int concurrency, int cacheMaxMb) {
            return new Settings(clamp(maxItems, 1, MAX_ITEMS_LIMIT), clamp(concurrency, 1, MAX_CONCURRENCY),
                    clamp(cacheMaxMb, 1, CACHE_MAX_MB_LIMIT));
        }

        public static Settings defaults() {
            return of(DEFAULT_MAX_ITEMS, DEFAULT_CONCURRENCY, DEFAULT_CACHE_MAX_MB);
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        /** Höchstens so viele Treffer ab der Cursorposition vorladen. */
        public int maxItems() {
            return maxItems;
        }

        /** Parallele Abrufe im Hintergrund. */
        public int concurrency() {
            return concurrency;
        }

        /** Größe des Speicher-Caches in MB. */
        public int cacheMaxMb() {
            return cacheMaxMb;
        }

        @Override
        public String toString() {
            return "Settings{maxItems=" + maxItems + ", concurrency=" + concurrency + ", cacheMaxMb=" + cacheMaxMb
                    + "}";
        }
    }
}
