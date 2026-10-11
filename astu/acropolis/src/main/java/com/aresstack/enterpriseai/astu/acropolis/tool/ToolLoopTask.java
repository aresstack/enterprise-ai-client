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
    /** Die laufende Modellanfrage der aktuellen Runde (gestreamt); ein Abbruch bricht auch sie ab. */
    private ChatTask round;
    /** Schon als Delta weitergereichter Text; {@link #finish} schickt ihn nicht noch einmal. */
    private final StringBuilder streamed = new StringBuilder();

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
        ChatTask running;
        synchronized (this) {
            running = round;
        }
        if (running != null) {
            running.cancel();
        }
        finish(null, null);
    }

    /** Merkt die laufende Runde; wurde schon abgebrochen, wird sie sofort abgebrochen. */
    void round(ChatTask task) {
        boolean cancelNow;
        synchronized (this) {
            round = task;
            cancelNow = cancelRequested;
        }
        if (cancelNow && task != null) {
            task.cancel();
        }
    }

    /** Ein Textstück der laufenden Runde sofort an die Blase (solange der Lauf nicht beendet ist). */
    void delta(String text) {
        synchronized (this) {
            if (finished || cancelRequested) {
                return;
            }
            streamed.append(text);
        }
        listener.onDelta(text);
    }

    /** Alles bisher gestreamte, in Empfangsreihenfolge. */
    synchronized String streamedText() {
        return streamed.toString();
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
            String content = response.content();
            String already = streamedText();
            if (content.startsWith(already) && content.length() > already.length()) {
                listener.onDelta(content.substring(already.length()));
            }
            listener.onComplete(response);
        }
    }
}
