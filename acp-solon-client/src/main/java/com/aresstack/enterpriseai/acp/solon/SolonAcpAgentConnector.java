package com.aresstack.enterpriseai.acp.solon;

import com.agentclientprotocol.sdk.client.AcpClient;
import com.agentclientprotocol.sdk.client.AcpSyncClient;
import com.agentclientprotocol.sdk.client.transport.AgentParameters;
import com.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import com.agentclientprotocol.sdk.spec.AcpClientSession;
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

import reactor.core.publisher.Mono;

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
 * with the ACP STDOUT stream. Updates and terminals reach listeners on one callback thread per connection,
 * in wire order and with the terminal last; never on the protocol reader and never on a UI thread.</p>
 */
public final class SolonAcpAgentConnector implements AcpAgentConnector {

    private static final int STDERR_RING_LIMIT = 200;
    private static final String SESSION_UPDATE = "session/update";
    private static final String AGENT_MESSAGE_CHUNK = "agent_message_chunk";
    private static final String AGENT_THOUGHT_CHUNK = "agent_thought_chunk";
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
        /**
         * The neutral callback executor: one thread per connection, FIFO. The reader enqueues updates in
         * wire order, the prompt thread enqueues the terminal after the response, which the reader only
         * sees after every preceding update; so the terminal always follows the updates of its turn.
         * Listeners never run on the protocol reader and may block or call back into ACP.
         */
        private final ExecutorService callbacks = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "acp-callbacks");
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
                        // NOT sessionUpdateConsumer(): the SDK runs each sync consumer call on its own
                        // elastic thread, so chunks can overtake each other. A raw notification handler
                        // runs inline on the transport's single reader thread, in wire order.
                        .notificationHandler(SESSION_UPDATE, new AcpClientSession.NotificationHandler() {
                            public Mono<Void> handle(Object params) {
                                route(params);
                                return Mono.empty();
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

        /**
         * Routes one {@code session/update} notification to the session's active prompt dispatcher. Runs on
         * the transport reader thread, so updates reach the dispatcher in wire order; the dispatcher's
         * delivery lock keeps that order against the terminal. The params arrive as the decoded JSON map
         * ({@code {sessionId, update: {sessionUpdate, content: {type, text}}}}); anything unexpected is
         * surfaced as OTHER and never kills the reader.
         */
        private void route(Object params) {
            Map<?, ?> notification = params instanceof Map ? (Map<?, ?>) params : null;
            Object sessionId = notification == null ? null : notification.get("sessionId");
            final PromptDispatcher dispatcher =
                    sessionId == null ? null : activePromptBySession.get(sessionId.toString());
            if (dispatcher == null) {
                return; // no active prompt (late/unknown) → drop, never crash the reader
            }
            Object update = notification.get("update");
            Object kind = update instanceof Map ? ((Map<?, ?>) update).get("sessionUpdate") : null;
            final AcpUpdate.Kind mapped;
            final String text;
            if (AGENT_MESSAGE_CHUNK.equals(kind)) {
                mapped = AcpUpdate.Kind.MESSAGE;
                text = textOf(((Map<?, ?>) update).get("content"));
            } else if (AGENT_THOUGHT_CHUNK.equals(kind)) {
                mapped = AcpUpdate.Kind.THOUGHT;
                text = textOf(((Map<?, ?>) update).get("content"));
            } else {
                // Unknown/custom update kinds are tolerated and surfaced generically, never fatal.
                mapped = AcpUpdate.Kind.OTHER;
                text = String.valueOf(update);
            }
            deliver(new Runnable() {
                public void run() {
                    dispatcher.update(mapped, text);
                }
            });
        }

        /** Runs a listener-facing step on the callback thread; after close() directly (it is then a no-op). */
        void deliver(Runnable step) {
            try {
                callbacks.execute(step);
            } catch (RejectedExecutionException closed) {
                step.run();
            }
        }

        private String textOf(Object content) {
            if (content instanceof Map && "text".equals(((Map<?, ?>) content).get("type"))) {
                Object text = ((Map<?, ?>) content).get("text");
                return text == null ? "" : text.toString();
            }
            return String.valueOf(content);
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
            callbacks.shutdownNow();
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
                        // Every update precedes the response on the wire and the reader handles both in
                        // order, so all updates of this turn are already queued on the callback thread:
                        // queue the terminal behind them instead of guessing with a drain.
                        final AcpPromptState outcome = terminal;
                        final String detail = String.valueOf(response == null ? "" : response.stopReason());
                        connection.deliver(new Runnable() {
                            public void run() {
                                dispatcher.terminal(outcome, detail);
                            }
                        });
                    } catch (RuntimeException ex) {
                        // The prompt FAILS either way. The connection is marked FAILED only when the agent
                        // process is gone (death, broken pipe); a request timeout or an agent-side error
                        // with a live process leaves it READY for further sessions. The connection state
                        // changes BEFORE the terminal callback, so a listener sees a consistent state.
                        if (!connection.getProcess().isAlive()) {
                            connection.state.to(AcpConnectionState.FAILED);
                            connection.processAlive = false;
                        }
                        final String detail = "prompt failed: " + ex.getMessage();
                        connection.deliver(new Runnable() {
                            public void run() {
                                dispatcher.terminal(AcpPromptState.FAILED, detail);
                            }
                        });
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
