package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Der optionale letzte Schritt „Lokale Spracheingabe“ über den echten Assistenten: installierte STT-Modelle sind
 * markiert und direkt wählbar, Installieren zeigt den Fortschritt und wählt das Modell danach, ein Fehler bietet
 * „Erneut versuchen“, und Überspringen schließt die Einrichtung jederzeit ab, auch während eines Downloads.
 */
public class SetupWizardSpeechStepTest {

    private static final String SUGGESTION = "gemma-4-e2b";
    private static final String INSTALLED_FOLDER = "onnx-community--gemma-4-E2B-it-ONNX--audio_q4";

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();
    private final List<Boolean> ended = new ArrayList<Boolean>();

    private SetupWizardPanel start() throws Exception {
        final AtomicReference<SetupWizardPanel> wizard = new AtomicReference<SetupWizardPanel>();
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                    ComicPalette.defaultPalette(), ended::add);
            SetupWizardDriver.throughBasicSetup(run.wizard());
            wizard.set(run.wizard());
        });
        return wizard.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static LocalVoiceOffer stt(String id, String name, boolean installed) {
        return new LocalVoiceOffer(id, name, "", installed, ModelCategory.STT);
    }

    private SettingsForm lastSaved() {
        return settings.saved.get(settings.saved.size() - 1);
    }

    @Test
    public void installedModelIsMarkedPreselectedAndStoredAsModelStt() throws Exception {
        settings.offers.add(new LocalVoiceOffer("thorsten", "Thorsten", "de_DE", true, ModelCategory.TTS));
        settings.offers.add(stt(SUGGESTION, "Gemma 4 E2B (Vorschlag)", false));
        settings.offers.add(stt("gemma-local", "Gemma 4 E2B", true));
        final SetupWizardPanel wizard = start();
        onEdt(() -> {
            assertEquals("nur Spracheingabe, installierte zuerst", 2, wizard.speechModelChoice().getItemCount());
            assertEquals("Gemma 4 E2B (installiert)", wizard.speechModelChoice().getItemAt(0));
            assertEquals(0, wizard.speechModelChoice().getSelectedIndex());
            assertFalse("nichts zu installieren", wizard.speechInstallButton().isVisible());
            assertTrue(wizard.skipButton().isVisible());
            assertFalse("Abbrechen weicht dem Überspringen", wizard.cancelButton().isVisible());
            SetupWizardDriver.finish(wizard);
        });
        assertEquals("local:gemma-local", lastSaved().modelSelection(ModelCategory.STT.key()));
        assertEquals("Grundeinrichtung bleibt erhalten", "chat-model", lastSaved().chatModel());
        assertEquals(1, ended.size());
        assertTrue(ended.get(0));
    }

    @Test
    public void installShowsProgressAndSelectsTheInstalledModelAfterwards() throws Exception {
        settings.offers.add(stt(SUGGESTION, "Gemma 4 E2B", false));
        settings.holdInstalls = true;
        settings.installedAs = INSTALLED_FOLDER;
        final SetupWizardPanel wizard = start();
        onEdt(() -> {
            assertTrue(wizard.speechInstallButton().isVisible());
            assertFalse("ohne Modell kein Fertig", wizard.finishButton().isEnabled());
            assertTrue("Überspringen geht immer", wizard.skipButton().isEnabled());
            SetupWizardDriver.install(wizard);
            assertEquals(SUGGESTION, settings.installRequests.get(0));
            assertTrue("Fortschritt sichtbar", wizard.speechProgress().isVisible());
            assertEquals(50, wizard.speechProgress().bar().getValue());
            assertTrue(wizard.speechProgress().caption(), wizard.speechProgress().caption().startsWith("Datei 1 von 3"));
            assertTrue(wizard.speechProgress().bar().getString(), wizard.speechProgress().bar().getString().startsWith("50 %"));
            assertFalse("während des Downloads kein Fertig", wizard.finishButton().isEnabled());
            assertTrue("Überspringen auch während des Downloads", wizard.skipButton().isEnabled());
            settings.complete(settings.pendingInstalls.remove(0));
            assertFalse(wizard.speechProgress().isVisible());
            assertEquals("Gemma 4 E2B (Spracheingabe) (installiert)", wizard.speechModelChoice().getSelectedItem());
            SetupWizardDriver.finish(wizard);
        });
        assertEquals("local:" + INSTALLED_FOLDER, lastSaved().modelSelection(ModelCategory.STT.key()));
        assertTrue(ended.get(0));
    }

    @Test
    public void downloadErrorOffersRetryAndSkipStillCompletesTheSetup() throws Exception {
        settings.offers.add(stt(SUGGESTION, "Gemma 4 E2B", false));
        settings.installFailure = "HTTP 503 vom Hub";
        settings.installedAs = INSTALLED_FOLDER;
        final SetupWizardPanel wizard = start();
        onEdt(() -> {
            SetupWizardDriver.install(wizard);
            String error = wizard.errorOf(SetupWizardPanel.STEP_SPEECH);
            assertTrue(error, error.contains("HTTP 503 vom Hub"));
            assertTrue("Erneut versuchen", wizard.speechRetryButton().isVisible());
            assertFalse(wizard.speechInstallButton().isVisible());
            assertTrue("Überspringen", wizard.skipButton().isEnabled());
            assertFalse(wizard.finishButton().isEnabled());

            settings.installFailure = null;
            wizard.speechRetryButton().doClick();
            assertEquals(2, settings.installRequests.size());
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_SPEECH));
            assertTrue("nach Erfolg wählbar", wizard.finishButton().isEnabled());
            SetupWizardDriver.skip(wizard);
        });
        assertTrue("übersprungen ist trotzdem eingerichtet", ended.get(0));
        assertTrue("Überspringen speichert keine Auswahl", settings.saved.isEmpty());
    }

    @Test
    public void skipDuringDownloadFinishesWithoutSpeechModel() throws Exception {
        settings.offers.add(stt(SUGGESTION, "Gemma 4 E2B", false));
        settings.holdInstalls = true;
        final SetupWizardPanel wizard = start();
        onEdt(() -> {
            SetupWizardDriver.install(wizard);
            SetupWizardDriver.skip(wizard);
        });
        assertEquals(1, ended.size());
        assertTrue(ended.get(0));
        assertTrue("Überspringen speichert keine Auswahl", settings.saved.isEmpty());
    }

    @Test
    public void withoutOffersOnlySkipRemains() throws Exception {
        final SetupWizardPanel wizard = start();
        onEdt(() -> {
            assertEquals(0, wizard.speechModelChoice().getItemCount());
            assertFalse(wizard.speechInstallButton().isVisible());
            assertFalse(wizard.finishButton().isEnabled());
            SetupWizardDriver.skip(wizard);
        });
        assertTrue(ended.get(0));
    }

    @Test
    public void offersThatCannotBeLoadedAreNotShownAsNoModelsAndCanBeRetried() throws Exception {
        settings.offersFailure = "Die Angebote ließen sich nicht ermitteln (IllegalStateException: kaputt).";
        settings.offers.add(stt(SUGGESTION, "Gemma 4 E2B (Spracheingabe)", false));
        final SetupWizardPanel wizard = start();
        onEdt(() -> {
            String error = wizard.errorOf(SetupWizardPanel.STEP_SPEECH);
            assertTrue(String.valueOf(error), error != null && error.contains("kaputt"));
            assertFalse(wizard.speechStatusText(), wizard.speechStatusText().contains("Kein lokales Modell"));
            assertTrue("Erneut versuchen", wizard.speechRetryButton().isVisible());
            assertTrue("Überspringen bleibt", wizard.skipButton().isEnabled());
            settings.offersFailure = null;
            wizard.speechRetryButton().doClick();
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_SPEECH));
            assertEquals(1, wizard.speechModelChoice().getItemCount());
            assertTrue("noch nicht installiert: Installieren", wizard.speechInstallButton().isVisible());
            assertFalse(wizard.speechRetryButton().isVisible());
        });
    }

    @Test
    public void cancelBeforeTheFinalCheckEndsUncompleted() throws Exception {
        onEdt(() -> {
            SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                    ComicPalette.defaultPalette(), ended::add);
            run.wizard().cancelButton().doClick();
            run.wizard().cancel(); // ein zweites Ende zählt nicht
        });
        assertEquals(1, ended.size());
        assertFalse(ended.get(0));
    }
}
