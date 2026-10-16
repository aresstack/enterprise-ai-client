package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.searchrun.api.CandidateEvent;
import com.aresstack.enterpriseai.searchrun.api.CandidateEventKind;
import com.aresstack.enterpriseai.searchrun.api.CandidateOccurrence;
import com.aresstack.enterpriseai.searchrun.api.CandidateOrigin;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeCandidate;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRun;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Use Case „Suche füllt die Wissensbasis“ (Betriebsmodell aus MainframeMate {@code SearchService} +
 * {@code ConfluenceSearchProvider.preloadResults}): Die Quellen werden live durchsucht und die Treffer
 * zusammengeführt; die vollständige Trefferliste geht an den {@link KnowledgePrefetchService}, der sie ab Position 0
 * im Hintergrund vorlädt und jede geladene Seite – falls neu oder geändert (Revision, dann Inhalts-Hash) – zerlegt,
 * einbettet und indexiert. Der Index wächst so mit der Nutzung; einen Vollindex beim Start gibt es nicht.
 *
 * <p>Die Suche wartet nicht auf das Vorladen ({@link #search}). Die Chat-Recherche wartet danach nur, bis die ersten
 * Treffer im Rohindex durchsuchbar sind ({@code search(…, awaited)} und
 * {@link KnowledgePrefetchService.Series#awaitAvailable}), nie auf Passagen und Embeddings; wer die fertige
 * Aufbereitung braucht (Agent-Werkzeug {@code knowledge_search}), wartet darauf ({@link #searchAndIndex}). Ein Recherche-Budget kennt der Use Case nicht: wie viele
 * Seiten vorgeladen werden, bestimmen die Prefetch-Einstellungen.
 *
 * <p>Durchsucht werden nur Quellen mit eigener Suche ({@link SearchableKnowledgeSource}); alle anderen liegen nur
 * über ihren Index vor (Vollindex auf Knopfdruck, wie NDV in MainframeMate). Ein Fehler einer Quelle betrifft nur
 * sie und steht in {@link Result#failures()}. Blockiert für die Live-Suche.
 *
 * <p>Every search is also recorded as a {@link KnowledgeSearchRun} (#153): all received candidates in merged order,
 * with every occurrence per source, so a hit can be deepened later without searching again. Recording never changes
 * the search; when the run log fails, the run stays in the {@link Result} and {@link Result#runRecorded()} is false.
 *
 * <p>Before loading, all hits are scored on title + excerpt ({@link ExcerptRanking}, #154) and loaded best first. The
 * ranking only sets the load order: every hit stays in the result and in the prefetch window as before, the run keeps
 * the discovery order, and scores, queueing and load outcomes are appended to the run as separate events.
 */
public final class SearchDrivenIndexingUseCase {

    /** Kanal der Vorlade-Serie, wenn der Aufrufer keinen nennt. */
    public static final String DEFAULT_CHANNEL = "search";
    /** So lange wartet {@link #searchAndIndex} höchstens auf die ersten Treffer. */
    public static final long INDEX_WAIT_MILLIS = 60_000L;

    private final KnowledgePrefetchService prefetch;
    private final SourceCalls sourceCalls;
    private final KnowledgeSearchRunPort runs;
    private final Clock clock;
    private volatile ExcerptRanking ranking = ExcerptRanking.off();

    /** Mit eigenem Vorlader (direktes Lesen aus der Quelle, Standard-Einstellungen). */
    public SearchDrivenIndexingUseCase(IndexKnowledgeUseCase indexing) {
        this(new KnowledgePrefetchService(indexing, KnowledgeDocumentReader.direct(),
                KnowledgePrefetchService.Settings::defaults));
    }

    public SearchDrivenIndexingUseCase(KnowledgePrefetchService prefetch) {
        this(prefetch, SourceCalls.direct());
    }

    /**
     * @param sourceCalls wie die Suche einer Quelle aufgerufen wird (Composition Root: Wiederholung bei vorübergehenden
     *                    Fehlern); scheitert eine Quelle trotzdem, steht sie in {@link Result#failures()} und die
     *                    Treffer der übrigen gelten
     */
    public SearchDrivenIndexingUseCase(KnowledgePrefetchService prefetch, SourceCalls sourceCalls) {
        this(prefetch, sourceCalls, null);
    }

    /**
     * @param runs where every search is recorded (#153); {@code null}: runs are built but not persisted
     */
    public SearchDrivenIndexingUseCase(KnowledgePrefetchService prefetch, SourceCalls sourceCalls,
                                       KnowledgeSearchRunPort runs) {
        this(prefetch, sourceCalls, runs, Clock.systemUTC());
    }

    SearchDrivenIndexingUseCase(KnowledgePrefetchService prefetch, SourceCalls sourceCalls,
                                KnowledgeSearchRunPort runs, Clock clock) {
        if (prefetch == null || sourceCalls == null || clock == null) {
            throw new IllegalArgumentException("prefetch und sourceCalls sind Pflicht");
        }
        this.prefetch = prefetch;
        this.sourceCalls = sourceCalls;
        this.runs = runs;
        this.clock = clock;
    }

    /** Scores title + excerpt of all live hits before loading (#154); {@code null} = source order. */
    public void rankExcerptsWith(ExcerptRanking ranking) {
        this.ranking = ranking == null ? ExcerptRanking.off() : ranking;
    }

    /** Der Vorlader (Cache, Priority-Lesen) dieser Suche. */
    public KnowledgePrefetchService prefetch() {
        return prefetch;
    }

    /** Wie {@link #search(String, Collection, String, int)} über die Quellen {@code only} des Katalogs. */
    public Result search(String channel, KnowledgeSourceCatalog catalog, Collection<SourceAddress> only,
                         String query, int hitLimit) {
        List<KnowledgeSourcePort> sources = new ArrayList<KnowledgeSourcePort>();
        if (catalog != null && only != null) {
            for (KnowledgeSourcePort port : catalog.ports()) {
                if (only.contains(port.sourceId())) {
                    sources.add(port);
                }
            }
        }
        return search(channel, sources, query, hitLimit);
    }

    /**
     * Sucht live, führt die Treffer zusammen und stößt das Vorladen ab Position 0 an; wartet nicht darauf.
     *
     * @param channel  wessen Vorlade-Serie (eine neue Suche desselben Kanals bricht die alte ab)
     * @param hitLimit höchstens so viele Treffer je Quelle (Suche ist billig, Laden nicht)
     */
    public Result search(String channel, Collection<KnowledgeSourcePort> sources, String query, int hitLimit) {
        return search(channel, sources, query, hitLimit, false);
    }

    /**
     * Wie {@link #search(String, Collection, String, int)}; {@code awaited}: der Aufrufer wartet gleich auf die ersten
     * Treffer (Chat-Recherche über {@link KnowledgePrefetchService.Series#awaitAvailable}), ihre Abrufe laufen vor dem
     * übrigen Vorladen.
     */
    public Result search(String channel, Collection<KnowledgeSourcePort> sources, String query, int hitLimit,
                         boolean awaited) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (hitLimit < 1) {
            throw new IllegalArgumentException("hitLimit >= 1 erwartet");
        }
        Map<SourceAddress, String> failures = new LinkedHashMap<SourceAddress, String>();
        List<SourceAddress> asked = new ArrayList<SourceAddress>();
        Instant startedAt = clock.instant();
        List<List<SourceSearchHit>> perSource = new ArrayList<List<SourceSearchHit>>();
        List<KnowledgeSourcePort> searched = new ArrayList<KnowledgeSourcePort>();
        for (KnowledgeSourcePort source : sources == null ? Collections.<KnowledgeSourcePort>emptyList() : sources) {
            if (!(source instanceof SearchableKnowledgeSource)) {
                continue;
            }
            final SearchableKnowledgeSource searchable = (SearchableKnowledgeSource) source;
            asked.add(source.sourceId());
            final SourceQuery sourceQuery = new SourceQuery(query.trim(), hitLimit);
            try {
                perSource.add(sourceCalls.call(() -> searchable.search(sourceQuery)));
                searched.add(source);
            } catch (KnowledgeSourceException e) {
                if (e.kind() != KnowledgeSourceException.Kind.UNSUPPORTED) {
                    failures.put(source.sourceId(), SearchKnowledgeSourcesUseCase.problemText(e));
                }
            } catch (RuntimeException e) {
                failures.put(source.sourceId(), "Suche fehlgeschlagen (" + e.getClass().getSimpleName() + ")");
            }
        }

        // Zusammenführen wie MainframeMate: abwechselnd je Quelle, doppelte Ressourcen nur einmal.
        List<FoundResource> found = new ArrayList<FoundResource>();
        List<KnowledgePrefetchService.Candidate> candidates = new ArrayList<KnowledgePrefetchService.Candidate>();
        Set<KnowledgeResourceId> seen = new LinkedHashSet<KnowledgeResourceId>();
        // Every place a resource appeared, also the duplicates the merge drops (#153: no provenance is lost).
        Map<KnowledgeResourceId, List<CandidateOccurrence>> occurrences =
                new LinkedHashMap<KnowledgeResourceId, List<CandidateOccurrence>>();
        for (int rank = 0; ; rank++) {
            boolean any = false;
            for (int s = 0; s < perSource.size(); s++) {
                List<SourceSearchHit> hits = perSource.get(s);
                if (rank >= hits.size()) {
                    continue;
                }
                any = true;
                SourceSearchHit hit = hits.get(rank);
                KnowledgeSourcePort source = searched.get(s);
                List<CandidateOccurrence> places = occurrences.get(hit.resourceId());
                if (places == null) {
                    places = new ArrayList<CandidateOccurrence>();
                    occurrences.put(hit.resourceId(), places);
                }
                places.add(new CandidateOccurrence(source.sourceId(), rank));
                if (!seen.add(hit.resourceId())) {
                    continue;
                }
                found.add(new FoundResource(source.sourceId(), hit.resourceId(), hit.title(), hit.snippet(),
                        hit.resourceId().value(), FoundResource.Origin.SOURCE, hit.revision(),
                        hit.urlText()));
                candidates.add(new KnowledgePrefetchService.Candidate(source, hit.resourceId(), hit.revision(),
                        hit.snippet()));
            }
            if (!any) {
                break;
            }
        }
        KnowledgeSearchRun run = run(query, startedAt, asked, failures, found, occurrences);
        boolean recorded = record(run);
        ExcerptRanking.Priority priority = ranking.prioritize(query.trim(), found);
        List<FoundResource> prioritized = new ArrayList<FoundResource>(found.size());
        List<KnowledgePrefetchService.Candidate> loadOrder =
                new ArrayList<KnowledgePrefetchService.Candidate>(candidates.size());
        for (int index : priority.order()) {
            prioritized.add(found.get(index));
            loadOrder.add(candidates.get(index));
        }
        KnowledgePrefetchService.Series series = prefetch.prefetch(channel == null ? DEFAULT_CHANNEL : channel,
                loadOrder, 0, awaited);
        if (recorded) {
            recordPriority(run, priority, series);
        }
        return new Result(prioritized, series, failures, run, recorded, priority);
    }

    /** Appends scores, queueing and (later, from the prefetch threads) load outcomes to the run. */
    private void recordPriority(final KnowledgeSearchRun run, final ExcerptRanking.Priority priority,
                                KnowledgePrefetchService.Series series) {
        Instant now = clock.instant();
        List<CandidateEvent> events = new ArrayList<CandidateEvent>();
        List<Integer> order = priority.order();
        if (priority.ranked()) {
            for (int p = 0; p < order.size(); p++) {
                int index = order.get(p);
                events.add(new CandidateEvent(run.id(), index + 1, CandidateEventKind.EXCERPT_SCORED, now,
                        "priority " + (p + 1), priority.score(index)));
            }
        }
        int queued = Math.min(series.size(), order.size());
        for (int p = 0; p < queued; p++) {
            events.add(new CandidateEvent(run.id(), order.get(p) + 1, CandidateEventKind.PREFETCH_QUEUED, now,
                    "load position " + (p + 1), null));
        }
        try {
            runs.appendAll(events);
        } catch (RuntimeException e) {
            return; // the log never fails a search
        }
        final List<Integer> positions = new ArrayList<Integer>(order);
        series.onHarvest((position, harvest, failure) -> {
            if (position >= positions.size()) {
                return;
            }
            CandidateEventKind kind = harvest == KnowledgePrefetchService.Harvest.ALREADY_CURRENT
                    ? CandidateEventKind.ALREADY_CURRENT
                    : harvest == KnowledgePrefetchService.Harvest.LOADED
                    ? CandidateEventKind.LOADED : CandidateEventKind.LOAD_FAILED;
            String detail = failure == null ? null : failure instanceof KnowledgeSourceException
                    ? SearchKnowledgeSourcesUseCase.problemText((KnowledgeSourceException) failure)
                    : failure.getClass().getSimpleName();
            try {
                runs.append(new CandidateEvent(run.id(), positions.get(position) + 1, kind, clock.instant(), detail,
                        null));
            } catch (RuntimeException ignored) {
                // the log never fails prefetching
            }
        });
    }

    private static KnowledgeSearchRun run(String query, Instant startedAt, List<SourceAddress> asked,
                                          Map<SourceAddress, String> failures, List<FoundResource> found,
                                          Map<KnowledgeResourceId, List<CandidateOccurrence>> occurrences) {
        List<KnowledgeCandidate> received = new ArrayList<KnowledgeCandidate>(found.size());
        for (FoundResource resource : found) {
            received.add(new KnowledgeCandidate(received.size() + 1, resource.sourceId(), resource.resourceId(),
                    resource.title(), resource.snippet(), resource.url(), resource.revision(), CandidateOrigin.SOURCE,
                    occurrences.get(resource.resourceId())));
        }
        return new KnowledgeSearchRun(SearchRunId.random(), query, startedAt, asked, failures, received);
    }

    /** Writes the run to the log; a failing log never fails the search. */
    private boolean record(KnowledgeSearchRun run) {
        if (runs == null) {
            return false;
        }
        try {
            runs.record(run);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Wie {@link #searchAndIndex(Collection, String, int, int)} über alle Quellen des Katalogs. */
    public Result searchAndIndex(KnowledgeSourceCatalog catalog, String query, int hitLimit, int waitFor) {
        return searchAndIndex(catalog == null ? Collections.<KnowledgeSourcePort>emptyList() : catalog.ports(), query,
                hitLimit, waitFor);
    }

    /**
     * Sucht wie {@link #search} und wartet danach, bis die ersten {@code waitFor} Treffer indexiert sind (höchstens
     * {@link #INDEX_WAIT_MILLIS}); für Aufrufer, die gleich danach im Index suchen (Chat-RAG). Die übrigen Treffer
     * lädt der Vorlader weiter im Hintergrund.
     */
    public Result searchAndIndex(Collection<KnowledgeSourcePort> sources, String query, int hitLimit, int waitFor) {
        return searchAndIndex(DEFAULT_CHANNEL, sources, query, hitLimit, waitFor);
    }

    public Result searchAndIndex(String channel, Collection<KnowledgeSourcePort> sources, String query, int hitLimit,
                                 int waitFor) {
        Result result = search(channel, sources, query, hitLimit);
        result.outcomes = result.series.awaitIndexed(waitFor, INDEX_WAIT_MILLIS);
        return result;
    }

    /**
     * Ergebnis einer Suche: alle Treffer der Quellen in Rangfolge, die Vorlade-Serie, Fehler je Quelle und – nach
     * {@link #searchAndIndex} – was beim Warten indexiert wurde.
     */
    public static final class Result {

        private final List<FoundResource> hits;
        private final KnowledgePrefetchService.Series series;
        private final Map<SourceAddress, String> failures;
        private final KnowledgeSearchRun run;
        private final boolean runRecorded;
        private final ExcerptRanking.Priority priority;
        private volatile List<ResourceIndexingOutcome> outcomes = Collections.emptyList();

        Result(List<FoundResource> hits, KnowledgePrefetchService.Series series,
               Map<SourceAddress, String> failures, KnowledgeSearchRun run, boolean runRecorded,
               ExcerptRanking.Priority priority) {
            this.hits = Collections.unmodifiableList(new ArrayList<FoundResource>(hits));
            this.series = series;
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<SourceAddress, String>(failures));
            this.run = run;
            this.runRecorded = runRecorded;
            this.priority = priority;
        }

        /** How the hits were ordered for loading (#154): reranked on title + excerpt, or the source order. */
        public ExcerptRanking.Priority priority() {
            return priority;
        }

        /** What this search received, candidate by candidate (#153); candidates are not sources. */
        public KnowledgeSearchRun run() {
            return run;
        }

        /** {@code true} when {@link #run()} is in the persistent run log and can be addressed later. */
        public boolean runRecorded() {
            return runRecorded;
        }

        /** Id of the recorded run as text, or {@code null} when it is not in the run log. */
        public String recordedRunId() {
            return runRecorded ? run.id().value() : null;
        }

        /**
         * Number of the live hit {@code id} in the run ("Treffer 18"), or 0 when the run has no such candidate. The
         * run numbers only the live candidates in discovery order, independent of how a caller lists hits.
         */
        public int candidateNumber(KnowledgeResourceId id) {
            for (KnowledgeCandidate candidate : run.candidates()) {
                if (candidate.resourceId().equals(id)) {
                    return candidate.number();
                }
            }
            return 0;
        }

        /** How many candidates the run received. */
        public int candidateCount() {
            return run.candidates().size();
        }

        /** Die Vorlade-Serie der Treffer. */
        public KnowledgePrefetchService.Series series() {
            return series;
        }

        /** All hits in load order: best excerpt score first, or the merged source order without ranking. */
        public List<FoundResource> hits() {
            return hits;
        }

        /** Was {@link #searchAndIndex} abgewartet hat; nach {@link #search} leer. */
        public List<ResourceIndexingOutcome> outcomes() {
            return outcomes;
        }

        public int count(IndexingStatus status) {
            int n = 0;
            for (ResourceIndexingOutcome outcome : outcomes) {
                if (outcome.status() == status) {
                    n++;
                }
            }
            return n;
        }

        public Map<SourceAddress, String> failures() {
            return failures;
        }
    }
}
