package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;

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
 * <p>Nur auf dem UI-Thread; jede Methode schreibt sofort ins Model, solange die Karte offen ist.
 */
final class ChatResearchCard {

    static final String KIND = "research";
    static final String TITLE = "Recherche in den Wissensquellen";
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
    private boolean open;

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

    /** Die Karte erscheint (direkt nach dem Senden, vor dem ersten Suchschritt). */
    void open() {
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

    /** „2 geeignete Quellen (von 14 Treffern)“. */
    void ranked(int candidates, int relevant) {
        lines.add(relevant + (relevant == 1 ? " geeignete Quelle" : " geeignete Quellen") + " (von " + candidates
                + (candidates == 1 ? " Treffer)" : " Treffern)"));
        current = "";
        update();
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
        current = PREPARING;
        update();
    }

    void cancelling() {
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

    /** „abgeschlossen: 2 Suchen, 11 Treffer, 6 Quellen im Kontext“. */
    String summary() {
        if (failed && hits == 0) {
            return "Wissenssuche ausgefallen";
        }
        StringBuilder text = new StringBuilder("abgeschlossen: ").append(searches)
                .append(searches == 1 ? " Suche, " : " Suchen, ").append(hits).append(" Treffer");
        if (sources >= 0) {
            text.append(", ").append(sources).append(sources == 1 ? " Quelle" : " Quellen").append(" im Kontext");
        }
        return text.toString();
    }

    String body() {
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(text.length() == 0 ? "" : "\n").append(line);
        }
        if (!current.isEmpty()) {
            text.append(text.length() == 0 ? "" : "\n").append(current);
        }
        text.append(" [[bar:").append(Math.min(completed, max)).append('/').append(max).append("]]");
        return text.toString();
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
