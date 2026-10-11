package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentToolActivity;
import com.aresstack.enterpriseai.proasteion.application.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Ende zu Ende über die echte ACP-Leitung (Demo-Agent als Kindprozess): Plan, gestreamte Überlegungen und ein
 * Werkzeugergebnis nur im {@code content} kommen als Plan-, Denk- und Werkzeugwolke im Verlauf an, je eine Wolke.
 */
public class AgentActivityWireTest {

    private ExecutorService agentStarts;
    private ExecutorService ui;
    private AgentService agentService;

    @Before
    public void setUp() {
        String agentJar = System.getProperty("acp.demo.agent.jar");
        String home = System.getProperty("acp.agent.java.home", System.getProperty("java.home"));
        String javaBin = home + File.separator + "bin" + File.separator
                + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        assumeTrue("demo agent jar not set (run via Gradle)",
                agentJar != null && new File(agentJar).isFile() && new File(javaBin).isFile());
        agentStarts = Executors.newSingleThreadExecutor();
        ui = Executors.newSingleThreadExecutor();
        agentService = new AgentService(new AcpAgentLauncher(new SolonAcpAgentConnector(Duration.ofSeconds(30), null),
                new AgentLaunchSpec(javaBin, Arrays.asList("-jar", agentJar), null)), agentStarts);
    }

    @After
    public void tearDown() {
        if (agentService != null) {
            agentService.close();
            agentStarts.shutdownNow();
            ui.shutdownNow();
        }
    }

    @Test
    public void everyUtteranceIsItsOwnBubble() throws Exception {
        List<TranscriptEntry> entries = run("utter");
        List<String> answers = new ArrayList<String>();
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                answers.add(entry.getText());
            }
        }
        assertEquals(Arrays.asList("Erstens und mehr.", "Zweitens."), answers);
    }

    private List<TranscriptEntry> run(final String prompt) throws Exception {
        final ChatShellModel model = new ChatShellModel(System::currentTimeMillis);
        final AgentServiceBinding binding = new AgentServiceBinding(agentService, model, ui);
        ui.submit(() -> binding.sendRequested(prompt, false)).get(10, TimeUnit.SECONDS);
        long deadline = System.currentTimeMillis() + 60_000L;
        while (System.currentTimeMillis() < deadline) {
            if (!ui.submit(model::isStreaming).get(10, TimeUnit.SECONDS)) {
                break;
            }
            Thread.sleep(50L);
        }
        return ui.submit(() -> new ArrayList<TranscriptEntry>(model.getEntries())).get(10, TimeUnit.SECONDS);
    }

    @Test
    public void planThoughtsAndToolResultBecomeOneCloudEach() throws Exception {
        List<TranscriptEntry> entries = run("plan IAR");
        List<TranscriptEntry> plans = new ArrayList<TranscriptEntry>();
        List<TranscriptEntry> thoughts = new ArrayList<TranscriptEntry>();
        List<TranscriptEntry> tools = new ArrayList<TranscriptEntry>();
        String answer = "";
        for (TranscriptEntry entry : entries) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
                (entry.isThinking() ? thoughts : AgentToolActivity.KIND_PLAN.equals(entry.getActivityKind())
                        ? plans : tools).add(entry);
            } else if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                answer += entry.getText();
            }
        }
        assertEquals(1, plans.size());
        assertEquals("[x] Quellen suchen: IAR\n[x] Antwort schreiben", plans.get(0).getText());
        assertEquals(TranscriptEntry.State.COMPLETE, plans.get(0).getState());
        assertEquals(1, thoughts.size());
        assertEquals("Ich suche zuerst.", thoughts.get(0).getText());
        assertEquals(1, tools.size());
        assertEquals("2 Treffer", tools.get(0).getActivitySummary());
        assertTrue(answer, answer.contains("fertig: IAR"));
    }
}
