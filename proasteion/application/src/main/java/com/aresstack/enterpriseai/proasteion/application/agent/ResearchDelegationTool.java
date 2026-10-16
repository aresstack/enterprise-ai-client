package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.astu.acropolis.mcp.KnowledgeMcpTools;
import com.aresstack.enterpriseai.astu.acropolis.rag.FunnelRecorder;
import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.knowledge.HierarchyExploration;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeHit;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeResearch;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeResearchResult;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeSearchRunPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The research boundary of the normal chat: {@value #NAME}{@code (task)} hands a research task in unrestricted
 * natural language to the Research Agent ({@link KnowledgeResearch}), which formulates the search expressions, uses
 * the low-level knowledge tools and returns findings with their sources. The chat never needs to know whether a
 * source searches lexically, by hierarchy or semantically.
 *
 * <p>Same agent core as the agent process, run in-process: its {@link ToolInvoker} calls the handlers of the
 * existing knowledge tools ({@code knowledge_search}, {@code knowledge_read}, {@code knowledge_tree},
 * {@code knowledge_read_many}) directly, its {@link MainModelChat} is
 * the client's chat port ({@link ChatPortMainModelChat}). No second protocol, no source-specific code.
 */
public final class ResearchDelegationTool implements McpToolHandler {

    public static final String NAME = KnowledgeMcpTools.KNOWLEDGE_RESEARCH;
    static final String PARAM_TASK = "task";
    /** The run log line of {@code knowledge_search} (#153). */
    private static final Pattern SEARCH_RUN = Pattern.compile("(?m)^Search run: (\\S+)");

    private final Function<String, McpToolContribution> knowledgeTools;
    private final MainModelChat model;
    private final KnowledgeSearchRunPort runs;

    /**
     * @param knowledgeTools finds a knowledge tool by name per call ({@code null} when it is not offered)
     * @param model          the model the Research Agent plans and writes with
     */
    public ResearchDelegationTool(Function<String, McpToolContribution> knowledgeTools, MainModelChat model) {
        this(knowledgeTools, model, null);
    }

    /**
     * @param runs the run log: the funnel of each research (#155) reads the live search runs from it and is stored
     *             there; {@code null} counts without live search runs and stores nothing
     */
    public ResearchDelegationTool(Function<String, McpToolContribution> knowledgeTools, MainModelChat model,
                                  KnowledgeSearchRunPort runs) {
        if (knowledgeTools == null || model == null) {
            throw new IllegalArgumentException("knowledgeTools and model must not be null");
        }
        this.knowledgeTools = knowledgeTools;
        this.model = model;
        this.runs = runs;
    }

    public McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Delegate a research task to the Research Agent, which searches the user's knowledge sources (e.g. "
                        + "Confluence, wiki, files), reads the relevant documents and returns findings with their "
                        + "sources. Describe the task in natural language, as precisely as the user said it; the "
                        + "agent formulates the search queries itself. Use it when the answer needs internal or "
                        + "organization-specific information (acronym, internal system or project name, unit, "
                        + "document, process), even if you know a public meaning, and when the user asks you to "
                        + "search or look something up. Answer from the findings only and cite the sources. "
                        + "Example: task=\"Wie wird in HaVdB der PoC mit Hashicorp Vault für PIMA beschrieben?\".",
                this,
                McpToolParameter.string(PARAM_TASK, true, "The research task in natural language. Example: "
                        + "task=\"Finde heraus, welche Bedeutung IRR im internen Unternehmenswissen hat.\""));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String task = call.getString(PARAM_TASK);
        if (task == null || task.trim().isEmpty()) {
            return McpToolResult.error("Missing argument: task — example: task=\"Was bedeutet RSL in IAR?\"");
        }
        KnowledgeToolInvoker invoker = new KnowledgeToolInvoker();
        FunnelRecorder funnel = new FunnelRecorder(runs);
        KnowledgeResearchResult result = null;
        try {
            result = new KnowledgeResearch(invoker, model).research(task);
            List<McpContextResource> provenance = invoker.contextOf(result.sources());
            KnowledgeFunnel counted = count(funnel, invoker, result, provenance);
            return McpToolResult.ok(result.render() + "\n" + funnelLine(counted), provenance);
        } catch (ToolInvoker.EndpointUnavailable unavailable) {
            return McpToolResult.error("The knowledge sources are not available: " + unavailable.getMessage());
        } finally {
            if (result == null) {
                for (String run : invoker.searchRuns) {
                    funnel.liveSearch(run);
                }
                funnel.finish(false); // failed: the live searches that ran stay counted
            }
        }
    }

    /**
     * The funnel of one research (#155): live search candidates discovered; every hit the knowledge tools returned
     * examined (each was scored by retrieval or the reranker); the read and judged pages relevant; the resources in
     * the provenance used.
     */
    private static KnowledgeFunnel count(FunnelRecorder funnel, KnowledgeToolInvoker invoker,
                                         KnowledgeResearchResult result, List<McpContextResource> provenance) {
        for (String run : invoker.searchRuns) {
            funnel.liveSearch(run);
        }
        funnel.examined(resources(result.sources()));
        funnel.examined(resources(result.unread()));
        funnel.relevant(resources(result.sources()));
        List<FunnelResource> used = new ArrayList<FunnelResource>();
        for (McpContextResource resource : provenance) {
            if (!resource.sourceId().trim().isEmpty() && !resource.resourceId().trim().isEmpty()) {
                used.add(new FunnelResource(resource.sourceId(), resource.resourceId()));
            }
        }
        funnel.used(used);
        return funnel.finish(result.outcome() != KnowledgeResearchResult.Outcome.FAILED);
    }

    private static List<FunnelResource> resources(List<KnowledgeHit> hits) {
        List<FunnelResource> resources = new ArrayList<FunnelResource>();
        for (KnowledgeHit hit : hits) {
            if (!hit.source.trim().isEmpty() && !hit.id.trim().isEmpty()) {
                resources.add(new FunnelResource(hit.source, hit.id));
            }
        }
        return resources;
    }

    static String funnelLine(KnowledgeFunnel funnel) {
        return "Research funnel: " + funnel.id() + " (" + funnel.discovered() + " discovered, "
                + funnel.examined().size() + " examined, " + funnel.relevant().size() + " relevant, "
                + funnel.used().size() + " used)";
    }

    /**
     * The knowledge tools of the host, called in-process through their handlers. Keeps the structured context
     * resources of each successful {@code knowledge_read}, so the outer result names the resources actually used.
     */
    private final class KnowledgeToolInvoker implements ToolInvoker {

        private final Map<String, List<McpContextResource>> readContext =
                new LinkedHashMap<String, List<McpContextResource>>();
        /** Context resources of bundled reads ({@code knowledge_read_many}), by resource id. */
        private final Map<String, McpContextResource> bundleContext = new LinkedHashMap<String, McpContextResource>();
        /** The run log ids named by the {@code knowledge_search} results, in call order. */
        private final List<String> searchRuns = new ArrayList<String>();

        /** The context resources of the given read resources, deduplicated by source and resource id. */
        List<McpContextResource> contextOf(List<KnowledgeHit> sources) {
            Map<String, McpContextResource> unique = new LinkedHashMap<String, McpContextResource>();
            for (KnowledgeHit source : sources) {
                List<McpContextResource> resources = readContext.get(source.id);
                if (resources == null) {
                    McpContextResource bundled = bundleContext.get(source.id);
                    if (bundled == null) {
                        continue;
                    }
                    resources = java.util.Collections.singletonList(bundled);
                }
                for (McpContextResource resource : resources) {
                    String key = resource.sourceId() + "\u0000" + resource.resourceId();
                    if (!unique.containsKey(key)) {
                        unique.put(key, resource);
                    }
                }
            }
            return new ArrayList<McpContextResource>(unique.values());
        }

        @Override
        public String call(String toolName, Map<String, Object> args) throws ToolFailure, EndpointUnavailable {
            McpToolContribution tool = knowledgeTools.apply(toolName);
            if (tool == null) {
                throw new EndpointUnavailable(toolName + " is not offered (check the tools in the settings)");
            }
            McpToolResult result;
            try {
                result = tool.getHandler().invoke(new McpToolCall(toolName, args));
            } catch (RuntimeException failed) {
                throw new ToolFailure(failed.getClass().getSimpleName() + ": " + failed.getMessage());
            }
            if (result == null) {
                throw new ToolFailure("no result from " + toolName);
            }
            if (result.isError()) {
                throw new ToolFailure(result.getText());
            }
            if (KnowledgeResearch.SEARCH_TOOL.equals(toolName) && result.getText() != null) {
                Matcher run = SEARCH_RUN.matcher(result.getText());
                if (run.find()) {
                    searchRuns.add(run.group(1));
                }
            }
            if (KnowledgeResearch.READ_TOOL.equals(toolName) && args != null && args.get("id") != null) {
                readContext.put(String.valueOf(args.get("id")), result.contextResources());
            }
            if (HierarchyExploration.READ_MANY_TOOL.equals(toolName)) {
                // the bundle names its resources, not the requested ids: keep them by resource id
                for (McpContextResource resource : result.contextResources()) {
                    if (!bundleContext.containsKey(resource.resourceId())) {
                        bundleContext.put(resource.resourceId(), resource);
                    }
                }
            }
            return result.getText();
        }
    }
}
