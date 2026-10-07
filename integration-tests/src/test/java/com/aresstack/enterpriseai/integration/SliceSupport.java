package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.app.ui.chat.SourceReference;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.integration.agent.KnowledgeDemoAgentMain;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Gemeinsame Helfer der Slice-Tests: Übergabe an den Event Dispatch Thread, Warten mit Frist ohne Blockieren
 * auf dem EDT (Latches statt {@code sleep}, kein {@code invokeAndWait} in Warteschleifen), Kindprozess-Parameter
 * aus Gradle und die Prüfung, dass kein Secret in Oberflächentexten auftaucht.
 */
final class SliceSupport {

    static final long TIMEOUT_SECONDS = 60L;

    private SliceSupport() {
    }

    /** Die JVM, mit der Agentenprozesse gestartet werden (Gradle: {@code acp.agent.java.home}, sonst die eigene). */
    static String javaBinary() {
        String home = System.getProperty("acp.agent.java.home", System.getProperty("java.home"));
        return home + File.separator + "bin" + File.separator
                + (System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java");
    }

    /**
     * Das Demo-Agent-Jar aus acp-demo-agent (Gradle: {@code acp.demo.agent.jar}). Im Gradle-Build
     * ({@code acp.roundtrip.required=true}) ist ein fehlendes Jar ein Fehler, außerhalb (IDE) wird der Test
     * übersprungen.
     */
    static String demoAgentJar() {
        boolean required = Boolean.getBoolean("acp.roundtrip.required");
        String jar = System.getProperty("acp.demo.agent.jar");
        boolean ready = jar != null && new File(jar).isFile() && new File(javaBinary()).isFile();
        if (required && !ready) {
            fail("Voraussetzungen für den Prozess-Roundtrip fehlen: jar=" + jar + ", java=" + javaBinary());
        }
        assumeTrue("Demo-Agent-Jar nicht gesetzt (über Gradle starten)", ready);
        return jar;
    }

    /** Der Test-Klassenpfad dieses Moduls für Kindprozesse (Gradle: {@code integration.agent.classpath}). */
    static String agentClasspath() {
        boolean required = Boolean.getBoolean("acp.roundtrip.required");
        String classpath = System.getProperty("integration.agent.classpath");
        if (classpath == null || classpath.isEmpty()) {
            classpath = System.getProperty("java.class.path");
        }
        boolean ready = classpath != null && !classpath.isEmpty() && new File(javaBinary()).isFile();
        if (required && !ready) {
            fail("Voraussetzungen für den Testagenten fehlen: classpath leer oder java=" + javaBinary());
        }
        assumeTrue("Klassenpfad für den Testagenten nicht verfügbar", ready);
        return classpath;
    }

    /**
     * Startparameter für den Testagenten aus Slice G ({@link KnowledgeDemoAgentMain}): dieselbe JVM wie der
     * Build, der Test-Klassenpfad dieses Moduls über ein Pathing-Jar (Manifest {@code Class-Path}), damit die
     * Kommandozeile auch unter Windows kurz bleibt. Keine Umgebungsvariablen hier: den MCP-Endpoint trägt der
     * {@code AcpAgentLauncher} des Hosts ein.
     */
    static AgentLaunchSpec knowledgeAgentLaunchSpec(Path workDir) throws IOException {
        Path jar = pathingJar(workDir.resolve("knowledge-agent-classpath.jar"), agentClasspath());
        return new AgentLaunchSpec(javaBinary(),
                Arrays.asList("-cp", jar.toString(), KnowledgeDemoAgentMain.class.getName()), null);
    }

    /** Ein leeres Jar, dessen Manifest den Klassenpfad als {@code file:}-URLs trägt (Verzeichnisse mit Schrägstrich). */
    static Path pathingJar(Path target, String classpath) throws IOException {
        StringBuilder urls = new StringBuilder();
        for (String entry : classpath.split(Pattern.quote(File.pathSeparator))) {
            if (entry.isEmpty()) {
                continue;
            }
            File file = new File(entry);
            if (!file.exists()) {
                continue;
            }
            if (urls.length() > 0) {
                urls.append(' ');
            }
            urls.append(file.toURI().toString());
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, urls.toString());
        Files.createDirectories(target.getParent());
        JarOutputStream out = new JarOutputStream(Files.newOutputStream(target), manifest);
        out.close();
        return target;
    }

    static <T> T onEdt(final Callable<T> callable) throws Exception {
        final AtomicReference<T> result = new AtomicReference<T>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.set(callable.call());
                    } catch (Throwable t) {
                        failure.set(t);
                    }
                }
            });
        } catch (InvocationTargetException e) {
            failure.set(e.getCause());
        }
        Throwable t = failure.get();
        if (t instanceof Exception) {
            throw (Exception) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        return result.get();
    }

    static void runOnEdt(final Runnable runnable) throws Exception {
        onEdt(new Callable<Void>() {
            @Override
            public Void call() {
                runnable.run();
                return null;
            }
        });
    }

    /**
     * Wartet außerhalb des EDT, bis das Model nicht mehr streamt: ein Listener auf dem EDT setzt den Latch, der
     * Testthread blockiert nur im {@code await} mit Frist (Muster aus AP21).
     */
    static void awaitIdle(final ChatShellModel model) throws Exception {
        final CountDownLatch idle = new CountDownLatch(1);
        final ChatShellModelListener listener = new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
            }

            @Override
            public void stateChanged() {
                if (!model.isStreaming()) {
                    idle.countDown();
                }
            }
        };
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                model.addListener(listener);
                if (!model.isStreaming()) {
                    idle.countDown();
                }
            }
        });
        boolean reached = idle.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                model.removeListener(listener);
            }
        });
        assertTrue("Model streamt nach " + TIMEOUT_SECONDS + " s noch", reached);
    }

    /** Wartet, bis irgendein Eintrag den Text enthält (Zwischenstand einer streamenden Antwort). */
    static void awaitTextContaining(final ChatShellModel model, final String text) throws Exception {
        final CountDownLatch seen = new CountDownLatch(1);
        final ChatShellModelListener listener = new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
                check(entry);
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
                check(entry);
            }

            @Override
            public void stateChanged() {
            }

            private void check(TranscriptEntry entry) {
                if (entry.getText().contains(text)) {
                    seen.countDown();
                }
            }
        };
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                model.addListener(listener);
                for (TranscriptEntry entry : model.getEntries()) {
                    if (entry.getText().contains(text)) {
                        seen.countDown();
                    }
                }
            }
        });
        boolean reached = seen.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                model.removeListener(listener);
            }
        });
        assertTrue("Text '" + text + "' erschien nicht innerhalb von " + TIMEOUT_SECONDS + " s", reached);
    }

    /** Wartet mit Frist auf eine Bedingung, die auf dem EDT ausgewertet wird (kurze Abfragen, kein Dauerlauf). */
    static void await(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (onEdt(condition)) {
                return;
            }
            Thread.sleep(20L);
        }
        fail("Bedingung nicht innerhalb von " + TIMEOUT_SECONDS + " s erfüllt: " + what);
    }

    /** Eine Momentaufnahme des Verlaufs vom EDT. */
    static List<TranscriptEntry> entries(final ChatShellModel model) throws Exception {
        return onEdt(new Callable<List<TranscriptEntry>>() {
            @Override
            public List<TranscriptEntry> call() {
                return new ArrayList<TranscriptEntry>(model.getEntries());
            }
        });
    }

    static TranscriptEntry lastEntry(ChatShellModel model) throws Exception {
        List<TranscriptEntry> all = entries(model);
        assertFalse("Verlauf ist leer", all.isEmpty());
        return all.get(all.size() - 1);
    }

    /** Kein Secret in Texten, Fehlermeldungen oder Quellenangaben des Verlaufs (Nachtrag: nie in der Historie). */
    static void assertNoSecretInTranscript(ChatShellModel model, String... secrets) throws Exception {
        for (TranscriptEntry entry : entries(model)) {
            assertNoSecret(entry.getText(), secrets);
            assertNoSecret(entry.getFailureMessage(), secrets);
            assertNoSecret(entry.getActivity(), secrets);
            for (SourceReference reference : entry.getSources()) {
                assertNoSecret(reference.toString(), secrets);
                assertNoSecret(reference.getTitle(), secrets);
                assertNoSecret(reference.getLocation(), secrets);
            }
        }
    }

    /**
     * Schlägt die Prüfung fehl, enthält {@code haystack} das Secret; die Fehlermeldung darf es deshalb nicht
     * wiederholen (sie landet in der JUnit- und CI-Ausgabe). Genannt werden nur Nummer, Länge und Position des
     * Secrets sowie ein Auszug, in dem alle geprüften Secrets geschwärzt sind.
     */
    static void assertNoSecret(String haystack, String... secrets) {
        if (haystack == null) {
            return;
        }
        for (int i = 0; i < secrets.length; i++) {
            String secret = secrets[i];
            if (secret != null && !secret.isEmpty() && haystack.contains(secret)) {
                fail("Secret Nr. " + (i + 1) + " (Länge " + secret.length() + ") taucht an Position "
                        + haystack.indexOf(secret) + " auf: " + describeRedacted(haystack, secrets));
            }
        }
    }

    private static String describeRedacted(String haystack, String... secrets) {
        String redacted = haystack;
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) {
                redacted = redacted.replace(secret, "«geschwärzt»");
            }
        }
        return redacted.length() > 120 ? redacted.substring(0, 120) + "…" : redacted;
    }
}
