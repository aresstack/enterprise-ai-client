package com.aresstack.enterpriseai.acp.api;

/** One streamed update, attributed by sessionId + promptId and ordered by a monotonic sequenceNumber. */
public final class AcpUpdate {

    public enum Kind { MESSAGE, THOUGHT, TOOL_CALL, OTHER }

    private final String sessionId;
    private final String promptId;
    private final long sequenceNumber;
    private final Kind kind;
    private final String text;
    private final AcpToolCall toolCall;

    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, Kind kind, String text) {
        this.sessionId = sessionId;
        this.promptId = promptId;
        this.sequenceNumber = sequenceNumber;
        this.kind = kind == null ? Kind.OTHER : kind;
        this.text = text == null ? "" : text;
        this.toolCall = null;
    }

    /** Ein Werkzeugaufruf ({@link Kind#TOOL_CALL}); {@link #getText()} ist sein Titel. */
    public AcpUpdate(String sessionId, String promptId, long sequenceNumber, AcpToolCall toolCall) {
        this.sessionId = sessionId;
        this.promptId = promptId;
        this.sequenceNumber = sequenceNumber;
        this.kind = Kind.TOOL_CALL;
        this.toolCall = toolCall;
        this.text = toolCall == null ? "" : toolCall.getTitle();
    }

    public String getSessionId() { return sessionId; }
    public String getPromptId() { return promptId; }
    public long getSequenceNumber() { return sequenceNumber; }
    public Kind getKind() { return kind; }
    public String getText() { return text; }
    /** Der Werkzeugaufruf bei {@link Kind#TOOL_CALL}, sonst {@code null}. */
    public AcpToolCall getToolCall() { return toolCall; }
}
