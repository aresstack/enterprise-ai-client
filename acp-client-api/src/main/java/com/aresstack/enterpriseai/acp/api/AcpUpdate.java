package com.aresstack.enterpriseai.acp.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One streamed update, attributed by sessionId + promptId and ordered by a monotonic sequenceNumber.
 *
 * <p>{@link Kind#OTHER} covers every ACP {@code sessionUpdate} that is neither text nor a tool call. {@link #getType()}
 * names it ({@code plan}, {@code available_commands_update} …) so a consumer can show it instead of dropping it; a
 * {@code plan} update carries its entries in {@link #getPlan()}.</p>
 */
public final class AcpUpdate {

    public enum Kind { MESSAGE, THOUGHT, TOOL_CALL, OTHER }

    /** ACP {@code sessionUpdate} of an agent plan. */
    public static final String TYPE_PLAN = "plan";

    private final String sessionId;
    private final String promptId;
    private final long sequenceNumber;
    private final Kind kind;
    private final String text;
    private final AcpToolCall toolCall;
    private final String type;
    private final List<AcpPlanEntry> plan;

    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, Kind kind, String text) {
        this(sessionId, promptId, sequenceNumber, kind, text, "", null);
    }

    /** An {@link Kind#OTHER} update with its ACP {@code sessionUpdate} name; {@code plan} only for {@link #TYPE_PLAN}. */
    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, String type, String text,
                     List<AcpPlanEntry> plan) {
        this(sessionId, promptId, sequenceNumber, Kind.OTHER, text, type, plan);
    }

    private AcpUpdate(String sessionId, String promptId, long sequenceNumber, Kind kind, String text, String type,
                      List<AcpPlanEntry> plan) {
        this.sessionId = sessionId;
        this.promptId = promptId;
        this.sequenceNumber = sequenceNumber;
        this.kind = kind == null ? Kind.OTHER : kind;
        this.text = text == null ? "" : text;
        this.toolCall = null;
        this.type = type == null ? "" : type;
        this.plan = plan == null ? Collections.<AcpPlanEntry>emptyList()
                : Collections.unmodifiableList(new ArrayList<AcpPlanEntry>(plan));
    }

    /** Ein Werkzeugaufruf ({@link Kind#TOOL_CALL}); {@link #getText()} ist sein Titel. */
    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, AcpToolCall toolCall) {
        this.sessionId = sessionId;
        this.promptId = promptId;
        this.sequenceNumber = sequenceNumber;
        this.kind = Kind.TOOL_CALL;
        this.toolCall = toolCall;
        this.text = toolCall == null ? "" : toolCall.getTitle();
        this.type = "";
        this.plan = Collections.emptyList();
    }

    public String getSessionId() { return sessionId; }
    public String getPromptId() { return promptId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public Kind getKind() { return kind; }
    public String getText() { return text; }
    /** Der Werkzeugaufruf bei {@link Kind#TOOL_CALL}, sonst {@code null}. */
    public AcpToolCall getToolCall() { return toolCall; }
    /** ACP {@code sessionUpdate} name of an {@link Kind#OTHER} update ({@code plan} …), empty when unknown. */
    public String getType() { return type; }
    /** The whole plan of a {@link #TYPE_PLAN} update (it replaces the previous one), otherwise empty. */
    public List<AcpPlanEntry> getPlan() { return plan; }

    public boolean isPlan() {
        return kind == Kind.OTHER && TYPE_PLAN.equals(type);
    }
}
