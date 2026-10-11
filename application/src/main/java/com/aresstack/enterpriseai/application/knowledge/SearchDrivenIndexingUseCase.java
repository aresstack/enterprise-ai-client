package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
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
 * {@code ConfluenceSearchProvider}): Die Quellen werden live durchsucht, die Treffer zusammengeführt, und nur die
 * ersten {@code maxPages} Treffer werden – falls neu oder geändert – geladen, zerlegt, eingebettet und indexiert
 * ({@link IndexKnowledgeUseCase#indexIfChanged}). Unveränderte Seiten (gleiche Revision oder gleicher Inhalts-Hash)
 * werden nie neu verarbeitet. Der Index wächst so mit der Nutzung; einen Vollindex beim Start gibt es nicht.
 *
 * <p>Durchsucht werden nur Quellen mit eigener Suche ({@link SearchableKnowledgeSource}); alle anderen liegen nur
 * über ihren Index vor (Vollindex auf Knopfdruck, wie NDV in MainframeMate). Ein Fehler einer Quelle betrifft nur
 * sie und steht in {@link Result#failures()}. Blockiert für Suche und Indexierung.
 */
public final class SearchDrivenIndexingUseCase {

    /** {@code maxPages = 0}: alle Treffer laden und indexieren. */
    public static final int UNLIMITED = 0;

    private final IndexKnowledgeUseCase indexing;

    public SearchDrivenIndexingUseCase(IndexKnowledgeUseCase indexing) {
        if (indexing == null) {
            throw new IllegalArgumentException("indexing ist Pflicht");
        }
        this.indexing = indexing;
    }

    /** Wie {@link #searchAndIndex(Collection, String, int, int)} über alle Quellen des Katalogs. */
    public Result searchAndIndex(KnowledgeSourceCatalog catalog, String query, int hitLimit, int maxPages) {
        return searchAndIndex(catalog == null ? Collections.<KnowledgeSourcePort>emptyList() : catalog.ports(), query,
                hitLimit, maxPages);
    }

    /** Wie {@link #searchAndIndex(KnowledgeSourceCatalog, String, int, int)}, aber nur über die Quellen {@code only}. */
    public Result searchAndIndex(KnowledgeSourceCatalog catalog, Collection<KnowledgeSourceId> only, String query,
                                 int hitLimit, int maxPages) {
        List<KnowledgeSourcePort> sources = new ArrayList<KnowledgeSourcePort>();
        if (catalog != null && only != null) {
            for (KnowledgeSourcePort port : catalog.ports()) {
                if (only.contains(port.sourceId())) {
                    sources.add(port);
                }
            }
        }
        return searchAndIndex(sources, query, hitLimit, maxPages);
    }

    /**
     * @param hitLimit höchstens so viele Treffer je Quelle (Suche ist billig, Laden nicht)
     * @param maxPages höchstens so viele Treffer laden und indexieren; {@link #UNLIMITED} = alle
     */
    public Result searchAndIndex(Collection<KnowledgeSourcePort> sources, String query, int hitLimit, int maxPages) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (hitLimit < 1 || maxPages < 0) {
            throw new IllegalArgumentException("hitLimit >= 1 und maxPages >= 0 erwartet");
        }
        Map<KnowledgeSourceId, String> failures = new LinkedHashMap<KnowledgeSourceId, String>();
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
        Map<KnowledgeSourcePort, Map<KnowledgeResourceId, KnowledgeRevision>> toIndex =
                new LinkedHashMap<KnowledgeSourcePort, Map<KnowledgeResourceId, KnowledgeRevision>>();
        Set<KnowledgeResourceId> seen = new LinkedHashSet<KnowledgeResourceId>();
        int selected = 0;
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
                        hit.resourceId().value(), FoundResource.Origin.SOURCE));
                if (maxPages == UNLIMITED || selected < maxPages) {
                    Map<KnowledgeResourceId, KnowledgeRevision> ids = toIndex.get(source);
                    if (ids == null) {
                        ids = new LinkedHashMap<KnowledgeResourceId, KnowledgeRevision>();
                        toIndex.put(source, ids);
                    }
                    ids.put(hit.resourceId(), hit.revision());
                    selected++;
                }
            }
            if (!any) {
                break;
            }
        }

        List<ResourceIndexingOutcome> outcomes = new ArrayList<ResourceIndexingOutcome>();
        for (Map.Entry<KnowledgeSourcePort, Map<KnowledgeResourceId, KnowledgeRevision>> batch : toIndex.entrySet()) {
            try {
                outcomes.addAll(indexing.indexIfChanged(batch.getKey(), batch.getValue(), null).outcomes());
            } catch (RuntimeException e) {
                failures.put(batch.getKey().sourceId(), "Indexierung fehlgeschlagen ("
                        + e.getClass().getSimpleName() + ")");
            }
        }
        return new Result(found, outcomes, failures);
    }

    /** Ein gelesenes Dokument in den Index nehmen, falls neu oder geändert (On-Demand wie beim Öffnen in MainframeMate). */
    public ResourceIndexingOutcome indexRead(KnowledgeDocument document) {
        return indexing.indexDocumentIfChanged(document);
    }

    /** Ergebnis einer Suche: Treffer der Quellen in Rangfolge, was indexiert wurde, und Fehler je Quelle. */
    public static final class Result {

        private final List<FoundResource> hits;
        private final List<ResourceIndexingOutcome> outcomes;
        private final Map<KnowledgeSourceId, String> failures;

        Result(List<FoundResource> hits, List<ResourceIndexingOutcome> outcomes, Map<KnowledgeSourceId, String> failures) {
            this.hits = Collections.unmodifiableList(new ArrayList<FoundResource>(hits));
            this.outcomes = Collections.unmodifiableList(new ArrayList<ResourceIndexingOutcome>(outcomes));
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<KnowledgeSourceId, String>(failures));
        }

        public List<FoundResource> hits() {
            return hits;
        }

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

        public Map<KnowledgeSourceId, String> failures() {
            return failures;
        }
    }
}
