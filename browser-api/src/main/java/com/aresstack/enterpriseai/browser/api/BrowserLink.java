package com.aresstack.enterpriseai.browser.api;

/**
 * A link of one page snapshot: an id valid only for that snapshot, the visible text, the absolute address and the
 * optional title / aria-label of the anchor.
 */
public final class BrowserLink {

    private final String id;
    private final String text;
    private final String url;
    private final String label;

    public BrowserLink(String id, String text, String url, String label) {
        if (id == null || id.isEmpty() || url == null || url.isEmpty()) {
            throw new IllegalArgumentException("id and url must not be empty");
        }
        this.id = id;
        this.text = text == null ? "" : text;
        this.url = url;
        this.label = label == null ? "" : label;
    }

    public String id() {
        return id;
    }

    public String text() {
        return text;
    }

    public String url() {
        return url;
    }

    /** Title or aria-label of the anchor, empty when it has none or it repeats the text. */
    public String label() {
        return label;
    }
}
