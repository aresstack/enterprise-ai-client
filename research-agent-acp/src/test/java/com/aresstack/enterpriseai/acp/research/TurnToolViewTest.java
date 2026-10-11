package com.aresstack.enterpriseai.acp.research;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Senke der Host-Aufrufe eines Turns (askai arch {@code updateConceptToolBubble}/{@code finishConceptToolBubble},
 * {@code applyRunLog}): Konzept-Werkzeuge bündeln sich in einer Denkwolke, eine offene Recherche-Karte nimmt das
 * Notieren der Quellen auf (Suche und Lesen bleiben sichtbar), ein Fehler trägt seinen Text.
 */
public class TurnToolViewTest {

    @Test
    public void conceptToolsOfATurnShareOneThinkCloudThatEndsWithTheStepCount() {
        Recorder out = new Recorder();
        TurnToolView view = new TurnToolView(out, new ToolNarration());

        view.started("c1", "concept_add", args("name", "Ablauf"));
        view.finished("c1", "concept_add", args("name", "Ablauf"), "angelegt", null);
        view.started("c2", "exclude_topic", args("topic", "Preise"));
        view.finished("c2", "exclude_topic", args("topic", "Preise"), "ausgeschlossen", null);
        view.started("c3", "concept_rename", args("name", "Fluss"));
        view.finished("c3", "concept_rename", args("name", "Fluss"), "Tool failed: unbekannt", null);
        view.turnFinished();

        assertEquals("eine Wolke für alle Konzept-Schritte", 1, out.started.size());
        String cloud = out.started.get(0);
        assertEquals("think", out.kinds.get(cloud));
        assertEquals(TurnToolView.CONCEPT_TITLE, out.titles.get(cloud));
        assertEquals("- Konzept: Karte anlegen „Ablauf“: angelegt\n"
                + "- Weidezaun: Thema ausschließen „Preise“: ausgeschlossen\n"
                + "- Konzept: Karte umbenennen „Fluss“: fehlgeschlagen: Tool failed: unbekannt", out.text.get(cloud));
        assertEquals("3 Werkzeug-Schritte", out.finished.get(cloud));
        assertTrue(out.succeeded.get(cloud).booleanValue());
    }

    @Test
    public void anOpenResearchCardTakesTheSourceNotesButSearchAndReadStayVisible() {
        Recorder out = new Recorder();
        TurnToolView view = new TurnToolView(out, new ToolNarration());
        view.cardStarted("card-1", TurnToolView.CARD_KIND);

        view.started("s1", KnowledgePages.SEARCH_TOOL, args("query", "IAR"));
        view.finished("s1", KnowledgePages.SEARCH_TOOL, args("query", "IAR"), "", null);
        view.started("n1", "project_source_add", args("title", "IAR Übersicht"));
        view.finished("n1", "project_source_add", args("title", "IAR Übersicht"), "source_id=x", null);
        assertEquals("Suche sichtbar, Notiz in der Karte", Collections.singletonList("s1"), out.started);

        view.cardFinished("card-1");
        view.started("n2", "project_source_add", args("title", "IAR Planung"));
        assertEquals(java.util.Arrays.asList("s1", "n2"), out.started);
    }

    @Test
    public void aFailedToolEndsItsCloudWithTheErrorText() {
        Recorder out = new Recorder();
        TurnToolView view = new TurnToolView(out, new ToolNarration());

        view.started("s1", KnowledgePages.SEARCH_TOOL, args("query", "IAR"));
        view.finished("s1", KnowledgePages.SEARCH_TOOL, args("query", "IAR"), null, "Quelle nicht erreichbar");

        assertEquals(Boolean.FALSE, out.succeeded.get("s1"));
        assertEquals("Quelle nicht erreichbar", out.finished.get("s1"));
    }

    private static Map<String, Object> args(String key, String value) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put(key, value);
        return args;
    }

    private static final class Recorder implements ResearchAgentTurns.Output {
        final List<String> started = new ArrayList<String>();
        final Map<String, String> kinds = new HashMap<String, String>();
        final Map<String, String> titles = new HashMap<String, String>();
        final Map<String, String> text = new HashMap<String, String>();
        final Map<String, String> finished = new HashMap<String, String>();
        final Map<String, Boolean> succeeded = new HashMap<String, Boolean>();

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void message(String message) {
        }

        @Override
        public void toolStarted(String callId, String title, String kind, String input) {
            started.add(callId);
            kinds.put(callId, kind);
            titles.put(callId, title);
            text.put(callId, input);
        }

        @Override
        public void toolUpdated(String callId, String title, String input) {
            text.put(callId, input);
        }

        @Override
        public void toolFinished(String callId, boolean ok, String summary) {
            finished.put(callId, summary);
            succeeded.put(callId, Boolean.valueOf(ok));
        }
    }
}
