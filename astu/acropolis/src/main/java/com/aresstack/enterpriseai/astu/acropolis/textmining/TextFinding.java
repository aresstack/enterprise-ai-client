package com.aresstack.enterpriseai.astu.acropolis.textmining;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One structured finding of a text extractor, ready to be stored: its type (e.g. {@code ACRONYM_DEFINITION}), an
 * optional role of the document for it ({@code DEFINES}, {@code MENTIONS}), small machine-readable fields and the place
 * it was found (span in the canonical text plus a short quote). Never a copy of the document.
 */
public final class TextFinding {

    /** Longest quote kept with a finding. */
    public static final int MAX_QUOTE_LENGTH = 300;

    private final String type;
    private final String role;
    private final Map<String, Object> fields;
    private final TextSpan span;
    private final String quote;

    /**
     * @param fields values: strings, numbers, booleans and lists or maps of them, in the order they are written
     * @param span   where it was found, or {@code null}
     * @param quote  short evidence from the text, or {@code null}; cut to {@link #MAX_QUOTE_LENGTH}
     */
    public TextFinding(String type, String role, Map<String, Object> fields, TextSpan span, String quote) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        this.type = type;
        this.role = role == null ? "" : role;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                fields == null ? Collections.<String, Object>emptyMap() : fields));
        this.span = span;
        String text = quote == null ? "" : quote.replaceAll("[\\s\\u00A0]+", " ").trim();
        this.quote = text.length() > MAX_QUOTE_LENGTH ? text.substring(0, MAX_QUOTE_LENGTH - 1) + "…" : text;
    }

    public String type() {
        return type;
    }

    /** The role of the document for the finding, or {@code ""}. */
    public String role() {
        return role;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    /** Where it was found, or {@code null}. */
    public TextSpan span() {
        return span;
    }

    /** Short evidence, or {@code ""}. */
    public String quote() {
        return quote;
    }

    @Override
    public String toString() {
        return "TextFinding[" + type + ", " + role + ", " + fields + ", " + span + "]";
    }
}
