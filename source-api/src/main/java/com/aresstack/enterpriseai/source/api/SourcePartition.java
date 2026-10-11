package com.aresstack.enterpriseai.source.api;

/**
 * Ein Bereich, in den sich eine Quelle gliedert und den der Benutzer an- und abhaken kann (Confluence: ein Space).
 * {@link #key()} ist quellennativ und nur für den Adapter bedeutsam; {@link #title()} ist der Name für Menschen.
 */
public final class SourcePartition {

    private final String key;
    private final String title;

    public SourcePartition(String key, String title) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        this.key = key.trim();
        this.title = title == null || title.trim().isEmpty() ? this.key : title.trim();
    }

    public String key() {
        return key;
    }

    public String title() {
        return title;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof SourcePartition && key.equals(((SourcePartition) other).key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "SourcePartition[" + key + ", " + title + "]";
    }
}
