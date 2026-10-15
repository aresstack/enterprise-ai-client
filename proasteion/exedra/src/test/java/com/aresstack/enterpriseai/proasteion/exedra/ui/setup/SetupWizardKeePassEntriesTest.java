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
 * verlangt keine Eingabe (nur Status, keine Secrets). The wizard never changes existing entries; new titles
 * are typed and only created. "Reload" reads KeePass again. Übersprungenes Confluence blockiert den Abschluss nicht.
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
            assertEquals(SetupWizardPanel.ENTRIES_REFRESH_HINT, wizard.confluenceEntryRefreshButton().getToolTipText());
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
    public void existingEntryOffersNoFieldsToChangeIt() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertFalse("vorhandene Einträge bearbeitet der Benutzer in KeePass", wizard.confluenceFieldsShown());
            assertEquals("", wizard.confluenceUrlField().getText());
            assertEquals(0, wizard.confluencePasswordField().getPassword().length);
        });
    }

    @Test
    public void entryWithoutSecretIsNeverCompletedByTheWizard() throws Exception {
        settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "https://wiki.example.invalid",
                Collections.<String, String>emptyMap(), null));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue(wizard.confluenceSummaryShown());
            assertTrue(wizard.confluenceSummaryText().contains("URL vorhanden"));
            assertTrue(wizard.confluenceSummaryText().contains("Passwort bzw. Token fehlt"));
            assertFalse("keine Felder zum Ergänzen", wizard.confluenceFieldsShown());
            assertTrue(wizard.confluenceEntryStatusText(), wizard.confluenceEntryStatusText().contains("in KeePass"));
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertEquals("nichts geschrieben", 0, settings.saves + settings.creates);
            assertTrue("nicht getestet", settings.tested.isEmpty());
        });
    }

    @Test
    public void entryWithoutUrlIsNeverOverwritten() throws Exception {
        settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "",
                Collections.<String, String>singletonMap("MM_Custom", "bleibt"), "test-password".toCharArray()));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue(wizard.confluenceSummaryShown());
            assertTrue(wizard.confluenceSummaryText().contains("URL fehlt"));
            assertFalse(wizard.confluenceFieldsShown());
            wizard.confluenceUrlField().setText("https://wiki.example.invalid");
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertEquals(0, settings.saves + settings.creates);
            PasswordEntry stored = settings.vault.get("Confluence");
            assertEquals("URL bleibt leer", "", stored.url());
            assertEquals("bleibt", stored.field("MM_Custom"));
        });
    }

    @Test
    public void typedNewTitleCreatesTheEntryInKeePass() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            type(wizard.confluenceEntryChoice(), "Wiki neu");
            assertTrue("Status schon beim Tippen: " + wizard.confluenceEntryStatusText(),
                    wizard.confluenceEntryStatusText().contains("Neuer Eintrag"));
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
            assertEquals("Confluence", stored.field(PasswordEntry.CATEGORY));
            assertEquals("nur angelegt", 1, settings.creates);
            assertEquals("nie geändert", 0, settings.saves);
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertTrue(items(wizard.confluenceEntryChoice()).contains("Wiki neu"));
        });
    }

    @Test
    public void typingAnExistingTitleInOtherCaseSelectsItInsteadOfCreatingOne() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            type(wizard.confluenceEntryChoice(), "Wiki neu");
            type(wizard.confluenceEntryChoice(), "  confluence ");
            assertTrue(wizard.confluenceSummaryShown());
            assertFalse(wizard.confluenceFieldsShown());
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertEquals(Collections.singletonList("Confluence|https://wiki.example.invalid|jdoe|test-password"),
                    settings.tested);
            assertEquals(0, settings.saves + settings.creates);
        });
    }

    @Test
    public void entryCreatedElsewhereAfterTheListWasReadIsNotOverwritten() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            type(wizard.confluenceEntryChoice(), "Wiki neu");
            wizard.confluenceUrlField().setText("https://wiki2.example.invalid");
            wizard.confluencePasswordField().setText("neu-geheim");
            wizard.withoutCertificateBox().doClick();
            settings.vault.put("WIKI NEU", new PasswordEntry("WIKI NEU", "anderer", "https://other.invalid",
                    Collections.<String, String>emptyMap(), "original".toCharArray()));
            wizard.confluenceTestButton().doClick();
            assertFalse(wizard.isDone(SetupWizardPanel.STEP_CONFLUENCE));
            assertTrue(String.valueOf(wizard.errorOf(SetupWizardPanel.STEP_CONFLUENCE)),
                    String.valueOf(wizard.errorOf(SetupWizardPanel.STEP_CONFLUENCE)).contains("nicht angelegt"));
            PasswordEntry original = settings.vault.get("WIKI NEU");
            assertEquals("https://other.invalid", original.url());
            assertEquals("original", new String(original.copyPassword()));
            assertNull(settings.vault.get("Wiki neu"));
            assertTrue(settings.tested.isEmpty());
        });
    }

    @Test
    public void refreshReadsKeePassAgainAndKeepsTypedInput() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            type(wizard.confluenceEntryChoice(), "Wiki neu");
            wizard.confluenceUrlField().setText("https://wiki2.example.invalid");
            settings.vault.put("Extern", new PasswordEntry("Extern", "", "https://x.invalid",
                    Collections.<String, String>emptyMap(), "x".toCharArray()));
            settings.vault.remove("Enterprise AI API");
            int before = settings.listCalls;
            wizard.confluenceEntryRefreshButton().doClick();
            assertEquals("neu gelesen", before + 1, settings.listCalls);
            assertEquals(Arrays.asList("Confluence", "Extern"), items(wizard.confluenceEntryChoice()));
            assertEquals(Arrays.asList("Confluence", "Extern"), items(wizard.serviceEntryChoice()));
            assertEquals("getippter Titel bleibt", "Wiki neu", wizard.confluenceEntryChoice().getEditor().getItem());
            assertEquals("Eingabe bleibt", "https://wiki2.example.invalid", wizard.confluenceUrlField().getText());
            assertTrue(wizard.confluenceFieldsShown());
        });
    }

    @Test
    public void refreshPicksUpAnEntryCompletedInKeePass() throws Exception {
        settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "",
                Collections.<String, String>emptyMap(), "test-password".toCharArray()));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            assertTrue(wizard.confluenceSummaryText().contains("URL fehlt"));
            settings.vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "https://wiki.example.invalid",
                    Collections.<String, String>emptyMap(), "test-password".toCharArray()));
            wizard.confluenceEntryRefreshButton().doClick();
            assertTrue(wizard.confluenceSummaryText(),
                    wizard.confluenceSummaryText().contains("URL vorhanden: https://wiki.example.invalid"));
        });
    }

    @Test
    public void failedRefreshBlocksCreatingInsteadOfCallingTheTitleNew() throws Exception {
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            type(wizard.confluenceEntryChoice(), "Wiki neu");
            settings.listFailure = "KeePass gesperrt";
            wizard.confluenceEntryRefreshButton().doClick();
            String status = wizard.confluenceEntryStatusText();
            assertTrue(status, status.contains("KeePass gesperrt"));
            assertFalse(status, status.contains("Neuer Eintrag"));
            assertFalse(wizard.confluenceFieldsShown());
            wizard.withoutCertificateBox().doClick();
            wizard.confluenceTestButton().doClick();
            assertEquals(0, settings.creates + settings.saves);
        });
    }

    @Test
    public void serviceStepCreatesATypedNewTitleAndNeverChangesAnExistingOne() throws Exception {
        settings.vault.put("Enterprise AI API", new PasswordEntry("Enterprise AI API", "", "",
                Collections.<String, String>emptyMap(), "alt".toCharArray()));
        onEdt(() -> {
            SetupWizardPanel wizard = open();
            wizard.jumpTo(SetupWizardPanel.STEP_SERVICE);
            assertFalse("unvollständig, aber vorhanden: keine Felder", wizard.serviceFieldsShown());
            assertTrue(wizard.serviceEntryStatusText(), wizard.serviceEntryStatusText().contains("in KeePass"));
            type(wizard.serviceEntryChoice(), "enterprise ai api");
            assertFalse(wizard.serviceFieldsShown());
            type(wizard.serviceEntryChoice(), "KI neu");
            assertTrue(wizard.serviceEntryStatusText(), wizard.serviceEntryStatusText().contains("Neuer Eintrag"));
            assertTrue(wizard.serviceFieldsShown());
            wizard.serviceUrlField().setText("https://ai2.example.invalid/v1");
            wizard.serviceKeyField().setText("neu-key");
            wizard.serviceSaveButton().doClick();
            PasswordEntry created = settings.vault.get("KI neu");
            assertEquals("https://ai2.example.invalid/v1", created.url());
            assertEquals("neu-key", new String(created.copyPassword()));
            assertEquals(1, settings.creates);
            assertEquals(0, settings.saves);
            assertEquals("", settings.vault.get("Enterprise AI API").url());
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
        });
    }

    /** Types into the combo editor like a user (document changes, no setSelectedItem, no Enter). */
    private static void type(JComboBox<String> combo, String text) {
        javax.swing.text.JTextComponent editor = (javax.swing.text.JTextComponent) combo.getEditor()
                .getEditorComponent();
        javax.swing.text.Document document = editor.getDocument();
        try {
            document.remove(0, document.getLength());
            for (int i = 0; i < text.length(); i++) {
                document.insertString(document.getLength(), String.valueOf(text.charAt(i)), null);
            }
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
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
