package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

/** Ergebnis einer Bildbeschreibung: der Text oder ein Hinweis, warum es keinen gibt. */
public final class ReaderImageDescription {

    private final String text;
    private final String problem;

    private ReaderImageDescription(String text, String problem) {
        this.text = text;
        this.problem = problem;
    }

    public static ReaderImageDescription of(String text) {
        return new ReaderImageDescription(text == null ? "" : text, null);
    }

    public static ReaderImageDescription unavailable(String problem) {
        return new ReaderImageDescription("", problem == null ? "Keine Beschreibung." : problem);
    }

    public boolean available() {
        return problem == null;
    }

    public String text() {
        return text;
    }

    /** Der Hinweis, wenn {@link #available()} {@code false} ist. */
    public String problem() {
        return problem;
    }
}
