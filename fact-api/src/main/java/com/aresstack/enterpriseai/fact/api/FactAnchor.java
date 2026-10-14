package com.aresstack.enterpriseai.fact.api;

/**
 * Optional place of a fact inside its resource, strictly separate from the bookmark (the resource id): section,
 * structure path, line or character range, a short quote and a fingerprint of the quoted text. Every part is
 * optional, at least one must be set. An anchor is only meaningful while the fact is
 * {@link FactFreshness#CURRENT}.
 */
public final class FactAnchor {

    /** Longest section, structure path or fingerprint; matches the column width in every store. */
    public static final int MAX_TEXT_LENGTH = 1024;
    /** Longest quote: a short evidence snippet, never a copy of the document. */
    public static final int MAX_QUOTE_LENGTH = 1000;

    private final String section;
    private final String structurePath;
    private final Integer lineStart;
    private final Integer lineEnd;
    private final Integer charStart;
    private final Integer charEnd;
    private final String quote;
    private final String fingerprint;

    private FactAnchor(Builder builder) {
        this.section = text("section", builder.section, MAX_TEXT_LENGTH);
        this.structurePath = text("structure path", builder.structurePath, MAX_TEXT_LENGTH);
        this.quote = text("quote", builder.quote, MAX_QUOTE_LENGTH);
        this.fingerprint = text("fingerprint", builder.fingerprint, MAX_TEXT_LENGTH);
        range("line", builder.lineStart, builder.lineEnd);
        range("character", builder.charStart, builder.charEnd);
        this.lineStart = builder.lineStart;
        this.lineEnd = builder.lineEnd;
        this.charStart = builder.charStart;
        this.charEnd = builder.charEnd;
        if (section.isEmpty() && structurePath.isEmpty() && quote.isEmpty() && fingerprint.isEmpty()
                && lineStart == null && charStart == null) {
            throw new IllegalArgumentException("anchor needs at least one part");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Heading or section name, or {@code ""}. */
    public String section() {
        return section;
    }

    /** Path in the document structure (e.g. {@code DATA DIVISION/WORKING-STORAGE}), or {@code ""}. */
    public String structurePath() {
        return structurePath;
    }

    /** First line (1-based), or {@code null}. */
    public Integer lineStart() {
        return lineStart;
    }

    /** Last line (inclusive), or {@code null}. */
    public Integer lineEnd() {
        return lineEnd;
    }

    /** First character offset (0-based), or {@code null}. */
    public Integer charStart() {
        return charStart;
    }

    /** Character offset after the range, or {@code null}. */
    public Integer charEnd() {
        return charEnd;
    }

    /** Short quote from the document, or {@code ""}. */
    public String quote() {
        return quote;
    }

    /** Fingerprint of the quoted text (to find it again after edits), or {@code ""}. */
    public String fingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FactAnchor)) {
            return false;
        }
        FactAnchor that = (FactAnchor) other;
        return section.equals(that.section) && structurePath.equals(that.structurePath)
                && same(lineStart, that.lineStart) && same(lineEnd, that.lineEnd)
                && same(charStart, that.charStart) && same(charEnd, that.charEnd)
                && quote.equals(that.quote) && fingerprint.equals(that.fingerprint);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * section.hashCode() + structurePath.hashCode()) + quote.hashCode();
    }

    @Override
    public String toString() {
        return "FactAnchor[section=" + section + ", path=" + structurePath + ", lines=" + lineStart + "-" + lineEnd
                + ", chars=" + charStart + "-" + charEnd + "]";
    }

    private static boolean same(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private static String text(String name, String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw new IllegalArgumentException("anchor " + name + " longer than " + max);
        }
        return text;
    }

    private static void range(String name, Integer start, Integer end) {
        if (start == null && end != null) {
            throw new IllegalArgumentException("anchor " + name + " range needs a start");
        }
        if (start != null && start < 0 || end != null && end < start) {
            throw new IllegalArgumentException("invalid anchor " + name + " range " + start + "-" + end);
        }
    }

    public static final class Builder {

        private String section;
        private String structurePath;
        private Integer lineStart;
        private Integer lineEnd;
        private Integer charStart;
        private Integer charEnd;
        private String quote;
        private String fingerprint;

        private Builder() {
        }

        public Builder section(String value) {
            this.section = value;
            return this;
        }

        public Builder structurePath(String value) {
            this.structurePath = value;
            return this;
        }

        /** @param end last line, or {@code null} for an open range */
        public Builder lines(Integer start, Integer end) {
            this.lineStart = start;
            this.lineEnd = end;
            return this;
        }

        /** @param end offset after the range, or {@code null} for an open range */
        public Builder characters(Integer start, Integer end) {
            this.charStart = start;
            this.charEnd = end;
            return this;
        }

        public Builder quote(String value) {
            this.quote = value;
            return this;
        }

        public Builder fingerprint(String value) {
            this.fingerprint = value;
            return this;
        }

        public FactAnchor build() {
            return new FactAnchor(this);
        }
    }
}
