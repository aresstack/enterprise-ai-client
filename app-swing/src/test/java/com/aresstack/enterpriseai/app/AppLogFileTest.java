package com.aresstack.enterpriseai.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Die Protokolldatei entsteht im Anwendungsverzeichnis, enthält Meldung und Ursachenkette, und ihr Fehlen ist kein Fehler. */
public class AppLogFileTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void installWritesLinesWithCauseChainIntoTheDirectory() throws Exception {
        Path directory = temp.getRoot().toPath().resolve("logs");
        Handler handler = AppLogFile.install(directory);
        assertNotNull(handler);
        try {
            Logger logger = Logger.getLogger("com.aresstack.enterpriseai.app.AppLogFileTest.probe");
            logger.log(Level.WARNING, "Chat-Anfrage fehlgeschlagen: Probe",
                    new IOException("außen", new IllegalStateException("innen")));
            handler.flush();
            Path file = directory.resolve("enterprise-ai-client.0.log");
            assertTrue(Files.exists(file));
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertTrue(content, content.contains("WARNING probe: Chat-Anfrage fehlgeschlagen: Probe"));
            assertTrue(content, content.contains("java.io.IOException: außen"));
            assertTrue(content, content.contains("Caused by: java.lang.IllegalStateException: innen"));
            assertTrue(content, content.contains("\tat "));
        } finally {
            AppLogFile.uninstall(handler);
        }
        AppLogFile.uninstall(null);
    }

    @Test
    public void formatterKeepsOneLinePerEntryAndLimitsTheTrace() {
        AppLogFile.LineFormatter formatter = new AppLogFile.LineFormatter();
        LogRecord record = new LogRecord(Level.INFO, "Start {0}");
        record.setParameters(new Object[] {"ok"});
        record.setLoggerName("a.b.C");
        String line = formatter.format(record);
        assertTrue(line, line.matches("(?s)\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} INFO C: Start ok\\R"));

        IOException loop = new IOException("loop");
        StringBuilder out = new StringBuilder();
        AppLogFile.LineFormatter.appendTrace(out, loop);
        assertTrue(out.toString().startsWith("java.io.IOException: loop"));
        assertFalse(out.toString().contains("Caused by"));
    }

    @Test
    public void unwritableDirectoryYieldsNoHandlerInsteadOfAnException() throws Exception {
        Path file = temp.newFile("datei-statt-verzeichnis").toPath();
        assertNull(AppLogFile.install(file.resolve("logs")));
    }
}
