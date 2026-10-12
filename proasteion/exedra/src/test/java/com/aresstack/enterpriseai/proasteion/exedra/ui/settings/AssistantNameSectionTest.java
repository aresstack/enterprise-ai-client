package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Platte „Assistent“ im Reiter „Sprachausgabe“: Feld, Knopf „Standard“ und Vorschau. */
public class AssistantNameSectionTest {

    private static final String DEFAULT = "Standardname";

    private static SettingsForm form(String name) {
        return SettingsForm.builder().assistantDisplayName(name).defaultAssistantDisplayName(DEFAULT).build();
    }

    @Test
    public void fieldPreviewAndDefaultButton() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AssistantNameSection section = new AssistantNameSection(ComicPalette.defaultPalette());
            section.load(form("Morgana"));
            assertEquals("Morgana", section.nameField().getText());
            assertEquals("Morgana", section.previewHeader());

            section.nameField().setText("Viviane");
            assertEquals("Viviane", section.previewHeader());

            section.defaultButton().doClick();
            assertEquals(DEFAULT, section.nameField().getText());
            assertEquals(DEFAULT, section.previewHeader());

            section.nameField().setText("   ");
            assertEquals("leeres Feld zeigt den Standard", DEFAULT, section.previewHeader());

            section.nameField().setText(" Viviane ");
            SettingsForm.Builder b = form("Morgana").toBuilder().readAloudAutoStart(true);
            section.store(b);
            SettingsForm stored = b.build();
            assertEquals("Viviane", stored.assistantDisplayName());
            assertEquals(DEFAULT, stored.defaultAssistantDisplayName());
            assertTrue(stored.readAloudAutoStart());
        });
    }

}
