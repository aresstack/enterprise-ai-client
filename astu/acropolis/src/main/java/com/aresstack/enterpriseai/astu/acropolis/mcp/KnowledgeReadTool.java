package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeBookmarkIds;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentNotIndexedException;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeAddressUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeResourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeResourceUseCase.Passage;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeResourceUseCase.ResolvedResource;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolvedKnowledgeLink;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code knowledge_read(id)}: Resource-Resolver (corenth: Bookmark statt Textkopie). Die ID eines Suchtreffers
 * ({@code confluence:47110815}) oder einer Passage
 * ({@code confluence:47110815@<sha256>#p3@9f2c41ab-5e07d1c3}) oder ihr Bookmark ({@code confluence://cf.intern/confluence/page/
 * 47110815}, über {@link KnowledgeBookmarkIds}) wird zuerst aus
 * dem Index beantwortet: Referenz (id, url, Revision, Inhalts-Hash) und die aufbereiteten Passagen mit ihren IDs. Die
 * Quelle wird nur bei unbekanntem oder geändertem Stand gefragt, auf dem Thread des Agenten (an der Vorlade-Warteschlange
 * vorbei), danach wird indexiert und wieder aus dem Index geliefert. Bei einer Passage-ID kommen diese Passage und
 * ihre Nachbarn. Scheitert das Lesen eines nichtkanonischen Bookmarks oder einer Webadresse an der Adresse (nicht
 * gefunden, unbekannt), löst der Adress-Resolver sie einmal auf und das Werkzeug liest die kanonische ID (#109).
 */
final class KnowledgeReadTool implements McpToolHandler {

    static final String NAME = "knowledge_read";
    static final String PARAM_ID = "id";

    private static final String EXAMPLE = "id=\"confluence:47110815\"";
    private static final String BOOKMARK_EXAMPLE = "id=\"confluence://cf.intern/confluence/page/47110815\"";
    private static final int FIELD_CHARS = 200;
    private static final int HASH_CHARS = 12;
    /** Nachbarn links und rechts einer angefragten Passage. */
    private static final int NEIGHBOURS = 1;

    private final LoadKnowledgeDocumentUseCase documents;
    private final SearchDrivenIndexingUseCase live;
    private final ResolveKnowledgeAddressUseCase addresses;
    private final KnowledgeToolSettings settings;

    KnowledgeReadTool(LoadKnowledgeDocumentUseCase documents, SearchDrivenIndexingUseCase live,
                      ResolveKnowledgeAddressUseCase addresses, KnowledgeToolSettings settings) {
        this.documents = documents;
        this.live = live;
        this.addresses = addresses;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Read one document returned by knowledge_search, an attachment listed by knowledge_attachments, or a "
                        + "page bookmark the user added to the chat. Use the exact id from a search hit or attachment "
                        + "(line \"id:\") or the bookmark as given. Example: " + EXAMPLE
                        + ". Example: " + BOOKMARK_EXAMPLE + ".",
                this,
                McpToolParameter.string(PARAM_ID, true, "Document id from knowledge_search or a page bookmark. "
                        + "Example: " + EXAMPLE));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String rawId = call.getString(PARAM_ID);
        if (ToolText.isBlank(rawId)) {
            return McpToolResult.error("Missing argument: id — example: " + EXAMPLE);
        }
        String trimmed = rawId.trim();
        KnowledgePassageId passage = null;
        KnowledgeResourceId id;
        try {
            if (KnowledgeBookmarkIds.isBookmark(trimmed)) {
                id = KnowledgeBookmarkIds.resourceIdOf(trimmed);
            } else if (KnowledgePassageId.looksLike(trimmed)) {
                passage = KnowledgePassageId.parse(trimmed);
                id = passage.resourceId();
            } else {
                id = KnowledgeResourceId.of(trimmed);
            }
        } catch (IllegalArgumentException invalid) {
            McpToolResult recovered = recover(trimmed, null);
            return recovered != null ? recovered : McpToolResult.error("Invalid id '" + ToolText.oneLine(rawId, 120)
                    + "': copy the id of a knowledge_search hit or a page bookmark — example: " + EXAMPLE);
        }
        Attempt first = read(id, passage);
        if (!first.addressProblem || passage != null) {
            return first.result;
        }
        McpToolResult recovered = recover(trimmed, id);
        return recovered != null ? recovered : first.result;
    }

    /**
     * #109: repair a plausible but non-canonical address after a failed read through the address resolver, never
     * through own path rules. A web address asks every matching source and reads only a single candidate; a
     * bookmark-like value asks only the configured source whose address is its longest prefix. Read the resolved
     * resource exactly once.
     *
     * @param failed the id the first read used, {@code null} if the input was no valid id
     * @return the outcome, or {@code null} if the input is no address the resolver could know
     */
    private McpToolResult recover(String input, KnowledgeResourceId failed) {
        if (!KnowledgeBookmarkIds.isBookmark(input)) {
            return null;
        }
        if (isWebAddress(input)) {
            List<ResolvedKnowledgeLink> candidates = addresses.resolve(input);
            if (candidates.size() == 1) {
                return readResolved(input, candidates.get(0).resourceId(), failed);
            }
            if (candidates.size() > 1) {
                return McpToolResult.error(ambiguous(input, candidates));
            }
            return McpToolResult.error("No connected knowledge source owns the address '"
                    + ToolText.oneLine(input, FIELD_CHARS) + "'. Treat it as an external web page or search "
                    + "instead — example: knowledge_search query=\"<topic>\"");
        }
        Optional<SourceAddress> source = addresses.sourceOf(input);
        if (!source.isPresent()) {
            return null;
        }
        Optional<ResolvedKnowledgeLink> resolved;
        try {
            resolved = addresses.resolve(input, source.get());
        } catch (KnowledgeSourceException e) {
            if (e.kind() == KnowledgeSourceException.Kind.ACCESS_DENIED
                    || e.kind() == KnowledgeSourceException.Kind.UNAVAILABLE) {
                return McpToolResult.error(describe(e.kind(),
                        failed == null ? ToolText.oneLine(input, FIELD_CHARS) : failed.value()));
            }
            resolved = Optional.empty();
        }
        if (resolved.isPresent()) {
            return readResolved(input, resolved.get().resourceId(), failed);
        }
        return McpToolResult.error("The resource could not be read under '" + ToolText.oneLine(input, FIELD_CHARS)
                + "'. The input looks like a non-canonical knowledge address of the source '"
                + source.get().value() + "' but matches no resource of it. Use an id from knowledge_search or "
                + "knowledge_resolve_url — example: " + EXAMPLE);
    }

    private McpToolResult readResolved(String input, KnowledgeResourceId resolved, KnowledgeResourceId failed) {
        if (resolved.equals(failed)) {
            return null; // the resolver names the id that just failed: keep that error, never loop
        }
        Attempt second = read(resolved, null);
        if (second.result.isError()) {
            return second.result;
        }
        return McpToolResult.ok("note: '" + ToolText.oneLine(input, FIELD_CHARS) + "' is no canonical id; resolved "
                + "to " + resolved.value() + "\n" + second.result.getText(), second.result.contextResources());
    }

    private static String ambiguous(String input, List<ResolvedKnowledgeLink> candidates) {
        StringBuilder text = new StringBuilder("The address '").append(ToolText.oneLine(input, FIELD_CHARS))
                .append("' belongs to ").append(candidates.size())
                .append(" sources; none was picked. Read one of these ids with knowledge_read:");
        for (ResolvedKnowledgeLink candidate : candidates) {
            text.append("\nsource: ").append(candidate.sourceId().value())
                    .append(" id: ").append(candidate.resourceId().value());
        }
        return text.toString();
    }

    private static boolean isWebAddress(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** One read of a resource id; {@code addressProblem} says whether the failure may be a wrong address. */
    private Attempt read(KnowledgeResourceId id, KnowledgePassageId passage) {
        ResolvedResource resolved;
        try {
            resolved = new ResolveKnowledgeResourceUseCase(live.prefetch().indexing(), live.prefetch(), documents)
                    .resolve(id);
        } catch (KnowledgeDocumentNotIndexedException e) {
            return new Attempt(McpToolResult.error("Document '" + id.value() + "' is not available. Search first, "
                    + "then read a hit — example: knowledge_search query=\"Urlaubsantrag\", then knowledge_read "
                    + EXAMPLE), true);
        } catch (KnowledgeSourceException e) {
            boolean address = e.kind() == KnowledgeSourceException.Kind.NOT_FOUND
                    || e.kind() == KnowledgeSourceException.Kind.UNSUPPORTED;
            return new Attempt(McpToolResult.error(describe(e.kind(), id.value())), address);
        } catch (RuntimeException e) {
            return new Attempt(McpToolResult.error("Reading '" + id.value() + "' failed. Try again or read another "
                    + "hit — example: " + EXAMPLE), false);
        }
        return new Attempt(McpToolResult.ok(render(resolved, passage), provenance(resolved)), false);
    }

    /** Name the read resource as context the answer rests on (issue #118), from its reference, not from the text. */
    private static List<McpContextResource> provenance(ResolvedResource resolved) {
        KnowledgeResourceRef ref = resolved.ref();
        return Collections.singletonList(new McpContextResource(ref.sourceId().value(), ref.resourceId().value(),
                resolved.title(), ref.canonicalUrl() == null ? null : ref.canonicalUrl().toString(),
                ref.revision().version(), ref.revision().modifiedAt().orElse(null)));
    }

    private static final class Attempt {
        final McpToolResult result;
        final boolean addressProblem;

        Attempt(McpToolResult result, boolean addressProblem) {
            this.result = result;
            this.addressProblem = addressProblem;
        }
    }

    private static String describe(KnowledgeSourceException.Kind kind, String id) {
        String document = "'" + id + "'";
        switch (kind) {
            case NOT_FOUND:
                return "Document " + document + " was not found (deleted or moved). Search again — example: "
                        + "knowledge_search query=\"<topic>\"";
            case UNSUPPORTED:
                return "No knowledge source knows " + document + ". Use an id from knowledge_search — example: "
                        + EXAMPLE;
            case ACCESS_DENIED:
                return "Access to " + document + " was denied (files of some sources need a one-time code the user "
                        + "did not enter). Read another hit.";
            case UNAVAILABLE:
                return "The source of " + document + " is not reachable right now. Try again later.";
            default:
                return "Document " + document + " could not be read. Read another hit — example: " + EXAMPLE;
        }
    }

    private String render(ResolvedResource resolved, KnowledgePassageId requested) {
        KnowledgeResourceRef ref = resolved.ref();
        StringBuilder out = new StringBuilder();
        out.append("title: ").append(ToolText.oneLine(resolved.title(), FIELD_CHARS)).append('\n');
        out.append("id: ").append(ref.resourceId().value()).append('\n');
        if (ref.canonicalUrl() != null) {
            out.append("url: ").append(ref.canonicalUrl()).append('\n');
        }
        out.append("source: ").append(ref.sourceId().value()).append('\n');
        out.append("version: ").append(ToolText.revision(ref.revision())).append('\n');
        if (!ref.contentHash().isEmpty()) {
            out.append("contentHash: ").append(ref.contentHash(), 0, Math.min(HASH_CHARS, ref.contentHash().length()))
                    .append('\n');
        }
        out.append("from: ").append(resolved.fromIndex() ? "index" : "source").append('\n');
        if (resolved.unpreparedDocument().isPresent()) {
            KnowledgeDocument document = resolved.unpreparedDocument().get();
            out.append(document.isBlank() ? "(empty)" : "\n" + document.text());
            return ToolText.truncate(out.toString(), settings.maxResponseChars());
        }
        List<Passage> passages = resolved.passages();
        int from = 0;
        int to = passages.size();
        if (requested != null) {
            int at = indexOf(passages, requested);
            if (at >= 0) {
                from = Math.max(0, at - NEIGHBOURS);
                to = Math.min(passages.size(), at + NEIGHBOURS + 1);
                out.append("passage: ").append(requested.value()).append(" (with neighbours)\n");
            } else if (!passages.isEmpty() && !passages.get(0).id().capture().equals(requested.capture())) {
                out.append("note: passage ").append(requested.value()).append(" belongs to an older version of the ")
                        .append("page (capture ").append(requested.capture()).append(", now ")
                        .append(passages.get(0).id().capture())
                        .append("); its text is gone. The current passages follow — cite them instead.\n");
            } else {
                out.append("note: passage ").append(requested.value())
                        .append(" no longer exists (segmentation or embedding model changed); all passages follow.\n");
            }
        }
        out.append("passages: ").append(passages.size()).append('\n');
        for (int i = from; i < to; i++) {
            Passage passage = passages.get(i);
            out.append('\n').append("[passage ").append(passage.id().value()).append(']');
            if (!passage.heading().isEmpty()) {
                out.append(' ').append(ToolText.oneLine(passage.heading(), FIELD_CHARS));
            }
            out.append('\n').append(passage.text()).append('\n');
        }
        return ToolText.truncate(out.toString(), settings.maxResponseChars());
    }

    private static int indexOf(List<Passage> passages, KnowledgePassageId requested) {
        for (int i = 0; i < passages.size(); i++) {
            if (passages.get(i).id().equals(requested)) {
                return i;
            }
        }
        return -1;
    }
}
