package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import com.aresstack.enterpriseai.astu.navigation.SourcePosition;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Analysierte Navigation überlebt den Neustart; ältere Fassungen und kaputte Dateien werden verworfen. */
public class FileDocumentNavigationStoreTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static DocumentNavigation navigation(String capture) {
        NavigationSection topic = new NavigationSection("wiki:1@" + capture + "#p3", "Belegimport",
                NavigationSection.Origin.TOPIC, 2, new SourcePosition("wiki:1", capture, 40, -1, "Der Import prüft"),
                "wiki:1@" + capture + "#h1", Collections.<NavigationSection>emptyList());
        NavigationSection chapter = new NavigationSection("wiki:1@" + capture + "#h1", "Verarbeitung",
                NavigationSection.Origin.HEADING, 1, new SourcePosition("wiki:1", capture, 0, 120, "Verarbeitung"),
                null, Arrays.asList(topic));
        return new DocumentNavigation("wiki:1", capture, "Handbuch", Arrays.asList(chapter),
                DocumentNavigation.Analysis.SEMANTIC, "");
    }

    @Test
    public void navigationSurvivesARestart() throws Exception {
        Path directory = temp.getRoot().toPath().resolve("navigation");
        new FileDocumentNavigationStore(directory).save(navigation("abc"));

        DocumentNavigation loaded = new FileDocumentNavigationStore(directory).load("wiki:1", "abc");
        assertEquals(navigation("abc").toString(), loaded.toString());
        NavigationSection topic = loaded.chapters().get(0).children().get(0);
        assertEquals("Belegimport", topic.label());
        assertTrue(topic.isGenerated());
        assertEquals(navigation("abc").chapters().get(0).children().get(0).position(), topic.position());
        assertEquals("wiki:1@abc#h1", topic.parentId());
    }

    @Test
    public void newerCaptureReplacesTheOlderOne() throws Exception {
        Path directory = temp.getRoot().toPath();
        FileDocumentNavigationStore store = new FileDocumentNavigationStore(directory);
        store.save(navigation("old"));
        store.save(navigation("new"));
        assertNull(store.load("wiki:1", "old"));
        assertEquals("new", store.load("wiki:1", "new").captureId());
        assertEquals(1, directory.toFile().list().length);
    }

    @Test
    public void brokenFileCountsAsMissingAndIsDeleted() throws Exception {
        Path directory = temp.getRoot().toPath();
        FileDocumentNavigationStore store = new FileDocumentNavigationStore(directory);
        store.save(navigation("abc"));
        File file = directory.toFile().listFiles()[0];
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        assertNull(store.load("wiki:1", "abc"));
        assertEquals(0, directory.toFile().list().length);
    }
}
