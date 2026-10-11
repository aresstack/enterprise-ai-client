package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpSessionState;
import com.aresstack.enterpriseai.acp.api.AcpUpdate;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.acp.api.PromptHandle;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;
import com.aresstack.enterpriseai.research.runtime.team.ScopeUpdateDocument;
import com.aresstack.enterpriseai.research.runtime.team.ScopingAssistantOutput;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentReply;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentResult;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentStateView;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Der eingebaute Recherche-Agent als ACP-Verbindung im Prozess des Clients. In askai-java8 (arch) war das der
 * Agentenprozess {@code ResearchAgentMain}; hier gibt es keinen Prozess (keine fremde EXE, kein eigener Prozess):
 * die Verbindung implementiert den ACP-Port direkt und führt jeden Turn auf einem eigenen Arbeitsthread aus.
 *
 * <p>Ein Turn folgt {@code ResearchAgentMain.prompt}: ist das Projekt noch nicht begrüßt, begrüßt der Agent
 * (und beantwortet eine mitgeschickte Nachricht im selben Turn), sonst antwortet er. Jede Antwort geht als
 * sichtbare Nachricht hinaus ({@code TeamAgentReply.visible}); ein Scoping-Vorschlag wird auf den Weidezaun des
 * Hosts angewendet ({@code emitScopeUpdate}).
 */
final class ResearchAgentConnection implements AcpConnection {

    private static final Logger LOG = Logger.getLogger(ResearchAgentConnection.class.getName());
    private static final String PHASE_SCOPING = "scoping";
    private static final String LANGUAGE = "de";

    private final ResearchAgentHost host;
    private final ResearchTeamAgent teamAgent;
    private final ExecutorService worker;
    private volatile AcpConnectionState state = AcpConnectionState.READY;
    private volatile Session session;

    ResearchAgentConnection(ResearchAgentHost host, ResearchTeamAgent teamAgent) {
        this.host = host;
        this.teamAgent = teamAgent;
        this.worker = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "research-agent");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    @Override
    public AcpConnectionState getState() {
        return state;
    }

    @Override
    public AgentProcessHandle getProcess() {
        return null; // kein Prozess: der Agent läuft im Client
    }

    @Override
    public synchronized AcpSession newSession() throws AcpException {
        if (state != AcpConnectionState.READY) {
            throw new AcpException(AcpException.Phase.SESSION, "research agent closed", null);
        }
        session = new Session("research-" + UUID.randomUUID());
        return session;
    }

    @Override
    public void close() {
        state = AcpConnectionState.CLOSED;
        Session open = session;
        if (open != null) {
            open.close();
        }
        worker.shutdownNow();
    }

    /** Ein Turn; die Begrüßung hängt nur am Zustand des Projekts (askai: Zustand scoping/new). */
    private void runTurn(Prompt prompt, String text) {
        TeamAgentStateView view = new TeamAgentStateView(PHASE_SCOPING, host.isGreeted() ? "scoping" : "new",
                Collections.<String>emptyList());
        if (!teamAgent.hasGreeted() && !host.isGreeted()) {
            TeamAgentResult greeting = teamAgent.greet(view);
            emit(prompt, greeting);
            if (greeting.isOk()) {
                host.markGreeted();
                // Eine Begrüßung trägt keinen Nutzertext; was der Nutzer schon geschrieben hat, wird im selben
                // Turn beantwortet, damit nichts verloren geht.
                if (!text.trim().isEmpty() && !prompt.isCancelled()) {
                    emit(prompt, teamAgent.respond(text, view));
                }
            }
        } else {
            emit(prompt, teamAgent.respond(text, view));
        }
    }

    private void emit(Prompt prompt, TeamAgentResult result) {
        if (prompt.isCancelled()) {
            return;
        }
        prompt.message(TeamAgentReply.visible(result, LANGUAGE));
        if (result.getStatus() == TeamAgentResult.Status.OK
                && result.getOutput() instanceof ScopingAssistantOutput) {
            ScopeUpdateDocument scopeUpdate = ((ScopingAssistantOutput) result.getOutput()).getScopeUpdate();
            if (scopeUpdate == null) {
                return;
            }
            for (String dropped : scopeUpdate.getDroppedAdvisories()) {
                LOG.info("scope advisory dropped: " + dropped);
            }
            if (scopeUpdate.isValid()) {
                host.applyScopeUpdate(scopeUpdate.toJson());
            } else {
                // Der GANZE Vorschlag entfällt, nie ein Teil davon — und das wird sichtbar gesagt.
                host.scopeUpdateRejected(scopeUpdate.describeViolations());
            }
        }
    }

    private final class Session implements AcpSession {

        private final String sessionId;
        private final AtomicLong promptCounter = new AtomicLong();
        private volatile AcpSessionState sessionState = AcpSessionState.ACTIVE;
        private volatile Prompt running;

        Session(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public AcpSessionState getState() {
            return sessionState;
        }

        @Override
        public PromptHandle prompt(final String text, AcpUpdateListener listener) {
            final Prompt prompt = new Prompt(sessionId, sessionId + "-" + promptCounter.incrementAndGet(),
                    listener);
            if (sessionState != AcpSessionState.ACTIVE || state != AcpConnectionState.READY) {
                prompt.terminate(AcpPromptState.FAILED, "session closed");
                return prompt;
            }
            running = prompt;
            prompt.start();
            try {
                worker.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            runTurn(prompt, text == null ? "" : text);
                            prompt.terminate(AcpPromptState.COMPLETED, null);
                        } catch (RuntimeException failed) {
                            LOG.warning("Recherche-Agent: Turn gescheitert: " + failed);
                            prompt.terminate(AcpPromptState.FAILED, failed.getClass().getSimpleName());
                        }
                    }
                });
            } catch (RejectedExecutionException closed) {
                prompt.terminate(AcpPromptState.FAILED, "research agent closed");
            }
            return prompt;
        }

        @Override
        public void close() {
            sessionState = AcpSessionState.CLOSED;
            Prompt open = running;
            if (open != null) {
                open.cancel();
            }
        }
    }

    /** Ein laufender Auftrag; das Modell wird nicht unterbrochen, ein Abbruch verwirft nur, was danach kommt. */
    private static final class Prompt implements PromptHandle {

        private final String sessionId;
        private final String promptId;
        private final AcpUpdateListener listener;
        private final AtomicLong sequence = new AtomicLong();
        private AcpPromptState promptState = AcpPromptState.IDLE;

        Prompt(String sessionId, String promptId, AcpUpdateListener listener) {
            this.sessionId = sessionId;
            this.promptId = promptId;
            this.listener = listener;
        }

        @Override
        public String getPromptId() {
            return promptId;
        }

        @Override
        public synchronized AcpPromptState getState() {
            return promptState;
        }

        synchronized void start() {
            promptState = AcpPromptState.RUNNING;
        }

        synchronized boolean isCancelled() {
            return promptState.isTerminal() || promptState == AcpPromptState.CANCELLING;
        }

        void message(String text) {
            synchronized (this) {
                if (promptState != AcpPromptState.RUNNING || text == null || text.isEmpty()) {
                    return;
                }
            }
            listener.onUpdate(new AcpUpdate(sessionId, promptId, sequence.incrementAndGet(),
                    AcpUpdate.Kind.MESSAGE, sequence.get() > 1 ? "\n\n" + text : text));
        }

        @Override
        public void cancel() {
            terminate(AcpPromptState.CANCELLED, null);
        }

        void terminate(AcpPromptState terminal, String detail) {
            synchronized (this) {
                if (promptState.isTerminal()) {
                    return;
                }
                promptState = terminal;
            }
            listener.onTerminal(promptId, terminal, detail);
        }
    }
}
