package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@code knowledge_index_search(query, limit)}: the hybrid search of the local knowledge index alone (keyword over
 * passages and loaded pages, semantic where embeddings exist, fused), without asking any live source. The same
 * retrieval and the same hit format as {@code knowledge_search}, so each hit is read with {@code knowledge_read}. A
 * tool of the Research Agent's local stage; the enabled sources apply as for {@code knowledge_search}.
 */
final class KnowledgeIndexSearchTool implements McpToolHandler {

    static final String NAME = "knowledge_index_search";

    private final KnowledgeSearchTool search;
    private final KnowledgeToolSettings settings;

    KnowledgeIndexSearchTool(RetrieveKnowledgeUseCase retrieval, SearchDrivenIndexingUseCase live,
                             Supplier<KnowledgeSourceCatalog> catalogs, Predicate<SourceAddress> enabled,
                             KnowledgeToolSettings settings) {
        this.search = new KnowledgeSearchTool(retrieval, live, catalogs, enabled, settings, true);
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Search only the local knowledge index (content already loaded or indexed), keyword and semantic, "
                        + "without asking the live knowledge sources. Read a hit with knowledge_read. "
                        + "Example: query=\"Wie wird die IRR geprüft?\", limit=4.",
                this,
                McpToolParameter.string(KnowledgeSearchTool.PARAM_QUERY, true,
                        "What to look for, natural language or keywords. Example: query=\"IRR Prüfung\""),
                McpToolParameter.integer(KnowledgeSearchTool.PARAM_LIMIT, false, "How many hits to return "
                        + "(default " + settings.defaultMaxResults() + "). Example: limit=4"));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        return search.invoke(call);
    }
}
