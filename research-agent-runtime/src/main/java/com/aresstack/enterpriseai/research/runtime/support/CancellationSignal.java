package com.aresstack.enterpriseai.research.runtime.support;

/**
 * A neutral, framework-free cancellation query. The research runtime's cooperative cancellation
 * (an {@code AtomicBoolean} gate) adapts to this so the layout resolver and the inference port can
 * be cancellation-aware without importing any runtime type. {@link #none()} never cancels.
 */
public interface CancellationSignal {

    static CancellationSignal none() {
        return new CancellationSignal() {
            public boolean isCancelled() {
                return false;
            }
        };
    }

    boolean isCancelled();
}
