package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCallException;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolClient;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Der Werkzeugsatz des Hosts gilt je Aufruf: {@code offers} und die Konzeptwerkzeuge fragen frisch per
 * {@code tools/list}, und ein Aufruf, den der Host nur deshalb ablehnt, weil er den neuen Satz nach einem
 * Zustandswechsel noch nicht veröffentlicht hat, gelingt nach kurzem Warten.
 */
public class ResearchHostControlTest {

    /** Ein Host, dessen angebotene Werkzeuge der Test ändert; ein nicht angebotenes Werkzeug ist ein Fehler. */
    private static final class Host implements McpToolClient {
        final Set<String> offered = new CopyOnWriteArraySet<String>();
        final AtomicInteger lists = new AtomicInteger();
        final AtomicInteger calls = new AtomicInteger();
        volatile String publishAfterLists;
        volatile int publishAt;

        @Override
        public Map<String, String> listTools() {
            int n = lists.incrementAndGet();
            if (publishAfterLists != null && n >= publishAt) {
                offered.add(publishAfterLists);
            }
            Map<String, String> tools = new LinkedHashMap<String, String>();
            for (String name : offered) {
                tools.put(name, "");
            }
            return tools;
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) throws McpToolCallException {
            calls.incrementAndGet();
            if (!offered.contains(toolName)) {
                throw new McpToolCallException("Unknown tool: " + toolName, false);
            }
            if ("broken".equals(toolName)) {
                throw new McpToolCallException("kaputt", false);
            }
            return "ok:" + toolName;
        }

        @Override
        public void close() {
        }
    }

    @Test
    public void offersAndConceptToolsAreAskedFreshOnEveryCall() {
        Host host = new Host();
        ResearchHostControl control = new ResearchHostControl(host);
        BooleanSupplier conceptTools = ResearchAgentMain.conceptToolsOf(control);
        assertFalse(control.offers("concept_read"));
        assertFalse(conceptTools.getAsBoolean());
        host.offered.add("concept_read"); // im Reiter „Tools“ eingeschaltet, ohne Neustart
        assertTrue(control.offers("concept_read"));
        assertTrue(conceptTools.getAsBoolean());
        host.offered.remove("concept_read");
        assertFalse(conceptTools.getAsBoolean());
    }

    @Test
    public void callWaitsForAToolTheHostIsAboutToPublish() throws Exception {
        Host host = new Host();
        host.publishAfterLists = "research_progress";
        host.publishAt = 3; // erst die dritte tools/list-Antwort enthält es
        ResearchHostControl control = new ResearchHostControl(host);
        assertEquals("ok:research_progress", control.call("research_progress",
                Collections.<String, Object>emptyMap()));
        assertEquals("ein Fehlversuch, ein Wiederholversuch", 2, host.calls.get());
    }

    @Test
    public void offeredButFailingToolFailsAtOnce() throws Exception {
        Host host = new Host();
        host.offered.add("broken");
        ResearchHostControl control = new ResearchHostControl(host);
        long start = System.currentTimeMillis();
        try {
            control.call("broken", null);
            fail("expected failure");
        } catch (ToolInvoker.ToolFailure expected) {
            assertEquals("kaputt", expected.getMessage());
        }
        assertEquals(1, host.calls.get());
        assertTrue("kein Warten", System.currentTimeMillis() - start < ResearchHostControl.PUBLISH_WAIT_MILLIS);
    }

    @Test
    public void toolTheHostNeverOffersStaysAFailure() throws Exception {
        Host host = new Host();
        ResearchHostControl control = new ResearchHostControl(host);
        try {
            control.call("outline_propose", null);
            fail("expected failure");
        } catch (ToolInvoker.ToolFailure expected) {
            assertTrue(expected.getMessage().contains("Unknown tool"));
        }
        assertEquals(1, host.calls.get());
    }
}
