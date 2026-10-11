package com.aresstack.enterpriseai.proasteion.application.state;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ApplicationStateServiceTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void valuesSurviveARestart() throws Exception {
        Path file = folder.getRoot().toPath().resolve("application-state.json");
        new ApplicationStateService(file).putAndSave("chat.burgerPinned", "true");
        new ApplicationStateService(file).putAndSave("window.width", "1200");

        ApplicationStateService reloaded = new ApplicationStateService(file);
        assertTrue(reloaded.getBoolean("chat.burgerPinned", false));
        assertEquals(1200, reloaded.getInt("window.width", -1));
    }

    @Test
    public void corruptFileStartsEmpty() throws Exception {
        Path file = folder.getRoot().toPath().resolve("application-state.json");
        Files.write(file, "{kaputt".getBytes(StandardCharsets.UTF_8));
        assertEquals(-1, new ApplicationStateService(file).getInt("window.width", -1));
    }
}
