package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ConceptAction;
import com.aresstack.enterpriseai.research.runtime.team.ConceptToolRounds;
import com.aresstack.enterpriseai.research.runtime.team.ConceptTurnPolicy;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MoveActionGenerator;
import com.aresstack.enterpriseai.research.runtime.team.OutlineChangeRequest;
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
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Ein Turn des Agentenprozesses wie askai-java8 arch {@code ResearchAgentMain.prompt}: der Agent beantwortet die
 * Nachricht des Nutzers mit genau einer Antwort (keine vorgeschaltete Begrüßung; ein neues Projekt gilt mit der ersten
 * Nachricht als begonnen); Konzeptpapier wie {@code conceptAwareRespond} (ein eindeutiges Ja/Nein auf eine offene
 * Konfliktfrage beantwortet der Host selbst, ein Verschiebeauftrag läuft über die eigene Verschiebe-Generierung,
 * sonst treibt {@link ConceptToolRounds} die Konzeptwerkzeuge). Alle Werkzeuge gehen über MCP an den Host.
 *
 * <p>Research-Workflow: die Phase wählt allein der Nutzer; der Agent wechselt sie nie. Phase Konzept ist ein
 * interaktiver Recherche-Chat (jede Frage wird sofort in den Wissensquellen recherchiert, ohne Konzeptpflicht). Den
 * autonomen Lauf der Phase Quellen startet nur ihre Play-Aktion („Recherche starten“, {@code research:}), die
 * Gliederung entsteht nur über die Play-Aktion der Phase Gliederung, das Dokument nur über die der Phase Dokument;
 * jede andere Nachricht wird in jeder Phase als Frage beantwortet, zuerst aus dem Projektkorpus, bei Lücken aus den
 * Wissensquellen (mit Übernahme der gelesenen Seiten). In der Phase Gliederung überarbeitet zusätzlich ein
 * erkennbarer Änderungswunsch die Gliederung ({@link OutlineChangeRequest}).
 */
final class ResearchAgentTurns {

    private static final Logger LOG = Logger.getLogger(ResearchAgentTurns.class.getName());
    private static final String LANGUAGE = "de";
    static final String SEARCH_UNAVAILABLE = "Ich kann gerade nicht in den Wissensquellen suchen: Der Client bietet "
            + "mir knowledge_search nicht an. Prüfe in den Einstellungen unter „Tools“, ob knowledge_search und "
            + "knowledge_read aktiv sind; dann suche ich sofort.";

    /**
     * Wohin ein Turn spricht: {@link #message} ist ausschließlich die Antwort an den Nutzer (ACP
     * {@code agent_message_chunk}); was der Agent gerade tut („suche nach …“, „schreibe Kapitel …“), ist ein
     * {@link #thought Gedanke} (ACP {@code agent_thought_chunk}) und erscheint im Client nur als vergängliche
     * Denkwolke.
     */
    interface Output {
        boolean isCancelled();

        void message(String text);

        /** Eine Arbeitsmeldung des Agenten, keine Antwort (ACP {@code agent_thought_chunk}). */
        default void thought(String text) {
        }

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
    /** Ob der Host die Konzeptwerkzeuge anbietet; je Turn frisch gefragt. */
    private final BooleanSupplier conceptTools;
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
        this.direct = research == null ? null : new DirectAnswers(host, mainModelChat);
        this.writing = research == null ? null : new ResearchWriting(host, mainModelChat, direct);
    }

    /**
     * Die Phasen nach dem Start der autonomen Recherche: Gliederung/running erstellt die Gliederung (Aktion) oder
     * überarbeitet sie (erkennbarer Änderungswunsch), Dokument/running schreibt das Dokument (Aktion); jede andere
     * Nachricht ist eine Frage, beantwortet aus dem Projektkorpus und bei Lücken aus den Wissensquellen.
     */
    private boolean writingTurn(Turn turn, String text, ResearchHostControl.Status status)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        String phase = lower(status.phase);
        String state = lower(status.state);
        boolean action = isActionPrompt(text);
        if ("outline".equals(phase) && "running".equals(state)
                && (action || OutlineChangeRequest.isChangeRequest(text))) {
            String feedback = action ? "" : text;
            turn.message(writing.outline(currentConceptRendered(), host.call("research_scope_fence", null),
                    feedback));
            return true;
        }
        if ("draft".equals(phase) && "running".equals(state) && action) {
            turn.message(writing.document(turn.output()));
            return true;
        }
        if (afterResearchStart(status) && !action) {
            answerQuestion(turn, text.trim());
            return true;
        }
        return false;
    }

    /** Ab dem Start der autonomen Recherche (Quellen außer „wartet auf Start“, Gliederung, Dokument). */
    static boolean afterResearchStart(ResearchHostControl.Status status) {
        String phase = lower(status.phase);
        return ("research".equals(phase) && !"waiting".equals(lower(status.state))) || "outline".equals(phase)
                || "draft".equals(phase);
    }

    /**
     * Eine Frage nach dem Start: ein ausdrücklicher Suchauftrag recherchiert sofort in den Wissensquellen, sonst
     * zuerst der Projektkorpus und bei Lücken die Wissensquellen; gelesene Seiten kommen ins Projekt.
     */
    private void answerQuestion(Turn turn, String text)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        boolean searchOffered = offersKnowledgeSearch();
        if (searchOffered && DirectAnswers.asksForSearch(text)) {
            java.util.List<String> queries = direct.plan(text, currentConceptRendered(), host.scopeFence());
            if (queries.isEmpty()) {
                queries = DirectAnswers.keywordQueries(text);
            }
            turn.message(direct.answer(text, queries, turn.output()));
            return;
        }
        turn.message(writing.answer(text, searchOffered, turn.output()));
    }

    /** Der Auftrag eines gelben Suchvorschlags (Host {@code ResearchWorkflow.SEARCH_TAG_PREFIX}). */
    static final String SEARCH_TAG_PREFIX = "Wissenssuche:";

    static boolean isSearchTagCommand(String text) {
        return text != null && text.trim().startsWith(SEARCH_TAG_PREFIX);
    }

    /**
     * Ein Klick auf einen gelben Suchvorschlag (askai arch {@code applyManualSearch}): genau diese Anfrage wird
     * direkt recherchiert (Recherche-Karte, Weidezaun, Übernahme der Seiten), ohne Planung und ohne
     * Konzeptgespräch; danach meldet der Agent dem Host Haken oder Retry.
     */
    private void searchTagTurn(Turn turn, String query)
            throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        boolean succeeded = false;
        try {
            if (query.isEmpty()) {
                return;
            }
            if (!offersKnowledgeSearch()) {
                turn.message(SEARCH_UNAVAILABLE);
                return;
            }
            turn.message(direct.answer(query, java.util.Collections.singletonList(query), turn.output()));
            succeeded = direct.lastSucceeded() && !turn.isCancelled();
        } finally {
            if (!query.isEmpty()) {
                Map<String, Object> args = new LinkedHashMap<String, Object>();
                args.put("query", query);
                args.put("ok", String.valueOf(succeeded));
                try {
                    host.call("search_tag_finished", args);
                } catch (ToolInvoker.ToolFailure refused) {
                    LOG.info("[research-agent] Suchvorschlag nicht gemeldet: " + refused.getMessage());
                }
            }
        }
    }

    /** Die Texte der Workflow-Knöpfe tragen keinen eigenen Auftrag (leer zählt wie ein Knopf). */
    static boolean isActionPrompt(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() || "Gliederung erstellen".equalsIgnoreCase(trimmed)
                || "Dokument erstellen".equalsIgnoreCase(trimmed) || "Dokument schreiben".equalsIgnoreCase(trimmed)
                || isStartCommand(trimmed);
    }

    /**
     * Der ausdrückliche Start des autonomen Laufs: die Aktion „Recherche starten“ (Aktionsprompt des Workflows)
     * oder das Textkommando {@code research:}/{@code recherche:} aus askai. Nur er startet Phase 2.
     */
    static boolean isStartCommand(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("research:") || lower.startsWith("recherche:")
                || lower.startsWith("recherche starten");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
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
        if (direct != null && isSearchTagCommand(text)) {
            searchTagTurn(turn, text.trim().substring(SEARCH_TAG_PREFIX.length()).trim());
            return;
        }
        if (research != null && "research".equalsIgnoreCase(status.phase)
                && "running".equalsIgnoreCase(status.state) && isStartCommand(text)) {
            // Nur der ausdrückliche Start (Aktion „Recherche starten“, research:) startet den autonomen Lauf;
            // jede andere Nachricht in Quellen/running ist eine Frage (askai arch startete mit jeder Nachricht).
            String task = researchTask(text);
            if (task.isEmpty()) {
                task = currentConceptRendered();
            }
            if (task.trim().isEmpty()) {
                // ohne Konzeptwerkzeuge: der Auftrag aus dem Suchplan des Hosts (Auftrag, Konzeptkarten)
                task = host.call("research_search_plan", null).trim().replace('\n', ' ');
            }
            turn.thought("Ich starte die autonome Recherche anhand des Konzepts"
                    + (task.trim().isEmpty() ? "." : ": " + firstLine(task)));
            turn.message(research.finish(research.run(task, turn.output())));
            return;
        }
        if (writing != null && writingTurn(turn, text, status)) {
            return;
        }
        if (afterResearchStart(status)) {
            return; // ein leerer Turn nach dem Start: nichts zu tun
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
        if (text.trim().isEmpty()) {
            return; // ein leerer Turn ohne Nutzertext: keine Begrüßung, keine Antwort
        }
        markStarted(greeted);
        emit(turn, conceptAwareRespond(view, text, status.searchTagsOffered));
    }

    /**
     * Ein neues Projekt gilt mit der ersten Nachricht als begonnen (Konzept/neu → läuft). Es gibt keine sichtbare
     * Begrüßung: die Nachricht des Nutzers bekommt genau eine fachliche Antwort.
     */
    private void markStarted(boolean greeted) throws ToolInvoker.ToolFailure, ToolInvoker.EndpointUnavailable {
        if (!greeted) {
            host.greeted();
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
        markStarted(greeted);
        if (conceptTools.getAsBoolean() && !turn.isCancelled()) {
            TeamAgentResult concept = conceptAwareRespond(view, text, status.searchTagsOffered);
            if (concept.getStatus() == TeamAgentResult.Status.OK
                    && concept.getOutput() instanceof ScopingAssistantOutput) {
                applyScopeUpdate((ScopingAssistantOutput) concept.getOutput());
            }
        }
        if (turn.isCancelled()) {
            return;
        }
        // Projektkorpus zuerst (ohne Modellaufruf, solange das Projekt nichts Passendes hat); ein ausdrücklicher
        // Suchauftrag („Suche in Confluence nach …“) sucht immer sofort in den Wissensquellen.
        String fromCorpus = DirectAnswers.asksForSearch(text) ? null : writing.corpusAnswer(text);
        turn.message(fromCorpus != null ? fromCorpus : direct.answer(text, queries, turn.output()));
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
                public void thought(String text) {
                    Turn.this.thought(text);
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

        /** Eine Arbeitsmeldung: nie Teil der Antwort, nur Denkwolke (eine Zeile je Meldung). */
        void thought(String text) {
            if (text == null || text.trim().isEmpty() || output.isCancelled()) {
                return;
            }
            output.thought(text.trim() + "\n");
        }
    }
}
