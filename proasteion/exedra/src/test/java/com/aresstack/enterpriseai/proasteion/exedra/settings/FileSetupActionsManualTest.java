package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.security.AskedSecretProvider;
import com.aresstack.enterpriseai.proasteion.application.security.DpapiSecretStore;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.application.security.ManualCredentials;
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
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Abschlussprüfung und „API-Key speichern“ mit Zugang „Manuell“ (#134): ohne KeePass prüft der Abschluss den
 * gewählten Weg echt, KeePass heißt „nicht benötigt“; die Quelle speichert die manuelle Referenz und nur den
 * Benutzernamen; kein Secret landet in der Konfiguration.
 */
public class FileSetupActionsManualTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = Runnable::run;
    private static final String API_KEY = "manual-api-key-value";
    private static final String SERVICE_URL = "https://ai.example.invalid/v1";
    private static final String CONFLUENCE_URL = "https://wiki.example.invalid/confluence";
    private static final String TOKEN = "manual-confluence-token";
    private static final String CONFLUENCE_REF = "ask:Confluence (wiki.example.invalid)";

    private final ManualCredentials manual = new ManualCredentials();
    private final InMemoryVault vault = new InMemoryVault()
            .put("Enterprise AI API", "", "keepass-api-key", SERVICE_URL);
    private final List<String> confluenceCalls = new ArrayList<String>();
    private final List<String> titleReads = new ArrayList<String>();

    private final AskedSecretProvider.CredentialPrompt refusing = (ref, label) -> {
        throw new AskedSecretProvider.Unavailable("kein Dialog im Test");
    };

    /** Wie die Anwendung: ask: über die Sitzung, alles andere über KeePass (aus: nicht erreichbar). */
    private final SecretChecker secrets = new SecretChecker() {
        @Override
        public SecretCheckResult check(KeePassConfig keePass, SecretRef ref) {
            KeePassAccess.Check result = vault(keePass).check(ref);
            return result.ok() ? SecretCheckResult.ok(result.message()) : SecretCheckResult.failed(result.message());
        }

        @Override
        public KeePassAccess vault(KeePassConfig keePass) {
            AskedSecretProvider asked = new AskedSecretProvider(keePass.enabled() ? vault
                    : new com.aresstack.enterpriseai.proasteion.application.security.UnavailableSecretProvider(
                    "aus"), refusing, manual);
            if (!keePass.enabled()) {
                return KeePassAccess.manualOnly(asked);
            }
            return new KeePassAccess(asked, keePass.address());
        }

        @Override
        public String testConfluence(NetworkConfig network, String url, String user, char[] password,
                                     String certAlias) {
            confluenceCalls.add(url + "|" + user + "|" + new String(password) + "|" + certAlias);
            if (!TOKEN.equals(new String(password))) {
                throw new IllegalStateException("HTTP 401");
            }
            return "Verbindung erfolgreich: 2 Spaces sichtbar.";
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

    private Path configPath() {
        return tmp.getRoot().toPath().resolve("enterprise-ai-client.properties");
    }

    private FileSetupActions actions() {
        ConfigurationFile file = new ConfigurationFile(configPath());
        FileSettingsActions settings = new FileSettingsActions(file, secrets, connection, ConfigurationCheck.none(),
                DIRECT, DIRECT);
        return new FileSetupActions(settings, secrets, connection, ConfigurationCheck.none(), DIRECT, DIRECT, null,
                manual);
    }

    private static SettingsForm manualForm(boolean keePass) {
        return SettingsMapper.firstStartDefaults().toBuilder()
                .keePassEnabled(keePass).keePassHost("127.0.0.1").keePassPort("12546")
                .keePassClientDisplayName("Enterprise AI Client")
                .chatApiKeyRef("ask:KI-Dienst").chatBaseUrl(SERVICE_URL)
                .chatModel("chat-model").embeddingModel("embedding-model").embeddingDimension("768")
                .proxyMode(SettingsForm.PROXY_DISABLED).build();
    }

    private static SetupCheck find(List<SetupCheck> checks, String label) {
        for (SetupCheck check : checks) {
            if (check.label().equals(label)) {
                return check;
            }
        }
        throw new AssertionError("kein Punkt " + label + " in " + checks);
    }

    @Test
    public void fullyManualSetupPassesWithoutKeePass() throws Exception {
        manual.remember("ask:KI-Dienst", "", API_KEY.toCharArray());
        manual.remember(CONFLUENCE_REF, "jdoe", TOKEN.toCharArray());
        List<SetupCheck> checks = actions().verify(manualForm(false),
                ConfluenceSetup.manual(CONFLUENCE_URL, "jdoe", "", true));

        for (SetupCheck check : checks) {
            assertTrue(check.toString(), check.isOk());
        }
        assertEquals(FileSetupActions.KEEPASS_NOT_NEEDED, find(checks, FileSetupActions.CHECK_KEEPASS).detail());
        assertEquals("manuell eingegeben", find(checks, FileSetupActions.CHECK_API_KEY).detail());
        assertEquals("Confluence mit der Sitzungseingabe getestet",
                CONFLUENCE_URL + "|jdoe|" + TOKEN + "|", confluenceCalls.get(0));

        String written = new String(Files.readAllBytes(configPath()), StandardCharsets.UTF_8);
        assertFalse("API-Key in der Konfiguration", written.contains(API_KEY));
        assertFalse("Token in der Konfiguration", written.contains(TOKEN));
        AppConfig loaded = AppConfigLoader.load(configPath());
        assertEquals("ask:KI-Dienst", loaded.chat().apiKeyRef().id());
        SourceDefinition source = loaded.sources().get(0);
        assertEquals(CONFLUENCE_REF, source.settings().get("credentialRef"));
        assertEquals("nur der Benutzername ist vorgemerkt", "jdoe",
                source.settings().get(ManualCredentials.SOURCE_USER_SETTING));
    }

    @Test
    public void manualAccessWithoutSessionInputStaysRed() {
        List<SetupCheck> checks = actions().verify(manualForm(false),
                ConfluenceSetup.manual(CONFLUENCE_URL, "", "", false));
        assertFalse(find(checks, FileSetupActions.CHECK_API_KEY).isOk());
        SetupCheck confluence = find(checks, FileSetupActions.CHECK_CONFLUENCE);
        assertFalse(confluence.isOk());
        assertTrue(confluence.detail(), confluence.detail().contains("nicht eingegeben"));
        assertTrue("ohne Eingabe kein Confluence-Aufruf", confluenceCalls.isEmpty());
    }

    @Test
    public void wrongManualTokenFailsTheConfluencePoint() {
        manual.remember("ask:KI-Dienst", "", API_KEY.toCharArray());
        manual.remember(CONFLUENCE_REF, "jdoe", "wrong".toCharArray());
        List<SetupCheck> checks = actions().verify(manualForm(false),
                ConfluenceSetup.manual(CONFLUENCE_URL, "jdoe", "", true));
        SetupCheck confluence = find(checks, FileSetupActions.CHECK_CONFLUENCE);
        assertFalse(confluence.isOk());
        assertTrue(confluence.detail().contains("401"));
    }

    @Test
    public void manualServiceWithKeePassConfluenceChecksKeePass() {
        vault.put("Confluence", "jdoe", TOKEN, CONFLUENCE_URL);
        manual.remember("ask:KI-Dienst", "", API_KEY.toCharArray());
        List<SetupCheck> checks = actions().verify(manualForm(true),
                new ConfluenceSetup("Confluence", CONFLUENCE_URL, "jdoe", ""));
        SetupCheck keePass = find(checks, FileSetupActions.CHECK_KEEPASS);
        assertTrue(keePass.toString(), keePass.isOk());
        assertTrue("KeePass wirklich geprüft", keePass.detail().contains("Einträge lesbar"));
        assertTrue(find(checks, FileSetupActions.CHECK_CONFLUENCE).isOk());
    }

    @Test
    public void saveApiKeyStoresProtectedAndUncheckedRemovesIt() throws Exception {
        Path folder = tmp.newFolder("manual-secrets").toPath();
        DpapiSecretStore store = DpapiSecretStore.withProtector(folder, new DpapiSecretStore.Protector() {
            @Override
            public String protect(byte[] plain) {
                byte[] scrambled = plain.clone();
                for (int i = 0; i < scrambled.length; i++) {
                    scrambled[i] ^= 0x5a;
                }
                return Base64.getEncoder().encodeToString(scrambled);
            }

            @Override
            public byte[] unprotect(String blob) {
                byte[] plain = Base64.getDecoder().decode(blob);
                for (int i = 0; i < plain.length; i++) {
                    plain[i] ^= 0x5a;
                }
                return plain;
            }
        });
        manual.persistent(store);
        manual.remember("ask:KI-Dienst", "", API_KEY.toCharArray());
        FileSetupActions actions = actions();
        assertTrue(actions.canStoreManualKey());

        AtomicReference<String> error = new AtomicReference<String>("unset");
        actions.storeManualKey("ask:KI-Dienst", true, error::set);
        assertNull(error.get());
        assertTrue(actions.manualKeyStored("ask:KI-Dienst"));
        for (Path file : Files.newDirectoryStream(folder)) {
            String content = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII);
            assertFalse("kein Klartext in der Datei", content.contains(API_KEY));
            assertFalse("der Name verrät nichts", file.getFileName().toString().contains("KI"));
        }

        // nach einem Neustart: leere Sitzung, der normale Anwendungsweg liest den gespeicherten Key
        manual.forget("ask:KI-Dienst");
        AskedSecretProvider app = new AskedSecretProvider(vault, refusing, manual);
        char[] resolved = app.resolve(SecretRef.of("ask:KI-Dienst")).copySecret();
        assertEquals(API_KEY, new String(resolved));

        actions.storeManualKey("ask:KI-Dienst", false, error::set);
        assertNull(error.get());
        assertFalse(actions.manualKeyStored("ask:KI-Dienst"));
        assertFalse(Files.newDirectoryStream(folder).iterator().hasNext());
    }

    @Test
    public void withoutProtectedStoreNothingIsWritten() {
        manual.persistent(DpapiSecretStore.forDirectory(tmp.getRoot().toPath().resolve("never")));
        manual.remember("ask:KI-Dienst", "", API_KEY.toCharArray());
        FileSetupActions actions = actions();
        if (actions.canStoreManualKey()) {
            return; // nur unter Windows verfügbar; dort prüft der Test oben den Weg
        }
        AtomicReference<String> error = new AtomicReference<String>();
        actions.storeManualKey("ask:KI-Dienst", true, error::set);
        assertTrue(error.get(), error.get() != null && error.get().contains("Windows"));
        assertFalse(Files.exists(tmp.getRoot().toPath().resolve("never")));
    }

    @Test
    public void suggestedUserPrefillsTheDialogPerSourceButNeverTheSecret() throws Exception {
        final List<String> asked = new ArrayList<String>();
        AskedSecretProvider.CredentialPrompt prompt = new AskedSecretProvider.CredentialPrompt() {
            @Override
            public AskedSecretProvider.Answer ask(SecretRef ref, String label) {
                return ask(ref, label, "");
            }

            @Override
            public AskedSecretProvider.Answer ask(SecretRef ref, String label, String suggestedUser) {
                asked.add(label + "=" + suggestedUser);
                return new AskedSecretProvider.Answer(suggestedUser, "typed".toCharArray());
            }
        };
        manual.suggestUser("ask:Confluence (a.example)", "alice");
        manual.suggestUser("ask:Confluence (b.example)", "bob");
        AskedSecretProvider app = new AskedSecretProvider(vault, prompt, manual);
        assertEquals("alice", app.resolve(SecretRef.of("ask:Confluence (a.example)")).principal());
        assertEquals("bob", app.resolve(SecretRef.of("ask:Confluence (b.example)")).principal());
        app.resolve(SecretRef.of("ask:Confluence (a.example)"));
        assertEquals("einmal je Quelle gefragt, mit ihrem Namen",
                java.util.Arrays.asList("Confluence (a.example)=alice", "Confluence (b.example)=bob"), asked);
    }
}
