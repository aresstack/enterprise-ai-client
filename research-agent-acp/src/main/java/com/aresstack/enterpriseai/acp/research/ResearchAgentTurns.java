package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ConceptAction;
import com.aresstack.enterpriseai.research.runtime.team.ConceptToolRounds;
import com.aresstack.enterpriseai.research.runtime.team.ConceptTurnPolicy;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MoveActionGenerator;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;
import com.aresstack.enterpriseai.research.runtime.team.ScopeUpdateDocument;
import com.aresstack.enterpriseai.research.runtime.team.ScopingAssistantOutput;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentPlaybook;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentReply;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentResult;
import com.aresstack.enterpriseai.research.runtime.team.TeamAgentStateView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Ein Turn des Agentenprozesses wie askai-java8 arch {@code ResearchAgentMain.prompt}: ist das Projekt noch
 * nicht begrüßt, begrüßt der Agent (und beantwortet eine mitgeschickte Nachricht im selben Turn), sonst
 * antwortet er; Konzeptpapier wie {@code conceptAwareRespond} (ein eindeutiges Ja/Nein auf eine offene
 * Konfliktfrage beantwortet der Host selbst, ein Verschiebeauftrag läuft über die eigene Verschiebe-Generierung,
 * sonst treibt {@link ConceptToolRounds} die Konzeptwerkzeuge). Alle Werkzeuge gehen über MCP an den Host.
 */
final class ResearchAgentTurns {

    private static final Logger LOG = Logger.getLogger(ResearchAgentTurns.class.getName());
    private static final String LANGUAGE = "de";
    static final String SEARCH_UNAVAILABLE = "Ich kann gerade nicht in den Wissensquellen suchen: Der Client bietet "
            + "mir knowledge_search nicht an. Prüfe in den Einstellungen unter „Tools“, ob knowledge_search und "
            + "knowledge_read aktiv sind; dann suche ich sofort.";

    /** Wohin sichtbare Nachrichten eines Turns gehen (ACP {@code sendMessage}). */
    interface Output {
        boolean isCancelled();

        void message(String text);

        /** Ein sichtbarer Werkzeugaufruf beginnt (ACP {@code tool_call}); {@code kind} wie ACP {@code ToolKind}. */
        default void toolStarted(String callId, String title, String kind, String input) {
        }

        /** Ein laufender Aufruf ändert Titel oder Inhalt (ACP {@code tool_call_update}, in Arbeit). */
        default void toolUpdated(String callId, String title, String input) {
        }

        /** Der Aufruf ist beendet (ACP {@code tool_call_update}) mit seiner Ergebniszeile. */
        default void toolFinished(String callId, boolean succeeded, String summary) {
        }
    }

    private final ResearchHostControl host;
    private final ResearchTeamAgent teamAgent;
    private final MainModelChat mainModelChat;
    /** Bietet der Host die Konzeptwerkzeuge an? Je Aufruf frisch (Reiter „Tools“ gilt ohne Neustart). */
    private final java.util.function.BooleanSupplier conceptTools;
    private final ResearchRun research;
    private final ResearchWriting writing;
    private final DirectAnswers direct;
    private final ToolNarration narration = new ToolNarration();

    ResearchAgentTurns(ResearchHostControl host, ResearchTeamAgent teamAgent, MainModelChat mainModelChat,
                       boolean conceptTools, ResearchRun research) {
        this(host, teamAgent, mainModelChat, () -> conceptTools, research);
    }

    ResearchAgentTurns(ResearchHostControl host, ResearchTeamAgent teamAgent, MainModelChat mainModelChat,
                       java.util.function.BooleanSupplier conceptTools, ResearchRun research) {
        this.host = host;
        this.research = research;
        this.teamAgent = teamAgent;
        this.mainModelChat = mainModelChat;
        this.conceptTools = conceptTools;
        this.writing = research == null ? null : new ResearchWriting(host, mainModelChat);
        this.direct = research == null ? null : new DirectAnswers(host, mainModelChat);
    }

    /**
     * Die Phasen nach der Quellensuche: Gliederung/running erstellt oder überarbeitet die Gliederung, Dokument/running
     * schreibt das Dokument, und nach der Quellensuche beantwortet jede Nachricht eine Nachfrage aus dem
     * Projektkorpus (in der Phase Dokument mit Nachsuchen und Übernahme neuer Quellen).
     */
    private boolean writingTurn(Turn turn, String text, ResearchHostControl.Status status)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        String phase = status.phase == null ? "" : status.phase.toLowerCase(java.util.Locale.ROOT);
        String state = status.state == null ? "" : status.state.toLowerCase(java.util.Locale.ROOT);
        if ("outline".equals(phase) && "running".equals(state)) {
            String feedback = isActionPrompt(text) ? "" : text;
            turn.message(writing.outline(currentConceptRendered(), host.call("research_scope_fence", null),
                    feedback));
            return true;
        }
        if ("draft".equals(phase) && "running".equals(state)) {
            turn.message(writing.document(turn.output()));
            return true;
        }
        boolean afterSources = "draft".equals(phase) || "outline".equals(phase)
                || ("research".equals(phase) && "waiting_approval".equals(state));
        if (afterSources && !text.trim().isEmpty() && !isActionPrompt(text)) {
            turn.message(writing.answer(text.trim(), "draft".equals(phase), turn.output()));
            return true;
        }
        return false;
    }

    /** Die Texte der Workflow-Knöpfe tragen keinen eigenen Auftrag. */
    static boolean isActionPrompt(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() || "Gliederung erstellen".equalsIgnoreCase(trimmed)
                || "Dokument erstellen".equalsIgnoreCase(trimmed);
    }

    /** Sitzungsende: der Seitenpool des Recherche-Laufs wird beendet. */
    void close() {
        if (research != null) {
            research.close();
        }
    }

    void run(String text, Output output) {
        Turn turn = new Turn(output);
        TurnToolView tools = new TurnToolView(turn.output(), narration);
        turn.tools = tools;
        host.setObserver(tools);
        try {
            runTurn(turn, text);
        } catch (ToolInvoker.EndpointUnavailable dead) {
            LOG.warning("[research-agent] host endpoint unavailable: " + dead.getMessage());
            turn.message("Die Verbindung zum Client ist unterbrochen; der Auftrag wurde nicht bearbeitet.");
        } catch (ToolInvoker.ToolFailure failed) {
            LOG.warning("[research-agent] host tool failed: " + failed.getMessage());
            turn.message("Der Client hat den Auftrag abgelehnt: " + failed.getMessage());
        } finally {
            host.setObserver(null);
            tools.turnFinished();
        }
    }

    private void runTurn(Turn turn, String text) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        if (!text.trim().isEmpty()) {
            // Gate 6: solange eine Konfliktfrage offen ist, gehört das nächste eindeutige Ja/Nein dem Host.
            String hostAnswer = host.beginTurn(text);
            if (hostAnswer != null) {
                turn.message(hostAnswer);
                return;
            }
        } else {
            host.beginTurn("");
        }
        ResearchHostControl.Status status = host.status();
        if (research != null && "research".equalsIgnoreCase(status.phase)
                && "running".equalsIgnoreCase(status.state)) {
            // askai arch: in Phase Quellen/running startet jede Nachricht den autonomen Lauf
            String task = researchTask(text);
            if (task.isEmpty()) {
                task = currentConceptRendered();
            }
            turn.message("Ich suche in den Wissensquellen nach: " + firstLine(task));
            turn.message(research.finish(research.run(task, turn.output())));
            return;
        }
        if (writing != null && writingTurn(turn, text, status)) {
            return;
        }
        boolean greeted = !"new".equalsIgnoreCase(status.state);
        TeamAgentStateView view = new TeamAgentStateView(status.phase, status.state,
                Collections.<String>emptyList());
        boolean searchOffered = direct != null && beforeResearch(status) && offersKnowledgeSearch();
        if (direct != null && beforeResearch(status) && !searchOffered && DirectAnswers.asksForSearch(text)) {
            // Abgeschaltete Wissenssuche nie stumm übergehen: der Nutzer erfährt, warum nicht gesucht wird.
            turn.message(SEARCH_UNAVAILABLE);
        }
        if (searchOffered && !isActionPrompt(text)
                && ConceptTurnPolicy.modeFor(text) == ConceptTurnPolicy.Mode.FULL) {
            java.util.List<String> queries = direct.plan(text, currentConceptRendered(), host.scopeFence());
            if (!queries.isEmpty() && !turn.isCancelled()) {
                directTurn(turn, view, text, status, greeted, queries);
                return;
            }
        }
        if (!teamAgent.hasGreeted() && !greeted) {
            TeamAgentResult greeting = teamAgent.greet(view);
            emit(turn, greeting);
            if (greeting.isOk()) {
                host.greeted();
                // Eine Begrüßung trägt keinen Nutzertext; was der Nutzer schon geschrieben hat, wird im selben
                // Turn beantwortet, damit nichts verloren geht.
                if (!text.trim().isEmpty() && !turn.isCancelled()) {
                    emit(turn, conceptAwareRespond(view, text, status.searchTagsOffered));
                }
            }
        } else {
            emit(turn, conceptAwareRespond(view, text, status.searchTagsOffered));
        }
    }

    /**
     * Ob der Host die Wissenssuche gerade anbietet (Werkzeug aktiv und im Werkzeugsatz der Phase); ohne sie bleibt der
     * Turn beim Konzeptgespräch.
     */
    private boolean offersKnowledgeSearch() {
        try {
            return host.tools().containsKey(KnowledgePages.SEARCH_TOOL);
        } catch (ToolInvoker.EndpointUnavailable unavailable) {
            return false;
        }
    }

    /** Vor dem autonomen Lauf: Konzeptphase, oder der Auftrag ist übergeben, aber noch nicht gestartet. */
    static boolean beforeResearch(ResearchHostControl.Status status) {
        String phase = status.phase == null ? "" : status.phase.toLowerCase(java.util.Locale.ROOT);
        String state = status.state == null ? "" : status.state.toLowerCase(java.util.Locale.ROOT);
        return "scoping".equals(phase) || ("research".equals(phase) && "waiting".equals(state));
    }

    /**
     * Der direkte Suchauftrag (ohne Konzeptpflicht): das Konzept wird wie in jedem Turn ergänzt, seine Antwort bleibt
     * aber unsichtbar; sichtbar ist die Antwort aus den gelesenen Quellen. Die Quellen landen im Projekt, der
     * geführte Workflow bleibt über „Recherche starten“ erreichbar.
     */
    private void directTurn(Turn turn, TeamAgentStateView view, String text, ResearchHostControl.Status status,
                            boolean greeted, java.util.List<String> queries)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        if (!teamAgent.hasGreeted() && !greeted) {
            TeamAgentResult greeting = teamAgent.greet(view);
            emit(turn, greeting);
            if (greeting.isOk()) {
                host.greeted();
            }
        }
        if (conceptTools.getAsBoolean() && !turn.isCancelled()) {
            TeamAgentResult concept = conceptAwareRespond(view, text, status.searchTagsOffered);
            if (concept.getStatus() == TeamAgentResult.Status.OK
                    && concept.getOutput() instanceof ScopingAssistantOutput) {
                applyScopeUpdate((ScopingAssistantOutput) concept.getOutput());
            }
        }
        if (!turn.isCancelled()) {
            turn.message(direct.answer(text, queries, turn.output()));
        }
    }

    /** Der Auftrag ohne den Startbefehl ({@code research:} wie in askai, oder „Recherche starten“). */
    static String researchTask(String text) {
        String task = text == null ? "" : text.trim();
        String lower = task.toLowerCase(java.util.Locale.ROOT);
        for (String prefix : new String[]{"research:", "recherche starten", "recherche:"}) {
            if (lower.startsWith(prefix)) {
                return task.substring(prefix.length()).replaceFirst("^[\\s:.!-]+", "").trim();
            }
        }
        return task;
    }

    private static String firstLine(String text) {
        String line = text.trim();
        int end = line.indexOf('\n');
        line = end < 0 ? line : line.substring(0, end);
        return line.length() > 160 ? line.substring(0, 160) + "…" : line;
    }

    private TeamAgentResult conceptAwareRespond(final TeamAgentStateView view, String text,
                                                boolean searchTagsOffered) {
        if (!conceptTools.getAsBoolean()) {
            return teamAgent.respond(text, view);
        }
        if (ConceptTurnPolicy.modeFor(text) == ConceptTurnPolicy.Mode.MOVE_TRUTH) {
            boolean german = true;
            conceptLog("dedicated move generation (two-field schema)");
            String conceptContext;
            try {
                conceptContext = conceptToolCall(wholeConceptRead());
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
                    String receipt = conceptToolCall(move);
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
                        return conceptToolCall(action);
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
                !searchTagsOffered, // nudge until the project offered its first tags (gate 9b)
                ConceptTurnPolicy.modeFor(text), false, null);
    }

    /** Das sichtbare Konzeptpapier vor jeder Inferenz (askai K2e: CURRENT_CONCEPT); leer bei Fehlern. */
    String currentConceptRendered() {
        if (!conceptTools.getAsBoolean()) {
            return "";
        }
        try {
            return TeamAgentPlaybook.currentConceptContext(conceptToolCall(wholeConceptRead()), true);
        } catch (Exception unavailable) {
            return "";
        }
    }

    /**
     * Ein synchroner Konzeptwerkzeugaufruf über MCP mit der Fehlerzuordnung aus askai {@code conceptToolCall}:
     * ein Fehlerergebnis wird zur {@link ToolInvoker.ToolFailure} (die Diagnose geht zurück an das Modell).
     */
    private String conceptToolCall(ConceptAction action)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        Map<String, Object> args = new LinkedHashMap<String, Object>();
        String toolName;
        switch (action.getType()) {
            case READ:
                toolName = "concept_read";
                if (!action.getPath().isEmpty()) {
                    args.put("path_json", ResearchHostControl.segmentsJson(action.getPath()));
                }
                break;
            case ADD:
                toolName = "concept_add";
                if (!action.getParent().isEmpty()) {
                    args.put("parent_path_json", ResearchHostControl.segmentsJson(action.getParent()));
                }
                args.put("name", action.getName());
                break;
            case ADD_CARDS:
                toolName = "concept_add_cards";
                if (!action.getParent().isEmpty()) {
                    args.put("parent_path_json", ResearchHostControl.segmentsJson(action.getParent()));
                }
                args.put("names_json", action.getNamesJson());
                break;
            case MOVE:
                toolName = "concept_move_leaf";
                args.put("source_json", ResearchHostControl.segmentsJson(action.getPath()));
                args.put("parent_path_json", ResearchHostControl.segmentsJson(action.getParent()));
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
                args.put("path_json", ResearchHostControl.segmentsJson(action.getPath()));
                args.put("name", action.getName());
                break;
            default:
                // Safety slice: destructive edits (REMOVE/REWRITE) left the model contract.
                throw new ToolInvoker.ToolFailure(TeamAgentPlaybook.destructiveEditRefusal());
        }
        String text = host.call(toolName, args);
        if (text.startsWith("Tool failed:") || text.startsWith("Not allowed")) {
            throw new ToolInvoker.ToolFailure(text);
        }
        return text;
    }

    private static ConceptAction wholeConceptRead() {
        return ConceptAction.parse(Collections.singletonMap("type", (Object) "read")).getAction();
    }

    private static void conceptLog(String message) {
        LOG.info("[research-concept] " + message);
    }

    private void emit(Turn turn, TeamAgentResult result) {
        if (turn.isCancelled()) {
            return;
        }
        turn.message(TeamAgentReply.visible(result, LANGUAGE));
        if (result.getStatus() == TeamAgentResult.Status.OK
                && result.getOutput() instanceof ScopingAssistantOutput) {
            applyScopeUpdate((ScopingAssistantOutput) result.getOutput());
        }
    }

    /** askai {@code emitScopeUpdate}: ein Scoping-Vorschlag geht an den Weidezaun des Hosts. */
    private void applyScopeUpdate(ScopingAssistantOutput output) {
        ScopeUpdateDocument scopeUpdate = output.getScopeUpdate();
        if (scopeUpdate == null) {
            return;
        }
        for (String dropped : scopeUpdate.getDroppedAdvisories()) {
            LOG.info("scope advisory dropped: " + dropped);
        }
        try {
            if (scopeUpdate.isValid()) {
                host.applyScopeUpdate(scopeUpdate.toJson());
            } else {
                // Der GANZE Vorschlag entfällt, nie ein Teil davon, und das wird sichtbar gesagt.
                host.scopeUpdateRejected(scopeUpdate.describeViolations());
            }
        } catch (Exception lost) {
            LOG.warning("[research-agent] scope update not delivered: " + lost.getMessage());
        }
    }

    /** Sichtbare Nachrichten eines Turns; Folgeabsätze werden mit Leerzeile abgesetzt. */
    private static final class Turn {
        private final Output output;
        private int sent;

        /** Die Wolken-Sicht des Turns (Karte offen, Konzept-Wolke); {@code null} außerhalb von {@link #run}. */
        TurnToolView tools;

        Turn(Output output) {
            this.output = output;
        }

        boolean isCancelled() {
            return output.isCancelled();
        }

        /** Für den Recherche-Lauf: seine Zeilen gehen durch denselben Turn (Absätze, Abbruch). */
        Output output() {
            return new Output() {
                @Override
                public boolean isCancelled() {
                    return Turn.this.isCancelled();
                }

                @Override
                public void message(String text) {
                    Turn.this.message(text);
                }

                @Override
                public void toolStarted(String callId, String title, String kind, String input) {
                    if (!output.isCancelled()) {
                        if (tools != null) {
                            tools.cardStarted(callId, kind);
                        }
                        output.toolStarted(callId, title, kind, input);
                    }
                }

                @Override
                public void toolUpdated(String callId, String title, String input) {
                    if (!output.isCancelled()) {
                        output.toolUpdated(callId, title, input);
                    }
                }

                @Override
                public void toolFinished(String callId, boolean succeeded, String summary) {
                    if (tools != null) {
                        tools.cardFinished(callId);
                    }
                    output.toolFinished(callId, succeeded, summary);
                }
            };
        }

        void message(String text) {
            if (text == null || text.isEmpty() || output.isCancelled()) {
                return;
            }
            output.message(sent++ > 0 ? "\n\n" + text : text);
        }
    }
}
