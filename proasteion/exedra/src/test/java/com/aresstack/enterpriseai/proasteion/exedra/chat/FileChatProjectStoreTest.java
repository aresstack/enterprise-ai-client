package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.chatproject.ChatProjects;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FileChatProjectStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void projectsAndAssignmentsSurviveARestartInTheirOwnFile() throws Exception {
        Path file = folder.getRoot().toPath().resolve("chat-projects.json");
        ChatProjects projects = new ChatProjects(new FileChatProjectStore(file));
        String a = projects.create("Vertrieb", null);
        String a1 = projects.create("Nord", a);
        projects.updateContext(a1, "Kurz antworten.", Collections.singletonList("Kunde Nordwind"));
        projects.assign("chat-1", a1);

        ChatProjects restarted = new ChatProjects(new FileChatProjectStore(file));
        assertEquals(2, restarted.projects().size());
        assertEquals(a1, restarted.projectOf("chat-1"));
        assertEquals(projects.resolve("chat-1").scope(), restarted.resolve("chat-1").scope());
        assertEquals("Kurz antworten.", restarted.resolve("chat-1").instructions());
    }

    @Test
    public void anUnreadableFileReadsAsNoProjects() throws Exception {
        Path file = folder.getRoot().toPath().resolve("chat-projects.json");
        Files.write(file, "{ kaputt".getBytes(StandardCharsets.UTF_8));
        assertTrue(new ChatProjects(new FileChatProjectStore(file)).projects().isEmpty());
    }
}
