package com.aresstack.enterpriseai.acp.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The port is implementable without any infrastructure, built only from {@link AcpStates} and
 * {@link PromptDispatcher}. The in-memory connector here is the shape a fake for later use-case tests (AP21)
 * takes; the assertions pin the lifecycle separation every adapter must honour: cancelling a prompt keeps
 * the session, closing a session keeps the connection, closing the connection is idempotent.
 */
public class InMemoryConnectorContractTest {

    /** Synchronous fake: a prompt echoes its text as one message update unless it is cancelled first. */
    private static final class InMemoryConnector implements AcpAgentConnector {
        public AcpConnection connect(AgentLaunchSpec spec) throws AcpException {
            if (spec.getCommand().equals("missing")) {
                throw new AcpException(AcpException.Phase.SPAWN, "no such agent", null);
            }
            final AcpStates.Connection state = new AcpStates.Connection();
            state.to(AcpConnectionState.INITIALIZING);
            state.to(AcpConnectionState.READY);
            return new AcpConnection() {
                public AcpConnectionState getState() {
                    return state.get();
                }

                public AgentProcessHandle getProcess() {
                    return new AgentProcessHandle() {
                        public boolean isAlive() {
                            return state.get() == AcpConnectionState.READY;
                        }

                        public void destroyForcibly() {
                            state.to(AcpConnectionState.FAILED);
                        }
                    };
                }

                public AcpSession newSession() throws AcpException {
                    if (state.get() != AcpConnectionState.READY) {
                        throw new AcpException(AcpException.Phase.SESSION, "not READY", null);
                    }
                    return new InMemorySession();
                }

                public void close() {
                    state.to(AcpConnectionState.CLOSED);
                }
            };
        }
    }

    private static final class InMemorySession implements AcpSession {
        private final String id = UUID.randomUUID().toString();
        private final AcpStates.Session state = new AcpStates.Session();

        InMemorySession() {
            state.to(AcpSessionState.ACTIVE);
        }

        public String getSessionId() {
            return id;
        }

        public AcpSessionState getState() {
            return state.get();
        }

        public PromptHandle prompt(final String text, AcpUpdateListener listener) {
            final PromptDispatcher dispatcher = new PromptDispatcher(id, UUID.randomUUID().toString(), listener);
            return new PromptHandle() {
                public String getPromptId() {
                    return dispatcher.getPromptId();
                }

                public AcpPromptState getState() {
                    if (dispatcher.getState() == AcpPromptState.RUNNING) {
                        dispatcher.update(AcpUpdate.Kind.MESSAGE, text);
                        dispatcher.terminal(AcpPromptState.COMPLETED, "end_turn");
                    }
                    return dispatcher.getState();
                }

                public void cancel() {
                    if (dispatcher.cancelling()) {
                        dispatcher.terminal(AcpPromptState.CANCELLED, "cancelled");
                    }
                }
            };
        }

        public void close() {
            state.to(AcpSessionState.CLOSING);
            state.to(AcpSessionState.CLOSED);
        }
    }

    private static final class Recording implements AcpUpdateListener {
        final List<String> texts = new ArrayList<String>();
        final List<AcpPromptState> terminals = new ArrayList<AcpPromptState>();

        public void onUpdate(AcpUpdate update) {
            texts.add(update.getText());
        }

        public void onTerminal(String promptId, AcpPromptState state, String detail) {
            terminals.add(state);
        }
    }

    private final AgentLaunchSpec spec = new AgentLaunchSpec("agent", null, null);

    @Test
    public void cancelKeepsSessionAndConnectionUsable() throws Exception {
        AcpConnection connection = new InMemoryConnector().connect(spec);
        AcpSession session = connection.newSession();

        Recording cancelled = new Recording();
        PromptHandle first = session.prompt("first", cancelled);
        first.cancel();
        first.cancel();
        assertEquals(AcpPromptState.CANCELLED, first.getState());
        assertEquals(Collections.singletonList(AcpPromptState.CANCELLED), cancelled.terminals);
        assertTrue(cancelled.texts.isEmpty());

        assertEquals(AcpSessionState.ACTIVE, session.getState());
        assertEquals(AcpConnectionState.READY, connection.getState());

        Recording second = new Recording();
        PromptHandle next = session.prompt("second", second);
        assertEquals(AcpPromptState.COMPLETED, next.getState());
        assertEquals(Collections.singletonList("second"), second.texts);
        next.cancel();
        assertEquals("cancel after completion is a no-op", AcpPromptState.COMPLETED, next.getState());
    }

    @Test
    public void closingSessionKeepsConnectionAndCloseIsIdempotent() throws Exception {
        AcpConnection connection = new InMemoryConnector().connect(spec);
        AcpSession session = connection.newSession();
        session.close();
        session.close();
        assertEquals(AcpSessionState.CLOSED, session.getState());
        assertEquals(AcpConnectionState.READY, connection.getState());
        assertTrue(connection.getProcess().isAlive());

        AcpSession another = connection.newSession();
        assertFalse(another.getSessionId().equals(session.getSessionId()));

        connection.close();
        connection.close();
        assertEquals(AcpConnectionState.CLOSED, connection.getState());
        assertFalse(connection.getProcess().isAlive());
        try {
            connection.newSession();
            fail("no session on a closed connection");
        } catch (AcpException expected) {
            assertEquals(AcpException.Phase.SESSION, expected.getPhase());
        }
    }

    @Test
    public void spawnFailureIsClassified() {
        try {
            new InMemoryConnector().connect(new AgentLaunchSpec("missing", null, null));
            fail("expected SPAWN failure");
        } catch (AcpException expected) {
            assertEquals(AcpException.Phase.SPAWN, expected.getPhase());
        }
    }
}
