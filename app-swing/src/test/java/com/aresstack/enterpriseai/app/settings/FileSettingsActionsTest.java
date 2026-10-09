package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.app.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Prüfen und Speichern gehen durch den echten Loader; die KeePass-Probe läuft auf dem Arbeits-Executor. */
public class FileSettingsActionsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private ConfigurationFile fileWith(String text) throws Exception {
        Path path = tmp.getRoot().toPath().resolve("app.properties");
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return new ConfigurationFile(path);
    }

    private static String validText() {
        return "# Mein Kommentar bleibt\n"
                + "chat.baseUrl=http://127.0.0.1:9/v1\n"
                + "chat.model=test-chat\n"
                + "chat.apiKeyRef=keepass:Enterprise AI API\n"
                + "embedding.model=test-embedding\n"
                + "embedding.dimension=8\n"
                + "retrieval.maxResults=5\n"
                + "security.keepass.enabled=false\n"
                + "network.proxy.mode=NONE\n";
    }

    @Test
    public void validFormHasNoProblems() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read());
        assertTrue(actions.validate(form).isEmpty());
    }

    @Test
    public void checkProblemsBlockSavingLikeLoaderProblems() throws Exception {
        ConfigurationCheck caFileMustExist = new ConfigurationCheck() {
            @Override
            public void verify(AppConfig config) {
                if (config.network().caCertificatesFile() != null) {
                    throw new com.aresstack.enterpriseai.app.config.AppConfigException(
                            "network.tls.caCertificatesFile: Datei enthält kein Zertifikat");
                }
            }
        };
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, caFileMustExist,
                DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read()).toBuilder()
                .caCertificatesFile("C:/ca.pem").build();
        List<String> problems = actions.validate(form);
        assertEquals(1, problems.size());
        assertEquals("CA-Datei (network.tls.caCertificatesFile): Datei enthält kein Zertifikat", problems.get(0));
        try {
            actions.save(form);
            fail("mit Problemen darf nicht gespeichert werden");
        } catch (IllegalArgumentException expected) {
            assertFalse(actions.file().read().containsKey("network.tls.caCertificatesFile"));
        }
        assertTrue(actions.validate(form.toBuilder().caCertificatesFile("").build()).isEmpty());
    }

    @Test
    public void problemsCarryFieldLabels() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read()).toBuilder()
                .chatBaseUrl("").embeddingDimension("viele").build();
        List<String> problems = actions.validate(form);
        assertTrue(problems.toString(), problems.size() >= 2);
        assertTrue(problems.get(0), problems.get(0).startsWith("Basis-URL des KI-Dienstes (chat.baseUrl): "));
        assertTrue(problems.get(1), problems.get(1).startsWith("Embedding-Dimension (embedding.dimension): "));
        for (String problem : problems) {
            assertTrue(problem, problem.contains(" (") && problem.contains("): "));
        }
    }

    @Test
    public void saveWritesOnlyManagedKeysAndKeepsTheRest() throws Exception {
        ConfigurationFile file = fileWith(validText());
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(file.read()).toBuilder()
                .chatModel("neues-modell").windowTitle("Mein Client").build();
        actions.save(form);
        String text = new String(Files.readAllBytes(file.path()), StandardCharsets.UTF_8);
        assertTrue(text, text.startsWith("# Mein Kommentar bleibt\n"));
        assertTrue(text, text.contains("\nchat.model=neues-modell\n"));
        assertTrue(text, text.contains("retrieval.maxResults=5"));
        AppConfig loaded = AppConfigLoader.load(file.path());
        assertEquals("neues-modell", loaded.chat().model());
        assertEquals("Mein Client", loaded.windowTitle());
    }

    @Test
    public void clearingAFieldCommentsItsLineOutInsteadOfWritingAnEmptyValue() throws Exception {
        ConfigurationFile file = fileWith(validText() + "chat.systemPrompt=Antworte kurz.\n"
                + "sources=wiki\nsource.wiki.type=mediawiki\nsource.wiki.apiUrl=http://127.0.0.1:9/w/api.php\n"
                + "source.wiki.startPoints=Hauptseite\nsource.wiki.linkNamespaces=0\n");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(file.read()).toBuilder()
                .chatSystemPrompt("").sources(java.util.Collections.<com.aresstack.enterpriseai.app.ui.settings.SourceForm>emptyList())
                .build();
        actions.save(form);
        String text = new String(Files.readAllBytes(file.path()), StandardCharsets.UTF_8);
        assertTrue(text, text.contains("\n#chat.systemPrompt=Antworte kurz.\n"));
        assertFalse(text, text.contains("\nchat.systemPrompt="));
        assertTrue(text, text.contains("\n#sources=wiki\n"));
        assertTrue(text, text.contains("\n#source.wiki.linkNamespaces=0\n"));
        assertFalse(text, text.contains("\nsources="));
        AppConfig loaded = AppConfigLoader.load(file.path());
        assertTrue(loaded.sources().isEmpty());
    }

    @Test
    public void missingFileIsValidatedAndSavedAgainstTheTemplate() throws Exception {
        ConfigurationFile file = new ConfigurationFile(tmp.getRoot().toPath().resolve("neu").resolve("x"));
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.firstStartDefaults().toBuilder()
                .chatBaseUrl("http://127.0.0.1:9/v1").chatModel("test-chat")
                .embeddingModel("test-embedding").embeddingDimension("8")
                .keePassEnabled(false).proxyMode(SettingsForm.PROXY_NONE).build();
        assertTrue(actions.validate(form).toString(), actions.validate(form).isEmpty());
        assertFalse(file.exists());
        actions.save(form);
        assertTrue("kurzer Dateiname ist erlaubt", file.exists());
        AppConfig loaded = AppConfigLoader.load(file.path());
        assertEquals("test-chat", loaded.chat().model());
        assertTrue("Beispielquellen der Vorlage sind nicht aktiv", loaded.sources().isEmpty());
        for (String warning : loaded.warnings()) {
            assertFalse("keine verwaisten Quell-Schlüssel aus der Vorlage: " + warning, warning.contains("source."));
        }
    }

    @Test
    public void saveRefusesAFormWithProblems() throws Exception {
        ConfigurationFile file = fileWith(validText());
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        SettingsForm broken = SettingsMapper.fromProperties(file.read()).toBuilder().chatModel("").build();
        try {
            actions.save(broken);
            fail("Entwurf mit Problemen darf nicht gespeichert werden");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("chat.model"));
        }
        assertEquals(validText(), new String(Files.readAllBytes(file.path()), StandardCharsets.UTF_8));
    }

    @Test
    public void secretCheckWithoutCheckerOrRefFailsImmediately() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read());
        final List<SecretCheckResult> results = new ArrayList<SecretCheckResult>();
        Consumer<SecretCheckResult> collect = new Consumer<SecretCheckResult>() {
            @Override
            public void accept(SecretCheckResult result) {
                results.add(result);
            }
        };
        actions.checkSecret(form, "  ", collect);
        actions.checkSecret(form, "Enterprise AI API", collect);
        assertEquals(2, results.size());
        assertFalse(results.get(0).isSuccess());
        assertFalse(results.get(1).isSuccess());
        assertTrue(results.get(1).message(), results.get(1).message().contains("nicht verfügbar"));
    }

    @Test
    public void secretCheckUsesTheDraftsKeePassSettingsAndBothExecutors() throws Exception {
        final List<String> trace = new ArrayList<String>();
        Executor worker = new Executor() {
            @Override
            public void execute(Runnable command) {
                trace.add("worker");
                command.run();
            }
        };
        Executor ui = new Executor() {
            @Override
            public void execute(Runnable command) {
                trace.add("ui");
                command.run();
            }
        };
        final List<KeePassConfig> seen = new ArrayList<KeePassConfig>();
        SecretChecker checker = new SecretChecker() {
            @Override
            public SecretCheckResult check(KeePassConfig keePass, SecretRef ref) {
                seen.add(keePass);
                return SecretCheckResult.ok("gefunden: " + ref.id());
            }
        };
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), checker, worker, ui);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read()).toBuilder()
                .keePassEnabled(true).keePassPort("12999").build();
        final List<SecretCheckResult> results = new ArrayList<SecretCheckResult>();
        actions.checkSecret(form, "keepass:Enterprise AI API", new Consumer<SecretCheckResult>() {
            @Override
            public void accept(SecretCheckResult result) {
                results.add(result);
            }
        });
        assertEquals("[worker, ui]", trace.toString());
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).enabled());
        assertEquals(12999, seen.get(0).rpc().port());
        assertTrue(results.get(0).isSuccess());
        assertEquals("gefunden: keepass:Enterprise AI API", results.get(0).message());
    }

    private static final class RecordingListener implements ConnectionCheckListener {
        final List<ConnectionCheckStep> steps = new ArrayList<ConnectionCheckStep>();
        Boolean finished;

        @Override
        public void onStep(ConnectionCheckStep step) {
            steps.add(step);
        }

        @Override
        public void onFinished(boolean success) {
            assertTrue("onFinished nur einmal", finished == null);
            finished = success;
        }
    }

    private static final class RecordingChecker implements ConnectionChecker {
        final List<AppConfig> configs = new ArrayList<AppConfig>();
        boolean success = true;

        @Override
        public boolean check(AppConfig config, Consumer<ConnectionCheckStep> onStep) {
            configs.add(config);
            onStep.accept(ConnectionCheckStep.ok("Proxy-Route", "direkt (NONE)"));
            onStep.accept(ConnectionCheckStep.ok("GET /models", "HTTP 200"));
            return success;
        }
    }

    private static final class CountingExecutor implements Executor {
        int executions;

        @Override
        public void execute(Runnable command) {
            executions++;
            command.run();
        }
    }

    @Test
    public void connectionCheckWithoutCheckerFailsImmediately() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, DIRECT, DIRECT);
        RecordingListener listener = new RecordingListener();
        actions.checkConnection(SettingsMapper.fromProperties(actions.file().read()), listener);
        assertEquals(1, listener.steps.size());
        assertEquals(FileSettingsActions.STEP_TEST, listener.steps.get(0).title());
        assertTrue(listener.steps.get(0).isFailure());
        assertEquals(Boolean.FALSE, listener.finished);
    }

    @Test
    public void connectionCheckRejectsAnInvalidDraftBeforeRunningTheChecker() throws Exception {
        RecordingChecker checker = new RecordingChecker();
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, checker,
                ConfigurationCheck.none(), DIRECT, DIRECT);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read()).toBuilder()
                .chatBaseUrl("keine url").build();
        RecordingListener listener = new RecordingListener();
        actions.checkConnection(form, listener);
        assertEquals(1, listener.steps.size());
        assertEquals(FileSettingsActions.STEP_CONFIGURATION, listener.steps.get(0).title());
        assertTrue(listener.steps.get(0).detail(), listener.steps.get(0).detail().contains("chat.baseUrl"));
        assertEquals(Boolean.FALSE, listener.finished);
        assertTrue(checker.configs.isEmpty());
    }

    @Test
    public void connectionCheckRunsTheCheckerWithTheDraftOnTheWorkerAndDeliversOnTheUiExecutor() throws Exception {
        RecordingChecker checker = new RecordingChecker();
        CountingExecutor worker = new CountingExecutor();
        CountingExecutor ui = new CountingExecutor();
        FileSettingsActions actions = new FileSettingsActions(fileWith(validText()), null, checker,
                ConfigurationCheck.none(), worker, ui);
        SettingsForm form = SettingsMapper.fromProperties(actions.file().read()).toBuilder()
                .chatBaseUrl("http://127.0.0.1:9/v2").build();
        RecordingListener listener = new RecordingListener();
        actions.checkConnection(form, listener);

        assertEquals(1, checker.configs.size());
        assertEquals("http://127.0.0.1:9/v2", checker.configs.get(0).chat().baseUrl().toString());
        assertEquals(2, listener.steps.size());
        assertEquals(Boolean.TRUE, listener.finished);
        assertEquals(1, worker.executions);
        assertEquals(3, ui.executions);

        checker.success = false;
        listener = new RecordingListener();
        actions.checkConnection(form, listener);
        assertEquals(Boolean.FALSE, listener.finished);
    }
}
