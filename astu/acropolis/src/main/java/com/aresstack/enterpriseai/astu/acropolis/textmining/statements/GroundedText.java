package com.aresstack.enterpriseai.astu.acropolis.textmining.statements;

import com.aresstack.enterpriseai.astu.acropolis.textmining.TextSpan;

/** Words of the document with their place: the span in the canonical text and the text found there. */
public final class GroundedText {

    private final TextSpan span;
    private final String text;

    public GroundedText(TextSpan span, String text) {
        if (span == null || text == null) {
            throw new IllegalArgumentException("span and text must not be null");
        }
        this.span = span;
        this.text = text;
    }

    public TextSpan span() {
        return span;
    }

    public String text() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof GroundedText && span.equals(((GroundedText) other).span)
                && text.equals(((GroundedText) other).text);
    }

    @Override
    public int hashCode() {
        return span.hashCode();
    }

    @Override
    public String toString() {
        return text + "@" + span;
    }
}
