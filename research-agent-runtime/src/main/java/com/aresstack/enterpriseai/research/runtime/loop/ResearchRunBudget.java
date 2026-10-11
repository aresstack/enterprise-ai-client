package com.aresstack.enterpriseai.research.runtime.loop;

/**
 * The hard limits of one autonomous research run. Immutable. {@link #UNLIMITED} (0) means "no limit" for every
 * maximum, explicitly and in one place ({@link #reached}); askai's SearchStrategyProfile read 0 as zero visits.
 */
public final class ResearchRunBudget {

    private final int maxToolCalls;
    private final int maxPagesVisited;
    private final int maxAcceptedSources;
    private final int maxConsecutiveErrors;
    private final long maxDurationMillis;
    private final int minimumAcceptedSources;
    private final int minimumDistinctHosts;

    public ResearchRunBudget(int maxToolCalls, int maxPagesVisited, int maxAcceptedSources,
                             int maxConsecutiveErrors, long maxDurationMillis,
                             int minimumAcceptedSources, int minimumDistinctHosts) {
        this.maxToolCalls = maxToolCalls;
        this.maxPagesVisited = maxPagesVisited;
        this.maxAcceptedSources = maxAcceptedSources;
        this.maxConsecutiveErrors = maxConsecutiveErrors;
        this.maxDurationMillis = maxDurationMillis;
        this.minimumAcceptedSources = minimumAcceptedSources;
        this.minimumDistinctHosts = minimumDistinctHosts;
    }

    /** A maximum of 0 is no limit. */
    public static final int UNLIMITED = 0;

    /**
     * Enterprise defaults: 4 pages per run ({@code research.maxPages}), and one distinct host suffices because
     * Confluence sources all live on the same host.
     */
    public static ResearchRunBudget defaults() {
        return new ResearchRunBudget(30, 4, 8, 3, 10L * 60 * 1000, 3, 1);
    }

    /** True once {@code used} reached a positive {@code limit}; a limit of {@link #UNLIMITED} is never reached. */
    public static boolean reached(long used, long limit) {
        return limit > UNLIMITED && used >= limit;
    }

    public int getMaxToolCalls() { return maxToolCalls; }
    public int getMaxPagesVisited() { return maxPagesVisited; }
    public int getMaxAcceptedSources() { return maxAcceptedSources; }
    public int getMaxConsecutiveErrors() { return maxConsecutiveErrors; }
    public long getMaxDurationMillis() { return maxDurationMillis; }
    public int getMinimumAcceptedSources() { return minimumAcceptedSources; }
    public int getMinimumDistinctHosts() { return minimumDistinctHosts; }
}
