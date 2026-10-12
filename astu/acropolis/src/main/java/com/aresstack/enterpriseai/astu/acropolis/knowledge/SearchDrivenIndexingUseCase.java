package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;

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
 */
public final class SearchDrivenIndexingUseCase {

    /** Kanal der Vorlade-Serie, wenn der Aufrufer keinen nennt. */
    public static final String DEFAULT_CHANNEL = "search";
    /** So lange wartet {@link #searchAndIndex} höchstens auf die ersten Treffer. */
    public static final long INDEX_WAIT_MILLIS = 60_000L;

    private final KnowledgePrefetchService prefetch;

    /** Mit eigenem Vorlader (direktes Lesen aus der Quelle, Standard-Einstellungen). */
    public SearchDrivenIndexingUseCase(IndexKnowledgeUseCase indexing) {
        this(new KnowledgePrefetchService(indexing, KnowledgeDocumentReader.direct(),
                KnowledgePrefetchService.Settings::defaults));
    }

    public SearchDrivenIndexingUseCase(KnowledgePrefetchService prefetch) {
        if (prefetch == null) {
            throw new IllegalArgumentException("prefetch ist Pflicht");
        }
        this.prefetch = prefetch;
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
        List<List<SourceSearchHit>> perSource = new ArrayList<List<SourceSearchHit>>();
        List<KnowledgeSourcePort> searched = new ArrayList<KnowledgeSourcePort>();
        for (KnowledgeSourcePort source : sources == null ? Collections.<KnowledgeSourcePort>emptyList() : sources) {
            if (!(source instanceof SearchableKnowledgeSource)) {
                continue;
            }
            try {
                perSource.add(((SearchableKnowledgeSource) source).search(new SourceQuery(query.trim(), hitLimit)));
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
        for (int rank = 0; ; rank++) {
            boolean any = false;
            for (int s = 0; s < perSource.size(); s++) {
                List<SourceSearchHit> hits = perSource.get(s);
                if (rank >= hits.size()) {
                    continue;
                }
                any = true;
                SourceSearchHit hit = hits.get(rank);
                if (!seen.add(hit.resourceId())) {
                    continue;
                }
                KnowledgeSourcePort source = searched.get(s);
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
        KnowledgePrefetchService.Series series = prefetch.prefetch(channel == null ? DEFAULT_CHANNEL : channel,
                candidates, 0, awaited);
        return new Result(found, series, failures);
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
        private volatile List<ResourceIndexingOutcome> outcomes = Collections.emptyList();

        Result(List<FoundResource> hits, KnowledgePrefetchService.Series series,
               Map<SourceAddress, String> failures) {
            this.hits = Collections.unmodifiableList(new ArrayList<FoundResource>(hits));
            this.series = series;
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<SourceAddress, String>(failures));
        }

        /** Die Vorlade-Serie der Treffer. */
        public KnowledgePrefetchService.Series series() {
            return series;
        }

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
