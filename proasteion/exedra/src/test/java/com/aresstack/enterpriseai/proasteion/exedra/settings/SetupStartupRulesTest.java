package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
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

/**
 * Die sechs Auslöseregeln des Einrichtungsassistenten über {@code setup.completed} im Anwendungszustand, mit dem
 * echten {@link ApplicationStateService} auf Platte und einem Assistenten-Ersatz.
 */
public class SetupStartupRulesTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = Runnable::run;

    /** Assistent-Ersatz: merkt sich Aufrufe und den Einrichtungsstand, der während des Laufs auf Platte stand. */
    private final class ScriptedSetup implements ConfigurationStartup.SetupUi {
        final List<SettingsForm> initials = new ArrayList<SettingsForm>();
        final List<String> stateWhileRunning = new ArrayList<String>();
        private final boolean finish;

        ScriptedSetup(boolean finish) {
            this.finish = finish;
        }

        @Override
        public boolean run(SettingsForm initial) {
            initials.add(initial);
            stateWhileRunning.add(new ApplicationStateService(statePath()).get(ConfigurationStartup.SETUP_COMPLETED,
                    null));
            if (!finish) {
                return false;
            }
            try {
                new FileSettingsActions(new ConfigurationFile(configPath()), null, DIRECT, DIRECT).save(completeForm());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return true;
        }
    }

    /** Der normale Einstellungen-Dialog darf bei den Regeln nie erscheinen. */
    private static final ConfigurationStartup.SettingsUi NO_DIALOG = (initial, problems, firstStart) -> {
        throw new AssertionError("Einstellungen-Dialog statt Assistent geöffnet");
    };

    private Path configPath() {
        return tmp.getRoot().toPath().resolve("home").resolve("enterprise-ai-client.properties");
    }

    private Path statePath() {
        return tmp.getRoot().toPath().resolve("home").resolve("application-state.json");
    }

    private ConfigurationStartup.SetupState state() {
        return ConfigurationStartup.setupState(new ApplicationStateService(statePath()));
    }

    private static SettingsForm completeForm() {
        return SettingsMapper.firstStartDefaults().toBuilder()
                .chatBaseUrl("http://127.0.0.1:9/v1").chatModel("test-chat")
                .embeddingModel("test-embedding").embeddingDimension("8")
                .keePassEnabled(false).proxyMode(SettingsForm.PROXY_DISABLED).build();
    }

    private void writeLoadableConfig() throws IOException {
        new FileSettingsActions(new ConfigurationFile(configPath()), null, DIRECT, DIRECT).save(completeForm());
    }

    private ConfigurationStartup.Outcome start(ConfigurationStartup.SetupUi setup) {
        return ConfigurationStartup.obtain(new ConfigurationFile(configPath()), NO_DIALOG, ConfigurationCheck.none(),
                state(), setup);
    }

    /** Regel 1: Konfigurationsdatei fehlt → Assistent immer, und setup.completed=false steht während er läuft. */
    @Test
    public void missingFileOpensTheWizardAndSetsCompletedFalse() {
        ScriptedSetup setup = new ScriptedSetup(false);
        start(setup);
        assertEquals(1, setup.initials.size());
        assertEquals("false", setup.stateWhileRunning.get(0));
        assertEquals("keepass:Enterprise AI API", setup.initials.get(0).chatApiKeyRef());
    }

    /** Regel 2: erfolgreich beendet → setup.completed=true, und der Start geht ohne Neustart weiter. */
    @Test
    public void finishedWizardSetsCompletedTrueAndStarts() {
        ScriptedSetup setup = new ScriptedSetup(true);
        ConfigurationStartup.Outcome outcome = start(setup);
        assertTrue(outcome.message(), outcome.isStarted());
        assertEquals("test-chat", outcome.config().chat().model());
        assertEquals(Boolean.TRUE, state().completed());
    }

    /**
     * Regel 3: abgebrochen → nicht true; beendet sich die Anwendung während der Einrichtung, steht auf Platte schon
     * false (siehe Regel 1), und der nächste Start öffnet den Assistenten erneut.
     */
    @Test
    public void cancelledWizardLeavesCompletedFalseAndReopensNextStart() {
        ConfigurationStartup.Outcome outcome = start(new ScriptedSetup(false));
        assertFalse(outcome.isStarted());
        assertTrue(outcome.isCancelled());
        assertEquals(Boolean.FALSE, state().completed());
        ScriptedSetup again = new ScriptedSetup(false);
        start(again);
        assertEquals(1, again.initials.size());
    }

    /** Regel 4: Datei vorhanden, Schlüssel fehlt → bestehende Installation, kein Assistent, Schlüssel bleibt weg. */
    @Test
    public void existingInstallationWithoutStateKeyStartsWithoutWizard() throws Exception {
        writeLoadableConfig();
        ScriptedSetup setup = new ScriptedSetup(true);
        ConfigurationStartup.Outcome outcome = start(setup);
        assertTrue(outcome.message(), outcome.isStarted());
        assertTrue(setup.initials.isEmpty());
        assertNull(state().completed());
    }

    /** Regel 5: Datei vorhanden und setup.completed=false → der unterbrochene Assistent öffnet sich mit ihren Werten. */
    @Test
    public void interruptedSetupReopensWithTheValuesOfTheFile() throws Exception {
        writeLoadableConfig();
        state().setCompleted(false);
        ScriptedSetup setup = new ScriptedSetup(true);
        ConfigurationStartup.Outcome outcome = start(setup);
        assertEquals(1, setup.initials.size());
        assertEquals("test-chat", setup.initials.get(0).chatModel());
        assertTrue(outcome.isStarted());
        assertEquals(Boolean.TRUE, state().completed());
    }

    /** Regel 6: Datei fehlt → Vorrang vor einem alten setup.completed=true. */
    @Test
    public void missingFileWinsOverAnOldCompletedTrue() {
        state().setCompleted(true);
        ScriptedSetup setup = new ScriptedSetup(false);
        start(setup);
        assertEquals(1, setup.initials.size());
        assertEquals("false", setup.stateWhileRunning.get(0));
        assertEquals(Boolean.FALSE, state().completed());
    }

    /** Ohne Oberfläche bleibt der Erststart unverändert: Vorlage, Meldung, kein Assistent (smokeStartFatJar). */
    @Test
    public void headlessStartIsUnchanged() throws Exception {
        ScriptedSetup setup = new ScriptedSetup(true);
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(new ConfigurationFile(configPath()), null,
                ConfigurationCheck.none(), state(), setup);
        assertFalse(outcome.isStarted());
        assertTrue(outcome.message(), outcome.message().contains("Vorlage wurde angelegt"));
        assertTrue(setup.initials.isEmpty());
        assertTrue(new String(Files.readAllBytes(configPath()), StandardCharsets.UTF_8).length() > 0);
    }
}
