package com.aresstack.enterpriseai.astu.acropolis.tool;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;

/** Handle eines Laufs einer Werkzeug-Schleife; garantiert genau einen Abschluss-Callback. */
final class ToolLoopTask implements ChatTask {

    private final ChatStreamListener listener;
    private volatile boolean cancelRequested;
    private boolean finished;
    private boolean cancelled;

    ToolLoopTask(ChatStreamListener listener) {
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
