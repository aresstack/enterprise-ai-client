package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.SourcePartition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node a {@code knowledge_tree} call stands on (issue #116): the source, the space (Confluence), the pages between
 * the root of the space and the node, and the node itself. At the root of a space the node is the root and there are no
 * ancestors. Immutable; only {@link BrowseKnowledgeHierarchyUseCase} creates it.
 */
public final class KnowledgeTreeAnchor {

    private final KnowledgeSourcePort port;
    private final SourcePartition partition;
    private final NavigationNode root;
    private final NavigationNode node;
    private final List<KnowledgeTreeNode> ancestors;
    private final KnowledgeTreeNode current;

    KnowledgeTreeAnchor(KnowledgeSourcePort port, SourcePartition partition, NavigationNode root,
                        List<NavigationNode> ancestors, NavigationNode node) {
        this.port = port;
        this.partition = partition;
        this.root = root;
        this.node = node;
        List<KnowledgeTreeNode> path = new ArrayList<KnowledgeTreeNode>();
        if (ancestors != null) {
            for (NavigationNode ancestor : ancestors) {
                path.add(KnowledgeTreeNode.of(ancestor));
            }
        }
        this.ancestors = Collections.unmodifiableList(path);
        this.current = KnowledgeTreeNode.of(node);
    }

    KnowledgeSourcePort port() {
        return port;
    }

    /** Return the source node whose children the cursor walks. */
    NavigationNode node() {
        return node;
    }

    public SourceAddress source() {
        return port.sourceId();
    }

    /** Return the key of the space, or the key of the root if the source does not list the space. */
    public String spaceKey() {
        return partition == null ? root.key() : partition.key();
    }

    /** Return the title of the space, or the title of the root if the source does not list the space. */
    public String spaceTitle() {
        return partition == null ? root.title() : partition.title();
    }

    /** Return whether the node is the root of the space itself. */
    public boolean atRoot() {
        return node.isRoot();
    }

    /** Return the pages between root and node from the top down; empty at the root and for top-level pages. */
    public List<KnowledgeTreeNode> ancestors() {
        return ancestors;
    }

    public KnowledgeTreeNode current() {
        return current;
    }
}
