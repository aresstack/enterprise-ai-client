package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;
import com.aresstack.enterpriseai.proasteion.application.composition.ApplicationPorts;
import com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppPaths;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.application.settings.LocalVoiceInstaller;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.assembly.ShellAssembly;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupWizardDriver;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupWizardRun;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Echter Erststart mit leerem Anwendungsverzeichnis (keine Konfiguration, kein {@code setup.completed}): der
 * Startablauf ({@link ConfigurationStartup}) öffnet den echten Einrichtungsassistenten (derselbe Lauf, den das
 * Erststart-Fenster zeigt), der wird über seine Knöpfe bedient, die Abschlussprüfung schreibt über die echten
 * Datei-Aktionen, der optionale Schritt installiert ein lokales STT-Modell, und danach startet die Anwendung mit
 * der geschriebenen Konfiguration ohne Neustart. KeePass, KI-Dienst, Confluence und Hub sind Ersatz.
 */
public class SetupFirstStartEndToEndTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = Runnable::run;
    private static final String API_KEY = "test-api-key-value";
    private static final String CONFLUENCE_PASSWORD = "confluence-password";
    private static final String STT_SUGGESTION = "gemma-4-e2b";
    private static final String STT_FOLDER = "onnx-community--gemma-4-E2B-it-ONNX--audio_q4";

    private final InMemoryVault vault = new InMemoryVault()
            .put("Enterprise AI API", "", API_KEY, "https://ai.example.invalid/v1")
            .put("Confluence", "jdoe", CONFLUENCE_PASSWORD, "https://wiki.example.invalid/confluence");
    private String previousConfig;

    private final SecretChecker secrets = new SecretChecker() {
        @Override
        public SecretCheckResult check(KeePassConfig keePass, SecretRef ref) {
            KeePassAccess.Check result = vault(keePass).check(ref);
            return result.ok() ? SecretCheckResult.ok(result.message()) : SecretCheckResult.failed(result.message());
        }

        @Override
        public String locationOf(KeePassConfig keePass, SecretRef ref) throws Exception {
            return vault(keePass).locationOf(ref);
        }

        @Override
        public KeePassAccess vault(KeePassConfig keePass) {
            return new KeePassAccess(vault, keePass.address());
        }

        @Override
        public String testConfluence(NetworkConfig network, String url, String user, char[] password,
                                     String certAlias) {
            if (!CONFLUENCE_PASSWORD.equals(new String(password))) {
                throw new IllegalStateException("HTTP 401");
            }
            return "Verbindung erfolgreich: 3 Spaces sichtbar.";
        }
    };

    private final ConnectionChecker connection = new ConnectionChecker() {
        @Override
        public boolean check(AppConfig config, Consumer<ConnectionCheckStep> onStep) {
            onStep.accept(ConnectionCheckStep.ok("GET /models", "HTTP 200"));
            return true;
        }

        @Override
        public int embeddingDimension(AppConfig config) {
            return 768;
        }
    };

    /** Der Hub-Ersatz: ein STT-Vorschlag, nach dem Installieren ein Ordner wie bei der echten Hub-Installation. */
    private final List<String> installed = new ArrayList<String>();
    private final LocalVoiceInstaller voices = new LocalVoiceInstaller() {
        @Override
        public List<LocalVoiceOffer> offers(Path modelRoot) {
            List<LocalVoiceOffer> offers = new ArrayList<LocalVoiceOffer>();
            if (installed.isEmpty()) {
                offers.add(new LocalVoiceOffer(STT_SUGGESTION, "Gemma 4 E2B", "", false, ModelCategory.STT));
            } else {
                offers.add(new LocalVoiceOffer(STT_FOLDER, "Gemma 4 E2B", "", true, ModelCategory.STT));
            }
            return offers;
        }

        @Override
        public List<LocalVoiceOffer> search(NetworkConfig network, KeePassConfig keePass, String query,
                                            ModelCategory category, Path modelRoot) {
            return Collections.emptyList();
        }

        @Override
        public void install(NetworkConfig network, KeePassConfig keePass, String voiceId, Path modelRoot,
                            LocalVoiceInstallListener listener, InstallCancellation cancellation) {
            listener.progress("audio_encoder_q4.onnx", 1, 1, 1024, 1024);
            installed.add(voiceId);
        }

        @Override
        public void update(NetworkConfig network, KeePassConfig keePass, String voiceId, Path modelRoot,
                           LocalVoiceInstallListener listener, InstallCancellation cancellation) {
        }

        @Override
        public void remove(String voiceId, Path modelRoot) {
        }
    };

    @Before
    public void isolateConfigDirectory() {
        previousConfig = System.getProperty(AppPaths.CONFIG_PROPERTY);
        System.setProperty(AppPaths.CONFIG_PROPERTY, configPath().toString());
    }

    @After
    public void restoreConfigDirectory() {
        if (previousConfig == null) {
            System.clearProperty(AppPaths.CONFIG_PROPERTY);
        } else {
            System.setProperty(AppPaths.CONFIG_PROPERTY, previousConfig);
        }
    }

    private Path configPath() {
        return tmp.getRoot().toPath().resolve("enterprise-ai-client.properties");
    }

    @Test
    public void firstStartRunsTheRealWizardWritesTheConfigurationAndStartsTheApplication() throws Exception {
        final ConfigurationFile file = new ConfigurationFile(configPath());
        final FileSettingsActions settings = new FileSettingsActions(file, secrets, connection,
                ConfigurationCheck.none(), null, voices, DIRECT, DIRECT);
        final FileSetupActions setup = new FileSetupActions(settings, secrets, connection, ConfigurationCheck.none(),
                DIRECT, DIRECT, null);
        ApplicationStateService state = new ApplicationStateService(tmp.getRoot().toPath()
                .resolve("application-state.json"));
        final ConfigurationStartup.SetupState setupState = ConfigurationStartup.setupState(state);
        assertFalse("leeres Anwendungsverzeichnis", Files.exists(configPath()));
        assertEquals(null, setupState.completed());

        final List<Boolean> stateDuringWizard = new ArrayList<Boolean>();
        final AtomicReference<SetupWizardRun> shown = new AtomicReference<SetupWizardRun>();
        ConfigurationStartup.SetupUi wizardUi = initial -> {
            stateDuringWizard.add(setupState.completed());
            try {
                SwingUtilities.invokeAndWait(() -> {
                    SetupWizardRun run = new SetupWizardRun(initial, settings, setup, ComicPalette.defaultPalette(),
                            completed -> {
                            });
                    shown.set(run);
                    SetupWizardDriver.throughBasicSetup(run.wizard());
                    assertTrue("Abschlussprüfung hat geschrieben", Files.exists(configPath()));
                    SetupWizardDriver.install(run.wizard());
                    assertEquals("Gemma 4 E2B (installiert)",
                            SetupWizardDriver.speechModel(run.wizard()).getSelectedItem());
                    SetupWizardDriver.finish(run.wizard());
                });
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return shown.get().isCompleted();
        };
        ConfigurationStartup.SettingsUi noDialog = (initial, problems, firstStart) -> {
            throw new AssertionError("ohne Fehler kein Einstellungen-Dialog: " + problems);
        };

        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file, noDialog, ConfigurationCheck.none(),
                setupState, wizardUi);

        assertNotNull("Assistent erschien", shown.get());
        assertEquals("während der Einrichtung setup.completed=false", Collections.singletonList(Boolean.FALSE),
                stateDuringWizard);
        assertTrue(outcome.message(), outcome.isStarted());
        assertEquals(Boolean.TRUE, setupState.completed());
        assertEquals(Collections.singletonList(STT_SUGGESTION), installed);
        AppConfig config = outcome.config();
        assertEquals("local:" + STT_FOLDER, config.models().selections().get(ModelCategory.STT).key());

        String written = new String(Files.readAllBytes(configPath()), StandardCharsets.UTF_8);
        assertTrue(written, written.contains("chat.model=chat-model"));
        assertTrue(written, written.contains("model.stt=local:" + STT_FOLDER));
        assertFalse("API-Key in der Konfiguration", written.contains(API_KEY));
        assertFalse("Confluence-Passwort in der Konfiguration", written.contains(CONFLUENCE_PASSWORD));

        // Hauptanwendung mit genau dieser Konfiguration, ohne Neustart; wie ApplicationStart: die Basis-URL steht nur
        // im KeePass-Eintrag (der Assistent schreibt sie nicht in die Datei)
        AppConfig started = AdapterAssembly.withVaultLocations(config, file.read(), vault);
        assertNotNull("Chat-Konfiguration mit der Basis-URL aus KeePass", started.chat());
        assertEquals("https://ai.example.invalid/v1", started.chat().baseUrl().toString());
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        final CompositionRoot root = CompositionRoot.compose(started, ApplicationPorts.builder()
                        .chat(new FakeChatCompletionPort(0L)).embeddings(embeddings, embeddings.modelIdentity())
                        .index(new InMemoryKnowledgeIndex())
                        .sources(new KnowledgeSourceCatalog(Collections.<KnowledgeSourceRegistration>emptyList()))
                        .secrets(new UnavailableSecretProvider("Test ohne KeePass")).build(),
                SwingUtilities::invokeLater, System::currentTimeMillis, ZoneId.of("Europe/Berlin"));
        try {
            final AtomicReference<ShellAssembly.ShellView> view = new AtomicReference<ShellAssembly.ShellView>();
            SwingUtilities.invokeAndWait(() -> view.set(ShellAssembly.createShell(root,
                    ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired())));
            assertNotNull(view.get().workspace());
        } finally {
            root.shutdown().run();
        }
    }

    @Test
    public void cancelledFirstStartLeavesSetupIncompleteAndDoesNotStart() throws Exception {
        final ConfigurationFile file = new ConfigurationFile(configPath());
        final FileSettingsActions settings = new FileSettingsActions(file, secrets, connection,
                ConfigurationCheck.none(), null, voices, DIRECT, DIRECT);
        final FileSetupActions setup = new FileSetupActions(settings, secrets, connection, ConfigurationCheck.none(),
                DIRECT, DIRECT, null);
        final ConfigurationStartup.SetupState setupState = ConfigurationStartup.setupState(
                new ApplicationStateService(tmp.getRoot().toPath().resolve("application-state.json")));
        ConfigurationStartup.SetupUi wizardUi = initial -> {
            final AtomicReference<SetupWizardRun> shown = new AtomicReference<SetupWizardRun>();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    SetupWizardRun run = new SetupWizardRun(initial, settings, setup, ComicPalette.defaultPalette(),
                            completed -> {
                            });
                    shown.set(run);
                    run.overlay().closeButton().doClick(); // das Comic-✕
                });
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            return shown.get().isCompleted();
        };
        ConfigurationStartup.Outcome outcome = ConfigurationStartup.obtain(file,
                (initial, problems, firstStart) -> null, ConfigurationCheck.none(), setupState, wizardUi);
        assertFalse(outcome.isStarted());
        assertTrue(outcome.isCancelled());
        assertEquals(Boolean.FALSE, setupState.completed());
    }
}
