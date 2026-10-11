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
    /**
     * {@code _meta} key on an {@code agent_message_chunk}: value {@code "start"} marks the first chunk of a new
     * utterance, which the host shows as its own bubble (askai arch: one bubble per agent utterance). Agents that do
     * not send it keep one bubble per turn.
     */
    public static final String META_UTTERANCE = "aresstack/utterance";
    public static final String UTTERANCE_START = "start";

    private final String sessionId;
    private final String promptId;
    private final long sequenceNumber;
    private final Kind kind;
    private final String text;
    private final AcpToolCall toolCall;
    private final String type;
    private final List<AcpPlanEntry> plan;
    private final boolean startsUtterance;

    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, Kind kind, String text) {
        this(sessionId, promptId, sequenceNumber, kind, text, "", null, false);
    }

    /** A {@link Kind#MESSAGE} chunk; {@code startsUtterance} when it opens a new utterance of the agent. */
    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, String text, boolean startsUtterance) {
        this(sessionId, promptId, sequenceNumber, Kind.MESSAGE, text, "", null, startsUtterance);
    }

    /** An {@link Kind#OTHER} update with its ACP {@code sessionUpdate} name; {@code plan} only for {@link #TYPE_PLAN}. */
    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, String type, String text,
                     List<AcpPlanEntry> plan) {
        this(sessionId, promptId, sequenceNumber, Kind.OTHER, text, type, plan, false);
    }

    private AcpUpdate(String sessionId, String promptId, long sequenceNumber, Kind kind, String text, String type,
                      List<AcpPlanEntry> plan, boolean startsUtterance) {
        this.sessionId = sessionId;
        this.promptId = promptId;
        this.sequenceNumber = sequenceNumber;
        this.kind = kind == null ? Kind.OTHER : kind;
        this.text = text == null ? "" : text;
        this.toolCall = null;
        this.type = type == null ? "" : type;
        this.plan = plan == null ? Collections.<AcpPlanEntry>emptyList()
                : Collections.unmodifiableList(new ArrayList<AcpPlanEntry>(plan));
        this.startsUtterance = startsUtterance && this.kind == Kind.MESSAGE;
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
        this.startsUtterance = false;
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

    /** A {@link Kind#MESSAGE} chunk that opens a new utterance (see {@link #META_UTTERANCE}). */
    public boolean startsUtterance() { return startsUtterance; }

    public boolean isPlan() {
        return kind == Kind.OTHER && TYPE_PLAN.equals(type);
    }
}
