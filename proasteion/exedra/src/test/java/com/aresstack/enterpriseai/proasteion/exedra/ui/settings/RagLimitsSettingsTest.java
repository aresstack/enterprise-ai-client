package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.exedra.settings.FileSettingsActions;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Reiter „Index“ → „Wissenssuche im Chat“ und Reiter „Tools“ → „Max. Werkzeugrunden“: die Werte kommen aus der Datei
 * ins Feld, landen beim Speichern als {@code context.maxSources}, {@code retrieval.maxResults},
 * {@code context.maxTokens} und {@code chat.tools.maxRounds} in der Datei und bleiben getrennt vom Recherche-Budget
 * des Agenten.
 */
public class RagLimitsSettingsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = Runnable::run;

    private final ComicPalette palette = ComicPalette.defaultPalette();

    private ConfigurationFile file(String extra) throws Exception {
        Path path = tmp.getRoot().toPath().resolve("app.properties");
        String text = "chat.baseUrl=http://127.0.0.1:9/v1\n"
                + "chat.model=test-chat\n"
                + "chat.apiKeyRef=keepass:Enterprise AI API\n"
                + "embedding.model=test-embedding\n"
                + "embedding.dimension=8\n"
                + "security.keepass.enabled=false\n"
                + "network.proxy.mode=NONE\n"
                + extra;
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return new ConfigurationFile(path);
    }

    /** Öffnet den Dialog, zeigt die Felder, setzt optional neue Werte und speichert. */
    private String[] openAndSave(final FileSettingsActions actions, final String sources, final String results,
                                 final String tokens, final String rounds, final String pages) throws Exception {
        final AtomicReference<String[]> shown = new AtomicReference<String[]>();
        onEdt(() -> {
            try {
                SettingsPanel panel = new SettingsPanel(SettingsMapper.fromProperties(actions.file().read()),
                        Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions, palette);
                shown.set(new String[]{panel.indexTab().contextMaxSources().getText(),
                        panel.indexTab().retrievalMaxResults().getText(),
                        panel.indexTab().contextMaxTokens().getText(),
                        panel.toolsTab().maxRounds().getText(),
                        panel.systemTab().researchMaxPages().getText()});
                if (sources != null) {
                    panel.indexTab().contextMaxSources().setText(sources);
                    panel.indexTab().retrievalMaxResults().setText(results);
                    panel.indexTab().contextMaxTokens().setText(tokens);
                    panel.toolsTab().maxRounds().setText(rounds);
                    panel.systemTab().researchMaxPages().setText(pages);
                    assertTrue(actions.validate(panel.toForm()).toString(), actions.validate(panel.toForm()).isEmpty());
                    actions.save(panel.toForm());
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return shown.get();
    }

    @Test
    public void missingKeysShowTheDefaults() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(file(""), null, DIRECT, DIRECT);
        String[] shown = openAndSave(actions, null, null, null, null, null);
        assertEquals("6", shown[0]);
        assertEquals("10", shown[1]);
        assertEquals("1500", shown[2]);
        assertEquals("8", shown[3]);
        assertEquals("4", shown[4]);
    }

    @Test
    public void existingValuesAreShownAndKeptWhenSavingSomethingElse() throws Exception {
        ConfigurationFile file = file("context.maxSources=7\nretrieval.maxResults=12\ncontext.maxTokens=3000\n"
                + "chat.tools.maxRounds=5\n");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        String[] shown = openAndSave(actions, null, null, null, null, null);
        assertEquals("7", shown[0]);
        assertEquals("12", shown[1]);
        assertEquals("3000", shown[2]);
        assertEquals("5", shown[3]);

        actions.save(SettingsMapper.fromProperties(file.read()).toBuilder().windowTitle("Anders").build());
        assertEquals("7", file.read().getProperty("context.maxSources"));
        assertEquals("12", file.read().getProperty("retrieval.maxResults"));
        assertEquals("3000", file.read().getProperty("context.maxTokens"));
        assertEquals("5", file.read().getProperty("chat.tools.maxRounds"));
    }

    @Test
    public void threeSourcesAreStoredAndLoaded() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openAndSave(actions, "3", "10", "1500", "12", "4");

        AppConfig config = AppConfigLoader.load(file.path());
        assertEquals(3, config.knowledge().context().maxSources());
        assertEquals(12, config.chat().maxToolRounds());
        assertEquals("3", openAndSave(actions, null, null, null, null, null)[0]);
    }

    @Test
    public void tenSourcesAreStoredAndLoaded() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openAndSave(actions, "10", "20", "4000", "8", "4");

        AppConfig config = AppConfigLoader.load(file.path());
        assertEquals(10, config.knowledge().context().maxSources());
        assertEquals(20, config.knowledge().retrieval().maxResults());
        assertEquals(4000, config.knowledge().context().maxContextTokens());
    }

    /** Das Recherche-Budget des Agenten verändert die Quellen im Antwortkontext nicht, und umgekehrt. */
    @Test
    public void researchBudgetDoesNotChangeTheAnswerSources() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openAndSave(actions, "6", "10", "1500", "8", "10");

        AppConfig config = AppConfigLoader.load(file.path());
        assertEquals(10, config.agent().researchMaxPages());
        assertEquals(6, config.knowledge().context().maxSources());

        openAndSave(actions, "3", "10", "1500", "8", "4");
        config = AppConfigLoader.load(file.path());
        assertEquals(4, config.agent().researchMaxPages());
        assertEquals(3, config.knowledge().context().maxSources());
    }

    /** Kein 0 = unbegrenzt bei den Werkzeugrunden: 1–32. */
    @Test
    public void toolRoundsOutsideOneToThirtyTwoAreRejected() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(file(""), null, DIRECT, DIRECT);
        SettingsForm base = SettingsMapper.fromProperties(actions.file().read());
        assertFalse(actions.validate(base.toBuilder().chatToolsMaxRounds("0").build()).isEmpty());
        assertFalse(actions.validate(base.toBuilder().chatToolsMaxRounds("33").build()).isEmpty());
        assertTrue(actions.validate(base.toBuilder().chatToolsMaxRounds("32").build()).isEmpty());
        assertTrue(actions.validate(base.toBuilder().chatToolsMaxRounds("1").build()).isEmpty());
        assertFalse(actions.validate(base.toBuilder().contextMaxSources("0").build()).isEmpty());
    }

    private static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }
}
