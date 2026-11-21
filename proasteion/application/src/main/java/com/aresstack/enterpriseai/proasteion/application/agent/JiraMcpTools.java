package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.approval.ApprovalPolicy;
import com.aresstack.enterpriseai.astu.approval.ApprovalTarget;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.jira.JiraIssueOperations;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Jira-Werkzeuge des Chats (Schritt 3 der Jira-Anbindung) über die Jira-Quellen unter Wissensquellen, keine eigene
 * Anbindung: {@value #SEARCH} (JQL-Suche, nur lesend), {@value #COMMENT} (Kommentar schreiben) und {@value #TRANSITION}
 * (Übergänge nennen oder einen ausführen). Lesen, Seitenbaum und Volltext laufen weiter über die Wissenswerkzeuge
 * ({@code knowledge_read} …); diese Werkzeuge ergänzen nur, was Jira-eigen ist.
 *
 * <p>Schreiben nur mit ausdrücklicher Freigabe: {@value #COMMENT} und ein ausführender {@value #TRANSITION} haben die
 * Aktion {@value #WRITE} mit der eingebauten Regel {@code ASK} am gemeinsamen {@link ToolApprovalGate}; der Benutzer
 * sieht vor jedem Aufruf Vorgang, Titel und Inhalt und muss zustimmen. Das Ziel bestimmt der Resolver aus Jira
 * selbst (Schlüssel, Titel, letzte Änderung als Version, der echte Übergang); ändert sich der Vorgang zwischen Frage
 * und Ausführung, gilt die Freigabe nicht mehr. Nur angehakte Quellen sind sichtbar.
 */
public final class JiraMcpTools {

    public static final String SEARCH = "jira_search";
    public static final String COMMENT = "jira_comment";
    public static final String TRANSITION = "jira_transition";

    /** Freigabe-Aktion eines schreibenden Aufrufs; Vorgabe {@code ASK}. */
    public static final String WRITE = "write";
    /** Freigabe-Aktion von {@value #TRANSITION} ohne ausführbaren Übergang (nur Auflisten); Vorgabe {@code ALLOW}. */
    public static final String LIST = "list";

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;
    private static final int PREVIEW = 300;

    private final Supplier<KnowledgeSourceCatalog> catalog;
    private final Predicate<SourceAddress> enabled;
    /**
     * Vorgang und Übergang ({@code KEY#id}), die das Gate zuletzt in diesem Thread als Ziel bestimmt hat ({@code write}); {@code null} nach
     * einem Auflisten. {@code ApprovingMcpToolHandler} prüft und führt im selben Thread aus; ausgeführt wird nur genau
     * dieser Übergang, auch wenn er erst nach dem Auflisten verfügbar wurde (sonst liefe er ohne Freigabe).
     */
    private final ThreadLocal<String> resolvedTransition = new ThreadLocal<String>();

    /**
     * @param catalog   die Quellen unter Wissensquellen (dieselben wie die der Wissenswerkzeuge)
     * @param enabled   ob eine Quelle angehakt ist
     * @param approvals das gemeinsame Freigabe-Gate des Werkzeugkatalogs; hier werden die Resolver der schreibenden
     *                  Werkzeuge mit der Vorgabe {@code ASK} eingetragen
     */
    public JiraMcpTools(Supplier<KnowledgeSourceCatalog> catalog, Predicate<SourceAddress> enabled,
                        ToolApprovalGate approvals) {
        if (catalog == null || enabled == null || approvals == null) {
            throw new IllegalArgumentException("catalog, enabled und approvals sind Pflicht");
        }
        this.catalog = catalog;
        this.enabled = enabled;
        approvals.registerResolver(COMMENT, (tool, arguments) -> commentTarget(arguments), writePolicies());
        approvals.registerResolver(TRANSITION, (tool, arguments) -> transitionTarget(arguments), writePolicies());
    }

    /** Eingebaute Regeln: {@value #WRITE} {@code ASK}, {@value #LIST} {@code ALLOW}. */
    static Map<String, ApprovalPolicy> writePolicies() {
        Map<String, ApprovalPolicy> policies = new LinkedHashMap<String, ApprovalPolicy>();
        policies.put(WRITE, ApprovalPolicy.ASK);
        policies.put(LIST, ApprovalPolicy.ALLOW);
        return policies;
    }

    /** Die drei Werkzeuge; leer, solange keine Jira-Quelle angehakt ist. */
    public List<McpToolContribution> contributions() {
        if (jiras().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(search(), comment(), transition());
    }

    /** Nur das lesende {@value #SEARCH}, etwa für den Recherche-Agenten; leer ohne angehakte Jira-Quelle. */
    public List<McpToolContribution> readOnly() {
        return jiras().isEmpty() ? Collections.<McpToolContribution>emptyList()
                : Collections.singletonList(search());
    }

    McpToolContribution search() {
        return McpToolContribution.of(SEARCH, "Searches Jira issues with JQL (Jira Query Language) in the Jira "
                        + "sources the user enabled, e.g. project = ABC AND status = \"In Progress\" ORDER BY updated "
                        + "DESC, or assignee = currentUser() AND resolution = Unresolved. Returns key, title, type, "
                        + "status, assignee and link per issue. Read the full issue with knowledge_read (the link or "
                        + "the key's address). Read-only.",
                this::runSearch,
                McpToolParameter.string("jql", true, "The JQL query."),
                McpToolParameter.integer("limit", false, "Maximum number of issues (default " + DEFAULT_LIMIT
                        + ", at most " + MAX_LIMIT + ")."),
                McpToolParameter.string("source", false, "Only this Jira source (its address or base URL); "
                        + "default: all enabled Jira sources."));
    }

    McpToolContribution comment() {
        return McpToolContribution.of(COMMENT, "Adds a comment to ONE Jira issue, but only when the user explicitly "
                        + "asked for it. The user must confirm every comment before it is written; never claim it was "
                        + "posted before this tool reported success. Uses Jira wiki markup.",
                this::runComment,
                McpToolParameter.string("issue", true, "Issue key (ABC-123) or the issue's URL."),
                McpToolParameter.string("body", true, "The comment text exactly as it should appear."),
                McpToolParameter.string("source", false, "The Jira source, only needed when several are enabled "
                        + "and issue is a bare key."));
    }

    McpToolContribution transition() {
        return McpToolContribution.of(TRANSITION, "Lists the workflow transitions of ONE Jira issue (without "
                        + "transition) or moves the issue through one of them (with transition, by id or name), the "
                        + "latter only when the user explicitly asked for it. The user must confirm every transition "
                        + "before it runs; never claim a status change before this tool reported success.",
                this::runTransition,
                McpToolParameter.string("issue", true, "Issue key (ABC-123) or the issue's URL."),
                McpToolParameter.string("transition", false, "Id or exact name of the transition; omit to list "
                        + "the available ones."),
                McpToolParameter.string("source", false, "The Jira source, only needed when several are enabled "
                        + "and issue is a bare key."));
    }

    // --- Ausführung ---------------------------------------------------------------------------------------------

    private McpToolResult runSearch(McpToolCall call) {
        String jql = trimmed(call.getString("jql"));
        if (jql.isEmpty()) {
            return McpToolResult.error("Missing argument: jql.");
        }
        int limit = limit(call.getString("limit"));
        List<Jira> targets;
        try {
            targets = sources(trimmed(call.getString("source")));
        } catch (IllegalArgumentException e) {
            return McpToolResult.error(e.getMessage());
        }
        StringBuilder out = new StringBuilder();
        int shown = 0;
        for (Jira jira : targets) {
            try {
                JiraIssueOperations.IssueSearch found = jira.operations.searchIssues(jql, limit);
                out.append("Jira ").append(jira.operations.baseUrl()).append(": ").append(found.issues().size())
                        .append(found.total() > found.issues().size() ? " of " + found.total() : "")
                        .append(" issue(s)\n");
                for (JiraIssueOperations.IssueSummary issue : found.issues()) {
                    out.append("- ").append(line(issue)).append('\n');
                    shown++;
                }
            } catch (KnowledgeSourceException e) {
                out.append("Jira ").append(jira.operations.baseUrl()).append(": failed (").append(e.getMessage())
                        .append(")\n");
            }
        }
        if (shown == 0 && out.length() == 0) {
            return McpToolResult.error("No enabled Jira source.");
        }
        return McpToolResult.ok(out.toString().trim());
    }

    private McpToolResult runComment(McpToolCall call) {
        String body = trimmed(call.getString("body"));
        if (body.isEmpty()) {
            return McpToolResult.error("Missing argument: body.");
        }
        try {
            Target target = target(call.getArguments());
            String id = target.jira.operations.addComment(target.key, body);
            return McpToolResult.ok("Comment " + id + " added to " + target.key + " ("
                    + target.jira.operations.baseUrl() + "/browse/" + target.key + ").");
        } catch (IllegalArgumentException e) {
            return McpToolResult.error(e.getMessage());
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error("Comment not added: " + e.getMessage());
        }
    }

    private McpToolResult runTransition(McpToolCall call) {
        try {
            Target target = target(call.getArguments());
            List<JiraIssueOperations.Transition> available = target.jira.operations.transitions(target.key);
            String wanted = trimmed(call.getString("transition"));
            String approved = resolvedTransition.get();
            resolvedTransition.remove();
            JiraIssueOperations.Transition chosen = wanted.isEmpty() ? null : match(available, wanted);
            if (chosen != null && !(target.key + "#" + chosen.id()).equals(approved)) {
                chosen = null;
            }
            if (chosen == null) {
                StringBuilder out = new StringBuilder(wanted.isEmpty() ? "" : "No transition '" + wanted
                        + "' available. ");
                out.append(available.isEmpty() ? "No transitions available for " + target.key + "."
                        : "Transitions of " + target.key + ":");
                for (JiraIssueOperations.Transition transition : available) {
                    out.append("\n- id ").append(transition.id()).append(": ").append(transition.name())
                            .append(transition.toStatus() == null ? "" : " → " + transition.toStatus());
                }
                return wanted.isEmpty() ? McpToolResult.ok(out.toString()) : McpToolResult.error(out.toString());
            }
            target.jira.operations.transition(target.key, chosen.id());
            return McpToolResult.ok(target.key + " moved through '" + chosen.name() + "'"
                    + (chosen.toStatus() == null ? "" : " to " + chosen.toStatus()) + ".");
        } catch (IllegalArgumentException e) {
            return McpToolResult.error(e.getMessage());
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error("Transition not done: " + e.getMessage());
        }
    }

    // --- Freigabe -----------------------------------------------------------------------------------------------

    /** Ziel von {@value #COMMENT}: Vorgang und Version aus Jira, Text der Frage mit Titel und Kommentaranfang. */
    private ApprovalTarget commentTarget(Map<String, Object> arguments) {
        Target target = target(arguments);
        JiraIssueOperations.IssueSummary issue = summary(target);
        String body = trimmed(arguments.get("body") == null ? null : String.valueOf(arguments.get("body")));
        return new ApprovalTarget(WRITE, null, target.jira.source.value() + "/" + issue.key(), version(issue, body),
                "Kommentar in Jira zu " + title(issue) + " schreiben: " + preview(body));
    }

    /**
     * Ziel von {@value #TRANSITION}: ohne passenden Übergang nur Auflisten ({@value #LIST}); sonst der echte Übergang
     * aus Jira ({@value #WRITE}).
     */
    private ApprovalTarget transitionTarget(Map<String, Object> arguments) {
        resolvedTransition.remove();
        Target target = target(arguments);
        String wanted = trimmed(arguments.get("transition") == null ? null
                : String.valueOf(arguments.get("transition")));
        if (wanted.isEmpty()) {
            return new ApprovalTarget(LIST, null, target.jira.source.value() + "/" + target.key, null,
                    "Übergänge von " + target.key + " auflisten");
        }
        JiraIssueOperations.Transition chosen;
        try {
            chosen = match(target.jira.operations.transitions(target.key), wanted);
        } catch (KnowledgeSourceException e) {
            throw new IllegalStateException("Übergänge nicht lesbar");
        }
        if (chosen == null) {
            return new ApprovalTarget(LIST, null, target.jira.source.value() + "/" + target.key, null,
                    "Übergänge von " + target.key + " auflisten");
        }
        JiraIssueOperations.IssueSummary issue = summary(target);
        resolvedTransition.set(issue.key() + "#" + chosen.id());
        return new ApprovalTarget(WRITE, null, target.jira.source.value() + "/" + issue.key(),
                version(issue, chosen.id()), "Jira-Vorgang " + title(issue) + " von „" + issue.status()
                + "“ über „" + chosen.name() + "“" + (chosen.toStatus() == null ? "" : " nach „"
                + chosen.toStatus() + "“") + " setzen");
    }

    private static JiraIssueOperations.IssueSummary summary(Target target) {
        try {
            return target.jira.operations.issueSummary(target.key);
        } catch (KnowledgeSourceException e) {
            throw new IllegalStateException("Vorgang nicht lesbar");
        }
    }

    /** Version der Freigabe: letzte Änderung des Vorgangs und der Inhalt (Kommentar bzw. Übergang). */
    private static String version(JiraIssueOperations.IssueSummary issue, String content) {
        return (issue.updated() == null ? "?" : issue.updated().toString()) + "#"
                + Integer.toHexString((content == null ? "" : content).hashCode());
    }

    // --- Quellen ------------------------------------------------------------------------------------------------

    /** Die angehakten Jira-Quellen. */
    private List<Jira> jiras() {
        List<Jira> jiras = new ArrayList<Jira>();
        KnowledgeSourceCatalog current = catalog.get();
        if (current == null) {
            return jiras;
        }
        for (KnowledgeSourceRegistration registration : current.registrations()) {
            if (registration.port() instanceof JiraIssueOperations && enabled.test(registration.sourceId())) {
                jiras.add(new Jira(registration.sourceId(), (JiraIssueOperations) registration.port()));
            }
        }
        return jiras;
    }

    /** Alle angehakten Jira-Quellen oder nur die genannte (Adresse oder Basis-URL). */
    private List<Jira> sources(String source) {
        List<Jira> all = jiras();
        if (all.isEmpty()) {
            throw new IllegalArgumentException("No enabled Jira source.");
        }
        if (source.isEmpty()) {
            return all;
        }
        for (Jira jira : all) {
            if (jira.matches(source)) {
                return Collections.singletonList(jira);
            }
        }
        throw new IllegalArgumentException("Unknown Jira source '" + source + "'. Enabled: " + names(all) + ".");
    }

    /** Quelle und Schlüssel eines Vorgangs aus {@code issue} und {@code source}. */
    private Target target(Map<String, Object> arguments) {
        Object rawIssue = arguments == null ? null : arguments.get("issue");
        Object rawSource = arguments == null ? null : arguments.get("source");
        String issue = trimmed(rawIssue == null ? null : String.valueOf(rawIssue));
        if (issue.isEmpty()) {
            throw new IllegalArgumentException("Missing argument: issue (key ABC-123 or the issue's URL).");
        }
        List<Jira> candidates = sources(trimmed(rawSource == null ? null : String.valueOf(rawSource)));
        List<Target> found = new ArrayList<Target>();
        for (Jira jira : candidates) {
            String key = jira.operations.issueKey(issue);
            if (key != null) {
                found.add(new Target(jira, key));
            }
        }
        if (found.isEmpty()) {
            throw new IllegalArgumentException("'" + issue + "' is no issue of an enabled Jira source.");
        }
        if (found.size() > 1) {
            throw new IllegalArgumentException("Several Jira sources are enabled; give the issue's URL or source ("
                    + names(candidates) + ").");
        }
        return found.get(0);
    }

    private static JiraIssueOperations.Transition match(List<JiraIssueOperations.Transition> available,
                                                         String wanted) {
        for (JiraIssueOperations.Transition transition : available) {
            if (transition.id().equals(wanted)) {
                return transition;
            }
        }
        for (JiraIssueOperations.Transition transition : available) {
            if (transition.name() != null && transition.name().trim().equalsIgnoreCase(wanted)) {
                return transition;
            }
        }
        return null;
    }

    private static String names(List<Jira> jiras) {
        List<String> names = new ArrayList<String>();
        for (Jira jira : jiras) {
            names.add(jira.source.value());
        }
        return String.join(", ", names);
    }

    private static String line(JiraIssueOperations.IssueSummary issue) {
        StringBuilder sb = new StringBuilder(title(issue));
        List<String> facts = new ArrayList<String>();
        if (issue.issueType() != null) {
            facts.add(issue.issueType());
        }
        if (issue.status() != null) {
            facts.add(issue.status());
        }
        facts.add(issue.assignee() == null ? "unassigned" : issue.assignee());
        sb.append(" [").append(String.join(", ", facts)).append("] ").append(issue.url());
        return sb.toString();
    }

    private static String title(JiraIssueOperations.IssueSummary issue) {
        return issue.summary() == null || issue.summary().trim().isEmpty() ? issue.key()
                : issue.key() + ": " + issue.summary().trim();
    }

    private static String preview(String text) {
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > PREVIEW ? flat.substring(0, PREVIEW) + "…" : flat;
    }

    private static int limit(String raw) {
        try {
            // Modelle schicken Zahlen auch als 20.0.
            int value = raw == null || raw.trim().isEmpty() ? DEFAULT_LIMIT : (int) Double.parseDouble(raw.trim());
            return Math.max(1, Math.min(MAX_LIMIT, value));
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT;
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Jira {
        final SourceAddress source;
        final JiraIssueOperations operations;

        Jira(SourceAddress source, JiraIssueOperations operations) {
            this.source = source;
            this.operations = operations;
        }

        boolean matches(String name) {
            String wanted = name.trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
            return source.value().equalsIgnoreCase(name.trim())
                    || operations.baseUrl().toString().toLowerCase(Locale.ROOT).replaceAll("/+$", "").equals(wanted);
        }
    }

    private static final class Target {
        final Jira jira;
        final String key;

        Target(Jira jira, String key) {
            this.jira = jira;
            this.key = key;
        }
    }
}
