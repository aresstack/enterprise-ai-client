package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.control.ComicComboBoxUI;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Schritt „Lokale KI“ (#103): Comic-Häkchen rechts neben der Spracheingabe und jedem Textanalyse-Paket in einer
 * Spalte; „Ausgewählte installieren“ installiert genau die angehakten, noch fehlenden Ressourcen, „Alles
 * installieren“ bleibt unabhängig von den Häkchen; das Häkchen der Spracheingabe gehört immer zum gewählten Modell.
 */
public class SetupWizardResourceSelectionTest {

    private final WizardFakes.Settings settings = new WizardFakes.Settings();

    private SetupWizardPanel start() throws Exception {
        settings.offers.add(stt("gemma-a", "Gemma A"));
        settings.offers.add(stt("gemma-b", "Gemma B"));
        settings.installedAs = "gemma-local";
        final AtomicReference<SetupWizardPanel> wizard = new AtomicReference<SetupWizardPanel>();
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings,
                    new WizardFakes.Setup(), ComicPalette.defaultPalette(), completed -> {
            });
            SetupWizardDriver.throughBasicSetup(run.wizard());
            wizard.set(run.wizard());
        });
        return wizard.get();
    }

    private static LocalVoiceOffer stt(String id, String name) {
        return new LocalVoiceOffer(id, name, "", false, ModelCategory.STT);
    }

    @Test
    public void nothingIsTickedAtFirstAndInstallSelectedWaits() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(SetupWizardPanel.INSTALL_SELECTED, wizard.speechInstallButton().getText());
            assertFalse(wizard.speechCheckBox().isSelected());
            assertFalse(wizard.nlpCheckBox("pos-de").isSelected());
            assertFalse("ohne Häkchen gesperrt", wizard.speechInstallButton().isEnabled());
            assertTrue(wizard.installAllButton().isEnabled());
            assertTrue(settings.nlpInstallRequests.isEmpty());
            assertTrue(settings.installRequests.isEmpty());
        });
    }

    @Test
    public void onlyTheTickedTextAnalysisPackagesAreInstalled() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.nlpCheckBox("pos-de").doClick();
            wizard.nlpCheckBox("langdetect").doClick();
            assertTrue(wizard.speechInstallButton().isEnabled());
            wizard.speechInstallButton().doClick();
            assertEquals("Reihenfolge der Liste", Arrays.asList("langdetect", "pos-de"), settings.nlpInstallRequests);
            assertTrue("keine Spracheingabe", settings.installRequests.isEmpty());
            assertEquals(SetupWizardPanel.NLP_INSTALLED, wizard.nlpState("pos-de"));
            assertEquals(SetupWizardPanel.NLP_IN_ARCHIVE, wizard.nlpState("sentence-de"));
        });
    }

    @Test
    public void onlyTheTickedSpeechModelIsInstalled() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.speechCheckBox().doClick();
            wizard.speechInstallButton().doClick();
            assertEquals(Collections.singletonList("gemma-a"), settings.installRequests);
            assertTrue("kein OpenNLP-Paket", settings.nlpInstallRequests.isEmpty());
        });
    }

    @Test
    public void speechAndTextAnalysisTogether() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.speechCheckBox().doClick();
            wizard.nlpCheckBox("tokenize-de").doClick();
            wizard.speechInstallButton().doClick();
            assertEquals(Collections.singletonList("tokenize-de"), settings.nlpInstallRequests);
            assertEquals(Collections.singletonList("gemma-a"), settings.installRequests);
        });
    }

    @Test
    public void anotherSpeechModelDropsTheTickOfThePreviousOne() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.speechCheckBox().doClick();
            assertTrue(wizard.speechCheckBox().isSelected());
            wizard.speechModelChoice().setSelectedIndex(1);
            assertFalse("Häkchen gehört zum neuen Modell", wizard.speechCheckBox().isSelected());
            assertFalse(wizard.speechInstallButton().isEnabled());
            wizard.speechCheckBox().doClick();
            wizard.speechInstallButton().doClick();
            assertEquals("nur das neu gewählte", Collections.singletonList("gemma-b"), settings.installRequests);
        });
    }

    @Test
    public void installAllIgnoresTheTicks() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.nlpCheckBox("pos-de").doClick();
            wizard.installAllButton().doClick();
            assertEquals("alle fehlenden", 5, settings.nlpInstallRequests.size());
            assertEquals("und das gewählte Modell", Collections.singletonList("gemma-a"), settings.installRequests);
        });
    }

    @Test
    public void boxesFormOneColumnAndTheDropdownHasTheComicLook() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(wizard.speechModelChoice().getUI() instanceof ComicComboBoxUI);
            wizard.setSize(900, 760);
            layoutTree(wizard);
            JCheckBox speech = wizard.speechCheckBox();
            Point speechAt = SwingUtilities.convertPoint(speech.getParent(), speech.getLocation(), wizard);
            for (String id : Arrays.asList("sentence-de", "langdetect", "pos-de")) {
                JCheckBox box = wizard.nlpCheckBox(id);
                Point at = SwingUtilities.convertPoint(box.getParent(), box.getLocation(), wizard);
                assertEquals(id + ": linke Kante", speechAt.x, at.x);
                assertEquals(id + ": Breite", speech.getWidth(), box.getWidth());
                assertTrue(id + ": darunter", at.y > speechAt.y);
            }
        });
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutTree((Container) child);
            }
        }
    }
}
