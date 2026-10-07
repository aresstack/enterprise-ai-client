package com.aresstack.enterpriseai.knowledge.api.testing;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingModelIdentity;
import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeMetadata;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

/** Kleine Bausteine für Index-Tests: Ressourcen, Chunks und Einträge mit handgewählten Vektoren. */
public final class KnowledgeIndexTestData {

    /** Namespace mit 3 Dimensionen. */
    public static final EmbeddingModelIdentity SPACE_3D = EmbeddingModelIdentity.of("test-embedding", 3);
    /** Anderer Namespace mit gleicher Dimension, aber anderem Modell. */
    public static final EmbeddingModelIdentity OTHER_SPACE_3D = EmbeddingModelIdentity.of("other-embedding", 3);
    /** Namespace mit anderer Dimension. */
    public static final EmbeddingModelIdentity SPACE_2D = EmbeddingModelIdentity.of("test-embedding", 2);

    private KnowledgeIndexTestData() {
    }

    public static KnowledgeResource resource(String id, String source) {
        return KnowledgeResource.builder(KnowledgeResourceId.of(id), KnowledgeSourceId.of(source))
                .title("Titel " + id)
                .build();
    }

    /** Ressource mit allen optionalen Feldern, um die verlustfreie Ablage im Index zu prüfen. */
    public static KnowledgeResource richResource(String id, String source) {
        return KnowledgeResource.builder(KnowledgeResourceId.of(id), KnowledgeSourceId.of(source))
                .title("Betriebshandbuch Größe")
                .contentType("text/html")
                .revision(KnowledgeRevision.of(Instant.parse("2026-10-01T08:30:00Z"), "12"))
                .parentId(KnowledgeResourceId.of(id + "-parent"))
                .scope("OPS")
                .location(URI.create("https://wiki.example/pages/" + Math.abs(id.hashCode())))
                .metadata(KnowledgeMetadata.empty().with("space", "OPS").with("labels", "howto,java"))
                .build();
    }

    public static KnowledgeChunk chunk(KnowledgeResource resource, int ordinal, String text, String... headings) {
        return new KnowledgeChunk(KnowledgeChunkId.of(resource.id(), ordinal), resource.sourceId(),
                headings.length == 0 ? Collections.<String>emptyList() : Arrays.asList(headings), text,
                text.split("\\s+").length);
    }

    public static EmbeddingVector vector(EmbeddingModelIdentity space, float... values) {
        return EmbeddingVector.of(space, values);
    }

    public static KnowledgeIndexEntry entry(KnowledgeResource resource, int ordinal, String text,
                                            EmbeddingVector vector) {
        return KnowledgeIndexEntry.of(resource, chunk(resource, ordinal, text), vector);
    }
}
