package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSearchHit;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.OTHER_SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Adapterspezifisches: Verzeichnislayout, Namespace-Dateien, Beschädigung, Revisionsabgleich, Lebenszyklus. */
public class LuceneKnowledgeIndexTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void eachNamespaceHasItsOwnTextIndexAndVectorFile() throws Exception {
        Path root = folder.newFolder("index").toPath();
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(root);
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Arrays.asList(
                entry(a, 0, "eins", vector(SPACE_3D, 1, 0, 0)),
                entry(a, 1, "zwei", vector(OTHER_SPACE_3D, 0, 1, 0))));

        assertTrue(Files.isDirectory(root.resolve("text").resolve(SPACE_3D.fingerprint())));
        assertTrue(Files.isDirectory(root.resolve("text").resolve(OTHER_SPACE_3D.fingerprint())));
        assertTrue(Files.isRegularFile(root.resolve("vectors").resolve(SPACE_3D.fingerprint() + ".vec")));
        assertTrue(Files.isRegularFile(root.resolve("vectors").resolve(OTHER_SPACE_3D.fingerprint() + ".vec")));
        assertEquals(1, index.vectorCount(SPACE_3D));

        index.remove(a.id());
        assertFalse("leerer Namespace hinterlässt keine Vektordatei",
                Files.exists(root.resolve("vectors").resolve(SPACE_3D.fingerprint() + ".vec")));
        index.close();
    }

    @Test
    public void vectorFileOfAnotherWorldIsRejectedNotCompared() throws Exception {
        Path root = folder.newFolder("index").toPath();
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(root);
        index.index(Collections.singletonList(entry(resource("wiki:x/A", "wiki"), 0, "eins",
                vector(OTHER_SPACE_3D, 1, 0, 0))));
        index.close();
        // Datei unter falschem Namen ablegen: Kopf (OTHER_SPACE_3D) passt nicht zum Namespace (SPACE_3D).
        Path vectors = root.resolve("vectors");
        Files.move(vectors.resolve(OTHER_SPACE_3D.fingerprint() + ".vec"), vectors.resolve(SPACE_3D.fingerprint() + ".vec"));

        LuceneKnowledgeIndex reopened = new LuceneKnowledgeIndex(root);
        try {
            reopened.semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 5));
            fail("fremde Embedding-Welt muss abgelehnt werden");
        } catch (KnowledgeIndexException expected) {
            assertTrue(expected.getMessage().contains("anderen Embedding-Welt"));
        }
        reopened.close();
    }

    @Test
    public void corruptVectorFileIsReportedAndRebuildRecovers() throws Exception {
        Path root = folder.newFolder("index").toPath();
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(root);
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Collections.singletonList(entry(a, 0, "eins", vector(SPACE_3D, 1, 0, 0))));
        index.close();
        Files.write(root.resolve("vectors").resolve(SPACE_3D.fingerprint() + ".vec"), new byte[]{1, 2, 3});

        LuceneKnowledgeIndex reopened = new LuceneKnowledgeIndex(root);
        try {
            reopened.semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 5));
            fail();
        } catch (KnowledgeIndexException expected) {
            // erwartet
        }
        reopened.rebuild(Collections.singletonList(entry(a, 0, "eins", vector(SPACE_3D, 1, 0, 0))));
        assertEquals(1, reopened.semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 5)).size());
        reopened.close();
    }

    @Test
    public void negativeEntryCountIsReportedAsCorruption() throws Exception {
        Path root = folder.newFolder("index").toPath();
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(root);
        KnowledgeResource a = resource("wiki:x/A", "wiki");
        index.index(Collections.singletonList(entry(a, 0, "eins", vector(SPACE_3D, 1, 0, 0))));
        index.close();
        Path file = root.resolve("vectors").resolve(SPACE_3D.fingerprint() + ".vec");
        byte[] bytes = Files.readAllBytes(file);
        int entrySize = 2 + "wiki:x/A#chunk-0".length() + 2 + "wiki".length() + 2 + 64 + 4 * 3;
        ByteBuffer.wrap(bytes).putInt(bytes.length - entrySize - 4, -1);
        Files.write(file, bytes);

        LuceneKnowledgeIndex reopened = new LuceneKnowledgeIndex(root);
        try {
            reopened.semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 5));
            fail("negative Anzahl muss als Beschädigung gemeldet werden");
        } catch (KnowledgeIndexException expected) {
            assertTrue(expected.getMessage().contains("beschädigt"));
        }
        reopened.close();
    }

    @Test
    public void vectorOfANewerRevisionIsNotPairedWithOldText() throws Exception {
        Path root = folder.newFolder("index").toPath();
        KnowledgeResource old = resource("wiki:x/A", "wiki").toBuilder().revision(KnowledgeRevision.version("1"))
                .build();
        KnowledgeResource current = old.toBuilder().revision(KnowledgeRevision.version("2")).build();
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(root);
        index.index(Collections.singletonList(entry(old, 0, "alter Text", vector(SPACE_3D, 1, 0, 0))));
        index.close();
        // Abbruch nach dem Vektor-, vor dem Text-Schreiben eines Replace nachstellen.
        new FileVectorIndex(root.resolve("vectors")).replace(SPACE_3D, current.id(),
                Collections.singletonList(entry(current, 0, "neuer Text", vector(SPACE_3D, 1, 0, 0))));

        LuceneKnowledgeIndex reopened = new LuceneKnowledgeIndex(root);
        assertTrue("Vektor der neuen Revision darf keinen alten Text zitieren",
                reopened.semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 5)).isEmpty());
        reopened.replace(SPACE_3D, current.id(),
                Collections.singletonList(entry(current, 0, "neuer Text", vector(SPACE_3D, 1, 0, 0))));
        List<KnowledgeSearchHit> hits =
                reopened.semanticSearch(KnowledgeSemanticQuery.of(vector(SPACE_3D, 1, 0, 0), 5));
        assertEquals(1, hits.size());
        assertEquals("neuer Text", hits.get(0).chunk().text());
        reopened.close();
    }

    @Test
    public void recursiveDeleteRemovesSymbolicLinksWithoutFollowingThem() throws Exception {
        Path outside = folder.newFolder("outside").toPath();
        Path keep = Files.write(outside.resolve("keep.txt"), new byte[]{42});
        Path tree = folder.newFolder("tree").toPath();
        Files.createSymbolicLink(tree.resolve("link"), outside);

        IndexFiles.deleteRecursively(tree);

        assertFalse(Files.exists(tree, LinkOption.NOFOLLOW_LINKS));
        assertTrue("Ziel des Links bleibt unangetastet", Files.exists(keep));
    }

    @Test
    public void closedIndexRejectsOperationsAndCloseIsIdempotent() throws Exception {
        LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(folder.newFolder("index").toPath());
        index.close();
        index.close();
        try {
            index.keywordSearch(KnowledgeKeywordQuery.of(SPACE_3D, "x", 1));
            fail();
        } catch (IllegalStateException expected) {
            // erwartet
        }
    }

    @Test
    public void listEncodingRoundTripsArbitraryValues() {
        java.util.List<String> values = Arrays.asList("", "a:b", "12:x", "Größe\nzwei", "#");
        assertEquals(values, LuceneDocuments.decode(LuceneDocuments.encode(values)));
    }
}
