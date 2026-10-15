package com.aresstack.enterpriseai.research.runtime.knowledge;

/**
 * One hit of {@code knowledge_search}. {@code id} is the opaque resource id for {@code knowledge_read}, {@code url}
 * the real address for opening and citing (empty when the source has none), {@code passage} the passage id and
 * {@code source} the knowledge source: all metadata, never part of the snippet.
 */
public final class KnowledgeHit {

    public final String id;
    public final String title;
    public final String snippet;
    public final String url;
    public final String passage;
    public final String source;

    public KnowledgeHit(String id, String title, String snippet, String url, String passage, String source) {
        this.id = nonNull(id);
        this.title = nonNull(title);
        this.snippet = nonNull(snippet);
        this.url = nonNull(url);
        this.passage = nonNull(passage);
        this.source = nonNull(source);
    }

    /** Title, or the id when the hit has no title. */
    public String label() {
        return title.isEmpty() ? id : title;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return label() + " (" + id + ")";
    }
}
