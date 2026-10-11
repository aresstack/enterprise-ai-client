package com.aresstack.enterpriseai.acp.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Was ein Turn von den Host-Aufrufen zeigt (askai arch {@code ResearchAgentSession.applyRunLog},
 * {@code updateConceptToolBubble}, {@code finishConceptToolBubble}):
 * <ul>
 *   <li>Solange eine Recherche-Karte offen ist, erscheinen keine Einzelwolken: Suche, gelesene Seiten und
 *       übernommene Quellen stehen in der Karte (askai: im Lauf keine Einzelwerkzeug-Blasen).</li>
 *   <li>Die Konzept- und Weidezaun-Werkzeuge eines Turns sammeln sich in <b>einer</b> Denkwolke mit einer Zeile
 *       je Schritt, die mit dem Turn endet („N Werkzeug-Schritte“).</li>
 *   <li>Jeder andere sichtbare Aufruf ist eine eigene Wolke; ein Fehler beendet sie mit seinem Fehlertext.</li>
 * </ul>
 */
final class TurnToolView implements ResearchHostControl.ToolObserver {

    static final String CONCEPT_TITLE = "Werkzeug-Schritte an Konzept & Themenraum …";
    /** Art der Recherche-Karte ({@link ResearchCard}); solange eine offen ist, schweigen die Einzelwolken. */
    static final String CARD_KIND = "research";

    private static final AtomicLong CONCEPT_IDS = new AtomicLong();

    private final ResearchAgentTurns.Output output;
    private final ToolNarration narration;
    private final Set<String> visible = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Set<String> openCards = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final List<String> conceptLines = new ArrayList<String>();
    private String conceptCloud;

    TurnToolView(ResearchAgentTurns.Output output, ToolNarration narration) {
        this.output = output;
        this.narration = narration;
    }

    /** Eine Recherche-Karte beginnt ({@code toolStarted} mit {@link #CARD_KIND}). */
    void cardStarted(String callId, String kind) {
        if (CARD_KIND.equals(kind)) {
            openCards.add(callId);
        }
    }

    /** Ein Aufruf des Turns endet; war es eine Karte, sind Einzelwolken wieder sichtbar. */
    void cardFinished(String callId) {
        openCards.remove(callId);
    }

    boolean isCardOpen() {
        return !openCards.isEmpty();
    }

    static boolean isConceptTool(String toolName) {
        return toolName != null && (toolName.startsWith("concept_") || "exclude_topic".equals(toolName)
                || "resolve_concept_conflict".equals(toolName) || "offer_searches".equals(toolName));
    }

    @Override
    public void started(String callId, String toolName, Map<String, Object> args) {
        if (isConceptTool(toolName)) {
            return; // erscheint mit seinem Ergebnis als Zeile der Konzept-Wolke
        }
        ToolNarration.Visible shown = narration.describe(toolName, args);
        if (shown == null || output.isCancelled() || isCardOpen()) {
            return;
        }
        visible.add(callId);
        output.toolStarted(callId, shown.title, shown.kind, shown.input);
    }

    @Override
    public void finished(String callId, String toolName, Map<String, Object> args, String result, String failure) {
        String summary = narration.summarize(toolName, result);
        boolean failed = failure != null || ToolNarration.isFailureText(result);
        String failureText = failed ? ToolNarration.shorten(failure != null ? failure : result) : "";
        if (isConceptTool(toolName)) {
            ToolNarration.Visible shown = narration.describe(toolName, args);
            if (shown != null && !"concept_read".equals(toolName)) {
                conceptStep(shown.title + (shown.input.isEmpty() ? "" : " „" + shown.input + "“")
                        + ": " + (failed ? "fehlgeschlagen: " + failureText : summary));
            }
            return;
        }
        if (!visible.remove(callId)) {
            return;
        }
        output.toolFinished(callId, !failed, failed ? failureText : summary);
    }

    private synchronized void conceptStep(String line) {
        if (output.isCancelled()) {
            return;
        }
        conceptLines.add(line);
        StringBuilder body = new StringBuilder();
        for (String step : conceptLines) {
            body.append("- ").append(step).append('\n');
        }
        if (conceptCloud == null) {
            conceptCloud = "concept-tools-" + CONCEPT_IDS.incrementAndGet();
            output.toolStarted(conceptCloud, CONCEPT_TITLE, "think", body.toString().trim());
        } else {
            output.toolUpdated(conceptCloud, CONCEPT_TITLE, body.toString().trim());
        }
    }

    /** Ende des Turns auf jedem Weg: die Konzept-Wolke bleibt nie offen stehen. */
    synchronized void turnFinished() {
        if (conceptCloud != null) {
            int steps = conceptLines.size();
            output.toolFinished(conceptCloud, true, steps == 1 ? "1 Werkzeug-Schritt" : steps + " Werkzeug-Schritte");
        }
        conceptCloud = null;
        conceptLines.clear();
    }
}
