package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalDecision;
import com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalRequest;
import com.aresstack.enterpriseai.proasteion.katagogion.approval.ApprovalRules;
import com.aresstack.enterpriseai.proasteion.katagogion.approval.ToolApprovalGate;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Chat, agent and research tools all run behind the one approval gate of the {@link ToolCatalog}. */
public class ToolCatalogApprovalTest {

    private final AtomicInteger runs = new AtomicInteger();
    private final AtomicReference<String> rules = new AtomicReference<String>("");
    private final AtomicReference<ApprovalDecision.Outcome> answer =
            new AtomicReference<ApprovalDecision.Outcome>(ApprovalDecision.Outcome.REJECTED);
    private final List<ApprovalRequest> asked = new CopyOnWriteArrayList<ApprovalRequest>();
    private final ToolCatalog catalog = new ToolCatalog(name -> true,
            new ToolApprovalGate(() -> ApprovalRules.parse(rules.get()), (request, timeout) -> {
                asked.add(request);
                return ApprovalDecision.of(request, answer.get());
            }, 1000));

    public ToolCatalogApprovalTest() {
        final McpToolContribution tool = McpToolContribution.of("side_effect", "changes something", call -> {
            runs.incrementAndGet();
            return McpToolResult.ok("changed");
        });
        catalog.register("Test", EnumSet.allOf(ToolChannel.class), () -> Collections.singletonList(tool));
    }

    private McpToolResult invoke(ToolChannel channel) {
        return catalog.find("side_effect", channel).getHandler()
                .invoke(new McpToolCall("side_effect", Collections.<String, Object>emptyMap()));
    }

    @Test
    public void withoutRulesEveryChannelRunsAsBefore() {
        for (ToolChannel channel : ToolChannel.values()) {
            assertFalse(invoke(channel).isError());
        }
        assertEquals(ToolChannel.values().length, runs.get());
        assertTrue(asked.isEmpty());
    }

    @Test
    public void everyChannelUsesTheSameGate() {
        rules.set("side_effect=ASK");
        for (ToolChannel channel : ToolChannel.values()) {
            assertTrue(invoke(channel).isError());
        }
        assertEquals(0, runs.get());
        assertEquals(ToolChannel.values().length, asked.size());
        answer.set(ApprovalDecision.Outcome.APPROVED);
        for (ToolChannel channel : ToolChannel.values()) {
            assertFalse(invoke(channel).isError());
        }
        assertEquals(ToolChannel.values().length, runs.get());
    }

    @Test
    public void chatToolLoopAsksOnceAndDenyNeverRuns() {
        rules.set("side_effect=ASK");
        answer.set(ApprovalDecision.Outcome.APPROVED);
        List<AiTool> chat = McpChatTool.all(catalog.tools(EnumSet.of(ToolChannel.CHAT)));
        assertEquals("changed", chat.get(0).execute("{}"));
        assertEquals(1, asked.size());
        assertEquals(1, runs.get());
        rules.set("side_effect=DENY");
        assertTrue(chat.get(0).execute("{}").startsWith("Fehler:"));
        assertEquals(1, asked.size());
        assertEquals(1, runs.get());
    }
}
