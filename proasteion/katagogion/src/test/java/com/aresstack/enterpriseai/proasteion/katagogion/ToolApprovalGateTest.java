package com.aresstack.enterpriseai.proasteion.katagogion;

import com.aresstack.enterpriseai.astu.approval.ApprovalDecision;
import com.aresstack.enterpriseai.astu.approval.ApprovalPolicy;
import com.aresstack.enterpriseai.astu.approval.ApprovalPromptPort;
import com.aresstack.enterpriseai.astu.approval.ApprovalRequest;
import com.aresstack.enterpriseai.astu.approval.ApprovalRules;
import com.aresstack.enterpriseai.astu.approval.ApprovalTarget;
import com.aresstack.enterpriseai.astu.approval.ApprovalTargetResolver;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ToolApprovalGateTest {

    private final AtomicInteger runs = new AtomicInteger();
    private final McpToolHandler counting = call -> {
        runs.incrementAndGet();
        return McpToolResult.ok("done");
    };
    private final List<ApprovalRequest> asked = new CopyOnWriteArrayList<ApprovalRequest>();

    private ApprovalPromptPort answering(final ApprovalDecision.Outcome outcome) {
        return (request, timeout) -> {
            asked.add(request);
            return ApprovalDecision.of(request, outcome);
        };
    }

    private McpToolResult run(String rules, ApprovalPromptPort prompt, McpToolCall call) {
        ToolApprovalGate gate = new ToolApprovalGate(() -> ApprovalRules.parse(rules), prompt, 1000);
        gate.registerResolver("memory_delete", memoryResolver(), Arrays.asList("delete/chat", "delete/project"));
        return new ApprovingMcpToolHandler(gate, counting).invoke(call);
    }

    /** Resolves the scope from a trusted store, not from the model's arguments. */
    private static ApprovalTargetResolver memoryResolver() {
        final Map<String, String> scopes = new HashMap<String, String>();
        scopes.put("m1", "chat");
        scopes.put("m2", "project");
        return (toolName, arguments) -> {
            String id = (String) arguments.get("id");
            String scope = scopes.get(id);
            if (scope == null) {
                throw new IllegalArgumentException("unknown memory");
            }
            return new ApprovalTarget("delete", scope, id, "1", "Erinnerung löschen");
        };
    }

    private static McpToolCall call(String tool, String id, String claimedScope) {
        Map<String, Object> args = new HashMap<String, Object>();
        if (id != null) {
            args.put("id", id);
        }
        if (claimedScope != null) {
            args.put("scope", claimedScope);
        }
        return new McpToolCall(tool, args);
    }

    @Test
    public void allowRunsOnceWithoutAsking() {
        McpToolResult result = run("", answering(ApprovalDecision.Outcome.REJECTED), call("web_fetch", null, null));
        assertFalse(result.isError());
        assertEquals(1, runs.get());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void denyNeverRuns() {
        McpToolResult result = run("web_fetch=DENY", answering(ApprovalDecision.Outcome.APPROVED),
                call("web_fetch", null, null));
        assertTrue(result.isError());
        assertEquals(0, runs.get());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void askRunsOnlyAfterApproval() {
        assertTrue(run("web_fetch=ASK", answering(ApprovalDecision.Outcome.REJECTED),
                call("web_fetch", null, null)).isError());
        assertEquals(0, runs.get());
        assertFalse(run("web_fetch=ASK", answering(ApprovalDecision.Outcome.APPROVED),
                call("web_fetch", null, null)).isError());
        assertEquals(1, runs.get());
        assertEquals(2, asked.size());
    }

    @Test
    public void timeoutCancelAndMissingChannelNeverRun() {
        for (ApprovalDecision.Outcome outcome : Arrays.asList(ApprovalDecision.Outcome.TIMEOUT,
                ApprovalDecision.Outcome.CANCELLED, ApprovalDecision.Outcome.UNAVAILABLE)) {
            assertTrue(run("web_fetch=ASK", answering(outcome), call("web_fetch", null, null)).isError());
        }
        assertTrue(run("web_fetch=ASK", ApprovalPromptPort.unavailable(), call("web_fetch", null, null)).isError());
        assertTrue(run("web_fetch=ASK", (request, timeout) -> {
            throw new IllegalStateException("no ui");
        }, call("web_fetch", null, null)).isError());
        assertEquals(0, runs.get());
    }

    @Test
    public void staleOrForeignDecisionNeverRuns() {
        final AtomicReference<ApprovalRequest> first = new AtomicReference<ApprovalRequest>();
        ApprovalPromptPort replaying = (request, timeout) -> {
            first.compareAndSet(null, request);
            return ApprovalDecision.of(first.get(), ApprovalDecision.Outcome.APPROVED);
        };
        ToolApprovalGate gate = new ToolApprovalGate(() -> ApprovalRules.parse("web_fetch=ASK"), replaying, 1000);
        McpToolHandler handler = new ApprovingMcpToolHandler(gate, counting);
        assertFalse(handler.invoke(call("web_fetch", null, null)).isError());
        // The approval of the first call does not carry over to the second.
        assertTrue(handler.invoke(call("web_fetch", null, null)).isError());
        assertTrue(new ApprovingMcpToolHandler(gate, counting).invoke(call("web_fetch", null, null)).isError());
        assertEquals(1, runs.get());
        assertFalse(new ToolApprovalGate(() -> ApprovalRules.parse("web_fetch=ASK"),
                (request, timeout) -> null, 1000).check("web_fetch", call("web_fetch", null, null).getArguments()).permitted());
    }

    @Test
    public void scopeOfTheRealObjectSelectsTheRule() {
        String rules = "memory_delete:delete/chat=ALLOW;memory_delete:delete/project=ASK;memory_delete=DENY";
        assertFalse(run(rules, answering(ApprovalDecision.Outcome.REJECTED), call("memory_delete", "m1", null))
                .isError());
        assertEquals(1, runs.get());
        // The model claims "chat", but m2 lives in the project: the project rule asks.
        assertTrue(run(rules, answering(ApprovalDecision.Outcome.REJECTED), call("memory_delete", "m2", "chat"))
                .isError());
        assertEquals(1, asked.size());
        assertEquals("project", asked.get(0).target().scope());
        assertEquals("m2", asked.get(0).target().targetId());
        assertEquals(1, runs.get());
    }

    @Test
    public void unresolvableTargetNeverRuns() {
        assertTrue(run("", answering(ApprovalDecision.Outcome.APPROVED), call("memory_delete", "nope", null))
                .isError());
        assertEquals(0, runs.get());
    }

    @Test
    public void rulesFallBackFromScopeToActionToTool() {
        ApprovalRules rules = ApprovalRules.parse("t:delete=ASK; t = DENY ;x=bogus;broken");
        assertEquals(ApprovalPolicy.ASK, rules.policy("t", new ApprovalTarget("delete", "global", null, null, null)));
        assertEquals(ApprovalPolicy.DENY, rules.policy("t", ApprovalTarget.invoke()));
        assertEquals(ApprovalPolicy.ALLOW, rules.policy("x", ApprovalTarget.invoke()));
        assertEquals("t:delete=ASK;t=DENY", rules.format());
        assertEquals(rules.rules(), ApprovalRules.parse(rules.format()).rules());
        assertEquals(ApprovalPolicy.ALLOW, rules.with("t:Delete/Global", ApprovalPolicy.ALLOW)
                .policy("t", new ApprovalTarget("delete", "global", null, null, null)));
    }

    @Test
    public void descriptionNamesToolActionAndScopeButNoArguments() {
        run("memory_delete=ASK", answering(ApprovalDecision.Outcome.REJECTED), call("memory_delete", "m2", null));
        String text = asked.get(0).description();
        assertTrue(text, text.contains("memory_delete") && text.contains("delete") && text.contains("project"));
        assertFalse(text, text.contains("m2"));
    }

    @Test
    public void parallelCallsGetTheirOwnRequests() throws Exception {
        final CountDownLatch bothAsked = new CountDownLatch(2);
        final List<String> ids = Collections.synchronizedList(new java.util.ArrayList<String>());
        ApprovalPromptPort prompt = (request, timeout) -> {
            ids.add(request.id());
            bothAsked.countDown();
            try {
                bothAsked.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ApprovalDecision.of(request, ApprovalDecision.Outcome.APPROVED);
        };
        final ToolApprovalGate gate = new ToolApprovalGate(() -> ApprovalRules.parse("web_fetch=ASK"), prompt, 1000);
        Thread a = new Thread(() -> new ApprovingMcpToolHandler(gate, counting).invoke(call("web_fetch", null, null)));
        Thread b = new Thread(() -> new ApprovingMcpToolHandler(gate, counting).invoke(call("web_fetch", null, null)));
        a.start();
        b.start();
        a.join(5000);
        b.join(5000);
        assertEquals(2, runs.get());
        assertEquals(2, ids.size());
        assertFalse(ids.get(0).equals(ids.get(1)));
    }

    @Test
    public void targetChangedWhileWaitingForApprovalNeverRuns() {
        final Map<String, String> versions = new HashMap<String, String>();
        versions.put("m2", "1");
        ApprovalTargetResolver resolver = (toolName, arguments) -> {
            String id = (String) arguments.get("id");
            String version = versions.get(id);
            if (version == null) {
                throw new IllegalArgumentException("unknown memory");
            }
            return new ApprovalTarget("delete", "project", id, version, "");
        };
        ApprovalPromptPort editWhileAsking = (request, timeout) -> {
            versions.put("m2", "2"); // somebody edits the memory while the bubble waits
            return ApprovalDecision.of(request, ApprovalDecision.Outcome.APPROVED);
        };
        ToolApprovalGate gate = new ToolApprovalGate(() -> ApprovalRules.parse("memory_delete=ASK"), editWhileAsking,
                1000);
        gate.registerResolver("memory_delete", resolver, Arrays.asList("delete/project"));
        McpToolHandler handler = new ApprovingMcpToolHandler(gate, counting);
        McpToolResult changed = handler.invoke(call("memory_delete", "m2", null));
        assertTrue(changed.isError());
        assertTrue(changed.getText(), changed.getText().contains("geändert"));
        assertEquals(0, runs.get());

        ApprovalPromptPort deleteWhileAsking = (request, timeout) -> {
            versions.remove("m2");
            return ApprovalDecision.of(request, ApprovalDecision.Outcome.APPROVED);
        };
        versions.put("m2", "2");
        ToolApprovalGate deleting = new ToolApprovalGate(() -> ApprovalRules.parse("memory_delete=ASK"),
                deleteWhileAsking, 1000);
        deleting.registerResolver("memory_delete", resolver, Arrays.asList("delete/project"));
        assertTrue(new ApprovingMcpToolHandler(deleting, counting).invoke(call("memory_delete", "m2", null))
                .isError());
        assertEquals(0, runs.get());

        ApprovalPromptPort plain = (request, timeout) -> ApprovalDecision.of(request,
                ApprovalDecision.Outcome.APPROVED);
        versions.put("m2", "3");
        ToolApprovalGate unchanged = new ToolApprovalGate(() -> ApprovalRules.parse("memory_delete=ASK"), plain, 1000);
        unchanged.registerResolver("memory_delete", resolver, Arrays.asList("delete/project"));
        assertFalse(new ApprovingMcpToolHandler(unchanged, counting).invoke(call("memory_delete", "m2", null))
                .isError());
        assertEquals(1, runs.get());
    }

    @Test
    public void ruleTightenedToDenyWhileWaitingNeverRuns() {
        final AtomicReference<String> rules = new AtomicReference<String>("web_fetch=ASK");
        ApprovalPromptPort tighten = (request, timeout) -> {
            rules.set("web_fetch=DENY");
            return ApprovalDecision.of(request, ApprovalDecision.Outcome.APPROVED);
        };
        ToolApprovalGate gate = new ToolApprovalGate(() -> ApprovalRules.parse(rules.get()), tighten, 1000);
        assertTrue(new ApprovingMcpToolHandler(gate, counting).invoke(call("web_fetch", null, null)).isError());
        assertEquals(0, runs.get());
    }
}
