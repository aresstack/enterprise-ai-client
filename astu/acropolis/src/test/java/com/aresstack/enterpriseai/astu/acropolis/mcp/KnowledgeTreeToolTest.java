package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import com.aresstack.enterpriseai.source.api.testing.InMemoryNavigationSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Issue #116: knowledge_tree browses parents and direct children of a space or a page. */
public class KnowledgeTreeToolTest {

    private static final int MANY = 25;
    private static final Pattern CURSOR = Pattern.compile("nextCursor: (\\S+)");
    private static final Pattern TITLE = Pattern.compile("- title: (Child \\d+)");

    private final InMemoryNavigationSource wiki = wiki(7);
    private final InMemoryNavigationSource docs = new InMemoryNavigationSource("docs", 5).withPartitions()
            .space("OPS", "Operations").page("OPS", "run", "Runbook");
    private final InMemoryKnowledgeSource plain = new InMemoryKnowledgeSource("plain").add("x", "X", "text");

    private static InMemoryNavigationSource wiki(int batchSize) {
        InMemoryNavigationSource source = new InMemoryNavigationSource("wiki", batchSize).withPartitions()
                .space("DEV", "Development").space("QA", "Quality")
                .page("DEV", "a", "A").page("a", "b", "B").page("b", "c", "C").page("DEV", "many", "Many");
        for (int i = 1; i <= MANY; i++) {
            source.page("many", "m" + i, "Child " + i);
        }
        return source;
    }

    private KnowledgeMcpTools tools(KnowledgeToolSettings settings, InMemoryNavigationSource navigable) {
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
                new KnowledgeSourceRegistration(plain, SourceScope.of("x")),
                new KnowledgeSourceRegistration(navigable, SourceScope.of("space:DEV")),
                new KnowledgeSourceRegistration(docs, SourceScope.of("space:DEV")));
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        return new KnowledgeMcpTools(new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null),
                new LoadKnowledgeDocumentUseCase(catalog), new SearchDrivenIndexingUseCase(indexing), settings);
    }

    private final KnowledgeMcpTools tools = tools(null, wiki);

    private McpToolResult tree(Object... pairs) {
        return tree(tools, pairs);
    }

    private static McpToolResult tree(KnowledgeMcpTools tools, Object... pairs) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) {
            arguments.put((String) pairs[i], pairs[i + 1]);
        }
        return tools.knowledgeTree().getHandler().invoke(new McpToolCall(KnowledgeMcpTools.KNOWLEDGE_TREE,
                arguments));
    }

    private String bookmark(String key) {
        return KnowledgeBookmarks.of(wiki.idOf(key)).toString();
    }

    private static String ok(McpToolResult result) {
        assertFalse(result.getText(), result.isError());
        return result.getText();
    }

    private static List<String> titles(String text) {
        List<String> found = new ArrayList<String>();
        Matcher matcher = TITLE.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String cursor(String text) {
        Matcher matcher = CURSOR.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private int childCalls() {
        int calls = 0;
        for (String call : wiki.calls()) {
            calls += call.startsWith("children ") ? 1 : 0;
        }
        return calls;
    }

    @Test
    public void isTheSecondOfSixKnowledgeTools() {
        assertEquals(6, tools.contributions().size());
        assertEquals(KnowledgeMcpTools.KNOWLEDGE_TREE, tools.contributions().get(1).getName());
        String description = tools.knowledgeTree().getDescription();
        assertTrue(description, description.contains("0 means no logical item limit"));
        assertTrue(description, description.contains("never returns page contents"));
    }

    @Test
    public void withoutTargetItListsOnlyNavigableSourcesAndSpaces() {
        String text = ok(tree());

        assertTrue(text, text.contains("source: wiki"));
        assertTrue(text, text.contains("source: docs"));
        assertFalse(text, text.contains("plain"));
        assertTrue(text, text.contains("- space: DEV\n  title: Development"));
        assertTrue(text, text.contains("- space: OPS\n  title: Operations"));
        assertFalse(text, text.contains("children:"));
        assertEquals("no tree is traversed", 0, childCalls());
    }

    @Test
    public void aSourceAloneListsOnlyItsSpaces() {
        String text = ok(tree("source", "docs"));

        assertTrue(text, text.contains("- space: OPS"));
        assertFalse(text, text.contains("DEV"));
    }

    @Test
    public void anUnknownSourceNamesTheValidOnes() {
        McpToolResult result = tree("source", "wik");

        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("Unknown source: wik"));
        assertTrue(result.getText(), result.getText().contains("- wiki\n- docs"));
    }

    @Test
    public void aSpaceIsChosenByItsKeyNeverByPositionOrTitle() {
        String text = ok(tree("space", "DEV"));
        assertTrue(text, text.contains("space: DEV — Development"));
        assertTrue(text, text.contains("parents: none"));
        assertTrue(text, text.contains("type: space"));
        assertTrue(text, text.contains("- title: A\n") && text.contains("- title: Many\n"));

        for (String guess : new String[]{"1", "Development", "dev"}) {
            McpToolResult wrong = tree("source", "wiki", "space", guess);
            assertTrue(guess, wrong.isError());
            assertTrue(wrong.getText(), wrong.getText().contains("Unknown space '" + guess + "' for source 'wiki'"));
            assertTrue(wrong.getText(), wrong.getText().contains("- DEV — Development\n- QA — Quality"));
        }
    }

    @Test
    public void aBookmarkFindsItsSpaceWithoutBeingTold() {
        String text = ok(tree("bookmark", bookmark("c")));

        assertTrue(text, text.contains("space: DEV — Development"));
        int parents = text.indexOf("parents:");
        int current = text.indexOf("current:");
        int children = text.indexOf("children:\n");
        assertTrue(text, parents >= 0 && parents < current && current < children);
        String path = text.substring(parents, current);
        assertTrue(path, path.contains("- type: space\n  space: DEV"));
        assertTrue(path, path.indexOf("- title: A") < path.indexOf("- title: B"));
        String node = text.substring(current, children);
        assertTrue(node, node.contains("title: C\n  id: " + wiki.idOf("c").value() + "\n  bookmark: " + bookmark("c")));
        assertTrue(text, text.substring(children).contains("none"));
    }

    @Test
    public void anIdWorksLikeTheBookmarkAndStaysReadable() {
        String byId = ok(tree("id", wiki.idOf("b").value()));
        assertEquals(byId, ok(tree("bookmark", bookmark("b"))));
        assertTrue(byId, byId.contains("- title: C\n  id: " + wiki.idOf("c").value()));
        assertTrue(byId, byId.contains("  children: no"));

        McpToolResult read = tools.knowledgeRead().getHandler().invoke(new McpToolCall(
                KnowledgeMcpTools.KNOWLEDGE_READ, single("id", wiki.idOf("c").value())));
        assertFalse(read.getText(), read.isError());
        assertFalse("no page text in the tree", byId.contains("# C"));
    }

    @Test
    public void directionRestrictsTheResponse() {
        String parents = ok(tree("bookmark", bookmark("b"), "direction", "parents"));
        assertTrue(parents, parents.contains("parents:"));
        assertFalse(parents, parents.contains("children:\n"));

        String children = ok(tree("bookmark", bookmark("b"), "direction", "children"));
        assertFalse(children, children.contains("parents:"));
        assertTrue(children, children.contains("- title: C"));
        assertFalse("only direct children", children.contains("Child"));

        McpToolResult wrong = tree("bookmark", bookmark("b"), "direction", "down");
        assertTrue(wrong.isError());
        assertTrue(wrong.getText(), wrong.getText().contains("parents, children, both"));
    }

    @Test
    public void theDefaultLimitIsTwentyAndTheCursorContinuesExactly() {
        assertEquals(20, KnowledgeToolSettings.defaults().treeChildLimit());
        String first = ok(tree("bookmark", bookmark("many")));
        List<String> shown = titles(first);
        assertEquals(20, shown.size());
        assertEquals("Child 1", shown.get(0));
        assertTrue(first, first.contains("parents:"));

        String next = ok(tree("bookmark", bookmark("many"), "cursor", cursor(first)));
        List<String> rest = titles(next);
        assertEquals(5, rest.size());
        assertEquals("Child 21", rest.get(0));
        assertEquals(null, cursor(next));
    }

    @Test
    public void positiveLimitsAreExactWhateverTheBatchSize() {
        List<String> seen = new ArrayList<String>();
        String cursor = "";
        int calls = 0;
        do {
            String text = ok(tree("bookmark", bookmark("many"), "direction", "children", "limit", 3,
                    "cursor", cursor));
            List<String> page = titles(text);
            assertTrue(text, page.size() == 3 || page.size() == 1 && cursor(text) == null);
            seen.addAll(page);
            cursor = cursor(text);
            calls++;
        } while (cursor != null && calls < MANY);
        assertEquals(MANY, seen.size());
        assertEquals(MANY, new LinkedHashSet<String>(seen).size());
        assertEquals("Child 25", seen.get(MANY - 1));
    }

    @Test
    public void limitZeroLoadsEveryBatch() {
        String text = ok(tree("bookmark", bookmark("many"), "direction", "children", "limit", 0));

        assertEquals(MANY, titles(text).size());
        assertEquals(null, cursor(text));
        assertTrue("several source batches", childCalls() >= 4);
    }

    @Test
    public void theLimitNeverShortensTheParentPath() {
        String text = ok(tree("bookmark", bookmark("c"), "limit", 1));

        assertTrue(text, text.contains("- title: A") && text.contains("- title: B"));
    }

    @Test
    public void aFullResponseIsTruncatedWithACursorAndContinuesWithoutLoss() {
        KnowledgeMcpTools small = tools(KnowledgeToolSettings.defaults()
                .withMaxResponseChars(KnowledgeToolSettings.MIN_RESPONSE_CHARS), wiki);
        Set<String> seen = new LinkedHashSet<String>();
        String cursor = "";
        boolean truncated = false;
        int calls = 0;
        do {
            String text = ok(tree(small, "bookmark", bookmark("many"), "direction", "children", "limit", 0,
                    "cursor", cursor));
            assertTrue(text.length() + " > max", text.length() <= KnowledgeToolSettings.MIN_RESPONSE_CHARS);
            truncated |= text.contains("truncated: true");
            for (String title : titles(text)) {
                assertTrue("delivered once: " + title, seen.add(title));
            }
            cursor = cursor(text);
            calls++;
        } while (cursor != null && calls < MANY);
        assertTrue(truncated);
        assertEquals(MANY, seen.size());
    }

    @Test
    public void aCursorOfAnotherNodeIsRejected() {
        String cursor = cursor(ok(tree("bookmark", bookmark("many"), "limit", 2)));

        McpToolResult other = tree("bookmark", bookmark("a"), "cursor", cursor);
        assertTrue(other.isError());
        assertTrue(other.getText(), other.getText().contains("Invalid cursor"));
        assertTrue(tree("bookmark", bookmark("many"), "cursor", "garbage").isError());
        assertTrue(tree("space", "DEV", "cursor", cursor).isError());
    }

    @Test
    public void aPageOfAnotherSpaceIsNotAcceptedSilently() {
        McpToolResult result = tree("space", "QA", "bookmark", bookmark("b"));

        assertTrue(result.isError());
        assertEquals("The page belongs to space 'DEV', not requested space 'QA'.", result.getText());
    }

    @Test
    public void conflictingOrUnknownTargetsAreErrors() {
        assertTrue(tree("id", wiki.idOf("b").value(), "bookmark", bookmark("b")).isError());
        McpToolResult flat = tree("id", plain.idOf("x").value());
        assertTrue(flat.isError());
        assertTrue(flat.getText(), flat.getText().contains("was not found"));
        assertTrue(tree("bookmark", "not a bookmark").isError());
        assertTrue(tree("limit", -1, "space", "DEV").isError());
    }

    @Test
    public void theDefaultLimitComesFromTheSettings() {
        KnowledgeMcpTools five = tools(KnowledgeToolSettings.defaults().withTreeChildLimit(5), wiki);

        assertEquals(5, titles(ok(tree(five, "bookmark", bookmark("many")))).size());
        assertTrue(five.knowledgeTree().getDescription().contains("default 5"));
    }

    private static Map<String, Object> single(String name, Object value) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(name, value);
        return arguments;
    }
}
