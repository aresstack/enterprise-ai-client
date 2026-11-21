package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.approval.ApprovalDecision;
import com.aresstack.enterpriseai.astu.approval.ApprovalPromptPort;
import com.aresstack.enterpriseai.astu.approval.ApprovalRules;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.emporion.holkas.jira.JiraIssueOperations;
import com.aresstack.enterpriseai.proasteion.katagogion.ApprovingMcpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Jira-Werkzeuge über die angehakten Jira-Quellen: Suche lesend und frei, Kommentar und Übergang nur nach
 * ausdrücklicher Freigabe am gemeinsamen Gate (Vorgabe ASK), das Ziel aus Jira selbst.
 */
public class JiraMcpToolsTest {

    private static final SourceAddress SOURCE = SourceAddress.of("jira:jira.example.org/jira");

    private final StubJira jira = new StubJira();
    private final List<String> asked = new ArrayList<String>();

    @Test
    public void withoutAnEnabledJiraSourceThereAreNoTools() {
        JiraMcpTools none = new JiraMcpTools(KnowledgeSourceCatalog::empty, id -> true, ToolApprovalGate.allowAll());
        assertTrue(none.contributions().isEmpty());

        JiraMcpTools unchecked = new JiraMcpTools(this::catalog, id -> false, ToolApprovalGate.allowAll());
        assertTrue(unchecked.contributions().isEmpty());
        assertTrue(unchecked.readOnly().isEmpty());
    }

    @Test
    public void theSearchReadsWithoutAsking() {
        List<McpToolContribution> tools = tools(ApprovalDecision.Outcome.REJECTED);

        McpToolResult result = invoke(tools, JiraMcpTools.SEARCH, "jql", "project = DEMO", "limit", "5.0");

        assertFalse(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().contains("DEMO-2: Zertifikat erneuern [Task, Open, Bob] "
                + "https://jira.example.org/jira/browse/DEMO-2"));
        assertEquals(Arrays.asList("search project = DEMO 5"), jira.calls);
        assertTrue(asked.isEmpty());
    }

    @Test
    public void aCommentIsWrittenOnlyAfterTheUserConfirmedIt() {
        McpToolResult rejected = invoke(tools(ApprovalDecision.Outcome.REJECTED), JiraMcpTools.COMMENT,
                "issue", "demo-2", "body", "Zertifikat ist beantragt.");

        assertTrue(rejected.getText(), rejected.isError());
        assertEquals(1, asked.size());
        assertTrue(asked.get(0), asked.get(0).contains("DEMO-2: Zertifikat erneuern"));
        assertTrue(asked.get(0), asked.get(0).contains("Zertifikat ist beantragt."));
        assertFalse(jira.calls.toString(), jira.calls.toString().contains("comment"));

        asked.clear();
        McpToolResult approved = invoke(tools(ApprovalDecision.Outcome.APPROVED), JiraMcpTools.COMMENT,
                "issue", "https://jira.example.org/jira/browse/DEMO-2", "body", "Zertifikat ist beantragt.");

        assertFalse(approved.getText(), approved.isError());
        assertEquals(1, asked.size());
        assertTrue(jira.calls.contains("comment DEMO-2 Zertifikat ist beantragt."));
    }

    @Test
    public void withoutAnyoneToAskNothingIsWritten() {
        ToolApprovalGate gate = new ToolApprovalGate(ApprovalRules::none, ApprovalPromptPort.unavailable(), 1000);
        JiraMcpTools tools = new JiraMcpTools(this::catalog, id -> true, gate);

        McpToolResult result = invoke(gated(tools.contributions(), gate), JiraMcpTools.COMMENT, "issue", "DEMO-2",
                "body", "Text");

        assertTrue(result.getText(), result.isError());
        assertFalse(jira.calls.toString(), jira.calls.toString().contains("comment"));
    }

    @Test
    public void listingTransitionsIsFreeButMovingAnIssueAsks() {
        List<McpToolContribution> rejecting = tools(ApprovalDecision.Outcome.REJECTED);

        McpToolResult listed = invoke(rejecting, JiraMcpTools.TRANSITION, "issue", "DEMO-2");
        assertFalse(listed.getText(), listed.isError());
        assertTrue(listed.getText(), listed.getText().contains("id 31: Erledigen → Done"));
        assertTrue(asked.isEmpty());

        McpToolResult refused = invoke(rejecting, JiraMcpTools.TRANSITION, "issue", "DEMO-2", "transition",
                "erledigen");
        assertTrue(refused.getText(), refused.isError());
        assertEquals(1, asked.size());
        assertTrue(asked.get(0), asked.get(0).contains("von „Open“ über „Erledigen“ nach „Done“"));
        assertFalse(jira.calls.toString(), jira.calls.toString().contains("transition DEMO-2"));

        McpToolResult moved = invoke(tools(ApprovalDecision.Outcome.APPROVED), JiraMcpTools.TRANSITION,
                "issue", "DEMO-2", "transition", "31");
        assertFalse(moved.getText(), moved.isError());
        assertTrue(jira.calls.contains("transition DEMO-2 31"));
    }

    @Test
    public void anUnknownTransitionNamesTheAvailableOnesWithoutAsking() {
        McpToolResult result = invoke(tools(ApprovalDecision.Outcome.APPROVED), JiraMcpTools.TRANSITION,
                "issue", "DEMO-2", "transition", "Schließen");

        assertTrue(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().contains("id 21: In Arbeit nehmen"));
        assertTrue(asked.isEmpty());
    }

    @Test
    public void anIssueOfAnotherJiraIsRefused() {
        McpToolResult result = invoke(tools(ApprovalDecision.Outcome.APPROVED), JiraMcpTools.COMMENT,
                "issue", "https://other.example.org/browse/DEMO-2", "body", "Text");

        assertTrue(result.getText(), result.isError());
        assertTrue(asked.isEmpty());
    }

    private List<McpToolContribution> tools(final ApprovalDecision.Outcome outcome) {
        ToolApprovalGate gate = new ToolApprovalGate(ApprovalRules::none, (request, timeout) -> {
            asked.add(request.target().description());
            return ApprovalDecision.of(request, outcome);
        }, 1000);
        return gated(new JiraMcpTools(this::catalog, id -> true, gate).contributions(), gate);
    }

    /** Wie der Werkzeugkatalog ({@code ToolCatalog.gated}): jeder Aufruf geht über das Gate. */
    private static List<McpToolContribution> gated(List<McpToolContribution> tools, ToolApprovalGate gate) {
        List<McpToolContribution> gated = new ArrayList<McpToolContribution>();
        for (McpToolContribution tool : tools) {
            gated.add(McpToolContribution.of(tool.getName(), tool.getDescription(),
                    new ApprovingMcpToolHandler(gate, tool.getHandler())));
        }
        return gated;
    }

    private static McpToolResult invoke(List<McpToolContribution> tools, String name, String... pairs) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            arguments.put(pairs[i], pairs[i + 1]);
        }
        for (McpToolContribution tool : tools) {
            if (tool.getName().equals(name)) {
                return tool.getHandler().invoke(new McpToolCall(name, arguments));
            }
        }
        throw new AssertionError("Werkzeug fehlt: " + name);
    }

    private KnowledgeSourceCatalog catalog() {
        return KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(jira, SourceScope.of("project:*")));
    }

    /** Eine Jira mit einem Vorgang DEMO-2 und zwei Übergängen; protokolliert jeden Aufruf. */
    private static final class StubJira implements KnowledgeSourcePort, JiraIssueOperations {

        final List<String> calls = new ArrayList<String>();
        String status = "Open";

        @Override
        public SourceAddress sourceId() {
            return SOURCE;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "nicht im Test");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }

        @Override
        public URI baseUrl() {
            return URI.create("https://jira.example.org/jira");
        }

        @Override
        public String issueKey(String keyOrAddress) {
            String value = keyOrAddress.trim();
            if (value.toUpperCase(java.util.Locale.ROOT).matches("[A-Z]+-[0-9]+")) {
                return value.toUpperCase(java.util.Locale.ROOT);
            }
            String browse = baseUrl() + "/browse/";
            return value.startsWith(browse) ? value.substring(browse.length()) : null;
        }

        @Override
        public IssueSearch searchIssues(String jql, int limit) {
            calls.add("search " + jql + " " + limit);
            return new IssueSearch(Collections.singletonList(summary()), 1);
        }

        @Override
        public IssueSummary issueSummary(String issueKey) throws KnowledgeSourceException {
            if (!"DEMO-2".equals(issueKey)) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "unbekannt");
            }
            return summary();
        }

        @Override
        public List<Transition> transitions(String issueKey) {
            return Arrays.asList(new Transition("21", "In Arbeit nehmen", "In Progress"),
                    new Transition("31", "Erledigen", "Done"));
        }

        @Override
        public String addComment(String issueKey, String body) {
            calls.add("comment " + issueKey + " " + body);
            return "10001";
        }

        @Override
        public void transition(String issueKey, String transitionId) {
            calls.add("transition " + issueKey + " " + transitionId);
            status = "Done";
        }

        private IssueSummary summary() {
            return new IssueSummary("DEMO-2", "Zertifikat erneuern", "Task", status, "Bob",
                    Instant.parse("2026-02-01T07:00:00Z"), URI.create(baseUrl() + "/browse/DEMO-2"));
        }
    }
}
