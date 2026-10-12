package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.help.HelpCatalog;
import com.aresstack.enterpriseai.proasteion.help.HelpHit;
import com.aresstack.enterpriseai.proasteion.help.HelpSearchUseCase;
import com.aresstack.enterpriseai.proasteion.help.HelpText;
import com.aresstack.enterpriseai.proasteion.help.HelpTopic;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.util.List;
import java.util.Locale;

/** {@code app_help_search(query, limit?)}: BM25 over the bundled help; without hits it lists the table of contents. */
final class AppHelpSearchTool implements McpToolHandler {

    static final String NAME = "app_help_search";
    static final String PARAM_QUERY = "query";
    static final String PARAM_LIMIT = "limit";
    static final String EXAMPLE = "query=\"Stimme der Sprachausgabe ändern\", limit=4";

    private final HelpSearchUseCase search;
    private final HelpCatalog catalog;
    private final AppHelpToolLimits limits;

    AppHelpSearchTool(HelpSearchUseCase search, HelpCatalog catalog, AppHelpToolLimits limits) {
        this.search = search;
        this.catalog = catalog;
        this.limits = limits;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Search the built-in documentation of the Enterprise AI Client, the application you are running in "
                        + "(it ships with the installed version). Use this tool when the user asks how to use the "
                        + "application, where to find a setting, what a feature does, how setup or configuration "
                        + "works, or how to solve a problem with the application. Prefer this documentation over "
                        + "assumptions about the UI or behavior of the application. It does not search the user's "
                        + "own knowledge sources (that is knowledge_search). Then read a hit with "
                        + AppHelpReadTool.NAME + ". Example: " + EXAMPLE + ".",
                this,
                McpToolParameter.string(PARAM_QUERY, true, "Words of the question, in German or English. "
                        + "Example: " + EXAMPLE),
                McpToolParameter.integer(PARAM_LIMIT, false, "How many chapters to return (default "
                        + limits.defaultMaxResults() + "). Example: limit=4"));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String query = call.getString(PARAM_QUERY);
        if (query == null || query.trim().isEmpty()) {
            return McpToolResult.error("Missing argument: query — example: " + EXAMPLE);
        }
        long requested = call.getInteger(PARAM_LIMIT, limits.defaultMaxResults());
        if (requested < 1) {
            return McpToolResult.error("limit must be at least 1 — example: " + EXAMPLE);
        }
        int limit = (int) Math.min(requested, catalog.size());
        List<HelpHit> hits = search.search(query, limit, limits.snippetChars());
        StringBuilder out = new StringBuilder();
        out.append("query: ").append(oneLine(query)).append('\n');
        if (hits.isEmpty()) {
            out.append("hits: 0\n\nNo help chapter matches. Try other words, for example the name of a settings tab "
                    + "or the label of a button. Chapters of the built-in help:\n");
            for (HelpTopic topic : catalog.topics()) {
                out.append("- ").append(topic.qualifiedId()).append(": ").append(topic.title()).append('\n');
            }
            return McpToolResult.ok(HelpText.truncate(out.toString(), limits.maxResponseChars()));
        }
        out.append("hits: ").append(hits.size()).append('\n');
        int rank = 1;
        for (HelpHit hit : hits) {
            out.append('\n').append('[').append(rank++).append("] ").append(hit.topic().title()).append('\n');
            out.append("id: ").append(hit.sectionId()).append('\n');
            if (!hit.section().isIntroduction()) {
                out.append("section: ").append(hit.section().heading()).append('\n');
            }
            out.append("score: ").append(String.format(Locale.ROOT, "%.2f", hit.score())).append('\n');
            out.append(hit.snippet()).append('\n');
        }
        out.append("\nRead a hit with ").append(AppHelpReadTool.NAME).append(" and the id as shown; drop the #anchor "
                + "to read the whole chapter.");
        return McpToolResult.ok(HelpText.truncate(out.toString(), limits.maxResponseChars()));
    }

    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
