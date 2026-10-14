package com.aresstack.enterpriseai.fact.api;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ArgumentProjectionTest {

    private static final FactTypeId MENTION = FactTypeId.of("MENTION");
    private static final String CLERK = "2d1f6f0a-5a39-4b55-9bbd-6c1c3f61a0a1";

    private static final ArgumentProjection EVENT = ArgumentProjection.of(
            ArgumentRule.fixed("predicate.normalized", "PREDICATE", null, "value"),
            ArgumentRule.byRoleField("arguments", "role", "mention", "normalized").referencing(MENTION));

    @Test
    public void derivesRolesReferencesAndValuesFromThePayload() {
        List<ArgumentProjection.Projected> arguments = EVENT.projectWithTypes(FactPayload.json("{"
                + "\"predicate\":{\"surfaceForm\":\"löst … aus\",\"normalized\":{\"value\":\"auslösen\"}},"
                + "\"arguments\":[{\"role\":\"ACTOR\",\"mention\":\"" + CLERK + "\",\"normalized\":\"sachbearbeitung\"},"
                + "{\"role\":\"AFFECTED_OBJECT\",\"normalized\":\"zahlung\"},"
                + "{\"role\":\"AFFECTED_OBJECT\",\"normalized\":\"beleg\"}]}"));

        assertEquals(4, arguments.size());
        assertEquals(FactArgument.of("PREDICATE", 0, null, "auslösen"), arguments.get(0).argument());
        assertNull(arguments.get(0).referenceType());
        assertEquals(FactArgument.of("ACTOR", 0, FactId.parse(CLERK), "sachbearbeitung"), arguments.get(1).argument());
        assertEquals(MENTION, arguments.get(1).referenceType());
        assertEquals("repeated roles count up", 1, arguments.get(3).argument().ordinal());
    }

    @Test
    public void missingOrUnfitMembersYieldNoArgument() {
        List<FactArgument> arguments = EVENT.project(FactPayload.json("{\"predicate\":\"prüfen\",\"arguments\":["
                + "{\"role\":\"actor\",\"normalized\":\"x\"},{\"role\":\"ACTOR\"},{\"normalized\":\"y\"},7]}"));

        assertTrue("string where an object is expected, lower-case role, nothing to index: " + arguments,
                arguments.isEmpty());
        assertTrue(EVENT.project(FactPayload.json("{}")).isEmpty());
    }

    @Test
    public void aReferenceMustBeAFactId() {
        try {
            EVENT.project(FactPayload.json("{\"arguments\":[{\"role\":\"ACTOR\",\"mention\":\"m1\"}]}"));
            fail("accepted a local key as fact id");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("fact id"));
        }
    }

    @Test
    public void storedFormRoundTrips() {
        String json = EVENT.toJson();

        assertEquals(EVENT, ArgumentProjection.parse(json));
        assertEquals("", ArgumentProjection.none().toJson());
        assertTrue(ArgumentProjection.parse("").isEmpty());
        assertEquals(EVENT, FactType.of(FactTypeId.of("EVENT"), "Event", 1, null, EVENT).argumentProjection());
        assertTrue(FactType.of(FactTypeId.of("EVENT"), "Event").argumentProjection().isEmpty());
    }

    @Test
    public void rulesAreChecked() {
        for (Runnable invalid : Arrays.<Runnable>asList(
                () -> ArgumentRule.fixed("a..b", "ACTOR", null, "v"),
                () -> ArgumentRule.fixed("a", "actor", null, "v"),
                () -> ArgumentRule.byRoleField("a", "role", null, null),
                () -> ArgumentRule.fixed("a", "ACTOR", "bad-name", null))) {
            try {
                invalid.run();
                fail("accepted an invalid rule");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }
}
