package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelation;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationKind;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeRelationQuery;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.chunk;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;

public class IndexKnowledgeRelationsTest {

    @Test
    public void neighborsComeFromStoredVectorsAndStructure() {
        KnowledgeResource parent = resource("wiki:P", "wiki");
        KnowledgeResource page = KnowledgeResource.builder(KnowledgeResourceId.of("wiki:A"), SourceAddress.of("wiki"))
                .title("A").parentId(parent.id()).build();
        KnowledgeResource other = resource("wiki:B", "wiki");
        KnowledgeResource unrelated = resource("wiki:C", "wiki");
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        index.index(Arrays.asList(
                section(page, 0, 1f, 0f, 0f),
                section(page, 1, 1f, 0f, 0f), // Overlap-Nachbar: gleich, aber kein SIMILAR_TO
                section(page, 2, 0f, 1f, 0f),
                section(page, 3, 0f, 0f, 1f),
                KnowledgeIndexEntry.of(other, chunk(other, 0, "Ähnlich"), vector(SPACE_3D, 0.95f, 0.05f, 0f)),
                KnowledgeIndexEntry.of(unrelated, chunk(unrelated, 0, "Anders"), vector(SPACE_3D, 0f, 1f, 0f)),
                KnowledgeIndexEntry.of(parent, chunk(parent, 0, "Eltern"), vector(SPACE_3D, 0f, 0f, 1f))));

        List<KnowledgeRelation> neighbors = new IndexKnowledgeRelations(index)
                .neighbors(KnowledgeChunkId.of(page.id(), 0), KnowledgeRelationQuery.of(SPACE_3D));

        List<String> edges = new ArrayList<String>();
        for (KnowledgeRelation relation : neighbors) {
            edges.add(relation.kind() + " " + relation.to());
        }
        assertEquals(Arrays.asList(
                KnowledgeRelationKind.SIMILAR_TO + " wiki:B#chunk-0",
                KnowledgeRelationKind.PARENT + " wiki:P#chunk-0",
                KnowledgeRelationKind.NEXT + " wiki:A#chunk-1",
                KnowledgeRelationKind.SAME_SECTION + " wiki:A#chunk-2",
                KnowledgeRelationKind.SAME_SECTION + " wiki:A#chunk-3"), edges);
    }

    private static KnowledgeIndexEntry section(KnowledgeResource resource, int ordinal, float... values) {
        return KnowledgeIndexEntry.of(resource, chunk(resource, ordinal, "Text " + ordinal, "Abschnitt"),
                vector(SPACE_3D, values));
    }
}
