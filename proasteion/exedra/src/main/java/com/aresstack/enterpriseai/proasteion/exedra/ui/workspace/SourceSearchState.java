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
    private final List<ReaderLink> hits;
    private final List<String> problems;

    private SourceSearchState(String query, boolean running, int sourceCount, List<ReaderLink> hits,
                              List<String> problems) {
        this.query = query == null ? "" : query.trim();
        this.running = running;
        this.sourceCount = Math.max(0, sourceCount);
        this.hits = Collections.unmodifiableList(new ArrayList<ReaderLink>(hits));
        this.problems = Collections.unmodifiableList(new ArrayList<String>(problems));
    }

    public static SourceSearchState running(String query, int sourceCount) {
        return new SourceSearchState(query, true, sourceCount, Collections.<ReaderLink>emptyList(),
                Collections.<String>emptyList());
    }

    /** @param problems z. B. „confluence: nicht erreichbar“ */
    public static SourceSearchState done(String query, int sourceCount, List<ReaderLink> hits, List<String> problems) {
        return new SourceSearchState(query, false, sourceCount,
                hits == null ? Collections.<ReaderLink>emptyList() : hits,
                problems == null ? Collections.<String>emptyList() : problems);
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

    public List<ReaderLink> hits() {
        return hits;
    }

    public List<String> problems() {
        return problems;
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
