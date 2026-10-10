package com.aresstack.enterpriseai.model.sidecar;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.domain.chat.ChatFinishReason;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.domain.chat.ChatUsage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * {@link ChatCompletionPort} des lokalen Java-21-Sidecars über seine vorhandene Ollama-kompatible
 * {@code POST /api/chat} ({@code stream} im Body, NDJSON-Zeilen mit {@code message.content}, letzte mit
 * {@code done=true}). Rollen nur system/user/assistant (developer wird zu system); Parameter über {@code options}
 * ({@code temperature}, {@code top_p}, {@code num_predict}, {@code stop}). Das Modell ist die Kennung im Katalog
 * {@code local} ohne Präfix. Ob ein Modell wirklich generieren kann, hängt vom Sidecar-Build ab (ohne verlinkte
 * Laufzeit antwortet er mit HTTP 501).
 */
public final class LocalSidecarChatAdapter implements ChatCompletionPort {

    /** Erstes Laden und Generieren lokal kann dauern. */
    private static final int READ_TIMEOUT_MILLIS = 300000;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    private final LocalSidecarProcess process;

    LocalSidecarChatAdapter(LocalSidecarProcess process) {
        this.process = process;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        byte[] body = body(request, false);
        HttpURLConnection connection = null;
        try {
            connection = LocalSidecarHttp.post(process.ensureStarted(), "/api/chat", body, READ_TIMEOUT_MILLIS);
            check(connection);
            JsonObject response = JsonParser.parseString(new String(LocalSidecarHttp.read(
                    connection.getInputStream(), MAX_BODY_BYTES), StandardCharsets.UTF_8)).getAsJsonObject();
            return response(response, content(response), request);
        } catch (IOException e) {
            throw transport(e);
        } catch (RuntimeException e) {
            if (e instanceof ChatCompletionException) {
                throw e;
            }
            throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "Antwort des Sidecars nicht lesbar", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Override
    public ChatTask stream(final ChatRequest request, final ChatStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        final Task task = new Task(listener);
        final byte[] body;
        try {
            body = body(request, true);
        } catch (ChatCompletionException e) {
            task.finish(null, e);
            return task;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                runStream(request, body, task);
            }
        }, "local-sidecar-chat-stream");
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    private void runStream(ChatRequest request, byte[] body, Task task) {
        HttpURLConnection connection = null;
        try {
            if (task.isCancelRequested()) {
                task.finish(null, null);
                return;
            }
            connection = LocalSidecarHttp.post(process.ensureStarted(), "/api/chat", body, READ_TIMEOUT_MILLIS);
            if (!task.attach(connection)) {
                task.finish(null, null);
                return;
            }
            check(connection);
            task.started();
            StringBuilder text = new StringBuilder();
            JsonObject last = null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(),
                    StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    JsonObject event = JsonParser.parseString(line).getAsJsonObject();
                    if (event.has("error")) {
                        throw new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR,
                                "Lokales Modell: " + event.get("error").getAsString());
                    }
                    String delta = content(event);
                    if (!delta.isEmpty()) {
                        text.append(delta);
                        task.delta(delta);
                    }
                    last = event;
                    if (event.has("done") && event.get("done").getAsBoolean()) {
                        break;
                    }
                }
            } finally {
                reader.close();
            }
            if (last == null) {
                throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "Sidecar lieferte keine Antwort");
            }
            task.finish(response(last, text.toString(), request), null);
        } catch (ChatCompletionException e) {
            task.finish(null, task.isCancelRequested() ? null : e);
        } catch (IOException e) {
            task.finish(null, task.isCancelRequested() ? null : transport(e));
        } catch (RuntimeException e) {
            task.finish(null, task.isCancelRequested() ? null
                    : new ChatCompletionException(ChatErrorKind.PROTOCOL, "Antwort des Sidecars nicht lesbar", e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] body(ChatRequest request, boolean stream) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        ChatOptions options = request.options();
        if (options.model() == null) {
            throw new ChatCompletionException(ChatErrorKind.INVALID_REQUEST, "kein lokales Chat-Modell gewählt");
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", options.model());
        body.addProperty("stream", stream);
        JsonArray messages = new JsonArray();
        for (ChatMessage message : request.messages()) {
            JsonObject item = new JsonObject();
            item.addProperty("role", role(message.role()));
            item.addProperty("content", message.content() == null ? "" : message.content());
            messages.add(item);
        }
        body.add("messages", messages);
        JsonObject parameters = new JsonObject();
        if (options.temperature() != null) {
            parameters.addProperty("temperature", options.temperature());
        }
        if (options.topP() != null) {
            parameters.addProperty("top_p", options.topP());
        }
        if (options.maxTokens() != null) {
            parameters.addProperty("num_predict", options.maxTokens());
        }
        if (!options.stop().isEmpty()) {
            JsonArray stop = new JsonArray();
            for (String value : options.stop()) {
                stop.add(value);
            }
            parameters.add("stop", stop);
        }
        if (parameters.size() > 0) {
            body.add("options", parameters);
        }
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String role(ChatRole role) {
        switch (role) {
            case USER:
                return "user";
            case ASSISTANT:
                return "assistant";
            default:
                return "system"; // der Sidecar kennt keine developer-Rolle
        }
    }

    private static String content(JsonObject event) {
        JsonElement message = event.get("message");
        if (message == null || !message.isJsonObject()) {
            return "";
        }
        JsonElement content = message.getAsJsonObject().get("content");
        return content == null || content.isJsonNull() ? "" : content.getAsString();
    }

    private static ChatResponse response(JsonObject last, String text, ChatRequest request) {
        String reason = last.has("done_reason") && !last.get("done_reason").isJsonNull()
                ? last.get("done_reason").getAsString() : "";
        ChatFinishReason finish = "stop".equals(reason) ? ChatFinishReason.STOP
                : "length".equals(reason) ? ChatFinishReason.LENGTH : ChatFinishReason.UNKNOWN;
        ChatUsage usage = ChatUsage.notReported();
        if (last.has("prompt_eval_count") && last.has("eval_count")) {
            int prompt = last.get("prompt_eval_count").getAsInt();
            int completion = last.get("eval_count").getAsInt();
            if (prompt > 0 || completion > 0) {
                usage = ChatUsage.of(prompt, completion, prompt + completion);
            }
        }
        return new ChatResponse(ChatMessage.assistant(text), finish, usage, request.options().model());
    }

    private static void check(HttpURLConnection connection) throws IOException {
        int status = connection.getResponseCode();
        if (status != 200) {
            String detail = LocalSidecarHttp.error(connection);
            throw new ChatCompletionException(status >= 500 ? ChatErrorKind.PROVIDER_ERROR
                    : ChatErrorKind.INVALID_REQUEST, status, "Lokales Modell: HTTP " + status
                    + (detail.isEmpty() ? "" : " " + detail), null);
        }
    }

    private static ChatCompletionException transport(IOException e) {
        return new ChatCompletionException(ChatErrorKind.TRANSPORT, "Lokaler Sidecar nicht erreichbar ("
                + e.getClass().getSimpleName() + ")", e);
    }

    @Override
    public String toString() {
        return "LocalSidecarChatAdapter";
    }

    /** Abschluss genau einmal; ein Abbruch trennt die Verbindung und endet mit {@code onCancelled}. */
    private static final class Task implements ChatTask {

        private final ChatStreamListener listener;
        private volatile boolean cancelRequested;
        private boolean finished;
        private boolean cancelled;
        private HttpURLConnection connection;

        Task(ChatStreamListener listener) {
            this.listener = listener;
        }

        @Override
        public void cancel() {
            HttpURLConnection toClose;
            synchronized (this) {
                if (finished || cancelRequested) {
                    return;
                }
                cancelRequested = true;
                toClose = connection;
            }
            if (toClose != null) {
                toClose.disconnect();
            }
        }

        @Override
        public synchronized boolean isDone() {
            return finished;
        }

        @Override
        public synchronized boolean isCancelled() {
            return cancelled;
        }

        boolean isCancelRequested() {
            return cancelRequested;
        }

        synchronized boolean attach(HttpURLConnection value) {
            connection = value;
            return !cancelRequested;
        }

        void started() {
            listener.onStart();
        }

        void delta(String text) {
            if (!cancelRequested) {
                listener.onDelta(text);
            }
        }

        /** Antwort, Fehler oder (beides {@code null} oder Abbruch angefordert) abgebrochen. */
        void finish(ChatResponse response, ChatCompletionException error) {
            synchronized (this) {
                if (finished) {
                    return;
                }
                finished = true;
                cancelled = cancelRequested || (response == null && error == null);
            }
            if (cancelled) {
                listener.onCancelled();
            } else if (error != null) {
                listener.onError(error);
            } else {
                listener.onComplete(response);
            }
        }
    }
}
