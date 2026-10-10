package com.aresstack.enterpriseai.chat.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Eine Antwort des werkzeugfähigen Endpunkts: ihre Kennung, verlangte Werkzeugaufrufe und der Antworttext. */
public final class ResponsesResult {

    private final String id;
    private final List<FunctionCall> functionCalls;
    private final String outputText;
    private final String model;

    public ResponsesResult(String id, List<FunctionCall> functionCalls, String outputText, String model) {
        this.id = id;
        this.functionCalls = functionCalls == null || functionCalls.isEmpty()
                ? Collections.<FunctionCall>emptyList()
                : Collections.unmodifiableList(new ArrayList<FunctionCall>(functionCalls));
        this.outputText = outputText == null ? "" : outputText;
        this.model = model;
    }

    /** Kennung für {@code previous_response_id}; {@code null}, wenn der Server keine nennt. */
    public String id() {
        return id;
    }

    public List<FunctionCall> functionCalls() {
        return functionCalls;
    }

    public boolean hasFunctionCalls() {
        return !functionCalls.isEmpty();
    }

    /** Der Antworttext (alle Textteile zusammen), nie {@code null}. */
    public String outputText() {
        return outputText;
    }

    /** {@code null}, wenn der Server es nicht nennt. */
    public String model() {
        return model;
    }

    @Override
    public String toString() {
        return "ResponsesResult[" + id + ", " + functionCalls.size() + " calls, " + outputText.length() + " Zeichen]";
    }
}
