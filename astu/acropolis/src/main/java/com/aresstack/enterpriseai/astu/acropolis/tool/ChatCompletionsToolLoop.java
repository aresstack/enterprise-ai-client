package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatToolCall;
import com.aresstack.enterpriseai.astu.chat.ChatToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Die Werkzeug-Schleife über {@code /chat/completions} (wie {@link ToolCallingChatPort}, aber über den
 * gerouteten {@link ChatCompletionPort} statt {@code /responses}): die Anfrage geht mit den Werkzeugen der
 * {@link ToolRegistry} als {@code tools} hinaus; solange die Antwort {@code tool_calls} enthält, führt die Schleife
 * sie aus, hängt Aufruf (assistant) und Ergebnis ({@code role=tool}) an den Verlauf dieser Anfrage und fragt erneut,
 * höchstens {@code maxRounds} Mal. Entscheidend sind die gelieferten {@code tool_calls}, nicht {@code finish_reason};
 * fehlt einem Aufruf die {@code id}, vergibt die Schleife {@code call_<runde>_<index>}. Die fertige Antwort kommt als ein Delta (kein Streaming auf diesem Pfad).
 *
 * <p>Werkzeugaufrufe und -ergebnisse bleiben in dieser Anfrage; in die Historie kommt nur die fertige Antwort.
 * Unbekannte Werkzeuge und Werkzeugfehler gehen als Text an das Modell zurück. Ein Abbruch meldet sofort
 * {@code onCancelled}; eine noch laufende HTTP-Anfrage endet im Hintergrund, ihr Ergebnis wird verworfen.
 */
public final class ChatCompletionsToolLoop implements ChatCompletionPort {

    /** Höchstzahl der Werkzeugrunden je Anfrage. */
    public static final int DEFAULT_MAX_ROUNDS = 8;

    private final ChatCompletionPort chat;
    private final ToolRegistry tools;
    private final Executor executor;
    private final ToolActivityListener activity;
    private final int maxRounds;

    public ChatCompletionsToolLoop(ChatCompletionPort chat, ToolRegistry tools, Executor executor,
                                   ToolActivityListener activity, int maxRounds) {
        if (chat == null || tools == null || executor == null) {
            throw new IllegalArgumentException("chat, tools and executor must not be null");
        }
        if (maxRounds < 1) {
            throw new IllegalArgumentException("maxRounds must be positive");
        }
        this.chat = chat;
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
        final ToolLoopTask task = new ToolLoopTask(listener);
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
                        ChatResponse response = ChatCompletionsToolLoop.this.run(request, task);
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

    private ChatResponse run(ChatRequest request, ToolLoopTask task) {
        List<ChatToolDefinition> definitions = new ArrayList<ChatToolDefinition>();
        for (ToolDefinition definition : tools.definitions()) {
            definitions.add(new ChatToolDefinition(definition.name(), definition.description(),
                    definition.parametersSchema()));
        }
        List<ChatMessage> messages = new ArrayList<ChatMessage>(request.messages());
        ChatResponse response = chat.complete(new ChatRequest(messages, request.options(), definitions));
        int round = 0;
        while (response.message().hasToolCalls()) {
            if (task != null && task.isCancelRequested()) {
                return null;
            }
            if (++round > maxRounds) {
                throw new ChatCompletionException(ChatErrorKind.PROTOCOL,
                        "das Modell hat nach " + maxRounds + " Werkzeugrunden noch keine Antwort gegeben");
            }
            // Ohne id vom Server vergibt die Schleife eine eigene, damit Aufruf und Ergebnis zusammenpassen (ein
            // strenger Server lehnt eine leere tool_call_id ab).
            List<ChatToolCall> calls = new ArrayList<ChatToolCall>();
            for (int i = 0; i < response.message().toolCalls().size(); i++) {
                ChatToolCall call = response.message().toolCalls().get(i);
                calls.add(call.id().isEmpty()
                        ? new ChatToolCall("call_" + round + "_" + i, call.name(), call.argumentsJson()) : call);
            }
            messages.add(ChatMessage.assistantToolCalls(response.message().content(), calls));
            for (ChatToolCall call : calls) {
                messages.add(ChatMessage.tool(call.id(), tools.execute(call.name(), call.argumentsJson(), activity)));
            }
            if (task != null && task.isCancelRequested()) {
                return null;
            }
            response = chat.complete(new ChatRequest(messages, request.options(), definitions));
        }
        return response;
    }
}
