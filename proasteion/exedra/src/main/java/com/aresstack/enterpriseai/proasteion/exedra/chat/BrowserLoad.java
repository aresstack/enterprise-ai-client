package com.aresstack.enterpriseai.proasteion.exedra.chat;

/**
 * How browser_open_many loads in a chat (#141): tabs at the same time and the maximum random delay before each new
 * tab, set per chat in the globe's popup.
 */
public final class BrowserLoad {

    public static final int DEFAULT_TABS = 4;
    public static final int MIN_TABS = 1;
    public static final int MAX_TABS = 8;
    public static final int DEFAULT_DELAY = 0;
    public static final int MAX_DELAY = 10000;
    public static final int SLIDER_MAX_DELAY = 2000;

    private BrowserLoad() {
    }

    /** {@code value} within the tab range; 0 or less means the default. */
    public static int tabs(int value) {
        return value <= 0 ? DEFAULT_TABS : Math.min(MAX_TABS, Math.max(MIN_TABS, value));
    }

    /** {@code value} within 0..{@link #MAX_DELAY}. */
    public static int delay(int value) {
        return Math.max(0, Math.min(MAX_DELAY, value));
    }
}
