package com.aresstack.enterpriseai.chat.api;

/** Ein Werkzeugaufruf des Modells: Werkzeugname, Argumente als JSON-Text und die Kennung für die Antwort. */
public final class FunctionCall {

    private final String callId;
    private final String name;
    private final String arguments;

    public FunctionCall(String callId, String name, String arguments) {
        if (callId == null || callId.isEmpty()) {
            throw new IllegalArgumentException("callId must not be empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        this.callId = callId;
        this.name = name;
        this.arguments = arguments == null || arguments.trim().isEmpty() ? "{}" : arguments;
    }

    public String callId() {
        return callId;
    }

    public String name() {
        return name;
    }

    /** Argumente als JSON-Objekttext, nie {@code null} ({@code {}} wenn das Modell keine liefert). */
    public String arguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "FunctionCall[" + name + ", " + callId + "]";
    }
}
