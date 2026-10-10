package com.aresstack.enterpriseai.application.tool;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.FunctionCall;
import com.aresstack.enterpriseai.chat.api.ResponsesPort;
import com.aresstack.enterpriseai.chat.api.ResponsesRequest;
import com.aresstack.enterpriseai.chat.api.ResponsesResult;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.chat.api.ToolOutput;
import com.aresstack.enterpriseai.domain.chat.ChatFinishReason;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.chat.ChatRole;
import com.aresstack.enterpriseai.domain.chat.ChatUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Die Werkzeug-Schleife als {@link ChatCompletionPort}, damit der {@code ChatService} sie wie jeden Turn behandelt
 * (Historie, Abbruch): die Anfrage geht an den {@link ResponsesPort} mit den Werkzeugen der {@link ToolRegistry};
 * solange die Antwort Werkzeugaufrufe enthält, führt die Schleife sie aus und schickt die Ergebnisse mit
 * {@code previous_response_id} zurück, höchstens {@code maxRounds} Mal. Die fertige Antwort kommt als ein Delta
 * (kein Streaming auf diesem Pfad).
 *
 * <p>System-Nachrichten werden zu den Anweisungen der Anfrage, der übrige Verlauf zur Eingabe. Unbekannte
 * Werkzeuge und Werkzeugfehler gehen als Text an das Modell zurück, damit es reagieren kann. Ein Abbruch meldet
 * sofort {@code onCancelled}; eine noch laufende HTTP-Anfrage endet im Hintergrund, ihr Ergebnis wird verworfen.
 */
public final class ToolCallingChatPort implements ChatCompletionPort {

    /** Höchstzahl der Werkzeugrunden je Anfrage. */
    public static final int DEFAULT_MAX_ROUNDS = 8;

    private final ResponsesPort responses;
    private final ToolRegistry tools;
    private final Executor executor;
    private final ToolActivityListener activity;
    private final int maxRounds;

    public ToolCallingChatPort(ResponsesPort responses, ToolRegistry tools, Executor executor,
                               ToolActivityListener activity, int maxRounds) {
        if (responses == null || tools == null || executor == null) {
            throw new IllegalArgumentException("responses, tools and executor must not be null");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be positive");
        }
        this.responses = responses;
        this.tools = tools;
        this.executor = executor;
        this.activity = activity;
        this.maxRounds = maxRounds;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        return run(request, null);
    }

    @Override
    public ChatTask stream(final ChatRequest request, final ChatStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        final Task task = new Task(listener);
        try {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (task.isCancelRequested()) {
                        task.finish(null, null);
                        return;
                    }
                    listener.onStart();
                    try {
                        ChatResponse response = ToolCallingChatPort.this.run(request, task);
                        task.finish(response, null);
                    } catch (ChatCompletionException e) {
                        task.finish(null, e);
                    } catch (RuntimeException e) {
                        task.finish(null, new ChatCompletionException(ChatErrorKind.PROTOCOL,
                                "tool loop failed: " + e.getClass().getSimpleName(), e));
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            task.finish(null, new ChatCompletionException(ChatErrorKind.TRANSPORT, "no thread available", e));
        }
        return task;
    }

    private ChatResponse run(ChatRequest request, Task task) {
        List<ToolDefinition> definitions = tools.definitions();
        StringBuilder instructions = new StringBuilder();
        List<ChatMessage> input = new ArrayList<ChatMessage>();
        for (ChatMessage message : request.messages()) {
            if (message.role() == ChatRole.SYSTEM || message.role() == ChatRole.DEVELOPER) {
                if (instructions.length() > 0) {
                    instructions.append("\n\n");
                }
                instructions.append(message.content());
            } else {
                input.add(message);
            }
        }
        ResponsesResult result = responses.create(ResponsesRequest.start(
                instructions.length() == 0 ? null : instructions.toString(), input, definitions, request.options()));
        int round = 0;
        while (result.hasFunctionCalls()) {
            if (task != null && task.isCancelRequested()) {
                return null;
            }
            if (++round > maxRounds) {
                throw new ChatCompletionException(ChatErrorKind.PROTOCOL,
                        "das Modell hat nach " + maxRounds + " Werkzeugrunden noch keine Antwort gegeben");
            }
            if (result.id() == null) {
                throw new ChatCompletionException(ChatErrorKind.PROTOCOL, "tool call without response id");
            }
            List<ToolOutput> outputs = new ArrayList<ToolOutput>();
            for (FunctionCall call : result.functionCalls()) {
                outputs.add(new ToolOutput(call.callId(), execute(call)));
            }
            if (task != null && task.isCancelRequested()) {
                return null;
            }
            result = responses.create(ResponsesRequest.continueWith(result.id(), outputs, definitions,
                    request.options()));
        }
        return new ChatResponse(ChatMessage.assistant(result.outputText()), ChatFinishReason.STOP,
                ChatUsage.notReported(), result.model());
    }

    private String execute(FunctionCall call) {
        AiTool tool = tools.find(call.name());
        if (tool == null) {
            return "Fehler: unbekanntes Werkzeug " + call.name();
        }
        if (activity != null) {
            activity.onToolCall(call.name());
        }
        try {
            String output = tool.execute(call.arguments());
            return output == null ? "" : output;
        } catch (RuntimeException e) {
            String message = e.getMessage();
            return "Fehler: " + (message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message);
        }
    }

    /** Handle eines Laufs; garantiert genau einen Abschluss-Callback. */
    private static final class Task implements ChatTask {

        private final ChatStreamListener listener;
        private volatile boolean cancelRequested;
        private boolean finished;
        private boolean cancelled;

        Task(ChatStreamListener listener) {
            this.listener = listener;
        }

        @Override
        public void cancel() {
            synchronized (this) {
                if (finished || cancelRequested) {
                    return;
                }
                cancelRequested = true;
            }
            finish(null, null);
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
                if (!response.content().isEmpty()) {
                    listener.onDelta(response.content());
                }
                listener.onComplete(response);
            }
        }
    }
}
