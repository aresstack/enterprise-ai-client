package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.passage.SentenceSegmenter;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ChatStatementExtractor;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.DocumentStatementsUseCase;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.ParticipantRole;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.PredicateCheck;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementAnalyzer;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementSettings;
import com.aresstack.enterpriseai.astu.acropolis.textmining.statements.StatementVerifier;
import com.aresstack.enterpriseai.astu.json.Json;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.fact.api.FactArgument;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
import com.aresstack.enterpriseai.fact.h2.H2FactStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Issue #108 against the real metadata catalog (embedded H2): the example sentence becomes mentions, two events and
 * relations at the resource, and the role question "who releases payments?" is answered from the argument index with
 * the clerk, never with the receipt.
 */
public class FactCatalogStatementsTest {

    private static final SourceAddress SOURCE = SourceAddress.of("wiki");
    /** Words: 1 Die 2 zuständige 3 Sachbearbeitung 4 prüft 5 den 6 eingereichten 7 Beleg 8 und 9 löst
     * 10 anschließend 11 die 12 Zahlung 13 aus. */
    private static final String SENTENCE = "Die zuständige Sachbearbeitung prüft den eingereichten Beleg und löst "
            + "anschließend die Zahlung aus.";
    private static final String PAGE = "# Ablauf\n\n" + SENTENCE + "\n\n| Spalte | Wert |\n|---|---|\n";
    private static final String ANSWER = "{\"mentions\": ["
            + "{\"id\": \"m1\", \"sentence\": 1, \"from\": 1, \"to\": 3, \"text\": \"Die zuständige Sachbearbeitung\", "
            + "\"lemma\": \"Sachbearbeitung\"},"
            + "{\"id\": \"m2\", \"sentence\": 1, \"from\": 5, \"to\": 7, \"text\": \"den eingereichten Beleg\", "
            + "\"lemma\": \"Beleg\"},"
            + "{\"id\": \"m3\", \"sentence\": 1, \"from\": 11, \"to\": 12, \"text\": \"die Zahlung\", "
            + "\"lemma\": \"Zahlung\"}],"
            + "\"events\": ["
            + "{\"id\": \"e1\", \"sentence\": 1, \"predicate\": {\"from\": 4, \"to\": 4, \"text\": \"prüft\"}, "
            + "\"lemma\": \"prüfen\", \"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}, "
            + "{\"role\": \"AFFECTED_OBJECT\", \"mention\": \"m2\"}]},"
            + "{\"id\": \"e2\", \"sentence\": 1, \"predicate\": {\"from\": 9, \"to\": 9, \"text\": \"löst\"}, "
            + "\"particle\": {\"from\": 13, \"to\": 13, \"text\": \"aus\"}, \"lemma\": \"auslösen\", "
            + "\"participants\": [{\"role\": \"ACTOR\", \"mention\": \"m1\"}, {\"role\": \"AFFECTED_OBJECT\", "
            + "\"mention\": \"m3\"}]}],"
            + "\"relations\": [{\"id\": \"r1\", \"sentence\": 1, \"kind\": \"STATE\", \"subject\": \"m2\", "
            + "\"value\": {\"from\": 6, \"to\": 6, \"text\": \"eingereichten\"}, \"lemma\": \"eingereicht\"}],"
            + "\"order\": [{\"before\": \"e1\", \"after\": \"e2\", \"sentence\": 1, "
            + "\"cue\": {\"from\": 10, \"to\": 10, \"text\": \"anschließend\"}}]}";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private H2FactStore facts;
    private FakeChatCompletionPort chat;
    private DocumentStatementsUseCase useCase;
    private FactStatementQueries queries;

    @Before
    public void setUp() throws Exception {
        facts = new H2FactStore(folder.newFolder("facts").toPath(), 2000);
        chat = new FakeChatCompletionPort();
        StatementAnalyzer analyzer = new StatementAnalyzer(SentenceSegmenter.splitter(),
                new ChatStatementExtractor(chat, () -> "test-model"), PredicateCheck.none(), StatementVerifier.none());
        useCase = new DocumentStatementsUseCase(analyzer, new FactCatalogStatements(facts),
                () -> new StatementSettings(true, false, 40, 1500));
        queries = new FactStatementQueries(facts);
    }

    @After
    public void tearDown() throws Exception {
        facts.close();
    }

    private static KnowledgeDocument page(String id, String version, String text) {
        return KnowledgeDocument.of(KnowledgeResource.builder(KnowledgeResourceId.of(id), SOURCE).title("Seite")
                .revision(KnowledgeRevision.version(version)).build(), text);
    }

    @Test
    public void whoReleasesPaymentsIsTheClerkNotTheReceipt() {
        chat.enqueueAnswer(ANSWER);
        assertEquals(DocumentStatementsUseCase.Storage.STORED, useCase.analyze(page("wiki:1", "1", PAGE)).storage());

        List<FactStatementQueries.Answer> actors = queries.participants("auslösen", ParticipantRole.ACTOR,
                ParticipantRole.AFFECTED_OBJECT, "Zahlung");
        assertEquals(1, actors.size());
        assertEquals("Die zuständige Sachbearbeitung", actors.get(0).surfaceForm());
        assertEquals("sachbearbeitung", actors.get(0).normalized());
        assertEquals(SENTENCE, actors.get(0).evidence());
        assertEquals("EXTRACTED", actors.get(0).status());

        assertTrue(queries.participants("auslösen", ParticipantRole.ACTOR, ParticipantRole.AFFECTED_OBJECT, "Beleg")
                .isEmpty());
        assertTrue(queries.participants("auslösen", ParticipantRole.AFFECTED_OBJECT, ParticipantRole.ACTOR, "Beleg")
                .isEmpty());
        List<FactStatementQueries.Answer> checked = queries.participants("prüfen", ParticipantRole.AFFECTED_OBJECT,
                ParticipantRole.ACTOR, "Sachbearbeitung");
        assertEquals("den eingereichten Beleg", checked.get(0).surfaceForm());
    }

    @Test
    public void storesMentionsEventsAndRelationsWithSearchableArguments() {
        chat.enqueueAnswer(ANSWER);
        useCase.analyze(page("wiki:1", "1", PAGE));
        KnowledgeResourceId resource = KnowledgeResourceId.of("wiki:1");

        List<MetadataFact> mentions = facts.factsOf(resource, FactCatalogStatements.MENTION);
        List<MetadataFact> events = facts.factsOf(resource, FactCatalogStatements.EVENT);
        List<MetadataFact> relations = facts.factsOf(resource, FactCatalogStatements.RELATION);
        assertEquals(3, mentions.size());
        assertEquals(2, events.size());
        assertEquals(2, relations.size());

        MetadataFact clerk = byTerm(mentions, "sachbearbeitung");
        Map<String, Object> payload = Json.parseObject(clerk.payload().json());
        assertEquals("Die zuständige Sachbearbeitung", payload.get("surfaceForm"));
        assertEquals("PROPOSED_UNVERIFIED", Json.object(payload, "lemmaCandidate").get("status"));
        assertEquals("lemma-candidate/nfc-lower/1", Json.object(payload, "normalized").get("method"));
        assertEquals(Integer.valueOf(PAGE.indexOf("Die zuständige")), clerk.anchor().charStart());

        // Both events refer to the same mention fact as actor.
        int actorReferences = 0;
        for (MetadataFact event : events) {
            for (FactArgument argument : facts.argumentsOf(event.id())) {
                if (argument.role().equals("ACTOR")) {
                    assertEquals(clerk.id(), argument.valueFactId());
                    actorReferences++;
                }
            }
        }
        assertEquals(2, actorReferences);
        assertEquals(2, facts.factsReferencing(clerk.id()).size());

        MetadataFact order = null;
        for (MetadataFact relation : relations) {
            if (Json.parseObject(relation.payload().json()).get("kind").equals("BEFORE")) {
                order = relation;
            }
        }
        assertNotNull(order);
        List<String> roles = new ArrayList<String>();
        for (FactArgument argument : facts.argumentsOf(order.id())) {
            roles.add(argument.role());
        }
        assertTrue(roles.toString(), roles.contains("SUBJECT") && roles.contains("OBJECT"));
    }

    @Test
    public void unchangedDocumentIsNotAskedAgainEvenWithoutStatements() {
        chat.enqueueAnswer(ANSWER);
        useCase.analyze(page("wiki:1", "1", PAGE));
        assertEquals(DocumentStatementsUseCase.Storage.UNCHANGED, useCase.analyze(page("wiki:1", "1", PAGE))
                .storage());
        assertEquals(1, chat.requests().size());

        chat.enqueueAnswer("{\"mentions\": [], \"events\": [], \"relations\": []}");
        String empty = "Hier steht ein ganz gewöhnlicher Satz ohne Handlung.";
        assertEquals(DocumentStatementsUseCase.Storage.STORED, useCase.analyze(page("wiki:2", "1", empty)).storage());
        assertEquals(DocumentStatementsUseCase.Storage.UNCHANGED, useCase.analyze(page("wiki:2", "1", empty))
                .storage());
        assertEquals(2, chat.requests().size());
    }

    @Test
    public void changedDocumentReplacesItsStatements() {
        chat.enqueueAnswer(ANSWER);
        useCase.analyze(page("wiki:1", "1", PAGE));
        chat.enqueueAnswer("{\"mentions\": [{\"id\": \"m1\", \"sentence\": 1, \"from\": 1, \"to\": 2, \"text\": "
                + "\"Die Stelle\", \"lemma\": \"Stelle\"}, {\"id\": \"m2\", \"sentence\": 1, \"from\": 4, \"to\": 5, "
                + "\"text\": \"die Zahlung\", \"lemma\": \"Zahlung\"}], \"events\": [{\"id\": \"e1\", \"sentence\": 1, "
                + "\"predicate\": {\"from\": 3, \"to\": 3, \"text\": \"löst\"}, \"particle\": {\"from\": 6, \"to\": 6, "
                + "\"text\": \"aus\"}, \"lemma\": \"auslösen\", \"participants\": [{\"role\": \"ACTOR\", \"mention\": "
                + "\"m1\"}, {\"role\": \"AFFECTED_OBJECT\", \"mention\": \"m2\"}]}]}");
        assertEquals(DocumentStatementsUseCase.Storage.STORED,
                useCase.analyze(page("wiki:1", "2", "Die Stelle löst die Zahlung aus.")).storage());

        List<FactStatementQueries.Answer> actors = queries.participants("auslösen", ParticipantRole.ACTOR,
                ParticipantRole.AFFECTED_OBJECT, "Zahlung");
        assertEquals(1, actors.size());
        assertEquals("Die Stelle", actors.get(0).surfaceForm());
        assertEquals(2, facts.factsOf(KnowledgeResourceId.of("wiki:1"), FactCatalogStatements.MENTION).size());
    }

    @Test
    public void switchedOffMeansNoModelCall() {
        DocumentStatementsUseCase off = new DocumentStatementsUseCase(new StatementAnalyzer(SentenceSegmenter.splitter(),
                new ChatStatementExtractor(chat, () -> "test-model"), null, null),
                new FactCatalogStatements(facts), StatementSettings::defaults);
        assertEquals(DocumentStatementsUseCase.Storage.DISABLED, off.analyze(page("wiki:1", "1", PAGE)).storage());
        assertTrue(chat.requests().isEmpty());
    }

    private static MetadataFact byTerm(List<MetadataFact> mentions, String term) {
        for (MetadataFact mention : mentions) {
            if (term.equals(Json.object(Json.parseObject(mention.payload().json()), "normalized").get("value"))) {
                return mention;
            }
        }
        throw new AssertionError("no mention " + term);
    }
}
