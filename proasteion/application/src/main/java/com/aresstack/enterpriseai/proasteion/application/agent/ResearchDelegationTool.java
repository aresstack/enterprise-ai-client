package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeHit;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeResearch;
import com.aresstack.enterpriseai.research.runtime.knowledge.KnowledgeResearchResult;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The research boundary of the normal chat: {@value #NAME}{@code (task)} hands a research task in unrestricted
 * natural language to the Research Agent ({@link KnowledgeResearch}), which formulates the search expressions, uses
 * the low-level knowledge tools and returns findings with their sources. The chat never needs to know whether a
 * source searches lexically, by hierarchy or semantically.
 *
 * <p>Same agent core as the agent process, run in-process: its {@link ToolInvoker} calls the handlers of the
 * existing knowledge tools ({@code knowledge_search}, {@code knowledge_read}) directly, its {@link MainModelChat} is
 * the client's chat port ({@link ChatPortMainModelChat}). No second protocol, no source-specific code.
 */
public final class ResearchDelegationTool implements McpToolHandler {

    public static final String NAME = "knowledge_research";
    static final String PARAM_TASK = "task";

    private final Function<String, McpToolContribution> knowledgeTools;
    private final MainModelChat model;

    /**
     * @param knowledgeTools finds a knowledge tool by name per call ({@code null} when it is not offered)
     * @param model          the model the Research Agent plans and writes with
     */
    public ResearchDelegationTool(Function<String, McpToolContribution> knowledgeTools, MainModelChat model) {
        if (knowledgeTools == null || model == null) {
            throw new IllegalArgumentException("knowledgeTools and model must not be null");
        }
        this.knowledgeTools = knowledgeTools;
        this.model = model;
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
        try {
            KnowledgeResearchResult result = new KnowledgeResearch(invoker, model).research(task);
            return McpToolResult.ok(result.render(), invoker.contextOf(result.sources()));
        } catch (ToolInvoker.EndpointUnavailable unavailable) {
            return McpToolResult.error("The knowledge sources are not available: " + unavailable.getMessage());
        }
    }

    /**
     * The knowledge tools of the host, called in-process through their handlers. Keeps the structured context
     * resources of each successful {@code knowledge_read}, so the outer result names the resources actually used.
     */
    private final class KnowledgeToolInvoker implements ToolInvoker {

        private final Map<String, List<McpContextResource>> readContext =
                new LinkedHashMap<String, List<McpContextResource>>();

        /** The context resources of the given read resources, deduplicated by source and resource id. */
        List<McpContextResource> contextOf(List<KnowledgeHit> sources) {
            Map<String, McpContextResource> unique = new LinkedHashMap<String, McpContextResource>();
            for (KnowledgeHit source : sources) {
                List<McpContextResource> resources = readContext.get(source.id);
                if (resources == null) {
                    continue;
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
            if (KnowledgeResearch.READ_TOOL.equals(toolName) && args != null && args.get("id") != null) {
                readContext.put(String.valueOf(args.get("id")), result.contextResources());
            }
            return result.getText();
        }
    }
}
