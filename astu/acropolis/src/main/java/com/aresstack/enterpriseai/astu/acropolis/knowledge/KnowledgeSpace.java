package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.SourcePartition;

/**
 * A partition of a navigable source (Confluence: a space) as the knowledge tools see it (issue #116): its stable key,
 * its title and its home page with id and bookmark, if it has one. Immutable.
 */
public final class KnowledgeSpace {

    private final SourcePartition partition;

    KnowledgeSpace(SourcePartition partition) {
        this.partition = partition;
    }

    SourcePartition partition() {
        return partition;
    }

    /** Return the stable key ({@link SourcePartition#key()}), never a position in a list. */
    public String key() {
        return partition.key();
    }

    public String title() {
        return partition.title();
    }

    /** Return the home page, or {@code null}. */
    public KnowledgeResourceId homeResource() {
        return partition.homeResource();
    }

    /** Return the bookmark of the home page; empty without home page or bookmark. */
    public String homeBookmark() {
        return partition.homeResource() == null ? "" : KnowledgeTreeNode.bookmarkOf(partition.homeResource());
    }
}
