package com.aresstack.enterpriseai.astu.acropolis.chat;

/**
 * Eine zitierte Passage mit begrenztem Umfeld aus ihrer Assistentenantwort, nur für die Nebenanfrage der
 * Anschlussfragen ({@link QuoteFollowUps}): Markiert der Nutzer nur „Vorsystem“, versteht das Modell erst über das
 * Umfeld, wovon die Rede ist. Das Umfeld erscheint nie im Zitat-Chip und geht nicht mit der Nachricht an das Modell.
 */
public final class QuoteContext {

    /** Zeichen vor und nach der Passage, die das Umfeld höchstens umfasst (Standard). */
    public static final int DEFAULT_SURROUNDING_CHARS = 600;

    private final String selectedText;
    private final String surroundingText;

    public QuoteContext(String selectedText, String surroundingText) {
        if (selectedText == null || selectedText.trim().isEmpty()) {
            throw new IllegalArgumentException("selectedText must not be blank");
        }
        this.selectedText = selectedText;
        this.surroundingText = surroundingText == null ? "" : surroundingText;
    }

    /**
     * Die Passage mit ihrem Umfeld aus {@code answer}: um die bekannte Position ({@code sourceStart}..{@code sourceEnd},
     * sonst die erste Fundstelle der Passage, sonst der Anfang der Antwort) höchstens {@code surroundingChars} Zeichen
     * davor und danach.
     */
    public static QuoteContext around(String answer, String selectedText, int sourceStart, int sourceEnd,
                                      int surroundingChars) {
        String text = answer == null ? "" : answer;
        int start = sourceStart;
        int end = sourceEnd;
        if (start < 0 || end <= start || end > text.length()) {
            int found = selectedText == null ? -1 : text.indexOf(selectedText);
            start = Math.max(0, found);
            end = found < 0 ? 0 : found + selectedText.length();
        }
        int window = Math.max(0, surroundingChars);
        int from = Math.max(0, start - window);
        int to = Math.min(text.length(), end + window);
        String surrounding = text.substring(from, to).trim();
        if (from > 0) {
            surrounding = "… " + surrounding;
        }
        if (to < text.length()) {
            surrounding = surrounding + " …";
        }
        return new QuoteContext(selectedText, surrounding);
    }

    public String selectedText() {
        return selectedText;
    }

    /** Ausschnitt der Antwort um die Passage; leer, wenn keiner. */
    public String surroundingText() {
        return surroundingText;
    }

    @Override
    public String toString() {
        // Bewusst ohne Text: Verlaufsinhalte gehören nicht in Logs.
        return "QuoteContext[selected=" + selectedText.length() + ", surrounding=" + surroundingText.length() + "]";
    }
}
