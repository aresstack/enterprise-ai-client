package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PageNavigationSource;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryNavigationSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** #115: the export tree comes complete from the source, once, for every format. */
public class KnowledgeExportUseCaseTest {

    private final KnowledgeExportUseCase export = new KnowledgeExportUseCase(new PageNavigationUseCase());

    /** Two children per batch, so "a" needs two batches; nobody has expanded anything. */
    private final InMemoryNavigationSource source = new InMemoryNavigationSource("wiki", 2)
            .space("DEV", "Development").page("DEV", "a", "A").page("a", "b", "B").page("a", "c", "C")
            .page("a", "d", "D").page("c", "e", "E").page("DEV", "z", "Z");

    private NavigationNode node(String key) throws Exception {
        return source.position(source.idOf(key)).page();
    }

    private static String describe(ExportDocumentNode node) {
        StringBuilder text = new StringBuilder(node.title());
        if (!node.children().isEmpty()) {
            List<String> children = new ArrayList<String>();
            for (ExportDocumentNode child : node.children()) {
                children.add(describe(child));
            }
            text.append(children);
        }
        return text.toString();
    }

    @Test
    public void theSubtreeOfTheClickedNodeIsLoadedCompletelyInSourceOrder() throws Exception {
        final List<String> progress = new ArrayList<String>();
        ExportDocumentNode root = export.subtree(source, node("a"),
                (loaded, found, title) -> progress.add(loaded + "/" + found + " " + title), () -> false);
        assertEquals("A[B, C[E], D]", describe(root));
        assertEquals("# C", root.children().get(1).text());
        assertEquals(5, root.size());
        assertEquals(Arrays.asList("1/1 A", "2/4 B", "3/4 C", "4/5 E", "5/5 D"), progress);
        int batchesOfA = 0;
        for (String call : source.calls()) {
            batchesOfA += call.startsWith("children " + source.idOf("a").value()) ? 1 : 0;
        }
        assertEquals("both batches of A", 2, batchesOfA);
    }

    @Test
    public void aSinglePageNeverAsksForChildren() throws Exception {
        ExportDocumentNode page = export.page(source, source.idOf("a"), "A", null, null);
        assertEquals("A", describe(page));
        assertEquals("# A", page.text());
        for (String call : source.calls()) {
            assertTrue(call, !call.startsWith("children"));
        }
    }

    @Test
    public void failedPagesAreNamedInsteadOfASilentlyIncompleteTree() throws Exception {
        source.failWith("e", Kind.ACCESS_DENIED);
        try {
            export.subtree(source, node("a"), null, null);
            fail("incomplete export");
        } catch (ExportIncompleteException e) {
            assertEquals(Collections.singletonList("E: kein Zugriff"), e.failures());
        }
    }

    @Test(expected = CancellationException.class)
    public void cancellingStopsTheWalk() throws Exception {
        final int[] calls = {0};
        export.subtree(source, node("a"), null, () -> ++calls[0] > 3);
    }

    @Test
    public void aCycleOrADuplicateInAFaultySourceEndsAndExportsEachPageOnce() throws Exception {
        Cyclic cyclic = new Cyclic();
        ExportDocumentNode root = export.subtree(cyclic, cyclic.node("a"), null, null);
        assertEquals("a[b[c]]", describe(root));
    }

    /** a → (b, b again, c?) ; b → (a, c): a loop back to a and c twice. */
    private static final class Cyclic implements KnowledgeSourcePort, PageNavigationSource {
        private final SourceAddress id = SourceAddress.of("loop");
        private final Map<String, List<String>> children = new LinkedHashMap<String, List<String>>();
        private final Map<String, Integer> loads = new HashMap<String, Integer>();

        Cyclic() {
            children.put("a", Arrays.asList("b", "b"));
            children.put("b", Arrays.asList("a", "c"));
            children.put("c", Arrays.asList("b"));
        }

        NavigationNode node(String key) {
            return NavigationNode.page(KnowledgeResourceId.of("loop", key), key, NavigationNode.Children.YES);
        }

        @Override
        public NavigationChildren children(NavigationNode parent, String cursor) {
            List<NavigationNode> nodes = new ArrayList<NavigationNode>();
            for (String key : children.get(parent.resource().schemeSpecificPart())) {
                nodes.add(node(key));
            }
            return new NavigationChildren(nodes, "");
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) {
            String key = resourceId.schemeSpecificPart();
            loads.merge(key, 1, Integer::sum);
            if (loads.get(key) > 1) {
                throw new AssertionError("loaded twice: " + key);
            }
            return KnowledgeDocument.of(KnowledgeResource.builder(resourceId, id).title(key).build(), key);
        }

        @Override
        public PagePosition position(KnowledgeResourceId page) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, "no position");
        }

        @Override
        public CrossReferences crossReferences(KnowledgeResourceId page) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(Kind.UNSUPPORTED, "no references");
        }

        @Override
        public SourceAddress sourceId() {
            return id;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }
    }
}
