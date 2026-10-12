package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeBookmarkIds;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ListKnowledgeAttachmentsUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.List;
import java.util.Locale;

/**
 * {@code knowledge_attachments(id)}: die Dateianhänge einer Seite (PDF, Office, Bilder ...) mit Name, Typ, Größe und
 * ID, ohne sie zu laden. Gelesen wird ein Anhang danach mit {@code knowledge_read}; verlangt die Quelle dafür einen
 * Einmalcode, fragt die Oberfläche den Benutzer, das Modell sieht den Code nie.
 */
final class KnowledgeAttachmentsTool implements McpToolHandler {

    static final String NAME = "knowledge_attachments";
    static final String PARAM_ID = "id";

    private static final String EXAMPLE = "id=\"confluence:47110815\"";
    private static final int FIELD_CHARS = 200;
    private static final long KILO = 1024L;

    private final ListKnowledgeAttachmentsUseCase attachments;
    private final KnowledgeToolSettings settings;

    KnowledgeAttachmentsTool(ListKnowledgeAttachmentsUseCase attachments, KnowledgeToolSettings settings) {
        this.attachments = attachments;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "List the file attachments (PDF, Office documents, images) of a page returned by knowledge_search "
                        + "or knowledge_read, without loading them. Use it when the page text points to an attached "
                        + "file or misses details. Then read one attachment with knowledge_read and its id. Example: "
                        + EXAMPLE + ".",
                this,
                McpToolParameter.string(PARAM_ID, true, "Page id from knowledge_search or knowledge_read. "
                        + "Example: " + EXAMPLE));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String rawId = call.getString(PARAM_ID);
        if (ToolText.isBlank(rawId)) {
            return McpToolResult.error("Missing argument: id — example: " + EXAMPLE);
        }
        String trimmed = rawId.trim();
        KnowledgeResourceId page;
        try {
            if (KnowledgeBookmarkIds.isBookmark(trimmed)) {
                page = KnowledgeBookmarkIds.resourceIdOf(trimmed);
            } else if (KnowledgePassageId.looksLike(trimmed)) {
                page = KnowledgePassageId.parse(trimmed).resourceId();
            } else {
                page = KnowledgeResourceId.of(trimmed);
            }
        } catch (IllegalArgumentException invalid) {
            return McpToolResult.error("Invalid id '" + ToolText.oneLine(rawId, 120)
                    + "': copy the id of a knowledge_search hit — example: " + EXAMPLE);
        }
        List<KnowledgeResource> found;
        try {
            found = attachments.attachmentsOf(page);
        } catch (KnowledgeSourceException e) {
            return McpToolResult.error(describe(e.kind(), page));
        } catch (RuntimeException e) {
            return McpToolResult.error("Listing the attachments of '" + page.value() + "' failed. Try again later.");
        }
        return McpToolResult.ok(render(page, found));
    }

    private static String describe(KnowledgeSourceException.Kind kind, KnowledgeResourceId page) {
        String document = "'" + page.value() + "'";
        switch (kind) {
            case NOT_FOUND:
                return "Page " + document + " was not found. Search again — example: knowledge_search "
                        + "query=\"<topic>\"";
            case UNSUPPORTED:
                return "No knowledge source lists attachments for " + document + ". Only pages have attachments — "
                        + "example: " + EXAMPLE;
            case ACCESS_DENIED:
                return "Access to the attachments of " + document + " was denied.";
            case UNAVAILABLE:
                return "The source of " + document + " is not reachable right now. Try again later.";
            default:
                return "The attachments of " + document + " could not be listed.";
        }
    }

    private String render(KnowledgeResourceId page, List<KnowledgeResource> found) {
        StringBuilder out = new StringBuilder();
        out.append("page: ").append(page.value()).append('\n');
        out.append("attachments: ").append(found.size()).append('\n');
        for (KnowledgeResource attachment : found) {
            out.append("\n- title: ").append(ToolText.oneLine(attachment.title(), FIELD_CHARS)).append('\n');
            out.append("  id: ").append(attachment.id().value()).append('\n');
            out.append("  type: ").append(ToolText.oneLine(attachment.contentType(), FIELD_CHARS)).append('\n');
            String size = size(attachment.metadata());
            if (!size.isEmpty()) {
                out.append("  size: ").append(size).append('\n');
            }
            out.append("  version: ").append(ToolText.revision(attachment.revision())).append('\n');
        }
        if (!found.isEmpty()) {
            out.append("\nRead an attachment with knowledge_read and its id.");
        }
        return ToolText.truncate(out.toString(), settings.maxResponseChars());
    }

    private static String size(KnowledgeMetadata metadata) {
        if (!metadata.get(KnowledgeMetadata.FILE_SIZE).isPresent()) {
            return "";
        }
        long bytes;
        try {
            bytes = Long.parseLong(metadata.get(KnowledgeMetadata.FILE_SIZE).get());
        } catch (NumberFormatException e) {
            return "";
        }
        if (bytes < KILO) {
            return bytes + " B";
        }
        if (bytes < KILO * KILO) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / (double) KILO);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (double) (KILO * KILO));
    }
}
