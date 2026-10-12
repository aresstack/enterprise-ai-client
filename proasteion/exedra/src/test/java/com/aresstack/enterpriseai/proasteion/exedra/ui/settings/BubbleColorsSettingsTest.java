package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;
import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;
import com.aresstack.enterpriseai.proasteion.exedra.settings.FileSettingsActions;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reiter „Darstellung“: Farben laden, ändern, speichern und wieder laden; ungültige Hex-Werte, Kontrastwarnung ohne
 * Blockade, „Standard wiederherstellen“ und die Live-Vorschau.
 */
public class BubbleColorsSettingsTest {

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

    /** Öffnet den Dialog auf der Datei, lässt {@code edit} am Reiter arbeiten und speichert. */
    private void openEditAndSave(final FileSettingsActions actions, final Consumer<AppearanceTab> edit)
            throws Exception {
        onEdt(() -> {
            try {
                SettingsPanel panel = new SettingsPanel(SettingsMapper.fromProperties(actions.file().read()),
                        Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions, palette);
                edit.accept(panel.appearanceTab());
                SettingsForm form = panel.toForm();
                assertTrue(actions.validate(form).toString(), actions.validate(form).isEmpty());
                actions.save(form);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    public void oldFileShowsTheDefaultsAndSavingWritesNoBubbleKeys() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openEditAndSave(actions, tab -> {
            assertEquals("#1676D2", tab.userBackground().hexField().getText());
            assertEquals("#FFFFFF", tab.userForeground().hexField().getText());
            assertEquals("#15827A", tab.assistantBackground().hexField().getText());
            assertEquals("#FFFFFF", tab.assistantForeground().hexField().getText());
            assertFalse(tab.warning().isVisible());
        });
        Properties saved = file.read();
        assertNull(saved.getProperty(BubbleColorsConfig.USER_BACKGROUND_KEY));
        assertNull(saved.getProperty(BubbleColorsConfig.USER_FOREGROUND_KEY));
        assertNull(saved.getProperty(BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY));
        assertNull(saved.getProperty(BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY));
        assertEquals(BubbleColorsConfig.defaults(), AppConfigLoader.load(file.path()).bubbleColors());
    }

    @Test
    public void customColorsAreSavedAndLoadedAgain() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openEditAndSave(actions, tab -> {
            tab.userBackground().setColor(Color.WHITE);
            tab.userForeground().setColor(Color.BLACK);
            tab.assistantBackground().hexField().setText("#fffff0");
            tab.assistantForeground().hexField().setText("#202020");
        });
        assertEquals("#FFFFFF", file.read().getProperty(BubbleColorsConfig.USER_BACKGROUND_KEY));
        assertEquals("#000000", file.read().getProperty(BubbleColorsConfig.USER_FOREGROUND_KEY));
        assertEquals("#FFFFF0", file.read().getProperty(BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY));
        assertEquals("#202020", file.read().getProperty(BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY));
        AppConfig config = AppConfigLoader.load(file.path());
        assertEquals(new BubbleColorsConfig(0xFFFFFF, 0x000000, 0xFFFFF0, 0x202020), config.bubbleColors());

        openEditAndSave(actions, tab -> {
            assertEquals("#FFFFFF", tab.userBackground().hexField().getText());
            assertEquals("#000000", tab.userForeground().hexField().getText());
            assertEquals(new Color(0xFFFFF0), tab.assistantBackground().color());
            assertEquals(new Color(0x202020), tab.assistantForeground().swatch().color());
        });
    }

    @Test
    public void invalidHexIsFlaggedAndTheLastValidColorIsSaved() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openEditAndSave(actions, tab -> {
            tab.userBackground().hexField().setText("#FFFFFF");
            tab.userBackground().hexField().setText("#FFFFF");
            assertFalse(tab.userBackground().isValidInput());
            assertEquals(Color.WHITE, tab.userBackground().color());
            assertTrue(tab.warningText(), tab.warningText().contains("#RRGGBB"));
            tab.userForeground().hexField().setText("schwarz");
            assertEquals(Color.WHITE, tab.userForeground().color());
        });
        assertEquals("#FFFFFF", file.read().getProperty(BubbleColorsConfig.USER_BACKGROUND_KEY));
        // Schrift blieb Weiß (die Vorgabe): der Schlüssel entfällt.
        assertNull(file.read().getProperty(BubbleColorsConfig.USER_FOREGROUND_KEY));
    }

    @Test
    public void unreadableValueInTheFileIsShownAsInvalidAndDoesNotBlock() throws Exception {
        ConfigurationFile file = file("chat.bubble.assistant.background=gruen\n");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        assertEquals(BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND,
                AppConfigLoader.load(file.path()).bubbleColors().assistantBackground());
        openEditAndSave(actions, tab -> {
            assertEquals("gruen", tab.assistantBackground().hexField().getText());
            assertFalse(tab.assistantBackground().isValidInput());
            assertEquals(new Color(BubbleColorsConfig.DEFAULT_ASSISTANT_BACKGROUND), tab.assistantBackground().color());
        });
        assertNull("die Vorgabe ersetzt den unlesbaren Wert",
                file.read().getProperty(BubbleColorsConfig.ASSISTANT_BACKGROUND_KEY));
    }

    @Test
    public void lowContrastWarnsButSavingStillWorks() throws Exception {
        ConfigurationFile file = file("");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openEditAndSave(actions, tab -> {
            tab.userBackground().setColor(Color.WHITE);
            tab.userForeground().setColor(new Color(0xEEEEEE));
            assertTrue(tab.warning().isVisible());
            assertTrue(tab.warningText(), tab.warningText().contains(AppearanceTab.LOW_CONTRAST_WARNING));
            assertTrue(tab.warningText(), tab.warningText().contains("eigene Nachrichten"));
            assertFalse(tab.warningText(), tab.warningText().contains("(Assistent)"));
        });
        assertEquals("#EEEEEE", file.read().getProperty(BubbleColorsConfig.USER_FOREGROUND_KEY));
    }

    @Test
    public void restoreDefaultsSetsTheFourFormerColors() throws Exception {
        ConfigurationFile file = file("chat.bubble.user.background=#FFFFFF\nchat.bubble.user.foreground=#000000\n"
                + "chat.bubble.assistant.background=#FFFFFF\nchat.bubble.assistant.foreground=#000000\n");
        FileSettingsActions actions = new FileSettingsActions(file, null, DIRECT, DIRECT);
        openEditAndSave(actions, tab -> {
            assertEquals(Color.WHITE, tab.assistantBackground().color());
            tab.resetButton().doClick();
            assertEquals("#1676D2", tab.userBackground().hexField().getText());
            assertEquals("#FFFFFF", tab.userForeground().hexField().getText());
            assertEquals("#15827A", tab.assistantBackground().hexField().getText());
            assertEquals("#FFFFFF", tab.assistantForeground().hexField().getText());
        });
        Properties saved = file.read();
        assertNull(saved.getProperty(BubbleColorsConfig.USER_BACKGROUND_KEY));
        assertNull(saved.getProperty(BubbleColorsConfig.ASSISTANT_FOREGROUND_KEY));
        assertEquals(BubbleColorsConfig.defaults(), AppConfigLoader.load(file.path()).bubbleColors());
    }

    @Test
    public void previewFollowsTheChosenColors() throws Exception {
        onEdt(() -> {
            AppearanceTab tab = new AppearanceTab(palette);
            assertEquals(new Color(0x1676D2), tab.userPreview().getBubbleColor());
            assertEquals(new Color(0x15827A), tab.assistantPreview().getBubbleColor());
            int bubbles = tab.userPreview().getPreferredSize().height + tab.assistantPreview().getPreferredSize().height;
            assertTrue("Vorschau zeigt beide Blasen: " + tab.preview().getPreferredSize(),
                    tab.preview().getPreferredSize().height >= bubbles);
            tab.assistantBackground().setColor(Color.WHITE);
            tab.assistantForeground().hexField().setText("#000000");
            assertEquals(Color.WHITE, tab.assistantPreview().getBubbleColor());
            assertEquals(Color.BLACK, tab.assistantPreview().getTextColor());
            assertEquals(new Color(0x1676D2), tab.userPreview().getBubbleColor());
        });
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
