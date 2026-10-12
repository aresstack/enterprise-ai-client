package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.EnvironmentRunBudget;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchRunOutcome;
import com.aresstack.enterpriseai.research.runtime.loop.ResearchStopReason;
import com.aresstack.enterpriseai.research.runtime.team.PhaseAssistantProfileRegistry;
import com.aresstack.enterpriseai.research.runtime.team.PhaseContextAssembler;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Das Seitenbudget {@code agent.research.maxPages} (vom Host als {@link EnvironmentRunBudget#ENV_MAX_PAGES} in die
 * Umgebung des Agentenprozesses gelegt) gilt für beide Recherchearten gleich: höchstens N erfolgreich gelesene Seiten,
 * 0 = unbegrenzt. Die interaktive Recherche (Phase Konzept, {@link DirectAnswers}) und der autonome Lauf (Phase
 * Quellen, {@link ResearchRun}) lesen denselben Wert.
 */
public class ResearchPageBudgetTest {

    private static final int HITS = DirectAnswers.HITS_PER_QUERY;

    /** Sechs verschiedene Treffer, jede Seite lesbar; der autonome Lauf übernimmt keine (kein vorzeitiges Ende). */
    static final class ManyPages extends DirectModeAgentTest.FakeEndpoint {
        ManyPages(int count) {
            StringBuilder hits = new StringBuilder();
            for (int i = 1; i <= count; i++) {
                hits.append('[').append(i).append("] IAR Seite ").append(i).append("\nid: confluence:wiki/page/")
                        .append(2000 + i).append("\nurl: https://wiki.example/pages/").append(2000 + i)
                        .append("\nIAR Auszug ").append(i).append("\n\n");
            }
            answers.put(KnowledgePages.SEARCH_TOOL, hits.toString());
            answers.put("source_accept", "rejected: nicht passend");
        }

        int reads() {
            int reads = 0;
            for (String call : calls) {
                reads += KnowledgePages.READ_TOOL.equals(call) ? 1 : 0;
            }
            return reads;
        }
    }

    private static Map<String, String> budget(int maxPages) {
        Map<String, String> env = new HashMap<String, String>();
        env.put(EnvironmentRunBudget.ENV_MAX_PAGES, Integer.toString(maxPages));
        return env;
    }

    private static ResearchAgentTurns agent(ManyPages endpoint, Map<String, String> env) {
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
                new ResearchRun(control, env, new AtomicBoolean(false)));
    }

    private static DirectModeAgentTest.Screen interactive(ManyPages endpoint, int maxPages) {
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        agent(endpoint, budget(maxPages)).run("Suche in Confluence nach IAR.", screen);
        return screen;
    }

    // ------------------------------------------------------------------ interaktiv (DirectAnswers)

    @Test
    public void interactiveBudgetFourReadsAtMostFourPages() {
        ManyPages endpoint = new ManyPages(HITS);
        DirectModeAgentTest.Screen screen = interactive(endpoint, 4);
        assertEquals(4, endpoint.reads());
        String card = screen.inputs.get(screen.card());
        assertTrue(card, card.contains("Seiten: 4 / 4"));
    }

    @Test
    public void interactiveBudgetOneReadsOnePage() {
        ManyPages endpoint = new ManyPages(HITS);
        DirectModeAgentTest.Screen screen = interactive(endpoint, 1);
        assertEquals(1, endpoint.reads());
        String card = screen.inputs.get(screen.card());
        assertTrue(card, card.contains("Seiten: 1 / 1"));
    }

    @Test
    public void interactiveBudgetZeroIsUnlimitedAndTheCardShowsNoDenominator() {
        ManyPages endpoint = new ManyPages(HITS);
        DirectModeAgentTest.Screen screen = interactive(endpoint, 0);
        assertEquals("alle Treffer gelesen, nicht nach 0 oder 4 Seiten gestoppt", HITS, endpoint.reads());
        String card = screen.inputs.get(screen.card());
        assertTrue(card, card.contains("Seiten: " + HITS + "\n"));
        assertFalse(card, card.contains(" / 0"));
    }

    @Test
    public void interactiveWithoutSettingUsesTheDefaultOfFour() {
        ManyPages endpoint = new ManyPages(HITS);
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        agent(endpoint, new HashMap<String, String>()).run("Suche in Confluence nach IAR.", screen);
        assertEquals(4, endpoint.reads());
    }

    // ------------------------------------------------------------------ autonom (ResearchRun)

    private static ResearchRunOutcome autonomous(ManyPages endpoint, int maxPages) {
        ResearchRun run = new ResearchRun(new ResearchHostControl(endpoint), budget(maxPages),
                new AtomicBoolean(false));
        return run.run("IAR", new DirectModeAgentTest.Screen());
    }

    @Test
    public void autonomousBudgetFourReadsAtMostFourPages() {
        ManyPages endpoint = new ManyPages(12);
        ResearchRunOutcome outcome = autonomous(endpoint, 4);
        assertTrue(endpoint.calls.toString(), outcome.getPagesVisited() <= 4);
        assertTrue(endpoint.calls.toString(), endpoint.reads() <= 4);
        assertEquals(ResearchStopReason.PAGE_BUDGET_EXHAUSTED, outcome.getStopReason());
    }

    @Test
    public void autonomousBudgetOneReadsAtMostOnePage() {
        ManyPages endpoint = new ManyPages(12);
        ResearchRunOutcome outcome = autonomous(endpoint, 1);
        assertTrue(endpoint.calls.toString(), outcome.getPagesVisited() <= 1);
        assertTrue(endpoint.calls.toString(), endpoint.reads() <= 1);
        assertEquals(ResearchStopReason.PAGE_BUDGET_EXHAUSTED, outcome.getStopReason());
    }

    @Test
    public void autonomousBudgetZeroDoesNotStopForThePageBudget() {
        ManyPages endpoint = new ManyPages(12);
        ResearchRunOutcome outcome = autonomous(endpoint, 0);
        assertNotEquals(ResearchStopReason.PAGE_BUDGET_EXHAUSTED, outcome.getStopReason());
        assertTrue("mehr als das alte Limit 4: " + outcome.getPagesVisited() + " " + endpoint.calls,
                outcome.getPagesVisited() > 4);
    }

    @Test
    public void bothResearchKindsReadTheSameSetting() {
        ResearchRun run = new ResearchRun(new ResearchHostControl(new ManyPages(1)), budget(12),
                new AtomicBoolean(false));
        assertEquals(12, run.pageBudget());
        assertEquals(0, new ResearchRun(new ResearchHostControl(new ManyPages(1)), budget(0),
                new AtomicBoolean(false)).pageBudget());
    }
}
