package com.aresstack.enterpriseai.chat.api;

/** Das Ergebnis eines Werkzeugaufrufs, das an das Modell zurückgeht ({@code function_call_output}). */
public final class ToolOutput {

    private final String callId;
    private final String output;

    public ToolOutput(String callId, String output) {
        if (callId == null || callId.isEmpty()) {
            throw new IllegalArgumentException("callId must not be empty");
        }
        this.callId = callId;
        this.output = output == null ? "" : output;
    }

    public String callId() {
        return callId;
    }

    public String output() {
        return output;
    }

    @Override
    public String toString() {
        return "ToolOutput[" + callId + ", " + output.length() + " Zeichen]";
    }
}
