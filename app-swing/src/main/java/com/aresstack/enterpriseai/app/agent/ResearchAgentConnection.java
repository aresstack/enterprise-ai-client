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
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ConceptAction;
import com.aresstack.enterpriseai.research.runtime.team.ConceptToolRounds;
import com.aresstack.enterpriseai.research.runtime.team.ConceptTurnPolicy;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MoveActionGenerator;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentPlaybook;
import com.aresstack.enterpriseai.research.runtime.team.ScopeUpdateDocument;
import com.aresstack.enterpriseai.research.runtime.team.ScopingAssistantOutput;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentReply;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentResult;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentStateView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Konzeptpapier (Konzeptbaum mit Inseln) wie {@code ResearchAgentMain.conceptAwareRespond}: ein eindeutiges
 * Ja/Nein auf eine offene Konfliktfrage beantwortet der Host selbst, ein ausdrücklicher Verschiebeauftrag läuft
 * über die eigene Verschiebe-Generierung, sonst treibt {@link ConceptToolRounds} die Konzeptwerkzeuge. Die
 * Werkzeuge sind dieselben MCP-Beiträge ({@link ResearchConceptTools}), hier ohne Transport direkt gerufen.
 */
final class ResearchAgentConnection implements AcpConnection {

    private static final Logger LOG = Logger.getLogger(ResearchAgentConnection.class.getName());
    private static final String PHASE_SCOPING = "scoping";
    private static final String LANGUAGE = "de";

    private final ResearchAgentHost host;
    private final ResearchTeamAgent teamAgent;
    private final MainModelChat mainModelChat;
    private final ExecutorService worker;
    private volatile AcpConnectionState state = AcpConnectionState.READY;
    private volatile Session session;

    ResearchAgentConnection(ResearchAgentHost host, ResearchTeamAgent teamAgent, MainModelChat mainModelChat) {
        this.host = host;
        this.teamAgent = teamAgent;
        this.mainModelChat = mainModelChat;
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
        ResearchConceptHost concept = host.conceptHost();
        if (!text.trim().isEmpty()) {
            concept.recordMissionIfAbsent(text);
            // Gate 6: solange eine Konfliktfrage offen ist, gehört das nächste eindeutige Ja/Nein dem Host.
            String hostAnswer = concept.hostAnswerFor(text);
            if (hostAnswer != null) {
                prompt.message(hostAnswer);
                return;
            }
        }
        concept.refreshConversationPolicy();
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
                    emit(prompt, conceptAwareRespond(prompt, concept, view, text));
                }
            }
        } else {
            emit(prompt, conceptAwareRespond(prompt, concept, view, text));
        }
    }

    /**
     * askai {@code conceptAwareRespond}: die Maschine klassifiziert die Absicht ZUERST — ein ausdrücklicher
     * Verschiebeauftrag läuft über die eigene Verschiebe-Generierung und ist der eine maßgebliche
     * Konzeptschritt des Turns; sonst Antwort plus Werkzeugrunden.
     */
    private TeamAgentResult conceptAwareRespond(final Prompt prompt, final ResearchConceptHost concept,
                                                final TeamAgentStateView view, String text) {
        if (ConceptTurnPolicy.modeFor(text) == ConceptTurnPolicy.Mode.MOVE_TRUTH) {
            boolean german = true;
            conceptLog("dedicated move generation (two-field schema)");
            String conceptContext;
            try {
                conceptContext = conceptToolCall(concept, wholeConceptRead());
            } catch (Exception unavailable) {
                conceptContext = "";
            }
            ConceptAction move = MoveActionGenerator.generate(mainModelChat, text, conceptContext);
            String answer;
            if (move == null) {
                conceptLog("dedicated move generation -> unclear (no guess, no mutation)");
                answer = TeamAgentPlaybook.moveTruthAnswer(german, "unclear");
            } else {
                conceptLog("dedicated move: " + move.describe());
                try {
                    String receipt = conceptToolCall(concept, move);
                    if (receipt.startsWith("NO_CHANGE")) {
                        conceptLog("dedicated move -> NO_CHANGE (already at target)");
                        answer = TeamAgentPlaybook.moveTruthAnswer(german, "already-at-target");
                    } else {
                        conceptLog("dedicated move -> APPLIED (terminal)");
                        answer = TeamAgentPlaybook.moveAppliedAnswer(german, receipt);
                    }
                } catch (ToolInvoker.ToolFailure rejected) {
                    String reason = rejected.getMessage() == null ? "rejected"
                            : rejected.getMessage().replace("\r", "").replace('\n', ' ');
                    conceptLog("dedicated move -> REJECTED " + reason);
                    answer = TeamAgentPlaybook.moveRejectedAnswer(german, reason);
                } catch (ToolInvoker.EndpointUnavailable dead) {
                    conceptLog("dedicated move lost — endpoint unavailable");
                    answer = TeamAgentPlaybook.moveTruthAnswer(german, null);
                }
            }
            TeamAgentResult terminal = ConceptToolRounds.hostAnswer(answer, null);
            if (terminal != null) {
                return terminal;
            }
        }
        return ConceptToolRounds.run(teamAgent.respond(text, view),
                new ConceptToolRounds.FollowUpTurn() {
                    @Override
                    public TeamAgentResult run(String feedbackInstruction) {
                        return teamAgent.internalTurn(feedbackInstruction, view);
                    }
                },
                new ConceptToolRounds.ConceptTool() {
                    @Override
                    public String call(ConceptAction action)
                            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
                        return conceptToolCall(concept, action);
                    }
                },
                ConceptToolRounds.DEFAULT_MAX_TOOL_ROUNDS, ConceptToolRounds.DEFAULT_MAX_REPAIR_ATTEMPTS, true,
                new ConceptToolRounds.IntermediateSink() {
                    @Override
                    public void intermediate(ScopingAssistantOutput output) {
                        applyScopeUpdate(output);
                    }
                },
                new ConceptToolRounds.Trace() {
                    @Override
                    public void line(String message) {
                        conceptLog(message);
                    }
                },
                !concept.searchTagsOffered(), // nudge until the project offered its first tags (gate 9b)
                ConceptTurnPolicy.modeFor(text), false, null);
    }

    /** Das sichtbare Konzeptpapier vor jeder Inferenz (askai K2e: CURRENT_CONCEPT); leer bei Fehlern. */
    String currentConceptRendered() {
        try {
            return TeamAgentPlaybook.currentConceptContext(conceptToolCall(host.conceptHost(), wholeConceptRead()),
                    true);
        } catch (Exception unavailable) {
            return "";
        }
    }

    /**
     * Ein synchroner Konzeptwerkzeugaufruf mit der Fehlerzuordnung aus askai {@code conceptToolCall}: ein
     * Fehlerergebnis wird zur {@link ToolInvoker.ToolFailure} (die Diagnose geht zurück an das Modell).
     */
    private static String conceptToolCall(ResearchConceptHost concept, ConceptAction action)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        String toolName;
        // Segments travel as a JSON ARRAY string — never slash-joined: '/' in a card name must stay a character.
        switch (action.getType()) {
            case READ:
                toolName = "concept_read";
                if (!action.getPath().isEmpty()) {
                    args.put("path_json", segmentsJson(action.getPath()));
                }
                break;
            case ADD:
                toolName = "concept_add";
                if (!action.getParent().isEmpty()) {
                    args.put("parent_path_json", segmentsJson(action.getParent()));
                }
                args.put("name", action.getName());
                break;
            case ADD_CARDS:
                toolName = "concept_add_cards";
                if (!action.getParent().isEmpty()) {
                    args.put("parent_path_json", segmentsJson(action.getParent()));
                }
                args.put("names_json", action.getNamesJson());
                break;
            case MOVE:
                toolName = "concept_move_leaf";
                args.put("source_json", segmentsJson(action.getPath()));
                args.put("parent_path_json", segmentsJson(action.getParent()));
                break;
            case EXCLUDE:
                toolName = "exclude_topic";
                args.put("topic", action.getTopic());
                break;
            case RESOLVE:
                toolName = "resolve_concept_conflict";
                args.put("conflict_id", action.getConflictId());
                args.put("decision", action.getDecision());
                break;
            case OFFER:
                toolName = "offer_searches";
                args.put("suggestions_json", action.getSuggestionsJson());
                break;
            case PROBE:
                toolName = "scope_probe";
                args.put("terms_json", action.getTermsJson());
                break;
            case RENAME:
                toolName = "concept_rename";
                args.put("path_json", segmentsJson(action.getPath()));
                args.put("name", action.getName());
                break;
            default:
                // Safety slice: destructive edits (REMOVE/REWRITE) left the model contract.
                throw new ToolInvoker.ToolFailure(TeamAgentPlaybook.destructiveEditRefusal());
        }
        McpToolResult result;
        try {
            result = concept.call(toolName, args);
        } catch (RuntimeException failed) {
            throw new ToolInvoker.ToolFailure(failed.getMessage() == null ? failed.toString() : failed.getMessage());
        }
        if (result == null) {
            throw new ToolInvoker.ToolFailure("Not allowed: " + toolName + " is not offered in this phase");
        }
        String text = result.getText();
        if (result.isError() || text.startsWith("Tool failed:") || text.startsWith("Not allowed")) {
            throw new ToolInvoker.ToolFailure(text);
        }
        return text;
    }

    private static ConceptAction wholeConceptRead() {
        return ConceptAction.parse(Collections.singletonMap("type", (Object) "read")).getAction();
    }

    /** Card-name segments as a compact JSON array string (proper escaping, no separators). */
    private static String segmentsJson(List<String> segments) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"');
            String segment = segments.get(i);
            for (int c = 0; c < segment.length(); c++) {
                char ch = segment.charAt(c);
                if (ch == '"' || ch == '\\') {
                    sb.append('\\');
                }
                sb.append(ch);
            }
            sb.append('"');
        }
        return sb.append(']').toString();
    }

    private static void conceptLog(String message) {
        LOG.info("[research-concept] " + message);
    }

    private void emit(Prompt prompt, TeamAgentResult result) {
        if (prompt.isCancelled()) {
            return;
        }
        prompt.message(TeamAgentReply.visible(result, LANGUAGE));
        if (result.getStatus() == TeamAgentResult.Status.OK
                && result.getOutput() instanceof ScopingAssistantOutput) {
            applyScopeUpdate((ScopingAssistantOutput) result.getOutput());
        }
    }

    /** askai {@code emitScopeUpdate}: ein Scoping-Vorschlag geht auf den Weidezaun des Hosts. */
    private void applyScopeUpdate(ScopingAssistantOutput output) {
        ScopeUpdateDocument scopeUpdate = output.getScopeUpdate();
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
