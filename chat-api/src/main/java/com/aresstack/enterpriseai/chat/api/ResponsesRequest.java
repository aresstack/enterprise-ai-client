package com.aresstack.enterpriseai.chat.api;

import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eine Anfrage an den werkzeugfähigen Antwort-Endpunkt: entweder der Beginn (Anweisungen plus Nachrichten) oder
 * die Fortsetzung einer vorherigen Antwort mit Werkzeugergebnissen. Die Werkzeuge werden jedes Mal mitgegeben;
 * eine Werkzeugwahl ({@code tool_choice}) gibt es bewusst nicht (das Gateway antwortet auf "required" mit 500).
 */
public final class ResponsesRequest {

    private final String instructions;
    private final List<ChatMessage> messages;
    private final String previousResponseId;
    private final List<ToolOutput> toolOutputs;
    private final List<ToolDefinition> tools;
    private final ChatOptions options;

    private ResponsesRequest(String instructions, List<ChatMessage> messages, String previousResponseId,
                             List<ToolOutput> toolOutputs, List<ToolDefinition> tools, ChatOptions options) {
        this.instructions = instructions == null || instructions.trim().isEmpty() ? null : instructions;
        this.messages = copy(messages);
        this.previousResponseId = previousResponseId;
        this.toolOutputs = copy(toolOutputs);
        this.tools = copy(tools);
        this.options = options == null ? ChatOptions.defaults() : options;
    }

    /** Beginn einer Antwort: System-Anweisungen (oder {@code null}) und der Verlauf ohne System-Nachrichten. */
    public static ResponsesRequest start(String instructions, List<ChatMessage> messages, List<ToolDefinition> tools,
                                         ChatOptions options) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        return new ResponsesRequest(instructions, messages, null, null, tools, options);
    }

    /**
     * Fortsetzung der Antwort {@code previousResponseId} mit den Ergebnissen ihrer Werkzeugaufrufe. Die
     * Anweisungen werden erneut mitgegeben, weil {@code previous_response_id} sie nicht übernimmt.
     */
    public static ResponsesRequest continueWith(String previousResponseId, String instructions,
                                                List<ToolOutput> outputs, List<ToolDefinition> tools,
                                                ChatOptions options) {
        if (previousResponseId == null || previousResponseId.isEmpty()) {
            throw new IllegalArgumentException("previousResponseId must not be empty");
        }
        if (outputs == null || outputs.isEmpty()) {
            throw new IllegalArgumentException("outputs must not be empty");
        }
        return new ResponsesRequest(instructions, null, previousResponseId, outputs, tools, options);
    }

    /** {@code null} ohne Anweisungen. */
    public String instructions() {
        return instructions;
    }

    /** Nachrichten des Beginns; leer bei einer Fortsetzung. */
    public List<ChatMessage> messages() {
        return messages;
    }

    /** {@code null} beim Beginn. */
    public String previousResponseId() {
        return previousResponseId;
    }

    public boolean isContinuation() {
        return previousResponseId != null;
    }

    public List<ToolOutput> toolOutputs() {
        return toolOutputs;
    }

    public List<ToolDefinition> tools() {
        return tools;
    }

    public ChatOptions options() {
        return options;
    }

    private static <T> List<T> copy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        for (T value : values) {
            if (value == null) {
                throw new IllegalArgumentException("list must not contain null");
            }
        }
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    @Override
    public String toString() {
        return "ResponsesRequest[" + (isContinuation() ? "continue, " + toolOutputs.size() + " outputs"
                : messages.size() + " messages") + ", " + tools.size() + " tools]";
    }
}
