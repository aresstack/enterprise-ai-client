package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Schritt „KI-Dienst“ nach einem kurzen Ausfall (#101): Eine vorübergehende Störung prüft der Assistent begrenzt von
 * selbst erneut, ein Erfolg räumt den alten Fehler ab, ein bleibender Fehler bleibt stehen und wird nicht wiederholt,
 * erneutes Betreten prüft neu, und nach dem Schließen ändert keine verspätete Antwort mehr etwas.
 */
public class SetupWizardServiceRecheckTest {

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private SetupWizardPanel open() {
        return new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                ComicPalette.defaultPalette(), completed -> {
                }).wizard();
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static List<ConnectionCheckStep> keePassUnreachable() {
        return Collections.singletonList(ConnectionCheckStep.warning("API-Key",
                "KeePass ist unter 127.0.0.1:12546 nicht erreichbar. Der Aufruf geht ohne API-Key weiter.")
                .asTemporary());
    }

    private static List<ConnectionCheckStep> keyRejected() {
        return Arrays.asList(ConnectionCheckStep.ok("API-Key", "gelesen"),
                ConnectionCheckStep.failed("GET /models", "HTTP 401: Der Dienst lehnt den API-Key ab."));
    }

    @Test
    public void temporaryOutageClearsTheErrorAfterTheAutomaticRecheck() throws Exception {
        settings.connectionFailures.add(keePassUnreachable());
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            String error = wizard.errorOf(SetupWizardPanel.STEP_SERVICE);
            assertNotNull(error);
            assertTrue(error, error.contains("nicht erreichbar"));
            assertTrue(error, error.contains("von selbst erneut"));
            assertTrue("Wiederprüfung geplant", wizard.isServiceRecheckPending());
            assertFalse("nur KeePass betroffen: keine Netzwerkeinstellungen", wizard.networkButton().isVisible());
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_SERVICE));

            wizard.fireServiceRecheck();

            assertNull("alter Fehler weg", wizard.errorOf(SetupWizardPanel.STEP_SERVICE));
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
            assertFalse(wizard.isServiceRecheckPending());
            assertFalse(wizard.networkButton().isVisible());
            assertTrue(wizard.serviceStatusText(), wizard.serviceStatusText().contains("erreichbar"));
            assertEquals(2, settings.connectionChecks);
        });
    }

    @Test
    public void permanentFailureStaysAndIsNotRetried() throws Exception {
        settings.connectionFailures.add(keyRejected());
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            String error = wizard.errorOf(SetupWizardPanel.STEP_SERVICE);
            assertNotNull(error);
            assertTrue(error, error.contains("HTTP 401"));
            assertFalse("kein Retry bei abgelehntem Key", wizard.isServiceRecheckPending());
            assertTrue("HTTP betroffen: Netzwerkeinstellungen angeboten", wizard.networkButton().isVisible());
            assertEquals(1, settings.connectionChecks);
        });
    }

    @Test
    public void lastingOutageStopsAfterTheConfiguredRechecks() throws Exception {
        for (int i = 0; i < 10; i++) {
            settings.connectionFailures.add(keePassUnreachable());
        }
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            int rechecks = 0;
            while (wizard.isServiceRecheckPending() && rechecks < 20) {
                wizard.fireServiceRecheck();
                rechecks++;
            }
            assertEquals("Standard: vier Wiederprüfungen", 4, rechecks);
            assertNotNull("Fehler bleibt sichtbar", wizard.errorOf(SetupWizardPanel.STEP_SERVICE));
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
            assertTrue(wizard.serviceStatusText(), wizard.serviceStatusText().contains("Weiterhin"));
        });
    }

    @Test
    public void returningToTheStepChecksAgainInsteadOfShowingTheOldError() throws Exception {
        settings.connectionFailures.add(keyRejected());
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            assertNotNull(wizard.errorOf(SetupWizardPanel.STEP_SERVICE));
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            assertEquals(2, settings.connectionChecks);
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_SERVICE));
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
        });
    }

    @Test
    public void closingStopsRechecksAndIgnoresLateAnswers() throws Exception {
        settings.connectionFailures.add(keePassUnreachable());
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            assertTrue(wizard.isServiceRecheckPending());
            settings.holdConnections = true;
            wizard.serviceTestButton().doClick();
            assertFalse("Test von Hand ersetzt die geplante Wiederprüfung", wizard.isServiceRecheckPending());
            wizard.cancel();
            assertFalse(wizard.isServiceRecheckPending());
            String before = wizard.errorOf(SetupWizardPanel.STEP_SERVICE);
            for (ConnectionCheckStep step : keyRejected()) {
                settings.heldConnections.get(0).onStep(step);
            }
            settings.heldConnections.get(0).onFinished(false);
            assertEquals("verspätete Antwort ändert nichts", before, wizard.errorOf(SetupWizardPanel.STEP_SERVICE));
            assertFalse(wizard.isServiceRecheckPending());
        });
    }
}
