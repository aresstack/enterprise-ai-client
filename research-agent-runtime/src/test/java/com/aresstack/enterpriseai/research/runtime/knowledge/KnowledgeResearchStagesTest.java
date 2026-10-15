package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The Research Agent runs its discovery stages in order: a later stage runs only when the evidence read so far does
 * not answer the task, and a stage whose tool is not offered is skipped.
 */
public class KnowledgeResearchStagesTest {

    private static final String TASK = "Was ist ZYX?";

    /** An unplanned stage asking a tool named {@code cheap_lookup} with the literal term of the task. */
    private static final DiscoveryStrategy CHEAP = new DiscoveryStrategy() {
        @Override
        public String name() {
            return "cheap";
        }

        @Override
        public String planningRules() {
            return "";
        }

        @Override
        public boolean accepts(String query) {
            return true;
        }

        @Override
        public String fallbackQuery(String task) {
            return "ZYX";
        }

        @Override
        public String tool() {
            return "cheap_lookup";
        }

        @Override
        public boolean planned() {
            return false;
        }

        @Override
        public List<String> directQueries(String task) {
            return Collections.singletonList("ZYX");
        }

        @Override
        public String label(String query) {
            return "cheap: " + query;
        }
    };

    private static final class Tools implements ToolInvoker {
        final List<String> calls = new ArrayList<String>();
        boolean cheapOffered = true;

        @Override
        public String call(String toolName, Map<String, Object> args) throws ToolFailure, EndpointUnavailable {
            calls.add(toolName + ":" + (args.containsKey("query") ? args.get("query") : args.get("id")));
            if ("cheap_lookup".equals(toolName)) {
                if (!cheapOffered) {
                    throw new EndpointUnavailable("cheap_lookup is not offered");
                }
                return "[1] Glossar\nid: g1\nsource: files\nZYX steht für Zentrale Y-Exchange.\n";
            }
            if (KnowledgeResearch.SEARCH_TOOL.equals(toolName)) {
                return "Query: " + args.get("query") + "\n\n[1] Handbuch\nid: h1\nsource: wiki\nZYX im Handbuch.\n";
            }
            if (KnowledgeResearch.READ_TOOL.equals(toolName)) {
                return "title: x\nid: " + args.get("id") + "\n\nZYX steht für Zentrale Y-Exchange.";
            }
            throw new ToolFailure("unknown " + toolName);
        }
    }

    private static final class Model implements MainModelChat {
        final List<String> answers;
        int calls;

        Model(String... answers) {
            this.answers = new ArrayList<String>(Arrays.asList(answers));
        }

        @Override
        public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
            calls++;
            return answers.isEmpty() ? MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE, "-")
                    : MainModelChatResult.ok(answers.remove(0));
        }

        @Override
        public String modelName() {
            return "scripted";
        }
    }

    private static EvidenceJudge judge(final boolean sufficient) {
        return new EvidenceJudge() {
            @Override
            public boolean sufficient(String task, List<KnowledgeHit> read, List<String> excerpts) {
                return sufficient;
            }
        };
    }

    @Test
    public void sufficientEvidenceFromTheFirstStageEndsTheResearch() throws Exception {
        Tools tools = new Tools();
        Model model = new Model("ZYX steht für Zentrale Y-Exchange [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model,
                Arrays.asList(CHEAP, new LexicalDiscoveryStrategy()), judge(true)).research(TASK);

        assertEquals(Arrays.asList("cheap_lookup:ZYX", "knowledge_read:g1"), tools.calls);
        assertEquals("no planning, only the answer", 1, model.calls);
        assertEquals(Arrays.asList("cheap: ZYX"), result.queries());
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
    }

    @Test
    public void insufficientEvidenceFallsThroughToTheNextStage() throws Exception {
        Tools tools = new Tools();
        Model model = new Model("{\"queries\": [\"ZYX Handbuch\"]}", "Antwort [1] [2].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model,
                Arrays.asList(CHEAP, new LexicalDiscoveryStrategy()), judge(false)).research(TASK);

        assertEquals(Arrays.asList("cheap_lookup:ZYX", "knowledge_read:g1", "knowledge_search:ZYX Handbuch",
                "knowledge_read:h1"), tools.calls);
        assertEquals(Arrays.asList("cheap: ZYX", "ZYX Handbuch"), result.queries());
        assertEquals("both stages' evidence is kept", 2, result.sources().size());
    }

    @Test
    public void aStageWhoseToolIsNotOfferedIsSkipped() throws Exception {
        Tools tools = new Tools();
        tools.cheapOffered = false;
        Model model = new Model("{\"queries\": [\"ZYX Handbuch\"]}", "Antwort [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model,
                Arrays.asList(CHEAP, new LexicalDiscoveryStrategy()), judge(true)).research(TASK);

        assertEquals(Arrays.asList("ZYX Handbuch"), result.queries());
        assertTrue(result.notes().isEmpty());
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
    }

    @Test(expected = ToolInvoker.EndpointUnavailable.class)
    public void withoutAnyReachableStageTheOutageIsReported() throws Exception {
        ToolInvoker none = new ToolInvoker() {
            @Override
            public String call(String toolName, Map<String, Object> args) throws EndpointUnavailable {
                throw new EndpointUnavailable("down");
            }
        };
        new KnowledgeResearch(none, new Model(), Arrays.asList(CHEAP, new LexicalDiscoveryStrategy()), judge(true))
                .research(TASK);
    }
}
