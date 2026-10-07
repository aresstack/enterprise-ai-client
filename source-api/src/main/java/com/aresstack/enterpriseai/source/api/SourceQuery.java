package com.aresstack.enterpriseai.source.api;

/**
 * Suchanfrage an eine durchsuchbare Quelle ({@link SearchableKnowledgeSource}). Der Text wird von der
 * Quelle in ihrer eigenen Syntax interpretiert (MediaWiki-Volltextsuche, Confluence-CQL-Textsuche).
 */
public final class SourceQuery {

    public static final int DEFAULT_LIMIT = 20;

    private final String text;
    private final int limit;

    public SourceQuery(String text, int limit) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.text = text;
        this.limit = limit;
    }

    public static SourceQuery of(String text) {
        return new SourceQuery(text, DEFAULT_LIMIT);
    }

    public String text() {
        return text;
    }

    public int limit() {
        return limit;
    }

    @Override
    public String toString() {
        return "SourceQuery{text='" + text + "', limit=" + limit + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceQuery)) {
            return false;
        }
        SourceQuery that = (SourceQuery) o;
        return limit == that.limit && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return 31 * text.hashCode() + limit;
    }
}
