package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentNotIndexedException;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeResourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeResourceUseCase.Passage;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeResourceUseCase.ResolvedResource;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.List;

/**
 * {@code knowledge_read(id)}: Resource-Resolver (corenth: Bookmark statt Textkopie). Die ID eines Suchtreffers
 * ({@code confluence:47110815}) oder einer Passage
 * ({@code confluence:47110815@3fa9c2d1e0b4#p3@9f2c41ab-5e07d1c3}) wird zuerst aus
 * dem Index beantwortet: Referenz (id, url, Revision, Inhalts-Hash) und die aufbereiteten Passagen mit ihren IDs. Die
 * Quelle wird nur bei unbekanntem oder geändertem Stand gefragt, über den Priority-Lader (an der Vorlade-Warteschlange
 * vorbei), danach wird indexiert und wieder aus dem Index geliefert. Bei einer Passage-ID kommen diese Passage und
 * ihre Nachbarn.
 */
final class KnowledgeReadTool implements McpToolHandler {

    static final String NAME = "knowledge_read";
    static final String PARAM_ID = "id";

    private static final String EXAMPLE = "id=\"confluence:47110815\"";
    private static final int FIELD_CHARS = 200;
    private static final int HASH_CHARS = 12;
    /** Nachbarn links und rechts einer angefragten Passage. */
    private static final int NEIGHBOURS = 1;

    private final LoadKnowledgeDocumentUseCase documents;
    private final SearchDrivenIndexingUseCase live;
    private final KnowledgeToolSettings settings;

    KnowledgeReadTool(LoadKnowledgeDocumentUseCase documents, SearchDrivenIndexingUseCase live,
                      KnowledgeToolSettings settings) {
        this.documents = documents;
        this.live = live;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Read one document returned by knowledge_search. Use the exact id from a search hit (line \"id:\"). "
                        + "Example: " + EXAMPLE + ". Example: id=\"wiki:intranet/Urlaubsantrag\".",
                this,
                McpToolParameter.string(PARAM_ID, true, "Document id from knowledge_search. Example: " + EXAMPLE));
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
            if (KnowledgePassageId.looksLike(trimmed)) {
                passage = KnowledgePassageId.parse(trimmed);
                id = passage.resourceId();
            } else {
                id = KnowledgeResourceId.of(trimmed);
            }
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error("Invalid id '" + ToolText.oneLine(rawId, 120)
                    + "': copy the id of a knowledge_search hit — example: " + EXAMPLE);
        }
        ResolvedResource resolved;
        try {
            resolved = new ResolveKnowledgeResourceUseCase(live.prefetch().indexing(), live.prefetch(), documents)
                    .resolve(id);
        } catch (KnowledgeDocumentNotIndexedException e) {
            return McpToolResult.error("Document '" + id.value() + "' is not available. Search first, then read a "
                    + "hit — example: knowledge_search query=\"Urlaubsantrag\", then knowledge_read " + EXAMPLE);
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error(describe(e.kind(), id));
        } catch (RuntimeException e) {
            return McpToolResult.error("Reading '" + id.value() + "' failed. Try again or read another hit — "
                    + "example: " + EXAMPLE);
        }
        return McpToolResult.ok(render(resolved, passage));
    }

    private static String describe(KnowledgeSourceException.Kind kind, KnowledgeResourceId id) {
        String document = "'" + id.value() + "'";
        switch (kind) {
            case NOT_FOUND:
                return "Document " + document + " was not found (deleted or moved). Search again — example: "
                        + "knowledge_search query=\"<topic>\"";
            case UNSUPPORTED:
                return "No knowledge source knows " + document + ". Use an id from knowledge_search — example: "
                        + EXAMPLE;
            case ACCESS_DENIED:
                return "Access to " + document + " was denied. Read another hit.";
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
