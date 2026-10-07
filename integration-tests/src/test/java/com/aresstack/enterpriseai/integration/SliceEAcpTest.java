package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.application.agent.AgentExchange;
import com.aresstack.enterpriseai.application.agent.AgentFailure;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.agent.AgentStatus;
import com.aresstack.enterpriseai.application.agent.AgentTurnListener;
import com.aresstack.enterpriseai.application.agent.AgentTurnState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.aresstack.enterpriseai.integration.SliceSupport.TIMEOUT_SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Slice E – ACP: Host ({@code AgentService} aus application, {@code AcpAgentLauncher} aus app-swing) → ACP über
 * den echten Solon-Connector (STDIO, JSON-RPC) → Demo-Agent aus acp-demo-agent als eigener Java-8-Kindprozess.
 * Ohne MCP-Endpoint: hier zählt nur die Agentenverbindung (Streaming, Abbruch, Prozessende).
 */
public class SliceEAcpTest {

    private final List<String> agentStderr = Collections.synchronizedList(new ArrayList<String>());
    private RecordingConnector connector;
    private ExecutorService agentStarts;
    private AgentService agent;

    /** Sammelt einen Auftrag ein; der Testthread wartet nur auf dem Latch. */
    private static final class CollectingTurn implements AgentTurnListener {
        final List<String> messages = Collections.synchronizedList(new ArrayList<String>());
        final List<String> thoughts = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch firstMessage = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<String> outcome = new AtomicReference<String>();

        @Override
        public void onMessage(String text) {
            messages.add(text);
            firstMessage.countDown();
        }

        @Override
        public void onThought(String text) {
            thoughts.add(text);
        }

        @Override
        public void onCompleted() {
            finish("completed");
        }

        @Override
        public void onCancelled() {
            finish("cancelled");
        }

        @Override
        public void onFailed(AgentFailure failure) {
            finish("failed:" + failure);
        }

        private void finish(String result) {
            outcome.compareAndSet(null, result);
            done.countDown();
        }

        String text() {
            StringBuilder all = new StringBuilder();
            synchronized (messages) {
                for (String message : messages) {
                    all.append(message);
                }
            }
            return all.toString();
        }

        void awaitDone() throws InterruptedException {
            assertTrue("Auftrag endete nicht innerhalb von " + TIMEOUT_SECONDS + " s",
                    done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Before
    public void setUp() {
        String jar = SliceSupport.demoAgentJar();
        connector = new RecordingConnector(new SolonAcpAgentConnector(Duration.ofSeconds(30), agentStderr::add));
        agentStarts = Executors.newSingleThreadExecutor();
        agent = new AgentService(new AcpAgentLauncher(connector,
                new AgentLaunchSpec(SliceSupport.javaBinary(), Arrays.asList("-jar", jar), null)), agentStarts);
    }

    @After
    public void tearDown() {
        if (agent != null) {
            agent.close();
        }
        if (agentStarts != null) {
            agentStarts.shutdownNow();
        }
    }

    @Test
    public void promptStreamsThoughtAndChunksFromTheDemoAgentProcess() throws Exception {
        assertEquals(AgentStatus.NOT_STARTED, agent.status());
        CollectingTurn turn = new CollectingTurn();
        agent.send("hello slice e", turn);
        turn.awaitDone();

        assertEquals("completed", turn.outcome.get());
        // Inhalt statt Reihenfolge: der Adapter garantiert die Wire-Reihenfolge der Updates derzeit nicht (AP21).
        for (int i = 1; i <= 3; i++) {
            assertTrue(turn.text(), turn.text().contains("chunk " + i + " for 'hello slice e'"));
        }
        assertEquals(Collections.singletonList("thinking about: hello slice e"), new ArrayList<String>(turn.thoughts));
        assertEquals(AgentStatus.READY, agent.status());
        assertNotNull(agent.sessionId());
        assertEquals(1, agent.transcript().size());
        AgentExchange exchange = agent.transcript().get(0);
        assertEquals(AgentTurnState.COMPLETED, exchange.state());
        assertEquals("hello slice e", exchange.prompt());
        assertTrue(exchange.reply(), exchange.reply().contains("chunk 1 for 'hello slice e'"));

        // STDERR des Agenten kommt beim Host an und bleibt vom Protokoll auf STDOUT getrennt.
        SliceSupport.await("Agent-Log auf STDERR", () -> {
            synchronized (agentStderr) {
                for (String line : agentStderr) {
                    if (line.contains("[demo-agent] prompt: hello slice e")) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @Test
    public void cancelStopsASlowTurnAndKeepsTheSession() throws Exception {
        CollectingTurn turn = new CollectingTurn();
        agent.send("slow burn", turn);
        assertTrue(turn.firstMessage.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        agent.cancel();
        turn.awaitDone();

        assertEquals("cancelled", turn.outcome.get());
        assertEquals(AgentTurnState.CANCELLED, agent.transcript().get(0).state());
        assertEquals(AgentStatus.READY, agent.status());
        String session = agent.sessionId();

        CollectingTurn next = new CollectingTurn();
        agent.send("count 2", next);
        next.awaitDone();
        assertEquals("completed", next.outcome.get());
        assertTrue(next.text(), next.text().contains("#1") && next.text().contains("#2"));
        assertEquals("dieselbe ACP-Session", session, agent.sessionId());
    }

    @Test
    public void closingTheServiceEndsTheAgentProcess() throws Exception {
        CollectingTurn turn = new CollectingTurn();
        agent.send("count 1", turn);
        turn.awaitDone();
        AcpConnection connection = connector.connection.get();
        assertNotNull(connection);
        assertTrue(connection.getProcess().isAlive());

        agent.close();
        assertFalse("der Agentenprozess endet mit dem Service", connection.getProcess().isAlive());
        assertEquals(AgentStatus.CLOSED, agent.status());
    }
}
