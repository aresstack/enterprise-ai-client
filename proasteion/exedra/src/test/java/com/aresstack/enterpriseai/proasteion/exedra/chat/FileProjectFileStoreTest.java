package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileProjectFileStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void filesAreKeptPerProjectAndRemovable() throws Exception {
        Path source = folder.newFile("plan.txt").toPath();
        Files.write(source, "Zielwert 42".getBytes(StandardCharsets.UTF_8));
        FileProjectFileStore store = new FileProjectFileStore(folder.getRoot().toPath().resolve("project-files"));

        Attachment added = store.add("p-a", source);
        assertEquals(1, store.list("p-a").size());
        assertTrue(store.list("p-b").isEmpty());
        assertEquals("Zielwert 42", new String(store.read("p-a", added.id()), StandardCharsets.UTF_8));

        store.remove("p-a", added.id());
        assertTrue(store.list("p-a").isEmpty());

        store.add("p-a", source);
        store.deleteProject("p-a");
        assertTrue(store.list("p-a").isEmpty());
    }
}
