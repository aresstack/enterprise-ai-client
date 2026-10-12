package com.aresstack.enterpriseai.research.runtime.search.provider;

import java.util.Map;

/**
 * Repeats a search when the provider reports a retryable failure ({@link SearchProviderTemporaryException},
 * {@link SearchProviderRateLimitException}; {@link SearchProviderException#isRetryable()}). Authentication,
 * configuration, response and "not implemented" failures go straight to the strategy, which keeps its own graceful
 * degradation. Waits {@code initialDelayMillis} before the first retry and {@link #BACKOFF_FACTOR} times longer before
 * each further one, capped at {@link #MAX_DELAY_MILLIS} — the same staging as the host's {@code RetryPolicy}. The
 * host hands the user's setting ({@code knowledge.sources.retryCount}/{@code retryDelayMillis}) to the agent process
 * as {@link #ENV_RETRY_COUNT} and {@link #ENV_RETRY_DELAY_MILLIS}.
 */
public final class RetryingSearchProvider implements SearchProvider {

    public static final String ENV_RETRY_COUNT = "ENTERPRISE_AI_SEARCH_RETRY_COUNT";
    public static final String ENV_RETRY_DELAY_MILLIS = "ENTERPRISE_AI_SEARCH_RETRY_DELAY_MILLIS";
    /** Used when the host did not set {@link #ENV_RETRY_COUNT} (older host). */
    public static final int DEFAULT_RETRIES = 2;
    /** Used when the host did not set {@link #ENV_RETRY_DELAY_MILLIS}. */
    public static final long DEFAULT_INITIAL_DELAY_MILLIS = 500L;
    public static final double BACKOFF_FACTOR = 2.5;
    public static final long MAX_DELAY_MILLIS = 30_000L;
    /** Upper bound for a configured retry count; larger values fall back to the default. */
    public static final int MAX_RETRIES = 10;

    /** Waits between two attempts; {@link Thread#sleep} in production. */
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final SearchProvider delegate;
    private final int retries;
    private final long initialDelayMillis;
    private final Sleeper sleeper;

    public RetryingSearchProvider(SearchProvider delegate, int retries, long initialDelayMillis, Sleeper sleeper) {
        if (delegate == null || sleeper == null) {
            throw new IllegalArgumentException("delegate and sleeper must not be null");
        }
        if (retries < 0 || initialDelayMillis < 0) {
            throw new IllegalArgumentException("retries and initialDelayMillis must not be negative");
        }
        this.delegate = delegate;
        this.retries = retries;
        this.initialDelayMillis = initialDelayMillis;
        this.sleeper = sleeper;
    }

    /** The provider with the retry setting the host put into the agent's environment. */
    public static RetryingSearchProvider fromEnvironment(SearchProvider delegate, Map<String, String> environment) {
        int retries = (int) number(environment, ENV_RETRY_COUNT, DEFAULT_RETRIES, MAX_RETRIES);
        long delay = number(environment, ENV_RETRY_DELAY_MILLIS, DEFAULT_INITIAL_DELAY_MILLIS, MAX_DELAY_MILLIS);
        return new RetryingSearchProvider(delegate, retries, delay, Thread::sleep);
    }

    private static long number(Map<String, String> environment, String key, long fallback, long max) {
        String raw = environment == null ? null : environment.get(key);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            long value = Long.parseLong(raw.trim());
            return value < 0 || value > max ? fallback : value;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    public int getRetries() {
        return retries;
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    @Override
    public SearchProviderId getProviderId() {
        return delegate.getProviderId();
    }

    @Override
    public SearchProviderResult search(SearchProviderRequest request) {
        for (int retry = 1; ; retry++) {
            try {
                return delegate.search(request);
            } catch (SearchProviderException failure) {
                if (retry > retries || !failure.isRetryable()) {
                    throw failure;
                }
                double delay = initialDelayMillis * Math.pow(BACKOFF_FACTOR, retry - 1);
                try {
                    sleeper.sleep((long) Math.min(delay, (double) MAX_DELAY_MILLIS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw failure;
                }
            }
        }
    }

    @Override
    public SearchProviderAvailability getAvailability() {
        return delegate.getAvailability();
    }
}
