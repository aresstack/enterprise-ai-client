package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeDocumentNotIndexedException;
import com.aresstack.enterpriseai.application.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.application.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

/**
 * {@code knowledge_read(id)}: liest ein Dokument aus einem Suchtreffer vollständig – auch einen noch nicht indexierten
 * Live-Treffer – über den Priority-Lader des Vorladers (MainframeMate {@code loadPriority}: aus dem Cache, sonst sofort
 * an der Vorlade-Warteschlange vorbei). Die Seite wird danach im Hintergrund indexiert, falls neu oder geändert.
 */
final class KnowledgeReadTool implements McpToolHandler {

    static final String NAME = "knowledge_read";
    static final String PARAM_ID = "id";

    private static final String EXAMPLE = "id=\"confluence:47110815\"";
    private static final int FIELD_CHARS = 200;

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
        KnowledgeResourceId id;
        try {
            id = KnowledgeResourceId.of(rawId.trim());
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error("Invalid id '" + ToolText.oneLine(rawId, 120)
                    + "': copy the id of a knowledge_search hit — example: " + EXAMPLE);
        }
        KnowledgeDocument document;
        final KnowledgeResourceId requested = id;
        try {
            document = live.prefetch().read(id, () -> documents.load(requested));
        } catch (KnowledgeDocumentNotIndexedException e) {
            return McpToolResult.error("Document '" + id.value() + "' is not available. Search first, then read a "
                    + "hit — example: knowledge_search query=\"Urlaubsantrag\", then knowledge_read " + EXAMPLE);
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error(describe(e.kind(), id));
        } catch (RuntimeException e) {
            return McpToolResult.error("Reading '" + id.value() + "' failed. Try again or read another hit — "
                    + "example: " + EXAMPLE);
        }
        return McpToolResult.ok(render(document));
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

    private String render(KnowledgeDocument document) {
        KnowledgeResource resource = document.resource();
        StringBuilder out = new StringBuilder();
        out.append("title: ").append(ToolText.oneLine(resource.title(), FIELD_CHARS)).append('\n');
        out.append("id: ").append(resource.id().value()).append('\n');
        out.append("source: ").append(resource.sourceId().value()).append('\n');
        if (resource.location().isPresent()) {
            out.append("location: ").append(resource.location().get()).append('\n');
        }
        out.append("version: ").append(ToolText.revision(resource.revision())).append('\n');
        out.append(document.isBlank() ? "(empty)" : "\n" + document.text());
        return ToolText.truncate(out.toString(), settings.maxResponseChars());
    }
}
