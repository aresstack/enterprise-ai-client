package com.aresstack.enterpriseai.research.runtime.knowledge;

import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;
import com.aresstack.enterpriseai.research.runtime.team.ChatMessage;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChatResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The Research Agent mediates a natural-language research task into short lexical queries before it reaches the
 * knowledge tools, always searches, reads what it found and answers only from what it read.
 */
public class KnowledgeResearchTest {

    private static final String LONG_REQUEST = "Wie wird in HaVdB der PoC mit Hashicorp Vault für PIMA beschrieben?";

    /** A lexical source like a full-text search: a page matches only if it contains the whole query phrase. */
    private static final class LexicalKnowledgeTools implements ToolInvoker {
        final Map<String, String[]> pages = new LinkedHashMap<String, String[]>();
        final List<String> searches = new ArrayList<String>();
        final List<String> reads = new ArrayList<String>();

        LexicalKnowledgeTools page(String id, String title, String text) {
            pages.put(id, new String[]{title, text});
            return this;
        }

        @Override
        public String call(String toolName, Map<String, Object> args) throws ToolFailure {
            if (KnowledgeResearch.SEARCH_TOOL.equals(toolName)) {
                String query = String.valueOf(args.get("query"));
                searches.add(query);
                StringBuilder out = new StringBuilder("Query: " + query + "\n");
                int n = 0;
                for (Map.Entry<String, String[]> page : pages.entrySet()) {
                    String haystack = (page.getValue()[0] + " " + page.getValue()[1]).toLowerCase();
                    if (haystack.contains(query.toLowerCase())) {
                        out.append("\n[").append(++n).append("] ").append(page.getValue()[0]).append('\n')
                                .append("id: ").append(page.getKey()).append('\n')
                                .append("url: https://wiki.example/").append(page.getKey()).append('\n')
                                .append("source: wiki\n").append(page.getValue()[1]).append('\n');
                    }
                }
                if (n == 0) {
                    out.append("No hits.");
                }
                return out.toString();
            }
            if (KnowledgeResearch.READ_TOOL.equals(toolName)) {
                String id = String.valueOf(args.get("id"));
                reads.add(id);
                String[] page = pages.get(id);
                if (page == null) {
                    throw new ToolFailure("unknown id " + id);
                }
                return "title: " + page[0] + "\nid: " + id + "\nurl: https://wiki.example/" + id + "\n\n" + page[1];
            }
            throw new ToolFailure("not offered: " + toolName);
        }
    }

    /** Answers the planning prompt and the answer prompt with scripted texts; records the prompts. */
    private static final class ScriptedModel implements MainModelChat {
        final List<String> answers;
        final List<String> prompts = new ArrayList<String>();

        ScriptedModel(String... answers) {
            this.answers = new ArrayList<String>(Arrays.asList(answers));
        }

        @Override
        public MainModelChatResult complete(List<ChatMessage> messages, double temperature, int maxOutputTokens) {
            prompts.add(messages.get(messages.size() - 1).getContent());
            if (answers.isEmpty()) {
                return MainModelChatResult.failure(MainModelChatResult.Status.PROVIDER_FAILURE, "no script");
            }
            return MainModelChatResult.ok(answers.remove(0));
        }

        @Override
        public String modelName() {
            return "scripted";
        }
    }

    private final LexicalKnowledgeTools tools = new LexicalKnowledgeTools()
            .page("123", "PoC Hashicorp Vault (HaVdB)", "Der PoC binds PIMA Hashicorp Vault an das HaVdB-Backend an.\n"
                    + "Ergebnis: Secrets werden über Vault Agent verteilt.")
            .page("456", "Kantine", "Die Kantine öffnet um 11 Uhr.");

    @Test
    public void longNaturalLanguageRequestReachesTheLexicalSourceAsShortQuery() throws Exception {
        ScriptedModel model = new ScriptedModel("{\"queries\": [\"PIMA Hashicorp Vault\", \"Hashicorp Vault PoC\"]}",
                "Der PoC bindet PIMA Hashicorp Vault an das HaVdB-Backend an [1].");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(LONG_REQUEST);

        assertEquals(Arrays.asList("PIMA Hashicorp Vault"), tools.searches);
        assertFalse(tools.searches.contains(LONG_REQUEST));
        assertTrue(ResearchQueries.isConcise(tools.searches.get(0)));
        assertEquals("the hit is read after the search", Arrays.asList("123"), tools.reads);
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
        assertEquals("123", result.sources().get(0).id);
        String rendered = result.render();
        assertTrue(rendered, rendered.contains("[1] PoC Hashicorp Vault (HaVdB)"));
        assertTrue(rendered, rendered.contains("url: https://wiki.example/123"));
        assertTrue(rendered, rendered.contains("Searched with: \"PIMA Hashicorp Vault\""));
        assertTrue("the answer prompt carries the read excerpt", model.prompts.get(1).contains("Vault Agent"));
    }

    @Test
    public void aSentenceFromTheModelIsNeverForwardedAndLiteralIdentifiersArePreserved() throws Exception {
        ScriptedModel model = new ScriptedModel("{\"queries\": [\"" + LONG_REQUEST + "\"]}");

        new KnowledgeResearch(tools, model).research(LONG_REQUEST);

        assertEquals(1, tools.searches.size());
        String query = tools.searches.get(0);
        assertTrue(query, ResearchQueries.isConcise(query));
        assertTrue(query, query.contains("HaVdB"));
        assertTrue(query, query.contains("PIMA"));
    }

    @Test
    public void withoutAModelTheTaskIsStillSearched() throws Exception {
        ScriptedModel model = new ScriptedModel();

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(
                "Finde heraus, welche Bedeutung IRR im internen Unternehmenswissen hat.");

        assertEquals(Arrays.asList("IRR"), tools.searches);
        assertEquals(KnowledgeResearchResult.Outcome.NO_EVIDENCE, result.outcome());
    }

    @Test
    public void withoutHitsTheAgentRefinesOnceAndNeverAnswersFromModelKnowledge() throws Exception {
        ScriptedModel model = new ScriptedModel("{\"queries\": [\"IRR\", \"Internal Rate of Return\", \"IRR Glossar\"]}",
                "IRR bedeutet Internal Rate of Return.");

        KnowledgeResearchResult result = new KnowledgeResearch(tools, model).research(
                "Finde heraus, welche Bedeutung IRR im internen Unternehmenswissen hat.");

        assertEquals("one initial search and one refinement, no synonym loop",
                Arrays.asList("IRR", "Internal Rate of Return"), tools.searches);
        assertEquals(KnowledgeResearchResult.Outcome.NO_EVIDENCE, result.outcome());
        assertEquals("only the planning prompt went to the model", 1, model.prompts.size());
        assertFalse(result.render().contains("Internal Rate of Return."));
    }

    @Test
    public void literalQueryKeepsIdentifiersInTheirSpelling() {
        assertEquals("HaVdB PoC PIMA", ResearchQueries.literalQuery(LONG_REQUEST));
        assertEquals("BS-PIMA Hashicorp Vault", ResearchQueries.literalQuery("Was macht BS-PIMA mit Hashicorp Vault?"));
        assertEquals("RSL IAR", ResearchQueries.literalQuery("Was bedeutet RSL in IAR?"));
    }
}
