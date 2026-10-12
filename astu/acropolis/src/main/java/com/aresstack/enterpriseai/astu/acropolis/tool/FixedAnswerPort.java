package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;

/**
 * Ein Port für genau einen Turn, dessen Antwort der Client selbst schreibt (z. B. {@link ToolCapabilities#answer}):
 * kein Modellaufruf, der Text kommt am Stück als ein Delta und landet wie jede Antwort in der Historie.
 */
public final class FixedAnswerPort implements ChatCompletionPort {

    private final String answer;

    public FixedAnswerPort(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new IllegalArgumentException("answer must not be blank");
        }
        this.answer = answer;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        return response();
    }

    @Override
    public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listener.onStart();
        listener.onDelta(answer);
        listener.onComplete(response());
        return DONE;
    }

    private ChatResponse response() {
        return new ChatResponse(ChatMessage.assistant(answer), ChatFinishReason.STOP, null, null);
    }

    private static final ChatTask DONE = new ChatTask() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };
}
