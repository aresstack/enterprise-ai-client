package com.aresstack.enterpriseai.proasteion.application.agent;

/**
 * Der sichtbare Stand des autonomen Recherche-Laufs (Phase Quellen), wie ihn der Agentenprozess über
 * {@code research_progress}/{@code research_run_finished} meldet: die Begriffe von {@code ResearchRunProgress},
 * {@code ResearchRunActivity} und {@code ResearchStopReason} aus askai arch, hier als Wert für Host und Oberfläche.
 * Unveränderlich.
 */
public final class ResearchRunStatus {

    /** Tokens aus {@code ResearchRunActivity}; dazu der Beginn und das Ende eines Laufs. */
    public static final String STARTED = "STARTED";
    public static final String SEARCHING = "SEARCHING";
    public static final String READING_PAGE = "READING_PAGE";
    public static final String SOURCE_ACCEPTED = "SOURCE_ACCEPTED";
    public static final String PAGE_SKIPPED = "PAGE_SKIPPED";
    public static final String FINISHED = "FINISHED";


    private final boolean active;
    private final String activity;
    private final String query;
    private final String pageTitle;
    private final String url;
    private final int pagesVisited;
    private final int acceptedSources;
    private final String stopReason;
    private final String lastAccepted;

    public ResearchRunStatus(boolean active, String activity, String query, String pageTitle, String url,
                             int pagesVisited, int acceptedSources, String stopReason, String lastAccepted) {
        this.active = active;
        this.activity = clean(activity);
        this.query = clean(query);
        this.pageTitle = clean(pageTitle);
        this.url = clean(url);
        this.pagesVisited = Math.max(0, pagesVisited);
        this.acceptedSources = Math.max(0, acceptedSources);
        this.stopReason = clean(stopReason);
        this.lastAccepted = clean(lastAccepted);
    }

    /** Noch kein Lauf in diesem Projekt. */
    public static ResearchRunStatus idle() {
        return new ResearchRunStatus(false, "", "", "", "", 0, 0, "", "");
    }

    /** Ob gerade ein Lauf arbeitet. */
    public boolean isActive() {
        return active;
    }

    /** Ob es überhaupt einen Lauf gab (aktiv oder beendet). */
    public boolean hasRun() {
        return active || !activity.isEmpty();
    }

    public String activity() {
        return activity;
    }

    /** Wonach zuletzt gesucht wurde. */
    public String query() {
        return query;
    }

    /** Die Seite, die gerade gelesen wird (oder zuletzt gelesen wurde). */
    public String pageTitle() {
        return pageTitle;
    }

    public String url() {
        return url;
    }

    public int pagesVisited() {
        return pagesVisited;
    }

    public int acceptedSources() {
        return acceptedSources;
    }

    /** Der {@code ResearchStopReason} als Name; leer, solange der Lauf nicht beendet ist. */
    public String stopReason() {
        return stopReason;
    }

    /** Titel der zuletzt übernommenen Quelle. */
    public String lastAccepted() {
        return lastAccepted;
    }

    /** Derselbe Stand mit neuer Tätigkeit (Zähler, letzte Suche und letzte Quelle bleiben, wenn leer gemeldet). */
    public ResearchRunStatus next(String nextActivity, String nextQuery, String nextTitle, String nextUrl,
                                  int pages, int acceptedCount) {
        String token = clean(nextActivity);
        boolean starting = STARTED.equals(token);
        String accepted = lastAccepted;
        if (SOURCE_ACCEPTED.equals(token)) {
            accepted = clean(nextTitle).isEmpty() ? clean(nextUrl) : clean(nextTitle);
        } else if (starting) {
            accepted = "";
        }
        String searched = clean(nextQuery).isEmpty() && !starting ? query : nextQuery;
        return new ResearchRunStatus(true, token, searched, nextTitle, nextUrl, pages, acceptedCount, "", accepted);
    }

    /** Der beendete Lauf mit seinem Grund. */
    public ResearchRunStatus finished(String reason, int pages, int accepted) {
        return new ResearchRunStatus(false, FINISHED, query, pageTitle, url, pages, accepted, reason, lastAccepted);
    }

    /** Der Stoppgrund in Worten (askai arch {@code ResearchStopReason}). */
    public static String reasonText(String reason) {
        String name = clean(reason);
        if ("SUFFICIENT_EVIDENCE".equals(name)) {
            return "genug Belege";
        } else if ("PAGE_BUDGET_EXHAUSTED".equals(name)) {
            return "Seitenbudget erreicht";
        } else if ("SOURCE_BUDGET_EXHAUSTED".equals(name)) {
            return "Quellenbudget erreicht";
        } else if ("TOOL_BUDGET_EXHAUSTED".equals(name)) {
            return "Werkzeugbudget erreicht";
        } else if ("TIME_BUDGET_EXHAUSTED".equals(name)) {
            return "Zeitbudget erreicht";
        } else if ("NO_RELEVANT_PATHS".equals(name) || "NO_SEMANTIC_MATCHES".equals(name)) {
            return "keine weiteren passenden Treffer";
        } else if ("USER_CANCELLED".equals(name)) {
            return "abgebrochen";
        } else if ("MCP_UNAVAILABLE".equals(name)) {
            return "Wissenssuche nicht erreichbar";
        } else if ("ERROR_BUDGET_EXHAUSTED".equals(name) || "SEARCH_TECHNICAL_PROBLEM".equals(name)) {
            return "zu viele Fehler bei der Suche";
        } else if ("SCOPE_CONTROL_UNAVAILABLE".equals(name)) {
            return "Weidezaun nicht prüfbar";
        }
        return name.isEmpty() ? "" : name;
    }

    /** Eine Zeile für die Oberfläche: was der Lauf gerade tut bzw. wie er endete. */
    public String describe() {
        if (!hasRun()) {
            return "";
        }
        String counters = pagesVisited + (pagesVisited == 1 ? " Seite" : " Seiten") + " gelesen, "
                + acceptedSources + (acceptedSources == 1 ? " Quelle" : " Quellen") + " übernommen";
        if (!active) {
            String why = reasonText(stopReason);
            return "Recherche beendet" + (why.isEmpty() ? "" : " (" + why + ")") + ": " + counters + ".";
        }
        String now;
        if (SEARCHING.equals(activity)) {
            now = "Suche nach „" + query + "“";
        } else if (READING_PAGE.equals(activity)) {
            now = "Lese „" + label() + "“";
        } else if (SOURCE_ACCEPTED.equals(activity)) {
            now = "Übernommen: „" + label() + "“";
        } else if (PAGE_SKIPPED.equals(activity)) {
            now = "Übersprungen: „" + label() + "“";
        } else {
            now = "Recherche startet";
        }
        return now + " · " + counters;
    }

    private String label() {
        return pageTitle.isEmpty() ? url : pageTitle;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    @Override
    public String toString() {
        return describe();
    }
}
