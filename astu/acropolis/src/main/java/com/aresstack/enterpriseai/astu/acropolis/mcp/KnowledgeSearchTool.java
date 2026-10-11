package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.FoundResource;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalResult;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@code knowledge_search(query, limit)}: Quellen live durchsuchen und hybrid im Index suchen; die Live-Treffer lädt
 * der Vorlader (MainframeMate {@code ConfluencePrefetchService}) im Hintergrund vor und indexiert sie, ohne dass die
 * Suche darauf wartet. Die Antwort nennt Index- und Live-Treffer unabhängig davon, was schon geladen ist; jeder
 * Treffer ist mit {@code knowledge_read} lesbar. Ein Seitenbudget gibt es hier nicht: das Recherche-Budget zählt der
 * Agent (ein Aufruf = eine Recherche-Aktion), Vorladen verbraucht keines.
 */
final class KnowledgeSearchTool implements McpToolHandler {

    static final String NAME = "knowledge_search";
    static final String PARAM_QUERY = "query";
    static final String PARAM_LIMIT = "limit";

    private static final int TITLE_CHARS = 200;
    /** So viele Treffer je Quelle holt die Live-Suche mindestens (billige Metadaten; das Vorladen braucht ein Fenster). */
    static final int LIVE_HITS = 20;
    /** Kanal der Vorlade-Serie: eine neue Suche des Agenten bricht das Vorladen der vorigen ab. */
    static final String PREFETCH_CHANNEL = "agent";

    private final RetrieveKnowledgeUseCase retrieval;
    private final SearchDrivenIndexingUseCase live;
    private final Supplier<KnowledgeSourceCatalog> catalogs;
    private final Predicate<KnowledgeSourceId> enabled;
    private final KnowledgeToolSettings settings;

    KnowledgeSearchTool(RetrieveKnowledgeUseCase retrieval, SearchDrivenIndexingUseCase live,
                        Supplier<KnowledgeSourceCatalog> catalogs, Predicate<KnowledgeSourceId> enabled,
                        KnowledgeToolSettings settings) {
        this.retrieval = retrieval;
        this.live = live;
        this.catalogs = catalogs;
        this.enabled = enabled;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Search the knowledge sources (e.g. Confluence, wiki) and the local knowledge index. Hits are "
                        + "loaded and indexed in the background automatically. Use this first when you "
                        + "need information about a topic, then read a hit with knowledge_read. "
                        + "Example: query=\"RSL in IAR\", limit=4. "
                        + "Example: query=\"Was bedeutet Rückmeldeschlüssel bei IAR?\".",
                this,
                McpToolParameter.string(PARAM_QUERY, true,
                        "What to look for, natural language or keywords. Example: query=\"Urlaubsantrag Ablauf\""),
                McpToolParameter.integer(PARAM_LIMIT, false, "How many hits to return (default "
                        + settings.defaultMaxResults() + "). Example: limit=4"));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String query = call.getString(PARAM_QUERY);
        if (ToolText.isBlank(query)) {
            return McpToolResult.error("Missing argument: query — example: query=\"RSL in IAR\", limit=4");
        }
        long requested = call.getInteger(PARAM_LIMIT, settings.defaultMaxResults());
        if (requested < 1) {
            return McpToolResult.error("limit must be at least 1 — example: query=\"" + ToolText.oneLine(query, 80)
                    + "\", limit=4");
        }
        int limit = (int) Math.min(requested, Math.max(1, retrieval.settings().maxResults()));
        String text = query.trim();
        // Die Quellen von jetzt (auch zur Laufzeit hinzugefügte), davon nur die angehakten: abgewählte werden
        // weder live gefragt noch im Index durchsucht.
        KnowledgeSourceCatalog catalog = catalogs.get();
        List<KnowledgeSourceId> sources = new ArrayList<KnowledgeSourceId>();
        for (KnowledgeSourceId id : catalog.ids()) {
            if (enabled.test(id)) {
                sources.add(id);
            }
        }
        if (sources.isEmpty()) {
            return McpToolResult.ok("Query: " + ToolText.oneLine(text, 200)
                    + "\nNo knowledge source is enabled. Ask the user to check a source in the drawer.");
        }

        SearchDrivenIndexingUseCase.Result found;
        try {
            found = live.search(PREFETCH_CHANNEL, catalog, sources, text, Math.max(limit, LIVE_HITS));
        } catch (RuntimeException e) {
            found = null; // Live-Suche ist eine Ergänzung; der Index antwortet trotzdem.
        }
        List<RetrievedChunk> hits;
        boolean indexFailed = false;
        try {
            RetrievalResult result = retrieval.retrieve(text, sources);
            hits = result.hits();
        } catch (RuntimeException e) { // auch KnowledgeRetrievalException: alle Suchpfade ausgefallen
            hits = Collections.emptyList();
            indexFailed = true;
        }
        return McpToolResult.ok(render(text, limit, hits, found, indexFailed));
    }

    private String render(String query, int limit, List<RetrievedChunk> hits, SearchDrivenIndexingUseCase.Result found,
                          boolean indexFailed) {
        StringBuilder out = new StringBuilder();
        out.append("Query: ").append(ToolText.oneLine(query, 200)).append('\n');
        if (found != null) {
            for (Map.Entry<KnowledgeSourceId, String> failure : found.failures().entrySet()) {
                out.append("Note: source ").append(failure.getKey().value()).append(" could not be searched (")
                        .append(failure.getValue()).append(").\n");
            }
        }
        if (indexFailed) {
            out.append("Note: the local index could not be searched; only live hits are listed.\n");
        }
        int maxChars = settings.maxResponseChars() - ToolText.MARKER_RESERVE;
        Set<KnowledgeResourceId> listed = new HashSet<KnowledgeResourceId>();
        int shown = 0;
        String firstShown = null;
        for (RetrievedChunk hit : hits) {
            if (shown >= limit) {
                break;
            }
            String block = renderHit(shown + 1, hit);
            if (out.length() + block.length() > maxChars) {
                break;
            }
            out.append(block);
            listed.add(hit.resource().id());
            if (firstShown == null) {
                firstShown = hit.resource().id().value();
            }
            shown++;
        }
        if (found != null) {
            for (FoundResource resource : found.hits()) {
                if (shown >= limit) {
                    break;
                }
                if (!listed.add(resource.resourceId())) {
                    continue;
                }
                String block = renderLive(shown + 1, resource);
                if (out.length() + block.length() > maxChars) {
                    break;
                }
                out.append(block);
                if (firstShown == null) {
                    firstShown = resource.resourceId().value();
                }
                shown++;
            }
        }
        if (shown == 0) {
            out.append("No hits. Try other words — example: query=\"").append(ToolText.oneLine(query, 60))
                    .append(" Anleitung\"");
        } else {
            out.append("\nRead a hit completely with knowledge_read — example: id=\"")
                    .append(firstShown).append("\"");
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
        block.append('\n').append("id: ").append(resource.id().value()).append('\n');
        if (resource.location().isPresent()) {
            block.append("url: ").append(resource.location().get()).append('\n');
        }
        block.append("passage: ").append(live.prefetch().indexing().passageId(hit.chunk().id()).value()).append('\n');
        block.append("source: ").append(resource.sourceId().value()).append('\n');
        block.append(ToolText.snippet(hit.chunk().text(), settings.snippetChars())).append('\n');
        return block.toString();
    }

    private String renderLive(int number, FoundResource resource) {
        StringBuilder block = new StringBuilder();
        block.append('\n').append('[').append(number).append("] ")
                .append(ToolText.oneLine(resource.title(), TITLE_CHARS)).append('\n');
        block.append("id: ").append(resource.resourceId().value()).append('\n');
        if (resource.url() != null) {
            block.append("url: ").append(resource.url()).append('\n');
        }
        block.append("source: ").append(resource.sourceId().value()).append(" (not read yet)\n");
        if (!resource.snippet().isEmpty()) {
            block.append(ToolText.snippet(resource.snippet(), settings.snippetChars())).append('\n');
        }
        return block.toString();
    }
}
