package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Einstellungen → Technische Details: Merlin am Rand und die Werkzeuge des Maschinenraums. */
public class TechnicalDetailsMerlinTest {

    private static final class Actions implements SettingsDialogActions {
        List<String> problems = Collections.emptyList();

        @Override
        public List<String> validate(SettingsForm form) {
            return problems;
        }

        @Override
        public void save(SettingsForm form) {
        }

        @Override
        public void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult) {
            onResult.accept(SecretCheckResult.ok(""));
        }

        @Override
        public void refreshModels(SettingsForm form, Consumer<ModelCatalogSnapshot> onResult) {
            onResult.accept(ModelCatalogSnapshot.empty());
        }

        @Override
        public void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
            listener.onStep(ConnectionCheckStep.ok("GET /models", "HTTP 200"));
            listener.onFinished(true);
        }
    }

    @Test
    public void merlinWaitsInTheTechnicalCornerWithHisLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SettingsPanel panel = new SettingsPanel(SettingsMapper.firstStartDefaults(),
                    Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, new Actions(),
                    ComicPalette.defaultPalette());
            assertEquals("Ich wusste, dass wir uns wiedersehen.", panel.technicalMerlin().line());
            assertTrue("hat eine Höhe am Rand", panel.technicalMerlin().getPreferredSize().height > 0);
        });
    }

    @Test
    public void configurationCheckAndConnectionTestReportInPlace() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Actions actions = new Actions();
            SettingsPanel panel = new SettingsPanel(SettingsMapper.firstStartDefaults(),
                    Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions, ComicPalette.defaultPalette());
            assertFalse(panel.technicalStatus().isVisible());
            panel.checkConfigurationForTest();
            assertEquals("Konfiguration in Ordnung.", panel.technicalStatus().getText());
            actions.problems = Collections.singletonList("chat.model fehlt");
            panel.checkConfigurationForTest();
            assertTrue(panel.technicalStatus().getText().contains("chat.model fehlt"));
            panel.testConnectionForTest();
            assertTrue(panel.technicalStatus().getText().contains("GET /models"));
            assertTrue(panel.technicalStatus().getText().endsWith("Verbindung in Ordnung."));
        });
    }
}
