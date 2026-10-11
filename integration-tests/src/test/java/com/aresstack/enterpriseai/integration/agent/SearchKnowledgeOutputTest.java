package com.aresstack.enterpriseai.integration.agent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Der Parser des Testagenten gegen das Ausgabeformat von {@code search_knowledge} (AP20). */
public class SearchKnowledgeOutputTest {

    @Test
    public void readsTitleAndSnippetPerHit() {
        String output = "Query: Kündigungsfrist\n"
                + "\n"
                + "[1] Kündigungsfrist – Allgemeines\n"
                + "id: memory:handbuch/kuendigung\n"
                + "source: handbuch\n"
                + "Die Kündigungsfrist beträgt drei Monate zum Quartalsende.\n"
                + "Die Frist gilt für beide Seiten.\n"
                + "\n"
                + "[2] Urlaubsregelung\n"
                + "id: memory:handbuch/urlaub\n"
                + "source: handbuch (not read yet)\n"
                + "Dreißig Tage Urlaub …\n"
                + "\n"
                + "Read a hit completely with knowledge_read — example: id=\"memory:handbuch/kuendigung\"";
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
        assertTrue(SearchKnowledgeOutput.parse("Query: x\nNo hits. Try other words").isEmpty());
        assertTrue(SearchKnowledgeOutput.parse("").isEmpty());
        assertTrue(SearchKnowledgeOutput.parse(null).isEmpty());
    }
}
