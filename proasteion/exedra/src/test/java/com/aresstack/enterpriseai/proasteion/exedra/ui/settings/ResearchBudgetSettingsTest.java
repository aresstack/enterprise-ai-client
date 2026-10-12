package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

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
 * Netzwerk &amp; Agent → Agent-Modus (ACP) → „Recherche-Budget (Seiten)“: der Wert im Feld landet als
 * {@code agent.research.maxPages} in der Datei und kommt beim Neuladen unverändert zurück (auch 0 = unbegrenzt);
 * fehlt der Schlüssel, gilt 4. Das Vorladen bleibt davon unberührt.
 */
public class ResearchBudgetSettingsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private final ComicPalette palette = ComicPalette.defaultPalette();

    private ConfigurationFile file() throws Exception {
        Path path = tmp.getRoot().toPath().resolve("app.properties");
        String text = "chat.baseUrl=http://127.0.0.1:9/v1\n"
                + "chat.model=test-chat\n"
                + "chat.apiKeyRef=keepass:Enterprise AI API\n"
                + "embedding.model=test-embedding\n"
                + "embedding.dimension=8\n"
                + "security.keepass.enabled=false\n"
                + "network.proxy.mode=NONE\n";
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return new ConfigurationFile(path);
    }

    /** Öffnet den Dialog auf der Datei, tippt {@code typed} ins Feld (null: unverändert), speichert über die Aktionen. */
    private String saveThroughTheDialog(final FileSettingsActions actions, final String typed) throws Exception {
        final AtomicReference<String> shown = new AtomicReference<String>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                try {
                    SettingsPanel panel = new SettingsPanel(SettingsMapper.fromProperties(actions.file().read()),
                            Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions, palette);
                    shown.set(panel.systemTab().researchMaxPages().getText());
                    if (typed != null) {
                        panel.systemTab().researchMaxPages().setText(typed);
                        assertTrue(actions.validate(panel.toForm()).toString(),
                                actions.validate(panel.toForm()).isEmpty());
                        actions.save(panel.toForm());
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        });
        return shown.get();
    }

    private String reloadedField(FileSettingsActions actions) throws Exception {
        return saveThroughTheDialog(actions, null);
    }

    @Test
    public void zeroIsStoredAndStaysZeroAfterReload() throws Exception {
        ConfigurationFile file = file();
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        saveThroughTheDialog(actions, "0");

        assertEquals("0", file.read().getProperty("agent.research.maxPages"));
        assertEquals("0", reloadedField(actions));
        assertEquals(0, AppConfigLoader.load(file.path()).agent().researchMaxPages());
    }

    @Test
    public void twelveIsStoredAndStaysTwelveAfterReload() throws Exception {
        ConfigurationFile file = file();
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        saveThroughTheDialog(actions, "12");

        assertEquals("12", file.read().getProperty("agent.research.maxPages"));
        assertEquals("12", reloadedField(actions));
        assertEquals(12, AppConfigLoader.load(file.path()).agent().researchMaxPages());
    }

    @Test
    public void aMissingValueMeansFour() throws Exception {
        ConfigurationFile file = file();
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);

        assertEquals(null, file.read().getProperty("agent.research.maxPages"));
        assertEquals("4", reloadedField(actions));
        assertEquals(4, AppConfigLoader.load(file.path()).agent().researchMaxPages());
    }

    @Test
    public void fourIsTheDefaultAndAnOlderToolsKeyNeverOverridesTheChosenValue() throws Exception {
        ConfigurationFile file = file();
        java.util.Map<String, String> legacy = new java.util.LinkedHashMap<String, String>();
        legacy.put("agent.tools.maxPages", "10");
        file.update(legacy, Collections.<String>emptySet(), AppConfigLoader.exampleConfiguration());
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        assertEquals("älterer Wert erscheint im Feld", "10", reloadedField(actions));

        saveThroughTheDialog(actions, "4");
        assertEquals(null, file.read().getProperty("agent.tools.maxPages"));
        assertEquals(null, file.read().getProperty("agent.research.maxPages"));
        assertEquals("4", reloadedField(actions));
        assertEquals(4, AppConfigLoader.load(file.path()).agent().researchMaxPages());
    }

    @Test
    public void valuesOutsideZeroToTenThousandAreRejected() throws Exception {
        FileSettingsActions actions = new FileSettingsActions(file(), null, DIRECT, DIRECT);
        SettingsForm base = SettingsMapper.fromProperties(actions.file().read());
        assertFalse(actions.validate(base.toBuilder().researchMaxPages("-1").build()).isEmpty());
        assertFalse(actions.validate(base.toBuilder().researchMaxPages("10001").build()).isEmpty());
        assertTrue(actions.validate(base.toBuilder().researchMaxPages("10000").build()).isEmpty());
    }

    @Test
    public void theBudgetIsIndependentOfThePrefetchSettings() throws Exception {
        ConfigurationFile file = file();
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        saveThroughTheDialog(actions, "12");

        assertEquals(null, file.read().getProperty(AppConfigLoader.KNOWLEDGE_PREFETCH_CONCURRENCY_KEY));
        assertEquals(null, file.read().getProperty(AppConfigLoader.KNOWLEDGE_PREFETCH_MAX_ITEMS_KEY));
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
