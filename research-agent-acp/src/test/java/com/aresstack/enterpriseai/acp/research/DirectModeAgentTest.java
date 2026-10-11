package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCallException;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolClient;
import com.aresstack.enterpriseai.research.runtime.team.PhaseAssistantProfileRegistry;
import com.aresstack.enterpriseai.research.runtime.team.PhaseContextAssembler;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Direktmodus ohne Konzept über den ganzen Agenten-Turn: der Nutzer fragt direkt, der Agent sucht mit
 * knowledge_search, liest mit knowledge_read, notiert die Quelle im Projekt und antwortet mit Beleg. Jeder sichtbare
 * Host-Aufruf wird zur Werkzeugblase (Start und Ergebnis). Host-Werkzeuge und Modell sind Fakes hinter MCP.
 */
public class DirectModeAgentTest {

    static final String PAGE = "confluence:wiki/page/1001";
    static final String SEARCH_HIT = "[1] IAR Übersicht\nid: " + PAGE + "\nurl: \n"
            + "IAR bündelt die Auswertung der Anträge.";
    static final String PAGE_TEXT = "IAR Übersicht\n\nIAR bündelt die Auswertung der Anträge.\n\n"
            + "Zuständig ist das Team Auswertung; der Pilot läuft im Frühjahr.";

    /** Der MCP-Endpoint des Hosts mit festen Antworten; das Hauptmodell läuft über main_model_complete. */
    static class FakeEndpoint implements McpToolClient {
        final List<String> calls = new ArrayList<String>();
        final Map<String, Map<String, Object>> lastArgs = new HashMap<String, Map<String, Object>>();
        final Map<String, String> answers = new HashMap<String, String>();
        String phase = "scoping";
        String state = "dialog";
        String plan = "{\"search\": true, \"queries\": [\"IAR\"]}";
        String modelAnswer = "IAR bündelt die Auswertung der Anträge. [P1] Der Pilot läuft im Frühjahr. [P1]";
        final Map<String, String> tools = new HashMap<String, String>();

        FakeEndpoint() {
            tools.put(KnowledgePages.SEARCH_TOOL, "Volltextsuche");
            tools.put(KnowledgePages.READ_TOOL, "Seite lesen");
        }

        @Override
        public Map<String, String> listTools() {
            return tools;
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
            calls.add(toolName);
            lastArgs.put(toolName, arguments);
            if ("research_status".equals(toolName)) {
                return "{\"phase\":\"" + phase + "\",\"state\":\"" + state + "\",\"model\":\"fake\"}";
            }
            if ("main_model_complete".equals(toolName)) {
                String messages = String.valueOf(arguments.get("messages_json"));
                return messages.contains("Antworte NUR mit JSON") ? plan : modelAnswer;
            }
            if ("search_scope_begin".equals(toolName)) {
                return answers.containsKey(toolName) ? answers.get(toolName) : "INACTIVE";
            }
            if (KnowledgePages.SEARCH_TOOL.equals(toolName)) {
                return answers.containsKey(toolName) ? answers.get(toolName) : SEARCH_HIT;
            }
            if (KnowledgePages.READ_TOOL.equals(toolName)) {
                return PAGE_TEXT;
            }
            if ("project_source_add".equals(toolName)) {
                return "source_id=" + arguments.get("capture_id");
            }
            String answer = answers.get(toolName);
            return answer == null ? "" : answer;
        }

        @Override
        public void close() {
        }
    }

    /** Was der Client per ACP sieht: Nachrichten und Werkzeugblasen. */
    static final class Screen implements ResearchAgentTurns.Output {
        final List<String> messages = new ArrayList<String>();
        final Map<String, String> started = new LinkedHashMap<String, String>();
        final Map<String, String> inputs = new HashMap<String, String>();
        final Map<String, String> kinds = new HashMap<String, String>();
        final Map<String, String> finished = new HashMap<String, String>();
        final Map<String, Boolean> succeeded = new HashMap<String, Boolean>();

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void message(String text) {
            messages.add(text);
        }

        @Override
        public void toolStarted(String callId, String title, String kind, String input) {
            started.put(callId, title);
            inputs.put(callId, input);
            kinds.put(callId, kind);
        }

        final List<String> updates = new ArrayList<String>();

        @Override
        public void toolUpdated(String callId, String title, String input) {
            started.put(callId, title);
            inputs.put(callId, input);
            updates.add(title + "|" + input);
        }

        @Override
        public void toolFinished(String callId, boolean ok, String summary) {
            finished.put(callId, summary);
            succeeded.put(callId, Boolean.valueOf(ok));
        }

        String callOf(String title) {
            for (Map.Entry<String, String> entry : started.entrySet()) {
                if (entry.getValue().equals(title)) {
                    return entry.getKey();
                }
            }
            throw new AssertionError("keine Blase " + title + " in " + started.values());
        }

        String all() {
            StringBuilder text = new StringBuilder();
            for (String message : messages) {
                text.append(message).append('\n');
            }
            return text.toString();
        }
    }

    static ResearchAgentTurns agent(FakeEndpoint endpoint) {
        ResearchHostControl control = new ResearchHostControl(endpoint);
        McpMainModelChat model = new McpMainModelChat(control);
        ResearchTeamAgent team = new ResearchTeamAgent(model, PhaseAssistantProfileRegistry.defaults(false, false),
                new PhaseContextAssembler(new PhaseContextAssembler.CurrentLanguage() {
                    @Override
                    public String displayName() {
                        return "German";
                    }
                }));
        return new ResearchAgentTurns(control, team, model, () -> false,
                new ResearchRun(control, Collections.<String, String>emptyMap(), new AtomicBoolean(false)));
    }

    @Test
    public void directQuestionSearchesReadsNotesAndAnswersWithSourcesWithoutAConcept() {
        FakeEndpoint endpoint = new FakeEndpoint();
        Screen screen = new Screen();
        agent(endpoint).run("Kannst du mir was über IAR aus Confluence finden?", screen);

        assertTrue(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
        assertTrue(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.READ_TOOL));
        assertEquals(PAGE, endpoint.lastArgs.get(KnowledgePages.READ_TOOL).get("id"));
        assertEquals(PAGE, endpoint.lastArgs.get("project_source_add").get("capture_id"));
        assertEquals("IAR", endpoint.lastArgs.get(KnowledgePages.SEARCH_TOOL).get("query"));

        String text = screen.all();
        assertTrue(text, text.contains("Ich suche in den aktivierten Wissensquellen nach: \u201eIAR\u201c"));
        assertTrue(text, text.contains("Auswertung der Anträge. [1]"));
        assertTrue(text, text.contains("[1] IAR Übersicht"));
        assertFalse(text, text.toLowerCase(java.util.Locale.ROOT).contains("keinen zugriff"));
        assertFalse(text, text.toLowerCase(java.util.Locale.ROOT).contains("kein zugriff"));
    }

    @Test
    public void everyVisibleHostCallBecomesAToolBubbleWithItsResult() {
        FakeEndpoint endpoint = new FakeEndpoint();
        Screen screen = new Screen();
        agent(endpoint).run("Kannst du mir was über IAR aus Confluence finden?", screen);

        String search = screen.callOf("Suche in den Wissensquellen");
        assertEquals("search", screen.kinds.get(search));
        assertEquals("IAR", screen.inputs.get(search));
        assertEquals("1 Treffer: IAR Übersicht", screen.finished.get(search));

        String read = screen.callOf("Lese Seite");
        assertEquals("read", screen.kinds.get(read));
        assertEquals("IAR Übersicht", screen.inputs.get(read));
        assertTrue(screen.finished.get(read), screen.finished.get(read).startsWith("Gelesen ("));

        String note = screen.callOf("Quelle notieren");
        assertEquals("IAR Übersicht", screen.inputs.get(note));

        String think = screen.callOf("Denke nach");
        assertEquals("think", screen.kinds.get(think));

        assertEquals("jede Blase endet", screen.started.keySet(), screen.finished.keySet());
        for (Boolean ok : screen.succeeded.values()) {
            assertTrue(ok.booleanValue());
        }
        // interne Aufrufe bleiben unsichtbar
        assertFalse(screen.started.containsValue("research_status"));
        assertEquals(endpoint.calls.size() > screen.started.size(), true);
    }

    /** Phase 1 ist ein interaktiver Recherche-Chat: normale Fragen suchen sofort, ohne Konzept und Phasenwechsel. */
    @Test
    public void ordinaryQuestionsInTheConceptPhaseSearchImmediately() {
        for (String question : new String[]{"Was ist IAR?", "Welche Meilensteine gibt es?", "Wer arbeitet daran?",
                "Suche mir etwas zur Rückschnittstelle."}) {
            FakeEndpoint endpoint = new FakeEndpoint();
            endpoint.state = "new";
            Screen screen = new Screen();
            agent(endpoint).run(question, screen);
            assertTrue(question, endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
            assertTrue(question, endpoint.calls.contains(KnowledgePages.READ_TOOL));
            assertTrue(question + ": " + screen.all(), screen.all().contains("[1] IAR Übersicht"));
        }
    }

    /** askai-arch-Fortschrittskarte: eine Karte je Recherche, in place mit Treffern, Seiten n/4, Quellen. */
    @Test
    public void researchCardCountsHitsPagesAgainstTheBudgetAndSources() {
        FakeEndpoint endpoint = new FakeEndpoint();
        StringBuilder six = new StringBuilder();
        for (int i = 1; i <= 6; i++) {
            six.append("[").append(i).append("] IAR Seite ").append(i).append("\nid: confluence:wiki/page/100")
                    .append(i).append("\nurl: \nAuszug ").append(i).append("\n");
        }
        endpoint.answers.put(KnowledgePages.SEARCH_TOOL, six.toString());
        Screen screen = new Screen();
        agent(endpoint).run("Suche in Confluence nach IAR.", screen);

        int reads = 0;
        for (String call : endpoint.calls) {
            reads += KnowledgePages.READ_TOOL.equals(call) ? 1 : 0;
        }
        assertEquals("Seitenbudget der Konzeptphase", 4, reads);
        String card = screen.callOf("Recherche in Confluence");
        assertEquals("research", screen.kinds.get(card));
        String body = screen.inputs.get(card);
        assertTrue(body, body.contains("Suche nach: \u201eIAR\u201c"));
        assertTrue(body, body.contains("Wissensquelle: Confluence"));
        assertTrue(body, body.contains("Treffer: 6"));
        assertTrue(body, body.contains("Seiten: 4 / 4"));
        assertTrue(body, body.contains("Quellen: 4 übernommen"));
        assertTrue(body, body.endsWith("[[bar:4/4]]"));
        boolean sawCurrent = false;
        boolean sawHalf = false;
        for (String update : screen.updates) {
            sawCurrent |= update.contains("Aktuell: \u201eIAR Seite 2\u201c");
            sawHalf |= update.contains("Seiten: 2 / 4") && update.contains("[[bar:2/4]]");
        }
        assertTrue(screen.updates.toString(), sawCurrent);
        assertTrue(screen.updates.toString(), sawHalf);
        assertEquals("abgeschlossen: 4 Seiten gelesen, 6 Treffer, 4 Quellen übernommen", screen.finished.get(card));
        assertTrue(screen.succeeded.get(card).booleanValue());
    }

    @Test
    public void anExplicitSearchRequestSearchesEvenWhenTheModelDeclines() {
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.plan = "{\"search\": false, \"queries\": []}";
        agent(endpoint).run("Recherchiere zu IAR.", new Screen());
        assertTrue(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
        assertTrue(DirectAnswers.asksForSearch("Suche in Confluence nach IAR."));
        assertTrue(DirectAnswers.asksForSearch("Finde Informationen zu IAR"));
        assertFalse(DirectAnswers.asksForSearch("Lege eine Karte Zuständigkeiten an."));
        assertFalse(DirectAnswers.asksForSearch("Ich versuche es später"));
    }

    @Test
    public void conceptOnlyMessageStaysInTheConceptDialogWithoutSearching() {
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.plan = "{\"search\": false, \"queries\": []}";
        agent(endpoint).run("Lege eine Karte Zuständigkeiten an.", new Screen());
        assertFalse(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
    }

    @Test
    public void withoutTheSearchToolTheTurnStaysInTheConceptDialog() {
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.tools.clear();
        agent(endpoint).run("Was ist IAR?", new Screen());
        assertFalse(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
    }

    /** Ein abgeschaltetes Suchwerkzeug wird bei einem Suchauftrag sichtbar gemacht, nie stumm übergangen. */
    @Test
    public void anExplicitSearchWithTheToolSwitchedOffSaysSo() {
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.tools.clear();
        Screen screen = new Screen();
        agent(endpoint).run("Suche in Confluence nach IAR.", screen);
        assertFalse(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
        assertTrue(screen.all(), screen.all().contains(ResearchAgentTurns.SEARCH_UNAVAILABLE));
    }

    @Test
    public void aFailingSearchIsReportedNotDenied() {
        FakeEndpoint endpoint = new FakeEndpoint() {
            @Override
            public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
                if (KnowledgePages.SEARCH_TOOL.equals(toolName)) {
                    calls.add(toolName);
                    throw new McpToolCallException("Quelle nicht erreichbar", false);
                }
                return super.callTool(toolName, arguments);
            }
        };
        Screen screen = new Screen();
        agent(endpoint).run("Was ist IAR?", screen);
        String text = screen.all();
        assertTrue(text, text.contains("Die Suche in den Wissensquellen ist fehlgeschlagen: Quelle nicht erreichbar"));
        String search = screen.callOf("Suche in den Wissensquellen");
        assertFalse(screen.succeeded.get(search).booleanValue());
        assertEquals("Quelle nicht erreichbar", screen.finished.get(search));
    }

    @Test
    public void noHitsSaysSoInsteadOfDenyingAccess() {
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.answers.put(KnowledgePages.SEARCH_TOOL, "");
        Screen screen = new Screen();
        agent(endpoint).run("Was ist IAR?", screen);
        String text = screen.all();
        assertTrue(text, text.contains("nichts gefunden"));
        assertFalse(text, text.toLowerCase(java.util.Locale.ROOT).contains("zugriff"));
        assertEquals("Keine Treffer", screen.finished.get(screen.callOf("Suche in den Wissensquellen")));
    }

    @Test
    public void fenceDropsExcludedHits() {
        FakeEndpoint endpoint = new FakeEndpoint();
        endpoint.answers.put(KnowledgePages.SEARCH_TOOL, SEARCH_HIT + "\n[2] IAR Kantine\nid: "
                + "confluence:wiki/page/1002\nurl: \nSpeiseplan");
        endpoint.answers.put("search_scope_begin", "ACTIVE handle=h1");
        endpoint.answers.put("search_scope_evaluate", "EVALUATED\nIN id=" + PAGE
                + "\nOUT nearest=\"Kantine\" id=confluence:wiki/page/1002");
        agent(endpoint).run("Was ist IAR?", new Screen());
        assertEquals(PAGE, endpoint.lastArgs.get(KnowledgePages.READ_TOOL).get("id"));
        int reads = 0;
        for (String call : endpoint.calls) {
            reads += KnowledgePages.READ_TOOL.equals(call) ? 1 : 0;
        }
        assertEquals(1, reads);
        assertTrue(endpoint.calls.contains("search_scope_end"));
    }

    @Test
    public void planParsingAndKeywordFallback() {
        assertEquals(java.util.Arrays.asList("IAR", "IAR Zweck"),
                DirectAnswers.parsePlan("Hier: {\"search\":true,\"queries\":[\"IAR\",\"IAR Zweck\"]}"));
        assertTrue(DirectAnswers.parsePlan("{\"search\":false,\"queries\":[\"x\"]}").isEmpty());
        assertEquals(null, DirectAnswers.parsePlan("keine Ahnung"));
        List<String> keywords = DirectAnswers.keywordQueries("Kannst du mir was über IAR aus Confluence finden?");
        assertTrue(keywords.toString(), keywords.contains("IAR"));
        assertTrue(keywords.size() <= DirectAnswers.MAX_QUERIES);
    }
}
