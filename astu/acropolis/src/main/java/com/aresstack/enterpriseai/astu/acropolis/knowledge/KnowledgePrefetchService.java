package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
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
 * Lädt Suchtreffer vorab in einen Speicher-Cache und bereitet sie danach für den Index auf (Kopie von MainframeMate
 * {@code ConfluencePrefetchService}, quellenneutral über {@link KnowledgeSourcePort}):
 * <ul>
 *   <li><b>Prefetch-Pool</b>: begrenzter Pool ({@link Settings#concurrency()}, Standard 4) lädt ab der Cursorposition
 *       höchstens {@link Settings#maxItems()} Treffer voraus, nur in den Rohcache. Ein neuer {@link #prefetch}
 *       desselben Kanals bricht die laufende Serie ab (neue Suche, Auswahl oder Scrollen weiter unten).</li>
 *   <li><b>Priority-Thread</b>: eigener Thread nur für die Auswahl des Benutzers ({@link #openSelected}, MainframeMate
 *       {@code loadPriority}); wartet nie hinter der Prefetch-Warteschlange und nie hinter einem Agenten.</li>
 *   <li><b>Agent</b>: {@code knowledge_read} liest über {@link #read}/{@link #refresh} auf seinem eigenen Thread, wie
 *       in MainframeMate, wo der Priority-Thread nur der Oberfläche gehört.</li>
 *   <li><b>Index-Thread</b>: ein serieller Verarbeitungs-Thread (askai {@code KnowledgeProcessingRunner}) für
 *       Zerlegen, Embeddings und Index. Sofort verarbeitet er nur, worauf jemand wartet (Auswahl des Benutzers,
 *       {@link #indexNow}, {@link Series#awaitIndexed}); alles nur Vorgeladene erst bei Leerlauf, also wenn kein Abruf
 *       läuft und seit {@value #IDLE_QUIET_MILLIS} ms keiner endete.</li>
 * </ul>
 * Aufbereitet wird für Vorladen und Lesen über denselben Weg: ist die Revision laut Suche bekannt und so indexiert,
 * passiert nichts; sonst entscheidet der Inhalts-Hash ({@link IndexKnowledgeUseCase#indexDocumentIfChanged}), ob
 * zerlegt und eingebettet wird oder nur die Revision nachgezogen. Ein Cache-Eintrag gilt nur, solange die Suche keine
 * andere Revision gemeldet hat ({@link #seenRevision}).
 *
 * <p>Kanäle trennen die Serien der Aufrufer wie die Instanzen je Reiter in MainframeMate (Drawer, Chat, Agent); Pool,
 * Cache, Priority- und Index-Thread sind gemeinsam. Threadsicher.
 */
public final class KnowledgePrefetchService {

    /** Wie lange ein Lesen auf Anforderung höchstens wartet (MainframeMate {@code loadPriority}). */
    static final long PRIORITY_TIMEOUT_SECONDS = 30;
    /** Kleine Pause nach jeder Leerlauf-Aufbereitung, damit Suchen nicht verhungern (MainframeMate). */
    private static final long INDEX_PAUSE_MILLIS = 100;
    /** Leerlauf: kein Abruf läuft und seit so vielen Millisekunden endete keiner. */
    public static final long IDLE_QUIET_MILLIS = 2000;
    private static final int SEEN_LIMIT = 10000;

    /** Wie eine Seite auf Anforderung geladen wird (z. B. {@code documents.load(id)}). */
    public interface DocumentLoad {
        KnowledgeDocument load() throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException;
    }

    private final IndexKnowledgeUseCase indexing;
    private final KnowledgeDocumentReader reader;
    private final Supplier<Settings> settings;
    private final ExecutorService priorityThread;
    private final Thread indexThread;
    private final long idleQuietMillis;
    /** Wartende Aufbereitungen in Ankunftsreihenfolge; geschützt durch sich selbst. */
    private final java.util.LinkedList<IndexJob> indexQueue = new java.util.LinkedList<IndexJob>();
    private final java.util.concurrent.atomic.AtomicInteger activeLoads =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile long lastLoadEndNanos = System.nanoTime();
    private final Map<KnowledgeResourceId, KnowledgeDocument> cache =
            new LinkedHashMap<KnowledgeResourceId, KnowledgeDocument>();
    private final Map<String, Series> running = new HashMap<String, Series>();
    /** Zuletzt in einem Suchtreffer gesehene Revision je Ressource (für den Resource-Resolver), begrenzt. */
    private final Map<KnowledgeResourceId, KnowledgeRevision> seen =
            new java.util.LinkedHashMap<KnowledgeResourceId, KnowledgeRevision>() {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean removeEldestEntry(Map.Entry<KnowledgeResourceId, KnowledgeRevision> eldest) {
                    return size() > SEEN_LIMIT;
                }
            };
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
        this(indexing, reader, settings, IDLE_QUIET_MILLIS);
    }

    /** Wie oben mit eigener Leerlauf-Schwelle (Tests). */
    KnowledgePrefetchService(IndexKnowledgeUseCase indexing, KnowledgeDocumentReader reader,
                             Supplier<Settings> settings, long idleQuietMillis) {
        if (indexing == null || reader == null || settings == null) {
            throw new IllegalArgumentException("indexing, reader und settings sind Pflicht");
        }
        this.indexing = indexing;
        this.reader = reader;
        this.settings = settings;
        this.idleQuietMillis = Math.max(0, idleQuietMillis);
        this.priorityThread = Executors.newSingleThreadExecutor(threads("enterprise-ai-prefetch-priority"));
        this.indexThread = threads("enterprise-ai-prefetch-index").newThread(this::indexLoop);
        this.indexThread.start();
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
            synchronized (seen) {
                for (Candidate candidate : ranked) {
                    if (candidate != null && candidate.revision() != null && candidate.revision().isKnown()) {
                        seen.put(candidate.id(), candidate.revision());
                    }
                }
            }
            int start = Math.max(0, fromIndex);
            int end = Math.min(ranked.size(), start + current.maxItems());
            ExecutorService pool = pool(current.concurrency());
            for (int i = start; i < end; i++) {
                final Candidate candidate = ranked.get(i);
                if (candidate == null) {
                    continue;
                }
                final Pending pending = new Pending();
                Future<?> task = pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (Thread.currentThread().isInterrupted()) {
                            pending.indexed.cancel(false);
                            return;
                        }
                        prefetchOne(candidate, pending);
                    }
                });
                series.add(task, pending);
            }
            series.wake = this::wakeIndex;
            running.put(channel, series);
        }
        lastLoadEndNanos = System.nanoTime(); // eine neue Suche ist Aktivität: der Leerlauf beginnt neu
        return series;
    }

    private void prefetchOne(Candidate candidate, Pending pending) {
        CompletableFuture<ResourceIndexingOutcome> indexed = pending.indexed;
        try {
            if (indexing.isCurrent(candidate.id(), candidate.revision())) {
                // Revision bekannt und so indexiert: weder laden noch aufbereiten.
                indexed.complete(ResourceIndexingOutcome.done(candidate.id(), "", IndexingStatus.UNCHANGED, 0));
                return;
            }
            // Die Kopie gilt nur für genau die gemeldete Revision; ohne Revision weiß nur die Quelle, ob sich etwas
            // geändert hat (der Hash spart dann das Aufbereiten).
            KnowledgeDocument document = candidate.revision().isKnown()
                    ? cachedCurrent(candidate.id(), candidate.revision()) : null;
            if (document == null) {
                document = remember(loadCurrent(candidate.source(), candidate.id(), candidate.revision()));
            }
            // Nur Rohcache; zerlegt und eingebettet wird bei Leerlauf oder sobald jemand darauf wartet.
            enqueueIndex(document, indexed, pending.urgent);
        } catch (KnowledgeSourceException | RuntimeException e) {
            indexed.completeExceptionally(e); // nur ein Vorladen; Lesen auf Anforderung versucht es erneut
        }
    }

    /**
     * Lädt die Seite in dem Stand, den die Suche meldete: Bronze-Kopie oder Quelle; ist die Kopie älter als der
     * Suchtreffer oder die Revision unbekannt, gezielt neu (REFRESH_EXTERNAL).
     */
    private KnowledgeDocument loadCurrent(KnowledgeSourcePort source, KnowledgeResourceId id, KnowledgeRevision seen)
            throws KnowledgeSourceException {
        activeLoads.incrementAndGet();
        try {
            if (seen != null && seen.isKnown()) {
                KnowledgeDocument document = reader.read(source.sourceId(), source, id);
                return isSameRevision(document, seen) ? document : reader.refresh(source.sourceId(), source, id);
            }
            // Suche ohne Revision: nur die Quelle weiß, ob sich etwas geändert hat; der Hash spart das Aufbereiten
            return reader.refresh(source.sourceId(), source, id);
        } finally {
            loadEnded();
        }
    }

    private void loadEnded() {
        activeLoads.decrementAndGet();
        lastLoadEndNanos = System.nanoTime();
        wakeIndex();
    }

    /**
     * Der Cache-Eintrag, wenn er zu der zuletzt gemeldeten Revision passt; sonst wird er verworfen und {@code null}
     * geliefert. Ohne gemeldete Revision gilt der Eintrag.
     */
    private KnowledgeDocument cachedCurrent(KnowledgeResourceId id, KnowledgeRevision found) {
        KnowledgeDocument cached = cached(id);
        if (cached == null) {
            return null;
        }
        KnowledgeRevision seen = found != null && found.isKnown() ? found : seenRevision(id);
        if (seen != null && seen.isKnown() && !cached.resource().revision().equals(seen)) {
            evict(id); // die Suche kennt einen neueren Stand: nie die alte Fassung ausliefern
            return null;
        }
        return cached;
    }

    /** Der Cache gilt nur bei bekannter, gleicher Revision; ohne Revision wird neu geladen (der Hash spart dann). */
    private static boolean isSameRevision(KnowledgeDocument cached, KnowledgeRevision found) {
        KnowledgeRevision revision = cached.resource().revision();
        return found != null && found.isKnown() && revision.equals(found);
    }

    // ------------------------------------------------------------------ Auswahl des Benutzers (Priority-Thread)

    /**
     * Liest die Seite, die der Benutzer gerade ausgewählt hat, sofort auf dem Priority-Thread (MainframeMate
     * {@code loadPriority}), an der Prefetch-Warteschlange und am Agenten vorbei; aus dem Cache, wenn schon vorgeladen
     * und nicht älter als die zuletzt gemeldete Revision. Die Seite wird sofort aufbereitet (nicht erst bei Leerlauf).
     *
     * @throws KnowledgeSourceException {@code UNAVAILABLE} nach {@value #PRIORITY_TIMEOUT_SECONDS} s; sonst der Fehler
     *                                  der Quelle
     */
    public KnowledgeDocument openSelected(final KnowledgeSourcePort source, final KnowledgeResourceId id)
            throws KnowledgeSourceException {
        if (source == null || id == null) {
            throw new IllegalArgumentException("source und id sind Pflicht");
        }
        KnowledgeDocument cached = cachedCurrent(id, null);
        if (cached != null) {
            enqueueIndex(cached, null, URGENT);
            return cached;
        }
        Future<KnowledgeDocument> task;
        try {
            task = priorityThread.submit(() -> {
                KnowledgeDocument document = remember(loadCurrent(source, id, seenRevision(id)));
                enqueueIndex(document, null, URGENT);
                return document;
            });
        } catch (RuntimeException rejected) { // beendet: direkt lesen
            return remember(loadCurrent(source, id, seenRevision(id)));
        }
        try {
            return task.get(PRIORITY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof KnowledgeSourceException) {
                throw (KnowledgeSourceException) cause;
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

    // ------------------------------------------------------------------ Lesen des Agenten (eigener Thread)

    /**
     * Liest für den Agenten ({@code knowledge_read}) auf dessen eigenem Thread: aus dem Cache, wenn vorgeladen und
     * nicht älter als die zuletzt gemeldete Revision, sonst über {@code load}. Bereitet nicht auf; dafür ruft der
     * Aufrufer {@link #indexNow}.
     */
    public KnowledgeDocument read(KnowledgeResourceId id, DocumentLoad load)
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        if (id == null || load == null) {
            throw new IllegalArgumentException("id und load sind Pflicht");
        }
        KnowledgeDocument cached = cachedCurrent(id, null);
        if (cached != null) {
            return cached;
        }
        activeLoads.incrementAndGet();
        try {
            return remember(load.load());
        } finally {
            loadEnded();
        }
    }

    /** Der Index-Pfad, über den jedes Laden indexiert. */
    public IndexKnowledgeUseCase indexing() {
        return indexing;
    }

    /**
     * Die Revision, die die Quelle zuletzt in einem Suchtreffer meldete; {@code null}, wenn seit dem Start keine Suche
     * die Ressource lieferte. Neuer als der Indexstand heißt: neu holen.
     */
    public KnowledgeRevision seenRevision(KnowledgeResourceId id) {
        synchronized (seen) {
            return seen.get(id);
        }
    }

    /**
     * Indexiert ein geladenes Dokument auf dem Index-Thread (derselbe Weg wie beim Vorladen: Revision, dann Hash,
     * sonst zerlegen, embedden, ersetzen) und wartet höchstens {@code timeoutMillis} darauf.
     *
     * @return das Ergebnis, oder {@code null}, wenn es nicht rechtzeitig fertig wurde oder scheiterte
     */
    public ResourceIndexingOutcome indexNow(KnowledgeDocument document, long timeoutMillis) {
        CompletableFuture<ResourceIndexingOutcome> done = new CompletableFuture<ResourceIndexingOutcome>();
        enqueueIndex(document, done, URGENT);
        try {
            return done.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException | java.util.concurrent.CancellationException e) {
            return null;
        }
    }

    /**
     * Wie {@link #read}, aber am Speicher-Cache vorbei: für Ressourcen, deren Stand unklar oder geändert ist
     * ({@code load} holt dann typischerweise mit {@code REFRESH_EXTERNAL} neu).
     */
    public KnowledgeDocument refresh(KnowledgeResourceId id, DocumentLoad load)
            throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException {
        if (id == null) {
            throw new IllegalArgumentException("id ist Pflicht");
        }
        evict(id);
        return read(id, load);
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

    // ------------------------------------------------------------------ Aufbereitung (eigener Thread)

    private static final java.util.concurrent.atomic.AtomicBoolean URGENT =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    private void enqueueIndex(KnowledgeDocument document, CompletableFuture<ResourceIndexingOutcome> indexed,
                              java.util.concurrent.atomic.AtomicBoolean urgent) {
        if (document == null) {
            if (indexed != null) {
                indexed.complete(null);
            }
            return;
        }
        synchronized (indexQueue) {
            if (shutdown) {
                if (indexed != null) {
                    indexed.cancel(false);
                }
                return;
            }
            indexQueue.add(new IndexJob(document, indexed, urgent));
            indexQueue.notifyAll();
        }
    }

    private void wakeIndex() {
        synchronized (indexQueue) {
            indexQueue.notifyAll();
        }
    }

    /** {@code true}, wenn kein Abruf läuft und seit {@link #idleQuietMillis} keiner endete. */
    boolean isIdle() {
        return activeLoads.get() == 0 && idleLeftMillis() == 0;
    }

    private long idleLeftMillis() {
        long quiet = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastLoadEndNanos);
        return Math.max(0, idleQuietMillis - quiet);
    }

    /** Der serielle Verarbeitungs-Thread (askai {@code KnowledgeProcessingRunner}): wartet, statt zu pollen. */
    private void indexLoop() {
        while (!shutdown) {
            IndexJob job;
            synchronized (indexQueue) {
                job = nextJob();
                if (job == null) {
                    try {
                        long left = activeLoads.get() == 0 ? idleLeftMillis() : 0;
                        indexQueue.wait(indexQueue.isEmpty() || left == 0 ? 1000 : left);
                    } catch (InterruptedException e) {
                        return;
                    }
                    continue;
                }
            }
            try {
                ResourceIndexingOutcome outcome = indexing.indexDocumentIfChanged(job.document);
                if (job.indexed != null) {
                    job.indexed.complete(outcome);
                }
            } catch (RuntimeException e) {
                if (job.indexed != null) {
                    // Lesen gelingt auch ohne Index; die nächste Suche versucht es erneut
                    job.indexed.completeExceptionally(e);
                }
            }
            if (!job.urgent.get()) {
                try {
                    Thread.sleep(INDEX_PAUSE_MILLIS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /** Zuerst, worauf jemand wartet; Vorgeladenes nur bei Leerlauf. Unter {@code indexQueue}. */
    private IndexJob nextJob() {
        for (Iterator<IndexJob> it = indexQueue.iterator(); it.hasNext(); ) {
            IndexJob job = it.next();
            if (job.urgent.get()) {
                it.remove();
                return job;
            }
        }
        return !indexQueue.isEmpty() && isIdle() ? indexQueue.removeFirst() : null;
    }

    /** Wie viele Seiten auf ihre Aufbereitung warten (Tests, Diagnose). */
    int pendingIndexJobs() {
        synchronized (indexQueue) {
            return indexQueue.size();
        }
    }

    private static final class IndexJob {
        final KnowledgeDocument document;
        final CompletableFuture<ResourceIndexingOutcome> indexed;
        final java.util.concurrent.atomic.AtomicBoolean urgent;

        IndexJob(KnowledgeDocument document, CompletableFuture<ResourceIndexingOutcome> indexed,
                 java.util.concurrent.atomic.AtomicBoolean urgent) {
            this.document = document;
            this.indexed = indexed;
            this.urgent = urgent;
        }
    }

    /** Ein vorgeladener Treffer: wird dringend, sobald jemand auf ihn wartet. */
    static final class Pending {
        final CompletableFuture<ResourceIndexingOutcome> indexed = new CompletableFuture<ResourceIndexingOutcome>();
        final java.util.concurrent.atomic.AtomicBoolean urgent = new java.util.concurrent.atomic.AtomicBoolean();
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
        synchronized (indexQueue) {
            for (IndexJob job : indexQueue) {
                if (job.indexed != null) {
                    job.indexed.cancel(false);
                }
            }
            indexQueue.clear();
            indexQueue.notifyAll();
        }
        indexThread.interrupt();
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
        private final List<Pending> pending = new ArrayList<Pending>();
        volatile Runnable wake;

        synchronized void add(Future<?> task, Pending entry) {
            tasks.add(task);
            pending.add(entry);
        }

        synchronized void cancel() {
            for (Future<?> task : tasks) {
                task.cancel(true);
            }
            for (Pending entry : pending) {
                entry.indexed.cancel(false);
            }
        }

        /** Wie viele Treffer die Serie vorlädt. */
        public synchronized int size() {
            return tasks.size();
        }

        /**
         * Macht die ersten {@code count} Treffer dringend (aufbereitet wird sofort, nicht erst bei Leerlauf) und wartet
         * höchstens {@code timeoutMillis}, bis sie indexiert (oder unverändert,
         * gescheitert, abgebrochen) sind; z. B. damit der Chat seine Frage danach im Index findet.
         *
         * @return die fertigen Ergebnisse in Rangfolge (ohne gescheiterte)
         */
        public List<ResourceIndexingOutcome> awaitIndexed(int count, long timeoutMillis) {
            List<CompletableFuture<ResourceIndexingOutcome>> first =
                    new ArrayList<CompletableFuture<ResourceIndexingOutcome>>();
            synchronized (this) {
                for (Pending entry : pending.subList(0, Math.min(Math.max(0, count), pending.size()))) {
                    entry.urgent.set(true); // jemand wartet: nicht erst bei Leerlauf aufbereiten
                    first.add(entry.indexed);
                }
            }
            Runnable signal = wake;
            if (signal != null) {
                signal.run();
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
