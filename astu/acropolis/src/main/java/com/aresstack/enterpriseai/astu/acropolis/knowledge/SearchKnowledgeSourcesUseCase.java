package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexPort;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeSearchHit;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SearchableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceQuery;
import com.aresstack.enterpriseai.source.api.SourceSearchHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Use Case „Wissensquellen durchsuchen“ (globale Suche wie in MainframeMate, hier für die Seitenleiste): fragt alle
 * übergebenen Quellen PARALLEL und liefert je Quelle Treffer mit Titel, Auszug und Fundstelle.
 *
 * <p>Eine Quelle mit eigener Suche ({@link SearchableKnowledgeSource}, z. B. MediaWiki-Volltextsuche, Confluence-CQL)
 * wird live gefragt; meldet sie {@link KnowledgeSourceException.Kind#UNSUPPORTED} oder kann sie nicht suchen, sucht der
 * Use Case im Wissensindex (BM25), beschränkt auf diese Quelle. Ein Fehler einer Quelle betrifft nur ihr Ergebnis.
 *
 * <p>Mit {@link #withIndexedHits} werden die Live-Treffer um passende Abschnitte aus dem Index derselben Quelle
 * ergänzt. Vorladen und Indexieren der Treffer übernimmt der Aufrufer über den {@link KnowledgePrefetchService}
 * (MainframeMate: nach der Suche ab Position 0, nach jeder Auswahl ab der gewählten Zeile); die Live-Treffer tragen
 * dafür ihre Revision.
 *
 * <p>Kennt Quellen und Index nur über ihre Ports; blockiert nicht, die Arbeit läuft auf dem übergebenen Executor.
 */
public final class SearchKnowledgeSourcesUseCase {

    public static final int DEFAULT_LIMIT_PER_SOURCE = 10;
    static final int SNIPPET_LENGTH = 220;

    private final KnowledgeIndexPort index;
    private final Supplier<EmbeddingModelIdentity> space;
    private final Executor executor;
    private final boolean mergeIndexed;

    /**
     * @param index    Wissensindex für Quellen ohne eigene Suche; {@code null}: solche Quellen liefern nichts
     * @param space    Namespace des Index (Embedding-Welt), je Suche gelesen; Pflicht mit Index
     * @param executor führt die Suche je Quelle aus (mehrere gleichzeitig)
     */
    public SearchKnowledgeSourcesUseCase(KnowledgeIndexPort index, Supplier<EmbeddingModelIdentity> space,
                                         Executor executor) {
        if (executor == null) {
            throw new IllegalArgumentException("executor must not be null");
        }
        if (index != null && space == null) {
            throw new IllegalArgumentException("space must not be null when an index is given");
        }
        this.index = index;
        this.space = space;
        this.executor = executor;
        this.mergeIndexed = false;
    }

    private SearchKnowledgeSourcesUseCase(SearchKnowledgeSourcesUseCase base) {
        this.index = base.index;
        this.space = base.space;
        this.executor = base.executor;
        this.mergeIndexed = true;
    }

    /** Dieselbe Suche, die Live-Treffer um passende Ressourcen derselben Quelle aus dem Index ergänzt. */
    public SearchKnowledgeSourcesUseCase withIndexedHits() {
        return new SearchKnowledgeSourcesUseCase(this);
    }

    /**
     * Sucht {@code text} in allen Quellen gleichzeitig. Das Ergebnis enthält je Quelle genau einen Eintrag, in der
     * Reihenfolge der Quellen.
     */
    public CompletableFuture<List<SourceSearchOutcome>> search(List<KnowledgeSourcePort> sources, final String text,
                                                               final int limitPerSource) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (limitPerSource < 1) {
            throw new IllegalArgumentException("limitPerSource must be >= 1");
        }
        final List<CompletableFuture<SourceSearchOutcome>> parts = new ArrayList<CompletableFuture<SourceSearchOutcome>>();
        for (final KnowledgeSourcePort source : sources == null ? Collections.<KnowledgeSourcePort>emptyList() : sources) {
            if (source == null) {
                continue;
            }
            parts.add(CompletableFuture.supplyAsync(() -> searchOne(source, text.trim(), limitPerSource), executor));
        }
        return CompletableFuture.allOf(parts.toArray(new CompletableFuture<?>[0])).thenApply(done -> {
            List<SourceSearchOutcome> outcomes = new ArrayList<SourceSearchOutcome>(parts.size());
            for (CompletableFuture<SourceSearchOutcome> part : parts) {
                outcomes.add(part.join());
            }
            return Collections.unmodifiableList(outcomes);
        });
    }

    /** Die Suche in einer Quelle; wirft nie, ein Fehler wird zum Ergebnis der Quelle. */
    SourceSearchOutcome searchOne(KnowledgeSourcePort source, String text, int limit) {
        KnowledgeSourceId sourceId = source.sourceId();
        try {
            if (source instanceof SearchableKnowledgeSource) {
                try {
                    List<SourceSearchHit> hits = ((SearchableKnowledgeSource) source).search(new SourceQuery(text, limit));
                    return SourceSearchOutcome.found(sourceId, withIndexed(sourceId, live(sourceId, hits, text, limit),
                            text, limit));
                } catch (KnowledgeSourceException e) {
                    if (e.kind() != KnowledgeSourceException.Kind.UNSUPPORTED) {
                        return SourceSearchOutcome.failed(sourceId, problemText(e));
                    }
                }
            }
            return SourceSearchOutcome.found(sourceId, indexed(sourceId, text, limit));
        } catch (RuntimeException e) {
            return SourceSearchOutcome.failed(sourceId, "Suche fehlgeschlagen (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Live-Treffer zuerst, dann passende Ressourcen derselben Quelle aus dem Index, die die Live-Suche nicht nannte. */
    private List<FoundResource> withIndexed(KnowledgeSourceId sourceId, List<FoundResource> live, String text,
                                            int limit) {
        if (!mergeIndexed || index == null || live.size() >= limit) {
            return live;
        }
        List<FoundResource> merged = new ArrayList<FoundResource>(live);
        Set<KnowledgeResourceId> seen = new LinkedHashSet<KnowledgeResourceId>();
        for (FoundResource found : live) {
            seen.add(found.resourceId());
        }
        try {
            for (FoundResource found : indexed(sourceId, text, limit)) {
                if (merged.size() >= limit) {
                    break;
                }
                if (seen.add(found.resourceId())) {
                    merged.add(found);
                }
            }
        } catch (RuntimeException e) {
            // Index nicht lesbar: die Live-Treffer genügen.
        }
        return merged;
    }

    private static List<FoundResource> live(KnowledgeSourceId sourceId, List<SourceSearchHit> hits, String text,
                                            int limit) {
        List<FoundResource> found = new ArrayList<FoundResource>();
        for (SourceSearchHit hit : hits) {
            found.add(new FoundResource(sourceId, hit.resourceId(), hit.title(), shorten(hit.snippet(), text),
                    hit.resourceId().value(), FoundResource.Origin.SOURCE, hit.revision(),
                        hit.urlText()));
            if (found.size() >= limit) {
                break;
            }
        }
        return found;
    }

    /** BM25 im Index, beschränkt auf die Quelle; je Ressource nur der beste Abschnitt. */
    private List<FoundResource> indexed(KnowledgeSourceId sourceId, String text, int limit) {
        if (index == null) {
            return Collections.emptyList();
        }
        List<KnowledgeSearchHit> hits = index.keywordSearch(KnowledgeKeywordQuery.of(space.get(), text, limit * 4)
                .restrictedTo(Collections.singleton(sourceId)));
        List<FoundResource> found = new ArrayList<FoundResource>();
        Set<KnowledgeResourceId> seen = new LinkedHashSet<KnowledgeResourceId>();
        for (KnowledgeSearchHit hit : hits) {
            KnowledgeResourceId id = hit.resource().id();
            if (!seen.add(id)) {
                continue;
            }
            String heading = hit.chunk().headingLine();
            String location = heading.isEmpty() ? id.value() : id.value() + " › " + heading;
            found.add(new FoundResource(sourceId, id, hit.resource().title(), shorten(hit.chunk().text(), text),
                    location, FoundResource.Origin.INDEX));
            if (found.size() >= limit) {
                break;
            }
        }
        return found;
    }

    /** Fachliche Meldung ohne Rohantwort: die Art des Fehlers in Worten. */
    static String problemText(KnowledgeSourceException e) {
        switch (e.kind()) {
            case UNAVAILABLE:
                return "nicht erreichbar";
            case ACCESS_DENIED:
                return "Zugriff verweigert";
            case INVALID_RESPONSE:
                return "unerwartete Antwort";
            case NOT_FOUND:
                return "nichts gefunden";
            default:
                return "Suche nicht möglich";
        }
    }

    /**
     * Ein Auszug von höchstens {@value #SNIPPET_LENGTH} Zeichen, Leerraum zusammengezogen, möglichst um das erste
     * Vorkommen eines Suchworts herum.
     */
    static String shorten(String value, String query) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() <= SNIPPET_LENGTH) {
            return text;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int at = -1;
        for (String word : query.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (word.length() > 1) {
                int found = lower.indexOf(word);
                if (found >= 0 && (at < 0 || found < at)) {
                    at = found;
                }
            }
        }
        int start = at < 0 ? 0 : Math.max(0, at - SNIPPET_LENGTH / 3);
        int end = Math.min(text.length(), start + SNIPPET_LENGTH);
        start = Math.max(0, end - SNIPPET_LENGTH);
        return (start > 0 ? "…" : "") + text.substring(start, end).trim() + (end < text.length() ? "…" : "");
    }
}
