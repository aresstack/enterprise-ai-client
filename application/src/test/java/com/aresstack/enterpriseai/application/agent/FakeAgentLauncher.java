package com.aresstack.enterpriseai.application.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpSessionState;
import com.aresstack.enterpriseai.acp.api.AcpStates;
import com.aresstack.enterpriseai.acp.api.AcpUpdate;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.acp.api.PromptDispatcher;
import com.aresstack.enterpriseai.acp.api.PromptHandle;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Skriptbarer ACP-Fake ohne Prozess (Form nach {@code InMemoryConnectorContractTest} aus acp-client-api).
 * Prompts bleiben offen, bis der Test Updates und den Abschluss über {@link FakePrompt} auslöst; ein Abbruch
 * wird wie beim echten Agenten erst bestätigt, wenn der Test {@link FakePrompt#acknowledgeCancel()} ruft.
 */
final class FakeAgentLauncher implements AgentLauncher {

    final List<FakeConnection> connections = new CopyOnWriteArrayList<FakeConnection>();
    final List<FakePrompt> prompts = new CopyOnWriteArrayList<FakePrompt>();
    volatile AcpException launchFailure;
    volatile AcpException sessionFailure;
    private final AtomicInteger sessionIds = new AtomicInteger();

    @Override
    public AcpConnection launch() throws AcpException {
        if (launchFailure != null) {
            throw launchFailure;
        }
        FakeConnection connection = new FakeConnection();
        connections.add(connection);
        return connection;
    }

    FakePrompt lastPrompt() {
        return prompts.get(prompts.size() - 1);
    }

    final class FakeConnection implements AcpConnection {
        final AcpStates.Connection state = new AcpStates.Connection();
        final List<FakeSession> sessions = new CopyOnWriteArrayList<FakeSession>();

        FakeConnection() {
            state.to(AcpConnectionState.INITIALIZING);
            state.to(AcpConnectionState.READY);
        }

        @Override
        public AcpConnectionState getState() {
            return state.get();
        }

        @Override
        public AgentProcessHandle getProcess() {
            return new AgentProcessHandle() {
                @Override
                public boolean isAlive() {
                    return state.get() == AcpConnectionState.READY;
                }

                @Override
                public void destroyForcibly() {
                    state.to(AcpConnectionState.FAILED);
                }
            };
        }

        @Override
        public AcpSession newSession() throws AcpException {
            if (sessionFailure != null) {
                throw sessionFailure;
            }
            if (state.get() != AcpConnectionState.READY) {
                throw new AcpException(AcpException.Phase.SESSION, "not READY", null);
            }
            FakeSession session = new FakeSession(this, "fake-session-" + sessionIds.incrementAndGet());
            sessions.add(session);
            return session;
        }

        /** Simuliert den Tod des Agentenprozesses: laufende Prompts scheitern. */
        void die() {
            state.to(AcpConnectionState.FAILED);
            for (FakeSession session : sessions) {
                session.failOpenPrompts();
            }
        }

        @Override
        public void close() {
            state.to(AcpConnectionState.CLOSED);
            for (FakeSession session : sessions) {
                session.failOpenPrompts();
            }
        }

        boolean isClosed() {
            return state.get() == AcpConnectionState.CLOSED;
        }
    }

    final class FakeSession implements AcpSession {
        final FakeConnection connection;
        final String id;
        final AcpStates.Session state = new AcpStates.Session();
        final List<FakePrompt> open = new CopyOnWriteArrayList<FakePrompt>();

        FakeSession(FakeConnection connection, String id) {
            this.connection = connection;
            this.id = id;
            state.to(AcpSessionState.ACTIVE);
        }

        @Override
        public String getSessionId() {
            return id;
        }

        @Override
        public AcpSessionState getState() {
            return state.get();
        }

        @Override
        public PromptHandle prompt(String text, AcpUpdateListener listener) {
            FakePrompt prompt = new FakePrompt(this, text,
                    new PromptDispatcher(id, "prompt-" + (prompts.size() + 1), listener));
            prompts.add(prompt);
            open.add(prompt);
            return prompt;
        }

        void failOpenPrompts() {
            for (FakePrompt prompt : open) {
                prompt.terminal(AcpPromptState.FAILED);
            }
        }

        @Override
        public void close() {
            state.to(AcpSessionState.CLOSING);
            state.to(AcpSessionState.CLOSED);
        }
    }

    static final class FakePrompt implements PromptHandle {
        final FakeSession session;
        final String text;
        final PromptDispatcher dispatcher;
        volatile int cancelRequests;

        FakePrompt(FakeSession session, String text, PromptDispatcher dispatcher) {
            this.session = session;
            this.text = text;
            this.dispatcher = dispatcher;
        }

        void message(String chunk) {
            dispatcher.update(AcpUpdate.Kind.MESSAGE, chunk);
        }

        void thought(String chunk) {
            dispatcher.update(AcpUpdate.Kind.THOUGHT, chunk);
        }

        void other(String chunk) {
            dispatcher.update(AcpUpdate.Kind.OTHER, chunk);
        }

        void complete() {
            terminal(AcpPromptState.COMPLETED);
        }

        void terminal(AcpPromptState state) {
            dispatcher.terminal(state, "detail with /secret/path?token=abc");
            session.open.remove(this);
        }

        void acknowledgeCancel() {
            terminal(AcpPromptState.CANCELLED);
        }

        @Override
        public String getPromptId() {
            return dispatcher.getPromptId();
        }

        @Override
        public AcpPromptState getState() {
            return dispatcher.getState();
        }

        @Override
        public void cancel() {
            cancelRequests++;
            dispatcher.cancelling();
        }
    }
}
