package com.aresstack.enterpriseai.astu.acropolis.passage;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeStructuralBlock;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeStructuralBlock.Kind;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Liefert die Quelle Strukturblöcke, sind Tabellenzeile, Zitat und Code eigene Passagen (askai: Struktur zuerst). */
public class PassageStructureTest {

    @Test
    public void sourceBlocksAreHardBoundaries() {
        PassageSegmentation segmentation = new PassageSegmentation(DeterministicEmbeddingPort.withDimension(8));
        List<KnowledgeStructuralBlock> blocks = Arrays.asList(
                new KnowledgeStructuralBlock(Kind.HEADING, "Seite", "Seite"),
                new KnowledgeStructuralBlock(Kind.PARAGRAPH, "Seite", "Ein Satz. Noch ein Satz."),
                new KnowledgeStructuralBlock(Kind.TABLE_ROW, "Seite", "Name | Wert"),
                new KnowledgeStructuralBlock(Kind.QUOTE, "Seite", "Zitat."),
                new KnowledgeStructuralBlock(Kind.CODE, "Seite", "int x;"));
        // Der Text allein (ohne führendes | und ohne >) ließe Tabelle und Zitat im Absatz verschwinden.
        List<SourcePassage> passages = segmentation.segment("wiki:Seite",
                "# Seite\n\nEin Satz. Noch ein Satz.\nName | Wert\nZitat.\nint x;", blocks);
        assertEquals(4, passages.size());
        assertEquals("Name | Wert", passages.get(1).text());
        assertEquals("Zitat.", passages.get(2).text());
        assertEquals("int x;", passages.get(3).text());
        assertEquals("Seite", passages.get(1).headingPath());
    }
}
