package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Was die Suchleiste des Reiters „Wissensquellen“ gerade zeigt: die Anfrage, ob noch gesucht wird, die Treffer (in der
 * Reihenfolge der Quellen) und je Quelle, die nicht antwortete, eine kurze Meldung. Unveränderlich.
 */
public final class SourceSearchState {

    private final String query;
    private final boolean running;
    private final int sourceCount;
    private final List<ReaderLink> directHits;
    private final List<ReaderLink> searchHits;
    private final List<ReaderLink> hits;
    private final List<String> problems;

    private SourceSearchState(String query, boolean running, int sourceCount, List<ReaderLink> directHits,
                              List<ReaderLink> searchHits, List<String> problems) {
        this.query = query == null ? "" : query.trim();
        this.running = running;
        this.sourceCount = Math.max(0, sourceCount);
        this.directHits = immutable(directHits);
        this.searchHits = immutable(searchHits);
        List<ReaderLink> all = new ArrayList<ReaderLink>(this.directHits.size() + this.searchHits.size());
        all.addAll(this.directHits);
        all.addAll(this.searchHits);
        this.hits = Collections.unmodifiableList(all);
        this.problems = Collections.unmodifiableList(new ArrayList<String>(
                problems == null ? Collections.<String>emptyList() : problems));
    }

    public static SourceSearchState running(String query, int sourceCount) {
        return new SourceSearchState(query, true, sourceCount, Collections.<ReaderLink>emptyList(),
                Collections.<ReaderLink>emptyList(), Collections.<String>emptyList());
    }

    /** @param problems z. B. „confluence: nicht erreichbar“ */
    public static SourceSearchState done(String query, int sourceCount, List<ReaderLink> hits, List<String> problems) {
        return done(query, sourceCount, Collections.<ReaderLink>emptyList(), hits, problems);
    }

    /**
     * Ergebnis mit direkten Adresstreffern vor den normalen Suchtreffern. Direkttreffer stammen ausschließlich aus
     * Quellen, deren eigenes Address-Pattern auf die vollständige Eingabe gepasst hat.
     */
    public static SourceSearchState done(String query, int sourceCount, List<ReaderLink> directHits,
                                         List<ReaderLink> searchHits, List<String> problems) {
        return new SourceSearchState(query, false, sourceCount, directHits, searchHits, problems);
    }

    public String query() {
        return query;
    }

    public boolean running() {
        return running;
    }

    public int sourceCount() {
        return sourceCount;
    }

    /** Alle Treffer in Anzeigereihenfolge: Direkttreffer zuerst, danach normale Suchtreffer. */
    public List<ReaderLink> hits() {
        return hits;
    }

    public List<ReaderLink> directHits() {
        return directHits;
    }

    public List<ReaderLink> searchHits() {
        return searchHits;
    }

    public List<String> problems() {
        return problems;
    }

    private static List<ReaderLink> immutable(List<ReaderLink> links) {
        return Collections.unmodifiableList(new ArrayList<ReaderLink>(
                links == null ? Collections.<ReaderLink>emptyList() : links));
    }

    /** Die Zeile über der Trefferliste. */
    public String summary() {
        String sources = sourceCount == 1 ? "1 Quelle" : sourceCount + " Quellen";
        if (sourceCount == 0) {
            return "Keine Quelle angehakt";
        }
        if (running) {
            return "Suche in " + sources + " …";
        }
        String found = hits.isEmpty() ? "Keine Treffer" : hits.size() == 1 ? "1 Treffer" : hits.size() + " Treffer";
        return found + " in " + sources;
    }
}
