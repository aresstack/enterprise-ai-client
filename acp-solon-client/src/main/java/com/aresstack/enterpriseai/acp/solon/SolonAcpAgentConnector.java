package com.aresstack.enterpriseai.acp.solon;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.aresstack.enterpriseai.acp.api.AcpAgentConnector;
import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpSessionState;
import com.aresstack.enterpriseai.acp.api.AcpStates;
import com.aresstack.enterpriseai.acp.api.AcpUpdate;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.acp.api.PromptDispatcher;
import com.aresstack.enterpriseai.acp.api.PromptHandle;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The one place that touches acp-sdk/Reactor. Spawns the external agent via the SDK's STDIO transport,
 * initializes an {@link AcpSyncClient}, and adapts everything onto the neutral :acp-client-api ports.
 *
 * <p>Lifecycles are separate: the OS process (mediated by the SDK transport), the initialized connection
 * (guarded {@link AcpStates.Connection}), the logical session ({@link AcpStates.Session}) and each prompt run
 * (a {@link PromptDispatcher} with monotonic sequences and exactly one terminal). STDERR is drained by the
 * SDK reader into a BOUNDED ring buffer plus an optional host log consumer — never unbounded, never mixed
 * with the ACP STDOUT stream. All callbacks run on a private daemon executor, never a UI thread.</p>
 */
public final class SolonAcpAgentConnector implements AcpAgentConnector {

    private static final int STDERR_RING_LIMIT = 200;
    /** Upper bound on a connection close: a graceful transport shutdown must never wedge the caller. */
    private static final long CLOSE_TIMEOUT_MILLIS = 2500L;

    private final Duration requestTimeout;
    private final Consumer<String> hostLog;

    public SolonAcpAgentConnector(Duration requestTimeout, Consumer<String> hostLog) {
        this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(30) : requestTimeout;
        this.hostLog = hostLog;
    }

    @Override
    public AcpConnection connect(AgentLaunchSpec spec) throws AcpException {
        Connection connection = new Connection(spec);
        connection.establish();
        return connection;
    }

    // ------------------------------------------------------------------ connection

    private final class Connection implements AcpConnection {
        private final AgentLaunchSpec spec;
        private final AcpStates.Connection state = new AcpStates.Connection();
        private final Deque<String> stderrRing = new ArrayDeque<String>();
        private final ConcurrentHashMap<String, PromptDispatcher> activePromptBySession =
                new ConcurrentHashMap<String, PromptDispatcher>();
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "acp-prompt");
            t.setDaemon(true);
            return t;
        });
        private StdioAcpClientTransport transport;
        private AcpSyncClient client;
        private volatile boolean processAlive;
        /** The spawned child, when the SDK transport exposes it (see {@link #spawnedProcess}); else null. */
        private volatile Process process;

        private Connection(AgentLaunchSpec spec) {
            this.spec = spec;
        }

        void establish() throws AcpException {
            try {
                AgentParameters.Builder params = AgentParameters.builder(spec.getCommand())
                        .args(spec.getArgs());
                if (!spec.getEnv().isEmpty()) {
                    params.env(spec.getEnv());
                }
                transport = new StdioAcpClientTransport(params.build());
                transport.setStdErrorHandler(new Consumer<String>() {
                    public void accept(String line) {
                        synchronized (stderrRing) {
                            if (stderrRing.size() >= STDERR_RING_LIMIT) {
                                stderrRing.pollFirst();
                            }
                            stderrRing.addLast(line);
                        }
                        if (hostLog != null) {
                            hostLog.accept(line);
                        }
                    }
                });
                processAlive = true;
            } catch (RuntimeException ex) {
                state.to(AcpConnectionState.FAILED);
                throw new AcpException(AcpException.Phase.SPAWN,
                        "Could not spawn the agent process: " + ex.getMessage(), ex);
            }
            try {
                state.to(AcpConnectionState.INITIALIZING);
                client = AcpClient.sync(transport)
                        .requestTimeout(requestTimeout)
                        .sessionUpdateConsumer(new Consumer<AcpSchema.SessionNotification>() {
                            public void accept(AcpSchema.SessionNotification n) {
                                route(n);
                            }
                        })
                        .build();
                client.initialize(); // capability negotiation happens inside; result readable via client
                process = spawnedProcess(transport);
                state.to(AcpConnectionState.READY);
            } catch (RuntimeException ex) {
                state.to(AcpConnectionState.FAILED);
                closeQuietly();
                throw new AcpException(AcpException.Phase.INITIALIZE,
                        "ACP initialize failed: " + ex.getMessage(), ex);
            }
        }

        /** Routes a streamed SessionNotification to the session's active prompt dispatcher. */
        private void route(AcpSchema.SessionNotification notification) {
            PromptDispatcher dispatcher = activePromptBySession.get(notification.sessionId());
            if (dispatcher == null) {
                return; // no active prompt (late/unknown) → drop, never crash the reader
            }
            AcpSchema.SessionUpdate update = notification.update();
            if (update instanceof AcpSchema.AgentMessageChunk) {
                dispatcher.update(AcpUpdate.Kind.MESSAGE, textOf(((AcpSchema.AgentMessageChunk) update).content()));
            } else if (update instanceof AcpSchema.AgentThoughtChunk) {
                dispatcher.update(AcpUpdate.Kind.THOUGHT, textOf(((AcpSchema.AgentThoughtChunk) update).content()));
            } else {
                // Unknown/custom update kinds are tolerated and surfaced generically, never fatal.
                dispatcher.update(AcpUpdate.Kind.OTHER, String.valueOf(update));
            }
        }

        private String textOf(AcpSchema.ContentBlock block) {
            return block instanceof AcpSchema.TextContent
                    ? ((AcpSchema.TextContent) block).text() : String.valueOf(block);
        }

        public AcpConnectionState getState() {
            return state.get();
        }

        public AgentProcessHandle getProcess() {
            return new AgentProcessHandle() {
                public boolean isAlive() {
                    Process p = process;
                    return p != null ? p.isAlive() : processAlive;
                }

                public void destroyForcibly() {
                    closeQuietly();
                    Process p = process;
                    if (p != null) {
                        p.destroyForcibly(); // the graceful close may have timed out on a stuck agent
                    }
                }
            };
        }

        public AcpSession newSession() throws AcpException {
            if (state.get() != AcpConnectionState.READY) {
                throw new AcpException(AcpException.Phase.SESSION,
                        "Connection is not READY (" + state.get() + ").", null);
            }
            try {
                AcpSchema.NewSessionResponse response = client.newSession(new AcpSchema.NewSessionRequest(
                        System.getProperty("user.dir"), Collections.<AcpSchema.McpServer>emptyList()));
                return new Session(this, response.sessionId());
            } catch (RuntimeException ex) {
                throw new AcpException(AcpException.Phase.SESSION,
                        "session/new failed: " + ex.getMessage(), ex);
            }
        }

        public void close() {
            if (state.get() == AcpConnectionState.READY) {
                state.to(AcpConnectionState.CLOSED);
            }
            closeQuietly();
        }

        private void closeQuietly() {
            processAlive = false;
            final AcpSyncClient toClose = client;
            if (toClose != null) {
                // BOUNDED teardown: a graceful client.close() can block when the agent process is stuck
                // (e.g. mid /api/chat model call), and this runs on the CALLER's thread — the EDT on a tab
                // close, the shutdown thread on app exit. Never let that wedge the app: close on a daemon
                // thread and move on after a short budget. A lingering child is reaped by the OS; the app
                // still exits and its shutdown persistence still runs.
                Thread closer = new Thread(new Runnable() {
                    public void run() {
                        try {
                            toClose.close();
                        } catch (RuntimeException ignored) {
                            // best-effort
                        }
                    }
                }, "acp-connection-close");
                closer.setDaemon(true);
                closer.start();
                try {
                    closer.join(CLOSE_TIMEOUT_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            // The graceful close sends SIGTERM and waits; if the child is still there after the budget,
            // force it down (bounded) instead of leaving a stuck agent behind.
            Process p = process;
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
                try {
                    p.waitFor(CLOSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            executor.shutdownNow();
            // Queued prompts dropped by shutdownNow() never reach their finally block: give every prompt
            // still registered its single terminal so no consumer waits forever.
            for (Map.Entry<String, PromptDispatcher> active : activePromptBySession.entrySet()) {
                active.getValue().terminal(AcpPromptState.CANCELLED, "connection closed");
                activePromptBySession.remove(active.getKey(), active.getValue());
            }
        }
    }

    /**
     * The SDK transport keeps the spawned {@link Process} private and offers no force-kill. Reading it lets
     * {@link AgentProcessHandle} report the real process state and force-kill a stuck agent. Pinned to
     * acp-sdk 3.10.1 (field {@code process}); if the field is missing the handle falls back to the
     * connection's own view, and the round-trip test catches a changed SDK.
     */
    static Process spawnedProcess(StdioAcpClientTransport transport) {
        try {
            Field field = StdioAcpClientTransport.class.getDeclaredField("process");
            field.setAccessible(true);
            Object value = field.get(transport);
            return value instanceof Process ? (Process) value : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ session + prompt

    private final class Session implements AcpSession {
        private final Connection connection;
        private final String sessionId;
        private final AcpStates.Session state = new AcpStates.Session();

        private Session(Connection connection, String sessionId) {
            this.connection = connection;
            this.sessionId = sessionId;
            state.to(AcpSessionState.ACTIVE);
        }

        public String getSessionId() {
            return sessionId;
        }

        public AcpSessionState getState() {
            return state.get();
        }

        /**
         * Bounded drain before a terminal (see the prompt runnable): a 50ms grace for updates the reader
         * has not routed yet when the response lands, then wait until no update arrived for 100ms —
         * capped at 1s so a terminal is never delayed noticeably even under a continuous stream.
         */
        private void awaitUpdateQuiescence(PromptDispatcher dispatcher) {
            final long quietWindowNanos = TimeUnit.MILLISECONDS.toNanos(100);
            final long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            try {
                Thread.sleep(50);
                while (System.nanoTime() < deadlineNanos
                        && dispatcher.nanosSinceLastUpdate() < quietWindowNanos) {
                    Thread.sleep(10);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); // never block a terminal on interruption
            }
        }

        public PromptHandle prompt(String text, AcpUpdateListener listener) {
            final String promptId = UUID.randomUUID().toString();
            final PromptDispatcher dispatcher = new PromptDispatcher(sessionId, promptId, listener);
            if (state.get() != AcpSessionState.ACTIVE || connection.state.get() != AcpConnectionState.READY) {
                // Never send on a closed session or a dead connection; the prompt fails on its own,
                // without marking a healthy connection FAILED.
                dispatcher.terminal(AcpPromptState.FAILED, "session " + state.get()
                        + " / connection " + connection.state.get() + ": prompt not sent");
                return handleFor(promptId, dispatcher, new AtomicBoolean(false));
            }
            if (!register(dispatcher)) {
                // One prompt per session at a time: a second one would steal the first one's stream.
                dispatcher.terminal(AcpPromptState.FAILED, "another prompt is still running on this session");
                return handleFor(promptId, dispatcher, new AtomicBoolean(false));
            }
            final AtomicBoolean sent = new AtomicBoolean(false);
            final String promptText = text == null ? "" : text;
            Runnable task = new Runnable() {
                public void run() {
                    try {
                        // Cancelled while still queued: finish locally, never send a prompt that a cancel
                        // notification has already overtaken.
                        sent.set(true);
                        if (dispatcher.getState() != AcpPromptState.RUNNING) {
                            dispatcher.terminal(AcpPromptState.CANCELLED, "cancelled before it was sent");
                            return;
                        }
                        AcpSchema.PromptResponse response = connection.client.prompt(
                                new AcpSchema.PromptRequest(sessionId, Collections.<AcpSchema.ContentBlock>
                                        singletonList(new AcpSchema.TextContent(promptText))));
                        AcpPromptState terminal = response != null
                                && response.stopReason() == AcpSchema.StopReason.CANCELLED
                                ? AcpPromptState.CANCELLED : AcpPromptState.COMPLETED;
                        // Every update precedes the response ON THE WIRE, but notifications are delivered
                        // on the transport reader thread while the response unblocks THIS thread — on a
                        // slow machine the response overtakes the tail of the update stream, and marking
                        // terminal right away would DROP those real updates (the dispatcher's late-update
                        // guard cannot tell them from stragglers): the final chunks of a turn were lost.
                        // Drain first: a short unconditional grace for not-yet-routed updates, then wait
                        // until the stream is quiet — bounded, so the terminal is never delayed noticeably.
                        awaitUpdateQuiescence(dispatcher);
                        dispatcher.terminal(terminal, String.valueOf(
                                response == null ? "" : response.stopReason()));
                    } catch (RuntimeException ex) {
                        // The prompt FAILS either way. The connection is marked FAILED only when the agent
                        // process is gone (death, broken pipe); a request timeout or an agent-side error
                        // with a live process leaves it READY for further sessions. The connection state
                        // changes BEFORE the terminal callback, so a listener sees a consistent state.
                        if (!connection.getProcess().isAlive()) {
                            connection.state.to(AcpConnectionState.FAILED);
                            connection.processAlive = false;
                        }
                        dispatcher.terminal(AcpPromptState.FAILED, "prompt failed: " + ex.getMessage());
                    } finally {
                        connection.activePromptBySession.remove(sessionId, dispatcher);
                    }
                }
            };
            try {
                connection.executor.execute(task);
            } catch (RejectedExecutionException closedMeanwhile) {
                connection.activePromptBySession.remove(sessionId, dispatcher);
                dispatcher.terminal(AcpPromptState.FAILED, "connection closed: prompt not sent");
            }
            return handleFor(promptId, dispatcher, sent);
        }

        /** Registers the session's prompt unless a non-terminal one is still registered. */
        private boolean register(PromptDispatcher dispatcher) {
            while (true) {
                PromptDispatcher existing = connection.activePromptBySession.putIfAbsent(sessionId, dispatcher);
                if (existing == null) {
                    return true;
                }
                if (!existing.getState().isTerminal()) {
                    return false;
                }
                if (connection.activePromptBySession.replace(sessionId, existing, dispatcher)) {
                    return true;
                }
            }
        }

        private PromptHandle handleFor(final String promptId, final PromptDispatcher dispatcher,
                                       final AtomicBoolean sent) {
            return new PromptHandle() {
                public String getPromptId() {
                    return promptId;
                }

                public AcpPromptState getState() {
                    return dispatcher.getState();
                }

                public void cancel() {
                    // Idempotent; a no-op after completion. Cancelling never kills the process or session.
                    // A prompt that is still queued is finished locally by its task (nothing to cancel
                    // remotely yet), so the notification is only sent once the prompt is on its way.
                    if (!dispatcher.cancelling()) {
                        return;
                    }
                    if (!sent.get()) {
                        dispatcher.terminal(AcpPromptState.CANCELLED, "cancelled before it was sent");
                        return;
                    }
                    {
                        try {
                            connection.client.cancel(new AcpSchema.CancelNotification(sessionId));
                        } catch (RuntimeException ignored) {
                            // the prompt thread will surface the terminal state
                        }
                    }
                }
            };
        }

        public void close() {
            state.to(AcpSessionState.CLOSING);
            state.to(AcpSessionState.CLOSED);
            connection.activePromptBySession.remove(sessionId);
        }
    }
}
