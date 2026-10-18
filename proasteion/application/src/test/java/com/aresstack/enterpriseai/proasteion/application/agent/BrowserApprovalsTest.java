package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.approval.ApprovalPolicy;
import com.aresstack.enterpriseai.astu.approval.ApprovalPromptPort;
import com.aresstack.enterpriseai.astu.approval.ApprovalRules;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/** #141: opening a page asks first by default; everything else in the browser stays allowed. */
public class BrowserApprovalsTest {

    private static Map<String, Object> args(String... pairs) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static ToolApprovalGate gate(String rules) {
        ToolApprovalGate gate = new ToolApprovalGate(() -> ApprovalRules.parse(rules),
                ApprovalPromptPort.unavailable(), ToolApprovalGate.DEFAULT_TIMEOUT_MILLIS);
        BrowserMcpTools.registerApprovals(gate);
        return gate;
    }

    @Test
    public void openingAPageAsksByDefault() {
        ToolApprovalGate gate = gate("");
        assertEquals(ApprovalPolicy.ASK, gate.policy(BrowserMcpTools.NAVIGATE, args("url", "https://example.org")));
        assertEquals(ApprovalPolicy.ASK, gate.policy(BrowserMcpTools.TABS,
                args("action", "new", "url", "https://example.org")));
    }

    @Test
    public void listingSelectingAndReadingStayAllowed() {
        ToolApprovalGate gate = gate("");
        assertEquals(ApprovalPolicy.ALLOW, gate.policy(BrowserMcpTools.TABS, args()));
        assertEquals(ApprovalPolicy.ALLOW, gate.policy(BrowserMcpTools.TABS, args("action", "select", "tab", "tab-1")));
        assertEquals(ApprovalPolicy.ALLOW, gate.policy(BrowserMcpTools.TABS, args("action", "new")));
        assertEquals(ApprovalPolicy.ALLOW, gate.policy(BrowserMcpTools.READ, args()));
    }

    @Test
    public void settingsReplaceTheDefault() {
        assertEquals(ApprovalPolicy.ALLOW, gate("browser_navigate:open=ALLOW")
                .policy(BrowserMcpTools.NAVIGATE, args("url", "https://example.org")));
        assertEquals(ApprovalPolicy.DENY, gate("browser_navigate=DENY;browser_navigate:open=DENY")
                .policy(BrowserMcpTools.NAVIGATE, args("url", "https://example.org")));
    }
}
