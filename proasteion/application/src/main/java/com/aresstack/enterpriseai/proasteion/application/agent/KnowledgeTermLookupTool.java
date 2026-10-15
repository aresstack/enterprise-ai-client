package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymFindings;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymForms;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStorePort;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.knowledge.TermLookupStrategy;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@value #NAME}{@code (term, limit)}: the resources whose text states a long form of the term, from the acronym
 * findings already stored in the metadata catalog ({@link FactStorePort}, {@code ACRONYM_DEFINITION}). A cheap lookup
 * for the Research Agent's first stage, answered in the hit format of {@code knowledge_search}; each hit is read with
 * {@code knowledge_read} before it counts as evidence. Knows no source: the catalog is source-independent.
 */
public final class KnowledgeTermLookupTool implements McpToolHandler {

    public static final String NAME = TermLookupStrategy.TOOL;
    static final String PARAM_LIMIT = "limit";
    private static final int DEFAULT_LIMIT = 6;
    private static final int QUOTE_CHARS = 300;

    private final FactStorePort facts;

    public KnowledgeTermLookupTool(FactStorePort facts) {
        if (facts == null) {
            throw new IllegalArgumentException("facts must not be null");
        }
        this.facts = facts;
    }

    public McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Look up which knowledge resources state the long form of an acronym or short term, from the "
                        + "findings of already analysed documents (no source search). Read a hit with knowledge_read "
                        + "before using it. Example: term=\"IRR\".",
                this,
                McpToolParameter.string(TermLookupStrategy.PARAM_TERM, true,
                        "The acronym or short term, as written. Example: term=\"IRR\""),
                McpToolParameter.integer(PARAM_LIMIT, false, "How many resources to return (default "
                        + DEFAULT_LIMIT + ")."));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String term = call.getString(TermLookupStrategy.PARAM_TERM);
        if (term == null || term.trim().isEmpty()) {
            return McpToolResult.error("Missing argument: term — example: term=\"IRR\"");
        }
        long limit = Math.max(1, call.getInteger(PARAM_LIMIT, DEFAULT_LIMIT));
        String wanted = AcronymForms.normalize(term.trim());
        List<MetadataFact> definitions;
        try {
            definitions = facts.factsOfType(FactTypeId.of(AcronymFindings.ACRONYM_DEFINITION));
        } catch (FactStoreException failed) {
            return McpToolResult.error("The metadata catalog is not available: " + failed.reason());
        }
        // one hit per resource, with every long form it states
        Map<String, StringBuilder> byResource = new LinkedHashMap<String, StringBuilder>();
        for (MetadataFact fact : definitions) {
            JsonObject payload = payload(fact);
            if (payload == null || !wanted.equals(text(payload, "normalized"))) {
                continue;
            }
            String resource = fact.resourceId().value();
            StringBuilder snippet = byResource.get(resource);
            if (snippet == null) {
                if (byResource.size() >= limit) {
                    continue;
                }
                snippet = new StringBuilder();
                byResource.put(resource, snippet);
            }
            String quote = fact.anchor() == null ? "" : fact.anchor().quote();
            String line = quote == null || quote.trim().isEmpty()
                    ? text(payload, "longForm") + " (" + text(payload, "shortForm") + ")"
                    : oneLine(quote, QUOTE_CHARS);
            if (snippet.indexOf(line) < 0) {
                snippet.append(snippet.length() == 0 ? "" : " ").append(line);
            }
        }
        StringBuilder out = new StringBuilder("Term: ").append(oneLine(term, 80)).append('\n');
        if (byResource.isEmpty()) {
            return McpToolResult.ok(out.append("No resource states a long form of this term.").toString());
        }
        int number = 0;
        for (Map.Entry<String, StringBuilder> hit : byResource.entrySet()) {
            // the catalog knows the resource, not its title: knowledge_read names it
            out.append('\n').append('[').append(++number).append("] ").append(hit.getKey()).append('\n')
                    .append("id: ").append(hit.getKey()).append('\n')
                    .append(hit.getValue()).append('\n');
        }
        return McpToolResult.ok(out.toString());
    }

    private static JsonObject payload(MetadataFact fact) {
        try {
            JsonElement parsed = JsonParser.parseString(fact.payload().json());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            return null;
        }
    }

    private static String text(JsonObject payload, String field) {
        JsonElement value = payload.get(field);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString();
    }

    private static String oneLine(String text, int max) {
        String line = text.replaceAll("\\s+", " ").trim();
        return line.length() <= max ? line : line.substring(0, max) + "…";
    }
}
