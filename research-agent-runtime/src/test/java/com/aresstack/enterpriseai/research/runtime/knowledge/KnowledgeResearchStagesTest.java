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

    private static final String IRR_TASK = "Finde heraus, welche Bedeutung IRR im internen Unternehmenswissen hat.";

    /** A term lookup over stored findings, the local index and a lexical search over a few pages. */
    private static final class AcronymTools implements ToolInvoker {
        final List<String> calls = new ArrayList<String>();
        boolean defined = true;
        boolean indexed = false;
        boolean indexOffered = true;

        @Override
        public String call(String toolName, Map<String, Object> args) throws ToolFailure, EndpointUnavailable {
            if (TermLookupStrategy.TOOL.equals(toolName)) {
                calls.add(toolName + ":" + args.get(TermLookupStrategy.PARAM_TERM));
                return defined ? "Term: IRR\n\n[1] wiki:42\nid: wiki:42\nInterne Revisionsrichtlinie (IRR).\n"
                        : "Term: IRR\nNo resource states a long form of this term.";
            }
            if (HierarchyExploration.TREE_TOOL.equals(toolName)) {
                throw new EndpointUnavailable(toolName + " is not offered");
            }
            if (LocalIndexStrategy.TOOL.equals(toolName) && !indexOffered) {
                throw new EndpointUnavailable(toolName + " is not offered");
            }
            calls.add(toolName + ":" + (args.containsKey("query") ? args.get("query") : args.get("id")));
            if (LocalIndexStrategy.TOOL.equals(toolName)) {
                return indexed ? "Query: " + args.get("query") + "\n\n[1] Revisionshandbuch\nid: files:9\n"
                        + "source: files\nDie IRR wird jährlich überprüft.\n" : "Query: " + args.get("query")
                        + "\nNo hits.";
            }
            if (KnowledgeResearch.SEARCH_TOOL.equals(toolName)) {
                return "Query: IRR\n\n[1] Prüfplan 2026\nid: wiki:7\nsource: wiki\nDie IRR gilt ab Mai.\n";
            }
            if ("files:9".equals(args.get("id"))) {
                return "title: Revisionshandbuch\nid: files:9\nurl: file:///docs/revision.md\n\n"
                        + "Die IRR wird jährlich überprüft.";
            }
            if ("wiki:42".equals(args.get("id"))) {
                return "title: Richtlinien\nid: wiki:42\nurl: https://wiki.example/42\n\n"
                        + "Die Interne Revisionsrichtlinie (IRR) regelt Prüfungen.";
            }
            return "title: Prüfplan 2026\nid: wiki:7\n\nDie IRR gilt ab Mai.";
        }
    }

    @Test
    public void anAcronymTaskIsAnsweredByTheTermLookupAlone() throws Exception {
        AcronymTools tools = new AcronymTools();
        Model model = new Model("JA", "IRR steht für Interne Revisionsrichtlinie [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(IRR_TASK);

        assertEquals("one lookup and one read, no search", Arrays.asList("knowledge_term_lookup:IRR",
                "knowledge_read:wiki:42"), tools.calls);
        assertEquals("no planning: evidence check and answer", 2, model.calls);
        assertEquals(Arrays.asList("term: IRR"), result.queries());
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
        KnowledgeHit source = result.sources().get(0);
        assertEquals("wiki:42", source.id);
        assertEquals("the read names the resource", "Richtlinien", source.title);
        assertEquals("https://wiki.example/42", source.url);
        assertTrue(result.render(), result.render().contains("[1] Richtlinien"));
    }

    @Test
    public void withoutAStoredDefinitionTheTaskFallsThroughToTheLexicalStage() throws Exception {
        AcronymTools tools = new AcronymTools();
        tools.defined = false;
        Model model = new Model("{\"queries\": [\"IRR\"]}", "Die IRR gilt ab Mai [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(IRR_TASK);

        assertEquals("term lookup, both index lookups without hits, then the live search",
                Arrays.asList("knowledge_term_lookup:IRR", "knowledge_index_search:" + IRR_TASK,
                        "knowledge_index_search:IRR", "knowledge_search:IRR", "knowledge_read:wiki:7"), tools.calls);
        assertEquals(Arrays.asList("term: IRR", "index: " + IRR_TASK, "index: IRR", "IRR"), result.queries());
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
        assertEquals("wiki:7", result.sources().get(0).id);
    }

    @Test
    public void aTaskAnsweredFromTheLocalIndexNeverReachesTheLiveSource() throws Exception {
        AcronymTools tools = new AcronymTools();
        tools.defined = false;
        tools.indexed = true;
        Model model = new Model("JA", "Die IRR wird jährlich überprüft [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(IRR_TASK);

        assertEquals("the task itself goes to the index first, then the hit is read; no live search",
                Arrays.asList("knowledge_term_lookup:IRR", "knowledge_index_search:" + IRR_TASK,
                        "knowledge_read:files:9"), tools.calls);
        assertEquals("no planning: evidence check and answer", 2, model.calls);
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
        KnowledgeHit source = result.sources().get(0);
        assertEquals("files:9", source.id);
        assertEquals("Revisionshandbuch", source.title);
        assertEquals("file:///docs/revision.md", source.url);
    }

    @Test
    public void insufficientLocalEvidenceFallsThroughToTheLexicalLiveStage() throws Exception {
        AcronymTools tools = new AcronymTools();
        tools.defined = false;
        tools.indexed = true;
        Model model = new Model("NEIN", "NEIN", "{\"queries\": [\"IRR\"]}", "Die IRR gilt ab Mai [2].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(IRR_TASK);

        assertEquals("the local hit is read once; the live search follows",
                Arrays.asList("knowledge_term_lookup:IRR", "knowledge_index_search:" + IRR_TASK,
                        "knowledge_read:files:9", "knowledge_index_search:IRR", "knowledge_search:IRR",
                        "knowledge_read:wiki:7"), tools.calls);
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
        assertEquals(Arrays.asList("files:9", "wiki:7"), Arrays.asList(result.sources().get(0).id,
                result.sources().get(1).id));
    }

    @Test
    public void withoutTheIndexToolTheLocalStageIsSkipped() throws Exception {
        AcronymTools tools = new AcronymTools();
        tools.defined = false;
        tools.indexOffered = false;
        Model model = new Model("{\"queries\": [\"IRR\"]}", "Die IRR gilt ab Mai [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(IRR_TASK);

        assertEquals(Arrays.asList("knowledge_term_lookup:IRR", "knowledge_search:IRR", "knowledge_read:wiki:7"),
                tools.calls);
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
    }

    @Test
    public void theLocalStageAsksWithTheTaskAndWithItsLiteralTerms() {
        assertEquals(Arrays.asList(IRR_TASK, "IRR"), new LocalIndexStrategy().directQueries(IRR_TASK));
        assertEquals(Arrays.asList("IRR"), new LocalIndexStrategy().directQueries("IRR"));
        assertTrue(new LocalIndexStrategy().directQueries("  ").isEmpty());
    }

    @Test
    public void onlyAcronymLikeTermsAreLookedUp() {
        assertEquals(Arrays.asList("IRR"), TermLookupStrategy.terms(IRR_TASK));
        assertEquals(Arrays.asList("HaVdB", "PoC", "PIMA"), TermLookupStrategy.terms(
                "Wie wird in HaVdB der PoC mit Hashicorp Vault für PIMA beschrieben?"));
        assertEquals(Arrays.asList("BS-PIMA"), TermLookupStrategy.terms("Was macht BS-PIMA?"));
        assertTrue(TermLookupStrategy.terms("Wie beantrage ich Urlaub?").isEmpty());
    }

    @Test
    public void anEmptyTermLookupDoesNotHideAFailedSourceSearch() throws Exception {
        ToolInvoker tools = new ToolInvoker() {
            @Override
            public String call(String toolName, Map<String, Object> args) throws ToolFailure {
                if (TermLookupStrategy.TOOL.equals(toolName)) {
                    return "Term: IRR\nNo resource states a long form of this term.";
                }
                throw new ToolFailure("source timeout");
            }
        };
        Model model = new Model("{\"queries\": [\"IRR\"]}");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(IRR_TASK);

        assertEquals(KnowledgeResearchResult.Outcome.NO_EVIDENCE, result.outcome());
        assertEquals(Arrays.asList("index: " + IRR_TASK + " failed: source timeout",
                "index: IRR failed: source timeout", "IRR failed: source timeout"), result.notes());
        String rendered = result.render();
        assertTrue(rendered, rendered.contains("not every knowledge source could be searched"));
        assertTrue(rendered, !rendered.contains("No hits in the enabled knowledge sources"));
    }
}
