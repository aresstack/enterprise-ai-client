package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.LoadKnowledgeDocumentUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeAddressUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchDrivenIndexingUseCase;
import com.aresstack.enterpriseai.astu.acropolis.rag.RetrieveKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #109: knowledge_read repairs a non-canonical address through the address resolver, never by own path rules. */
public class KnowledgeReadRecoveryTest {

    private final Wiki root = new Wiki("test://wiki.example.org", "https://wiki.example.org");
    private final Wiki nested = new Wiki("test://wiki.example.org/team", "https://wiki.example.org/team");
    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();

    private McpToolContribution read(Wiki... sources) {
        List<KnowledgeSourceRegistration> registrations = new ArrayList<KnowledgeSourceRegistration>();
        for (Wiki source : sources) {
            registrations.add(new KnowledgeSourceRegistration(source, SourceScope.of("1")));
        }
        KnowledgeSourceCatalog catalog = new KnowledgeSourceCatalog(registrations);
        SearchDrivenIndexingUseCase live = new SearchDrivenIndexingUseCase(new IndexKnowledgeUseCase(index,
                embeddings, embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults())));
        return new KnowledgeMcpTools(new RetrieveKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(), null),
                new LoadKnowledgeDocumentUseCase(catalog), live, null).knowledgeRead();
    }

    private static McpToolResult call(McpToolContribution tool, String id) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put("id", id);
        return tool.getHandler().invoke(new McpToolCall(tool.getName(), arguments));
    }

    @Test
    public void canonicalIdsAndBookmarksNeverAskTheResolver() {
        root.page("7", "Handbuch", "Inhalt sieben");
        McpToolContribution tool = read(root);
        McpToolResult byId = call(tool, root.id("7").value());
        assertFalse(byId.getText(), byId.isError());
        McpToolResult byBookmark = call(tool, "test://wiki.example.org/page/7");
        assertFalse(byBookmark.getText(), byBookmark.isError());
        assertTrue(byBookmark.getText(), byBookmark.getText().contains("Inhalt sieben"));
        assertEquals(0, root.resolved.size());
    }

    @Test
    public void aNonCanonicalBookmarkIsResolvedWithTheLongestMatchingSourceOnly() {
        nested.page("7", "Teamseite", "Inhalt des Teams");
        root.page("7", "Handbuch", "Inhalt sieben");
        McpToolResult result = call(read(root, nested),
                "test://wiki.example.org/team/spaces/KEY/pages/7/Teamseite+Titel");
        assertFalse(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().contains("Inhalt des Teams"));
        assertTrue(result.getText(), result.getText().contains("resolved to " + nested.id("7").value()));
        assertEquals(Collections.singletonList("https://wiki.example.org/team/spaces/KEY/pages/7/Teamseite+Titel"),
                nested.resolved);
        assertEquals("the shorter prefix is never asked", 0, root.resolved.size());
        assertTrue("read after resolving", nested.loads("7") >= 1);
    }

    @Test
    public void anUnresolvableBookmarkExplainsItself() {
        McpToolResult result = call(read(root), "test://wiki.example.org/spaces/KEY/overview");
        assertTrue(result.isError());
        assertTrue(result.getText(), result.getText().contains("non-canonical knowledge address"));
        assertTrue(result.getText(), result.getText().contains(root.sourceId().value()));
    }

    @Test
    public void aWebAddressWithOneCandidateIsRead() {
        root.page("7", "Handbuch", "Inhalt sieben");
        McpToolResult result = call(read(root), "https://wiki.example.org/spaces/KEY/pages/7/Handbuch");
        assertFalse(result.getText(), result.isError());
        assertTrue(result.getText(), result.getText().contains("Inhalt sieben"));
    }

    @Test
    public void aWebAddressWithoutCandidateOrWithSeveralIsNeverGuessed() {
        McpToolResult none = call(read(root), "https://elsewhere.example.org/pages/7");
        assertTrue(none.isError());
        assertTrue(none.getText(), none.getText().contains("No connected knowledge source owns"));

        Wiki mirror = new Wiki("test://mirror.example.org", "https://wiki.example.org");
        root.page("7", "Handbuch", "Inhalt sieben");
        mirror.page("7", "Spiegel", "Gespiegelt");
        McpToolResult several = call(read(root, mirror), "https://wiki.example.org/spaces/KEY/pages/7");
        assertTrue(several.isError());
        assertTrue(several.getText(), several.getText().contains("id: " + root.id("7").value()));
        assertTrue(several.getText(), several.getText().contains("id: " + mirror.id("7").value()));
        assertEquals(0, root.loads("7") + mirror.loads("7"));
    }

    @Test
    public void accessAndAvailabilityProblemsAreNeverMaskedAsAddressProblems() {
        root.page("7", "Handbuch", "Inhalt sieben");
        McpToolContribution tool = read(root);
        root.failure = Kind.ACCESS_DENIED;
        McpToolResult denied = call(tool, "test://wiki.example.org/page/7");
        assertTrue(denied.getText(), denied.getText().contains("denied"));
        root.failure = Kind.UNAVAILABLE;
        McpToolResult down = call(tool, "test://wiki.example.org/page/7");
        assertTrue(down.getText(), down.getText().contains("not reachable"));
        assertEquals(0, root.resolved.size());
    }

    /** A wiki whose ids are {@code test:<locator>/page/<n>}; it resolves {@code .../pages/<n>} under its base. */
    private static final class Wiki implements KnowledgeSourcePort, AddressableKnowledgeSource {
        private static final Pattern PAGES = Pattern.compile("/(?:spaces/[^/]+/)?pages/([0-9]+)(?:/.*)?");

        private final SourceAddress sourceId;
        private final String base;
        private final Map<String, String[]> pages = new HashMap<String, String[]>();
        private final List<String> loaded = new ArrayList<String>();
        final List<String> resolved = new ArrayList<String>();
        Kind failure;

        Wiki(String sourceId, String base) {
            this.sourceId = SourceAddress.of(sourceId);
            this.base = base;
        }

        void page(String number, String title, String text) {
            pages.put(number, new String[]{title, text});
        }

        KnowledgeResourceId id(String number) {
            return KnowledgeResourceId.of("test", sourceId.locator() + "/page/" + number);
        }

        int loads(String number) {
            return Collections.frequency(loaded, number);
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
            String web = address.startsWith(prefix) ? base + address.substring(prefix.length()) : address;
            resolved.add(web);
            if (!web.startsWith(base + "/")) {
                return null;
            }
            Matcher matcher = PAGES.matcher(web.substring(base.length()));
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
            if (failure != null) {
                throw new KnowledgeSourceException(failure, "failure");
            }
            String[] page = pages.get(number);
            if (page == null) {
                throw new KnowledgeSourceException(Kind.NOT_FOUND, "unknown");
            }
            loaded.add(number);
            return KnowledgeDocument.of(KnowledgeResource.builder(resourceId, sourceId).title(page[0]).build(),
                    page[1]);
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }
    }
}
