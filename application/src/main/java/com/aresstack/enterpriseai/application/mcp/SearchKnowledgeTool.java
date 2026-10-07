package com.aresstack.enterpriseai.application.mcp;

import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.rag.KnowledgeRetrievalException;
import com.aresstack.enterpriseai.application.rag.RetrievalResult;
import com.aresstack.enterpriseai.application.rag.RetrievalWarning;
import com.aresstack.enterpriseai.application.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.application.rag.RetrievedChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code search_knowledge}: hybride Suche (Volltext + semantisch, Reciprocal Rank Fusion) über
 * {@link RetrieveKnowledgeUseCase}. Liefert je Treffer Titel, Überschrift, IDs, Quelle, Ort, Stand, Scores und
 * einen Textausschnitt; Treffer, die nicht mehr in die Antwortgröße passen, werden weggelassen und gezählt.
 */
final class SearchKnowledgeTool implements McpToolHandler {

    static final String NAME = "search_knowledge";
    static final String PARAM_QUERY = "query";
    static final String PARAM_MAX_RESULTS = "max_results";
    static final String PARAM_SOURCE_IDS = "source_ids";

    private static final int QUERY_ECHO_CHARS = 200;
    private static final int TITLE_CHARS = 200;

    private final RetrieveKnowledgeUseCase retrieval;
    private final KnowledgeSourceCatalog catalog;
    private final KnowledgeToolSettings settings;

    SearchKnowledgeTool(RetrieveKnowledgeUseCase retrieval, KnowledgeSourceCatalog catalog,
                        KnowledgeToolSettings settings) {
        this.retrieval = retrieval;
        this.catalog = catalog;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        int cap = retrieval.settings().maxResults();
        return McpToolContribution.of(NAME,
                "Durchsucht die indexierte Wissensbasis (Volltext und semantisch kombiniert) und liefert die "
                        + "relevantesten Textabschnitte mit Titel, Dokument-ID, Quelle, Ort, Stand und Scores. "
                        + "Nutze get_knowledge_document mit der Dokument-ID eines Treffers, um das vollständige "
                        + "Dokument zu lesen.",
                this,
                McpToolParameter.string(PARAM_QUERY, true, "Suchanfrage in natürlicher Sprache oder Stichwörter"),
                McpToolParameter.integer(PARAM_MAX_RESULTS, false, "Höchstzahl der Treffer (Standard "
                        + settings.defaultMaxResults() + ", höchstens " + cap + ")"),
                McpToolParameter.string(PARAM_SOURCE_IDS, false,
                        "Nur in diesen Quellen suchen: Quell-IDs durch Komma getrennt (Standard: alle; "
                                + "konfiguriert: " + knownSources() + ")"));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String query = call.getString(PARAM_QUERY);
        if (ToolText.isBlank(query)) {
            return McpToolResult.error("Parameter '" + PARAM_QUERY + "' fehlt oder ist leer.");
        }
        long requested = call.getInteger(PARAM_MAX_RESULTS, settings.defaultMaxResults());
        if (requested < 1) {
            return McpToolResult.error("Parameter '" + PARAM_MAX_RESULTS + "' muss mindestens 1 sein.");
        }
        int maxResults = (int) Math.min(requested, retrieval.settings().maxResults());

        List<KnowledgeSourceId> sources = new ArrayList<KnowledgeSourceId>();
        String problem = parseSourceIds(call.getString(PARAM_SOURCE_IDS), sources);
        if (problem != null) {
            return McpToolResult.error(problem);
        }

        RetrievalResult result;
        try {
            result = retrieval.retrieve(query.trim(), sources);
        } catch (KnowledgeRetrievalException e) {
            StringBuilder message = new StringBuilder("Wissenssuche nicht möglich, alle Suchpfade sind ausgefallen.");
            for (RetrievalWarning warning : e.warnings()) {
                message.append(' ').append(ToolText.oneLine(warning.message(), 300));
            }
            return McpToolResult.error(message.toString());
        } catch (RuntimeException e) {
            // Meldung bewusst ohne Details: sie könnte Anfrage- oder Infrastrukturdaten enthalten.
            return McpToolResult.error("Wissenssuche fehlgeschlagen.");
        }
        return McpToolResult.ok(render(query.trim(), sources, result, maxResults));
    }

    /** @return eine Fehlermeldung oder {@code null}; die erkannten IDs stehen in {@code target} */
    private String parseSourceIds(String raw, List<KnowledgeSourceId> target) {
        if (ToolText.isBlank(raw)) {
            return null;
        }
        for (String part : raw.split("[,;\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            KnowledgeSourceId id;
            try {
                id = KnowledgeSourceId.of(part);
            } catch (IllegalArgumentException invalid) {
                return "Parameter '" + PARAM_SOURCE_IDS + "' enthält eine ungültige Quell-ID. Konfigurierte Quellen: "
                        + knownSources() + ".";
            }
            if (!catalog.contains(id)) {
                return "Unbekannte Quelle '" + id.value() + "'. Konfigurierte Quellen: " + knownSources() + ".";
            }
            if (!target.contains(id)) {
                target.add(id);
            }
        }
        return null;
    }

    private String render(String query, List<KnowledgeSourceId> sources, RetrievalResult result, int maxResults) {
        List<RetrievedChunk> hits = result.hits();
        int shown = Math.min(hits.size(), maxResults);
        StringBuilder out = new StringBuilder();
        out.append("Treffer: ").append(shown);
        if (shown < hits.size()) {
            out.append(" von ").append(hits.size());
        }
        out.append('\n');
        out.append("Anfrage: ").append(ToolText.oneLine(query, QUERY_ECHO_CHARS)).append('\n');
        if (!sources.isEmpty()) {
            out.append("Quellen: ").append(ToolText.join(sources)).append('\n');
        }
        for (RetrievalWarning warning : result.warnings()) {
            out.append("Hinweis (").append(warning.path()).append("): nur ein Suchpfad hat geliefert – ")
                    .append(ToolText.oneLine(warning.message(), 300)).append('\n');
        }
        if (shown == 0) {
            out.append("Keine Treffer.");
            return ToolText.truncate(out.toString(), settings.maxResponseChars());
        }

        int limit = settings.maxResponseChars() - ToolText.MARKER_RESERVE;
        int included = 0;
        for (int i = 0; i < shown; i++) {
            String block = renderHit(i + 1, hits.get(i));
            if (out.length() + block.length() > limit) {
                break;
            }
            out.append(block);
            included++;
        }
        if (included < shown) {
            out.append("\n… ").append(shown - included).append(" weitere Treffer wegen der Antwortgröße ausgelassen.");
        }
        return ToolText.truncate(out.toString(), settings.maxResponseChars());
    }

    private String renderHit(int number, RetrievedChunk hit) {
        KnowledgeResource resource = hit.resource();
        StringBuilder block = new StringBuilder();
        block.append('\n').append('[').append(number).append("] ")
                .append(ToolText.oneLine(resource.title(), TITLE_CHARS));
        String heading = hit.chunk().headingLine();
        if (!heading.isEmpty()) {
            block.append(" – ").append(ToolText.oneLine(heading, TITLE_CHARS));
        }
        block.append('\n');
        block.append("Id: ").append(resource.id().value()).append('\n');
        block.append("Chunk: ").append(hit.chunk().id().value()).append('\n');
        block.append("Quelle: ").append(resource.sourceId().value()).append('\n');
        if (resource.location().isPresent()) {
            block.append("Ort: ").append(resource.location().get()).append('\n');
        }
        block.append("Stand: ").append(ToolText.revision(resource.revision())).append('\n');
        block.append("Score: ").append(ToolText.score(hit.fusedScore()));
        block.append(" (");
        if (hit.keywordRank().isPresent()) {
            block.append("Volltext #").append(hit.keywordRank().getAsInt()).append(": ")
                    .append(ToolText.score(hit.keywordScore().getAsDouble()));
        }
        if (hit.semanticRank().isPresent()) {
            if (hit.keywordRank().isPresent()) {
                block.append(" | ");
            }
            block.append("Semantik #").append(hit.semanticRank().getAsInt()).append(": ")
                    .append(ToolText.score(hit.semanticScore().getAsDouble()));
        }
        block.append(")\n");
        block.append("Text:\n").append(ToolText.snippet(hit.chunk().text(), settings.snippetChars())).append('\n');
        return block.toString();
    }

    private String knownSources() {
        return catalog.isEmpty() ? "keine" : ToolText.join(catalog.ids());
    }
}
