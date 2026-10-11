package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeStructuralBlock;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeStructuralBlock.Kind;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** Die Strukturblöcke überstehen die Bronze-Kopie (sonst rät die Zerlegung wieder aus dem Text). */
public class KnowledgeBronzeBlocksTest {

    @Test
    public void blocksSurviveTheBronzeCopy() throws Exception {
        List<KnowledgeStructuralBlock> blocks = Arrays.asList(
                new KnowledgeStructuralBlock(Kind.HEADING, "Seite", "Seite"),
                new KnowledgeStructuralBlock(Kind.TABLE_ROW, "Seite", "a | b: c"),
                new KnowledgeStructuralBlock(Kind.CODE, "Seite > Teil: 2", "x = 1;\ny = \\n;"));
        KnowledgeDocument document = KnowledgeDocument.of(KnowledgeResource.builder(
                KnowledgeResourceId.of("wiki:intranet/Seite"), SourceAddress.of("wiki")).title("Seite").build(),
                "# Seite\n\na | b: c", blocks);
        KnowledgeDocument copy = KnowledgeBronze.decode(KnowledgeBronze.encode(document));
        assertEquals(blocks, copy.blocks());
        assertEquals(document.text(), copy.text());
        assertEquals(document.contentHash(), copy.contentHash());
    }
}
