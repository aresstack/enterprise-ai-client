package com.aresstack.enterpriseai.proasteion.exedra.chat;

/**
 * The tool budget of a turn with the chat's browser on (#141): how many tool rounds the model may use before it has
 * to answer. Browsing needs more rounds than a knowledge lookup (open, list links, follow, read, mark …), so it has its
 * own default; the composer sets it per chat, any value from {@link #MIN} to {@link #MAX}, the slider covers the
 * usual range.
 */
public final class BrowserBudget {

    public static final int DEFAULT = 20;
    public static final int MIN = 1;
    public static final int MAX = 999;
    public static final int SLIDER_MIN = 4;
    public static final int SLIDER_MAX = 60;

    private BrowserBudget() {
    }

    /** {@code value} within {@link #MIN}..{@link #MAX}; 0 or less means the default. */
    public static int of(int value) {
        return value <= 0 ? DEFAULT : Math.min(MAX, Math.max(MIN, value));
    }

    /**
     * The tool rounds of a turn: the browser budget when the browser is on, never fewer than the general limit
     * {@code generalRounds} (chat.tools.maxRounds), else the general limit.
     */
    public static int roundsFor(boolean browserOn, int budget, int generalRounds) {
        return browserOn ? Math.max(of(budget), Math.max(1, generalRounds)) : Math.max(1, generalRounds);
    }
}
