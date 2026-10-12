package com.aresstack.enterpriseai.proasteion.help;

/** A help chapter breaks the format (front matter, title, sections, unique ids). */
public final class HelpFormatException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String resource;

    public HelpFormatException(String resource, String message) {
        super(resource + ": " + message);
        this.resource = resource;
    }

    /** Resource name relative to the help root, e.g. {@code chat/attachments.md}. */
    public String resource() {
        return resource;
    }
}
