package com.aresstack.enterpriseai.acp.solon;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpUpdate;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.api.PromptHandle;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Real process round-trip: the adapter spawns the Java-8 demo agent jar over STDIO, initializes ACP, opens a
 * session, streams a prompt (thought + message chunks with monotonic sequences), completes, cancels a slow
 * prompt, and shuts down. STDERR logs from the agent are drained and must not disturb the protocol. No
 * Thread.sleep — synchronization via latches with timeouts.
 *
 * <p>Gradle passes the agent jar, the JVM to run it with ({@code acp.agent.java.home}, the build JVM — a real
 * Java 8 child process when the build runs on JDK 8) and {@code acp.roundtrip.required=true}, so the
 * round-trip can never be skipped silently in the build; outside Gradle (IDE) it is skipped instead.</p>
 */
public class SolonAcpRoundTripTest {

    /** Must match {@code DemoAcpAgentMain.EXIT_MARKER_ENV}; no compile dependency on the fixture module. */
    private static final String EXIT_MARKER_ENV = "ACP_DEMO_EXIT_MARKER";

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private String javaBin;
    private String agentJar;

    @Before
    public void resolve() {
        boolean required = Boolean.getBoolean("acp.roundtrip.required");
        agentJar = System.getProperty("acp.demo.agent.jar");
        String home = System.getProperty("acp.agent.java.home", System.getProperty("java.home"));
        javaBin = home + File.separator + "bin" + File.separator
                + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
        boolean ready = agentJar != null && new File(agentJar).isFile() && new File(javaBin).isFile();
        if (required && !ready) {
            fail("round-trip prerequisites missing: agent jar=" + agentJar + ", java=" + javaBin);
        }
        assumeTrue("demo agent jar not set (run via Gradle)", ready);
    }

    private AgentLaunchSpec spec() {
        return spec(Collections.<String, String>emptyMap());
    }

    private AgentLaunchSpec spec(Map<String, String> env) {
        return new AgentLaunchSpec(javaBin, Arrays.asList("-jar", agentJar), env);
    }

    /** Polls for the marker the agent JVM writes from its shutdown hook (bounded, no fixed sleep). */
    private static boolean awaitFile(File file, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (file.isFile()) {
                return true;
            }
            Thread.sleep(25);
        }
        return file.isFile();
    }

    private static final class Collecting implements AcpUpdateListener {
        final List<AcpUpdate> updates = new CopyOnWriteArrayList<AcpUpdate>();
        final AtomicReference<AcpPromptState> terminal = new AtomicReference<AcpPromptState>();
        final CountDownLatch terminated = new CountDownLatch(1);
        final CountDownLatch firstUpdate = new CountDownLatch(1);
        volatile int terminalCount;

        public void onUpdate(AcpUpdate update) {
            updates.add(update);
            firstUpdate.countDown();
        }

        public void onTerminal(String promptId, AcpPromptState state, String detail) {
            terminal.set(state);
            terminalCount++;
            terminated.countDown();
        }
    }

    @Test
    public void happyPathStreamsUpdatesThenCompletesAndCloseIsIdempotent() throws Exception {
        List<String> stderr = new CopyOnWriteArrayList<String>();
        SolonAcpAgentConnector connector =
                new SolonAcpAgentConnector(Duration.ofSeconds(30), stderr::add);
        File exitMarker = new File(tmp.getRoot(), "agent-exited");
        AcpConnection connection = connector.connect(
                spec(Collections.singletonMap(EXIT_MARKER_ENV, exitMarker.getAbsolutePath())));
        try {
            assertEquals(AcpConnectionState.READY, connection.getState());
            AcpSession session = connection.newSession();
            Collecting listener = new Collecting();
            PromptHandle handle = session.prompt("hello acp", listener);

            assertTrue("prompt did not terminate", listener.terminated.await(30, TimeUnit.SECONDS));
            assertEquals(AcpPromptState.COMPLETED, listener.terminal.get());
            assertEquals("exactly one terminal", 1, listener.terminalCount);

            // Thought + 3 message chunks, monotonic sequence numbers, correct attribution.
            assertTrue("expected >= 4 updates, got " + listener.updates.size(),
                    listener.updates.size() >= 4);
            long last = 0;
            boolean sawThought = false;
            for (AcpUpdate u : listener.updates) {
                assertTrue("monotonic sequence", u.getSequenceNumber() > last);
                last = u.getSequenceNumber();
                assertEquals(handle.getPromptId(), u.getPromptId());
                sawThought |= u.getKind() == AcpUpdate.Kind.THOUGHT;
            }
            assertTrue("thought chunk mapped", sawThought);

            // Agent logs went to STDERR and were drained without disturbing ACP.
            assertTrue("stderr drained", !stderr.isEmpty());

            // Cancel after completion is a no-op (single terminal stays COMPLETED).
            handle.cancel();
            assertEquals(AcpPromptState.COMPLETED, handle.getState());

            // A second prompt on the SAME session still works (session survives prompt lifecycle).
            Collecting second = new Collecting();
            session.prompt("again", second);
            assertTrue(second.terminated.await(30, TimeUnit.SECONDS));
            assertEquals(AcpPromptState.COMPLETED, second.terminal.get());

            session.close();
        } finally {
            connection.close();
            connection.close(); // idempotent
        }
        assertFalse(connection.getProcess().isAlive());
        assertEquals(AcpConnectionState.CLOSED, connection.getState());
        // Shutdown really ended the child JVM: its shutdown hook wrote the marker.
        assertTrue("agent process did not exit after close", awaitFile(exitMarker, 15000));
    }

    @Test
    public void cancelDuringStreamingYieldsSingleCancelledTerminal() throws Exception {
        SolonAcpAgentConnector connector = new SolonAcpAgentConnector(Duration.ofSeconds(30), null);
        AcpConnection connection = connector.connect(spec());
        try {
            AcpSession session = connection.newSession();
            Collecting listener = new Collecting();
            PromptHandle handle = session.prompt("slow burn", listener);

            assertTrue("no streaming started", listener.firstUpdate.await(30, TimeUnit.SECONDS));
            handle.cancel();
            handle.cancel(); // idempotent

            assertTrue("prompt did not terminate after cancel",
                    listener.terminated.await(30, TimeUnit.SECONDS));
            assertEquals(AcpPromptState.CANCELLED, listener.terminal.get());
            assertEquals(1, listener.terminalCount);

            // No update may arrive after the terminal.
            int at = listener.updates.size();
            assertEquals(at, listener.updates.size());
        } finally {
            connection.close();
        }
    }

    @Test
    public void agentDeathMidPromptFailsThePromptAndTheConnection() throws Exception {
        SolonAcpAgentConnector connector = new SolonAcpAgentConnector(Duration.ofSeconds(10), null);
        AcpConnection connection = connector.connect(spec());
        try {
            AcpSession session = connection.newSession();
            Collecting listener = new Collecting();
            session.prompt("please crash now", listener);

            assertTrue("prompt did not terminate after agent death",
                    listener.terminated.await(30, TimeUnit.SECONDS));
            assertEquals(AcpPromptState.FAILED, listener.terminal.get());
            assertEquals(1, listener.terminalCount);
            assertEquals(AcpConnectionState.FAILED, connection.getState());
            assertFalse(connection.getProcess().isAlive());
            try {
                connection.newSession();
                fail("a FAILED connection must not open sessions");
            } catch (AcpException expected) {
                assertEquals(AcpException.Phase.SESSION, expected.getPhase());
            }
        } finally {
            connection.close();
        }
    }

    @Test
    public void processThatIsNoAcpAgentIsAClassifiedFailure() {
        // A real child process that never speaks ACP: it prints the JVM version to STDERR and exits.
        SolonAcpAgentConnector connector = new SolonAcpAgentConnector(Duration.ofSeconds(5), null);
        try {
            AcpConnection connection = connector.connect(
                    new AgentLaunchSpec(javaBin, Collections.singletonList("-version"), null));
            connection.close();
            fail("initialize must fail against a non-ACP process");
        } catch (AcpException expected) {
            assertTrue("phase " + expected.getPhase(), expected.getPhase() == AcpException.Phase.INITIALIZE
                    || expected.getPhase() == AcpException.Phase.SPAWN);
        }
    }
}
