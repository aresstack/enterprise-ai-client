package com.aresstack.enterpriseai.astu.acropolis.chat;

/**
 * Eine vorgeschlagene nächste Nutzernachricht zu einer zitierten Passage („Was bedeutet hier ‚Vorsystem‘?“). Fachlich
 * keine Suchanfrage (anders als die Suchvorschläge des Research-Agenten): Der Nutzer übernimmt sie in den Editor,
 * kann sie ändern und sendet selbst.
 */
public final class QuoteFollowUpSuggestion {

    private final String text;

    public QuoteFollowUpSuggestion(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        this.text = text.trim();
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuoteFollowUpSuggestion && text.equals(((QuoteFollowUpSuggestion) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return "QuoteFollowUpSuggestion[" + text + "]";
    }
}
