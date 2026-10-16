package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeDocument;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.RawKnowledgeIndexPort;
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
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Lädt Suchtreffer vorab, macht sie sofort lexikalisch durchsuchbar und bereitet sie im Hintergrund semantisch auf
 * (Kopie von MainframeMate {@code ConfluencePrefetchService}, quellenneutral über {@link KnowledgeSourcePort}). Zwei
 * Ebenen mit getrennten Pools:
 * <ul>
 *   <li><b>Ebene A (schnell, für den Chat)</b>: Der <b>Lade-Pool</b> ({@link Settings#concurrency()}) lädt ab der
 *       Cursorposition höchstens {@link Settings#maxItems()} Treffer; Serien, auf die jemand wartet (Chat-Recherche),
 *       laufen vor dem übrigen Vorladen. Jede geladene Seite schreibt der <b>Rohindex-Pool</b>
 *       ({@link Settings#rawWorkers()}) sofort in den {@link RawKnowledgeIndexPort}: Titel, URL, Revision, Volltext,
 *       Inhalts-Hash, Snippet. Danach ist sie über BM25 und Textfenster findbar ({@link Series#awaitAvailable}),
 *       ohne Passagen und ohne Embeddings.</li>
 *   <li><b>Ebene B (Hintergrund)</b>: Die <b>Aufbereitungs-Worker</b> ({@link Settings#enrichmentWorkers()}) zerlegen,
 *       betten ein und ersetzen die Passagen je Ressource ({@link IndexKnowledgeUseCase#indexDocumentIfChanged}),
 *       mehrere Dokumente gleichzeitig, mit niedrigerer Thread-Priorität. Dieselbe Ressource bearbeitet nie mehr als
 *       ein Worker; kommt eine neuere Fassung, ersetzt sie eine noch wartende ältere, und eine laufende wird vor ihr
 *       fertig: die neueste Fassung schreibt zuletzt. Wie viele Embedding-Aufrufe dabei gleichzeitig laufen, begrenzt
 *       der Embedding-Port der Indexierung ({@link ConcurrencyLimitedEmbeddingPort}). Nur ein Auftrag, auf dessen
 *       fertige Aufbereitung jemand wartet ({@link #indexNow}, {@link Series#awaitIndexed}), kommt vor die übrigen.</li>
 *   <li><b>Priority-Thread</b>: eigener Thread nur für die Auswahl des Benutzers ({@link #openSelected}, MainframeMate
 *       {@code loadPriority}); wartet nie hinter dem Vorladen.</li>
 *   <li><b>Agent</b>: {@code knowledge_read} liest über {@link #read}/{@link #refresh} auf seinem eigenen Thread.</li>
 * </ul>
 * Ist die Revision laut Suche bekannt und so im Rohindex und aufbereitet, passiert nichts; sonst entscheidet der
 * Inhalts-Hash, ob neu zerlegt wird. Ein Cache-Eintrag gilt nur, solange die Suche keine andere Revision gemeldet hat
 * ({@link #seenRevision}). Ohne Rohindex gilt eine Seite erst mit ihrer Aufbereitung als verfügbar (wie früher).
 *
 * <p>Kanäle trennen die Serien der Aufrufer wie die Instanzen je Reiter in MainframeMate (Drawer, Chat, Agent); Pools,
 * Cache und Worker sind gemeinsam. Alle Größen werden je Aufruf aus den Einstellungen gelesen (ohne Neustart).
 * Threadsicher.
 */
public final class KnowledgePrefetchService {

    /** Wie lange ein Lesen auf Anforderung höchstens wartet (MainframeMate {@code loadPriority}). */
    static final long PRIORITY_TIMEOUT_SECONDS = 30;
    /** Thread-Priorität der Aufbereitung: unter der des Chats und des Ladens. */
    private static final int BACKGROUND_PRIORITY = Thread.NORM_PRIORITY - 1;
    /** Wie lange ein Aufbereitungs-Worker bei leerer Warteschlange höchstens schläft (askai {@code idlePollMillis}). */
    private static final long IDLE_POLL_MILLIS = 1000;
    private static final int SEEN_LIMIT = 10000;

    /** Wie eine Seite auf Anforderung geladen wird (z. B. {@code documents.load(id)}). */
    public interface DocumentLoad {
        KnowledgeDocument load() throws KnowledgeSourceException, KnowledgeDocumentNotIndexedException;
    }

    private final IndexKnowledgeUseCase indexing;
    /** Rohindex der Ebene A oder {@code null} (dann gilt eine Seite erst aufbereitet als verfügbar). */
    private final RawKnowledgeIndexPort raw;
    private final KnowledgeDocumentReader reader;
    private final Supplier<Settings> settings;
    private final ExecutorService priorityThread;
    /** Lade-Pool; Serien, auf die jemand wartet, zuerst. */
    private final ThreadPoolExecutor prefetchPool;
    /** Schreibt geladene Seiten in den Rohindex. */
    private final ThreadPoolExecutor rawPool;
    private final AtomicLong sequence = new AtomicLong();
    /** Wartende Aufbereitungen in Ankunftsreihenfolge; geschützt durch sich selbst. */
    private final java.util.LinkedList<IndexJob> indexQueue = new java.util.LinkedList<IndexJob>();
    /** Aufträge, die Worker gerade aufbereiten, je Ressource. Unter {@code indexQueue}. */
    private final Map<KnowledgeResourceId, IndexJob> inProgress = new HashMap<KnowledgeResourceId, IndexJob>();
    /** Laufende Aufbereitungs-Worker. Unter {@code indexQueue}. */
    private int workers;
    /** Wie viele Aufträge die Worker begonnen haben (Tests). Unter {@code indexQueue}. */
    private int indexRuns;
    /** Wie viele Aufträge höchstens gleichzeitig liefen (Tests). Unter {@code indexQueue}. */
    private int maxParallelRuns;
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
    private volatile boolean shutdown;

    /**
     * Ohne Rohindex: eine Seite gilt erst als verfügbar, wenn sie aufbereitet ist.
     *
     * @param indexing indexiert jede geladene Seite, falls neu oder geändert
     * @param reader   liest einen Treffer aus seiner Quelle (produktiv der vermittelte Zugriff)
     * @param settings Fenster, Worker und Cache-Größe; je Aufruf gelesen (Einstellungen ohne Neustart)
     */
    public KnowledgePrefetchService(IndexKnowledgeUseCase indexing, KnowledgeDocumentReader reader,
                                    Supplier<Settings> settings) {
        this(indexing, null, reader, settings);
    }

    /**
     * @param raw Rohindex der Ebene A: jede geladene Seite ist dort sofort durchsuchbar; {@code null} = keiner
     */
    public KnowledgePrefetchService(IndexKnowledgeUseCase indexing, RawKnowledgeIndexPort raw,
                                    KnowledgeDocumentReader reader, Supplier<Settings> settings) {
        if (indexing == null || reader == null || settings == null) {
            throw new IllegalArgumentException("indexing, reader und settings sind Pflicht");
        }
        this.indexing = indexing;
        this.raw = raw;
        this.reader = reader;
        this.settings = settings;
        Settings current = currentSettings();
        this.priorityThread = Executors.newSingleThreadExecutor(threads("enterprise-ai-prefetch-priority",
                Thread.NORM_PRIORITY));
        this.prefetchPool = new ThreadPoolExecutor(current.concurrency(), current.concurrency(), IDLE_POLL_MILLIS,
                TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>(),
                threads("enterprise-ai-prefetch", Thread.NORM_PRIORITY)) {
            @Override
            protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
                return new RankedTask<T>(runnable, value, runnable instanceof Ranked ? ((Ranked) runnable).rank
                        : Long.MAX_VALUE);
            }
        };
        this.rawPool = new ThreadPoolExecutor(current.rawWorkers(), current.rawWorkers(), IDLE_POLL_MILLIS,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(),
                threads("enterprise-ai-raw-index", Thread.NORM_PRIORITY));
        ensureWorkers();
    }

    // ------------------------------------------------------------------ Prefetch (Hintergrund, aus Suchtreffern)

    /**
     * Lädt die Treffer ab {@code fromIndex} im Hintergrund vor (höchstens {@link Settings#maxItems()}) und bricht die
     * noch laufende Serie desselben Kanals ab.
     *
     * @param channel   wessen Serie (z. B. "drawer", "chat", "agent")
     * @param ranked    alle Treffer in Rangfolge
     * @param fromIndex Startposition, typischerweise die Cursor- oder Scrollposition
     * @return die neue Serie; ihre Einträge werden verfügbar, sobald die Seite im Rohindex liegt, und fertig, sobald
     *         sie aufbereitet (oder unverändert) ist
     */
    public Series prefetch(String channel, List<Candidate> ranked, int fromIndex) {
        return prefetch(channel, ranked, fromIndex, false);
    }

    /**
     * Wie {@link #prefetch(String, List, int)}; {@code awaited}: jemand wartet gleich auf die Serie (Chat-Recherche),
     * ihre Abrufe laufen vor allem übrigen Vorladen.
     */
    public Series prefetch(String channel, List<Candidate> ranked, int fromIndex, boolean awaited) {
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
            resize(current);
            int start = Math.max(0, fromIndex);
            int end = Math.min(ranked.size(), start + current.maxItems());
            for (int i = start; i < end; i++) {
                final Candidate candidate = ranked.get(i);
                if (candidate == null) {
                    continue;
                }
                final Pending pending = new Pending();
                // Rang: erwartete Serien vor dem übrigen Vorladen, sonst in Ankunftsreihenfolge.
                long rank = (awaited ? 0L : Long.MAX_VALUE / 2) + sequence.incrementAndGet();
                Future<?> task = prefetchPool.submit(new Ranked(rank) {
                    @Override
                    public void run() {
                        if (Thread.currentThread().isInterrupted()) {
                            pending.cancel();
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
        return series;
    }

    private void prefetchOne(Candidate candidate, Pending pending) {
        CompletableFuture<ResourceIndexingOutcome> indexed = pending.indexed;
        try {
            boolean prepared = indexing.isCurrent(candidate.id(), candidate.revision());
            boolean rawCurrent = raw == null || isRawCurrent(candidate.id(), candidate.revision());
            if (prepared && rawCurrent) {
                // Revision bekannt, im Rohindex und aufbereitet: weder laden noch aufbereiten.
                pending.harvest.complete(Harvest.ALREADY_CURRENT);
                pending.available.complete(Boolean.TRUE);
                indexed.complete(ResourceIndexingOutcome.done(candidate.id(), "", IndexingStatus.UNCHANGED, 0));
                return;
            }
            if (rawCurrent && raw != null) {
                pending.available.complete(Boolean.TRUE); // lexikalisch schon da; aufbereitet wird im Hintergrund
            }
            // Die Kopie gilt nur für genau die gemeldete Revision; ohne Revision weiß nur die Quelle, ob sich etwas
            // geändert hat (der Hash spart dann das Aufbereiten).
            KnowledgeDocument document = candidate.revision().isKnown()
                    ? cachedCurrent(candidate.id(), candidate.revision()) : null;
            if (document == null) {
                document = remember(loadCurrent(candidate.source(), candidate.id(), candidate.revision()));
            }
            pending.harvest.complete(Harvest.LOADED);
            // Ebene A: sofort in den Rohindex (eigener Pool); Ebene B: Aufbereitung im Hintergrund, unabhängig davon.
            if (raw != null && !rawCurrent) {
                storeRaw(document, candidate.snippet(), pending.available);
            }
            if (prepared) {
                indexed.complete(ResourceIndexingOutcome.done(candidate.id(), "", IndexingStatus.UNCHANGED, 0));
            } else {
                enqueueIndex(document, indexed, pending.urgent);
            }
        } catch (KnowledgeSourceException | RuntimeException e) {
            pending.fail(e); // nur ein Vorladen; Lesen auf Anforderung versucht es erneut
        }
    }

    /** Ob der Rohindex die Ressource in der gemeldeten (bekannten) Revision hat. */
    private boolean isRawCurrent(KnowledgeResourceId id, KnowledgeRevision revision) {
        if (revision == null || !revision.isKnown()) {
            return false;
        }
        try {
            java.util.Optional<KnowledgeRevision> stored = raw.revisionOf(id);
            return stored.isPresent() && stored.get().equals(revision);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Schreibt eine geladene Seite auf dem Rohindex-Pool in den Rohindex; {@code available} meldet, wann sie
     * durchsuchbar ist (auch bei einem Fehler endet es, dann mit ihm).
     */
    private void storeRaw(final KnowledgeDocument document, final String snippet,
                          final CompletableFuture<Boolean> available) {
        if (raw == null || document == null) {
            if (available != null) {
                available.complete(Boolean.FALSE);
            }
            return;
        }
        try {
            resize(currentSettings());
            rawPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        raw.put(RawKnowledgeDocument.of(document, snippet));
                        if (available != null) {
                            available.complete(Boolean.TRUE);
                        }
                    } catch (RuntimeException e) {
                        if (available != null) {
                            available.completeExceptionally(e);
                        }
                    }
                }
            });
        } catch (RuntimeException rejected) { // beendet
            if (available != null) {
                available.cancel(false);
            }
        }
    }

    /**
     * Lädt die Seite in dem Stand, den die Suche meldete: Bronze-Kopie oder Quelle; ist die Kopie älter als der
     * Suchtreffer oder die Revision unbekannt, gezielt neu (REFRESH_EXTERNAL).
     */
    private KnowledgeDocument loadCurrent(KnowledgeSourcePort source, KnowledgeResourceId id, KnowledgeRevision seen)
            throws KnowledgeSourceException {
        if (seen != null && seen.isKnown()) {
            KnowledgeDocument document = reader.read(source.sourceId(), source, id);
            return isSameRevision(document, seen) ? document : reader.refresh(source.sourceId(), source, id);
        }
        // Suche ohne Revision: nur die Quelle weiß, ob sich etwas geändert hat; der Hash spart das Aufbereiten
        return reader.refresh(source.sourceId(), source, id);
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
     * und nicht älter als die zuletzt gemeldete Revision. Aufbereitet wird sie im Hintergrund in normaler Reihenfolge,
     * weil der Aufrufer nur den Text braucht.
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
            enqueueIndex(cached, null, new java.util.concurrent.atomic.AtomicBoolean());
            return cached;
        }
        Future<KnowledgeDocument> task;
        try {
            task = priorityThread.submit(() -> {
                KnowledgeDocument document = remember(loadCurrent(source, id, seenRevision(id)));
                storeRaw(document, "", null);
                enqueueIndex(document, null, new java.util.concurrent.atomic.AtomicBoolean());
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
        KnowledgeDocument loaded = remember(load.load());
        storeRaw(loaded, "", null); // auch was der Agent liest, ist danach lexikalisch durchsuchbar
        return loaded;
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
     * sonst zerlegen, embedden, ersetzen) und wartet höchstens {@code timeoutMillis} darauf. Solange gewartet wird,
     * kommt der Auftrag vor die noch nicht begonnenen übrigen; endet das Warten vorher, reiht er sich normal ein.
     *
     * @return das Ergebnis, oder {@code null}, wenn es nicht rechtzeitig fertig wurde oder scheiterte
     */
    public ResourceIndexingOutcome indexNow(KnowledgeDocument document, long timeoutMillis) {
        CompletableFuture<ResourceIndexingOutcome> done = new CompletableFuture<ResourceIndexingOutcome>();
        java.util.concurrent.atomic.AtomicBoolean waiting = new java.util.concurrent.atomic.AtomicBoolean(true);
        enqueueIndex(document, done, waiting);
        try {
            return done.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException | java.util.concurrent.CancellationException e) {
            return null;
        } finally {
            waiting.set(false); // niemand wartet mehr: kein Vorrang
        }
    }

    /**
     * Reiht ein geladenes Dokument zur Aufbereitung ein, ohne Vorrang (FIFO wie jede andere Seite), und meldet das
     * Ergebnis, sobald der Index-Thread es erreicht. Ist die Fassung schon mit der aktuellen Zerlegung aufbereitet,
     * ist das Ergebnis sofort da. Für Aufrufer, die nicht blockieren (Dokumentnavigation).
     *
     * @return das Ergebnis; scheitert die Aufbereitung, endet die Zukunft mit dem Fehler
     */
    public CompletableFuture<ResourceIndexingOutcome> whenIndexed(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        java.util.Optional<com.aresstack.enterpriseai.astu.acropolis.chalcotheca.IndexedResourceState> state =
                indexing.stateOf(document.id());
        if (state.isPresent() && indexing.isPrepared(state.get())
                && document.contentHash().equals(state.get().contentHash())) {
            return CompletableFuture.completedFuture(ResourceIndexingOutcome.done(document.id(),
                    document.resource().title(), IndexingStatus.UNCHANGED, 0));
        }
        CompletableFuture<ResourceIndexingOutcome> done = new CompletableFuture<ResourceIndexingOutcome>();
        enqueueIndex(document, done, new java.util.concurrent.atomic.AtomicBoolean());
        return done;
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

    // ------------------------------------------------------------------ Aufbereitung (Worker im Hintergrund)

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
            // Dieselbe Fassung wartet schon oder wird gerade aufbereitet: anhängen statt ein zweites Mal einreihen.
            IndexJob same = sameCapture(document);
            if (same != null) {
                same.join(indexed, urgent);
            } else {
                IndexJob job = new IndexJob(document, indexed, urgent);
                // Eine noch wartende ältere Fassung derselben Ressource wird ersetzt: die neueste gewinnt, und wer
                // auf die alte wartete, bekommt das Ergebnis der neuen.
                for (Iterator<IndexJob> it = indexQueue.iterator(); it.hasNext(); ) {
                    IndexJob older = it.next();
                    if (older.document.id().equals(document.id())) {
                        it.remove();
                        job.adopt(older);
                    }
                }
                indexQueue.add(job);
            }
            indexQueue.notifyAll();
        }
        ensureWorkers();
    }

    /** Begonnene Aufbereitungen; eine Fassung, die mehrere Aufrufer wollen, zählt einmal. */
    int indexRunsForTest() {
        synchronized (indexQueue) {
            return indexRuns;
        }
    }

    /** Wie viele Aufbereitungen höchstens gleichzeitig liefen (Tests). */
    int maxParallelRunsForTest() {
        synchronized (indexQueue) {
            return maxParallelRuns;
        }
    }

    /** Der Auftrag für genau diese Fassung (gleiche ID, gleicher Inhalt) in Arbeit oder Warteschlange. Unter Lock. */
    private IndexJob sameCapture(KnowledgeDocument document) {
        IndexJob running = inProgress.get(document.id());
        if (running != null && running.covers(document)) {
            return running;
        }
        for (IndexJob job : indexQueue) {
            if (job.covers(document)) {
                return job;
            }
        }
        return null;
    }

    private void wakeIndex() {
        synchronized (indexQueue) {
            indexQueue.notifyAll();
        }
    }

    /** Startet Worker, bis so viele laufen wie eingestellt; überzählige beenden sich nach ihrem Auftrag. */
    private void ensureWorkers() {
        int wanted = currentSettings().enrichmentWorkers();
        synchronized (indexQueue) {
            while (!shutdown && workers < wanted) {
                workers++;
                threads("enterprise-ai-enrichment", BACKGROUND_PRIORITY).newThread(this::indexLoop).start();
            }
            indexQueue.notifyAll();
        }
    }

    /**
     * Ein Aufbereitungs-Worker (MainframeMate {@code autoIndexExecutor}, askai {@code KnowledgeProcessingRunner}, hier
     * mehrfach): nimmt den nächsten Auftrag, dessen Ressource kein anderer Worker gerade bearbeitet, und schläft nur,
     * wenn nichts zu tun ist.
     */
    private void indexLoop() {
        while (true) {
            IndexJob job;
            synchronized (indexQueue) {
                if (shutdown || workers > currentSettings().enrichmentWorkers()) {
                    workers--;
                    return;
                }
                job = nextJob();
                if (job == null) {
                    try {
                        indexQueue.wait(IDLE_POLL_MILLIS);
                    } catch (InterruptedException e) {
                        workers--;
                        return;
                    }
                    continue;
                }
                inProgress.put(job.document.id(), job);
                indexRuns++;
                maxParallelRuns = Math.max(maxParallelRuns, inProgress.size());
            }
            ResourceIndexingOutcome outcome = null;
            RuntimeException failure = null;
            try {
                outcome = indexing.indexDocumentIfChanged(job.document);
            } catch (RuntimeException e) {
                failure = e; // Lesen gelingt auch ohne Aufbereitung; die nächste Suche versucht es erneut
            }
            synchronized (indexQueue) {
                inProgress.remove(job.document.id()); // ab hier angehängte Aufrufer bekämen das Ergebnis nicht mehr
                indexQueue.notifyAll(); // eine wartende neuere Fassung derselben Ressource ist jetzt dran
            }
            job.complete(outcome, failure);
        }
    }

    /**
     * FIFO; zuerst, worauf ein Aufrufer gerade wartet. Übersprungen wird, wessen Ressource ein anderer Worker gerade
     * bearbeitet (so ersetzt nie eine ältere Fassung eine neuere). Unter {@code indexQueue}.
     */
    private IndexJob nextJob() {
        for (Iterator<IndexJob> it = indexQueue.iterator(); it.hasNext(); ) {
            IndexJob job = it.next();
            if (job.isUrgent() && !inProgress.containsKey(job.document.id())) {
                it.remove();
                return job;
            }
        }
        for (Iterator<IndexJob> it = indexQueue.iterator(); it.hasNext(); ) {
            IndexJob job = it.next();
            if (!inProgress.containsKey(job.document.id())) {
                it.remove();
                return job;
            }
        }
        return null;
    }

    /** Ein Auftrag je Fassung; weitere Aufrufer derselben Fassung hängen sich an (Ergebnis und Vorrang). */
    private static final class IndexJob {
        final KnowledgeDocument document;
        final List<CompletableFuture<ResourceIndexingOutcome>> waiting =
                new ArrayList<CompletableFuture<ResourceIndexingOutcome>>();
        final List<java.util.concurrent.atomic.AtomicBoolean> urgency =
                new ArrayList<java.util.concurrent.atomic.AtomicBoolean>();

        IndexJob(KnowledgeDocument document, CompletableFuture<ResourceIndexingOutcome> indexed,
                 java.util.concurrent.atomic.AtomicBoolean urgent) {
            this.document = document;
            join(indexed, urgent);
        }

        void join(CompletableFuture<ResourceIndexingOutcome> indexed,
                  java.util.concurrent.atomic.AtomicBoolean urgent) {
            if (indexed != null) {
                waiting.add(indexed);
            }
            if (urgent != null) {
                urgency.add(urgent);
            }
        }

        /** Übernimmt Wartende und Vorrang eines ersetzten Auftrags derselben Ressource. */
        void adopt(IndexJob older) {
            waiting.addAll(older.waiting);
            urgency.addAll(older.urgency);
        }

        boolean covers(KnowledgeDocument other) {
            return document.id().equals(other.id()) && document.contentHash().equals(other.contentHash());
        }

        /** Dringend, solange irgendein angehängter Aufrufer noch wartet. */
        boolean isUrgent() {
            for (java.util.concurrent.atomic.AtomicBoolean urgent : urgency) {
                if (urgent.get()) {
                    return true;
                }
            }
            return false;
        }

        void complete(ResourceIndexingOutcome outcome, RuntimeException failure) {
            // Nach dem Ende hängt sich niemand mehr an (die Ressource ist nicht mehr in Arbeit).
            for (CompletableFuture<ResourceIndexingOutcome> target : waiting) {
                if (failure != null) {
                    target.completeExceptionally(failure);
                } else {
                    target.complete(outcome);
                }
            }
        }

        void cancel() {
            for (CompletableFuture<ResourceIndexingOutcome> target : waiting) {
                target.cancel(false);
            }
        }
    }

    /**
     * Ein vorgeladener Treffer: verfügbar, sobald er lexikalisch durchsuchbar ist (Rohindex), fertig, sobald er
     * aufbereitet ist; wird dringend, sobald jemand auf die Aufbereitung wartet.
     */
    final class Pending {
        final CompletableFuture<ResourceIndexingOutcome> indexed = new CompletableFuture<ResourceIndexingOutcome>();
        final CompletableFuture<Boolean> available = new CompletableFuture<Boolean>();
        final java.util.concurrent.atomic.AtomicBoolean urgent = new java.util.concurrent.atomic.AtomicBoolean();
        final CompletableFuture<Harvest> harvest = new CompletableFuture<Harvest>();

        Pending() {
            if (raw == null) {
                // Ohne Rohindex ist eine Seite erst mit ihrer Aufbereitung durchsuchbar.
                indexed.whenComplete((outcome, failure) -> {
                    if (failure != null) {
                        available.completeExceptionally(failure);
                    } else {
                        available.complete(Boolean.TRUE);
                    }
                });
            }
        }

        void fail(Throwable failure) {
            harvest.completeExceptionally(failure);
            available.completeExceptionally(failure);
            indexed.completeExceptionally(failure);
        }

        void cancel() {
            harvest.cancel(false);
            available.cancel(false);
            indexed.cancel(false);
        }
    }

    /** Ein Abruf mit Rang im Lade-Pool (kleiner = früher). */
    private abstract static class Ranked implements Runnable {
        final long rank;

        Ranked(long rank) {
            this.rank = rank;
        }
    }

    /** Die Zukunft eines Abrufs, nach Rang geordnet (für die {@link PriorityBlockingQueue} des Lade-Pools). */
    private static final class RankedTask<T> extends FutureTask<T> implements Comparable<RankedTask<?>> {
        private final long rank;

        RankedTask(Runnable runnable, T value, long rank) {
            super(runnable, value);
            this.rank = rank;
        }

        @Override
        public int compareTo(RankedTask<?> other) {
            return Long.compare(rank, other.rank);
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
        }
        prefetchPool.shutdownNow();
        rawPool.shutdownNow();
        priorityThread.shutdownNow();
        synchronized (indexQueue) {
            for (IndexJob job : indexQueue) {
                job.cancel();
            }
            indexQueue.clear();
            indexQueue.notifyAll();
        }
        synchronized (cache) {
            cache.clear();
            cachedBytes = 0;
        }
    }

    private Settings currentSettings() {
        Settings current = settings.get();
        return current == null ? Settings.defaults() : current;
    }

    /** Übernimmt geänderte Worker-Zahlen in die Pools (ohne Neustart; laufende Abrufe enden noch). */
    private void resize(Settings current) {
        resize(prefetchPool, current.concurrency());
        resize(rawPool, current.rawWorkers());
        ensureWorkers();
    }

    private static void resize(ThreadPoolExecutor pool, int size) {
        synchronized (pool) {
            if (pool.getCorePoolSize() == size) {
                return;
            }
            if (size > pool.getMaximumPoolSize()) {
                pool.setMaximumPoolSize(size);
                pool.setCorePoolSize(size);
            } else {
                pool.setCorePoolSize(size);
                pool.setMaximumPoolSize(size);
            }
        }
    }

    private static ThreadFactory threads(final String name, final int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setPriority(priority);
            return thread;
        };
    }

    // ------------------------------------------------------------------ Typen

    /** Ein Treffer zum Vorladen: Quelle, Ressource, die Revision laut Suche (unbekannt erlaubt) und ihr Snippet. */
    public static final class Candidate {

        private final KnowledgeSourcePort source;
        private final KnowledgeResourceId id;
        private final KnowledgeRevision revision;
        private final String snippet;

        public Candidate(KnowledgeSourcePort source, KnowledgeResourceId id, KnowledgeRevision revision) {
            this(source, id, revision, "");
        }

        public Candidate(KnowledgeSourcePort source, KnowledgeResourceId id, KnowledgeRevision revision,
                         String snippet) {
            if (source == null || id == null) {
                throw new IllegalArgumentException("source und id sind Pflicht");
            }
            this.source = source;
            this.id = id;
            this.revision = revision == null ? KnowledgeRevision.unknown() : revision;
            this.snippet = snippet == null ? "" : snippet;
        }

        /** Snippet der Quellensuche (für den Rohindex); leer, wenn keines. */
        public String snippet() {
            return snippet;
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

    /** How the full text of a prefetched hit became available (#154). */
    public enum Harvest {
        /** Already in the index with the reported revision: nothing was downloaded. */
        ALREADY_CURRENT,
        /** Loaded (from the source or the copy of exactly the reported revision). */
        LOADED
    }

    /** Learns how each entry of a series ended; called on a prefetch thread, must return quickly. */
    public interface HarvestListener {
        /**
         * @param position zero-based position in the series (the order passed to {@link #prefetch})
         * @param harvest  how it became available, or {@code null} when it failed
         * @param failure  why it failed, or {@code null}
         */
        void harvested(int position, Harvest harvest, Throwable failure);
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
                entry.cancel();
            }
        }

        /**
         * Wartet höchstens {@code timeoutMillis}, bis die ersten {@code count} Treffer verfügbar sind, also
         * lexikalisch durchsuchbar im Rohindex (oder gescheitert, abgebrochen). Auf Passagen und Embeddings wartet das
         * nicht; die laufen im Hintergrund weiter. Ohne Rohindex heißt verfügbar: aufbereitet.
         *
         * @param listener erfährt jeden verfügbar gewordenen Treffer (Anzahl bisher); darf {@code null} sein
         * @return wie viele der ersten {@code count} verfügbar wurden
         */
        public int awaitAvailable(int count, long timeoutMillis, java.util.function.IntConsumer listener) {
            List<Pending> first = new ArrayList<Pending>();
            synchronized (this) {
                first.addAll(pending.subList(0, Math.min(Math.max(0, count), pending.size())));
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
            int available = 0;
            for (Pending entry : first) {
                long left = deadline - System.nanoTime();
                try {
                    if (Boolean.TRUE.equals(entry.available.get(Math.max(0, left), TimeUnit.NANOSECONDS))) {
                        available++;
                        if (listener != null) {
                            listener.accept(available);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException | CancellationException e) {
                    // gescheitert oder abgebrochen: zählt als erledigt, nicht als verfügbar
                } catch (TimeoutException e) {
                    break;
                }
            }
            return available;
        }

        /**
         * Reports how every entry of the series ends (loaded, already current, failed); cancelled entries are not
         * reported. Entries that already ended are reported at once.
         */
        public void onHarvest(final HarvestListener listener) {
            if (listener == null) {
                return;
            }
            List<Pending> entries;
            synchronized (this) {
                entries = new ArrayList<Pending>(pending);
            }
            for (int i = 0; i < entries.size(); i++) {
                final int position = i;
                entries.get(i).harvest.whenComplete((harvest, failure) -> {
                    if (failure instanceof CancellationException) {
                        return;
                    }
                    Throwable cause = failure instanceof java.util.concurrent.CompletionException
                            && failure.getCause() != null ? failure.getCause() : failure;
                    try {
                        listener.harvested(position, failure == null ? harvest : null, cause);
                    } catch (RuntimeException ignored) {
                        // a listener never breaks prefetching
                    }
                });
            }
        }

        /** Wie viele Treffer die Serie vorlädt. */
        public synchronized int size() {
            return tasks.size();
        }

        /**
         * Macht die ersten {@code count} Treffer dringend (vor übrigem Vorgeladenem aufbereitet) und wartet
         * höchstens {@code timeoutMillis}, bis sie indexiert (oder unverändert,
         * gescheitert, abgebrochen) sind; z. B. damit der Chat seine Frage danach im Index findet.
         *
         * @return die fertigen Ergebnisse in Rangfolge (ohne gescheiterte)
         */
        public List<ResourceIndexingOutcome> awaitIndexed(int count, long timeoutMillis) {
            List<Pending> first = new ArrayList<Pending>();
            synchronized (this) {
                for (Pending entry : pending.subList(0, Math.min(Math.max(0, count), pending.size()))) {
                    entry.urgent.set(true); // jemand wartet: vor übrigem Vorgeladenem aufbereiten
                    first.add(entry);
                }
            }
            Runnable signal = wake;
            if (signal != null) {
                signal.run();
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
            List<ResourceIndexingOutcome> outcomes = new ArrayList<ResourceIndexingOutcome>();
            try {
                for (Pending entry : first) {
                    long left = deadline - System.nanoTime();
                    try {
                        ResourceIndexingOutcome outcome = entry.indexed.get(Math.max(0, left), TimeUnit.NANOSECONDS);
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
            } finally {
                for (Pending entry : first) {
                    entry.urgent.set(false); // niemand wartet mehr: Rest wieder in normaler Reihenfolge
                }
            }
            return Collections.unmodifiableList(outcomes);
        }
    }

    /**
     * Fenster, Worker und Cache wie MainframeMate ({@code confluencePrefetchMaxItems/Concurrency/CacheMaxMb}), dazu
     * die getrennten Pools der zwei Ebenen: Rohindex-Worker, Aufbereitungs-Worker und parallele Embedding-Aufrufe.
     */
    public static final class Settings {

        public static final int DEFAULT_MAX_ITEMS = 100;
        public static final int MAX_ITEMS_LIMIT = 1000;
        public static final int DEFAULT_CONCURRENCY = 4;
        public static final int MAX_CONCURRENCY = 8;
        public static final int DEFAULT_CACHE_MAX_MB = 50;
        public static final int CACHE_MAX_MB_LIMIT = 500;
        /** Schreiber in den Rohindex ({@code knowledge.raw.workers}). */
        public static final int DEFAULT_RAW_WORKERS = 2;
        public static final int MAX_RAW_WORKERS = 8;
        /** Dokumente, die gleichzeitig zerlegt und eingebettet werden ({@code knowledge.enrichment.workers}). */
        public static final int DEFAULT_ENRICHMENT_WORKERS = 4;
        public static final int MAX_ENRICHMENT_WORKERS = 16;
        /** Gleichzeitige Aufrufe des Embedding-Endpunkts bei der Aufbereitung ({@code embedding.maxConcurrency}). */
        public static final int DEFAULT_EMBEDDING_CONCURRENCY = 4;
        public static final int MAX_EMBEDDING_CONCURRENCY = 32;

        private final int maxItems;
        private final int concurrency;
        private final int cacheMaxMb;
        private final int rawWorkers;
        private final int enrichmentWorkers;
        private final int embeddingConcurrency;

        private Settings(int maxItems, int concurrency, int cacheMaxMb, int rawWorkers, int enrichmentWorkers,
                         int embeddingConcurrency) {
            this.maxItems = maxItems;
            this.concurrency = concurrency;
            this.cacheMaxMb = cacheMaxMb;
            this.rawWorkers = rawWorkers;
            this.enrichmentWorkers = enrichmentWorkers;
            this.embeddingConcurrency = embeddingConcurrency;
        }

        /** Werte werden in ihre Grenzen gelegt (1–1000 Seiten, 1–8 Worker, 1–500 MB); Pools wie voreingestellt. */
        public static Settings of(int maxItems, int concurrency, int cacheMaxMb) {
            return of(maxItems, concurrency, cacheMaxMb, DEFAULT_RAW_WORKERS, DEFAULT_ENRICHMENT_WORKERS,
                    DEFAULT_EMBEDDING_CONCURRENCY);
        }

        /** Wie {@link #of(int, int, int)} samt Rohindex-, Aufbereitungs- und Embedding-Parallelität. */
        public static Settings of(int maxItems, int concurrency, int cacheMaxMb, int rawWorkers,
                                  int enrichmentWorkers, int embeddingConcurrency) {
            return new Settings(clamp(maxItems, 1, MAX_ITEMS_LIMIT), clamp(concurrency, 1, MAX_CONCURRENCY),
                    clamp(cacheMaxMb, 1, CACHE_MAX_MB_LIMIT), clamp(rawWorkers, 1, MAX_RAW_WORKERS),
                    clamp(enrichmentWorkers, 1, MAX_ENRICHMENT_WORKERS),
                    clamp(embeddingConcurrency, 1, MAX_EMBEDDING_CONCURRENCY));
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

        /** Parallele Abrufe aus den Quellen (Lade-Pool). */
        public int concurrency() {
            return concurrency;
        }

        /** Größe des Speicher-Caches in MB. */
        public int cacheMaxMb() {
            return cacheMaxMb;
        }

        /** Threads, die geladene Seiten in den Rohindex schreiben. */
        public int rawWorkers() {
            return rawWorkers;
        }

        /** Dokumente, die gleichzeitig semantisch aufbereitet werden (Passagen, Embeddings, Index). */
        public int enrichmentWorkers() {
            return enrichmentWorkers;
        }

        /** Gleichzeitige Aufrufe des Embedding-Endpunkts bei der Aufbereitung. */
        public int embeddingConcurrency() {
            return embeddingConcurrency;
        }

        @Override
        public String toString() {
            return "Settings{maxItems=" + maxItems + ", concurrency=" + concurrency + ", cacheMaxMb=" + cacheMaxMb
                    + ", rawWorkers=" + rawWorkers + ", enrichmentWorkers=" + enrichmentWorkers
                    + ", embeddingConcurrency=" + embeddingConcurrency + "}";
        }
    }
}
