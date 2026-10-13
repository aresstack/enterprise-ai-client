package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Der Doppelklick findet das angeklickte Wort im vorlesbaren Text, auch wenn die Anzeige anders umbricht. */
public class ReadAloudStartTest {

    private static final String SPOKEN = "Betrieb\n\nErster Absatz mit Text.\n\nZweiter Absatz,\nder umbricht.";

    @Test
    public void startsAtTheClickedWordOfTheParagraph() {
        String paragraph = "Zweiter Absatz, der umbricht.\n";
        int at = ReadAloudStart.find(SPOKEN, paragraph, paragraph.indexOf("umbricht") + 3);
        assertEquals("umbricht.", SPOKEN.substring(at));
    }

    @Test
    public void startsAtTheFirstWordWhenTheClickHitsIt() {
        String paragraph = "Erster Absatz mit Text.";
        int at = ReadAloudStart.find(SPOKEN, paragraph, 2);
        assertEquals(SPOKEN.indexOf("Erster"), at);
    }

    @Test
    public void whitespaceImagesAndUnknownTextAreNoHit() {
        assertEquals("Leerzeichen zwischen Wörtern", -1, ReadAloudStart.find(SPOKEN, "Erster  Absatz", 7));
        assertEquals("Leerraum", -1, ReadAloudStart.find(SPOKEN, "   \n", 1));
        assertEquals("Bildplatzhalter", -1, ReadAloudStart.find(SPOKEN, " \n", 0));
        assertEquals("nicht vorlesbar", -1, ReadAloudStart.find(SPOKEN, "Bildunterschrift", 3));
    }
}
