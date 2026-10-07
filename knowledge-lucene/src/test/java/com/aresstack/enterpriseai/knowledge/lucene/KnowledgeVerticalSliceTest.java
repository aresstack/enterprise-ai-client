package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.EmbeddingBatch;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexEntry;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * AP9-Vertical-Slice: Dokument → Chunks → Embeddings → persistieren → BM25 findet Passage → Semantic Search
 * findet Passage → Prozess (Index) neu öffnen → Ergebnisse weiterhin vorhanden. Embeddings kommen aus dem
 * deterministischen Fake-Port von embedding-api, ohne Netz.
 */
public class KnowledgeVerticalSliceTest {

    private static final String HANDBOOK = "# Betriebshandbuch\n"
            + "Dieses Handbuch beschreibt den Betrieb des Dienstes.\n\n"
            + "## Installation\n"
            + "Die Installation erfolgt über das Paket enterprise-ai-client. "
            + "Danach wird die Konfigurationsdatei angepasst. Java 8 ist Voraussetzung.\n\n"
            + "## Datensicherung\n"
            + "Die Datensicherung läuft jede Nacht um zwei Uhr. Backups werden dreißig Tage aufbewahrt. "
            + "Wiederherstellungen beantragt man beim Betriebsteam.\n\n"
            + "## Störungen\n"
            + "Bei Störungen zuerst die Protokolle prüfen. Hilft das nicht, wird ein Ticket eröffnet.";

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void documentToChunksToEmbeddingsToPersistentHybridSearch() throws Exception {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(64);
        KnowledgeResource resource = KnowledgeResource
                .builder(KnowledgeResourceId.of("wiki:intranet/Betriebshandbuch"), KnowledgeSourceId.of("wiki-intranet"))
                .title("Betriebshandbuch")
                .contentType("text/html")
                .revision(KnowledgeRevision.version("4711"))
                .build();

        // Dokument -> Chunks
        List<KnowledgeChunk> chunks = new KnowledgeChunker(KnowledgeChunkingPolicy.of(40, 1))
                .chunk(KnowledgeDocument.of(resource, HANDBOOK));
        assertTrue("mehrere Abschnitte ergeben mehrere Chunks", chunks.size() >= 4);

        // Chunks -> Embeddings (ein Batch, Reihenfolge garantiert)
        List<String> texts = new ArrayList<String>();
        for (KnowledgeChunk chunk : chunks) {
            texts.add(chunk.textWithHeading());
        }
        EmbeddingBatch batch = embeddings.embed(texts);
        List<KnowledgeIndexEntry> entries = new ArrayList<KnowledgeIndexEntry>();
        for (int i = 0; i < chunks.size(); i++) {
            entries.add(KnowledgeIndexEntry.of(resource, chunks.get(i), batch.get(i)));
        }

        // persistieren
        Path directory = folder.newFolder("knowledge-index").toPath();
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(directory);
        index.replace(embeddings.modelIdentity(), resource.id(), entries);
        assertFoundByBothSearches(index, embeddings);
        index.close();

        // "Prozess neu öffnen": neue Instanz auf demselben Verzeichnis
        LuceneKnowledgeIndex reopened = new LuceneKnowledgeIndex(directory);
        assertFoundByBothSearches(reopened, embeddings);
        assertEquals(chunks.size(), reopened.vectorCount(embeddings.modelIdentity()));

        // Neue Revision ersetzt die alte vollständig
        KnowledgeResource revised = resource.toBuilder().revision(KnowledgeRevision.version("4712")).build();
        List<KnowledgeChunk> revisedChunks = new KnowledgeChunker(KnowledgeChunkingPolicy.of(40, 1))
                .chunk(KnowledgeDocument.of(revised, "# Betriebshandbuch\nDer Dienst wurde abgeschaltet."));
        EmbeddingVector vector = embeddings.embed(Collections.singletonList(revisedChunks.get(0).textWithHeading()))
                .get(0);
        reopened.replace(embeddings.modelIdentity(), revised.id(),
                Collections.singletonList(KnowledgeIndexEntry.of(revised, revisedChunks.get(0), vector)));
        assertEquals(Collections.<KnowledgeSearchHit>emptyList(), reopened.keywordSearch(
                KnowledgeKeywordQuery.of(embeddings.modelIdentity(), "Datensicherung", 5)));
        assertEquals("4712", reopened.keywordSearch(
                KnowledgeKeywordQuery.of(embeddings.modelIdentity(), "abgeschaltet", 5)).get(0)
                .resource().revision().version());
        reopened.close();
    }

    private static void assertFoundByBothSearches(LuceneKnowledgeIndex index, DeterministicEmbeddingPort embeddings) {
        // BM25 findet die Passage zur Datensicherung
        List<KnowledgeSearchHit> keywordHits = index.keywordSearch(
                KnowledgeKeywordQuery.of(embeddings.modelIdentity(), "Wann läuft die Datensicherung?", 3));
        assertTrue(keywordHits.size() >= 1);
        KnowledgeSearchHit best = keywordHits.get(0);
        assertEquals(Collections.singletonList("Datensicherung"), best.chunk().headingPath().subList(1, 2));
        assertTrue(best.chunk().text().contains("jede Nacht um zwei Uhr"));
        assertEquals("Betriebshandbuch", best.resource().title());
        assertEquals("4711", best.resource().revision().version());

        // Semantic Search findet dieselbe Passage über ihr Embedding
        EmbeddingVector query = embeddings.embed(Collections.singletonList(
                "Datensicherung jede Nacht zwei Uhr Backups")).get(0);
        List<KnowledgeSearchHit> semanticHits = index.semanticSearch(KnowledgeSemanticQuery.of(query, 3));
        assertTrue(semanticHits.size() >= 1);
        assertTrue("bester semantischer Treffer ist die Sicherungspassage: " + semanticHits,
                semanticHits.get(0).chunk().text().contains("Datensicherung läuft jede Nacht"));
        assertTrue(semanticHits.get(0).score() > semanticHits.get(semanticHits.size() - 1).score()
                || semanticHits.size() == 1);
    }
}
