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
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolType;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #127: knowledge_read_many reads several known resources bounded in parallel, with knowledge_read's semantics. */
public class KnowledgeReadManyToolTest {

    private final Wiki wiki = new Wiki();
    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private KnowledgeMcpTools tools;

    private KnowledgeMcpTools tools(KnowledgeToolSettings settings) {
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
                new KnowledgeSourceRegistration(wiki, SourceScope.of("1")));
        SearchDrivenIndexingUseCase live = new SearchDrivenIndexingUseCase(new IndexKnowledgeUseCase(index,
                embeddings, embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults())));
        tools = new KnowledgeMcpTools(new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null),
                new LoadKnowledgeDocumentUseCase(catalog), live, settings);
        return tools;
    }

    @After
    public void shutdown() {
        if (tools != null) {
            tools.shutdown();
        }
    }

    private static McpToolResult call(McpToolContribution tool, Object ids) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        if (ids != null) {
            arguments.put("ids", ids);
        }
        return tool.getHandler().invoke(new McpToolCall(tool.getName(), arguments));
    }

    private McpToolResult readMany(String... ids) {
        return call(tools.knowledgeReadMany(), Arrays.asList(ids));
    }

    private static Set<String> resources(McpToolResult result) {
        Set<String> ids = new HashSet<String>();
        for (McpContextResource resource : result.contextResources()) {
            ids.add(resource.resourceId());
        }
        return ids;
    }

    @Test
    public void isAnAdditionalToolWithARealStringArrayParameter() {
        tools(null);
        assertEquals(Arrays.asList("knowledge_search", "knowledge_tree", "knowledge_read", "knowledge_read_many",
                "knowledge_attachments", "knowledge_resolve_url"), names(tools.contributions()));
        McpToolContribution tool = tools.knowledgeReadMany();
        assertEquals(1, tool.getParameters().size());
        assertEquals("ids", tool.getParameters().get(0).getName());
        assertEquals(McpToolType.STRING_ARRAY, tool.getParameters().get(0).getType());
        assertTrue(tool.getParameters().get(0).isRequired());
        assertTrue(tool.getDescription(), tool.getDescription().contains("knowledge_read")
                && tool.getDescription().contains("knowledge_search") && tool.getDescription().contains("Example:"));
    }

    @Test
    public void emptyOrMissingIdsAreRejected() {
        tools(null);
        assertTrue(call(tools.knowledgeReadMany(), null).isError());
        McpToolResult empty = call(tools.knowledgeReadMany(), Collections.emptyList());
        assertTrue(empty.isError());
        assertTrue(empty.getText(), empty.getText().contains("example: ids="));
        assertTrue(readMany(" ", "").isError());
    }

    @Test
    public void readsOneAndSeveralIdsInInputOrderWithProvenance() {
        wiki.page("1", "Eins", "Inhalt eins");
        wiki.page("2", "Zwei", "Inhalt zwei");
        wiki.page("3", "Drei", "Inhalt drei");
        tools(null);
        McpToolResult single = readMany(wiki.id("2").value());
        assertFalse(single.getText(), single.isError());
        assertTrue(single.getText(), single.getText().startsWith("[1/1] OK\n"));
        assertEquals(Collections.singleton(wiki.id("2").value()), resources(single));

        McpToolResult many = readMany(wiki.id("3").value(), wiki.id("1").value(), wiki.id("2").value());
        assertFalse(many.getText(), many.isError());
        String text = many.getText();
        assertTrue(text, text.indexOf("[1/3] OK") < text.indexOf("Inhalt drei"));
        assertTrue(text, text.indexOf("Inhalt drei") < text.indexOf("[2/3] OK"));
        assertTrue(text, text.indexOf("[2/3] OK") < text.indexOf("Inhalt eins"));
        assertTrue(text, text.indexOf("Inhalt eins") < text.indexOf("[3/3] OK"));
        assertTrue(text, text.indexOf("[3/3] OK") < text.indexOf("Inhalt zwei"));
        assertEquals(3, many.contextResources().size());
        assertEquals(new HashSet<String>(Arrays.asList(wiki.id("1").value(), wiki.id("2").value(),
                wiki.id("3").value())), resources(many));
    }

    @Test
    public void knowledgeReadIsUnchanged() {
        wiki.page("1", "Eins", "Inhalt eins");
        tools(null);
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("id", wiki.id("1").value());
        McpToolResult read = tools.knowledgeRead().getHandler().invoke(new McpToolCall("knowledge_read", arguments));
        assertFalse(read.getText(), read.isError());
        assertTrue(read.getText(), read.getText().startsWith("title: Eins\nid: " + wiki.id("1").value()));
        assertEquals(1, read.contextResources().size());
    }

    @Test
    public void readsRunInParallelButNeverAboveTheLimit() throws Exception {
        for (int i = 1; i <= 6; i++) {
            wiki.page(String.valueOf(i), "Seite " + i, "Inhalt " + i);
        }
        wiki.delayMillis = 300;
        tools(KnowledgeToolSettings.defaults().withReadManyParallelism(3));
        long start = System.nanoTime();
        McpToolResult result = readMany(ids(1, 2, 3, 4, 5, 6));
        long millis = (System.nanoTime() - start) / 1_000_000L;
        assertFalse(result.getText(), result.isError());
        assertEquals(3, wiki.maxConcurrent.get());
        assertTrue("six reads of 300 ms with three at a time take about 600 ms, not 1800: " + millis,
                millis < 1500);
    }

    @Test
    public void batchSizeAndParallelismAreSeparateLimits() {
        for (int i = 1; i <= 4; i++) {
            wiki.page(String.valueOf(i), "Seite " + i, "Inhalt " + i);
        }
        tools(KnowledgeToolSettings.defaults().withReadManyMaxIds(3).withReadManyParallelism(1));
        McpToolResult tooMany = readMany(ids(1, 2, 3, 4));
        assertTrue(tooMany.isError());
        assertTrue(tooMany.getText(), tooMany.getText().contains("at most 3"));
        assertEquals(0, wiki.loads.size());
        McpToolResult fine = readMany(ids(1, 2, 3));
        assertFalse(fine.getText(), fine.isError());
        assertEquals(1, wiki.maxConcurrent.get());
        assertEquals(KnowledgeToolSettings.DEFAULT_READ_MANY_MAX_IDS, KnowledgeToolSettings.defaults().readManyMaxIds());
        assertEquals(KnowledgeToolSettings.DEFAULT_READ_MANY_PARALLELISM,
                KnowledgeToolSettings.defaults().readManyParallelism());
    }

    @Test
    public void aSlowPageDoesNotReorderTheOutput() {
        wiki.page("1", "Langsam", "Inhalt langsam");
        wiki.page("2", "Schnell", "Inhalt schnell");
        wiki.slow.put("1", 400L);
        tools(null);
        String text = readMany(ids(1, 2)).getText();
        assertTrue(text, text.indexOf("Inhalt langsam") < text.indexOf("[2/2] OK"));
        assertTrue(text, text.indexOf("[2/2] OK") < text.indexOf("Inhalt schnell"));
    }

    @Test
    public void failedReadsKeepTheOthersAndGiveNoProvenance() {
        wiki.page("1", "Eins", "Inhalt eins");
        wiki.page("3", "Drei", "Inhalt drei");
        wiki.page("4", "Vier", "Inhalt vier");
        wiki.failures.put("4", Kind.ACCESS_DENIED);
        tools(null);
        McpToolResult result = readMany(ids(1, 2, 3, 4));
        assertFalse(result.getText(), result.isError());
        String text = result.getText();
        assertTrue(text, text.contains("[2/4] ERROR\nrequestedId: " + wiki.id("2").value()));
        assertTrue(text, text.contains("[4/4] ERROR\nrequestedId: " + wiki.id("4").value()));
        assertTrue(text, text.contains("denied"));
        assertTrue(text, text.contains("Inhalt eins") && text.contains("Inhalt drei"));
        assertEquals(new HashSet<String>(Arrays.asList(wiki.id("1").value(), wiki.id("3").value())), resources(result));
    }

    @Test
    public void allReadsFailingIsAnError() {
        tools(null);
        McpToolResult result = readMany(ids(1, 2));
        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("[1/2] ERROR") && result.getText().contains("[2/2] ERROR"));
        assertTrue(result.contextResources().isEmpty());
    }

    @Test
    public void duplicatesAreReadOnceAndShownOnce() {
        wiki.page("1", "Eins", "Inhalt eins");
        wiki.page("2", "Zwei", "Inhalt zwei");
        tools(null);
        McpToolResult result = readMany(wiki.id("1").value(), wiki.id("2").value(), wiki.id("1").value());
        assertEquals(1, wiki.loads("1"));
        assertEquals(2, result.contextResources().size());
        String text = result.getText();
        assertTrue(text, text.contains("[3/3] OK (same document as [1/3]"));
        assertEquals(text.indexOf("Inhalt eins"), text.lastIndexOf("Inhalt eins"));
    }

    @Test
    public void aBookmarkAndItsResourceIdAreReadOnce() {
        wiki.page("1", "Eins", "Inhalt eins");
        tools(null);
        McpToolResult result = readMany(wiki.id("1").value(), "test://wiki.example.org/page/1");
        assertFalse(result.getText(), result.isError());
        assertEquals(1, wiki.loads("1"));
        assertEquals(1, result.contextResources().size());
        assertTrue(result.getText(), result.getText().contains("[2/2] OK (same document as [1/2]"));
    }

    @Test
    public void aRecoveredAddressReadsLikeKnowledgeReadAndIsDedupedByItsCanonicalId() {
        wiki.page("7", "Handbuch", "Inhalt sieben");
        tools(null);
        McpToolResult result = readMany("https://wiki.example.org/spaces/KEY/pages/7/Handbuch",
                wiki.id("7").value());
        assertFalse(result.getText(), result.isError());
        String text = result.getText();
        assertTrue(text, text.contains("is no canonical id; resolved to " + wiki.id("7").value()));
        assertTrue(text, text.contains("[2/2] OK (same document as [1/2]"));
        assertEquals(Collections.singleton(wiki.id("7").value()), resources(result));
    }

    @Test
    public void aPassageIdReadsThePassageWithItsNeighbours() {
        wiki.page("1", "Eins", "Inhalt eins");
        tools(null);
        String whole = readMany(wiki.id("1").value()).getText();
        int start = whole.indexOf("[passage ") + "[passage ".length();
        String passage = whole.substring(start, whole.indexOf(']', start));
        McpToolResult result = readMany(passage);
        assertFalse(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().contains("passage: " + passage + " (with neighbours)"));
        assertEquals(Collections.singleton(wiki.id("1").value()), resources(result));
    }

    @Test
    public void theBudgetIsSharedAndEachCutIsMarked() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 400; i++) {
            longText.append("Erster langer Absatz Nummer ").append(i).append(". ");
        }
        wiki.page("1", "Lang", longText.toString());
        wiki.page("2", "Kurz", "Kurzer Inhalt zwei");
        wiki.page("3", "AuchLang", longText.toString().replace("Erster", "Dritter"));
        tools(KnowledgeToolSettings.defaults().withMaxResponseChars(4_000));
        McpToolResult result = readMany(ids(1, 2, 3));
        String text = result.getText();
        assertTrue("global limit holds: " + text.length(), text.length() <= 4_000);
        assertTrue(text, text.contains("Kurzer Inhalt zwei"));
        assertTrue("the third page gets text too: " + text, text.contains("Dritter langer Absatz"));
        assertTrue(text, text.contains("[1/3] OK (truncated"));
        assertTrue(text, text.contains("[2/3] OK\n"));
        assertTrue(text, text.contains("[3/3] OK (truncated"));
        assertEquals(2, count(text, "[gekürzt:"));
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            count++;
        }
        return count;
    }

    private String[] ids(int... numbers) {
        String[] ids = new String[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            ids[i] = wiki.id(String.valueOf(numbers[i])).value();
        }
        return ids;
    }

    private static List<String> names(List<McpToolContribution> tools) {
        List<String> names = new ArrayList<String>();
        for (McpToolContribution tool : tools) {
            names.add(tool.getName());
        }
        return names;
    }

    /** A wiki with ids {@code test:wiki.example.org/page/<n>}; it counts loads and concurrent loads. */
    private static final class Wiki implements KnowledgeSourcePort, AddressableKnowledgeSource {
        private static final Pattern PAGES = Pattern.compile("/(?:spaces/[^/]+/)?pages/([0-9]+)(?:/.*)?");
        private static final String BASE = "https://wiki.example.org";

        private final SourceAddress sourceId = SourceAddress.of("test://wiki.example.org");
        private final Map<String, String[]> pages = new ConcurrentHashMap<String, String[]>();
        final Map<String, Kind> failures = new ConcurrentHashMap<String, Kind>();
        final Map<String, Long> slow = new ConcurrentHashMap<String, Long>();
        final List<String> loads = new CopyOnWriteArrayList<String>();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        private final AtomicInteger running = new AtomicInteger();
        volatile long delayMillis;

        void page(String number, String title, String text) {
            pages.put(number, new String[]{title, text});
        }

        KnowledgeResourceId id(String number) {
            return KnowledgeResourceId.of("test", sourceId.locator() + "/page/" + number);
        }

        int loads(String number) {
            return Collections.frequency(loads, number);
        }

        @Override
        public SourceAddress sourceId() {
            return sourceId;
        }

        @Override
        public Pattern addressPattern() {
            return Pattern.compile("(?:https?|test)://\\S+");
        }

        @Override
        public KnowledgeResourceId resolveAddress(String address) {
            String prefix = sourceId.value();
            String web = address.startsWith(prefix) ? BASE + address.substring(prefix.length()) : address;
            if (!web.startsWith(BASE + "/")) {
                return null;
            }
            Matcher matcher = PAGES.matcher(web.substring(BASE.length()));
            return matcher.matches() && pages.containsKey(matcher.group(1)) ? id(matcher.group(1)) : null;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            String prefix = sourceId.locator() + "/page/";
            if (!"test".equals(resourceId.scheme()) || !resourceId.schemeSpecificPart().startsWith(prefix)) {
                throw new KnowledgeSourceException(Kind.UNSUPPORTED, "not mine");
            }
            String number = resourceId.schemeSpecificPart().substring(prefix.length());
            int now = running.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            try {
                Long pause = slow.get(number);
                sleep(pause != null ? pause : delayMillis);
                Kind failure = failures.get(number);
                if (failure != null) {
                    throw new KnowledgeSourceException(failure, "failure");
                }
                String[] page = pages.get(number);
                if (page == null) {
                    throw new KnowledgeSourceException(Kind.NOT_FOUND, "unknown");
                }
                loads.add(number);
                return KnowledgeDocument.of(KnowledgeResource.builder(resourceId, sourceId).title(page[0]).build(),
                        page[1]);
            } finally {
                running.decrementAndGet();
            }
        }

        private static void sleep(long millis) {
            if (millis <= 0) {
                return;
            }
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }
    }
}
