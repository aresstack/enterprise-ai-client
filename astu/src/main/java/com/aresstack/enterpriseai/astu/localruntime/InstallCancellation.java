package com.aresstack.enterpriseai.astu.localruntime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carries a user's request to stop a running model download through every layer down to the transfer. Create one
 * per download; {@link #cancel()} may come from any thread, the downloading thread polls {@link #isCancelled()}
 * before each step and for every received block.
 */
public final class InstallCancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    /**
     * Request the stop.
     *
     * @return {@code true} for the first request, {@code false} when it was already requested
     */
    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    /** Tell whether the stop was requested. */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
