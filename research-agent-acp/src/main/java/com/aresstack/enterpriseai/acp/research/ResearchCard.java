package com.aresstack.enterpriseai.acp.research;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Die eine Recherche-Karte einer interaktiven Suche, in place aktualisiert (askai arch: die Fortschrittskarte der
 * Websuche, {@code ResearchAgentSession.applyRunProgress} mit {@code ResearchPlaybook.progress*} und dem
 * Balken-Marker {@code [[bar:aktuell/gesamt]]} aus {@code ResearchAgentMain}). Sie zeigt, wonach in welcher
 * Wissensquelle gesucht wird, die Treffer, Seiten gelesen gegen das Seitenbudget, übernommene Quellen, die gerade
 * gelesene Seite und die zuletzt verarbeiteten Seiten; am Ende platzt sie mit der Bilanz. Dieselbe Karte zeigt den
 * autonomen Lauf der Phase Quellen ({@link #forRun}): dort kommen die Suchen nacheinander, Treffer zählt der Lauf
 * nicht, das Seitenbudget ist das des Laufs.
 */
final class ResearchCard {

    private static final AtomicLong IDS = new AtomicLong();
    private static final int HISTORY_LINES = 5;

    private final ResearchAgentTurns.Output output;
    private final String id = "research-" + IDS.incrementAndGet();
    private final int pageBudget;
    private final List<String> queries;
    /** Im autonomen Lauf gibt es keine Trefferzahl; die Zeile entfällt. */
    private final boolean showHits;
    private final Set<String> sources = new LinkedHashSet<String>();
    private final List<String> history = new ArrayList<String>();
    private String activity = "Suche läuft …";
    private String current = "";
    private int hits = -1;
    private int pagesRead;
    private int accepted;
    private boolean open;

    ResearchCard(ResearchAgentTurns.Output output, List<String> queries, int pageBudget) {
        this(output, queries, pageBudget, true);
    }

    private ResearchCard(ResearchAgentTurns.Output output, List<String> queries, int pageBudget, boolean showHits) {
        this.output = output;
        this.queries = new ArrayList<String>(queries);
        this.pageBudget = pageBudget;
        this.showHits = showHits;
    }

    /** Die Karte des autonomen Laufs; {@code pageBudget} 0 heißt unbegrenzt. */
    static ResearchCard forRun(ResearchAgentTurns.Output output, int pageBudget) {
        ResearchCard card = new ResearchCard(output, new ArrayList<String>(), pageBudget, false);
        card.activity = "Recherche startet …";
        return card;
    }

    /** Der Lauf sucht mit einer (weiteren) Anfrage. */
    void query(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (!trimmed.isEmpty() && !queries.contains(trimmed)) {
            queries.add(trimmed);
        }
        current = "";
        activity = "Suche läuft …";
        update();
    }

    /** Der Lauf hat eine Seite übernommen. */
    void accepted(String title, String id) {
        noteSource(id);
        remember("übernommen: „" + title + "“");
        current = "";
        activity = "Nächste Seite …";
        update();
    }

    /** Der Lauf hat eine Seite gelesen und als nicht passend verworfen. */
    void rejected(String title, String id) {
        noteSource(id);
        remember("nicht passend: „" + title + "“");
        current = "";
        activity = "Nächste Seite …";
        update();
    }

    /** Die Zähler des Laufs (gelesene Seiten, übernommene Quellen). */
    void counts(int pages, int acceptedSources) {
        pagesRead = pages;
        accepted = acceptedSources;
    }

    /** Der Lauf liest eine Seite; ihre Kennung verrät die Wissensquelle. */
    void reading(String title, String id) {
        noteSource(id);
        reading(title);
    }

    private void noteSource(String id) {
        String source = sourceNameOf(id);
        if (!source.isEmpty()) {
            sources.add(source);
        }
    }

    void searching() {
        open = true;
        output.toolStarted(id, title(), "research", body());
    }

    void found(List<KnowledgePages.Hit> hitList) {
        hits = hitList.size();
        for (KnowledgePages.Hit hit : hitList) {
            String source = sourceName(hit);
            if (!source.isEmpty()) {
                sources.add(source);
            }
        }
        activity = hits == 0 ? "Keine Treffer" : "Relevante Treffer ausgewählt";
        update();
    }

    void reading(String title) {
        current = title;
        activity = "Seite wird gelesen …";
        update();
    }

    void read(String title, boolean noted) {
        pagesRead++;
        if (noted) {
            accepted++;
        }
        remember((noted ? "übernommen: " : "gelesen: ") + "„" + title + "“");
        current = "";
        // Budget 0 = unbegrenzt: dann begrenzen nur die Treffer
        int limit = pageBudget > 0 ? Math.min(pageBudget, Math.max(hits, 0)) : Math.max(hits, 0);
        activity = pagesRead < limit ? "Nächste Seite …" : "Inhalte werden bewertet …";
        update();
    }

    void skipped(String title) {
        remember("nicht lesbar: „" + title + "“");
        update();
    }

    void answering() {
        current = "";
        activity = "Antwort wird geschrieben …";
        update();
    }

    void finish(boolean succeeded, String summary) {
        if (open) {
            open = false;
            output.toolFinished(id, succeeded, summary == null || summary.isEmpty() ? summary() : summary);
        }
    }

    /** Die Bilanz der fertigen Recherche. */
    String summary() {
        if (!showHits) {
            return "abgeschlossen: " + pagesRead + (pagesRead == 1 ? " Seite" : " Seiten") + " gelesen, "
                    + accepted + (accepted == 1 ? " Quelle" : " Quellen") + " übernommen";
        }
        return "abgeschlossen: " + pagesRead + (pagesRead == 1 ? " Seite" : " Seiten") + " gelesen, "
                + Math.max(hits, 0) + " Treffer, " + accepted + (accepted == 1 ? " Quelle" : " Quellen")
                + " übernommen";
    }

    private void update() {
        if (open) {
            output.toolUpdated(id, title(), body());
        }
    }

    private void remember(String line) {
        history.add(line);
        while (history.size() > HISTORY_LINES) {
            history.remove(0);
        }
    }

    /** „Recherche in Confluence“, sobald die Treffer ihre Quelle verraten; vorher in den Wissensquellen. */
    String title() {
        if (sources.size() == 1) {
            return "Recherche in " + sources.iterator().next();
        }
        return "Recherche in den Wissensquellen";
    }

    String body() {
        StringBuilder text = new StringBuilder("Suche nach: ");
        for (int i = 0; i < queries.size(); i++) {
            text.append(i == 0 ? "" : ", ").append('„').append(queries.get(i)).append('“');
        }
        if (!sources.isEmpty()) {
            text.append("\nWissensquelle: ").append(join(sources));
        }
        if (showHits) {
            text.append("\nTreffer: ").append(hits < 0 ? "…" : String.valueOf(hits));
        }
        // Budget 0 = unbegrenzt: „Seiten: 17“ ohne Nenner, nie „17 / 0“
        int budget = pageBudget <= 0 ? 0 : hits < 0 ? pageBudget : Math.min(pageBudget, hits);
        text.append("\nSeiten: ").append(pagesRead);
        if (budget > 0) {
            text.append(" / ").append(budget);
        } else {
            budget = Math.max(pagesRead, 1); // unbegrenzt: der Balken zeigt nur, dass gelesen wird
        }
        text.append("\nQuellen: ").append(accepted).append(" übernommen");
        if (!current.isEmpty()) {
            text.append("\nAktuell: „").append(current).append('“');
        }
        text.append('\n').append(activity);
        if (!history.isEmpty()) {
            text.append("\nZuletzt:");
            for (String line : history) {
                text.append("\n").append(line);
            }
        }
        text.append(" [[bar:").append(pagesRead).append('/').append(Math.max(budget, 1)).append("]]");
        return text.toString();
    }

    /** Die Wissensquelle eines Treffers aus seiner Kennung ({@code confluence:…}, {@code mediawiki:…}). */
    static String sourceName(KnowledgePages.Hit hit) {
        String source = hit.source == null ? "" : hit.source;
        return sourceNameOf(source.isEmpty() ? hit.id : source);
    }

    /** Die Wissensquelle aus einer Kennung oder Adresse ({@code knowledge:confluence:…}, {@code confluence:…}). */
    static String sourceNameOf(String idOrUrl) {
        String probe = idOrUrl == null ? "" : idOrUrl.trim().toLowerCase(Locale.ROOT);
        if (probe.startsWith("knowledge:")) {
            probe = probe.substring("knowledge:".length());
        }
        if (probe.startsWith("confluence")) {
            return "Confluence";
        }
        if (probe.startsWith("mediawiki") || probe.startsWith("wiki")) {
            return "MediaWiki";
        }
        if (probe.startsWith("sharepoint")) {
            return "SharePoint";
        }
        if (probe.startsWith("ndv")) {
            return "NDV";
        }
        if (probe.startsWith("ftp")) {
            return "FTP";
        }
        if (probe.startsWith("file")) {
            return "Dateien";
        }
        return "";
    }

    private static String join(Set<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            text.append(text.length() == 0 ? "" : ", ").append(value);
        }
        return text.toString();
    }
}
