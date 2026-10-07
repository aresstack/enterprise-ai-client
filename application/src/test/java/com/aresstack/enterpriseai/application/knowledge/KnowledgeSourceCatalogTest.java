package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class KnowledgeSourceCatalogTest {

    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki");
    private final InMemoryKnowledgeSource docs = new InMemoryKnowledgeSource("docs");
    private final KnowledgeSourceRegistration wikiRegistration =
            new KnowledgeSourceRegistration(wiki, SourceScope.of("Start"));
    private final KnowledgeSourceRegistration docsRegistration =
            new KnowledgeSourceRegistration(docs, SourceScope.builder().startPoint("Root").maxDepth(2).build());

    @Test
    public void keepsConfigurationOrderAndFindsById() {
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(docsRegistration, wikiRegistration);

        assertEquals(2, catalog.size());
        assertEquals(Arrays.asList(KnowledgeSourceId.of("docs"), KnowledgeSourceId.of("wiki")),
                new ArrayList<KnowledgeSourceId>(catalog.ids()));
        assertEquals(Arrays.asList(docs, wiki), catalog.ports());
        assertSame(wikiRegistration, catalog.find(KnowledgeSourceId.of("wiki")));
        assertSame(wiki, catalog.find(KnowledgeSourceId.of("wiki")).port());
        assertEquals(SourceScope.of("Start"), catalog.find(KnowledgeSourceId.of("wiki")).scope());
        assertTrue(catalog.contains(KnowledgeSourceId.of("docs")));
        assertFalse(catalog.contains(KnowledgeSourceId.of("other")));
        assertFalse(catalog.contains(null));
        assertNull(catalog.find(KnowledgeSourceId.of("other")));
        assertNull(catalog.find(null));
    }

    @Test
    public void emptyCatalogIsAllowed() {
        assertTrue(KnowledgeSourceCatalog.empty().isEmpty());
        assertTrue(KnowledgeSourceCatalog.of().isEmpty());
        assertTrue(new KnowledgeSourceCatalog(null).ids().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateSourceIdIsRejected() {
        KnowledgeSourceCatalog.of(wikiRegistration, new KnowledgeSourceRegistration(
                new InMemoryKnowledgeSource("wiki"), SourceScope.of("Other")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullRegistrationIsRejected() {
        new KnowledgeSourceCatalog(Arrays.asList(wikiRegistration, null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void registrationNeedsAPort() {
        new KnowledgeSourceRegistration(null, SourceScope.of("Start"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void registrationNeedsAScope() {
        new KnowledgeSourceRegistration(wiki, null);
    }

    @Test
    public void registrationExposesTheSourceId() {
        assertEquals(KnowledgeSourceId.of("wiki"), wikiRegistration.sourceId());
        assertTrue(wikiRegistration.toString().contains("wiki"));
    }
}
