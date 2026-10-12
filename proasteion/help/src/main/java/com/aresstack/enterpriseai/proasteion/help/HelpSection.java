package com.aresstack.enterpriseai.proasteion.help;

/**
 * One {@code ##} section of a help topic, including its {@code ###} subsections. The anchor is the slug of the
 * heading ({@link HelpMarkdown#slug}); the introduction before the first {@code ##} heading has the empty anchor.
 */
public final class HelpSection {

    private final String anchor;
    private final String heading;
    private final String markdown;

    HelpSection(String anchor, String heading, String markdown) {
        this.anchor = anchor;
        this.heading = heading;
        this.markdown = markdown;
    }

    /** Slug of the heading, empty for the introduction. */
    public String anchor() {
        return anchor;
    }

    /** Heading text without {@code ##}, empty for the introduction. */
    public String heading() {
        return heading;
    }

    /** Markdown of the section body without its own heading line. */
    public String markdown() {
        return markdown;
    }

    public boolean isIntroduction() {
        return anchor.isEmpty();
    }

    @Override
    public String toString() {
        return isIntroduction() ? "(Einleitung)" : heading;
    }
}
