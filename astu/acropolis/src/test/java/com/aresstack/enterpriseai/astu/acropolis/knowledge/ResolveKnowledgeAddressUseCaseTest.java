package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Direkte Adressen werden erst über das Source-Pattern geroutet und dann immer mit explizitem Source-Hint aufgelöst. */
public class ResolveKnowledgeAddressUseCaseTest {

    @Test
    public void hintedResolutionAsksOnlyTheHintedSourceEvenWhenTwoPatternsMatch() throws Exception {
        FakeAddressSource first = source("first", "^https://shared\\.example/.*$", "page/1");
        FakeAddressSource second = source("second", "^https://shared\\.example/.*$", "page/2");
        ResolveKnowledgeAddressUseCase resolver = resolver(first, second);

        Optional<ResolvedKnowledgeLink> found =
                resolver.resolve("https://shared.example/pages/1", first.sourceId());

        assertTrue(found.isPresent());
        assertEquals(first.sourceId(), found.get().sourceId());
        assertEquals(first.resolved, found.get().resourceId());
        assertEquals(1, first.resolveCalls);
        assertEquals(0, second.resolveCalls);
    }

    @Test
    public void globalResolutionInvokesEveryMatchingSourceSeparatelyAndNoOthers() {
        FakeAddressSource first = source("first", "^https://shared\\.example/.*$", "page/1");
        FakeAddressSource second = source("second", "^https://shared\\.example/.*$", "page/2");
        FakeAddressSource other = source("other", "^https://other\\.example/.*$", "page/3");
        FakeAddressSource withoutPattern = source("plain", null, "page/4");
        ResolveKnowledgeAddressUseCase resolver = resolver(first, second, other, withoutPattern);

        List<ResolvedKnowledgeLink> found = resolver.resolve("https://shared.example/pages/1");

        assertEquals(2, found.size());
        assertEquals(first.sourceId(), found.get(0).sourceId());
        assertEquals(second.sourceId(), found.get(1).sourceId());
        assertEquals(1, first.resolveCalls);
        assertEquals(1, second.resolveCalls);
        assertEquals(0, other.resolveCalls);
        assertEquals(0, withoutPattern.resolveCalls);
    }

    @Test
    public void patternMustMatchTheCompleteInputBeforeResolutionIsAttempted() throws Exception {
        FakeAddressSource source = source("wiki", "^https://wiki\\.example/pages/.*$", "page/1");
        ResolveKnowledgeAddressUseCase resolver = resolver(source);

        assertFalse(resolver.matches("Text https://wiki.example/pages/1", source.sourceId()));
        assertFalse(resolver.resolve("Text https://wiki.example/pages/1", source.sourceId()).isPresent());
        assertEquals(0, source.resolveCalls);
    }

    private static ResolveKnowledgeAddressUseCase resolver(FakeAddressSource... sources) {
        KnowledgeSourceRegistration[] registrations = new KnowledgeSourceRegistration[sources.length];
        for (int i = 0; i < sources.length; i++) {
            registrations[i] = new KnowledgeSourceRegistration(sources[i], SourceScope.of("1"));
        }
        final KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(registrations);
        return new ResolveKnowledgeAddressUseCase(() -> catalog);
    }

    private static FakeAddressSource source(String name, String regex, String resourcePath) {
        return new FakeAddressSource(SourceAddress.of("test", name),
                regex == null ? null : Pattern.compile(regex),
                KnowledgeResourceId.of("test", name + "/" + resourcePath));
    }

    private static final class FakeAddressSource implements KnowledgeSourcePort, AddressableKnowledgeSource {

        private final SourceAddress sourceId;
        private final Pattern pattern;
        private final KnowledgeResourceId resolved;
        private int resolveCalls;

        FakeAddressSource(SourceAddress sourceId, Pattern pattern, KnowledgeResourceId resolved) {
            this.sourceId = sourceId;
            this.pattern = pattern;
            this.resolved = resolved;
        }

        @Override
        public SourceAddress sourceId() {
            return sourceId;
        }

        @Override
        public Pattern addressPattern() {
            return pattern;
        }

        @Override
        public KnowledgeResourceId resolveAddress(String address) {
            resolveCalls++;
            return resolved;
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
