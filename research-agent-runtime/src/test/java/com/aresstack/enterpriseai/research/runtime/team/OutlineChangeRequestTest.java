package com.aresstack.enterpriseai.research.runtime.team;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** In der Phase Gliederung ist nicht jede Nachricht ein Änderungswunsch: Fragen bleiben Fragen. */
public class OutlineChangeRequestTest {

    @Test
    public void changeRequestsAreRecognized() {
        for (String text : new String[]{"Ergänze ein Kapitel zu den Kosten.", "Streiche Kapitel 3",
                "Bitte füge einen Abschnitt Risiken hinzu", "Kannst du Kapitel 2 kürzen?",
                "Die Reihenfolge der Kapitel 2 und 3 bitte vertauschen", "Benenne Kapitel 1 in Überblick um",
                "Nimm die Meilensteine raus"}) {
            assertTrue(text, OutlineChangeRequest.isChangeRequest(text));
        }
    }

    @Test
    public void questionsAreNotChangeRequests() {
        for (String text : new String[]{"Welche Meilensteine hat IAR?", "Was ändert sich mit IAR für die Anträge?",
                "Wer ist für IAR zuständig?", "Welche Änderungen beschreibt Kapitel 2?",
                "Suche in Confluence nach IAR.", "", "Gliederung erstellen"}) {
            assertFalse(text, OutlineChangeRequest.isChangeRequest(text));
        }
    }
}
