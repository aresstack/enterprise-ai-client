package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceInstallProgress;
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
 * The stop button next to the download progress, "Überspringen" and closing the wizard all cancel the real download
 * through its {@link InstallCancellation}; a stopped download is no failure, a late cancel changes nothing.
 */
public class SetupWizardDownloadCancelTest {

    private static final String SUGGESTION = "gemma-4-e2b";
    private static final String INSTALLED_FOLDER = "onnx-community--gemma-4-E2B-it-ONNX--audio_q4";

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();
    private final List<Boolean> ended = new ArrayList<Boolean>();

    private SetupWizardPanel downloading() throws Exception {
        settings.offers.add(new LocalVoiceOffer(SUGGESTION, "Gemma 4 E2B", "", false, ModelCategory.STT));
        settings.holdInstalls = true;
        settings.installedAs = INSTALLED_FOLDER;
        final AtomicReference<SetupWizardPanel> wizard = new AtomicReference<SetupWizardPanel>();
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                    ComicPalette.defaultPalette(), ended::add);
            SetupWizardDriver.throughBasicSetup(run.wizard());
            assertFalse("no stop button before a download", run.wizard().speechCancelButton().isVisible());
            SetupWizardDriver.install(run.wizard());
            assertTrue(run.wizard().isSpeechDownloading());
            wizard.set(run.wizard());
        });
        return wizard.get();
    }

    /** Act like the transfer: it notices the request at its next block and reports the stop. */
    private void transferNoticesCancel() {
        assertTrue("cancel requested", handle().isCancelled());
        settings.pendingInstalls.remove(0).cancelled("Download abgebrochen. Das Modell wurde nicht installiert.");
    }

    private InstallCancellation handle() {
        return settings.installCancellations.get(settings.installCancellations.size() - 1);
    }

    private static boolean hasSpeechModel(SettingsForm form) {
        String stt = form.modelSelection(ModelCategory.STT.key());
        return stt != null && !stt.isEmpty();
    }

    @Test
    public void stopButtonCancelsTheTransferAndKeepsTheWizardUsable() throws Exception {
        final SetupWizardPanel wizard = downloading();
        SwingUtilities.invokeAndWait(() -> {
            assertTrue("stop button shown while downloading", wizard.speechCancelButton().isVisible());
            assertTrue(wizard.speechCancelButton().isEnabled());
            wizard.speechCancelButton().doClick();
            assertTrue(handle().isCancelled());
            transferNoticesCancel();
            assertFalse(wizard.isSpeechDownloading());
            assertFalse("stop button gone", wizard.speechCancelButton().isVisible());
            assertFalse(wizard.speechProgressBar().isVisible());
            assertEquals(SetupWizardPanel.SPEECH_CANCELLED, wizard.speechStatusText());
            assertNull("a stop is no failure", wizard.errorOf(SetupWizardPanel.STEP_SPEECH));
            assertFalse("nothing installed", wizard.isDone(SetupWizardPanel.STEP_SPEECH));
            assertTrue("install again offered", wizard.speechInstallButton().isVisible());
            assertTrue(wizard.speechInstallButton().isEnabled());
            assertTrue("finish without speech model", wizard.skipButton().isEnabled());
            SetupWizardDriver.skip(wizard);
        });
        assertTrue(ended.get(0));
        for (SettingsForm form : settings.saved) {
            assertFalse("no model.stt", hasSpeechModel(form));
        }
    }

    @Test
    public void skipDuringDownloadCancelsTheTransferAndEndsWithoutSpeechModel() throws Exception {
        final SetupWizardPanel wizard = downloading();
        final LocalVoiceInstallProgress progress = settings.pendingInstalls.get(0);
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardDriver.skip(wizard);
            assertTrue("skip cancels the transfer", handle().isCancelled());
            // late reports of the stopped transfer must not touch the closed wizard
            progress.file("audio_encoder_q4.onnx", 1, 3, 900L * 1024, 1024L * 1024);
            progress.cancelled("Download abgebrochen.");
            progress.finished(false, "spät");
            assertFalse(wizard.isSpeechDownloading());
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_SPEECH));
        });
        assertEquals(1, ended.size());
        assertTrue(ended.get(0));
        for (SettingsForm form : settings.saved) {
            assertFalse("no model.stt", hasSpeechModel(form));
        }
    }

    @Test
    public void closingTheWizardDuringDownloadCancelsTheTransfer() throws Exception {
        final SetupWizardPanel wizard = downloading();
        SwingUtilities.invokeAndWait(() -> {
            wizard.cancel();
            assertTrue("window X cancels the transfer", handle().isCancelled());
            settings.complete(settings.pendingInstalls.remove(0));
            assertFalse("late completion ignored by the closed wizard", wizard.isSpeechDownloading());
        });
        assertEquals(1, ended.size());
        assertFalse(ended.get(0));
    }

    @Test
    public void lateCancelAfterCompletedInstallChangesNothing() throws Exception {
        final SetupWizardPanel wizard = downloading();
        SwingUtilities.invokeAndWait(() -> {
            final InstallCancellation download = handle();
            wizard.speechCancelButton().doClick();
            // the transfer was already done: completion wins, the model is installed
            settings.complete(settings.pendingInstalls.remove(0));
            assertTrue(download.isCancelled());
            assertFalse(wizard.isSpeechDownloading());
            assertTrue("installed despite the late cancel", wizard.isDone(SetupWizardPanel.STEP_SPEECH));
            wizard.stopSpeechDownload();
            assertTrue("still installed", wizard.isDone(SetupWizardPanel.STEP_SPEECH));
            SetupWizardDriver.finish(wizard);
        });
        assertTrue(ended.get(0));
    }
}
