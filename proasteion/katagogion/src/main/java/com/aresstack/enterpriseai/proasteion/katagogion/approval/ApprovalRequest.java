package com.aresstack.enterpriseai.proasteion.katagogion.approval;

import java.util.UUID;

/** One pending question to the user: approve exactly this call of {@code toolName} on {@code target}? */
public final class ApprovalRequest {

    private final String id;
    private final String toolName;
    private final ApprovalTarget target;

    public ApprovalRequest(String toolName, ApprovalTarget target) {
        if (toolName == null || toolName.trim().isEmpty() || target == null) {
            throw new IllegalArgumentException("toolName and target must not be empty");
        }
        this.id = UUID.randomUUID().toString();
        this.toolName = toolName.trim();
        this.target = target;
    }

    /** Unique id; a decision counts only for the request with this id. */
    public String id() {
        return id;
    }

    public String toolName() {
        return toolName;
    }

    public ApprovalTarget target() {
        return target;
    }

    /** German text for the confirmation: tool, action, scope and the resolver's description, never argument values. */
    public String description() {
        StringBuilder text = new StringBuilder("Werkzeug ").append(toolName);
        if (!ApprovalTarget.INVOKE.equals(target.action())) {
            text.append(", Aktion ").append(target.action());
        }
        if (target.scope() != null) {
            text.append(", Bereich ").append(target.scope());
        }
        if (!target.description().isEmpty()) {
            text.append(": ").append(target.description());
        }
        return text.toString();
    }

    @Override
    public String toString() {
        return "ApprovalRequest[" + toolName + ", " + target + "]";
    }
}
