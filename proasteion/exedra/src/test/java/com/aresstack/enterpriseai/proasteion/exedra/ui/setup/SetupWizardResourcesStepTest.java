package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Schritt 6 „Lokale KI- und Sprachressourcen“: die OpenNLP-Modelle stehen mit ihrer Quelle (Ressourcen-Archiv oder
 * Download) neben der Spracheingabe, „Alles installieren“ holt alle fehlenden nacheinander und danach das gewählte
 * Spracheingabe-Modell, und ein fehlgeschlagenes Modell hält weder die übrigen noch den Assistenten auf.
 */
public class SetupWizardResourcesStepTest {

    private final WizardFakes.Settings settings = new WizardFakes.Settings();

    private SetupWizardPanel start() throws Exception {
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

    @Test
    public void listsEveryNlpModelWithItsSource() throws Exception {
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(SetupWizardPanel.NLP_IN_ARCHIVE, wizard.nlpState("sentence-de"));
            assertEquals(SetupWizardPanel.NLP_DOWNLOAD, wizard.nlpState("langdetect"));
            assertEquals(SetupWizardPanel.NLP_IN_ARCHIVE, wizard.nlpState("pos-de"));
            assertTrue(wizard.installAllButton().isVisible());
            assertTrue(wizard.installAllButton().isEnabled());
        });
    }

    @Test
    public void installAllInstallsTheNlpModelsAndThenTheChosenSpeechModel() throws Exception {
        settings.offers.add(new LocalVoiceOffer("gemma-4-e2b", "Gemma 4 E2B", "", false, ModelCategory.STT));
        settings.installedAs = "gemma-local";
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.installAllButton().doClick();
            assertEquals(Arrays.asList("sentence-de", "sentence-en", "langdetect", "tokenize-de", "pos-de"),
                    settings.nlpInstallRequests);
            assertEquals(Arrays.asList("gemma-4-e2b"), settings.installRequests);
            for (String id : Arrays.asList("sentence-de", "sentence-en", "langdetect", "tokenize-de", "pos-de")) {
                assertEquals(id, SetupWizardPanel.NLP_INSTALLED, wizard.nlpState(id));
            }
            assertTrue("Spracheingabe installiert", wizard.isDone(SetupWizardPanel.STEP_SPEECH));
            assertFalse("nichts mehr zu installieren", wizard.installAllButton().isVisible());
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_SPEECH));
        });
    }

    @Test
    public void theFailureReasonStaysVisibleWhileTheSpeechModelInstalls() throws Exception {
        settings.nlpFailures.add("tokenize-de");
        settings.offers.add(new LocalVoiceOffer("gemma-4-e2b", "Gemma 4 E2B", "", false, ModelCategory.STT));
        settings.installedAs = "gemma-local";
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.installAllButton().doClick();
            assertEquals("Spracheingabe trotzdem installiert", Arrays.asList("gemma-4-e2b"), settings.installRequests);
            assertEquals("fehlgeschlagen", wizard.nlpState("tokenize-de"));
            assertTrue(wizard.nlpNote(), wizard.nlpNote().startsWith("Tokenisierung Deutsch: HTTP 403"));
        });
    }

    @Test
    public void aFailedModelKeepsTheOthersAndTheWizardGoing() throws Exception {
        settings.nlpFailures.add("langdetect");
        final SetupWizardPanel wizard = start();
        SwingUtilities.invokeAndWait(() -> {
            wizard.installAllButton().doClick();
            assertEquals("alle versucht", 5, settings.nlpInstallRequests.size());
            assertEquals(SetupWizardPanel.NLP_INSTALLED, wizard.nlpState("pos-de"));
            assertEquals("fehlgeschlagen", wizard.nlpState("langdetect"));
            String error = wizard.errorOf(SetupWizardPanel.STEP_SPEECH);
            assertNotNull(error);
            assertTrue(error, error.contains("Spracherkennung, alle Sprachen (HTTP 403)"));
            assertTrue(wizard.nlpNote(), wizard.nlpNote().contains("Spracherkennung, alle Sprachen: HTTP 403"));
            assertTrue("erneut versuchbar", wizard.installAllButton().isVisible());
            assertTrue("Überspringen geht immer", wizard.skipButton().isEnabled());
        });
    }
}
