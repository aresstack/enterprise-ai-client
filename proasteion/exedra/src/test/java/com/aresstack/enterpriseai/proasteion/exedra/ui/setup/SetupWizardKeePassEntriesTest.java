package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordEntry;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * KeePass-Einträge im Einrichtungsassistenten: Ist KeePass längst gekoppelt und springt die Schrittleiste an
 * „Mit KeePass verbinden“ vorbei, lädt der Assistent die Einträge selbst nach. Ein vollständiger Confluence-Eintrag
 * verlangt keine Eingabe (nur Status, keine Secrets), die Felder kommen nur bei neuem oder unvollständigem Eintrag
 * oder nach „Zugangsdaten ändern“. Übersprungenes Confluence blockiert den Abschluss nicht.
 */
public class SetupWizardKeePassEntriesTest {

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private SetupWizardPanel open() {
        return new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                ComicPalette.defaultPalette(), completed -> {
                }).wizard();
    }

    private static List<String> items(JComboBox<String> combo) {
        List<String> items = new ArrayList<String>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            items.add(combo.getItemAt(i));
        }
        return items;
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    @Test
    public void pairedKeePassAndDirectJumpToConfluenceLoadsTheFullList() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertEquals(SetupWizardPanel.STEP_CONFLUENCE, wizard.step());
            assertEquals("Einträge einmal nachgeladen", 1, settings.listCalls);
            assertEquals(Arrays.asList("Enterprise AI API", "Confluence"), items(wizard.confluenceEntryChoice()));
            assertEquals("Enterprise AI API und Confluence auch im Schritt KI-Dienst",
                    Arrays.asList("Enterprise AI API", "Confluence"), items(wizard.serviceEntryChoice()));
            assertTrue("gelesene Einträge erledigen „KeePass“", wizard.isDone(SetupWizardPanel.STEP_KEEPASS));
            assertFalse("kein „Neuer Eintrag“: " + wizard.confluenceEntryStatusText(),
                    wizard.confluenceEntryStatusText().contains("Neuer Eintrag"));
        });
    }

    @Test
    public void existingCompleteEntryShowsOnlyItsStatusWithoutSecrets() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue("Status sichtbar", wizard.confluenceSummaryShown());
            assertFalse("keine Eingabefelder", wizard.confluenceFieldsShown());
            String summary = wizard.confluenceSummaryText();
            assertTrue(summary, summary.contains("URL vorhanden: https://wiki.example.invalid"));
            assertTrue(summary, summary.contains("Benutzername vorhanden"));
            assertTrue(summary, summary.contains("Passwort bzw. Token vorhanden"));
            assertFalse("kein Secret im Status", summary.contains("test-password"));
            assertFalse("kein Benutzername im Status", summary.contains("jdoe"));
            assertTrue(wizard.confluenceChangeButton().isVisible());
        });
    }

    @Test
    public void completeEntryIsTestedWithTheKeePassValuesWithoutTypingAnything() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertNull(wizard.errorOf(SetupWizardPanel.STEP_CONFLUENCE));
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertEquals(Collections.singletonList("Confluence|https://wiki.example.invalid|jdoe|test-password"),
                    settings.tested);
            assertEquals("unveränderter Eintrag wird nicht neu gespeichert", 0, settings.saves);
        });
    }

    @Test
    public void changeCredentialsShowsTheFields() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.confluenceChangeButton().doClick();
            assertTrue(wizard.confluenceFieldsShown());
            assertFalse(wizard.confluenceSummaryShown());
            assertEquals("https://wiki.example.invalid", wizard.confluenceUrlField().getText());
            assertEquals("Passwort bleibt in KeePass", 0, wizard.confluencePasswordField().getPassword().length);
        });
    }

    @Test
    public void entryWithoutSecretAsksOnlyForTheSecret() throws Exception {
        settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "https://wiki.example.invalid",
                Collections.<String, String>emptyMap(), null));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue(wizard.confluenceSummaryShown());
            assertTrue(wizard.confluenceSummaryText().contains("URL vorhanden"));
            assertTrue(wizard.confluenceSummaryText().contains("Passwort bzw. Token fehlt"));
            assertTrue("Passwort/Token", wizard.confluenceFieldShown(2));
            assertFalse("URL steht schon in KeePass", wizard.confluenceFieldShown(0));
            assertFalse(wizard.confluenceFieldShown(1));
        });
    }

    @Test
    public void entryWithoutUrlAsksOnlyForTheUrlAndStoresItInKeePass() throws Exception {
        settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "",
                Collections.<String, String>singletonMap("MM_Custom", "bleibt"), "test-password".toCharArray()));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue(wizard.confluenceSummaryShown());
            assertTrue(wizard.confluenceSummaryText().contains("URL fehlt"));
            assertTrue("nur die URL wird verlangt", wizard.confluenceFieldShown(0));
            assertFalse(wizard.confluenceFieldShown(1));
            assertFalse(wizard.confluenceFieldShown(2));
            wizard.confluenceUrlField().setText("https://wiki.example.invalid");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertTrue(String.valueOf(wizard.errorOf(SetupWizardPanel.STEP_CONFLUENCE)),
                    wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            PasswordEntry stored = settings.vault.get("Confluence");
            assertEquals("URL im KeePass-Eintrag", "https://wiki.example.invalid", stored.url());
            assertEquals("jdoe", stored.userName());
            assertEquals("Passwort bleibt", "test-password", new String(stored.copyPassword()));
            assertEquals("Zusatzfelder bleiben", "bleibt", stored.field("MM_Custom"));
        });
    }

    @Test
    public void newEntryNameStoresEverythingInKeePass() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.confluenceEntryChoice().setSelectedItem("Wiki neu");
            assertTrue(wizard.confluenceFieldShown(0));
            assertTrue(wizard.confluenceFieldShown(1));
            assertTrue(wizard.confluenceFieldShown(2));
            wizard.confluenceUrlField().setText("https://wiki2.example.invalid");
            wizard.confluenceUserField().setText("neu");
            wizard.confluencePasswordField().setText("neu-geheim");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            PasswordEntry stored = settings.vault.get("Wiki neu");
            assertEquals("https://wiki2.example.invalid", stored.url());
            assertEquals("neu", stored.userName());
            assertEquals("neu-geheim", new String(stored.copyPassword()));
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
        });
    }

    @Test
    public void unknownTitleIsANewEntryWithFields() throws Exception {
        settings.vault.remove("Confluence");
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue(wizard.confluenceEntryStatusText(),
                    wizard.confluenceEntryStatusText().contains("Neuer Eintrag"));
            assertTrue(wizard.confluenceFieldsShown());
        });
    }

    @Test
    public void unreachableKeePassIsNamedInsteadOfCallingTheEntryNew() throws Exception {
        settings.listFailure = "KeePass antwortet nicht";
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            String status = wizard.confluenceEntryStatusText();
            assertFalse(status, status.contains("Neuer Eintrag"));
            assertTrue(status, status.contains("KeePass antwortet nicht"));
            assertFalse(wizard.confluenceFieldsShown());
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_KEEPASS));
        });
    }

    @Test
    public void certificateFromTheEntryTestsRightAway() throws Exception {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<String, String>();
        fields.put(PasswordEntry.CERT_ALIAS, "my-cert");
        settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "https://wiki.example.invalid",
                fields, "test-password".toCharArray()));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertEquals("my-cert", wizard.certificateAlias());
            assertTrue("ohne Eingabe geprüft", wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertTrue(wizard.nextButton().isEnabled());
        });
    }

    @Test
    public void directJumpToTheServiceStepReadsTheExistingEntry() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            assertEquals("Enterprise AI API", wizard.serviceEntryChoice().getEditor().getItem());
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
        });
    }

    @Test
    public void skippedConfluenceDoesNotBlockTheFinalCheck() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.pairButton().doClick();
            wizard.nextButton().doClick();
            wizard.nextButton().doClick();
            wizard.chatModelChoice().setSelectedItem("chat-model");
            wizard.embeddingModelChoice().setSelectedItem("embedding-model");
            wizard.nextButton().doClick();
            assertEquals(SetupWizardPanel.STEP_CONFLUENCE, wizard.step());
            wizard.skipButton().doClick();
            assertEquals(SetupWizardPanel.STEP_FINISH, wizard.step());
            assertTrue("Abschluss trotz übersprungenem Confluence: " + wizard.errorOf(SetupWizardPanel.STEP_FINISH),
                    wizard.isDone(SetupWizardPanel.STEP_FINISH));
            assertEquals(Collections.<ConfluenceSetup>singletonList(null), setup.confluences);
            assertEquals(SetupWizardPanel.CONFLUENCE_SKIPPED, wizard.finishStatusText());
            assertFalse("Confluence bleibt unerledigt", wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
        });
    }
}
