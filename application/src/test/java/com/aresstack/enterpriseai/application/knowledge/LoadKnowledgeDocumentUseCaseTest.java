package com.aresstack.enterpriseai.application.knowledge;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class LoadKnowledgeDocumentUseCaseTest {

    private InMemoryKnowledgeSource wiki;
    private InMemoryKnowledgeSource docs;
    private KnowledgeSourceCatalog catalog;
    private LoadKnowledgeDocumentUseCase useCase;

    @Before
    public void setUp() {
        wiki = new InMemoryKnowledgeSource("wiki").add("Java", "Java installieren", "apt install openjdk-8-jdk");
        docs = new InMemoryKnowledgeSource("docs").add("Urlaub", "Urlaubsantrag", "Im Portal beantragen.");
        catalog = KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(wiki, SourceScope.of("Java")),
                new KnowledgeSourceRegistration(docs, SourceScope.of("Urlaub")));
        useCase = new LoadKnowledgeDocumentUseCase(catalog);
    }

    @Test
    public void loadsFromTheSourceThatKnowsTheIdSkippingForeignSources() throws KnowledgeSourceException {
        KnowledgeDocument document = useCase.load(docs.idOf("Urlaub"));

        assertEquals("Urlaubsantrag", document.resource().title());
        assertEquals("Im Portal beantragen.", document.text());
        assertEquals("die Wiki-Quelle lehnt die fremde ID ab (UNSUPPORTED) und wird übersprungen",
                Collections.emptyList(), wiki.calls());
        assertEquals(Collections.singletonList("load:Urlaub"), docs.calls());
        assertSame(catalog, useCase.catalog());
    }

    @Test
    public void unknownIdInTheResponsibleSourceIsNotFoundAndStopsTheSearch() {
        try {
            useCase.load(wiki.idOf("Nope"));
            fail("NOT_FOUND erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.NOT_FOUND, e.kind());
        }
        assertEquals("die zweite Quelle wird nicht mehr gefragt", Collections.emptyList(), docs.calls());
    }

    @Test
    public void idNoSourceKnowsIsUnsupported() {
        try {
            useCase.load(KnowledgeResourceId.of("confluence:ABC/1"));
            fail("UNSUPPORTED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNSUPPORTED, e.kind());
        }
        assertEquals(Arrays.asList(Collections.emptyList(), Collections.emptyList()),
                Arrays.asList(wiki.calls(), docs.calls()));
    }

    @Test
    public void otherSourceFailuresPropagateUnchanged() {
        wiki.failWith("Java", KnowledgeSourceException.Kind.ACCESS_DENIED);
        try {
            useCase.load(wiki.idOf("Java"));
            fail("ACCESS_DENIED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.ACCESS_DENIED, e.kind());
        }
    }

    @Test
    public void explicitSourceIsAskedDirectly() throws KnowledgeSourceException {
        KnowledgeDocument document = useCase.load(wiki.idOf("Java"), KnowledgeSourceId.of("wiki"));
        assertEquals("Java installieren", document.resource().title());

        try {
            useCase.load(wiki.idOf("Java"), KnowledgeSourceId.of("docs"));
            fail("UNSUPPORTED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNSUPPORTED, e.kind());
        }
        try {
            useCase.load(wiki.idOf("Java"), KnowledgeSourceId.of("sharepoint"));
            fail("NOT_FOUND erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.NOT_FOUND, e.kind());
        }
    }

    @Test
    public void emptyCatalogKnowsNothing() {
        LoadKnowledgeDocumentUseCase empty = new LoadKnowledgeDocumentUseCase(KnowledgeSourceCatalog.empty());
        try {
            empty.load(wiki.idOf("Java"));
            fail("UNSUPPORTED erwartet");
        } catch (KnowledgeSourceException e) {
            assertEquals(KnowledgeSourceException.Kind.UNSUPPORTED, e.kind());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void catalogIsMandatory() {
        new LoadKnowledgeDocumentUseCase(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resourceIdIsMandatory() throws KnowledgeSourceException {
        useCase.load(null);
    }
}
