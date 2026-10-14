package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeHierarchyUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeBookmarkIds;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSpace;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeTreeAnchor;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeTreeChildren;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeTreeNode;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.NavigableKnowledgeSource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * {@code knowledge_tree(source?, space?, id?, bookmark?, direction?, limit?, cursor?)} (issue #116): the page hierarchy
 * around a partition (Confluence: space) or a page for the model. Without a target it lists the navigable sources and
 * their spaces; with a space or a page it returns the parent path up to the space, the node itself and its direct
 * children, at most {@code limit} of them ({@code 0} = all) behind an opaque {@code nextCursor}. It never returns page
 * text; that stays with {@code knowledge_read}. Validation and rendering only; the navigation is
 * {@link BrowseKnowledgeHierarchyUseCase}.
 */
final class KnowledgeTreeTool implements McpToolHandler {

    static final String NAME = "knowledge_tree";
    static final String PARAM_SOURCE = "source";
    static final String PARAM_SPACE = "space";
    static final String PARAM_ID = "id";
    static final String PARAM_BOOKMARK = "bookmark";
    static final String PARAM_DIRECTION = "direction";
    static final String PARAM_LIMIT = "limit";
    static final String PARAM_CURSOR = "cursor";
    static final String PARENTS = "parents";
    static final String CHILDREN = "children";
    static final String BOTH = "both";

    private static final List<String> DIRECTIONS = Arrays.asList(PARENTS, CHILDREN, BOTH);
    private static final String EXAMPLE_PAGE = "bookmark=\"<bookmark from knowledge_search>\"";
    private static final String EXAMPLE_SPACE = "source=\"<source from knowledge_tree()>\", space=\"DEV\"";
    private static final int FIELD_CHARS = 200;
    /** Room for the cursor lines: two bytes per char in base64 grow by 8/3, a source cursor is a short path. */
    private static final int SOURCE_CURSOR_CHARS = 256;
    private static final int FOOTER_LINE_CHARS = 160;
    private static final int BASE64_GROWTH_NUMERATOR = 8;
    private static final int BASE64_GROWTH_DENOMINATOR = 3;

    private final BrowseKnowledgeHierarchyUseCase hierarchy;
    private final KnowledgeToolSettings settings;
    private final IntSupplier defaultLimit;

    /** @param defaultLimit direct children without {@code limit}, read per call ({@code 0} = all) */
    KnowledgeTreeTool(BrowseKnowledgeHierarchyUseCase hierarchy, KnowledgeToolSettings settings,
                      IntSupplier defaultLimit) {
        this.hierarchy = hierarchy;
        this.settings = settings;
        this.defaultLimit = defaultLimit;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Browse the hierarchy around one knowledge space or page. Call without a target to list the valid "
                        + "navigable sources and spaces; reuse their exact source and space values, never guess a "
                        + "space from its title. For a page, pass an exact id or bookmark returned by the knowledge "
                        + "tools; the space is found for you. direction is optional and defaults to both, returning "
                        + "the complete parent path and the direct children. Use parents or children to restrict the "
                        + "response. limit controls the number of direct children; default "
                        + defaultLimit.getAsInt() + ", and 0 means no logical item limit. Follow nextCursor when "
                        + "present by repeating the call with cursor. This tool never returns page contents; read "
                        + "them separately with knowledge_read. Example: knowledge_tree() lists the spaces; "
                        + EXAMPLE_SPACE + " shows the top-level pages of a space; " + EXAMPLE_PAGE
                        + " shows parents and children of a page; " + EXAMPLE_PAGE
                        + ", direction=\"children\", limit=0 lists all direct children.",
                this,
                McpToolParameter.string(PARAM_SOURCE, false, "Optional: a source value listed by knowledge_tree() "
                        + "(or the \"source:\" of a knowledge_search hit). Without a target it lists only the spaces "
                        + "of this source."),
                McpToolParameter.string(PARAM_SPACE, false, "Optional: exact space key listed by knowledge_tree(), "
                        + "e.g. space=\"DEV\". Alone it starts at the root of the space; with id or bookmark it must "
                        + "be the space of that page."),
                McpToolParameter.string(PARAM_ID, false, "Optional: exact page id returned by the knowledge tools. "
                        + "Not together with bookmark."),
                McpToolParameter.string(PARAM_BOOKMARK, false, "Optional: exact page bookmark returned by the "
                        + "knowledge tools. Not together with id. Example: " + EXAMPLE_PAGE),
                McpToolParameter.enumeration(PARAM_DIRECTION, false, "Optional, default both: parents = path up to "
                        + "the space, children = direct children only, both = parents, node and direct children.",
                        DIRECTIONS),
                McpToolParameter.integer(PARAM_LIMIT, false, "Optional: maximum number of direct children, default "
                        + defaultLimit.getAsInt() + "; 0 = all direct children (follow nextCursor if the response "
                        + "is truncated)."),
                McpToolParameter.string(PARAM_CURSOR, false, "Optional: the nextCursor of the previous call, "
                        + "unchanged, with the same target."));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String rawSource = trimmed(call.getString(PARAM_SOURCE));
        String space = trimmed(call.getString(PARAM_SPACE));
        String rawId = trimmed(call.getString(PARAM_ID));
        String rawBookmark = trimmed(call.getString(PARAM_BOOKMARK));
        String rawDirection = trimmed(call.getString(PARAM_DIRECTION));
        String cursor = trimmed(call.getString(PARAM_CURSOR));
        if (!rawId.isEmpty() && !rawBookmark.isEmpty()) {
            return McpToolResult.error("Pass either id or bookmark, not both.");
        }
        String direction = rawDirection.isEmpty() ? BOTH : rawDirection.toLowerCase(Locale.ROOT);
        if (!DIRECTIONS.contains(direction)) {
            return McpToolResult.error("Invalid direction '" + ToolText.oneLine(rawDirection, FIELD_CHARS)
                    + "'. Valid values: parents, children, both (default both).");
        }
        long limit = call.getInteger(PARAM_LIMIT, defaultLimit.getAsInt());
        if (limit < 0 || limit > Integer.MAX_VALUE) {
            return McpToolResult.error("Invalid limit " + limit + ": use a positive number of direct children, "
                    + "or 0 for all of them.");
        }
        if (!cursor.isEmpty() && PARENTS.equals(direction)) {
            return McpToolResult.error("A cursor continues the children: use direction children or both.");
        }
        List<NavigableKnowledgeSource> navigable = hierarchy.navigableSources();
        NavigableKnowledgeSource source = null;
        if (!rawSource.isEmpty()) {
            source = find(navigable, rawSource);
            if (source == null) {
                return McpToolResult.error("Unknown source: " + ToolText.oneLine(rawSource, FIELD_CHARS) + "\n\n"
                        + validSources(navigable));
            }
        }
        boolean page = !rawId.isEmpty() || !rawBookmark.isEmpty();
        if (!page && space.isEmpty()) {
            if (!cursor.isEmpty()) {
                return McpToolResult.error("A cursor needs the same space, id or bookmark as the call it came from.");
            }
            return McpToolResult.ok(ToolText.truncate(discovery(navigable, source), settings.maxResponseChars()));
        }
        KnowledgeTreeAnchor anchor;
        try {
            anchor = page ? pageAnchor(rawId, rawBookmark, source) : null;
            if (page && anchor == null) {
                return McpToolResult.error("No navigable knowledge source places '"
                        + ToolText.oneLine(rawId.isEmpty() ? rawBookmark : rawId, FIELD_CHARS) + "' in a page tree"
                        + (source == null ? "" : " of source " + source.source().value())
                        + ". Only pages of sources listed by knowledge_tree() have a hierarchy.");
            }
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error(invalid.getMessage());
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error(describe(e.kind(), rawId.isEmpty() ? rawBookmark : rawId));
        } catch (RuntimeException e) {
            return McpToolResult.error("Locating the page failed. Try again later.");
        }
        if (anchor != null) {
            if (!space.isEmpty() && !space.equals(anchor.spaceKey())) {
                return McpToolResult.error("The page belongs to space '" + anchor.spaceKey()
                        + "', not requested space '" + ToolText.oneLine(space, FIELD_CHARS) + "'.");
            }
        } else {
            NavigableKnowledgeSource owner = source;
            if (owner == null) {
                List<NavigableKnowledgeSource> owners = new ArrayList<NavigableKnowledgeSource>();
                for (NavigableKnowledgeSource candidate : navigable) {
                    if (candidate.space(space) != null) {
                        owners.add(candidate);
                    }
                }
                if (owners.size() > 1) {
                    return McpToolResult.error("Space '" + space + "' exists in several sources; pass source.\n\n"
                            + validSources(owners));
                }
                owner = owners.isEmpty() ? null : owners.get(0);
            }
            KnowledgeSpace chosen = owner == null ? null : owner.space(space);
            if (chosen == null) {
                return McpToolResult.error("Unknown space '" + ToolText.oneLine(space, FIELD_CHARS) + "'"
                        + (owner == null ? "" : " for source '" + owner.source().value() + "'") + ".\n\n"
                        + validSpaces(owner == null ? navigable : Collections.singletonList(owner)));
            }
            try {
                anchor = hierarchy.space(owner.source(), chosen);
            } catch (KnowledgeSourceException e) {
                return McpToolResult.error(describe(e.kind(), space));
            } catch (RuntimeException e) {
                return McpToolResult.error("Opening space '" + space + "' failed. Try again later.");
            }
            if (anchor == null) {
                return McpToolResult.error("Space '" + space + "' has no page tree to browse.");
            }
        }
        try {
            return McpToolResult.ok(render(anchor, direction, (int) limit, cursor));
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error("Invalid cursor: pass the nextCursor of the previous call unchanged and with "
                    + "the same target, or leave it out to start at the first child.");
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error(describe(e.kind(), anchor.current().title()));
        } catch (RuntimeException e) {
            return McpToolResult.error("Loading the children failed. Try again later.");
        }
    }

    private KnowledgeTreeAnchor pageAnchor(String rawId, String rawBookmark, NavigableKnowledgeSource source)
            throws KnowledgeSourceException {
        KnowledgeResourceId id;
        if (!rawBookmark.isEmpty()) {
            if (!KnowledgeBookmarkIds.isBookmark(rawBookmark)) {
                throw new IllegalArgumentException("Invalid bookmark '" + ToolText.oneLine(rawBookmark, FIELD_CHARS)
                        + "': pass a bookmark returned by the knowledge tools, or the id as id.");
            }
            id = KnowledgeBookmarkIds.resourceIdOf(rawBookmark);
        } else {
            try {
                if (KnowledgeBookmarkIds.isBookmark(rawId)) {
                    id = KnowledgeBookmarkIds.resourceIdOf(rawId);
                } else if (KnowledgePassageId.looksLike(rawId)) {
                    id = KnowledgePassageId.parse(rawId).resourceId();
                } else {
                    id = KnowledgeResourceId.of(rawId);
                }
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Invalid id '" + ToolText.oneLine(rawId, FIELD_CHARS)
                        + "': copy the id of a knowledge_search hit.");
            }
        }
        return hierarchy.page(id, source == null ? null : source.source());
    }

    private String render(KnowledgeTreeAnchor anchor, String direction, int limit, String cursor)
            throws KnowledgeSourceException {
        StringBuilder out = new StringBuilder();
        out.append("source: ").append(anchor.source().value()).append('\n');
        out.append("space: ").append(ToolText.oneLine(anchor.spaceKey(), FIELD_CHARS)).append(" — ")
                .append(ToolText.oneLine(anchor.spaceTitle(), FIELD_CHARS)).append('\n');
        if (!CHILDREN.equals(direction)) {
            out.append("\nparents:");
            if (anchor.atRoot()) {
                out.append(" none\n");
            } else {
                out.append('\n');
                spaceEntry(out, anchor);
                for (KnowledgeTreeNode ancestor : anchor.ancestors()) {
                    entry(out, ancestor, false);
                }
            }
        }
        out.append("\ncurrent:\n");
        if (anchor.atRoot()) {
            out.append("  type: space\n");
            out.append("  space: ").append(ToolText.oneLine(anchor.spaceKey(), FIELD_CHARS)).append('\n');
            out.append("  title: ").append(ToolText.oneLine(anchor.spaceTitle(), FIELD_CHARS)).append('\n');
        } else {
            out.append("  type: page\n");
            fields(out, anchor.current());
        }
        if (PARENTS.equals(direction)) {
            return ToolText.truncate(out.toString(), settings.maxResponseChars());
        }
        out.append("\nchildren:\n");
        final int budget = settings.maxResponseChars() - footerReserve(anchor);
        final StringBuilder children = new StringBuilder();
        final int head = out.length();
        final int[] taken = {0};
        KnowledgeTreeChildren section = hierarchy.children(anchor, cursor, limit, child -> {
            StringBuilder line = new StringBuilder();
            entry(line, child, true);
            if (taken[0] > 0 && head + children.length() + line.length() > budget) {
                return false;
            }
            children.append(line);
            taken[0]++;
            return true;
        });
        out.append(section.children().isEmpty() ? (cursor.isEmpty() ? "none\n" : "no further children\n")
                : children.toString());
        if (!section.complete()) {
            if (section.truncated()) {
                out.append("\ntruncated: true");
            }
            out.append("\nnextCursor: ").append(section.nextCursor()).append('\n');
            out.append("More direct children follow: repeat this call with cursor=\"<nextCursor>\".");
        }
        if (!section.children().isEmpty()) {
            out.append("\nRead a page with knowledge_read and its id; browse further with knowledge_tree and its "
                    + "bookmark.");
        }
        return out.toString();
    }

    private static void spaceEntry(StringBuilder out, KnowledgeTreeAnchor anchor) {
        out.append("- type: space\n");
        out.append("  space: ").append(ToolText.oneLine(anchor.spaceKey(), FIELD_CHARS)).append('\n');
        out.append("  title: ").append(ToolText.oneLine(anchor.spaceTitle(), FIELD_CHARS)).append('\n');
    }

    private static void entry(StringBuilder out, KnowledgeTreeNode node, boolean withChildren) {
        out.append("- title: ").append(ToolText.oneLine(node.title(), FIELD_CHARS)).append('\n');
        ids(out, node);
        if (withChildren) {
            out.append("  children: ").append(childHint(node)).append('\n');
        }
    }

    private static void fields(StringBuilder out, KnowledgeTreeNode node) {
        out.append("  title: ").append(ToolText.oneLine(node.title(), FIELD_CHARS)).append('\n');
        ids(out, node);
        out.append("  children: ").append(childHint(node)).append('\n');
    }

    private static void ids(StringBuilder out, KnowledgeTreeNode node) {
        if (node.resource() == null) {
            return;
        }
        out.append("  id: ").append(node.resource().value()).append('\n');
        if (!node.bookmark().isEmpty()) {
            out.append("  bookmark: ").append(node.bookmark()).append('\n');
        }
    }

    private static String childHint(KnowledgeTreeNode node) {
        switch (node.children()) {
            case YES:
                return "yes";
            case NO:
                return "no";
            default:
                return "unknown";
        }
    }

    /** Estimate the room the cursor lines take, so a truncated section never loses its continuation. */
    private static int footerReserve(KnowledgeTreeAnchor anchor) {
        String key = anchor.current().isRoot() ? anchor.spaceKey() : anchor.current().resource().value();
        int fields = anchor.source().value().length() + anchor.spaceKey().length() + key.length()
                + SOURCE_CURSOR_CHARS;
        return fields * BASE64_GROWTH_NUMERATOR / BASE64_GROWTH_DENOMINATOR + 2 * FOOTER_LINE_CHARS;
    }

    private static String discovery(List<NavigableKnowledgeSource> navigable, NavigableKnowledgeSource only) {
        StringBuilder out = new StringBuilder();
        if (navigable.isEmpty()) {
            return "No connected knowledge source has a page tree. Search with knowledge_search instead.";
        }
        out.append("Navigable knowledge spaces:\n");
        for (NavigableKnowledgeSource source : navigable) {
            if (only != null && only != source) {
                continue;
            }
            out.append("\nsource: ").append(source.source().value()).append('\n');
            if (source.failure() != null) {
                out.append("  spaces unavailable right now (").append(source.failure()).append(")\n");
            }
            for (KnowledgeSpace space : source.spaces()) {
                out.append("\n- space: ").append(ToolText.oneLine(space.key(), FIELD_CHARS)).append('\n');
                out.append("  title: ").append(ToolText.oneLine(space.title(), FIELD_CHARS)).append('\n');
                if (space.homeResource() != null) {
                    out.append("  homeId: ").append(space.homeResource().value()).append('\n');
                    if (!space.homeBookmark().isEmpty()) {
                        out.append("  homeBookmark: ").append(space.homeBookmark()).append('\n');
                    }
                }
            }
        }
        out.append("\nOpen a space with knowledge_tree, its source and its exact space value.");
        return out.toString();
    }

    private static String validSources(List<NavigableKnowledgeSource> navigable) {
        if (navigable.isEmpty()) {
            return "No connected knowledge source has a page tree.";
        }
        StringBuilder out = new StringBuilder("Valid navigable sources:");
        for (NavigableKnowledgeSource source : navigable) {
            out.append("\n- ").append(source.source().value());
        }
        return out.toString();
    }

    private static String validSpaces(List<NavigableKnowledgeSource> sources) {
        StringBuilder out = new StringBuilder("Valid spaces:");
        boolean any = false;
        for (NavigableKnowledgeSource source : sources) {
            for (KnowledgeSpace space : source.spaces()) {
                out.append("\n- ").append(ToolText.oneLine(space.key(), FIELD_CHARS)).append(" — ")
                        .append(ToolText.oneLine(space.title(), FIELD_CHARS));
                if (sources.size() > 1) {
                    out.append(" (source ").append(source.source().value()).append(')');
                }
                any = true;
            }
        }
        return any ? out.toString() : "No navigable source lists spaces right now.";
    }

    private static NavigableKnowledgeSource find(List<NavigableKnowledgeSource> navigable, String raw) {
        SourceAddress address;
        try {
            address = SourceAddress.of(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        for (NavigableKnowledgeSource source : navigable) {
            if (source.source().equals(address)) {
                return source;
            }
        }
        return null;
    }

    private static String describe(KnowledgeSourceException.Kind kind, String target) {
        String what = "'" + ToolText.oneLine(target, FIELD_CHARS) + "'";
        switch (kind) {
            case NOT_FOUND:
                return "Page " + what + " was not found. Search again — example: knowledge_search query=\"<topic>\"";
            case UNSUPPORTED:
                return "No page tree is available for " + what + ". Only pages have parents and children.";
            case ACCESS_DENIED:
                return "Access to the page tree of " + what + " was denied.";
            case UNAVAILABLE:
                return "The source of " + what + " is not reachable right now. Try again later.";
            default:
                return "The page tree of " + what + " could not be loaded.";
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
