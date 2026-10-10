package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Erststart und fehlerhafte Datei: headless wie bisher (Vorlage, Exit), mit Oberfläche über den Dialog. */
public class ConfigurationStartupTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    /** Ein Dialog-Ersatz: merkt sich, womit er geöffnet wurde, und speichert ein vorgegebenes Formular. */
    private static final class ScriptedUi implements ConfigurationStartup.SettingsUi {
        final List<SettingsForm> initials = new ArrayList<SettingsForm>();
        final List<List<String>> problems = new ArrayList<List<String>>();
        final List<Boolean> firstStarts = new ArrayList<Boolean>();
        final FileSettingsActions actions;
        final SettingsForm toSave;

        ScriptedUi(FileSettingsActions actions, SettingsForm toSave) {
            this.actions = actions;
            this.toSave = toSave;
        }

        @Override
        public SettingsForm edit(SettingsForm initial, List<String> shownProblems, boolean firstStart) {
            initials.add(initial);
            problems.add(shownProblems);
            firstStarts.add(firstStart);
            if (toSave == null) {
                return null;
            }
            try {
                actions.save(toSave);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return toSave;
        }
    }

    /** Wie die TLS-Vertrauensregel des Starts: eine eingetragene CA-Datei, die es nicht gibt, ist ein Problem. */
    private static final ConfigurationCheck CA_FILE_MUST_EXIST = new ConfigurationCheck() {
        @Override
        public void verify(AppConfig config) {
            Path file = config.network().caCertificatesFile();
            if (file != null && !Files.exists(file)) {
                throw new AppConfigException("network.tls.caCertificatesFile: Datei nicht lesbar (NoSuchFileException)");
            }
        }
    };

    private Path configPath() {
        return tmp.getRoot().toPath().resolve("home").resolve("enterprise-ai-client.properties");
    }

    private static SettingsForm completeForm() {
        return SettingsMapper.firstStartDefaults().toBuilder()
                .chatBaseUrl("http://127.0.0.1:9/v1").chatModel("test-chat")
                .embeddingModel("test-embedding").embeddingDimension("8")
                .keePassEnabled(false).proxyMode(SettingsForm.PROXY_DISABLED).build();
    }

    @Test
    public void headlessWithoutFileWritesTheTemplateAndFails() throws Exception {
        ConfigurationFile file = new ConfigurationFile(configPath());
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, null);
        assertFalse(outcome.isStarted());
        assertFalse(outcome.isCancelled());
        assertTrue(outcome.message(), outcome.message().contains("Vorlage wurde angelegt"));
        assertTrue(outcome.message(), outcome.message().contains(configPath().toString()));
        assertEquals(AppConfigLoader.exampleConfiguration(),
                new String(Files.readAllBytes(configPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void headlessWithBrokenFileReportsTheProblems() throws Exception {
        Files.createDirectories(configPath().getParent());
        Files.write(configPath(), "chat.model=x\n".getBytes(StandardCharsets.UTF_8));
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(new ConfigurationFile(configPath()), null);
        assertFalse(outcome.isStarted());
        assertTrue(outcome.message(), outcome.message().contains("hat Fehler"));
        assertTrue(outcome.message(), outcome.message().contains("chat.baseUrl"));
    }

    @Test
    public void loadableFileStartsWithoutAnyDialog() throws Exception {
        ConfigurationFile file = new ConfigurationFile(configPath());
        new FileSettingsActions(file, null, DIRECT, DIRECT).save(completeForm());
        ScriptedUi ui = new ScriptedUi(null, null);
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, ui);
        assertTrue(outcome.isStarted());
        assertEquals("test-chat", outcome.config().chat().model());
        assertTrue(ui.initials.isEmpty());
    }

    @Test
    public void firstStartWithUiOpensEmptyDefaultsAndStartsAfterSaving() throws Exception {
        ConfigurationFile file = new ConfigurationFile(configPath());
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        ScriptedUi ui = new ScriptedUi(actions, completeForm());
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, ui);
        assertTrue(outcome.message(), outcome.isStarted());
        assertEquals(1, ui.initials.size());
        assertEquals("", ui.initials.get(0).chatBaseUrl());
        assertEquals("keepass:Enterprise AI API", ui.initials.get(0).chatApiKeyRef());
        assertTrue(ui.problems.get(0).isEmpty());
        assertTrue(ui.firstStarts.get(0));
        assertTrue("die Datei entsteht erst beim Speichern", file.exists());
        assertEquals("test-chat", outcome.config().chat().model());
        String text = new String(Files.readAllBytes(configPath()), StandardCharsets.UTF_8);
        assertTrue("die Kommentare der Vorlage bleiben erhalten", text.contains("#"));
        assertNull("Beispielquellen der Vorlage sind nicht aktiv", file.read().getProperty("source.wiki.type"));
    }

    @Test
    public void cancellingTheFirstStartIsNotAnError() throws Exception {
        ConfigurationFile file = new ConfigurationFile(configPath());
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, new ScriptedUi(null, null));
        assertFalse(outcome.isStarted());
        assertTrue(outcome.isCancelled());
        assertTrue(outcome.message(), outcome.message().contains(configPath().toString()));
        assertFalse("ohne Speichern keine Datei, sonst lädt der nächste Start die Beispielwerte der Vorlage",
                file.exists());
    }

    @Test
    public void checkProblemsCountLikeLoaderProblems() throws Exception {
        ConfigurationFile file = new ConfigurationFile(configPath());
        FileSettingsActions actions = new FileSettingsActions(file, null, CA_FILE_MUST_EXIST, DIRECT, DIRECT);
        new FileSettingsActions(file, null, DIRECT, DIRECT)
                .save(completeForm().toBuilder().caCertificatesFile(tmp.getRoot().toPath().resolve("fehlt.pem")
                        .toString()).build());

        ConfigurationStartup.Outcome headless = ConfigurationStartup.obtain(file, null, CA_FILE_MUST_EXIST);
        assertFalse(headless.isStarted());
        assertTrue(headless.message(), headless.message().contains("hat Fehler"));
        assertTrue(headless.message(), headless.message().contains("network.tls.caCertificatesFile"));
        assertFalse(headless.message(), headless.message().contains("fehlt.pem"));

        SettingsForm fixed = SettingsMapper.fromProperties(file.read()).toBuilder().caCertificatesFile("").build();
        ScriptedUi ui = new ScriptedUi(actions, fixed);
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, ui, CA_FILE_MUST_EXIST);
        assertTrue(outcome.isStarted());
        assertEquals(1, ui.problems.get(0).size());
        assertTrue(ui.problems.get(0).get(0), ui.problems.get(0).get(0)
                .startsWith("CA-Datei (network.tls.caCertificatesFile): "));
        assertNull(outcome.config().network().caCertificatesFile());
    }

    @Test
    public void brokenFileOpensTheDialogWithItsValuesAndProblems() throws Exception {
        Files.createDirectories(configPath().getParent());
        Files.write(configPath(), ("chat.model=altes-modell\nchat.apiKeyRef=keepass:Enterprise AI API\n"
                + "embedding.model=e\nembedding.dimension=8\n"
                + "security.keepass.enabled=false\nnetwork.proxy.mode=NONE\n").getBytes(StandardCharsets.UTF_8));
        ConfigurationFile file = new ConfigurationFile(configPath());
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        SettingsForm fixed = SettingsMapper.fromProperties(file.read()).toBuilder()
                .chatBaseUrl("http://127.0.0.1:9/v1").build();
        ScriptedUi ui = new ScriptedUi(actions, fixed);
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, ui);
        assertTrue(outcome.isStarted());
        assertEquals("altes-modell", ui.initials.get(0).chatModel());
        assertFalse(ui.firstStarts.get(0));
        assertEquals(1, ui.problems.get(0).size());
        assertTrue(ui.problems.get(0).get(0), ui.problems.get(0).get(0).startsWith("Basis-URL des KI-Dienstes (chat.baseUrl)"));
        assertEquals("altes-modell", outcome.config().chat().model());
    }
}
