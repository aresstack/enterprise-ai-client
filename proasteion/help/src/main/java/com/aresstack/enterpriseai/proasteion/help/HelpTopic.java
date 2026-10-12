package com.aresstack.enterpriseai.proasteion.help;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One help chapter ({@code docs/help/<area>/<topic>.md}) with its front matter and sections. Immutable.
 */
public final class HelpTopic {

    /** Prefix of topic ids in tool results and links ({@code help:chat.attachments}). */
    public static final String ID_PREFIX = "help:";

    private final String id;
    private final String title;
    private final List<String> keywords;
    private final String since;
    private final String resource;
    private final List<HelpSection> sections;

    HelpTopic(String id, String title, List<String> keywords, String since, String resource,
              List<HelpSection> sections) {
        this.id = id;
        this.title = title;
        this.keywords = Collections.unmodifiableList(new ArrayList<String>(keywords));
        this.since = since;
        this.resource = resource;
        this.sections = Collections.unmodifiableList(new ArrayList<HelpSection>(sections));
    }

    /** Id from the front matter, e.g. {@code chat.attachments}. */
    public String id() {
        return id;
    }

    /** Id as the tools print it, e.g. {@code help:chat.attachments}. */
    public String qualifiedId() {
        return ID_PREFIX + id;
    }

    public String title() {
        return title;
    }

    public List<String> keywords() {
        return keywords;
    }

    /** Version the chapter describes the feature since, or empty. */
    public String since() {
        return since == null ? "" : since;
    }

    /** Resource name relative to the help root, e.g. {@code chat/attachments.md}. */
    public String resource() {
        return resource;
    }

    /** Area of the topic: the part of the id before the first dot. */
    public String area() {
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }

    /** Introduction first (if any), then the {@code ##} sections in document order. */
    public List<HelpSection> sections() {
        return sections;
    }

    /** Section with the given anchor, or {@code null}. */
    public HelpSection section(String anchor) {
        for (HelpSection section : sections) {
            if (section.anchor().equals(anchor)) {
                return section;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return qualifiedId() + " (" + title + ")";
    }
}
