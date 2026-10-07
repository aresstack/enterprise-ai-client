package com.aresstack.enterpriseai.application.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AgentServiceTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private final FakeAgentLauncher launcher = new FakeAgentLauncher();

    /** Hält Starts zurück, bis der Test sie ausführt. */
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> queued = new ArrayDeque<Runnable>();

        @Override
        public void execute(Runnable command) {
            queued.add(command);
        }

        void runAll() {
            Runnable next;
            while ((next = queued.poll()) != null) {
                next.run();
            }
        }
    }

    @Test
    public void firstPromptStartsTheAgentAndStreamsThoughtsAndMessages() {
        AgentService service = new AgentService(launcher, DIRECT);
        assertEquals(AgentStatus.NOT_STARTED, service.status());
        assertTrue(launcher.connections.isEmpty());

        RecordingAgentListener listener = new RecordingAgentListener();
        AgentTurn turn = service.send("Was steht an?", listener);

        assertEquals(AgentStatus.READY, service.status());
        assertEquals(1, launcher.connections.size());
        assertEquals("Was steht an?", launcher.lastPrompt().text);
        assertTrue(service.isBusy());
        assertEquals(AgentTurnState.RUNNING, service.transcript().get(0).state());

        launcher.lastPrompt().thought("überlege");
        launcher.lastPrompt().message("Hallo ");
        launcher.lastPrompt().other("tool call");
        launcher.lastPrompt().message("Welt");
        launcher.lastPrompt().complete();

        assertEquals(Arrays.asList("thought:überlege", "message:Hallo ", "message:Welt", "completed"),
                listener.events);
        assertEquals(AgentTurnState.COMPLETED, turn.state());
        assertFalse(service.isBusy());
        AgentExchange exchange = service.transcript().get(0);
        assertEquals("Was steht an?", exchange.prompt());
        assertEquals("Hallo Welt", exchange.reply());
        assertEquals("überlege", exchange.thoughts());
        assertEquals(AgentTurnState.COMPLETED, exchange.state());
        assertNull(exchange.failure());
    }

    @Test
    public void followUpPromptsReuseProcessAndSession() {
        AgentService service = new AgentService(launcher, DIRECT);
        service.send("eins", new RecordingAgentListener());
        String sessionId = service.sessionId();
        launcher.lastPrompt().complete();

        service.send("zwei", new RecordingAgentListener());
        launcher.lastPrompt().complete();

        assertEquals("ein Agentenprozess für beide Aufträge", 1, launcher.connections.size());
        assertEquals(sessionId, service.sessionId());
        assertSame(launcher.prompts.get(0).session, launcher.prompts.get(1).session);
        assertEquals(2, service.transcript().size());
    }

    @Test
    public void aSecondPromptWhileOneRunsIsRejected() {
        AgentService service = new AgentService(launcher, DIRECT);
        service.send("eins", new RecordingAgentListener());
        try {
            service.send("zwei", new RecordingAgentListener());
            fail("busy agent must reject a second prompt");
        } catch (IllegalStateException expected) {
            // erwartet
        }
        assertEquals(1, launcher.prompts.size());
        assertEquals(1, service.transcript().size());
    }

    @Test
    public void cancelWaitsForTheAgentAndKeepsThePartialReply() {
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        AgentTurn turn = service.send("lange Aufgabe", listener);
        launcher.lastPrompt().message("Teil");

        turn.cancel();
        turn.cancel();
        assertEquals(AgentTurnState.CANCELLING, turn.state());
        assertEquals(1, launcher.lastPrompt().cancelRequests);
        assertTrue("agent has not confirmed yet", service.isBusy());
        try {
            service.send("neu", new RecordingAgentListener());
            fail("the ACP session is still busy until the agent confirms the cancel");
        } catch (IllegalStateException expected) {
            // erwartet
        }

        launcher.lastPrompt().acknowledgeCancel();
        assertEquals(Arrays.asList("message:Teil", "cancelled"), listener.events);
        assertFalse(service.isBusy());
        AgentExchange exchange = service.transcript().get(0);
        assertEquals(AgentTurnState.CANCELLED, exchange.state());
        assertEquals("Teil", exchange.reply());

        service.send("neu", new RecordingAgentListener());
        assertEquals("cancel keeps process and session", 1, launcher.connections.size());
    }

    @Test
    public void serviceCancelReachesTheRunningTurn() {
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);
        service.cancel();
        launcher.lastPrompt().acknowledgeCancel();
        assertEquals(Collections.singletonList("cancelled"), listener.events);
        service.cancel(); // nichts läuft: kein Fehler
    }

    @Test
    public void cancelBeforeTheAgentStartedEndsLocallyAndStartsNothing() {
        ManualExecutor executor = new ManualExecutor();
        AgentService service = new AgentService(launcher, executor);
        RecordingAgentListener listener = new RecordingAgentListener();
        AgentTurn turn = service.send("x", listener);

        turn.cancel();
        assertEquals(Collections.singletonList("cancelled"), listener.events);
        assertFalse(service.isBusy());

        executor.runAll();
        assertTrue("no process for a cancelled prompt", launcher.connections.isEmpty());
        assertTrue(launcher.prompts.isEmpty());
        assertEquals(AgentTurnState.CANCELLED, service.transcript().get(0).state());
    }

    @Test
    public void launchFailureFailsThePromptAndTheNextPromptRetries() {
        launcher.launchFailure = new AcpException(AcpException.Phase.SPAWN, "java not found at /opt/x", null);
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);

        assertEquals(Collections.singletonList("failed:START_FAILED"), listener.events);
        assertEquals(AgentStatus.FAILED, service.status());
        assertEquals(AgentFailure.START_FAILED, service.transcript().get(0).failure());

        launcher.launchFailure = null;
        RecordingAgentListener retry = new RecordingAgentListener();
        service.send("y", retry);
        launcher.lastPrompt().complete();
        assertEquals(Collections.singletonList("completed"), retry.events);
        assertEquals(AgentStatus.READY, service.status());
    }

    @Test
    public void runtimeFailureOfTheLauncherIsAStartFailure() {
        AgentService service = new AgentService(new AgentLauncher() {
            @Override
            public AcpConnection launch() {
                throw new IllegalStateException("boom");
            }
        }, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);
        assertEquals(Collections.singletonList("failed:START_FAILED"), listener.events);
        assertFalse(service.isBusy());
    }

    @Test
    public void sessionFailureClosesTheHalfStartedProcess() {
        launcher.sessionFailure = new AcpException(AcpException.Phase.SESSION, "no session", null);
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);

        assertEquals(Collections.singletonList("failed:SESSION_FAILED"), listener.events);
        assertTrue("half-started agent must not keep running", launcher.connections.get(0).isClosed());
        assertEquals(AgentStatus.FAILED, service.status());
    }

    @Test
    public void agentDeathFailsThePromptAndTheNextPromptStartsAFreshAgent() {
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);
        String firstSession = service.sessionId();
        launcher.lastPrompt().message("halb");

        launcher.connections.get(0).die();
        assertEquals(Arrays.asList("message:halb", "failed:AGENT_TERMINATED"), listener.events);
        assertEquals(AgentStatus.FAILED, service.status());

        RecordingAgentListener next = new RecordingAgentListener();
        service.send("y", next);
        assertEquals(2, launcher.connections.size());
        assertNotEquals(firstSession, service.sessionId());
        launcher.lastPrompt().complete();
        assertEquals(Collections.singletonList("completed"), next.events);
        assertEquals(AgentStatus.READY, service.status());
    }

    @Test
    public void agentErrorIsReportedWithoutItsTechnicalDetail() {
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);
        launcher.lastPrompt().terminal(AcpPromptState.FAILED);

        assertEquals(Collections.singletonList("failed:PROMPT_FAILED"), listener.events);
        assertFalse(listener.events.toString().contains("secret"));
        assertFalse(service.transcript().toString().contains("secret"));
        assertEquals("prompt failure keeps the agent", AgentStatus.READY, service.status());
    }

    @Test
    public void closeCancelsTheRunningPromptAndEndsTheAgent() {
        AgentService service = new AgentService(launcher, DIRECT);
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);
        launcher.lastPrompt().message("halb");

        service.close();
        service.close();

        assertEquals(Arrays.asList("message:halb", "cancelled"), listener.events);
        assertEquals(1, listener.terminalCount());
        assertTrue(launcher.connections.get(0).isClosed());
        assertEquals(AgentStatus.CLOSED, service.status());
        assertNull(service.sessionId());
        assertEquals("transcript stays readable", "halb", service.transcript().get(0).reply());
        try {
            service.send("y", new RecordingAgentListener());
            fail("closed agent mode accepts no prompts");
        } catch (IllegalStateException expected) {
            // erwartet
        }
    }

    @Test
    public void closingWhileTheAgentStartsDoesNotLeaveAProcessBehind() {
        final AgentService[] holder = new AgentService[1];
        final FakeAgentLauncher inner = launcher;
        AgentService service = new AgentService(new AgentLauncher() {
            @Override
            public AcpConnection launch() throws AcpException {
                AcpConnection connection = inner.launch();
                holder[0].close(); // Nutzer schließt, während der Prozess hochfährt
                return connection;
            }
        }, DIRECT);
        holder[0] = service;
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);

        assertEquals(Collections.singletonList("cancelled"), listener.events);
        assertTrue("process started during close is closed", launcher.connections.get(0).isClosed());
        assertTrue(launcher.prompts.isEmpty());
        assertEquals(AgentStatus.CLOSED, service.status());
    }

    @Test
    public void rejectedStartIsAStartFailure() {
        AgentService service = new AgentService(launcher, new Executor() {
            @Override
            public void execute(Runnable command) {
                throw new java.util.concurrent.RejectedExecutionException("shut down");
            }
        });
        RecordingAgentListener listener = new RecordingAgentListener();
        service.send("x", listener);
        assertEquals(Collections.singletonList("failed:START_FAILED"), listener.events);
        assertFalse(service.isBusy());
    }

    @Test
    public void startRunsOffTheCallingThread() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AgentService service = new AgentService(launcher, executor);
            RecordingAgentListener listener = new RecordingAgentListener();
            service.send("x", listener);
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    // wartet, bis der Start durch ist (Single-Thread-Executor)
                }
            }).get(10, TimeUnit.SECONDS);
            launcher.lastPrompt().message("ok");
            launcher.lastPrompt().complete();
            assertTrue(listener.awaitTerminal());
            assertEquals(Arrays.asList("message:ok", "completed"), listener.events);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void invalidArgumentsAreRejected() {
        try {
            new AgentService(null, DIRECT);
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        AgentService service = new AgentService(launcher, DIRECT);
        try {
            service.send("  ", new RecordingAgentListener());
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        try {
            service.send("x", null);
            fail();
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
        assertTrue(service.transcript().isEmpty());
    }

    @Test
    public void exchangeToStringHidesContent() {
        AgentService service = new AgentService(launcher, DIRECT);
        service.send("geheimer Auftrag", new RecordingAgentListener());
        launcher.lastPrompt().message("geheime Antwort");
        launcher.lastPrompt().complete();
        String text = service.transcript().get(0).toString();
        assertFalse(text, text.contains("geheim"));
    }
}
