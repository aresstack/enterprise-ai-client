package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.FoundResource;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievalResult;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrievedChunk;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
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
    private final Predicate<SourceAddress> enabled;
    private final KnowledgeToolSettings settings;
    /** Only the local index, no live source search (the handler of {@code knowledge_index_search}). */
    private final boolean indexOnly;

    KnowledgeSearchTool(RetrieveKnowledgeUseCase retrieval, SearchDrivenIndexingUseCase live,
                        Supplier<KnowledgeSourceCatalog> catalogs, Predicate<SourceAddress> enabled,
                        KnowledgeToolSettings settings) {
        this(retrieval, live, catalogs, enabled, settings, false);
    }

    KnowledgeSearchTool(RetrieveKnowledgeUseCase retrieval, SearchDrivenIndexingUseCase live,
                        Supplier<KnowledgeSourceCatalog> catalogs, Predicate<SourceAddress> enabled,
                        KnowledgeToolSettings settings, boolean indexOnly) {
        this.indexOnly = indexOnly;
        this.retrieval = retrieval;
        this.live = live;
        this.catalogs = catalogs;
        this.enabled = enabled;
        this.settings = settings;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Search the user's knowledge sources (e.g. Confluence, wiki) and the local knowledge index. Hits "
                        + "are loaded and indexed in the background automatically. Use this when the answer needs "
                        + "subject-matter, internal or organization-specific information, then read a hit with "
                        + "knowledge_read. Use it when the user wants to know about a term that is not resolved in "
                        + "the conversation and may be organization-specific (acronym, internal system or project "
                        + "name, unit, document, process), even if you know a public meaning, and when the user asks "
                        + "you to search or look something up. Not for greetings, opinions, corrections, questions "
                        + "about yourself or the application, unambiguous general knowledge, or a word that the "
                        + "client analysis marks as a possible speech recognition error. "
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
        int limit = (int) Math.min(requested, Math.max(1, retrieval.maxResults()));
        String text = query.trim();
        // Die Quellen von jetzt (auch zur Laufzeit hinzugefügte), davon nur die angehakten: abgewählte werden
        // weder live gefragt noch im Index durchsucht.
        KnowledgeSourceCatalog catalog = catalogs.get();
        List<SourceAddress> sources = new ArrayList<SourceAddress>();
        for (SourceAddress id : catalog.ids()) {
            if (enabled.test(id)) {
                sources.add(id);
            }
        }
        if (sources.isEmpty()) {
            return McpToolResult.ok("Query: " + ToolText.oneLine(text, 200)
                    + "\nNo knowledge source is enabled. Ask the user to check a source in the drawer.");
        }

        SearchDrivenIndexingUseCase.Result found = null;
        if (!indexOnly) {
            try {
                found = live.search(PREFETCH_CHANNEL, catalog, sources, text, Math.max(limit, LIVE_HITS));
            } catch (RuntimeException e) {
                found = null; // Live-Suche ist eine Ergänzung; der Index antwortet trotzdem.
            }
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
        if (indexOnly && indexFailed) {
            // without the index this tool has nothing to answer: a failure, never "no hits"
            return McpToolResult.error("The local knowledge index could not be searched.");
        }
        return McpToolResult.ok(render(text, limit, hits, found, indexFailed));
    }

    private String render(String query, int limit, List<RetrievedChunk> hits, SearchDrivenIndexingUseCase.Result found,
                          boolean indexFailed) {
        StringBuilder out = new StringBuilder();
        out.append("Query: ").append(ToolText.oneLine(query, 200)).append('\n');
        if (found != null && found.runRecorded()) {
            // The run keeps every live candidate (#153); candidates are not sources until read and judged.
            out.append("Search run: ").append(found.recordedRunId()).append(" (")
                    .append(found.candidateCount())
                    .append(" live candidates recorded; \"run candidate\" is a hit's number in that run)\n");
        }
        if (found != null) {
            for (Map.Entry<SourceAddress, String> failure : found.failures().entrySet()) {
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
                String block = renderLive(shown + 1, resource, found);
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
            out.append("No hits. Do not retry with variants or parts of the same terms. Check first whether the "
                    + "message was understood correctly (typo, speech recognition error, reference to earlier "
                    + "messages); if the meaning is unclear, ask the user. Search again only with clearly different "
                    + "terms.");
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
        java.util.Optional<KnowledgePassageId> passage =
                live.prefetch().indexing().passageId(resource, hit.chunk().id());
        if (passage.isPresent()) {
            block.append("passage: ").append(passage.get().value()).append('\n');
        }
        block.append("source: ").append(resource.sourceId().value()).append('\n');
        block.append(ToolText.snippet(hit.chunk().text(), settings.snippetChars())).append('\n');
        return block.toString();
    }

    private String renderLive(int number, FoundResource resource, SearchDrivenIndexingUseCase.Result found) {
        StringBuilder block = new StringBuilder();
        block.append('\n').append('[').append(number).append("] ")
                .append(ToolText.oneLine(resource.title(), TITLE_CHARS)).append('\n');
        block.append("id: ").append(resource.resourceId().value()).append('\n');
        if (resource.url() != null) {
            block.append("url: ").append(resource.url()).append('\n');
        }
        block.append("source: ").append(resource.sourceId().value()).append(" (not read yet)\n");
        int candidate = found.runRecorded() ? found.candidateNumber(resource.resourceId()) : 0;
        if (candidate > 0) {
            // the run numbers live candidates on their own; the list number above is only this answer's order
            block.append("run candidate: ").append(candidate).append('\n');
        }
        if (!resource.snippet().isEmpty()) {
            block.append(ToolText.snippet(resource.snippet(), settings.snippetChars())).append('\n');
        }
        return block.toString();
    }
}
