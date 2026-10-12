package com.aresstack.enterpriseai.proasteion.application.composition;

/**
 * Response limits of the help tools. The composition root passes the limits configured for the knowledge tools
 * ({@code agent.tools.*}), so both tool sets follow the same settings.
 */
public final class AppHelpToolLimits {

    private final int maxResponseChars;
    private final int snippetChars;
    private final int defaultMaxResults;

    public AppHelpToolLimits(int maxResponseChars, int snippetChars, int defaultMaxResults) {
        if (maxResponseChars < 1 || snippetChars < 1 || defaultMaxResults < 1) {
            throw new IllegalArgumentException("limits must be positive");
        }
        this.maxResponseChars = maxResponseChars;
        this.snippetChars = snippetChars;
        this.defaultMaxResults = defaultMaxResults;
    }

    public int maxResponseChars() {
        return maxResponseChars;
    }

    public int snippetChars() {
        return snippetChars;
    }

    public int defaultMaxResults() {
        return defaultMaxResults;
    }
}
