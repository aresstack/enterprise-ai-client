package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One section of the direct children of a {@link KnowledgeTreeAnchor} (issue #116): the children, an opaque cursor on
 * the next child not yet delivered (empty at the end) and whether the response size ended the section before the
 * {@code limit}. Immutable.
 */
public final class KnowledgeTreeChildren {

    private final List<KnowledgeTreeNode> children;
    private final String nextCursor;
    private final boolean truncated;

    KnowledgeTreeChildren(List<KnowledgeTreeNode> children, String nextCursor, boolean truncated) {
        this.children = Collections.unmodifiableList(new ArrayList<KnowledgeTreeNode>(children));
        this.nextCursor = nextCursor == null ? "" : nextCursor;
        this.truncated = truncated;
    }

    public List<KnowledgeTreeNode> children() {
        return children;
    }

    /** Return the cursor of the next section; empty when all direct children are delivered. */
    public String nextCursor() {
        return nextCursor;
    }

    public boolean complete() {
        return nextCursor.isEmpty();
    }

    /** Return whether the response ran out of room before the {@code limit}. */
    public boolean truncated() {
        return truncated;
    }
}
