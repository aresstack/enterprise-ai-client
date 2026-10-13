package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeAddressUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolvedKnowledgeLink;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * {@code knowledge_resolve_url(url, source?)}: eine vom Benutzer genannte Adresse (z. B. eine Confluence-Seite) als
 * Ressource der angebundenen Quellen, über denselben Resolver wie Suchleiste und Lesebereich (#94). Ohne {@code
 * source} fragt er jede Quelle, deren Adressmuster passt, einzeln und gibt alle Kandidaten zurück, ohne zu wählen;
 * mit {@code source} nur diese Quelle (höchstens ein Kandidat). Gelesen wird danach mit {@code knowledge_read}.
 */
final class KnowledgeResolveUrlTool implements McpToolHandler {

    static final String NAME = "knowledge_resolve_url";
    static final String PARAM_URL = "url";
    static final String PARAM_SOURCE = "source";

    /** Without host: base addresses are configuration, never literals (HardcodedConfigurationRules). */
    private static final String EXAMPLE = "url=\"<the complete link as given, e.g. "
            + ".../pages/viewpage.action?pageId=47110815>\"";
    private static final int FIELD_CHARS = 300;

    private final ResolveKnowledgeAddressUseCase addresses;

    KnowledgeResolveUrlTool(ResolveKnowledgeAddressUseCase addresses) {
        this.addresses = addresses;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Resolve a web address the user gave (e.g. a link to an internal wiki page) to documents of the "
                        + "connected knowledge sources, without searching. Returns every candidate with source, id "
                        + "and bookmark; it never picks one. Read a candidate with knowledge_read and its id. With "
                        + "several candidates decide by the user's context, or read them. No candidate: the address "
                        + "is not an internal document. Example: " + EXAMPLE + ".",
                this,
                McpToolParameter.string(PARAM_URL, true, "The complete address as given, with http:// or https://. "
                        + "Example: " + EXAMPLE),
                McpToolParameter.string(PARAM_SOURCE, false, "Optional: only ask this source (the \"source:\" "
                        + "value of a knowledge_search hit or of an earlier candidate)."));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String url = call.getString(PARAM_URL);
        if (ToolText.isBlank(url)) {
            return McpToolResult.error("Missing argument: url — example: " + EXAMPLE);
        }
        String source = call.getString(PARAM_SOURCE);
        List<ResolvedKnowledgeLink> candidates;
        if (ToolText.isBlank(source)) {
            candidates = addresses.resolve(url);
        } else {
            SourceAddress hint;
            try {
                hint = SourceAddress.of(source.trim());
            } catch (IllegalArgumentException invalid) {
                return McpToolResult.error("Invalid source '" + ToolText.oneLine(source, FIELD_CHARS)
                        + "': use the \"source:\" value of a knowledge_search hit, or leave it out.");
            }
            try {
                Optional<ResolvedKnowledgeLink> found = addresses.resolve(url, hint);
                candidates = found.isPresent() ? Collections.singletonList(found.get())
                        : Collections.<ResolvedKnowledgeLink>emptyList();
            } catch (KnowledgeSourceException e) {
                return McpToolResult.error("The source '" + hint.value() + "' could not check the address ("
                        + e.kind() + "). Try again later or without source.");
            }
        }
        return McpToolResult.ok(render(url.trim(), candidates));
    }

    static String render(String url, List<ResolvedKnowledgeLink> candidates) {
        List<String> lines = new ArrayList<String>();
        lines.add("url: " + ToolText.oneLine(url, FIELD_CHARS));
        lines.add("candidates: " + candidates.size());
        if (candidates.isEmpty()) {
            lines.add("No connected knowledge source owns this address. Treat it as an external web page.");
        }
        for (int i = 0; i < candidates.size(); i++) {
            ResolvedKnowledgeLink candidate = candidates.get(i);
            lines.add("");
            lines.add("[" + (i + 1) + "]");
            lines.add("source: " + candidate.sourceId().value());
            lines.add("id: " + candidate.resourceId().value());
            lines.add("bookmark: " + candidate.bookmark());
            if (!candidate.fragment().isEmpty()) {
                lines.add("fragment: " + ToolText.oneLine(candidate.fragment(), FIELD_CHARS));
            }
        }
        if (!candidates.isEmpty()) {
            lines.add("");
            lines.add("Read a candidate with knowledge_read and its id.");
        }
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append('\n');
        }
        return text.toString().trim();
    }
}
