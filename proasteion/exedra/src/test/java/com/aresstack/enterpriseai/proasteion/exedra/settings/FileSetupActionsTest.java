package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationCheck;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.ConfluenceSetup;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupCheck;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Die Abschlussprüfung des Einrichtungsassistenten (neun Punkte) und der Embedding-Test über der echten
 * Konfigurationsdatei, mit KeePass im Speicher, einem Verbindungstest-Ersatz und einem Confluence-Ersatz.
 */
public class FileSetupActionsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = Runnable::run;
    private static final String API_KEY = "test-api-key-value";
    private static final String SERVICE_URL = "https://ai.example.invalid/v1";
    private static final String CONFLUENCE_URL = "https://wiki.example.invalid/confluence";

    private final InMemoryVault vault = new InMemoryVault()
            .put("Enterprise AI API", "", API_KEY, SERVICE_URL)
            .put("Confluence", "jdoe", "confluence-password", CONFLUENCE_URL);
    private final List<String> confluenceCalls = new ArrayList<String>();
    private final List<AppConfig> checkedConfigs = new ArrayList<AppConfig>();

    /** KeePass-Probe über {@link KeePassAccess} mit dem Speicher-Tresor; Confluence antwortet mit drei Spaces. */
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
            confluenceCalls.add(url + "|" + user + "|" + new String(password) + "|" + certAlias);
            if (!"confluence-password".equals(new String(password))) {
                throw new IllegalStateException("HTTP 401");
            }
            return "Verbindung erfolgreich: 3 Spaces sichtbar.";
        }
    };

    private final ConnectionChecker connection = new ConnectionChecker() {
        @Override
        public boolean check(AppConfig config, Consumer<ConnectionCheckStep> onStep) {
            checkedConfigs.add(config);
            onStep.accept(ConnectionCheckStep.ok("GET /models", "HTTP 200"));
            return true;
        }

        @Override
        public int embeddingDimension(AppConfig config) {
            checkedConfigs.add(config);
            return 768;
        }
    };

    private Path configPath() {
        return tmp.getRoot().toPath().resolve("enterprise-ai-client.properties");
    }

    private FileSetupActions actions() {
        ConfigurationFile file = new ConfigurationFile(configPath());
        FileSettingsActions settings = new FileSettingsActions(file, secrets, connection, ConfigurationCheck.none(),
                DIRECT, DIRECT);
        return new FileSetupActions(settings, secrets, connection, ConfigurationCheck.none(), DIRECT, DIRECT, null);
    }

    /** Wie der Assistent ihn füllt: KeePass-Vorgaben, Eintrag des KI-Dienstes, Modelle, keine Basis-URL. */
    private static SettingsForm wizardForm() {
        return SettingsMapper.firstStartDefaults().toBuilder()
                .keePassEnabled(true).keePassHost("127.0.0.1").keePassPort("12546")
                .keePassClientDisplayName("Enterprise AI Client")
                .keePassPairingKeyStore(SettingsForm.PAIRING_KEY_STORE_FILE)
                .chatModel("chat-model").embeddingModel("embedding-model").embeddingDimension("768")
                .proxyMode(SettingsForm.PROXY_DISABLED).build();
    }

    private static List<String> labels(List<SetupCheck> checks) {
        List<String> labels = new ArrayList<String>();
        for (SetupCheck check : checks) {
            labels.add(check.label());
        }
        return labels;
    }

    @Test
    public void finalCheckWritesConfigurationAndSourceAndPassesAllNinePoints() throws Exception {
        List<SetupCheck> checks = actions().verify(wizardForm(),
                new ConfluenceSetup("Confluence", CONFLUENCE_URL, "jdoe", ""));

        assertEquals(Arrays.asList(FileSetupActions.CHECK_KEEPASS, FileSetupActions.CHECK_SERVICE,
                FileSetupActions.CHECK_API_KEY, FileSetupActions.CHECK_CHAT_MODEL,
                FileSetupActions.CHECK_EMBEDDING_MODEL, FileSetupActions.CHECK_DIMENSION,
                FileSetupActions.CHECK_CONFLUENCE, FileSetupActions.CHECK_SOURCE, FileSetupActions.CHECK_LOADS),
                labels(checks));
        for (SetupCheck check : checks) {
            assertTrue(check.toString(), check.isOk());
        }
        String written = new String(Files.readAllBytes(configPath()), StandardCharsets.UTF_8);
        assertFalse("API-Key in der Konfiguration", written.contains(API_KEY));
        assertFalse("Confluence-Passwort in der Konfiguration", written.contains("confluence-password"));
        assertFalse("Basis-URL bleibt leer, sie kommt aus KeePass", written.contains(SERVICE_URL));
        assertTrue(written.contains("chat.model=chat-model"));
        assertTrue(written.contains("embedding.dimension=768"));
        AppConfig loaded = AppConfigLoader.load(configPath());
        assertEquals(1, loaded.sources().size());
        assertEquals("confluence", loaded.sources().get(0).typeId());
        assertTrue(loaded.sources().get(0).enabled());
        assertEquals("Confluence", loaded.sources().get(0).settings().get("credentialRef"));
        assertEquals("false", loaded.sources().get(0).settings().get("clientCertificate.use"));
        // Der Verbindungstest bekam die Basis-URL aus dem KeePass-Eintrag (VaultLocations).
        assertEquals(SERVICE_URL, checkedConfigs.get(0).chat().baseUrl().toString());
        assertEquals(Arrays.asList(CONFLUENCE_URL + "|jdoe|confluence-password|"), confluenceCalls);
    }

    @Test
    public void certificateAliasIsStoredOnTheSource() throws Exception {
        actions().verify(wizardForm(), new ConfluenceSetup("Confluence", CONFLUENCE_URL, "jdoe", "my-cert"));
        AppConfig loaded = AppConfigLoader.load(configPath());
        assertEquals("my-cert", loaded.sources().get(0).settings().get("clientCertificate.alias"));
        assertFalse("false".equals(loaded.sources().get(0).settings().get("clientCertificate.use")));
    }

    @Test
    public void missingApiKeyAndBrokenConfluenceFailTheirPoints() {
        vault.put("Enterprise AI API", "", "", SERVICE_URL).put("Confluence", "jdoe", "wrong", CONFLUENCE_URL);
        List<SetupCheck> checks = actions().verify(wizardForm(),
                new ConfluenceSetup("Confluence", CONFLUENCE_URL, "jdoe", ""));
        assertFalse(checks.get(2).toString(), checks.get(2).isOk());
        assertFalse(checks.get(6).toString(), checks.get(6).isOk());
        assertTrue(checks.get(6).detail().contains("401"));
    }

    @Test
    public void incompleteModelsFailWithoutWritingAnything() {
        SettingsForm withoutModels = wizardForm().toBuilder().chatModel("").embeddingModel("").embeddingDimension("")
                .build();
        List<SetupCheck> checks = actions().verify(withoutModels,
                new ConfluenceSetup("Confluence", CONFLUENCE_URL, "jdoe", ""));
        assertFalse(checks.get(3).isOk());
        assertFalse(checks.get(4).isOk());
        assertFalse(checks.get(5).isOk());
        assertFalse(checks.get(8).isOk());
        assertFalse("ohne gültigen Entwurf keine Datei", Files.exists(configPath()));
        // Klartext statt Properties-Schlüssel (der Assistent zeigt keine Schlüssel)
        String detail = checks.get(8).detail();
        assertTrue(detail, detail.contains("Chat-Modell"));
        for (SetupCheck check : checks) {
            assertFalse(check.toString(), check.detail().matches("(?s).*\\b(chat|embedding)\\.[a-zA-Z]+.*"));
        }
    }

    @Test
    public void embeddingDimensionComesFromTheEmbeddingTest() {
        final AtomicReference<Integer> measured = new AtomicReference<Integer>();
        final AtomicReference<String> error = new AtomicReference<String>();
        actions().embeddingDimension(wizardForm().toBuilder().chatModel("").embeddingDimension("").build(),
                (dimension, failure) -> {
                    measured.set(dimension);
                    error.set(failure);
                });
        assertNull(error.get());
        assertEquals(Integer.valueOf(768), measured.get());
        assertEquals("embedding-model", checkedConfigs.get(0).embedding().model());
        assertEquals(SERVICE_URL, checkedConfigs.get(0).embedding().baseUrl().toString());
    }
}
