package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;

/**
 * Answer one turn with the clarifying question of an {@link AnswerPlan} instead of calling the model: the main agent
 * does not start, the question arrives as one delta and lands in the history like any answer.
 */
public final class ClarificationPort implements ChatCompletionPort {

    private final String question;

    public ClarificationPort(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        this.question = question.trim();
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
        listener.onDelta(question);
        listener.onComplete(response());
        return DONE;
    }

    private ChatResponse response() {
        return new ChatResponse(ChatMessage.assistant(question), ChatFinishReason.STOP, null, null);
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
