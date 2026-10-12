package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

/**
 * Whether the answer planning step runs and how far its interpretation may be trusted: from {@link #useAtLeast()} the
 * interpretation is used, from {@link #askBelow()} it is a hint only (no risky or irreversible action on it), below
 * that a clarifying question is preferred. Read per turn, so changes apply without restart.
 */
public final class AnswerPlanningPolicy {

    public static final int DEFAULT_USE_AT_PERCENT = 85;
    public static final int DEFAULT_ASK_BELOW_PERCENT = 60;

    private final boolean enabled;
    private final int useAtPercent;
    private final int askBelowPercent;

    public AnswerPlanningPolicy(boolean enabled, int useAtPercent, int askBelowPercent) {
        this.enabled = enabled;
        this.useAtPercent = clamp(useAtPercent);
        this.askBelowPercent = Math.min(clamp(askBelowPercent), this.useAtPercent);
    }

    public static AnswerPlanningPolicy off() {
        return new AnswerPlanningPolicy(false, DEFAULT_USE_AT_PERCENT, DEFAULT_ASK_BELOW_PERCENT);
    }

    public static AnswerPlanningPolicy defaults() {
        return new AnswerPlanningPolicy(true, DEFAULT_USE_AT_PERCENT, DEFAULT_ASK_BELOW_PERCENT);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Confidence from which the interpretation may be used for the further work. */
    public double useAtLeast() {
        return useAtPercent / 100.0;
    }

    /** Confidence below which a clarifying question is preferred. */
    public double askBelow() {
        return askBelowPercent / 100.0;
    }

    public int useAtPercent() {
        return useAtPercent;
    }

    public int askBelowPercent() {
        return askBelowPercent;
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
