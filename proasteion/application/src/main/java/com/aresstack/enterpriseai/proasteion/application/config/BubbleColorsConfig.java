package com.aresstack.enterpriseai.proasteion.application.config;

import java.util.Locale;

/**
 * Farben der Chat-Sprechblasen von Nutzer und Assistent ({@code chat.bubble.*}), als 24-Bit-RGB ohne Kopplung an
 * AWT. Unabhängig vom Hell-/Dunkel-Thema: wer etwa schwarze Schrift auf weißen Blasen besser liest, behält sie in
 * jedem Thema. Fehlende Schlüssel ergeben exakt die bisherigen Farben (Blau/Weiß und Petrol/Weiß).
 */
public final class BubbleColorsConfig {

    public static final String USER_BACKGROUND_KEY = "chat.bubble.user.background";
    public static final String USER_FOREGROUND_KEY = "chat.bubble.user.foreground";
    public static final String ASSISTANT_BACKGROUND_KEY = "chat.bubble.assistant.background";
    public static final String ASSISTANT_FOREGROUND_KEY = "chat.bubble.assistant.foreground";

    /** Die bisherigen Farben der Blasen ({@code BubblePalette.windowsPhoneInspired()}). */
    public static final int DEFAULT_USER_BACKGROUND = 0x1676D2;
    public static final int DEFAULT_USER_FOREGROUND = 0xFFFFFF;
    public static final int DEFAULT_ASSISTANT_BACKGROUND = 0x15827A;
    public static final int DEFAULT_ASSISTANT_FOREGROUND = 0xFFFFFF;

    private static final int RGB_MASK = 0xFFFFFF;
    private static final int HEX_DIGITS = 6;

    private final int userBackground;
    private final int userForeground;
    private final int assistantBackground;
    private final int assistantForeground;

    public BubbleColorsConfig(int userBackground, int userForeground, int assistantBackground,
                              int assistantForeground) {
        this.userBackground = userBackground & RGB_MASK;
        this.userForeground = userForeground & RGB_MASK;
        this.assistantBackground = assistantBackground & RGB_MASK;
        this.assistantForeground = assistantForeground & RGB_MASK;
    }

    public static BubbleColorsConfig defaults() {
        return new BubbleColorsConfig(DEFAULT_USER_BACKGROUND, DEFAULT_USER_FOREGROUND,
                DEFAULT_ASSISTANT_BACKGROUND, DEFAULT_ASSISTANT_FOREGROUND);
    }

    public int userBackground() {
        return userBackground;
    }

    public int userForeground() {
        return userForeground;
    }

    public int assistantBackground() {
        return assistantBackground;
    }

    public int assistantForeground() {
        return assistantForeground;
    }

    /**
     * Liest {@code #RRGGBB} (das {@code #} darf fehlen, Groß-/Kleinschreibung egal); alles andere ergibt
     * {@code null}, nie eine Ausnahme.
     */
    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != HEX_DIGITS) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return null;
            }
        }
        return Integer.parseInt(value, 16);
    }

    /** {@code #RRGGBB} in Großbuchstaben. */
    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & RGB_MASK);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BubbleColorsConfig)) {
            return false;
        }
        BubbleColorsConfig that = (BubbleColorsConfig) other;
        return userBackground == that.userBackground && userForeground == that.userForeground
                && assistantBackground == that.assistantBackground && assistantForeground == that.assistantForeground;
    }

    @Override
    public int hashCode() {
        int result = userBackground;
        result = 31 * result + userForeground;
        result = 31 * result + assistantBackground;
        return 31 * result + assistantForeground;
    }

    @Override
    public String toString() {
        return "BubbleColorsConfig[user=" + hex(userBackground) + "/" + hex(userForeground) + ", assistant="
                + hex(assistantBackground) + "/" + hex(assistantForeground) + "]";
    }
}
