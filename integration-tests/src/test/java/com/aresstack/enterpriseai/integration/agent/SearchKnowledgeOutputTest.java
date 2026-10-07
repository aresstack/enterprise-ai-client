package com.aresstack.enterpriseai.integration.agent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Der Parser des Testagenten gegen das Ausgabeformat von {@code search_knowledge} (AP20). */
public class SearchKnowledgeOutputTest {

    @Test
    public void readsTitleAndSnippetPerHit() {
        String output = "Treffer: 2\n"
                + "Anfrage: Kündigungsfrist\n"
                + "\n"
                + "[1] Kündigungsfrist – Allgemeines\n"
                + "Id: memory:handbuch/kuendigung\n"
                + "Chunk: memory:handbuch/kuendigung#0\n"
                + "Quelle: handbuch\n"
                + "Stand: unbekannt\n"
                + "Score: 0.0328 (Volltext #1: 1.2 | Semantik #2: 0.4)\n"
                + "Text:\n"
                + "Die Kündigungsfrist beträgt drei Monate zum Quartalsende.\n"
                + "Die Frist gilt für beide Seiten.\n"
                + "\n"
                + "[2] Urlaubsregelung\n"
                + "Id: memory:handbuch/urlaub\n"
                + "Text:\n"
                + "Dreißig Tage Urlaub …\n";
        List<SearchKnowledgeOutput.Hit> hits = SearchKnowledgeOutput.parse(output);
        assertEquals(2, hits.size());
        assertEquals("Kündigungsfrist – Allgemeines", hits.get(0).title());
        assertEquals("Die Kündigungsfrist beträgt drei Monate zum Quartalsende. Die Frist gilt für beide Seiten.",
                hits.get(0).snippet());
        assertEquals("Urlaubsregelung", hits.get(1).title());
        assertEquals("Dreißig Tage Urlaub …", hits.get(1).snippet());
    }

    @Test
    public void noHitsAndEmptyOutputGiveNothing() {
        assertTrue(SearchKnowledgeOutput.parse("Treffer: 0\nAnfrage: x\nKeine Treffer.").isEmpty());
        assertTrue(SearchKnowledgeOutput.parse("").isEmpty());
        assertTrue(SearchKnowledgeOutput.parse(null).isEmpty());
    }

    @Test
    public void omittedHitsNoteDoesNotBecomeSnippetText() {
        String output = "Treffer: 2\nAnfrage: x\n\n[1] A\nText:\nText A\n\n… 1 weitere Treffer wegen der Antwortgröße ausgelassen.";
        List<SearchKnowledgeOutput.Hit> hits = SearchKnowledgeOutput.parse(output);
        assertEquals(1, hits.size());
        assertEquals("Text A", hits.get(0).snippet());
    }
}
