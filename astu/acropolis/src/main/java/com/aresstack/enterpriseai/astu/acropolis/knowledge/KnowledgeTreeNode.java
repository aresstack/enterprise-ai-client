package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.source.api.NavigationNode;

/**
 * A node of the page hierarchy as the knowledge tools see it (issue #116): title, page id and bookmark (both empty for
 * the root of a partition) and whether the node has children. Immutable; it hides the source port types from the tools.
 */
public final class KnowledgeTreeNode {

    /** Whether the node has children, as far as the source knows without loading them. */
    public enum Children {
        YES,
        NO,
        UNKNOWN
    }

    private final String key;
    private final String title;
    private final KnowledgeResourceId resource;
    private final String bookmark;
    private final Children children;

    private KnowledgeTreeNode(String key, String title, KnowledgeResourceId resource, Children children) {
        this.key = key;
        this.title = title;
        this.resource = resource;
        this.bookmark = resource == null ? "" : bookmarkOf(resource);
        this.children = children;
    }

    static KnowledgeTreeNode of(NavigationNode node) {
        Children children;
        switch (node.children()) {
            case YES:
                children = Children.YES;
                break;
            case NO:
                children = Children.NO;
                break;
            default:
                children = Children.UNKNOWN;
        }
        return new KnowledgeTreeNode(node.key(), node.title(), node.resource(), children);
    }

    /** Return the bookmark of a resource, or {@code ""} if its scheme has none. */
    static String bookmarkOf(KnowledgeResourceId resource) {
        try {
            return KnowledgeBookmarks.of(resource).toString();
        } catch (IllegalArgumentException noBookmark) {
            return "";
        }
    }

    /** Return the key unique within the source; it binds the cursor of the children. */
    String key() {
        return key;
    }

    public String title() {
        return title;
    }

    /** Return the page, {@code null} for the root of a partition. */
    public KnowledgeResourceId resource() {
        return resource;
    }

    /** Return the bookmark of the page; empty for a root or a scheme without bookmarks. */
    public String bookmark() {
        return bookmark;
    }

    public boolean isRoot() {
        return resource == null;
    }

    public Children children() {
        return children;
    }
}
