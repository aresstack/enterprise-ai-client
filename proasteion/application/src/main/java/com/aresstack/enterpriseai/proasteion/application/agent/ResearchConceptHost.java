package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.concept.ConceptBlacklistReconciler;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.ConceptTopicScanner;
import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeDraft;
import com.aresstack.enterpriseai.research.domain.scope.ScopeFacet;
import com.aresstack.enterpriseai.research.domain.scope.ScopePatch;
import com.aresstack.enterpriseai.research.domain.scope.ScopePatchOperations;
import com.aresstack.enterpriseai.research.domain.scope.UnresolvedScopeIssue;
import com.aresstack.enterpriseai.research.scope.ConflictAnswerInterpreter;
import com.aresstack.enterpriseai.research.scope.ConversationPolicyProjection;
import com.aresstack.enterpriseai.research.scope.ResearchScopeCoordinator;
import com.aresstack.enterpriseai.research.scope.ScopeUpdateResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Das Konzeptpapier (Konzeptbaum mit Inseln) eines Research-Projekts auf der Host-Seite, aus askai-java8 (arch)
 * {@code ResearchAgentSession} übernommen: die Konzeptwerkzeuge ({@link ResearchConceptTools}) über den einen
 * {@link ConceptBranchService} des Projekts, der Ausschluss mit Konzeptkonflikt ({@code excludeTopicCommand},
 * {@code resolveConceptConflictCommand}), die Antwort des Hosts auf ein eindeutiges Ja/Nein zu einer offenen
 * Konfliktfrage, der Abgleich der Blacklist nach einer Grenze (Rohspeichern, Wiederherstellen) und die
 * Gesprächsführung ({@link ConversationPolicyProjection}), die mit dem Weidezaun in den Kontext geht.
 *
 * <p>Die Scope-Prüfung (Sweep) und {@code scope_probe} brauchen Embeddings und folgen mit der Recherche; bis
 * dahin antwortet {@code scope_probe} ehrlich mit „nicht verfügbar“.
 */
final class ResearchConceptHost implements ResearchConceptTools.Context {

    private static final Logger LOG = Logger.getLogger(ResearchConceptHost.class.getName());
    /** askai {@code ResearchRuntimeSettings} DEFAULT_POLICY_SPARSE_CARDS / DEFAULT_POLICY_NOVELTY_TURNS. */
    private static final int POLICY_SPARSE_CARDS = 4;
    private static final int POLICY_NOVELTY_TURNS = 3;
    /** How many exploration tags one offer may carry — the accessory row stays readable. */
    private static final int MAX_OFFERED_SEARCHES = 5;

    /** Was der Konzept-Host vom Projekt braucht. */
    interface Owner {
        ResearchScopeCoordinator scopeCoordinator();

        void stateChanged();

        boolean isGerman();

        /** Ob das Konzept gerade geschrieben werden darf (Phase scoping, Zustand running). */
        boolean scopingWritable();

        /** {@code phase/state} für Fehlermeldungen der Werkzeuge. */
        String stateDescription();
    }

    /** Eine vom Modell angebotene Orientierungssuche (gelber Tag). */
    static final class SearchOffer {
        final String query;
        final String purpose;

        SearchOffer(String query, String purpose) {
            this.query = query;
            this.purpose = purpose;
        }
    }

    static final class ConceptConflictRef {
        final String epoch;
        final String nodeId;
        final long expectedRevision;
        final String label;
        final boolean open;

        ConceptConflictRef(String epoch, String nodeId, long expectedRevision, String label, boolean open) {
            this.epoch = epoch;
            this.nodeId = nodeId;
            this.expectedRevision = expectedRevision;
            this.label = label;
            this.open = open;
        }
    }

    private final ConceptBranchService service;
    private final Owner owner;

    /** Opaque conflictId → reference, INSERTION-ordered (the host resolves oldest first). */
    private final Map<String, ConceptConflictRef> conceptConflicts =
            Collections.synchronizedMap(new LinkedHashMap<String, ConceptConflictRef>());
    private final AtomicLong conflictIds = new AtomicLong();
    /**
     * The DiscardedDecisionTombstone: when a raw save/restore discards an OPEN conflict, the transcript still
     * shows the old removal question — a bare "Ja." afterwards must get a deterministic HOST answer.
     */
    private volatile String staleDecisionTombstone;
    private volatile List<SearchOffer> offeredSearches = Collections.emptyList();
    private volatile boolean searchTagsOffered;

    // conversation policy (ratified slice)
    private Set<String> noveltyPreviousIds = new HashSet<String>();
    private String noveltyEpoch;
    private int turnsWithoutNewCards;
    private volatile String conversationPolicyBlock = "";

    ResearchConceptHost(ConceptBranchService service, Owner owner) {
        this.service = service;
        this.owner = owner;
        service.setDiagnosticSink(new ConceptBranchService.DiagnosticSink() {
            @Override
            public void line(String line) {
                technicalLog(line);
            }
        });
        service.setLifecycleListener(new ConceptBranchService.LifecycleListener() {
            @Override
            public void onTransientBoundary(String reason, String newEpoch) {
                discardTransientsAndReconcile(reason, newEpoch);
            }
        });
        service.addChangeListener(new Runnable() {
            @Override
            public void run() {
                owner.stateChanged();
            }
        });
    }

    ConceptBranchService service() {
        return service;
    }

    /** Die Konzeptwerkzeuge für diesen Turn (Scoping: lesen und schreiben). */
    List<McpToolContribution> tools() {
        return ResearchConceptTools.toolsFor(this);
    }

    /** Ein Werkzeugaufruf über denselben Trichter wie ein MCP-Aufruf; {@code null}, wenn nicht angeboten. */
    McpToolResult call(String toolName, Map<String, Object> args) {
        for (McpToolContribution tool : tools()) {
            if (tool.getName().equals(toolName)) {
                return tool.getHandler().invoke(new McpToolCall(toolName, args));
            }
        }
        return null;
    }

    boolean searchTagsOffered() {
        return searchTagsOffered;
    }

    List<SearchOffer> offeredSearches() {
        return offeredSearches;
    }

    /** Ein angeklickter gelber Tag verschwindet (askai: eine gesuchte Anfrage wird nicht erneut angeboten). */
    void searchTagUsed(String query) {
        List<SearchOffer> remaining = new ArrayList<SearchOffer>();
        for (SearchOffer offer : offeredSearches) {
            if (!offer.query.equalsIgnoreCase(query == null ? "" : query.trim())) {
                remaining.add(offer);
            }
        }
        offeredSearches = Collections.unmodifiableList(remaining);
        owner.stateChanged();
    }

    // ------------------------------------------------------------------ Context (tools)

    @Override
    public ConceptBranchService conceptBranchService() {
        return service;
    }

    @Override
    public boolean scopingWritable() {
        return owner.scopingWritable();
    }

    @Override
    public String stateDescription() {
        return owner.stateDescription();
    }

    @Override
    public void onConceptChanged(long newWorkingRevision) {
        owner.stateChanged();
    }

    @Override
    public String excludeTopic(String topic) {
        return excludeTopicCommand(topic);
    }

    @Override
    public String resolveConceptConflict(String conflictId, String decision) {
        return resolveConceptConflictCommand(conflictId, decision);
    }

    @Override
    public String offerSearches(String suggestionsJson) {
        return offerSearchesCommand(suggestionsJson);
    }

    @Override
    public List<String> blacklistedTerms() {
        return currentBlacklistTerms();
    }

    @Override
    public void conceptToolLog(String line) {
        technicalLog(line);
    }

    @Override
    public String scopeProbe(List<String> terms) {
        technicalLog("scope_probe -> PROBE_UNAVAILABLE (no scope measurement in this client yet)");
        return "PROBE_UNAVAILABLE: the scope measurement is not available yet — tell the user the "
                + "measurement is unavailable; do not guess.";
    }

    // ------------------------------------------------------------------ fence blocks

    /** Was mit dem Weidezaun in den Kontext geht: offene Konfliktfrage und Gesprächsführung. */
    String fenceBlocks() {
        return openConflictBlock(openConflictPaths()) + conversationPolicyBlock;
    }

    /**
     * The fence's OPEN CONCEPT CONFLICT block (empty string without conflicts): the model must find the
     * conflictId and the exact resolve operation in its context, never invent them.
     */
    static String openConflictBlock(Map<String, List<String>> conflicts) {
        if (conflicts.isEmpty()) {
            return "";
        }
        StringBuilder open = new StringBuilder();
        open.append("\nOPEN CONCEPT CONFLICT — you already asked the user whether the "
                + "excluded topic should ALSO be removed from the concept; interpret their "
                + "next answer against that question:\n");
        for (Map.Entry<String, List<String>> conflict : conflicts.entrySet()) {
            open.append("- conflictId \"").append(conflict.getKey()).append("\": card \"")
                    .append(conflict.getValue().get(conflict.getValue().size() - 1))
                    .append("\" is still in the concept\n");
        }
        open.append("If the user AGREES to the removal, answer with EXACTLY "
                + "{\"type\": \"resolve\", \"conflictId\": \"<id from above>\", "
                + "\"decision\": \"REMOVE\"}; if they DECLINE, use decision "
                + "\"KEEP_SUPPRESSED\". No other action in that turn.\n");
        return open.toString();
    }

    // ------------------------------------------------------------------ user turn (host-owned answers)

    /**
     * Gate 6 contract: while a concept conflict is PENDING, the host OWNS the next unambiguous yes/no — "Ja."
     * never reaches the model. Returns the deterministic host answer, or {@code null} when the turn goes to
     * the model.
     */
    String hostAnswerFor(String text) {
        if (hasOpenConflict()) {
            Boolean agrees = ConflictAnswerInterpreter.interpret(text);
            if (agrees != null) {
                return resolvePendingConflictFromHost(agrees.booleanValue());
            }
        } else if (staleDecisionTombstone != null) {
            String stale = staleDecisionTombstone;
            staleDecisionTombstone = null;
            Boolean agrees = ConflictAnswerInterpreter.interpret(text);
            if (agrees != null) {
                technicalLog("stale conflict confirmation -> deterministic host answer (no concept mutation)");
                boolean plural = stale.contains(", ");
                owner.stateChanged();
                return owner.isGerman()
                        ? "Die vorherige Rückfrage ist durch die manuelle Änderung "
                                + "veraltet. Am Konzept wurde nichts verändert. „" + stale
                                + "“ " + (plural ? "bleiben" : "bleibt")
                                + " ausgeschlossen und wird in der Konzeptprojektion "
                                + "unterdrückt."
                        : "The earlier question is outdated after the manual change. "
                                + "Nothing in the concept was changed. \"" + stale + "\" "
                                + (plural ? "remain" : "remains") + " excluded and "
                                + "suppressed in the concept projection.";
            }
            technicalLog("stale decision tombstone superseded by a substantive turn");
        }
        return null;
    }

    /**
     * KISS mission bookkeeping (live-gate 4): the FIRST user message becomes the mission — the fence
     * calibration needs one. The user (or the model, on a restated goal) can refine it later.
     */
    void recordMissionIfAbsent(String text) {
        String mission = text == null ? "" : text.trim();
        if (mission.isEmpty()) {
            return;
        }
        ResearchScopeCoordinator coordinator = owner.scopeCoordinator();
        if (coordinator == null || !coordinator.isUsable() || !coordinator.current().getMission().isEmpty()) {
            return;
        }
        if (mission.length() > 300) {
            mission = mission.substring(0, 300);
        }
        ScopeUpdateResult applied = coordinator.apply(
                new ScopePatch(Collections.singletonList(ScopePatchOperations.setMission(mission))),
                Collections.<UnresolvedScopeIssue>emptyList());
        if (applied.isApplied()) {
            technicalLog("scope update -> APPLIED: mission recorded from the user's first message");
        }
    }

    /**
     * Recompute the ratified conversation policy for the upcoming turn: pure projection over the CURRENT
     * snapshot (node-ID novelty, OPEN boundary material) — the scope check joins with the sweep later.
     */
    synchronized void refreshConversationPolicy() {
        ResearchScopeCoordinator coordinator = owner.scopeCoordinator();
        if (coordinator == null || !coordinator.isUsable()) {
            conversationPolicyBlock = "";
            return;
        }
        Set<String> ids = new HashSet<String>(service.allNodeIds());
        String epoch = service.currentEpoch();
        if (noveltyEpoch != null && noveltyEpoch.equals(epoch)) {
            boolean newCards = false;
            for (String id : ids) {
                if (!noveltyPreviousIds.contains(id)) {
                    newCards = true;
                    break;
                }
            }
            turnsWithoutNewCards = newCards ? 0 : turnsWithoutNewCards + 1;
        } else {
            turnsWithoutNewCards = 0; // a fresh epoch restarts the observation window
        }
        noveltyPreviousIds = ids;
        noveltyEpoch = epoch;

        ResearchScopeDraft draft = coordinator.current();
        boolean missionPresent = draft.getMission() != null && !draft.getMission().trim().isEmpty();
        boolean openConflict = false;
        int candidates = 0;
        synchronized (conceptConflicts) {
            for (ConceptConflictRef ref : conceptConflicts.values()) {
                if (ref.open) {
                    openConflict = true;
                } else {
                    candidates++;
                }
            }
        }
        boolean openBoundaryWork = openConflict || candidates > 0;
        ConversationPolicyProjection.Inputs inputs = new ConversationPolicyProjection.Inputs(
                missionPresent, ids.size(), turnsWithoutNewCards, openBoundaryWork,
                openConflict, false, null, false, 0, POLICY_SPARSE_CARDS, POLICY_NOVELTY_TURNS);
        ConversationPolicyProjection.Stage stage = ConversationPolicyProjection.stageOf(inputs);
        conversationPolicyBlock = ConversationPolicyProjection.hintBlock(stage, false);
        technicalLog(ConversationPolicyProjection.logLine(stage, inputs));
    }

    // ------------------------------------------------------------------ commands

    /**
     * The whole exclusion in ONE host-owned step: derive the id from the USER'S term, persist the EXCLUDED
     * facet (immediately effective), then scan the concept for an EXACT name conflict. The reply is the
     * structured JSON the tool returns; a conflict obliges the agent to INFORM_AND_ASK_REMOVE — this method
     * never touches the concept itself.
     */
    String excludeTopicCommand(String topic) {
        ResearchScopeCoordinator coordinator = owner.scopeCoordinator();
        if (coordinator == null) {
            return null;
        }
        String label = topic == null ? "" : topic.trim();
        String facetId = slugOf(label);
        com.google.gson.JsonObject reply = new com.google.gson.JsonObject();
        if (facetId.isEmpty()) {
            reply.addProperty("result", "REJECTED");
            reply.addProperty("reason", "the topic yields no technical id — pass the user's term");
            return reply.toString();
        }
        ScopeUpdateResult applied = coordinator.apply(
                new ScopePatch(Arrays.asList(
                        ScopePatchOperations.addFacet(facetId, label, "explicit user exclusion"),
                        ScopePatchOperations.excludeFacet(facetId, "explicit user exclusion"))),
                Collections.<UnresolvedScopeIssue>emptyList());
        if (applied.getStatus() == ScopeUpdateResult.Status.REJECTED) {
            technicalLog("exclude_topic -> REJECTED: " + applied.getReason());
            reply.addProperty("result", "REJECTED");
            reply.addProperty("reason", applied.getReason());
            return reply.toString();
        }
        technicalLog("exclude_topic -> EXCLUDED id=" + facetId + " label=\"" + label + "\"");
        reply.addProperty("result", "EXCLUDED");
        reply.addProperty("facetId", facetId);
        reply.addProperty("label", label);
        boolean german = owner.isGerman();
        StringBuilder userMessage = new StringBuilder(german
                ? "„" + label + "“ wurde ausgeschlossen und wird bei der Recherche unterdrückt."
                : "\"" + label + "\" has been excluded and will be suppressed during research.");
        List<String> conflictPath = ConceptTopicScanner.findExactPath(service.snapshot().getDocumentJson(), label);
        if (conflictPath == null) {
            owner.stateChanged();
            reply.addProperty("requiredResponse", "NONE");
            reply.addProperty("userMessage", userMessage.toString());
            return reply.toString();
        }
        if (!service.isLeafAt(conflictPath)) {
            // Safety-slice double guard, part 1: a BRANCH conflict is never offered for removal.
            owner.stateChanged();
            reply.addProperty("requiredResponse", "NONE");
            reply.addProperty("conceptBranchNote",
                    "the term matches a concept BRANCH with sub-cards — removal is manual "
                            + "editor work, no conflict question is asked");
            userMessage.append(german
                    ? " Der Begriff steht außerdem als Zweig mit Unterkarten im Konzept —"
                            + " dort entfernen ist bis zum Tree-Editor Handarbeit im"
                            + " Konzept-Editor; die Recherche dazu bleibt unabhängig davon"
                            + " unterdrückt."
                    : " The term also matches a concept BRANCH with sub-cards — removing it"
                            + " there is manual concept-editor work until the tree editor"
                            + " ships; research on it stays suppressed either way.");
            reply.addProperty("userMessage", userMessage.toString());
            technicalLog("exclude_topic -> concept BRANCH at " + conflictPath
                    + " — no removable conflict registered (manual editor work)");
            return reply.toString();
        }
        String nodeId = service.nodeIdAtPath(conflictPath);
        if (nodeId == null) {
            owner.stateChanged();
            reply.addProperty("requiredResponse", "NONE");
            reply.addProperty("userMessage", userMessage.toString());
            technicalLog("exclude_topic -> concept match at " + conflictPath
                    + " has no identity — no removable conflict registered");
            return reply.toString();
        }
        String conflictId = "conflict-" + conflictIds.incrementAndGet();
        staleDecisionTombstone = null;
        conceptConflicts.put(conflictId, new ConceptConflictRef(service.currentEpoch(),
                nodeId, service.snapshot().getWorkingRevision(), label, true));
        owner.stateChanged();
        com.google.gson.JsonObject conflict = new com.google.gson.JsonObject();
        conflict.addProperty("conflictId", conflictId);
        com.google.gson.JsonArray pathArray = new com.google.gson.JsonArray();
        for (String segment : conflictPath) {
            pathArray.add(segment);
        }
        conflict.add("path", pathArray);
        reply.add("conceptConflict", conflict);
        reply.addProperty("requiredResponse", "INFORM_AND_ASK_REMOVE");
        String parent = conflictPath.size() >= 2 ? conflictPath.get(conflictPath.size() - 2) : null;
        userMessage.append(german
                ? (parent == null
                        ? " Der Begriff steht außerdem noch auf oberster Ebene im Konzept."
                        : " Der Begriff steht außerdem noch im Konzept unter „" + parent + "“.")
                        + " Soll er dort ebenfalls entfernt werden?"
                : (parent == null
                        ? " The term also still sits at the top level of the concept."
                        : " The term also still appears in the concept under \"" + parent + "\".")
                        + " Should it be removed there as well?");
        reply.addProperty("userMessage", userMessage.toString());
        technicalLog("exclude_topic -> concept conflict " + conflictId + " at " + conflictPath);
        return reply.toString();
    }

    /** The user's answer arrives as its OWN command — the reference stays host-side by id. */
    String resolveConceptConflictCommand(String conflictId, String decision) {
        ConceptConflictRef ref = conceptConflicts.get(conflictId);
        if (ref == null || !ref.open) {
            return "Unknown conflict_id \"" + conflictId + "\" — use the id exclude_topic "
                    + "reported (it is single-use).";
        }
        com.google.gson.JsonObject reply = new com.google.gson.JsonObject();
        boolean german = owner.isGerman();
        if ("KEEP_SUPPRESSED".equalsIgnoreCase(decision)) {
            conceptConflicts.remove(conflictId);
            technicalLog("resolve_concept_conflict " + conflictId + " -> KEPT_SUPPRESSED");
            reply.addProperty("result", "KEPT_SUPPRESSED");
            reply.addProperty("userMessage", german
                    ? "Der Eintrag bleibt im Konzept stehen; die Recherche dazu bleibt "
                            + "unterdrückt."
                    : "The entry stays in the concept; research on it remains suppressed.");
            return reply.toString();
        }
        if (!"REMOVE".equalsIgnoreCase(decision)) {
            return "Unknown decision \"" + decision + "\" — allowed: REMOVE, KEEP_SUPPRESSED.";
        }
        // Full reason re-check IMMEDIATELY before the destructive commit: the reference's epoch must still be
        // current, the node must still exist, and the underlying exclusion must still stand.
        if (!ref.epoch.equals(service.currentEpoch())) {
            conceptConflicts.remove(conflictId);
            technicalLog("resolve_concept_conflict " + conflictId
                    + " -> REFUSED: identity epoch changed (reference discarded)");
            return "This conflict belongs to an earlier document state and cannot be executed "
                    + "anymore — nothing was removed.";
        }
        List<String> path = service.pathOfNodeId(ref.nodeId);
        if (path == null) {
            conceptConflicts.remove(conflictId);
            technicalLog("resolve_concept_conflict " + conflictId + " -> node gone — nothing to remove");
            reply.addProperty("result", "KEPT_SUPPRESSED");
            reply.addProperty("reason", "the entry no longer exists in the concept");
            reply.addProperty("userMessage", german
                    ? "Der Eintrag steht nicht mehr im Konzept — es gibt nichts zu entfernen."
                    : "The entry is no longer in the concept — there is nothing to remove.");
            return reply.toString();
        }
        boolean exclusionStillStands = false;
        for (String term : currentBlacklistTerms()) {
            if (term != null && term.trim().equalsIgnoreCase(ref.label.trim())) {
                exclusionStillStands = true;
                break;
            }
        }
        if (!exclusionStillStands) {
            conceptConflicts.remove(conflictId);
            technicalLog("resolve_concept_conflict " + conflictId
                    + " -> REFUSED: the exclusion was lifted (conflict reason gone)");
            reply.addProperty("result", "KEPT_SUPPRESSED");
            reply.addProperty("reason", "the underlying exclusion no longer exists");
            reply.addProperty("userMessage", german
                    ? "Der Ausschluss besteht nicht mehr — der Eintrag bleibt unverändert im "
                            + "Konzept."
                    : "The exclusion no longer stands — the entry stays in the concept "
                            + "unchanged.");
            return reply.toString();
        }
        if (!service.isLeafAt(path)) {
            conceptConflicts.remove(conflictId);
            technicalLog("resolve_concept_conflict " + conflictId
                    + " -> REFUSED: no longer a leaf at " + path
                    + " (kept suppressed, removal is manual editor work)");
            reply.addProperty("result", "KEPT_SUPPRESSED");
            reply.addProperty("reason", "the entry grew into a branch — a non-leaf is never "
                    + "removed by the host; use the concept editor");
            reply.addProperty("userMessage", german
                    ? "Der Eintrag „" + path.get(path.size() - 1) + "“ hat inzwischen "
                            + "Unterkarten und wird deshalb nicht automatisch entfernt — das "
                            + "geht bis zum Tree-Editor nur im Konzept-Editor. Die Recherche "
                            + "dazu bleibt unterdrückt."
                    : "The entry \"" + path.get(path.size() - 1) + "\" now has sub-cards and "
                            + "is therefore not removed automatically — until the tree editor "
                            + "that is concept-editor work. Research on it stays suppressed.");
            return reply.toString();
        }
        ConceptBranchService.EditResult removed = service.removeNodeAt(path);
        if (!removed.isApplied()) {
            return removed.getDiagnostic().describeForModel();
        }
        conceptConflicts.remove(conflictId);
        technicalLog("resolve_concept_conflict " + conflictId + " -> REMOVED " + path
                + " revision=" + removed.getNewRevision());
        reply.addProperty("result", "REMOVED");
        com.google.gson.JsonArray pathArray = new com.google.gson.JsonArray();
        for (String segment : path) {
            pathArray.add(segment);
        }
        reply.add("path", pathArray);
        reply.addProperty("userMessage", german
                ? "Der Eintrag „" + path.get(path.size() - 1) + "“ wurde aus dem Konzept "
                        + "entfernt."
                : "The entry \"" + path.get(path.size() - 1) + "\" has been removed from the "
                        + "concept.");
        return reply.toString();
    }

    /** The HOST resolves the pending conflict deterministically (yes/no answer): oldest conflict first. */
    private String resolvePendingConflictFromHost(boolean remove) {
        String conflictId = firstOpenConflictId();
        if (conflictId == null) {
            return null;
        }
        String reply = resolveConceptConflictCommand(conflictId, remove ? "REMOVE" : "KEEP_SUPPRESSED");
        String userMessage = userMessageOf(reply);
        owner.stateChanged();
        return userMessage != null ? userMessage : reply;
    }

    /**
     * The search-offer command (gate 8b): the model authored the suggestions, the HOST renders them as the
     * user's yellow exploration tags (display state; a later offer replaces it).
     */
    String offerSearchesCommand(String suggestionsJson) {
        List<SearchOffer> suggestions = parseOfferedSearches(suggestionsJson);
        if (suggestions.isEmpty() && isEmptyArray(suggestionsJson)) {
            // Der recherchierte Stand gibt keine Anschlussfrage her: der alte Satz passt nicht mehr und geht.
            offeredSearches = Collections.emptyList();
            technicalLog("offer_searches -> CLEARED");
            owner.stateChanged();
            com.google.gson.JsonObject reply = new com.google.gson.JsonObject();
            reply.addProperty("result", "CLEARED");
            reply.addProperty("count", 0);
            return reply.toString();
        }
        if (suggestions.isEmpty()) {
            return "No usable suggestion — every entry needs a non-empty \"query\". Example: "
                    + "[{\"query\":\"FreeRTOS ESP32 Grundlagen Tutorial\","
                    + "\"purpose\":\"Einstieg sichten\"}]";
        }
        offeredSearches = Collections.unmodifiableList(suggestions);
        searchTagsOffered = true;
        technicalLog("offer_searches -> OFFERED " + suggestions.size() + " tags");
        owner.stateChanged();
        com.google.gson.JsonObject reply = new com.google.gson.JsonObject();
        reply.addProperty("result", "OFFERED");
        reply.addProperty("count", suggestions.size());
        return reply.toString();
    }

    private static boolean isEmptyArray(String suggestionsJson) {
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(suggestionsJson);
            return parsed.isJsonArray() && parsed.getAsJsonArray().size() == 0;
        } catch (RuntimeException notJson) {
            return false;
        }
    }

    private static List<SearchOffer> parseOfferedSearches(String suggestionsJson) {
        List<SearchOffer> suggestions = new ArrayList<SearchOffer>();
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(suggestionsJson);
            if (!parsed.isJsonArray()) {
                return suggestions;
            }
            for (com.google.gson.JsonElement element : parsed.getAsJsonArray()) {
                if (suggestions.size() >= MAX_OFFERED_SEARCHES || !element.isJsonObject()) {
                    continue;
                }
                com.google.gson.JsonObject object = element.getAsJsonObject();
                String query = object.has("query") && object.get("query").isJsonPrimitive()
                        ? object.get("query").getAsString().trim() : "";
                String purpose = object.has("purpose") && object.get("purpose").isJsonPrimitive()
                        ? object.get("purpose").getAsString().trim() : "";
                if (!query.isEmpty()) {
                    suggestions.add(new SearchOffer(query, purpose));
                }
            }
        } catch (RuntimeException notJson) {
            // empty list: the tool answers with the honest example
        }
        return suggestions;
    }

    // ------------------------------------------------------------------ identity boundary

    /**
     * Every conflict (open AND candidate) dies with a transient boundary — never revived from history; then
     * the deterministic reconciliation derives fresh CANDIDATES from the new head.
     */
    private void discardTransientsAndReconcile(String reason, String newEpoch) {
        int discarded;
        List<String> discardedOpenLabels = new ArrayList<String>();
        synchronized (conceptConflicts) {
            discarded = conceptConflicts.size();
            for (ConceptConflictRef ref : conceptConflicts.values()) {
                if (ref.open) {
                    discardedOpenLabels.add(ref.label);
                }
            }
            conceptConflicts.clear();
        }
        if (discarded > 0) {
            technicalLog("concept identity -> " + discarded + " transient conflict(s) discarded (" + reason + ")");
        }
        if (!discardedOpenLabels.isEmpty()) {
            staleDecisionTombstone = joined(discardedOpenLabels);
            technicalLog("concept identity -> stale decision tombstone armed for \"" + staleDecisionTombstone + "\"");
        }
        owner.stateChanged();
        reconcileBlacklistAgainstConcept(reason);
    }

    private void reconcileBlacklistAgainstConcept(String reason) {
        if (service.isFailClosed()) {
            return;
        }
        String summary = ConceptBlacklistReconciler.reconcile(service, exclusionIdentities(),
                new ConceptBlacklistReconciler.CandidateRegistrar() {
                    @Override
                    public void candidate(String exclusionId, String label, String nodeId) {
                        conceptConflicts.put("conflict-" + conflictIds.incrementAndGet(),
                                new ConceptConflictRef(service.currentEpoch(), nodeId,
                                        service.snapshot().getWorkingRevision(), label, false));
                    }
                });
        technicalLog("concept identity -> reconciliation after " + reason + ": " + summary);
    }

    /** One entry per exclusion IDENTITY: facets id→label, plain exclusions only when no facet covers them. */
    private LinkedHashMap<String, String> exclusionIdentities() {
        LinkedHashMap<String, String> exclusions = new LinkedHashMap<String, String>();
        ResearchScopeCoordinator coordinator = owner.scopeCoordinator();
        if (coordinator == null || !coordinator.isUsable()) {
            return exclusions;
        }
        ResearchScopeDraft draft = coordinator.current();
        for (ScopeFacet facet : draft.excludedFacets()) {
            exclusions.put(facet.getFacetId(), facet.getLabel());
        }
        for (String exclusion : draft.getExclusions()) {
            boolean covered = false;
            for (String label : exclusions.values()) {
                if (label.trim().equalsIgnoreCase(exclusion.trim())) {
                    covered = true;
                    break;
                }
            }
            if (!covered && !exclusion.trim().isEmpty()) {
                exclusions.put(exclusion, exclusion);
            }
        }
        return exclusions;
    }

    // ------------------------------------------------------------------ helpers

    /** The blacklist truth for the concept tools: excluded labels/ids + plain exclusions. */
    List<String> currentBlacklistTerms() {
        ResearchScopeCoordinator coordinator = owner.scopeCoordinator();
        if (coordinator == null || !coordinator.isUsable()) {
            return Collections.emptyList();
        }
        ResearchScopeDraft draft = coordinator.current();
        LinkedHashSet<String> terms = new LinkedHashSet<String>();
        for (ScopeFacet facet : draft.excludedFacets()) {
            terms.add(facet.getLabel());
            terms.add(facet.getFacetId());
        }
        terms.addAll(draft.getExclusions());
        terms.remove("");
        return new ArrayList<String>(terms);
    }

    private boolean hasOpenConflict() {
        return firstOpenConflictId() != null;
    }

    private String firstOpenConflictId() {
        synchronized (conceptConflicts) {
            for (Map.Entry<String, ConceptConflictRef> entry : conceptConflicts.entrySet()) {
                if (entry.getValue().open) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /** The OPEN conflicts' CURRENT name paths (resolved via the ID sidecar at render time). */
    private Map<String, List<String>> openConflictPaths() {
        Map<String, List<String>> paths = new LinkedHashMap<String, List<String>>();
        synchronized (conceptConflicts) {
            for (Map.Entry<String, ConceptConflictRef> entry : conceptConflicts.entrySet()) {
                if (!entry.getValue().open || !entry.getValue().epoch.equals(service.currentEpoch())) {
                    continue;
                }
                List<String> path = service.pathOfNodeId(entry.getValue().nodeId);
                if (path != null) {
                    paths.put(entry.getKey(), path);
                }
            }
        }
        return paths;
    }

    /** The receipt's user-facing sentence, or {@code null} when the reply is no such JSON. */
    static String userMessageOf(String replyJson) {
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(replyJson);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("userMessage")) {
                return parsed.getAsJsonObject().get("userMessage").getAsString();
            }
        } catch (RuntimeException notJson) {
            // fall through — the caller shows the raw reply honestly
        }
        return null;
    }

    private static String joined(List<String> labels) {
        StringBuilder sb = new StringBuilder();
        for (String label : labels) {
            sb.append(sb.length() > 0 ? ", " : "").append(label);
        }
        return sb.toString();
    }

    /** A stable, short, lowercase-ascii id from a human term. */
    static String slugOf(String label) {
        String lower = label.toLowerCase(Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        StringBuilder sb = new StringBuilder();
        boolean gap = false;
        for (int index = 0; index < lower.length() && sb.length() < 40; index++) {
            char character = lower.charAt(index);
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                if (gap && sb.length() > 0) {
                    sb.append('-');
                }
                sb.append(character);
                gap = false;
            } else {
                gap = true;
            }
        }
        return sb.toString();
    }

    private static void technicalLog(String line) {
        LOG.info("[research-concept] " + line);
    }
}
