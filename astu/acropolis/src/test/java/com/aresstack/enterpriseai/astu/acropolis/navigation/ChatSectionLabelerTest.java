package com.aresstack.enterpriseai.astu.acropolis.navigation;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Die Antwort des Sprachmodells wird zeilenweise gelesen; fehlende Nummern behalten die Analyse-Bezeichnung. */
public class ChatSectionLabelerTest {

    @Test
    public void readsNumberedLines() {
        assertEquals(Arrays.asList("Belegimport", "Urlaubsantrag"),
                ChatSectionLabeler.parse("1: Belegimport\n2) Urlaubsantrag\n", 2));
    }

    @Test
    public void missingNumbersStayOpenForTheAnalysisLabel() {
        assertEquals(Arrays.asList("Belegimport", null), ChatSectionLabeler.parse("1: Belegimport\nUnsinn", 2));
        assertNull(ChatSectionLabeler.parse(null, 1).get(0));
        assertEquals("Satzzeichen weg", "Urlaub", ChatSectionLabeler.parse("1: \"Urlaub\".", 1).get(0));
    }
}
