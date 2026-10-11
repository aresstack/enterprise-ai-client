package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Passage-IDs hängen wie in askai an der unveränderlichen Fassung (Capture): neuer Inhalt, neue IDs. */
public class PassageCaptureIdTest {

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(16);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("Kaffee", "Kaffeemaschine", "Die Kaffeemaschine wird monatlich entkalkt.");
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));

    @Test
    public void changedContentYieldsNewPassageIdsAndUnchangedContentTheSame() throws Exception {
        KnowledgeResourceId id = wiki.idOf("Kaffee");
        indexing.indexDocumentIfChanged(wiki.load(id));
        List<KnowledgePassageId> first = passageIds(id);
        assertFalse(first.isEmpty());

        indexing.indexDocumentIfChanged(wiki.load(id));
        assertEquals("gleiche Fassung: gleiche IDs", first, passageIds(id));

        wiki.update("Kaffee", "Die Kaffeemaschine wird jetzt wöchentlich entkalkt.");
        indexing.indexDocumentIfChanged(wiki.load(id));
        List<KnowledgePassageId> second = passageIds(id);
        assertEquals(first.get(0).position(), second.get(0).position());
        assertEquals(first.get(0).resourceId(), second.get(0).resourceId());
        assertNotEquals("neue Fassung: andere Capture", first.get(0).capture(), second.get(0).capture());
        assertFalse("alte Passage-ID zeigt nicht still auf neuen Text", second.contains(first.get(0)));
    }

    @Test
    public void passageIdRoundTrips() {
        KnowledgePassageId id = KnowledgePassageId.of(KnowledgeResourceId.of("confluence:47110815"),
                KnowledgePassageId.captureOf("3FA9C2D1E0B4aa00"), 3, "9f2c41ab", "5e07d1c3");
        assertEquals("confluence:47110815@3fa9c2d1e0b4#p3@9f2c41ab-5e07d1c3", id.value());
        assertEquals(id, KnowledgePassageId.parse(id.value()));
        assertTrue(KnowledgePassageId.looksLike(id.value()));
        assertFalse("ohne Capture keine Passage-ID",
                KnowledgePassageId.looksLike("confluence:47110815#p3@9f2c41ab-5e07d1c3"));
        assertFalse(KnowledgePassageId.looksLike("confluence:47110815"));
    }

    private List<KnowledgePassageId> passageIds(KnowledgeResourceId id) {
        List<KnowledgePassageId> ids = new ArrayList<KnowledgePassageId>();
        for (KnowledgeIndexEntry entry : indexing.entriesOf(id)) {
            ids.add(indexing.passageId(entry).get());
        }
        return ids;
    }
}
