package com.aresstack.enterpriseai.browser.api.engine;

/** One anchor of a rendered page as the engine read it: visible text, resolved href, title and aria-label. */
public final class RawLink {

    private final String text;
    private final String href;
    private final String title;
    private final String ariaLabel;

    public RawLink(String text, String href, String title, String ariaLabel) {
        this.text = text == null ? "" : text;
        this.href = href == null ? "" : href;
        this.title = title == null ? "" : title;
        this.ariaLabel = ariaLabel == null ? "" : ariaLabel;
    }

    public String text() {
        return text;
    }

    public String href() {
        return href;
    }

    public String title() {
        return title;
    }

    public String ariaLabel() {
        return ariaLabel;
    }
}
