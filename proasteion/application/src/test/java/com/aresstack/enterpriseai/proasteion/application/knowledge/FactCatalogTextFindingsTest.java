package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymExtractor;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymFindings;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymSettings;
import com.aresstack.enterpriseai.astu.acropolis.textmining.DocumentAcronymsUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactOrigin;
import com.aresstack.enterpriseai.fact.api.FactPayload;
import com.aresstack.enterpriseai.fact.api.FactProvenance;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
import com.aresstack.enterpriseai.fact.h2.H2FactStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Issue #107 against the real metadata catalog of #106 (embedded H2 in a temporary folder): acronym findings of a
 * page become facts at its stable resource id, are not duplicated on rereading, are replaced when the page changes
 * and leave manual facts alone. Long forms are invented.
 */
public class FactCatalogTextFindingsTest {

    private static final SourceAddress SOURCE = SourceAddress.of("wiki");
    private static final String PAGE_TEXT = "# Überblick\n\nDas Inventar aller Regenschirme (IAR) liefert an NIZZA. "
            + "Später meldet die IAR an ZAUF.\n\nAuftrag ABC-1234 ist offen.";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private H2FactStore facts;
    private DocumentAcronymsUseCase useCase;

    @Before
    public void setUp() throws Exception {
        facts = new H2FactStore(folder.newFolder("facts").toPath(), 2000);
        useCase = new DocumentAcronymsUseCase(new AcronymExtractor(SentenceSegmenter.splitter()),
                AcronymSettings::defaults, new FactCatalogTextFindings(facts), () -> true);
    }

    @After
    public void tearDown() throws Exception {
        facts.close();
    }

    private static KnowledgeDocument page(String id, String version, String text) {
        return KnowledgeDocument.of(KnowledgeResource.builder(KnowledgeResourceId.of(id), SOURCE).title("Seite")
                .revision(KnowledgeRevision.version(version)).build(), text);
    }

    private List<String> payloads(String resource, String type) {
        List<String> found = new ArrayList<String>();
        for (MetadataFact fact : facts.factsOf(KnowledgeResourceId.of(resource), FactTypeId.of(type))) {
            found.add(fact.payload().json());
        }
        return found;
    }

    @Test
    public void storesCandidatesDefinitionsAndIdentifiersAtTheResource() {
        DocumentAcronymsUseCase.Outcome outcome = useCase.analyze(page("wiki:1", "1", PAGE_TEXT));
        assertEquals(DocumentAcronymsUseCase.Storage.STORED, outcome.storage());
        assertEquals(3, payloads("wiki:1", AcronymFindings.ACRONYM_CANDIDATE).size());
        List<String> definitions = payloads("wiki:1", AcronymFindings.ACRONYM_DEFINITION);
        assertEquals(1, definitions.size());
        assertTrue(definitions.get(0), definitions.get(0).contains("\"longForm\":\"Inventar aller Regenschirme\""));
        assertEquals(1, payloads("wiki:1", AcronymFindings.TECHNICAL_IDENTIFIER_CANDIDATE).size());
        assertTrue(facts.type(FactTypeId.of(AcronymFindings.ACRONYM_DEFINITION)).isPresent());
        MetadataFact definition = facts.factsOf(KnowledgeResourceId.of("wiki:1"),
                FactTypeId.of(AcronymFindings.ACRONYM_DEFINITION)).get(0);
        assertEquals("DEFINES", definition.role().value());
        assertEquals(AcronymExtractor.ID, definition.provenance().extractorId());
        assertTrue(definition.anchor().quote().contains("(IAR)"));
        assertEquals("Inventar aller Regenschirme (IAR", PAGE_TEXT.substring(definition.anchor().charStart(),
                definition.anchor().charEnd()));
    }

    @Test
    public void rereadingTheSamePageWritesNothingAndDuplicatesNothing() {
        useCase.analyze(page("wiki:1", "1", PAGE_TEXT));
        int stored = facts.factsOf(KnowledgeResourceId.of("wiki:1")).size();
        DocumentAcronymsUseCase.Outcome again = useCase.analyze(page("wiki:1", "1", PAGE_TEXT));
        assertEquals(DocumentAcronymsUseCase.Storage.UNCHANGED, again.storage());
        assertEquals(stored, facts.factsOf(KnowledgeResourceId.of("wiki:1")).size());
    }

    @Test
    public void separateResourcesGetTheirOwnFactsAndAllAreFoundByType() {
        useCase.analyze(page("wiki:1", "1", PAGE_TEXT));
        useCase.analyze(page("wiki:2", "1", "Die Initiative autonomer Roboter (IAR) ist etwas anderes."));
        assertEquals(2, facts.factsOfType(FactTypeId.of(AcronymFindings.ACRONYM_DEFINITION)).size());
        assertEquals(2, facts.resourcesWithType(FactTypeId.of(AcronymFindings.ACRONYM_DEFINITION)).size());
    }

    @Test
    public void changedContentReplacesTheAutomaticFactsAndKeepsManualOnes() {
        useCase.analyze(page("wiki:1", "1", PAGE_TEXT));
        facts.registerType(FactType.of(FactTypeId.of("NOTE"), "manual note"));
        MetadataFact manual = facts.save(MetadataFact.builder(KnowledgeResourceId.of("wiki:1"), FactTypeId.of("NOTE"),
                FactPayload.json("{\"text\":\"geprüft\"}"), FactProvenance.manual("manual")).id(FactId.random())
                .build());
        DocumentAcronymsUseCase.Outcome changed = useCase.analyze(page("wiki:1", "2", "Nur noch ZAUF bleibt."));
        assertEquals(DocumentAcronymsUseCase.Storage.STORED, changed.storage());
        assertEquals(1, payloads("wiki:1", AcronymFindings.ACRONYM_CANDIDATE).size());
        assertTrue(payloads("wiki:1", AcronymFindings.ACRONYM_DEFINITION).isEmpty());
        assertTrue(facts.fact(manual.id()).isPresent());
        for (MetadataFact fact : facts.factsOf(KnowledgeResourceId.of("wiki:1"))) {
            if (fact.provenance().origin() == FactOrigin.AUTOMATIC) {
                assertEquals("2", fact.provenance().sourceRevision().version());
            }
        }
    }

    @Test
    public void otherThresholdsCountAsAnotherExtractorState() {
        useCase.analyze(page("wiki:1", "1", PAGE_TEXT));
        DocumentAcronymsUseCase stricter = new DocumentAcronymsUseCase(
                new AcronymExtractor(SentenceSegmenter.splitter()), () -> new AcronymSettings(4, 60, 3),
                new FactCatalogTextFindings(facts), () -> true);
        assertEquals(DocumentAcronymsUseCase.Storage.STORED, stricter.analyze(page("wiki:1", "1", PAGE_TEXT))
                .storage());
        assertEquals(2, payloads("wiki:1", AcronymFindings.ACRONYM_CANDIDATE).size());
    }

    @Test
    public void switchedOffStoresNothingButStillAnalyses() {
        DocumentAcronymsUseCase off = new DocumentAcronymsUseCase(new AcronymExtractor(SentenceSegmenter.splitter()),
                AcronymSettings::defaults, new FactCatalogTextFindings(facts), () -> false);
        DocumentAcronymsUseCase.Outcome outcome = off.analyze(page("wiki:1", "1", PAGE_TEXT));
        assertEquals(DocumentAcronymsUseCase.Storage.DISABLED, outcome.storage());
        assertEquals(3, outcome.analysis().acronyms().size());
        assertFalse(facts.isResourceRegistered(KnowledgeResourceId.of("wiki:1")));
    }
}
