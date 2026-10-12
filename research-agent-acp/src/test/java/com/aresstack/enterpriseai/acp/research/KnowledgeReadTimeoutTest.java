package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCallException;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Eine Zeitüberschreitung bei {@code knowledge_read} beendet den Turn nicht: der Treffer wird einmal wiederholt und
 * dann übersprungen, der nächste Treffer liefert die Antwort. Nur ein wirklich nicht erreichbarer Host bricht ab.
 */
public class KnowledgeReadTimeoutTest {

    static final String SLOW = "confluence:wiki/page/1001";
    static final String FAST = "confluence:wiki/page/1002";

    /** Treffer 1 hängt (Zeitüberschreitung im Transport), Treffer 2 antwortet. */
    static final class SlowFirstHit extends DirectModeAgentTest.FakeEndpoint {
        int slowReads;
        int fastReads;
        /** Der Host fällt mit der hängenden Anfrage aus (danach antwortet auch {@code tools/list} nicht mehr). */
        boolean hostDiesOnSlowRead;
        private boolean hostDown;

        SlowFirstHit() {
            answers.put(KnowledgePages.SEARCH_TOOL, "[1] IAR Übersicht\nid: " + SLOW + "\nurl: \nIAR\n\n"
                    + "[2] IAR Planung\nid: " + FAST + "\nurl: \nIAR Planung");
            modelAnswer = "Der Pilot von IAR startet im Frühjahr. [P1]";
        }

        @Override
        public Map<String, String> listTools() {
            if (hostDown) {
                throw new IllegalStateException("connection refused");
            }
            return super.listTools();
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
            if (KnowledgePages.READ_TOOL.equals(toolName)) {
                calls.add(toolName);
                if (SLOW.equals(arguments.get("id"))) {
                    slowReads++;
                    hostDown = hostDiesOnSlowRead;
                    throw new McpToolCallException("java.net.SocketTimeoutException: Read timed out", true);
                }
                fastReads++;
                return "IAR Planung\n\nDer Pilot von IAR startet im Frühjahr.";
            }
            return super.callTool(toolName, arguments);
        }
    }

    @Test
    public void aTimeoutOnTheFirstHitIsRetriedOnceThenSkippedAndTheSecondHitAnswers() {
        SlowFirstHit endpoint = new SlowFirstHit();
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        DirectModeAgentTest.agent(endpoint).run("Suche in Confluence nach IAR", screen);

        assertEquals("ein Versuch und eine Wiederholung", DirectAnswers.READ_ATTEMPTS, endpoint.slowReads);
        assertEquals(1, endpoint.fastReads);
        assertEquals("nur die gelesene Seite wird Quelle", FAST, endpoint.lastArgs.get("project_source_add")
                .get("capture_id"));
        String text = screen.all();
        assertTrue(text, text.contains("Der Pilot von IAR startet im Frühjahr"));
        assertFalse(text, text.contains("Verbindung zum Client"));
        assertEquals("genau eine Antwort: " + screen.messages, 1, screen.messages.size());
        // die Karte endet erfolgreich, die übersprungene Seite steht in ihr
        String card = screen.card();
        assertTrue(screen.succeeded.get(card));
    }

    @Test
    public void onlyAnUnreachableHostEndsTheTurnWithTheConnectionError() {
        SlowFirstHit endpoint = new SlowFirstHit();
        endpoint.hostDiesOnSlowRead = true;
        DirectModeAgentTest.Screen screen = new DirectModeAgentTest.Screen();
        DirectModeAgentTest.agent(endpoint).run("Suche in Confluence nach IAR", screen);

        assertEquals("kein zweiter Versuch gegen einen toten Host", 1, endpoint.slowReads);
        assertEquals(0, endpoint.fastReads);
        assertTrue(screen.all(), screen.all().contains("Die Verbindung zum Client ist unterbrochen"));
    }
}
