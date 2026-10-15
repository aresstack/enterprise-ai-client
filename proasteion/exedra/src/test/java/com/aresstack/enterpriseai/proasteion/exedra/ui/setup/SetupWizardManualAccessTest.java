package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.control.ComicComboBoxUI;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Zugang „Manuell“ im Einrichtungsassistenten (#134): KI-Dienst und Confluence ohne KeePass, API-Key nur für die
 * Sitzung oder ausdrücklich geschützt gespeichert, Benutzername der Quelle vorgemerkt, Passwort bzw. Token nie; alle
 * Auswahlen im Comic-Look.
 */
public class SetupWizardManualAccessTest {

    private static final String KEY = "sk-manual-test";

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private SetupWizardPanel open(SettingsForm initial) {
        return new SetupWizardRun(initial, settings, setup, ComicPalette.defaultPalette(), completed -> {
        }).wizard();
    }

    /** Mit Modellen, damit die Abschlussprüfung des Ersatzes besteht. */
    private static SettingsForm defaults() {
        return SettingsMapper.firstStartDefaults().toBuilder().chatModel("chat-model")
                .embeddingModel("embedding-model").embeddingDimension("768").build();
    }

    private SetupWizardPanel open() {
        return open(defaults());
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    /** „Manuell“ wählen, bevor der Schritt betreten wird: so liest der Assistent KeePass gar nicht erst. */
    private static void manualService(SetupWizardPanel wizard) {
        wizard.serviceAccessChoice().setSelectedItem(SetupWizardPanel.MODE_MANUAL);
        wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
        wizard.manualServiceUrlField().setText("https://ai.example.invalid/v1");
        wizard.manualServiceKeyField().setText(KEY);
    }

    @Test
    public void bothStepsOfferKeePassAndManualLikeTheSettings() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            for (JComboBox<String> access : Arrays.asList(wizard.serviceAccessChoice(),
                    wizard.confluenceAccessChoice())) {
                assertEquals(2, access.getItemCount());
                assertEquals("KeePass", access.getItemAt(0));
                assertEquals("Manuell", access.getItemAt(1));
                assertEquals("Standard bleibt KeePass", "KeePass", access.getSelectedItem());
            }
        });
    }

    @Test
    public void everyWizardComboUsesTheComicLookAndModelChoicesStayTypable() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            for (JComboBox<String> combo : Arrays.asList(wizard.serviceAccessChoice(), wizard.serviceEntryChoice(),
                    wizard.chatModelChoice(), wizard.embeddingModelChoice(), wizard.speechOutputModelChoice(),
                    wizard.visionModelChoice(), wizard.rerankModelChoice(), wizard.confluenceAccessChoice(),
                    wizard.confluenceEntryChoice(), wizard.speechModelChoice())) {
                assertTrue(combo.getUI().getClass().getSimpleName(), combo.getUI() instanceof ComicComboBoxUI);
            }
            for (JComboBox<String> editable : Arrays.asList(wizard.serviceEntryChoice(), wizard.chatModelChoice(),
                    wizard.embeddingModelChoice(), wizard.rerankModelChoice(), wizard.confluenceEntryChoice())) {
                assertTrue(editable.isEditable());
                editable.getEditor().setItem("frei-getippt");
                assertEquals("frei-getippt", editable.getEditor().getItem());
            }
            assertFalse(wizard.serviceAccessChoice().isEditable());
        });
    }

    @Test
    public void manualServiceTestsWithTheEnteredKeyAndStoresItOnlyForTheSession() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            assertFalse("KeePass-Eintrag ausgeblendet", wizard.serviceKeePassShown());
            assertTrue(wizard.serviceManualShown());
            assertFalse("„API-Key speichern“ ist anfangs aus", wizard.saveApiKeyBox().isSelected());
            int before = settings.connectionChecks;
            wizard.serviceTestButton().doClick();
            assertEquals(before + 1, settings.connectionChecks);
            assertTrue(wizard.serviceStatusText(), wizard.isDone(SetupWizardPanel.STEP_SERVICE));
            assertEquals("Key in der Sitzung, ohne Benutzer", "|" + KEY,
                    setup.session.get(SetupWizardPanel.MANUAL_SERVICE_REF));
            assertEquals("das Feld behält den Key nicht", 0, wizard.manualServiceKeyField().getPassword().length);

            SettingsForm form = wizard.form();
            assertEquals("ask:KI-Dienst", form.chatApiKeyRef());
            assertEquals("https://ai.example.invalid/v1", form.chatBaseUrl());
            assertFalse("kein Secret im Entwurf", form.toString().contains(KEY));
            assertFalse("kein Secret in den Properties", SettingsMapper.toProperties(form).toString().contains(KEY));
        });
    }

    @Test
    public void manualOnlySetupNeedsNoKeePassAndKeepsTheKeyOnlyWhenAsked() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            wizard.serviceTestButton().doClick();
            assertEquals("kein KeePass-Zugriff für den manuellen Zugang", 0, settings.listCalls);

            wizard.jumpTo(SetupWizardPanel.STEP_FINISH);
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_FINISH));
            assertEquals("ohne Häkchen wird gezielt entfernt, nicht gespeichert",
                    Collections.singletonList("ask:KI-Dienst=false"), setup.storeCalls);
            assertTrue(setup.storedKeys.isEmpty());
        });
    }

    @Test
    public void checkedSaveStoresTheKeyAfterThePassedFinalCheck() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            wizard.saveApiKeyBox().doClick();
            assertTrue(wizard.saveApiKeyNoteText(), wizard.saveApiKeyNoteText().contains("Abschlussprüfung"));
            wizard.serviceTestButton().doClick();
            assertTrue("vor dem Abschluss nichts gespeichert", setup.storedKeys.isEmpty());
            wizard.jumpTo(SetupWizardPanel.STEP_FINISH);
            assertEquals(KEY, setup.storedKeys.get("ask:KI-Dienst"));
        });
    }

    @Test
    public void failedFinalCheckStoresNothing() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            wizard.saveApiKeyBox().doClick();
            wizard.serviceTestButton().doClick();
            wizard.chatModelChoice().getEditor().setItem("");
            wizard.chatModelChoice().setSelectedItem("");
            wizard.jumpTo(SetupWizardPanel.STEP_FINISH);
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_FINISH));
            assertTrue(setup.storeCalls.isEmpty());
            assertTrue(setup.storedKeys.isEmpty());
        });
    }

    @Test
    public void storageUnavailableDisablesTheCheckboxInsteadOfSavingInPlainText() throws Exception {
        setup.canStore = false;
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            assertFalse(wizard.saveApiKeyBox().isEnabled());
            assertFalse(wizard.saveApiKeyBox().isSelected());
            assertEquals("Hier nicht möglich.", wizard.saveApiKeyNoteText());
        });
    }

    @Test
    public void rejectedKeyIsForgottenAndSwitchingBackToKeePassLeavesNothingBehind() throws Exception {
        settings.connectionFailures.add(Collections.singletonList(
                ConnectionCheckStep.failed("API-Key", "HTTP 401: abgelehnt")));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            wizard.serviceTestButton().doClick();
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
            assertNull("ein abgelehnter Key bleibt nicht in der Sitzung",
                    setup.session.get(SetupWizardPanel.MANUAL_SERVICE_REF));

            wizard.manualServiceKeyField().setText(KEY);
            wizard.serviceTestButton().doClick();
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_SERVICE));

            wizard.serviceAccessChoice().setSelectedItem(SetupWizardPanel.MODE_KEEPASS);
            assertNull("Wechsel zu KeePass vergisst den manuellen Key",
                    setup.session.get(SetupWizardPanel.MANUAL_SERVICE_REF));
            assertTrue(wizard.serviceKeePassShown());
            assertEquals("keepass:Enterprise AI API", wizard.form().chatApiKeyRef());
            assertEquals("die manuelle URL überschreibt den KeePass-Eintrag nicht", "",
                    wizard.form().chatBaseUrl());
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_SERVICE));
        });
    }

    @Test
    public void manualConfluenceTestsWithoutKeePassAndRemembersOnlyTheUserName() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            manualService(wizard);
            wizard.serviceTestButton().doClick();
            wizard.confluenceAccessChoice().setSelectedItem(SetupWizardPanel.MODE_MANUAL);
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertFalse(wizard.confluenceKeePassShown());
            assertTrue(wizard.confluenceFieldsShown());
            assertTrue(wizard.rememberUserBox().isVisible());
            wizard.confluenceUrlField().setText("https://wiki.example.invalid");
            wizard.confluenceUserField().setText("jdoe");
            wizard.confluencePasswordField().setText("token-123");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();

            assertEquals(Collections.singletonList("https://wiki.example.invalid|jdoe|token-123|"),
                    setup.confluenceTests);
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertEquals("jdoe|token-123", setup.session.get("ask:Confluence (wiki.example.invalid)"));
            assertEquals(0, wizard.confluencePasswordField().getPassword().length);
            assertEquals("kein KeePass gelesen", 0, settings.listCalls);

            wizard.jumpTo(SetupWizardPanel.STEP_FINISH);
            ConfluenceSetup chosen = setup.confluences.get(setup.confluences.size() - 1);
            assertNotNull(chosen);
            assertTrue(chosen.isManual());
            assertEquals("ask:Confluence (wiki.example.invalid)", chosen.credentialRef());
            assertEquals("jdoe", chosen.rememberedUser());
            assertEquals("", chosen.entryTitle());
            assertFalse(chosen.toString().contains("token-123"));
        });
    }

    @Test
    public void failedManualConfluenceTestForgetsTheToken() throws Exception {
        setup.confluenceFailure = "HTTP 401";
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.confluenceAccessChoice().setSelectedItem(SetupWizardPanel.MODE_MANUAL);
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.confluenceUrlField().setText("https://wiki.example.invalid");
            wizard.confluencePasswordField().setText("wrong");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertTrue(setup.session.isEmpty());
            assertEquals("HTTP 401", wizard.errorOf(SetupWizardPanel.STEP_CONFLUENCE));
        });
    }

    @Test
    public void uncheckedRememberUserStoresNoUserName() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.confluenceAccessChoice().setSelectedItem(SetupWizardPanel.MODE_MANUAL);
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.confluenceUrlField().setText("https://wiki.example.invalid");
            wizard.confluenceUserField().setText("jdoe");
            wizard.confluencePasswordField().setText("token-123");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            wizard.rememberUserBox().doClick();
            wizard.jumpTo(SetupWizardPanel.STEP_FINISH);
            ConfluenceSetup chosen = setup.confluences.get(setup.confluences.size() - 1);
            assertEquals("", chosen.rememberedUser());
        });
    }

    @Test
    public void mixedKeePassServiceAndManualConfluence() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            assertTrue("KeePass-Eintrag gelesen und getestet", wizard.isDone(SetupWizardPanel.STEP_SERVICE));
            wizard.confluenceAccessChoice().setSelectedItem(SetupWizardPanel.MODE_MANUAL);
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.confluenceUrlField().setText("https://wiki.example.invalid");
            wizard.confluencePasswordField().setText("token-123");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            wizard.jumpTo(SetupWizardPanel.STEP_FINISH);
            SettingsForm form = setup.finished.get(setup.finished.size() - 1);
            assertEquals("keepass:Enterprise AI API", form.chatApiKeyRef());
            assertTrue("KeePass wird für den KI-Dienst gebraucht", form.keePassEnabled());
            assertTrue(setup.confluences.get(setup.confluences.size() - 1).isManual());
            assertTrue("KeePass-Zugang: kein Speichern eines manuellen Keys", setup.storeCalls.isEmpty());
        });
    }

    @Test
    public void reopenedWizardShowsTheStoredManualState() throws Exception {
        setup.storedKeys.put("ask:KI-Dienst", KEY);
        onEdt(() -> {
            SetupWizardPanel wizard = open(defaults().toBuilder()
                    .chatApiKeyRef("ask:KI-Dienst").chatBaseUrl("https://ai.example.invalid/v1").build());
            assertEquals("Manuell", wizard.serviceAccessChoice().getSelectedItem());
            assertEquals("https://ai.example.invalid/v1", wizard.manualServiceUrlField().getText());
            assertTrue("gespeicherter Key: Häkchen spiegelt den Zustand", wizard.saveApiKeyBox().isSelected());
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            wizard.serviceTestButton().doClick();
            assertTrue("gespeicherter Key genügt ohne neue Eingabe", wizard.isDone(SetupWizardPanel.STEP_SERVICE));
        });
    }
}
