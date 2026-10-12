package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCallException;
import com.aresstack.enterpriseai.research.runtime.team.PhaseAssistantProfileRegistry;
import com.aresstack.enterpriseai.research.runtime.team.PhaseContextAssembler;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression „Was ist IAR?“: das Modell kennt aus eigenem Wissen ein gleichnamiges Entwicklungswerkzeug, die
 * Wissensquelle beschreibt dagegen eine interne Import- und Autorisierungsplattform. Gelbe Suchvorschläge dürfen
 * erst nach dem Lesen entstehen und nur aus dem Gelesenen; das Konzeptgespräch bietet nie selbst welche an.
 */
public class GroundedSearchTagsTest {

    static final String PAGE = "confluence:wiki/page/2002";
    static final String HIT = "[1] IAR Überblick\nid: " + PAGE + "\nurl: \n"
            + "IAR ist die Import- und Autorisierungsplattform.";
    static final String PAGE_TEXT = "IAR Überblick\n\nIAR ist die Import- und Autorisierungsplattform.\n\n"
            + "Sie bietet eine REST-Schnittstelle zu NIZZA und liefert per SFTP an ZAUF.";

    /** Was das Modell aus eigenem Wissen über „IAR“ sagt, ohne eine Seite gelesen zu haben. */
    static final String UNGROUNDED_OFFER = "{\"assistantMessage\":\"IAR ist eine Entwicklungsumgebung.\","
            + "\"conceptAction\":{\"type\":\"offer\",\"suggestions\":["
            + "{\"query\":\"IAR Embedded Workbench\",\"purpose\":\"Werkzeug\"},"
            + "{\"query\":\"IAR Compiler\",\"purpose\":\"Werkzeug\"}]}}";

    static final String SUGGESTIONS = "{\"suggestions\":["
            + "{\"query\":\"IAR REST-Schnittstelle\",\"purpose\":\"Schnittstellen\"},"
            + "{\"query\":\"IAR NIZZA\",\"purpose\":\"Anbindung\"},"
            + "{\"query\":\"IAR ZAUF\",\"purpose\":\"Lieferung\"},"
            + "{\"query\":\"IAR Embedded Workbench\",\"purpose\":\"Werkzeug\"},"
            + "{\"query\":\"IAR Compiler\",\"purpose\":\"Werkzeug\"},"
            + "{\"query\":\"GCC Vergleich\",\"purpose\":\"Vergleich\"}]}";

    /** Der Host: hält fest, in welcher Reihenfolge gesucht, gelesen, das Konzept gefragt und angeboten wird. */
    static final class Endpoint extends DirectModeAgentTest.FakeEndpoint {
        final List<String> events = new ArrayList<String>();
        final List<String> offered = new ArrayList<String>();
        String searchResult = HIT;
        int conceptTurns;

        Endpoint() {
            tools.put(GroundedSearchTags.OFFER_TOOL, "Suchvorschläge anbieten");
            tools.put("concept_read", "Konzept lesen");
            plan = "{\"search\": true, \"queries\": [\"IAR\"]}";
            modelAnswer = "IAR ist die Import- und Autorisierungsplattform [P1], mit REST zu NIZZA und SFTP zu "
                    + "ZAUF. [P1]";
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
            if ("main_model_complete".equals(toolName)) {
                String messages = String.valueOf(arguments.get("messages_json"));
                if (messages.contains("Anschluss-Suchen")) {
                    events.add("model:tags");
                    calls.add(toolName);
                    return SUGGESTIONS;
                }
                if (!messages.contains("Antworte NUR mit JSON") && !messages.contains("AUSZÜGE")) {
                    events.add("model:concept");
                    calls.add(toolName);
                    return conceptTurns++ == 0 ? UNGROUNDED_OFFER : "{\"assistantMessage\":\"Notiert.\"}";
                }
            }
            if (KnowledgePages.SEARCH_TOOL.equals(toolName)) {
                answers.put(toolName, searchResult);
            }
            if (GroundedSearchTags.OFFER_TOOL.equals(toolName)) {
                offered.add(String.valueOf(arguments.get("suggestions_json")));
            }
            events.add(toolName);
            String answer = super.callTool(toolName, arguments);
            return KnowledgePages.READ_TOOL.equals(toolName) ? PAGE_TEXT : answer;
        }
    }

    static ResearchAgentTurns agent(Endpoint endpoint) {
        ResearchHostControl control = new ResearchHostControl(endpoint);
        McpMainModelChat model = new McpMainModelChat(control);
        ResearchTeamAgent team = new ResearchTeamAgent(model, PhaseAssistantProfileRegistry.defaults(false, true),
                new PhaseContextAssembler(new PhaseContextAssembler.CurrentLanguage() {
                    @Override
                    public String displayName() {
                        return "German";
                    }
                }));
        return new ResearchAgentTurns(control, team, model, () -> true,
                new ResearchRun(control, Collections.<String, String>emptyMap(), new AtomicBoolean(false)));
    }

    @Test
    public void tagsComeOnlyAfterReadingAndOnlyFromTheReadPages() {
        Endpoint endpoint = new Endpoint();
        agent(endpoint).run("Was ist IAR?", new DirectModeAgentTest.Screen());

        List<String> events = endpoint.events;
        int read = events.lastIndexOf(KnowledgePages.READ_TOOL);
        assertTrue(events.toString(), read >= 0);
        assertEquals("vor knowledge_read kein Konzeptgespräch und kein Vorschlag", -1,
                firstOf(events.subList(0, read), "model:concept", "model:tags", GroundedSearchTags.OFFER_TOOL));
        assertEquals("genau ein Angebot, aus dem Post-Research-Schritt", 1, endpoint.offered.size());
        assertTrue(events.toString(), events.indexOf(GroundedSearchTags.OFFER_TOOL) > read);
        assertTrue(events.toString(), events.indexOf("model:tags") > events.indexOf("model:concept"));

        String tags = endpoint.offered.get(0).toLowerCase(Locale.ROOT);
        for (String invented : Arrays.asList("embedded", "workbench", "compiler", "gcc")) {
            assertFalse(invented + " in " + tags, tags.contains(invented));
        }
        assertTrue(tags, tags.contains("iar nizza"));
        assertTrue(tags, tags.contains("iar zauf"));
        assertTrue(tags, tags.contains("iar rest-schnittstelle"));
    }

    @Test
    public void withoutAReadPageThereAreNoNewTags() {
        Endpoint endpoint = new Endpoint();
        endpoint.searchResult = "keine Treffer";
        agent(endpoint).run("Was ist IAR?", new DirectModeAgentTest.Screen());

        assertTrue(endpoint.events.toString(), endpoint.events.contains(KnowledgePages.SEARCH_TOOL));
        assertFalse(endpoint.events.toString(), endpoint.events.contains(KnowledgePages.READ_TOOL));
        assertTrue("kein Angebot ohne gelesene Seite", endpoint.offered.isEmpty());
        assertFalse("kein ungestütztes Konzeptgespräch", endpoint.events.contains("model:concept"));
    }

    @Test
    public void groundingDropsEverySuggestionWhoseWordsTheReadPagesDoNotContain() {
        List<GroundedSearchTags.Suggestion> kept = GroundedSearchTags.grounded(GroundedSearchTags.parse(SUGGESTIONS),
                PAGE_TEXT, Collections.singletonList("IAR"), "Was ist IAR?");
        List<String> queries = new ArrayList<String>();
        for (GroundedSearchTags.Suggestion suggestion : kept) {
            queries.add(suggestion.query);
        }
        assertEquals(Arrays.asList("IAR REST-Schnittstelle", "IAR NIZZA", "IAR ZAUF"), queries);
        assertEquals("[]", GroundedSearchTags.json(GroundedSearchTags.grounded(
                GroundedSearchTags.parse(SUGGESTIONS), "", Collections.<String>emptyList(), "")));
    }

    private static int firstOf(List<String> events, String... names) {
        for (int i = 0; i < events.size(); i++) {
            if (Arrays.asList(names).contains(events.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
