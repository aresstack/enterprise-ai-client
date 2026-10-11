package com.aresstack.enterpriseai.acp.research;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Der Research-Workflow über den ganzen Agenten-Turn: nur der ausdrückliche Start startet den autonomen Lauf (mit
 * Recherche-Karte), jede andere Nachricht ist in jeder Phase eine Frage (Projektkorpus zuerst, sonst
 * Wissensquellen samt Übernahme), und in der Phase Gliederung überarbeitet nur ein Änderungswunsch die Gliederung.
 */
public class ResearchWorkflowTurnsTest {

    private static final String PASSAGE = DirectModeAgentTest.PAGE + "@cap1#p0@aaaaaaaa-bbbbbbbb";
    private static final String CORPUS_HIT = "passage: " + PASSAGE + "\nresource: " + DirectModeAgentTest.PAGE
            + "\ntitle: IAR Übersicht\nIAR bündelt die Auswertung der Anträge.\n\n";

    private static DirectModeAgentTest.FakeEndpoint endpoint(String phase, String state) {
        DirectModeAgentTest.FakeEndpoint endpoint = new DirectModeAgentTest.FakeEndpoint();
        endpoint.phase = phase;
        endpoint.state = state;
        endpoint.answers.put("research_search_plan", "IAR");
        return endpoint;
    }

    private static int count(List<String> calls, String tool) {
        int n = 0;
        for (String call : calls) {
            n += tool.equals(call) ? 1 : 0;
        }
        return n;
    }

    @Test
    public void aQuestionWhileTheSourcePhaseRunsIsAnsweredNotANewAutonomousRun() {
        DirectModeAgentTest.FakeEndpoint endpoint = endpoint("research", "running");
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        DirectModeAgentTest.agent(endpoint).run("Was ist IAR?", screen);

        assertFalse(endpoint.calls.toString(), endpoint.calls.contains("research_run_finished"));
        assertFalse(endpoint.calls.toString(), endpoint.calls.contains("research_progress"));
        assertTrue(endpoint.calls.toString(), endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
        assertEquals(DirectModeAgentTest.PAGE, endpoint.lastArgs.get("project_source_add").get("capture_id"));
        assertTrue(screen.all(), screen.all().contains("[1] IAR Übersicht"));
    }

    @Test
    public void onlyTheStartActionStartsTheAutonomousRunWithAResearchCard() {
        DirectModeAgentTest.FakeEndpoint endpoint = endpoint("research", "running");
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        DirectModeAgentTest.agent(endpoint).run("Recherche starten", screen);

        assertTrue(endpoint.calls.toString(), endpoint.calls.contains("research_run_finished"));
        String card = null;
        for (java.util.Map.Entry<String, String> started : screen.started.entrySet()) {
            if ("research".equals(screen.kinds.get(started.getKey()))) {
                card = started.getKey();
            }
        }
        assertTrue("Recherche-Karte im autonomen Lauf: " + screen.started.values(), card != null);
        assertTrue(screen.inputs.get(card), screen.inputs.get(card).contains("Seiten: "));
        assertTrue(screen.inputs.get(card), screen.inputs.get(card).contains("[[bar:"));
        assertTrue("die Karte endet", screen.finished.containsKey(card));
        assertFalse("kein Fortschritt als Chattext", screen.all().contains("Quelle übernommen:"));
    }

    @Test
    public void continuingTheRunDoesNotReadTheSamePagesAgain() {
        DirectModeAgentTest.FakeEndpoint endpoint = endpoint("research", "running");
        ResearchAgentTurns agent = DirectModeAgentTest.agent(endpoint);
        agent.run("Recherche starten", new DirectModeAgentTest.Screen());
        int firstReads = count(endpoint.calls, KnowledgePages.READ_TOOL);
        assertTrue("der erste Lauf liest die Seite", firstReads > 0);
        endpoint.calls.clear();
        agent.run("Recherche starten", new DirectModeAgentTest.Screen());
        assertEquals("der Folgelauf liest dieselbe Seite nicht noch einmal", 0,
                count(endpoint.calls, KnowledgePages.READ_TOOL));
    }

    @Test
    public void aQuestionWhileTheOutlineWaitsIsAnsweredFromTheProjectCorpus() {
        DirectModeAgentTest.FakeEndpoint endpoint = endpoint("outline", "waiting_approval");
        endpoint.answers.put("project_search", CORPUS_HIT);
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        DirectModeAgentTest.agent(endpoint).run("Welche Meilensteine hat IAR?", screen);

        assertFalse(endpoint.calls.toString(), endpoint.calls.contains("outline_propose"));
        assertFalse("Korpus reicht, keine Wissenssuche", endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
        assertTrue(screen.all(), screen.all().contains("[1] IAR Übersicht"));
    }

    @Test
    public void inTheRunningOutlinePhaseOnlyAChangeRequestRevisesTheOutline() {
        DirectModeAgentTest.FakeEndpoint question = endpoint("outline", "running");
        question.answers.put("project_search", CORPUS_HIT);
        DirectModeAgentTest.agent(question).run("Wer ist für IAR zuständig?", new DirectModeAgentTest.Screen());
        assertFalse(question.calls.toString(), question.calls.contains("outline_propose"));

        DirectModeAgentTest.FakeEndpoint change = endpoint("outline", "running");
        change.answers.put("project_search", CORPUS_HIT);
        DirectModeAgentTest.agent(change).run("Ergänze ein Kapitel zu den Kosten.", new DirectModeAgentTest.Screen());
        assertTrue(change.calls.toString(), change.calls.contains("outline_propose"));
    }

    @Test
    public void aFollowUpWhoseAnswerIsNotInTheCorpusSearchesAndAddsSourcesInEveryPhaseAfterTheStart() {
        for (String[] phase : new String[][]{{"research", "waiting_approval"}, {"outline", "waiting_approval"},
                {"draft", "waiting_approval"}, {"research", "paused"}}) {
            DirectModeAgentTest.FakeEndpoint endpoint = endpoint(phase[0], phase[1]);
            DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
            DirectModeAgentTest.agent(endpoint).run("Welche Meilensteine hat IAR?", screen);
            String where = phase[0] + "/" + phase[1] + " " + endpoint.calls;
            assertTrue(where, endpoint.calls.contains(KnowledgePages.SEARCH_TOOL));
            assertTrue(where, endpoint.calls.contains("project_source_add"));
            assertTrue(where + screen.all(), screen.all().contains("[1] IAR Übersicht"));
        }
    }

    @Test
    public void inTheConceptPhaseTheProjectCorpusComesFirstButAnExplicitSearchAlwaysSearches() {
        DirectModeAgentTest.FakeEndpoint corpus = endpoint("scoping", "running");
        corpus.answers.put("project_search", CORPUS_HIT);
        DirectModeAgentTest.agent(corpus).run("Was ist IAR?", new DirectModeAgentTest.Screen());
        assertFalse(corpus.calls.toString(), corpus.calls.contains(KnowledgePages.SEARCH_TOOL));

        DirectModeAgentTest.FakeEndpoint explicit = endpoint("scoping", "running");
        explicit.answers.put("project_search", CORPUS_HIT);
        DirectModeAgentTest.agent(explicit).run("Suche in Confluence nach IAR", new DirectModeAgentTest.Screen());
        assertTrue(explicit.calls.toString(), explicit.calls.contains(KnowledgePages.SEARCH_TOOL));
        assertTrue(explicit.calls.toString(), explicit.calls.contains(KnowledgePages.READ_TOOL));
    }
}
