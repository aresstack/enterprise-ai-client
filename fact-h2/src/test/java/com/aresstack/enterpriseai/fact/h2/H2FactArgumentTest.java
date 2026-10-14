package com.aresstack.enterpriseai.fact.h2;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.fact.api.ArgumentCondition;
import com.aresstack.enterpriseai.fact.api.ArgumentProjection;
import com.aresstack.enterpriseai.fact.api.ArgumentRule;
import com.aresstack.enterpriseai.fact.api.FactArgument;
import com.aresstack.enterpriseai.fact.api.FactId;
import com.aresstack.enterpriseai.fact.api.FactPayload;
import com.aresstack.enterpriseai.fact.api.FactProvenance;
import com.aresstack.enterpriseai.fact.api.FactReview;
import com.aresstack.enterpriseai.fact.api.FactStoreException;
import com.aresstack.enterpriseai.fact.api.FactStoreException.Reason;
import com.aresstack.enterpriseai.fact.api.FactType;
import com.aresstack.enterpriseai.fact.api.FactTypeId;
import com.aresstack.enterpriseai.fact.api.MetadataFact;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The generic argument projection of issue #108: derived from payloads, searchable both ways, never dangling. */
public class H2FactArgumentTest {

    private static final FactTypeId MENTION = FactTypeId.of("MENTION");
    private static final FactTypeId EVENT = FactTypeId.of("EVENT");
    private static final String EXTRACTOR = "statements";

    private static final ArgumentProjection MENTION_ARGUMENTS = ArgumentProjection.of(
            ArgumentRule.fixed("normalized", "TERM", null, "value"));
    private static final ArgumentProjection EVENT_ARGUMENTS = ArgumentProjection.of(
            ArgumentRule.fixed("predicate", "PREDICATE", null, "normalized"),
            ArgumentRule.byRoleField("arguments", "role", "mention", "normalized").referencing(MENTION));

    private static final SourceAddress CONFLUENCE = SourceAddress.of("confluence://cf.example/confluence");
    private static final KnowledgeResourceId PAGE = KnowledgeResourceId.of("confluence:cf.example/confluence/page/1");
    private static final KnowledgeResourceId OTHER = KnowledgeResourceId.of("confluence:cf.example/confluence/page/2");

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private H2FactStore store;

    @Before
    public void openStore() throws Exception {
        store = new H2FactStore(folder.newFolder("facts").toPath(), 300);
        store.registerType(FactType.of(MENTION, "Mention", 1, null, MENTION_ARGUMENTS));
        store.registerType(FactType.of(EVENT, "Event", 1, null, EVENT_ARGUMENTS));
        store.registerResource(PAGE, CONFLUENCE);
        store.registerResource(OTHER, CONFLUENCE);
    }

    @After
    public void closeStore() throws Exception {
        store.close();
    }

    @Test
    public void projectionIsPartOfTheStoredType() {
        assertEquals(EVENT_ARGUMENTS, store.type(EVENT).get().argumentProjection());
        assertTrue(store.type(MENTION).get().argumentProjection().rules().size() == 1);
    }

    @Test
    public void anEventIsStoredOnceAndFoundFromEachParticipant() {
        Example example = Example.store(store);

        List<MetadataFact> triggers = store.factsWithArguments(EVENT, Arrays.asList(
                ArgumentCondition.value("PREDICATE", "auslösen"), ArgumentCondition.value("AFFECTED_OBJECT", "zahlung")));
        assertEquals(1, triggers.size());
        assertEquals(example.trigger, triggers.get(0).id());
        FactArgument actor = argument(store.argumentsOf(example.trigger), "ACTOR");
        assertEquals("the shared subject of both verbs", example.clerk, actor.valueFactId());
        assertEquals("sachbearbeitung", actor.normalizedValue());

        assertTrue("the document never says the receipt triggers the payment", store.factsWithArguments(EVENT,
                Arrays.asList(ArgumentCondition.value("PREDICATE", "auslösen"),
                        ArgumentCondition.value("ACTOR", "beleg"))).isEmpty());
        // Both facts may share one creation millisecond; then their order is not defined.
        assertEquals(new java.util.HashSet<Object>(Arrays.asList(example.check, example.trigger)),
                new java.util.HashSet<Object>(ids(store.factsReferencing(example.clerk))));
        assertEquals(Collections.singletonList(example.check), ids(store.factsWithArguments(null,
                Collections.singletonList(ArgumentCondition.reference("AFFECTED_OBJECT", example.receipt)))));
        assertEquals("mentions are searchable by their term", Collections.singletonList(example.receipt),
                ids(store.factsWithArguments(MENTION,
                        Collections.singletonList(ArgumentCondition.value("TERM", "beleg")))));
    }

    @Test
    public void searchesRunOnTheArgumentIndexes() {
        String byValue = store.explain("SELECT FACT_ID FROM METADATA_FACT WHERE "
                + H2FactStore.argumentCondition(ArgumentCondition.value("ACTOR", "x")), "ACTOR", "sachbearbeitung");
        String byReference = store.explain("SELECT FACT_ID FROM METADATA_FACT WHERE "
                        + H2FactStore.argumentCondition(ArgumentCondition.reference("ACTOR", FactId.random())), "ACTOR",
                FactId.random().toString());

        assertTrue(byValue, byValue.contains(FactSchema.ARGUMENT_VALUE_INDEX));
        // H2 keeps its own single-column index for the foreign key next to (VALUE_FACT_ID, FACT_ID); either serves.
        assertTrue(byReference, byReference.contains(FactSchema.ARGUMENT_REFERENCE_INDEX)
                || byReference.contains("FK_ARGUMENT_VALUE"));
        for (String plan : Arrays.asList(byValue, byReference)) {
            assertTrue(plan, plan.contains("PRIMARY_KEY"));
            assertFalse("no scan of facts or arguments: " + plan, plan.contains("tableScan"));
        }
    }

    @Test
    public void referencesMustNameAMentionOfTheSameResource() {
        final MetadataFact foreign = mention(OTHER, "zahlung");
        store.save(foreign);
        final MetadataFact otherEvent = event(PAGE, "prüfen", null, null);
        store.save(otherEvent);

        expect(Reason.INVALID_REFERENCE, () -> store.save(event(PAGE, "auslösen", null, FactId.random())));
        expect(Reason.INVALID_REFERENCE, () -> store.save(event(PAGE, "auslösen", null, foreign.id())));
        expect(Reason.INVALID_REFERENCE, () -> store.save(event(PAGE, "auslösen", null, otherEvent.id())));
    }

    @Test
    public void aRejectedBatchLeavesNeitherFactsNorArguments() {
        Example.store(store);
        int before = store.factsOf(PAGE).size();
        MetadataFact mention = mention(PAGE, "antrag");
        final List<MetadataFact> batch = Arrays.asList(mention, event(PAGE, "stellen", mention.id(), FactId.random()));

        expect(Reason.INVALID_REFERENCE, () -> store.replaceAutomaticFacts(PAGE, EXTRACTOR, batch));
        assertEquals(before, store.factsOf(PAGE).size());
        assertTrue(store.factsWithArguments(MENTION,
                Collections.singletonList(ArgumentCondition.value("TERM", "antrag"))).isEmpty());
    }

    @Test
    public void replacingKeepsOldMentionsThatAReviewedEventStillNeeds() {
        Example example = Example.store(store);
        MetadataFact check = store.fact(example.check).get();
        store.save(check.toBuilder().review(FactReview.CONFIRMED).build());

        MetadataFact clerk = mention(PAGE, "sachbearbeitung");
        int removed = store.replaceAutomaticFacts(PAGE, EXTRACTOR, Collections.singletonList(clerk));

        assertEquals("trigger event and payment mention went", 2, removed);
        assertTrue("old clerk kept for the reviewed event", store.fact(example.clerk).isPresent());
        assertTrue(store.fact(example.receipt).isPresent());
        assertFalse(store.fact(example.payment).isPresent());
        assertEquals(example.clerk, argument(store.argumentsOf(example.check), "ACTOR").valueFactId());
        assertEquals("reviewed event, its two mentions and the new clerk", 4, store.factsOf(PAGE).size());
    }

    @Test
    public void aReferencedMentionCannotBeRemovedAlone() {
        final Example example = Example.store(store);

        expect(Reason.CONFLICT, () -> store.removeFact(example.clerk));
        assertTrue(store.removeFact(example.trigger));
        assertTrue(store.removeFact(example.payment));
        assertEquals("clerk, receipt and the check that refers to them", 3, store.removeResource(PAGE));
    }

    @Test
    public void anEditedPayloadReplacesItsArguments() {
        Example example = Example.store(store);
        MetadataFact trigger = store.fact(example.trigger).get();
        MetadataFact edited = MetadataFact.builder(PAGE, EVENT, FactPayload.json("{\"predicate\":{\"normalized\":"
                + "\"anweisen\"},\"arguments\":[]}"), trigger.provenance()).id(trigger.id()).build();

        store.save(edited);

        List<FactArgument> arguments = store.argumentsOf(example.trigger);
        assertEquals(1, arguments.size());
        assertEquals("anweisen", arguments.get(0).normalizedValue());
        assertTrue(store.factsReferencing(example.payment).isEmpty());
    }

    @Test
    public void argumentsCanBeRebuiltFromStoredPayloadsAlone() throws Exception {
        Example example = Example.store(store);
        List<FactArgument> before = store.argumentsOf(example.trigger);
        try (Connection connection = store.openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM FACT_ARGUMENT");
        }
        assertTrue(store.argumentsOf(example.trigger).isEmpty());

        assertEquals("3 mention terms, 2 x predicate, actor and object", 9, store.rebuildArguments());
        assertEquals(before, store.argumentsOf(example.trigger));
    }

    @Test
    public void aNewProjectionVersionReDerivesTheArguments() {
        final Example example = Example.store(store);
        expect(Reason.INCOMPATIBLE_SCHEMA, () -> store.registerType(FactType.of(EVENT, "Event", 1, null,
                ArgumentProjection.of(ArgumentRule.fixed("predicate", "VERB", null, "normalized")))));

        store.registerType(FactType.of(EVENT, "Event", 2, null,
                ArgumentProjection.of(ArgumentRule.fixed("predicate", "VERB", null, "normalized"))));

        assertEquals(Collections.singletonList(FactArgument.of("VERB", 0, null, "auslösen")),
                store.argumentsOf(example.trigger));
        assertTrue(store.factsReferencing(example.clerk).isEmpty());
    }

    // ---------------------------------------------------------------- data

    /** "Die zuständige Sachbearbeitung prüft den eingereichten Beleg und löst anschließend die Zahlung aus." */
    private static final class Example {
        FactId clerk;
        FactId receipt;
        FactId payment;
        FactId check;
        FactId trigger;

        static Example store(H2FactStore store) {
            MetadataFact clerk = mention(PAGE, "sachbearbeitung");
            MetadataFact receipt = mention(PAGE, "beleg");
            MetadataFact payment = mention(PAGE, "zahlung");
            MetadataFact check = event(PAGE, "prüfen", clerk.id(), receipt.id());
            MetadataFact trigger = event(PAGE, "auslösen", clerk.id(), payment.id());
            // Events first: the batch may refer forward.
            store.replaceAutomaticFacts(PAGE, EXTRACTOR, Arrays.asList(check, trigger, clerk, receipt, payment));
            Example example = new Example();
            example.clerk = clerk.id();
            example.receipt = receipt.id();
            example.payment = payment.id();
            example.check = check.id();
            example.trigger = trigger.id();
            return example;
        }
    }

    private static MetadataFact mention(KnowledgeResourceId resource, String term) {
        return MetadataFact.builder(resource, MENTION, FactPayload.json("{\"surfaceForm\":\"" + term
                + "\",\"normalized\":{\"value\":\"" + term + "\",\"method\":\"test\"}}"), provenance()).build();
    }

    /** @param actor the acting mention or {@code null}; the object gets the term of {@code object} if known */
    private static MetadataFact event(KnowledgeResourceId resource, String predicate, FactId actor, FactId object) {
        List<String> arguments = new ArrayList<String>();
        if (actor != null) {
            arguments.add("{\"role\":\"ACTOR\",\"mention\":\"" + actor + "\",\"normalized\":\"sachbearbeitung\"}");
        }
        if (object != null) {
            String term = "auslösen".equals(predicate) ? "zahlung" : "beleg";
            arguments.add("{\"role\":\"AFFECTED_OBJECT\",\"mention\":\"" + object + "\",\"normalized\":\"" + term
                    + "\"}");
        }
        StringBuilder list = new StringBuilder();
        for (String argument : arguments) {
            list.append(list.length() == 0 ? "" : ",").append(argument);
        }
        return MetadataFact.builder(resource, EVENT, FactPayload.json("{\"predicate\":{\"surfaceForm\":\"x\","
                + "\"normalized\":\"" + predicate + "\"},\"arguments\":[" + list + "]}"), provenance()).build();
    }

    private static FactProvenance provenance() {
        return FactProvenance.automatic(EXTRACTOR, "1");
    }

    private static FactArgument argument(List<FactArgument> arguments, String role) {
        for (FactArgument argument : arguments) {
            if (argument.role().equals(role)) {
                return argument;
            }
        }
        throw new AssertionError("no " + role + " in " + arguments);
    }

    private static List<FactId> ids(List<MetadataFact> facts) {
        List<FactId> ids = new ArrayList<FactId>();
        for (MetadataFact fact : facts) {
            ids.add(fact.id());
        }
        return ids;
    }

    private static void expect(Reason reason, Runnable action) {
        try {
            action.run();
            fail("expected " + reason);
        } catch (FactStoreException e) {
            assertEquals(e.getMessage(), reason, e.reason());
        }
    }
}
