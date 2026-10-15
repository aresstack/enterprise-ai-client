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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A good but insufficient hit is explored through its hierarchy: the agent opens the area around it with
 * {@code knowledge_tree}, reads the related pages in one {@code knowledge_read_many} call and the judge decides again.
 */
public class HierarchyExplorationTest {

    private static final String TASK = "Wie ist der Betrieb von Vault organisiert?";

    private static final String TREE_P1 = "source: wiki\nspace: OPS — Betrieb\n\nparents:\n- type: space\n"
            + "  space: OPS\n  title: Betrieb\n- title: Plattformen\n  id: p0\n  bookmark: bm0\n\ncurrent:\n"
            + "  type: page\n  title: Vault Überblick\n  id: p1\n  bookmark: bm1\n  children: yes\n\nchildren:\n"
            + "- title: Vault Rufbereitschaft\n  id: c1\n  children: no\n- title: Vault Backup\n  id: c2\n"
            + "  children: no\n\nRead a page with knowledge_read and its id; browse further with knowledge_tree and "
            + "its bookmark.";
    private static final String TREE_P0 = "source: wiki\nspace: OPS — Betrieb\n\ncurrent:\n  type: page\n"
            + "  title: Plattformen\n  id: p0\n  children: yes\n\nchildren:\n- title: Vault Überblick\n  id: p1\n"
            + "  children: yes\n- title: Vault Betriebshandbuch\n  id: s1\n  children: no\n";

    private static final class Tools implements ToolInvoker {
        final List<String> calls = new ArrayList<String>();
        boolean hierarchy = true;
        boolean readManyOffered = true;

        @Override
        public String call(String toolName, Map<String, Object> args) throws ToolFailure, EndpointUnavailable {
            Object target = args.containsKey("query") ? args.get("query") : args.containsKey("ids")
                    ? args.get("ids") : args.get("id");
            calls.add(toolName + ":" + target);
            if (KnowledgeResearch.SEARCH_TOOL.equals(toolName)) {
                return "Query: Vault\n\n[1] Vault Überblick\nid: p1\nsource: wiki\nVault wird zentral betrieben.\n";
            }
            if (HierarchyExploration.TREE_TOOL.equals(toolName)) {
                if (!hierarchy) {
                    throw new ToolFailure("No navigable knowledge source places 'p1' in a page tree.");
                }
                return "p1".equals(args.get("id")) ? TREE_P1 : TREE_P0;
            }
            if (HierarchyExploration.READ_MANY_TOOL.equals(toolName)) {
                if (!readManyOffered) {
                    throw new EndpointUnavailable(toolName + " is not offered");
                }
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) args.get("ids");
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) {
                        out.append("\n\n---\n\n");
                    }
                    out.append('[').append(i + 1).append('/').append(ids.size()).append("] OK\n")
                            .append(page(ids.get(i)));
                }
                return out.toString();
            }
            return page(String.valueOf(args.get("id")));
        }

        private static String page(String id) {
            return "title: Seite " + id + "\nid: " + id + "\nurl: https://wiki.example/" + id + "\n\n"
                    + "Vault Betrieb: Inhalt der Seite " + id + ".";
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

    /** Answers with the given verdicts in order, then always yes. */
    private static final class Judge implements EvidenceJudge {
        final List<Boolean> verdicts;
        int calls;

        Judge(Boolean... verdicts) {
            this.verdicts = new ArrayList<Boolean>(Arrays.asList(verdicts));
        }

        @Override
        public boolean sufficient(String task, List<KnowledgeHit> read, List<String> excerpts) {
            calls++;
            return verdicts.isEmpty() || verdicts.remove(0).booleanValue();
        }
    }

    private static KnowledgeResearch research(Tools tools, Model model, Judge judge) {
        return new KnowledgeResearch(tools, model, Collections.<DiscoveryStrategy>singletonList(
                new LexicalDiscoveryStrategy()), judge, new HierarchyExploration());
    }

    @Test
    public void anInsufficientHitInAHierarchyIsExploredAndReadInOneBundle() throws Exception {
        Tools tools = new Tools();
        Model model = new Model("{\"queries\": [\"Vault\"]}", "Vault wird zentral betrieben [1], mit Backup [3].");
        Judge judge = new Judge(Boolean.FALSE);

        KnowledgeResearchResult result = research(tools, model, judge).research(TASK);

        assertEquals("one search, one read, the tree of the hit and of its parent, one bundled read",
                Arrays.asList("knowledge_search:Vault", "knowledge_read:p1", "knowledge_tree:p1", "knowledge_tree:p0",
                        "knowledge_read_many:[c1, c2, s1]"), tools.calls);
        assertEquals(KnowledgeResearchResult.Outcome.ANSWERED, result.outcome());
        List<String> ids = new ArrayList<String>();
        for (KnowledgeHit source : result.sources()) {
            ids.add(source.id);
        }
        assertEquals("the anchor, its children and its sibling", Arrays.asList("p1", "c1", "c2", "s1"), ids);
        assertEquals("the read header names the explored page", "https://wiki.example/c1",
                result.sources().get(1).url);
        assertEquals(Arrays.asList("Vault", "tree: Vault Überblick"), result.queries());
        assertEquals("judged once before the exploration, no more lookups after it", 1, judge.calls);
    }

    @Test
    public void sufficientEvidenceNeverOpensTheTree() throws Exception {
        Tools tools = new Tools();
        Model model = new Model("{\"queries\": [\"Vault\", \"Vault Betrieb\"]}", "Antwort [1].");

        research(tools, model, new Judge(Boolean.TRUE)).research(TASK);

        assertEquals(Arrays.asList("knowledge_search:Vault", "knowledge_read:p1"), tools.calls);
    }

    @Test
    public void aHitWithoutHierarchyIsNoAnchorAndLeavesNoNote() throws Exception {
        Tools tools = new Tools();
        tools.hierarchy = false;
        Model model = new Model("{\"queries\": [\"Vault\"]}", "Antwort [1].");

        KnowledgeResearchResult result = research(tools, model, new Judge(Boolean.FALSE)).research(TASK);

        assertEquals(Arrays.asList("knowledge_search:Vault", "knowledge_read:p1", "knowledge_tree:p1"), tools.calls);
        assertTrue(result.notes().isEmpty());
        assertEquals(Arrays.asList("Vault"), result.queries());
        assertEquals(1, result.sources().size());
    }

    @Test
    public void withoutTheBundleToolTheRelatedPagesAreReadOneByOne() throws Exception {
        Tools tools = new Tools();
        tools.readManyOffered = false;
        Model model = new Model("{\"queries\": [\"Vault\"]}", "Antwort [1].");

        KnowledgeResearchResult result = research(tools, model, new Judge(Boolean.FALSE)).research(TASK);

        assertTrue(tools.calls.toString(), tools.calls.containsAll(Arrays.asList("knowledge_read:c1",
                "knowledge_read:c2", "knowledge_read:s1")));
        assertEquals(4, result.sources().size());
    }

    @Test
    public void theTreeAnswerIsParsedIntoItsNeighbourhood() {
        KnowledgeToolOutput.TreeNeighbourhood around = KnowledgeToolOutput.parseTree(TREE_P1);

        assertEquals("wiki", around.source);
        assertEquals("the space entry has no page id", 1, around.parents.size());
        assertEquals("p0", around.parents.get(0).id);
        assertEquals("p1", around.current.id);
        assertEquals("bm1", around.current.bookmark);
        assertEquals(2, around.children.size());
        assertEquals("Vault Backup", around.children.get(1).title);
        assertNull(KnowledgeToolOutput.parseTree("source: wiki\nspace: OPS — Betrieb\n\nparents: none\n\ncurrent:\n"
                + "  type: space\n  space: OPS\n  title: Betrieb\n\nchildren:\nnone\n").current);
    }

    @Test
    public void theBundleAnswerIsSplitByPosition() {
        String output = "[1/3] OK\ntitle: A\nid: a\n\nText A\n\n---\n\nnoch Text A\n\n---\n\n[2/3] ERROR\n"
                + "requestedId: b\nerror: not found\n\n---\n\n[3/3] OK (truncated — read it alone with "
                + "knowledge_read for more)\ntitle: C\nid: c\n\nText C";

        Map<Integer, String> texts = KnowledgeToolOutput.readManyTexts(output);

        assertEquals(Arrays.asList(0, 2), new ArrayList<Integer>(texts.keySet()));
        assertTrue("a separator inside a page stays in its text", texts.get(0).contains("noch Text A"));
        assertEquals("Text C", KnowledgeToolOutput.pageText(texts.get(2)));
    }

    @Test
    public void manyCandidatesArePickedByTheModelOrByTitleTerms() {
        List<KnowledgeToolOutput.TreeNode> nodes = new ArrayList<KnowledgeToolOutput.TreeNode>();
        for (String title : Arrays.asList("Kantine", "Urlaub", "Vault Betrieb", "Parkplätze", "Reisekosten",
                "Vault Backup")) {
            nodes.add(new KnowledgeToolOutput.TreeNode(title, title.toLowerCase(), ""));
        }
        HierarchyExploration.Anchor anchor = new HierarchyExploration.Anchor(
                new KnowledgeHit("p1", "Vault", "", "", "", "wiki"), "wiki", nodes);
        HierarchyExploration exploration = new HierarchyExploration();

        List<KnowledgeToolOutput.TreeNode> byModel = exploration.select((system, user) -> "{\"pick\": [6, 3, 99]}",
                TASK, anchor);
        List<KnowledgeToolOutput.TreeNode> byTerms = exploration.select((system, user) -> "", TASK, anchor);

        assertEquals(Arrays.asList("Vault Backup", "Vault Betrieb"), titles(byModel));
        assertEquals(HierarchyExploration.MAX_PAGES, byTerms.size());
        assertEquals("Vault Betrieb", byTerms.get(0).title);
        assertFalse(titles(byTerms).subList(0, 2).contains("Kantine"));
    }

    private static List<String> titles(List<KnowledgeToolOutput.TreeNode> nodes) {
        List<String> titles = new ArrayList<String>();
        for (KnowledgeToolOutput.TreeNode node : nodes) {
            titles.add(node.title);
        }
        return titles;
    }
}
