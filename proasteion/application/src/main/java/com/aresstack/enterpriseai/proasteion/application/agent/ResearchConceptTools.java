package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Konzeptpapier-Werkzeuge des Recherche-Agenten (Konzeptbaum mit Inseln), aus askai-java8 (arch)
 * {@code ResearchToolPolicy} übernommen: {@code concept_read}, {@code concept_add_cards}, {@code concept_add},
 * {@code concept_rename}, {@code concept_move_leaf}, {@code exclude_topic}, {@code resolve_concept_conflict},
 * {@code offer_searches} und {@code scope_probe} als MCP-Werkzeuge. Lesen geht immer, Schreiben nur in der
 * Scoping-Phase ({@link Context#scopingWritable()}); die Zustandsmaschine der Phasen folgt mit der Recherche.
 */
public final class ResearchConceptTools {

    private ResearchConceptTools() {
    }

    /** Was die Werkzeuge vom Host brauchen (askai {@code ResearchControlContext}, Ausschnitt Konzeptpapier). */
    public interface Context {

        ConceptBranchService conceptBranchService();

        /** Ob Schreibwerkzeuge gerade erlaubt sind (askai: SCOPING/running). */
        boolean scopingWritable();

        /** Phase/Zustand für die ehrliche Ablehnung. */
        String stateDescription();

        void onConceptChanged(long newWorkingRevision);

        String excludeTopic(String topic);

        String resolveConceptConflict(String conflictId, String decision);

        String offerSearches(String suggestionsJson);

        List<String> blacklistedTerms();

        void conceptToolLog(String line);

        String scopeProbe(List<String> terms);
    }

    /** Das angebotene Werkzeugset; ohne Konzeptdienst keine Konzeptwerkzeuge. */
    public static List<McpToolContribution> toolsFor(Context ctx) {
        List<McpToolContribution> tools = new ArrayList<McpToolContribution>();
        if (ctx.conceptBranchService() != null) {
            tools.add(conceptReadTool(ctx));
            if (ctx.scopingWritable()) {
                tools.add(conceptAddCardsTool(ctx));
                tools.add(conceptAddTool(ctx));
                tools.add(conceptRenameTool(ctx));
                tools.add(conceptMoveLeafTool(ctx));
                tools.add(excludeTopicTool(ctx));
                tools.add(resolveConflictTool(ctx));
                tools.add(offerSearchesTool(ctx));
                tools.add(scopeProbeTool(ctx));
            }
        }
        return tools;
    }

    /**
     * Das Werkzeugset eines noch nicht angelegten Projekts (Konzept/neu: nur lesen), ohne es anzulegen. Nur für die
     * Beschreibung des Werkzeugsatzes: ausgeführt wird jeder Aufruf über den Host, der das Projekt dann öffnet.
     */
    static List<McpToolContribution> freshProjectTools() {
        List<McpToolContribution> tools = new ArrayList<McpToolContribution>();
        tools.add(conceptReadTool(null));
        return tools;
    }

    /** Serverseitige Nachprüfung beim Ausführen (Sichtbarkeit ist keine Berechtigung). */
    private static McpToolResult requireWritable(Context ctx) {
        if (!ctx.scopingWritable()) {
            return McpToolResult.error("Not allowed in the current state (" + ctx.stateDescription() + ").");
        }
        return null;
    }

    // ------------------------------------------------------------------ Konzeptpapier tools
    //
    // The SMALL-MODEL facade (K2c, the MainframeMate lesson): tiny atomic operations addressed
    // by a human-readable name path — no handles, no revisions, no full-branch replacement in
    // the model contract. Examples live IN the tool description; required arguments are
    // validated server-side BEFORE dispatch; all transactional machinery (strict parse,
    // candidate validation, atomic commit) runs inside ConceptBranchService on every call.

    private static McpToolContribution conceptReadTool(final Context ctx) {
        return McpToolContribution.of("concept_read",
                "Read the concept tree or one of its branches. "
                        + "Example: path=\"\" (whole concept). Example: path=\"FreeRTOS\". "
                        + "Example: path=\"FreeRTOS/Kommunikation\".",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        com.aresstack.enterpriseai.research.concept.ConceptBranchService.ReadResult result =
                                ctx.conceptBranchService()
                                        .readBranch(segmentsOf(call, "path"), 0);
                        if (!result.isOk()) {
                            return McpToolResult.error(result.getDiagnostic().describeForModel());
                        }
                        StringBuilder sb = new StringBuilder();
                        // Revision as DISPLAYED state (the CURRENT_CONCEPT block cites it) — it
                        // is never a token the model must echo back.
                        sb.append("revision=").append(result.getWorkingRevision()).append('\n');
                        if (result.getParentName() != null) {
                            sb.append("parent=").append(result.getParentName()).append('\n');
                        }
                        if (!result.getSiblingNames().isEmpty()) {
                            sb.append("siblings=");
                            for (int i = 0; i < result.getSiblingNames().size(); i++) {
                                sb.append(i > 0 ? ", " : "").append(result.getSiblingNames().get(i));
                            }
                            sb.append('\n');
                        }
                        sb.append(result.getBranchJson());
                        // Zielbild auto-cleanup: a read hands the model the branch AND the
                        // relevant blacklist truth — a later rewrite must omit these names.
                        String suppressed = suppressedNamesIn(result.getBranchJson(), ctx);
                        if (!suppressed.isEmpty()) {
                            sb.append("\nSUPPRESSED IN THIS BRANCH (blacklisted — research on "
                                    + "them stays suppressed): ").append(suppressed);
                            // Slice-2 gate observability finding: the hint reached only the
                            // model — the technical log must show the suppression truth too.
                            ctx.conceptToolLog("concept_read -> SUPPRESSED IN THIS BRANCH: "
                                    + suppressed);
                        }
                        return McpToolResult.ok(sb.toString());
                    }
                },
                McpToolParameter.string("path", false,
                        "Node names from the concept root, separated by '/'. Empty = whole concept."),
                McpToolParameter.string("path_json", false,
                        "The segments as a JSON array of card names — the unambiguous form"));
    }

    /**
     * The ATOMIC list add: all genuinely new cards of one call appear under ONE parent in ONE
     * revision — or none at all. Blacklisted names are skipped as {@code SUPPRESSED_BY_SCOPE}
     * (the blacklist stays authoritative), existing siblings come back {@code ALREADY_PRESENT}
     * (idempotent repeats bump nothing). The receipt names every input's fate — the model may
     * only claim what the receipt confirms.
     */
    private static McpToolContribution conceptAddCardsTool(final Context ctx) {
        return McpToolContribution.of("concept_add_cards",
                "Add ALL named topic cards in ONE atomic step under one parent (empty parent = "
                        + "top level; a plain topic list stays FLAT). Example: parent_path=\"\", "
                        + "names_json=[\"Grundlagen\",\"Architektur\",\"Debugging\"].",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        java.util.List<String> names = namesOf(call.getString("names_json"));
                        if (names == null || names.isEmpty()) {
                            return McpToolResult.error("Missing argument: names_json — a JSON "
                                    + "array of card names, e.g. names_json=[\"Grundlagen\","
                                    + "\"Computer Science\"] (multi-word names stay ONE name)");
                        }
                        java.util.List<String> allowed = new java.util.ArrayList<String>();
                        java.util.List<String> suppressed = new java.util.ArrayList<String>();
                        for (String name : names) {
                            if (isBlacklisted(name, ctx)) {
                                suppressed.add(name);
                            } else {
                                allowed.add(name);
                            }
                        }
                        long revision;
                        String createdParent = null;
                        java.util.List<String> added;
                        java.util.List<String> alreadyPresent;
                        if (allowed.isEmpty()) {
                            added = java.util.Collections.emptyList();
                            alreadyPresent = java.util.Collections.emptyList();
                            revision = ctx.conceptBranchService().snapshot()
                                    .getWorkingRevision();
                        } else {
                            com.aresstack.enterpriseai.research.concept.ConceptBranchService
                                    .AddCardsResult result = ctx.conceptBranchService()
                                    .addCards(segmentsOf(call, "parent_path"), allowed);
                            if (!result.isApplied()) {
                                // The parent gate failed → the WHOLE call fails, no mutation.
                                return McpToolResult.error(
                                        result.getDiagnostic().describeForModel());
                            }
                            added = result.getAdded();
                            alreadyPresent = result.getAlreadyPresent();
                            createdParent = result.getCreatedParent();
                            revision = result.getNewRevision();
                            if (!added.isEmpty() || createdParent != null) {
                                ctx.onConceptChanged(revision);
                            }
                        }
                        // NO_CHANGE is honest bookkeeping (the gate saw a misleading APPLIED
                        // on a batch that changed nothing); every single receipt line is
                        // mirrored into the technical log — the live observer must see the
                        // same fates the model sees.
                        boolean changed = !added.isEmpty() || createdParent != null;
                        StringBuilder receipt = new StringBuilder(
                                changed ? "APPLIED" : "NO_CHANGE")
                                .append(" revision=").append(revision);
                        java.util.List<String> lines = new java.util.ArrayList<String>();
                        if (createdParent != null) {
                            lines.add("CREATED_PARENT: " + createdParent);
                        }
                        for (String name : added) {
                            lines.add("ADDED: " + name);
                        }
                        for (String name : alreadyPresent) {
                            lines.add("ALREADY_PRESENT: " + name);
                        }
                        for (String name : suppressed) {
                            lines.add("SUPPRESSED_BY_SCOPE: " + name);
                        }
                        ctx.conceptToolLog("concept_add_cards -> "
                                + (changed ? "APPLIED" : "NO_CHANGE")
                                + " revision=" + revision);
                        for (String line : lines) {
                            receipt.append('\n').append(line);
                            ctx.conceptToolLog("concept_add_cards -> " + line);
                        }
                        return McpToolResult.ok(receipt.toString());
                    }
                },
                McpToolParameter.string("parent_path", false,
                        "The parent card's names from the concept root, separated by '/'. "
                                + "Empty = top-level cards."),
                McpToolParameter.string("parent_path_json", false,
                        "The parent segments as a JSON array of card names — the unambiguous "
                                + "form"),
                McpToolParameter.string("names_json", true,
                        "The card names as a JSON array (preferred). Comma-, semicolon-, "
                                + "newline- or bullet-separated lists are tolerated; bare "
                                + "spaces are NEVER split."));
    }

    /**
     * Permissive technical list parse: a JSON array is canonical; otherwise newline-, bullet-,
     * semicolon- and comma-separated entries are accepted, with double quotes protecting
     * multi-word segments. Bare spaces are NEVER a separator ("Computer Science" is one name).
     */
    static java.util.List<String> namesOf(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String text = raw.trim();
        try {
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(text);
            if (parsed.isJsonArray()) {
                java.util.List<String> names = new java.util.ArrayList<String>();
                for (com.google.gson.JsonElement element : parsed.getAsJsonArray()) {
                    if (element.isJsonPrimitive()) {
                        names.add(element.getAsString());
                    }
                }
                return names;
            }
        } catch (RuntimeException notJson) {
            // fall through to the permissive separators
        }
        java.util.List<String> names = new java.util.ArrayList<String>();
        for (String line : text.split("\\r?\\n")) {
            String entry = line.trim();
            // Strip bullet markers ("- ", "* ", "• ", "3. ") — one per line.
            entry = entry.replaceFirst("^([-*•]|\\d+[.)])\\s+", "");
            for (String part : entry.split("[;,]")) {
                String name = part.trim();
                if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
                    name = name.substring(1, name.length() - 1).trim();
                }
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static McpToolContribution conceptAddTool(final Context ctx) {
        return McpToolContribution.of("concept_add",
                "Add ONE new topic card to the concept. "
                        + "Example: parent_path=\"\", name=\"FreeRTOS\" (a top-level card). "
                        + "Example: parent_path=\"FreeRTOS/Kommunikation\", "
                        + "name=\"Task Notifications\". "
                        + "If unsure whether the parent exists, call concept_read first.",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        String name = call.getString("name");
                        if (name == null || name.trim().isEmpty()) {
                            return McpToolResult.error("Missing argument: name — example: "
                                    + "parent_path=\"FreeRTOS\", name=\"Synchronisation\"");
                        }
                        com.aresstack.enterpriseai.research.concept.ConceptBranchService.EditResult result =
                                ctx.conceptBranchService().addNode(
                                        segmentsOf(call, "parent_path"), name.trim());
                        if (!result.isApplied()) {
                            return McpToolResult.error(result.getDiagnostic().describeForModel());
                        }
                        ctx.onConceptChanged(result.getNewRevision());
                        return McpToolResult.ok("added \"" + name.trim() + "\" revision="
                                + result.getNewRevision());
                    }
                },
                McpToolParameter.string("parent_path", false,
                        "The parent card's names from the concept root, separated by '/'. "
                                + "Empty = add a top-level card."),
                McpToolParameter.string("parent_path_json", false,
                        "The parent segments as a JSON array of card names — the unambiguous "
                                + "form ('/' in a name stays a character)"),
                McpToolParameter.string("name", true, "The new card's name (short noun phrase)"));
    }

    /** Rename ONE card, any depth — never a deletion; children and position stay untouched. */
    private static McpToolContribution conceptRenameTool(final Context ctx) {
        return McpToolContribution.of("concept_rename",
                "Rename ONE concept card; its children and position stay untouched. "
                        + "Example: path=\"FreeRTOS/Setup\", name=\"ESP32-Entwicklung mit "
                        + "Arduino\".",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        String name = call.getString("name");
                        if (name == null || name.trim().isEmpty()) {
                            return McpToolResult.error("Missing argument: name — example: "
                                    + "path=\"FreeRTOS/Setup\", name=\"ESP32-Entwicklung\"");
                        }
                        java.util.List<String> path = segmentsOf(call, "path");
                        com.aresstack.enterpriseai.research.concept.ConceptBranchService.EditResult result =
                                ctx.conceptBranchService().renameNode(path, name.trim());
                        if (!result.isApplied()) {
                            return McpToolResult.error(result.getDiagnostic().describeForModel());
                        }
                        ctx.onConceptChanged(result.getNewRevision());
                        String note = isBlacklisted(name.trim(), ctx)
                                ? " (note: \"" + name.trim() + "\" is blacklisted — the card "
                                        + "will stay suppressed for research)"
                                : "";
                        return McpToolResult.ok("renamed to \"" + name.trim() + "\" revision="
                                + result.getNewRevision() + note);
                    }
                },
                McpToolParameter.string("path", false,
                        "The card's names from the concept root, separated by '/'"),
                McpToolParameter.string("path_json", false,
                        "The segments as a JSON array of card names — the unambiguous form"),
                McpToolParameter.string("name", true, "The new card name — ONE label"));
    }

    /**
     * The atomic leaf move: one leaf, one target parent, one revision, UNCHANGED UUID. Every
     * receipt line mirrors into the technical log; every refusal ({@code SOURCE_NOT_LEAF},
     * {@code TARGET_PARENT_NOT_FOUND}, {@code AMBIGUOUS_*}, {@code TARGET_NAME_COLLISION})
     * leaves document and sidecar byte-identical.
     */
    private static McpToolContribution conceptMoveLeafTool(final Context ctx) {
        return McpToolContribution.of("concept_move_leaf",
                "Move ONE existing LEAF card under an existing parent (or the top level). "
                        + "Never a branch, never creates the target. Example: "
                        + "source_json=[\"Scheduling\"], parent_path_json=[\"FreeRTOS\"]; "
                        + "parent_path_json=[] moves to the top level.",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        java.util.List<String> source = segmentsOf(call, "source");
                        if (source.isEmpty()) {
                            return McpToolResult.error("Missing argument: source_json — the "
                                    + "leaf as a JSON array of card names (a globally unique "
                                    + "single name is enough), e.g. "
                                    + "source_json=[\"Scheduling\"]");
                        }
                        com.aresstack.enterpriseai.research.concept.ConceptBranchService
                                .MoveLeafResult result = ctx.conceptBranchService()
                                .moveLeaf(source, segmentsOf(call, "parent_path"));
                        if (!result.isApplied()) {
                            String diagnostic = result.getDiagnostic().describeForModel();
                            ctx.conceptToolLog("concept_move_leaf -> REFUSED "
                                    + diagnostic.replace("\r", "").replace('\n', ' '));
                            return McpToolResult.error(diagnostic);
                        }
                        java.util.List<String> lines = new java.util.ArrayList<String>();
                        StringBuilder receipt;
                        if (result.isNoChange()) {
                            receipt = new StringBuilder("NO_CHANGE revision=")
                                    .append(result.getNewRevision());
                            lines.add("ALREADY_AT_TARGET: " + result.getLabel());
                        } else {
                            receipt = new StringBuilder("APPLIED revision=")
                                    .append(result.getNewRevision());
                            lines.add("MOVED: " + result.getLabel());
                            lines.add("FROM: " + result.getFromPath());
                            lines.add("TO: " + result.getToPath());
                            ctx.onConceptChanged(result.getNewRevision());
                        }
                        lines.add("ID: " + result.getNodeId());
                        ctx.conceptToolLog("concept_move_leaf -> "
                                + (result.isNoChange() ? "NO_CHANGE" : "APPLIED")
                                + " revision=" + result.getNewRevision());
                        for (String line : lines) {
                            receipt.append('\n').append(line);
                            ctx.conceptToolLog("concept_move_leaf -> " + line);
                        }
                        return McpToolResult.ok(receipt.toString());
                    }
                },
                McpToolParameter.string("source", false,
                        "The leaf's names from the concept root, separated by '/' (or ONE "
                                + "globally unique card name)"),
                McpToolParameter.string("source_json", false,
                        "The leaf as a JSON array of card names — the unambiguous form"),
                McpToolParameter.string("parent_path", false,
                        "The target parent's names separated by '/'. Empty = top level."),
                McpToolParameter.string("parent_path_json", false,
                        "The target parent segments as a JSON array ([] = top level)"));
    }

    /** Case-insensitive exact match against the session's blacklist terms. */
    private static boolean isBlacklisted(String name, Context ctx) {
        String normalized = name == null ? "" : name.trim().toLowerCase(java.util.Locale.ROOT);
        for (String term : ctx.blacklistedTerms()) {
            if (normalized.equals(term == null ? ""
                    : term.trim().toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** The blacklisted card names present in a branch JSON, comma-joined ("" when none). */
    private static String suppressedNamesIn(String branchJson, Context ctx) {
        java.util.List<String> terms = ctx.blacklistedTerms();
        if (terms.isEmpty()) {
            return "";
        }
        java.util.LinkedHashSet<String> hits = new java.util.LinkedHashSet<String>();
        for (java.util.List<String> path : com.aresstack.enterpriseai.research.concept
                .ConceptTopicScanner.collectCardPaths(
                        "{\"concept\":[" + branchJson + "]}")) {
            String card = path.get(path.size() - 1);
            if (isBlacklisted(card, ctx)) {
                hits.add(card);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String hit : hits) {
            sb.append(sb.length() > 0 ? ", " : "").append(hit);
        }
        return sb.toString();
    }

    /**
     * The ONE-command exclusion facade: the model hands over the USER'S term verbatim; the host
     * derives the id, records the EXCLUDED facet (immediately effective), republishes the fence
     * and scans the concept for an EXACT name match. A conflict comes back as a structured
     * {@code conceptConflict} with an opaque conflictId + {@code INFORM_AND_ASK_REMOVE} — the
     * tool NEVER deletes anything itself.
     */
    private static McpToolContribution excludeTopicTool(final Context ctx) {
        return McpToolContribution.of("exclude_topic",
                "Record a topic the user ruled out, in the USER'S words. The application derives "
                        + "the id, suppresses the topic for all research and checks the concept "
                        + "for a conflicting entry. Example: topic=\"ESP-IDF\".",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        String topic = call.getString("topic");
                        if (topic == null || topic.trim().isEmpty()) {
                            return McpToolResult.error(
                                    "Missing argument: topic — example: topic=\"ESP-IDF\"");
                        }
                        String reply = ctx.excludeTopic(topic.trim());
                        if (reply == null) {
                            return McpToolResult.error("This session has no scope system.");
                        }
                        // A rejected commit is the tool's REJECTION (ToolFailure on the runtime
                        // side, repair budget) — never a green result.
                        return reply.contains("\"result\":\"REJECTED\"")
                                ? McpToolResult.error(reply) : McpToolResult.ok(reply);
                    }
                },
                McpToolParameter.string("topic", true,
                        "The excluded topic in the user's own words, e.g. \"ESP-IDF\""));
    }

    /** The search-offer command: 3-5 AI-authored lookups rendered as clickable exploration tags. */
    private static McpToolContribution offerSearchesTool(final Context ctx) {
        return McpToolContribution.of("offer_searches",
                "Offer 3-5 orientation searches the user can run with one click (yellow tags). "
                        + "Nothing runs by itself. Example: suggestions_json="
                        + "[{\"query\":\"FreeRTOS ESP32 Grundlagen Tutorial\","
                        + "\"purpose\":\"Einstieg sichten\"}]",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        String suggestionsJson = call.getString("suggestions_json");
                        if (suggestionsJson == null || suggestionsJson.trim().isEmpty()) {
                            return McpToolResult.error("Missing argument: suggestions_json — "
                                    + "example: [{\"query\":\"FreeRTOS ESP32 Grundlagen "
                                    + "Tutorial\",\"purpose\":\"Einstieg sichten\"}]");
                        }
                        String reply = ctx.offerSearches(suggestionsJson.trim());
                        if (reply == null) {
                            return McpToolResult.error("This session has no scoping surface.");
                        }
                        return reply.startsWith("{") ? McpToolResult.ok(reply)
                                : McpToolResult.error(reply);
                    }
                },
                McpToolParameter.string("suggestions_json", true,
                        "JSON array of {query, purpose} objects — the user's exploration tags"));
    }

    /** scope_probe (AP3): read-only semantic measurement — a sensor, never an author. */
    private static McpToolContribution scopeProbeTool(final Context ctx) {
        return McpToolContribution.of("scope_probe",
                "READ-ONLY: measure where the given terms sit relative to the negotiated "
                        + "research scope (LIKELY_IN / LIKELY_OUT / BOUNDARY / NOVEL). Changes "
                        + "nothing. Use it only for the late closure check. Example: "
                        + "terms_json=[\"Priority Inversion\",\"SMP Scheduling\"].",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        java.util.List<String> terms = namesOf(call.getString("terms_json"));
                        if (terms == null || terms.isEmpty()) {
                            return McpToolResult.error("Missing argument: terms_json — a JSON "
                                    + "array of terms, e.g. terms_json=[\"Priority Inversion\","
                                    + "\"SMP Scheduling\"]");
                        }
                        String reply = ctx.scopeProbe(terms);
                        if (reply == null) {
                            return McpToolResult.error("This session has no scope system.");
                        }
                        // STALE_FENCE / PROBE_FAILED / PROBE_UNAVAILABLE are honest sensor
                        // rejections — never a green result over discarded readings.
                        return reply.startsWith("PROBED")
                                ? McpToolResult.ok(reply) : McpToolResult.error(reply);
                    }
                },
                McpToolParameter.string("terms_json", true,
                        "The terms to probe as a JSON array of strings"));
    }

    /** Resolve a reported concept conflict — only ever AFTER the user answered the question. */
    private static McpToolContribution resolveConflictTool(final Context ctx) {
        return McpToolContribution.of("resolve_concept_conflict",
                "Resolve a concept conflict exclude_topic reported, AFTER the user decided: "
                        + "decision=REMOVE deletes the concept entry, decision=KEEP_SUPPRESSED "
                        + "keeps it (research stays suppressed). Example: "
                        + "conflict_id=\"conflict-1\" decision=\"REMOVE\".",
                new McpToolHandler() {
                    public McpToolResult invoke(McpToolCall call) {
                        McpToolResult denied = requireWritable(ctx);
                        if (denied != null) {
                            return denied;
                        }
                        String conflictId = call.getString("conflict_id");
                        String decision = call.getString("decision");
                        if (conflictId == null || conflictId.trim().isEmpty()
                                || decision == null || decision.trim().isEmpty()) {
                            return McpToolResult.error("Missing argument: conflict_id and "
                                    + "decision are required — example: "
                                    + "conflict_id=\"conflict-1\" decision=\"REMOVE\"");
                        }
                        String reply = ctx.resolveConceptConflict(conflictId.trim(),
                                decision.trim());
                        return reply == null
                                ? McpToolResult.error("This session has no scope system.")
                                : (reply.startsWith("{") ? McpToolResult.ok(reply)
                                        : McpToolResult.error(reply));
                    }
                },
                McpToolParameter.string("conflict_id", true,
                        "The opaque id from exclude_topic's conceptConflict"),
                McpToolParameter.string("decision", true,
                        "REMOVE (delete the concept entry) or KEEP_SUPPRESSED (keep it)"));
    }

    /**
     * Segment resolution with the UNAMBIGUOUS form first: {@code <name>_json} carries a JSON
     * array of card names (the runtime's transport — '/' in a name stays a character, a missed
     * parent can never collapse into one literal root name); the plain {@code <name>} parameter
     * keeps the slash-joined convenience for external drivers and humans.
     */
    private static java.util.List<String> segmentsOf(McpToolCall call, String parameter) {
        String json = call.getString(parameter + "_json");
        if (json != null && !json.trim().isEmpty()) {
            java.util.List<String> segments = new ArrayList<String>();
            try {
                com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
                if (parsed.isJsonArray()) {
                    for (com.google.gson.JsonElement element : parsed.getAsJsonArray()) {
                        String segment = element.isJsonPrimitive() ? element.getAsString() : "";
                        if (!segment.trim().isEmpty()) {
                            segments.add(segment.trim());
                        }
                    }
                    return segments;
                }
            } catch (RuntimeException notJson) {
                // fall through to the slash form — an unreadable array must not silently
                // reinterpret the call
            }
        }
        return splitPath(call.getString(parameter));
    }

    /** "A/B/C" → [A, B, C]; empty/null → the concept root. */
    private static java.util.List<String> splitPath(String path) {
        java.util.List<String> names = new ArrayList<String>();
        if (path != null) {
            for (String part : path.split("/")) {
                if (!part.trim().isEmpty()) {
                    names.add(part.trim());
                }
            }
        }
        return names;
    }
}
