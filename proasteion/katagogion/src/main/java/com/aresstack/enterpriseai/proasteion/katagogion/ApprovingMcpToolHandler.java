package com.aresstack.enterpriseai.proasteion.katagogion;

import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;

/** Runs {@code delegate} only when the {@link ToolApprovalGate} permits the call; otherwise an error result. */
public final class ApprovingMcpToolHandler implements McpToolHandler {

    private final ToolApprovalGate gate;
    private final McpToolHandler delegate;

    public ApprovingMcpToolHandler(ToolApprovalGate gate, McpToolHandler delegate) {
        if (gate == null || delegate == null) {
            throw new IllegalArgumentException("gate and delegate must not be null");
        }
        this.gate = gate;
        this.delegate = delegate;
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        ToolApprovalGate.Verdict verdict = gate.check(call.getToolName(), call.getArguments());
        if (!verdict.permitted()) {
            return McpToolResult.error("Werkzeug " + call.getToolName() + " nicht ausgeführt: " + verdict.reason());
        }
        return delegate.invoke(call);
    }
}
