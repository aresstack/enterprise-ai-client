package com.aresstack.enterpriseai.astu.acropolis.chat;

import java.util.List;

/**
 * Eine Passage aus einer früheren Assistentenantwort, auf die sich die aktuelle Nutzernachricht ausdrücklich bezieht
 * (Zitieren im Chat). Sie geht nur für diesen einen Turn als zusätzlicher Kontext an das Modell ({@link #context});
 * die sichtbare Nutzernachricht und der Verlauf für spätere Turns bleiben unverändert. Keine externe Wissensquelle.
 */
public final class QuotedPassage {

    private final String text;

    public QuotedPassage(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        this.text = text;
    }

    /** Die zitierte Passage, wie der Nutzer sie markiert hat. */
    public String text() {
        return text;
    }

    /** Hinweis für das Modell auf die zitierten Passagen dieses Turns oder {@code null} ohne Zitate. */
    public static String context(List<QuotedPassage> passages) {
        if (passages == null || passages.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder();
        if (passages.size() == 1) {
            context.append("Der Nutzer bezieht sich auf folgende Passage aus einer früheren Assistentenantwort:\n\n")
                    .append(quoted(passages.get(0).text()))
                    .append("\n\nDie aktuelle Nutzernachricht bezieht sich insbesondere auf diese Passage.");
            return context.toString();
        }
        context.append("Der Nutzer bezieht sich auf folgende Passagen aus früheren Assistentenantworten:");
        for (int index = 0; index < passages.size(); index++) {
            context.append("\n\n").append(index + 1).append(". ").append(quoted(passages.get(index).text()));
        }
        context.append("\n\nDie aktuelle Nutzernachricht bezieht sich insbesondere auf diese Passagen.");
        return context.toString();
    }

    private static String quoted(String text) {
        return "„" + text.trim() + "“";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuotedPassage && text.equals(((QuotedPassage) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        // Bewusst ohne Text: Verlaufsinhalte gehören nicht in Logs.
        return "QuotedPassage[length=" + text.length() + "]";
    }
}
