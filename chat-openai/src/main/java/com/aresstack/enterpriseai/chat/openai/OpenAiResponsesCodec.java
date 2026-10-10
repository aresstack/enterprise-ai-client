package com.aresstack.enterpriseai.chat.openai;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.FunctionCall;
import com.aresstack.enterpriseai.chat.api.ResponsesRequest;
import com.aresstack.enterpriseai.chat.api.ResponsesResult;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.chat.api.ToolOutput;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON von und nach {@code POST <baseUrl>/responses} in der Form, die Angelos PowerShell-Test gegen das Gateway
 * bestätigt hat: Werkzeuge flach als {@code {"type":"function","name","description","parameters"}}, kein
 * {@code tool_choice}, Fortsetzung über {@code previous_response_id} mit {@code function_call_output}-Items.
 * Die Antwort wird tolerant gelesen: Items vom Typ {@code function_call} und {@code message} (Teile
 * {@code output_text}), ersatzweise ein oberstes {@code output_text}; Unbekanntes wird übergangen.
 */
final class OpenAiResponsesCodec {

    private final String defaultModel;

    OpenAiResponsesCodec(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    String write(ResponsesRequest request) {
        ChatOptions options = request.options();
        JsonObject body = new JsonObject();
        body.addProperty("model", options.model() != null ? options.model() : defaultModel);
        JsonArray input = new JsonArray();
        if (request.instructions() != null) {
            // Auch bei Fortsetzungen: previous_response_id übernimmt die Anweisungen nicht.
            body.addProperty("instructions", request.instructions());
        }
        if (request.isContinuation()) {
            body.addProperty("previous_response_id", request.previousResponseId());
            for (ToolOutput output : request.toolOutputs()) {
                JsonObject item = new JsonObject();
                item.addProperty("type", "function_call_output");
                item.addProperty("call_id", output.callId());
                item.addProperty("output", output.output());
                input.add(item);
            }
        } else {
            for (ChatMessage message : request.messages()) {
                JsonObject item = new JsonObject();
                item.addProperty("role", message.role() == ChatRole.ASSISTANT
                        ? "assistant" : "user");
                item.addProperty("content", message.content());
                input.add(item);
            }
        }
        body.add("input", input);
        if (!request.tools().isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolDefinition definition : request.tools()) {
                JsonObject tool = new JsonObject();
                tool.addProperty("type", "function");
                tool.addProperty("name", definition.name());
                tool.addProperty("description", definition.description());
                tool.add("parameters", JsonParser.parseString(definition.parametersSchema()));
                tools.add(tool);
            }
            body.add("tools", tools);
        }
        if (options.temperature() != null) {
            body.addProperty("temperature", options.temperature());
        }
        if (options.topP() != null) {
            body.addProperty("top_p", options.topP());
        }
        if (options.maxTokens() != null) {
            body.addProperty("max_output_tokens", options.maxTokens());
        }
        if (options.endUserId() != null) {
            body.addProperty("user", options.endUserId());
        }
        return body.toString();
    }

    ResponsesResult read(String body) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "response is not a JSON object");
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "response is not valid JSON", e);
        }
        JsonElement error = root.get("error");
        if (error != null && !error.isJsonNull()) {
            String message = OpenAiResponseParser.errorMessage(body);
            throw new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR,
                    "server reported an error: " + OpenAiErrors.shorten(message == null ? error.toString() : message));
        }
        List<FunctionCall> calls = new ArrayList<FunctionCall>();
        StringBuilder text = new StringBuilder();
        JsonElement output = root.get("output");
        if (output != null && output.isJsonArray()) {
            for (JsonElement element : output.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                String type = string(item, "type");
                if ("function_call".equals(type)) {
                    String callId = string(item, "call_id");
                    if (callId == null) {
                        callId = string(item, "id");
                    }
                    JsonElement arguments = item.get("arguments");
                    String args = arguments == null || arguments.isJsonNull() ? "{}"
                            : arguments.isJsonPrimitive() ? arguments.getAsString() : arguments.toString();
                    String name = string(item, "name");
                    if (callId != null && name != null) {
                        calls.add(new FunctionCall(callId, name, args));
                    }
                } else if ("message".equals(type)) {
                    appendContent(item.get("content"), text);
                }
            }
        }
        if (text.length() == 0) {
            JsonElement outputText = root.get("output_text");
            if (outputText != null && outputText.isJsonPrimitive()) {
                text.append(outputText.getAsString());
            }
        }
        return new ResponsesResult(string(root, "id"), calls, text.toString(), string(root, "model"));
    }

    private static void appendContent(JsonElement content, StringBuilder text) {
        if (content == null || content.isJsonNull()) {
            return;
        }
        if (content.isJsonPrimitive()) {
            text.append(content.getAsString());
            return;
        }
        if (!content.isJsonArray()) {
            return;
        }
        for (JsonElement part : content.getAsJsonArray()) {
            if (part.isJsonObject()) {
                JsonObject object = part.getAsJsonObject();
                String type = string(object, "type");
                String value = string(object, "text");
                if (value != null && (type == null || "output_text".equals(type) || "text".equals(type))) {
                    text.append(value);
                }
            } else if (part.isJsonPrimitive()) {
                text.append(part.getAsString());
            }
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }
}
