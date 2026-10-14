package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.SourcePartition;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Use case "browse the hierarchy of a knowledge source" behind the tool {@code knowledge_tree} (issue #116): which
 * sources have a page tree and which partitions (spaces) they offer, where a page or a space sits and
 * the direct children of a node, section by section behind an opaque cursor. It reuses {@link PageNavigationUseCase},
 * the navigation of the compass view, and knows sources only through their ports. All calls block.
 *
 * <p>A cursor is bound to source, space, node and the position within the batches of the source, including an offset
 * inside a batch, so a section ends exactly after {@code limit} children whatever batch size the source chooses. The
 * tools see only {@link KnowledgeTreeNode} and {@link KnowledgeSpace}, never the port types.
 */
public final class BrowseKnowledgeHierarchyUseCase {

    private static final String CURSOR_PREFIX = "kt1.";
    private static final String CURSOR_SEPARATOR = "\n";
    private static final int CURSOR_FIELDS = 5;
    private static final int BYTE_MASK = 0xFF;

    private final Supplier<KnowledgeSourceCatalog> catalog;
    private final PageNavigationUseCase navigation;

    /**
     * @param catalog    the configured sources, read per call
     * @param navigation the navigation shared with the compass view
     */
    public BrowseKnowledgeHierarchyUseCase(Supplier<KnowledgeSourceCatalog> catalog, PageNavigationUseCase navigation) {
        if (catalog == null || navigation == null) {
            throw new IllegalArgumentException("catalog and navigation must not be null");
        }
        this.catalog = catalog;
        this.navigation = navigation;
    }

    /**
     * Return the sources with a page tree and their partitions in configuration order; sources without partitions are
     * left out, a source failing to list them is kept with its {@link NavigableKnowledgeSource#failure()}.
     */
    public List<NavigableKnowledgeSource> navigableSources() {
        List<NavigableKnowledgeSource> found = new ArrayList<NavigableKnowledgeSource>();
        for (KnowledgeSourcePort port : catalog.get().ports()) {
            if (!navigation.supports(port)) {
                continue;
            }
            try {
                List<SourcePartition> partitions = port.partitions();
                if (!partitions.isEmpty()) {
                    found.add(new NavigableKnowledgeSource(port.sourceId(), partitions, null));
                }
            } catch (KnowledgeSourceException e) {
                found.add(new NavigableKnowledgeSource(port.sourceId(), null, e.kind()));
            }
        }
        return found;
    }

    /**
     * Stand on the root of a space.
     *
     * @return {@code null} if the source is not navigable or the space has neither start point nor home page
     */
    public KnowledgeTreeAnchor space(SourceAddress source, KnowledgeSpace space) throws KnowledgeSourceException {
        if (source == null || space == null) {
            throw new IllegalArgumentException("source and space must not be null");
        }
        SourcePartition partition = space.partition();
        KnowledgeSourcePort port = navigablePort(source);
        if (port == null) {
            return null;
        }
        NavigationNode root;
        if (!partition.startPoint().isEmpty()) {
            root = NavigationNode.root(partition.startPoint(), partition.title(), partition.startPoint(),
                    NavigationNode.Children.UNKNOWN);
        } else if (partition.homeResource() != null) {
            PagePosition home = navigation.position(port, partition.homeResource());
            if (home == null) {
                return null;
            }
            root = home.root();
        } else {
            return null;
        }
        return new KnowledgeTreeAnchor(port, partition, root, null, root);
    }

    /**
     * Stand on a page; the source is the first navigable one (only {@code source}, if given) that knows the page, and
     * the partition is found among the partitions of that source.
     *
     * @return {@code null} if no navigable source places the page in a hierarchy
     * @throws KnowledgeSourceException {@code NOT_FOUND} if no source knows the page, otherwise the first other failure
     *                                  ({@code ACCESS_DENIED}, {@code UNAVAILABLE} ...)
     */
    public KnowledgeTreeAnchor page(KnowledgeResourceId page, SourceAddress source) throws KnowledgeSourceException {
        if (page == null) {
            throw new IllegalArgumentException("page must not be null");
        }
        KnowledgeSourceException missing = null;
        for (KnowledgeSourcePort port : catalog.get().ports()) {
            if (!navigation.supports(port) || source != null && !source.equals(port.sourceId())) {
                continue;
            }
            PagePosition position;
            try {
                position = navigation.position(port, page);
            } catch (KnowledgeSourceException e) {
                if (e.kind() != KnowledgeSourceException.Kind.NOT_FOUND) {
                    throw e;
                }
                // Ask the other sources too: one source may not know ids of another of the same kind.
                missing = missing == null ? e : missing;
                continue;
            }
            if (position != null) {
                return new KnowledgeTreeAnchor(port, partitionOf(port, position.root()), position.root(),
                        position.ancestors(), position.page());
            }
        }
        if (missing != null) {
            throw missing;
        }
        return null;
    }

    /**
     * Load the direct children of the anchor from {@code cursor} on: at most {@code limit} children ({@code 0} = all),
     * and only as many as {@code room} accepts; the first child it refuses ends the section with
     * {@link KnowledgeTreeChildren#truncated()} and a cursor on exactly that child.
     *
     * @param cursor {@code null}, empty or a cursor of a previous section of the same anchor
     * @throws IllegalArgumentException if the cursor is malformed or belongs to another source, partition or node
     */
    public KnowledgeTreeChildren children(KnowledgeTreeAnchor anchor, String cursor, int limit,
                                          Predicate<KnowledgeTreeNode> room) throws KnowledgeSourceException {
        if (anchor == null || room == null || limit < 0) {
            throw new IllegalArgumentException("anchor and room must not be null, limit must be >= 0");
        }
        String batch = "";
        int offset = 0;
        if (cursor != null && !cursor.trim().isEmpty()) {
            String[] fields = decode(cursor.trim());
            if (!fields[0].equals(anchor.source().value()) || !fields[1].equals(anchor.spaceKey())
                    || !fields[2].equals(anchor.node().key())) {
                throw new IllegalArgumentException("cursor belongs to another node");
            }
            batch = fields[3];
            offset = Integer.parseInt(fields[4]);
        }
        List<KnowledgeTreeNode> delivered = new ArrayList<KnowledgeTreeNode>();
        while (true) {
            NavigationChildren loaded = navigation.children(anchor.port(), anchor.node(), batch);
            List<NavigationNode> nodes = loaded.nodes();
            for (int i = offset; i < nodes.size(); i++) {
                if (limit > 0 && delivered.size() == limit) {
                    return new KnowledgeTreeChildren(delivered, encode(anchor, batch, i), false);
                }
                KnowledgeTreeNode child = KnowledgeTreeNode.of(nodes.get(i));
                if (!room.test(child)) {
                    return new KnowledgeTreeChildren(delivered, encode(anchor, batch, i), true);
                }
                delivered.add(child);
            }
            if (loaded.complete() || loaded.nextCursor().equals(batch)) {
                return new KnowledgeTreeChildren(delivered, "", false);
            }
            batch = loaded.nextCursor();
            offset = 0;
            if (limit > 0 && delivered.size() == limit) {
                return new KnowledgeTreeChildren(delivered, encode(anchor, batch, 0), false);
            }
        }
    }

    private KnowledgeSourcePort navigablePort(SourceAddress source) {
        KnowledgeSourceRegistration registration = catalog.get().find(source);
        return registration == null || !navigation.supports(registration.port()) ? null : registration.port();
    }

    /** Match the root of a page against the partitions of its source by start point or key; {@code null} if none. */
    private static SourcePartition partitionOf(KnowledgeSourcePort port, NavigationNode root) {
        List<SourcePartition> partitions;
        try {
            partitions = port.partitions();
        } catch (KnowledgeSourceException e) {
            return null;
        }
        for (SourcePartition partition : partitions) {
            String start = partition.startPoint();
            if (!start.isEmpty() && (start.equals(root.startPoint()) || start.equals(root.key()))) {
                return partition;
            }
        }
        return null;
    }

    private static String encode(KnowledgeTreeAnchor anchor, String batch, int offset) {
        String plain = anchor.source().value() + CURSOR_SEPARATOR + anchor.spaceKey() + CURSOR_SEPARATOR
                + anchor.node().key() + CURSOR_SEPARATOR + batch + CURSOR_SEPARATOR + offset;
        byte[] bytes = new byte[plain.length() * 2];
        for (int i = 0; i < plain.length(); i++) {
            bytes[2 * i] = (byte) (plain.charAt(i) >>> Byte.SIZE);
            bytes[2 * i + 1] = (byte) plain.charAt(i);
        }
        return CURSOR_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String[] decode(String cursor) {
        if (!cursor.startsWith(CURSOR_PREFIX)) {
            throw new IllegalArgumentException("not a knowledge_tree cursor");
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(cursor.substring(CURSOR_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed knowledge_tree cursor");
        }
        if (bytes.length % 2 != 0) {
            throw new IllegalArgumentException("malformed knowledge_tree cursor");
        }
        char[] chars = new char[bytes.length / 2];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) ((bytes[2 * i] & BYTE_MASK) << Byte.SIZE | bytes[2 * i + 1] & BYTE_MASK);
        }
        String plain = new String(chars);
        String[] fields = plain.split(CURSOR_SEPARATOR, -1);
        if (fields.length != CURSOR_FIELDS || !fields[4].matches("\\d{1,9}")) {
            throw new IllegalArgumentException("malformed knowledge_tree cursor");
        }
        return fields;
    }
}
