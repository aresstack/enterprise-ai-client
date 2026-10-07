package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeIndexException;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeKeywordQuery;
import com.aresstack.enterpriseai.knowledge.api.KnowledgeSemanticQuery;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.OTHER_SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.SPACE_3D;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.entry;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.resource;
import static com.aresstack.enterpriseai.knowledge.api.testing.KnowledgeIndexTestData.vector;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Adapterspezifisches: Verzeichnislayout, Namespace-Dateien, Beschädigung, Lebenszyklus. */
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
