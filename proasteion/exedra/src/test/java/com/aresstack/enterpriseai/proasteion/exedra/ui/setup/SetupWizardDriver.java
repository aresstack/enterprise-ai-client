package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import javax.swing.JComboBox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Bedient den echten {@link SetupWizardPanel} wie ein Mensch über seine Knöpfe und Felder (auf dem EDT, mit
 * synchronen Aktionen); öffentlich, damit auch die Erststart-Tests im Paket {@code settings} ihn nutzen.
 */
public final class SetupWizardDriver {

    private SetupWizardDriver() {
    }

    /** KeePass koppeln, KI-Dienst, Modelle, Confluence ohne Zertifikat, Abschlussprüfung; endet im Schritt 6. */
    public static void throughBasicSetup(SetupWizardPanel wizard) {
        wizard.pairButton().doClick();
        expectDone(wizard, SetupWizardPanel.STEP_KEEPASS);
        wizard.nextButton().doClick();
        expectDone(wizard, SetupWizardPanel.STEP_SERVICE);
        wizard.nextButton().doClick();
        wizard.chatModelChoice().setSelectedItem("chat-model");
        wizard.embeddingModelChoice().setSelectedItem("embedding-model");
        expectDone(wizard, SetupWizardPanel.STEP_MODELS);
        assertEquals("768", wizard.embeddingDimensionField().getText());
        wizard.nextButton().doClick();
        wizard.withoutCertificateBox().doClick();
        wizard.confluenceTestButton().doClick();
        expectDone(wizard, SetupWizardPanel.STEP_CONFLUENCE);
        wizard.nextButton().doClick();
        expectDone(wizard, SetupWizardPanel.STEP_FINISH);
        assertTrue("„Weiter“ zum optionalen Schritt", wizard.nextButton().isVisible());
        wizard.nextButton().doClick();
        assertEquals(SetupWizardPanel.STEP_SPEECH, wizard.step());
    }

    private static void expectDone(SetupWizardPanel wizard, int step) {
        assertEquals(step, wizard.step());
        assertTrue("Schritt " + step + " nicht erledigt: " + wizard.errorOf(step), wizard.isDone(step));
    }

    public static int step(SetupWizardPanel wizard) {
        return wizard.step();
    }

    public static boolean isSpeechStep(SetupWizardPanel wizard) {
        return wizard.step() == SetupWizardPanel.STEP_SPEECH;
    }

    public static JComboBox<String> speechModel(SetupWizardPanel wizard) {
        return wizard.speechModelChoice();
    }

    /** Tick the chosen speech input model and press „Ausgewählte installieren“. */
    public static void install(SetupWizardPanel wizard) {
        if (!wizard.speechCheckBox().isSelected()) {
            wizard.speechCheckBox().doClick();
        }
        wizard.speechInstallButton().doClick();
    }

    public static void finish(SetupWizardPanel wizard) {
        assertTrue("„Fertig“ ist frei", wizard.finishButton().isEnabled());
        wizard.finishButton().doClick();
    }

    public static void skip(SetupWizardPanel wizard) {
        wizard.skipButton().doClick();
    }
}
