package com.aresstack.enterpriseai.astu.acropolis.rag;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class FollowUpResearchTest {

    @Test
    public void insufficientVerdictYieldsTheQuery() {
        assertEquals("NIZZA Dateiformat",
                FollowUpResearch.parse("{\"sufficient\": false, \"query\": \"NIZZA Dateiformat\"}"));
        assertEquals("ZAUF \"Feld\" Länge", FollowUpResearch.parse(
                "Urteil:\n```json\n{\"query\":\"ZAUF \\\"Feld\\\" L\\u00e4nge\",\"sufficient\":false}\n```"));
    }

    @Test
    public void sufficientEmptyOrUnreadableVerdictYieldsNoQuery() {
        assertNull(FollowUpResearch.parse("{\"sufficient\": true, \"query\": \"IAR\"}"));
        assertNull(FollowUpResearch.parse("{\"sufficient\": false, \"query\": \"  \"}"));
        assertNull(FollowUpResearch.parse("{\"sufficient\": false}"));
        assertNull(FollowUpResearch.parse("Der Kontext reicht nicht."));
        assertNull(FollowUpResearch.parse(null));
    }
}
