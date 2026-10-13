package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeAddressUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #94: knowledge_resolve_url returns every candidate of the matching sources and never picks one. */
public class KnowledgeResolveUrlToolTest {

    private static final String URL = "https://wiki.example.org/pages/viewpage.action?pageId=7#Abschnitt";

    private final Owning first = new Owning("first", "wiki.example.org/page/7");
    private final Owning second = new Owning("second", "mirror.example.org/page/7");
    private final McpToolContribution tool = new KnowledgeResolveUrlTool(new ResolveKnowledgeAddressUseCase(
            () -> KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(first, SourceScope.of("1")),
                    new KnowledgeSourceRegistration(second, SourceScope.of("1"))))).contribution();

    @Test
    public void withoutSourceEveryMatchingSourceIsAskedAndAllCandidatesAreListed() {
        String text = call(URL, null).getText();
        assertTrue(text, text.contains("candidates: 2"));
        assertTrue(text, text.contains("source: " + first.sourceId().value()));
        assertTrue(text, text.contains("id: test:wiki.example.org/page/7"));
        assertTrue(text, text.contains("id: test:mirror.example.org/page/7"));
        assertTrue(text, text.contains("bookmark: "));
        assertTrue(text, text.contains("fragment: Abschnitt"));
    }

    @Test
    public void withSourceOnlyThatSourceIsAsked() {
        String text = call(URL, second.sourceId().value()).getText();
        assertTrue(text, text.contains("candidates: 1"));
        assertTrue(text, text.contains("id: test:mirror.example.org/page/7"));
        assertFalse(text, text.contains("wiki.example.org/page/7"));
        assertTrue("die erste Quelle wird nie gefragt", first.calls == 0);
    }

    @Test
    public void anAddressNoSourceOwnsHasNoCandidateAndAMissingUrlIsAnError() {
        String text = call("mailto:someone@example.org", null).getText();
        assertTrue(text, text.contains("candidates: 0"));
        McpToolResult missing = call(null, null);
        assertTrue(missing.isError());
        assertTrue(missing.getText(), missing.getText().contains("example: url="));
    }

    private McpToolResult call(String url, String source) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        if (url != null) {
            arguments.put("url", url);
        }
        if (source != null) {
            arguments.put("source", source);
        }
        return tool.getHandler().invoke(new McpToolCall(tool.getName(), arguments));
    }

    /** A source that owns every web address and maps it to one fixed page. */
    private static final class Owning implements KnowledgeSourcePort, AddressableKnowledgeSource {
        private final SourceAddress sourceId;
        private final KnowledgeResourceId page;
        int calls;

        Owning(String name, String page) {
            this.sourceId = SourceAddress.of("test", name);
            this.page = KnowledgeResourceId.of("test", page);
        }

        @Override
        public SourceAddress sourceId() {
            return sourceId;
        }

        @Override
        public Pattern addressPattern() {
            return Pattern.compile("https?://\\S+");
        }

        @Override
        public KnowledgeResourceId resolveAddress(String address) {
            calls++;
            return page;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNSUPPORTED, "not needed");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }
    }
}
