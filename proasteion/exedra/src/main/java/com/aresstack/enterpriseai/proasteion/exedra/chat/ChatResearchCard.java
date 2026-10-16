package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Recherchekarte der automatischen Wissenssuche im normalen Chat: dieselbe gelbe Werkzeugwolke wie die
 * Recherche-Karte des Agenten ({@code ResearchCard} in research-agent-acp), in place aktualisiert über
 * {@link ChatShellModel#startActivity}. Sie zeigt je Suche Anfrage und Trefferzahl (die Nachsuchen als weitere
 * Suchschritte), darunter den laufenden Schritt und einen Balken über die Suchen des Turns. Bei der Live-Suche zeigt
 * sie die Treffer in den Quellen und wie viele davon geladen und lokal durchsuchbar sind; Passagen und Embeddings
 * zeigt sie nicht, denn auf die wartet der Chat nicht.
 *
 * <p>Oben steht der Trichter des Turns (#155/#156): „57 Seiten geprüft · 8 relevant · 3 verwendet“, live aus dem
 * {@link KnowledgeFunnel} des Use Case, nie hier neu berechnet und nie aus der Gesamttrefferzahl einer Quelle. Der
 * Balken zeigt Arbeitsfortschritt (Suchen des Turns mal ihre Arbeitsphasen), nie das Verhältnis relevant / geprüft.
 *
 * <p>Nur auf dem UI-Thread; jede Methode schreibt sofort ins Model, solange die Karte offen ist.
 */
final class ChatResearchCard {

    static final String KIND = "research";
    static final String TITLE = "Internes Wissen durchsuchen";
    static final String STARTING = "Suche startet …";
    static final String SEARCHING = "Suche läuft …";
    static final String LIVE_SEARCH = "Live-Suche in den Quellen …";
    static final String KEYWORD_SEARCH = "Volltextsuche …";
    static final String SEMANTIC_SEARCH = "Semantische Suche …";
    static final String CHECKING = "Prüfe Evidenz …";
    static final String MORE_PAGES = "Lese weitere Treffer …";
    static final String NOT_ENOUGH = "Kontext reicht noch nicht, weitere Suche.";
    static final String PREPARING = "Antwort wird erstellt …";
    static final String EVIDENCE_SUFFICIENT = "Evidenz ausreichend";
    static final String CANCELLING = "Wird abgebrochen …";
    static final String FAILED_LINE = "Suche ausgefallen";

    private final ChatShellModel model;
    private final String key;
    private final List<String> lines = new ArrayList<String>();
    private String current = STARTING;
    private int searches;
    private int completed;
    private int max = 1;
    private int hits;
    private int sources = -1;
    private boolean failed;
    private boolean cancelled;
    private boolean contextDone;
    private KnowledgeFunnel funnel;
    /** Work steps done so far, in units of {@link #PHASES} per search; never moves backwards. */
    private int worked;
    private static final int PHASES = FunnelPhase.DONE.ordinal();
    private boolean open;
    private boolean opened;

    ChatResearchCard(ChatShellModel model, String key) {
        this.model = model;
        this.key = key;
    }

    String key() {
        return key;
    }

    boolean isOpen() {
        return open;
    }

    /** Show the card with the first research step of the turn; once only, a finished card stays finished. */
    void open() {
        if (opened) {
            return;
        }
        opened = true;
        open = true;
        model.startActivity(key, TITLE, KIND, body());
    }

    void searchStarted(String query, int number, int maxSearches) {
        if (number > 1) {
            lines.add(NOT_ENOUGH);
        }
        searches = number;
        max = Math.max(1, maxSearches);
        lines.add("Suche " + number + " / " + max + ": „" + oneLine(query) + "“");
        current = SEARCHING;
        update();
    }

    void step(String text) {
        current = text;
        update();
    }

    /** „10 Treffer in den Quellen“: die Live-Suche hat geantwortet, die Treffer werden geladen. */
    void liveHitsFound(int count) {
        lines.add(count + " Treffer in den Quellen");
        current = "";
        update();
    }

    /** „Lade 1/4 …“, bis die abgewarteten Treffer lokal durchsuchbar sind, dann „Quelle lokal verfügbar“. */
    void pagesAvailable(int available, int awaited) {
        if (awaited <= 0) {
            return;
        }
        if (available >= awaited) {
            lines.add(available == 1 ? "Quelle lokal verfügbar" : available + " Quellen lokal verfügbar");
            current = "";
        } else {
            current = "Lade " + (available + 1) + "/" + awaited + " …";
        }
        update();
    }

    /** Das Modell prüft {@code count} Treffer gegen die Frage. */
    void ranking(int count) {
        current = "Prüfe Relevanz von " + count + (count == 1 ? " Treffer …" : " Treffern …");
        update();
    }

    /**
     * Die Bewertung einer Kandidatenrunde ist fertig. Keine eigene Zeile: wie viele Quellen relevant sind, zeigt der
     * Trichter oben, sonst stünden zwei verschiedene Relevanzzahlen in der Karte.
     */
    void ranked(int candidates, int relevant) {
        current = "";
        update();
    }

    /**
     * Der Trichter des Turns hat sich geändert: Zählerzeile und Balken folgen ihm. Gleiche Zahlen und Phase schreiben
     * nichts neu, damit die Karte nicht flackert.
     */
    void funnel(KnowledgeFunnel changed) {
        if (changed == null) {
            return;
        }
        boolean same = funnel != null && funnel.examined().size() == changed.examined().size()
                && funnel.relevant().size() == changed.relevant().size()
                && funnel.used().size() == changed.used().size() && funnel.phase() == changed.phase();
        funnel = changed;
        if (changed.phase() != FunnelPhase.DONE) {
            worked = Math.max(worked, Math.max(0, searches - 1) * PHASES + changed.phase().ordinal());
        }
        if (!same) {
            update();
        }
    }

    /** „57 Seiten geprüft · 8 relevant · 3 verwendet“; leer, solange noch nichts geprüft wurde. */
    String funnelLine() {
        if (funnel == null || funnel.examined().isEmpty()) {
            return "";
        }
        int examined = funnel.examined().size();
        StringBuilder text = new StringBuilder().append(examined).append(examined == 1 ? " Seite" : " Seiten")
                .append(" geprüft · ").append(funnel.relevant().size()).append(" relevant");
        if (!funnel.used().isEmpty()) {
            text.append(" · ").append(funnel.used().size()).append(" verwendet");
        }
        return text.toString();
    }

    /** „Evidenz ausreichend“ bzw. „1 Quelle: nicht ausreichend“. */
    void evidenceChecked(int contextSources, boolean sufficient) {
        lines.add(sufficient ? EVIDENCE_SUFFICIENT : contextSources + (contextSources == 1 ? " Quelle: " : " Quellen: ")
                + "nicht ausreichend");
        current = "";
        update();
    }

    void hitsFound(int count) {
        hits += Math.max(0, count);
        completed = searches;
        lines.add("Treffer: " + count);
        current = "";
        update();
    }

    void searchFailed() {
        completed = searches;
        failed = true;
        lines.add(FAILED_LINE);
        current = "";
        update();
    }

    /** Die Suche ist beendet; der Balken steht voll, auch wenn weniger Suchen als erlaubt nötig waren. */
    void contextReady(int contextSources) {
        sources = contextSources;
        completed = max;
        contextDone = true;
        current = PREPARING;
        update();
    }

    void cancelling() {
        cancelled = true;
        current = CANCELLING;
        update();
    }

    /** Die Wolke platzt mit ihrer Bilanz (Erfolg) bzw. als Fehler, wenn alle Suchen ausgefallen sind. */
    void finish() {
        if (!open) {
            return;
        }
        open = false;
        boolean succeeded = !(failed && hits == 0);
        model.finishActivity(key, succeeded, summary());
    }

    /**
     * „Recherche abgeschlossen · 57 Seiten geprüft · 8 relevante Quellen · 3 verwendet“; abgebrochen oder teilweise
     * mit den bis dahin ehrlich erreichten Zahlen. Ohne Trichter: „abgeschlossen: 2 Suchen, 11 Treffer, 6 Quellen im
     * Kontext“.
     */
    String summary() {
        boolean counted = funnel != null && !funnel.examined().isEmpty();
        if (cancelled && !counted) {
            return "Recherche abgebrochen";
        }
        if (failed && hits == 0 && !counted) {
            return "Wissenssuche ausgefallen";
        }
        if (funnel != null && !funnel.examined().isEmpty()) {
            int examined = funnel.examined().size();
            int relevant = funnel.relevant().size();
            // the answer may start before the final funnel arrives: only a finished incomplete funnel is partial
            boolean partial = failed || funnel.phase() == FunnelPhase.DONE && !funnel.complete();
            String state = cancelled ? "Recherche abgebrochen"
                    : partial ? "Recherche teilweise abgeschlossen" : "Recherche abgeschlossen";
            return state + " · " + examined + (examined == 1 ? " Seite" : " Seiten") + " geprüft · " + relevant
                    + (relevant == 1 ? " relevante Quelle" : " relevante Quellen") + " · " + funnel.used().size()
                    + " verwendet";
        }
        StringBuilder text = new StringBuilder("abgeschlossen: ").append(searches)
                .append(searches == 1 ? " Suche, " : " Suchen, ").append(hits).append(" Treffer");
        if (sources >= 0) {
            text.append(", ").append(sources).append(sources == 1 ? " Quelle" : " Quellen").append(" im Kontext");
        }
        return text.toString();
    }

    String body() {
        StringBuilder text = new StringBuilder(funnelLine());
        for (String line : lines) {
            text.append(text.length() == 0 ? "" : "\n").append(line);
        }
        if (!current.isEmpty()) {
            text.append(text.length() == 0 ? "" : "\n").append(current);
        }
        if (funnel == null) {
            text.append(" [[bar:").append(Math.min(completed, max)).append('/').append(max).append("]]");
        } else {
            int total = max * PHASES;
            int done = contextDone ? total : Math.min(worked, total);
            text.append(" [[bar:").append(done).append('/').append(total).append("]]");
        }
        return text.toString();
    }

    /**
     * Takes over the final funnel of the turn (#156): the balance must show the definitive counts, "used" in
     * particular, even when the card already burst with the first answer text.
     */
    void settle(KnowledgeFunnel last) {
        funnel(last);
        if (opened && !open) {
            model.updateActivitySummary(key, summary());
        }
    }

    private void update() {
        if (open && model.isActivityRunning(key)) {
            model.startActivity(key, TITLE, KIND, body());
        }
    }

    private static String oneLine(String query) {
        String text = query == null ? "" : query.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= 120 ? text : text.substring(0, 120) + " …";
    }
}
