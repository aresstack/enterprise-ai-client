package com.aresstack.enterpriseai.proasteion.application.chat;

import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Wiederholt eine einzelne Modellanfrage nach einem vorübergehenden Fehler ({@code chat.retryCount},
 * {@code chat.retryDelayMillis}): Verbindung/Timeout ({@code TRANSPORT}), Drosselung ({@code RATE_LIMITED}) und
 * Serverfehler ({@code PROVIDER_ERROR}). Nie wiederholt werden eine abgelehnte Anfrage, ein Protokollfehler, ein
 * echtes 401/403 und eine nicht beschaffbare Zugangsangabe ({@code CREDENTIALS_UNAVAILABLE}; dafür hat der
 * Security-Port selbst schon wiederholt).
 *
 * <p>Der Decorator sitzt unter der Werkzeugschleife: eine Wiederholung schickt genau dieselbe Anfrage mit demselben
 * Verlauf (samt bisheriger Werkzeugaufrufe und -ergebnisse) noch einmal; kein Werkzeug läuft dadurch erneut, und
 * die Benutzeranfrage beginnt nicht von vorn. Gestreamt wird nur wiederholt, solange diese Anfrage noch kein
 * Textstück geliefert hat; danach würde ein Neuabspielen Text doppelt zeigen, der Fehler geht dann unverändert
 * weiter. Ein Abbruch beendet auch die Pause zwischen zwei Versuchen sofort.
 */
public final class RetryingChatCompletionPort implements ChatCompletionPort {

    private static final Logger LOG = Logger.getLogger(RetryingChatCompletionPort.class.getName());

    private final ChatCompletionPort delegate;
    private final RetryPolicy policy;
    private final RetryPolicy.Sleeper sleeper;

    public RetryingChatCompletionPort(ChatCompletionPort delegate, RetryPolicy policy) {
        this(delegate, policy, RetryPolicy.Sleeper.threadSleep());
    }

    RetryingChatCompletionPort(ChatCompletionPort delegate, RetryPolicy policy, RetryPolicy.Sleeper sleeper) {
        if (delegate == null || policy == null || sleeper == null) {
            throw new IllegalArgumentException("delegate, policy and sleeper must not be null");
        }
        this.delegate = delegate;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    /** Vorübergehend und damit wiederholbar: Transport, Drosselung, Serverfehler. */
    public static boolean isTransient(ChatCompletionException error) {
        switch (error.kind()) {
            case TRANSPORT:
            case RATE_LIMITED:
            case PROVIDER_ERROR:
                return true;
            default:
                return false;
        }
    }

    @Override
    public ChatResponse complete(final ChatRequest request) {
        return policy.call(() -> delegate.complete(request), ChatCompletionException.class,
                RetryingChatCompletionPort::isTransient, RetryingChatCompletionPort::log, sleeper);
    }

    @Override
    public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        RetryingTask task = new RetryingTask(request, listener);
        task.attempt();
        return task;
    }

    private static void log(int retry, int retries, long delay, ChatCompletionException failure) {
        LOG.info("Modellanfrage vorübergehend gescheitert (" + failure.kind()
                + (failure.statusCode() > 0 ? " " + failure.statusCode() : "") + "); Wiederholung " + retry
                + " von " + retries + " in " + delay + " ms");
    }

    @Override
    public String toString() {
        return "RetryingChat[" + delegate + ", " + policy + "]";
    }

    /** Eine gestreamte Anfrage über mehrere Versuche; genau ein Abschluss-Callback an den Aufrufer. */
    private final class RetryingTask implements ChatTask {

        private final ChatRequest request;
        private final ChatStreamListener listener;
        private final CountDownLatch cancelSignal = new CountDownLatch(1);
        private final AtomicBoolean finished = new AtomicBoolean();
        private volatile boolean cancelRequested;
        private volatile boolean cancelled;
        private volatile ChatTask current;
        private volatile boolean started;
        private volatile boolean delivered;
        private int retry;

        RetryingTask(ChatRequest request, ChatStreamListener listener) {
            this.request = request;
            this.listener = listener;
        }

        void attempt() {
            ChatTask next = delegate.stream(request, new AttemptListener());
            current = next;
            if (cancelRequested) {
                next.cancel();
            }
        }

        @Override
        public void cancel() {
            if (finished.get()) {
                return;
            }
            cancelRequested = true;
            cancelSignal.countDown();
            ChatTask running = current;
            if (running != null) {
                running.cancel();
            }
        }

        @Override
        public boolean isDone() {
            return finished.get();
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        private void failed(ChatCompletionException error) {
            if (delivered || cancelRequested || retry >= policy.retries() || !isTransient(error)) {
                if (cancelRequested) {
                    finishCancelled();
                } else if (finished.compareAndSet(false, true)) {
                    listener.onError(error);
                }
                return;
            }
            retry++;
            long delay = policy.delayBeforeRetry(retry);
            log(retry, policy.retries(), delay, error);
            if (pause(delay)) {
                finishCancelled();
                return;
            }
            attempt();
        }

        /** @return {@code true}, wenn die Pause durch einen Abbruch endete */
        private boolean pause(long delay) {
            try {
                if (delay > 0 && cancelSignal.await(delay, TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return true;
            }
            return cancelRequested;
        }

        private void finishCancelled() {
            if (finished.compareAndSet(false, true)) {
                cancelled = true;
                listener.onCancelled();
            }
        }

        private final class AttemptListener implements ChatStreamListener {

            @Override
            public void onStart() {
                if (!started) {
                    started = true;
                    listener.onStart();
                }
            }

            @Override
            public void onDelta(String text) {
                delivered = true;
                listener.onDelta(text);
            }

            @Override
            public void onComplete(ChatResponse response) {
                if (finished.compareAndSet(false, true)) {
                    listener.onComplete(response);
                }
            }

            @Override
            public void onError(ChatCompletionException error) {
                failed(error);
            }

            @Override
            public void onCancelled() {
                finishCancelled();
            }
        }
    }
}
