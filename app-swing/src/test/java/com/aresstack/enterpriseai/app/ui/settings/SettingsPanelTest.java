package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Der Dialog headless: Felder fließen ins Formular, Probleme werden gezeigt, Speichern geht über die Aktionen. */
public class SettingsPanelTest {

    private final ComicPalette palette = ComicPalette.defaultPalette();

    /** Aktionen mit vorgegebenen Antworten; merken sich, was gespeichert wurde. */
    private static final class ScriptedActions implements SettingsDialogActions {
        List<String> problems = Collections.emptyList();
        IOException failure;
        final List<SettingsForm> saved = new ArrayList<SettingsForm>();
        final List<String> checkedRefs = new ArrayList<String>();
        SecretCheckResult checkResult = SecretCheckResult.ok("gefunden");

        @Override
        public List<String> validate(SettingsForm form) {
            return problems;
        }

        @Override
        public void save(SettingsForm form) throws IOException {
            if (failure != null) {
                throw failure;
            }
            saved.add(form);
        }

        @Override
        public void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult) {
            checkedRefs.add(secretRef);
            onResult.accept(checkResult);
        }
    }

    private static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }

    private static SettingsForm sample() {
        return SettingsForm.builder()
                .chatBaseUrl("http://127.0.0.1:9/v1").chatModel("test-chat").chatApiKeyRef("keepass:Enterprise AI API")
                .embeddingModel("test-embedding").embeddingDimension("8")
                .addSource(SourceForm.builder("wiki", SourceForm.TYPE_MEDIAWIKI).url("http://127.0.0.1:9/w/api.php")
                        .startPoints("Hauptseite").build())
                .keePassEnabled(false).proxyMode(SettingsForm.PROXY_NONE).build();
    }

    @Test
    public void fieldsFlowIntoTheFormAndBack() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                assertEquals("http://127.0.0.1:9/v1", panel.serviceTab().chatBaseUrl().getText());
                assertEquals(1, panel.knowledgeTab().sources().sources().size());
                assertFalse(panel.securityTab().enabled().isSelected());
                assertEquals(SettingsForm.PROXY_NONE, panel.systemTab().proxyMode().getSelectedItem());

                panel.serviceTab().chatModel().setText("  anderes-modell ");
                panel.systemTab().windowTitle().setText("Mein Client");
                panel.securityTab().enabled().setSelected(true);
                panel.securityTab().port().setText("12999");
                panel.systemTab().agentEnabled().setSelected(true);
                panel.systemTab().agentCommand().setText("agent.cmd");
                panel.systemTab().proxyMode().setSelectedItem(SettingsForm.PROXY_AUTO);
                panel.systemTab().pacUrl().setText("file:///C:/wpad.dat");
                panel.systemTab().useWindowsCertificateStore().setSelected(false);
                panel.systemTab().caCertificatesFile().setText("C:/ca.pem");
                SettingsForm form = panel.toForm();
                assertEquals("anderes-modell", form.chatModel());
                assertEquals("Mein Client", form.windowTitle());
                assertTrue(form.keePassEnabled());
                assertEquals("12999", form.keePassPort());
                assertTrue(form.agentEnabled());
                assertEquals("agent.cmd", form.agentCommand());
                assertEquals(SettingsForm.PROXY_AUTO, form.proxyMode());
                assertEquals("file:///C:/wpad.dat", form.pacUrl());
                assertEquals(SettingsForm.PAC_WINDOWS_SETTINGS, form.pacDiscovery());
                assertFalse(form.useWindowsCertificateStore());
                assertEquals("C:/ca.pem", form.caCertificatesFile());
                assertEquals("http://127.0.0.1:9/w/api.php", form.sources().get(0).url());

                panel.setForm(sample());
                assertEquals("test-chat", panel.serviceTab().chatModel().getText());
            }
        });
    }

    @Test
    public void sourcesCanBeAddedEditedAndRemoved() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(SettingsForm.builder().build(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                SourcesEditor editor = panel.knowledgeTab().sources();
                editor.addWikiButton().doClick();
                assertEquals("wiki", editor.idField().getText());
                editor.urlField().setText("http://127.0.0.1:9/w/api.php");
                editor.startPointsField().setText("Hauptseite");
                editor.addConfluenceButton().doClick();
                assertEquals("confluence", editor.idField().getText());
                editor.urlField().setText("http://127.0.0.1:9/confluence");
                editor.addWikiButton().doClick();
                assertEquals("wiki2", editor.idField().getText());

                List<SourceForm> sources = panel.toForm().sources();
                assertEquals(3, sources.size());
                assertEquals("wiki", sources.get(0).id());
                assertEquals("http://127.0.0.1:9/w/api.php", sources.get(0).url());
                assertEquals("Hauptseite", sources.get(0).startPoints());
                assertTrue(sources.get(1).isConfluence());
                assertEquals("http://127.0.0.1:9/confluence", sources.get(1).url());

                editor.sourceList().setSelectedIndex(1);
                editor.removeButton().doClick();
                sources = panel.toForm().sources();
                assertEquals(2, sources.size());
                assertEquals("wiki", sources.get(0).id());
                assertEquals("wiki2", sources.get(1).id());
            }
        });
    }

    @Test
    public void problemsAreShownAndTheMatchingTabSelected() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        actions.problems = Arrays.asList("KeePassRPC-Port (security.keepass.port): keine ganze Zahl",
                "Chat-Modell (chat.model): fehlt (Pflichtangabe)");
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                assertEquals("", panel.problemsText());
                assertTrue(panel.tabButton(0).isSelected());
                assertFalse(panel.save());
                assertTrue(panel.problemsText(), panel.problemsText().contains("security.keepass.port"));
                assertTrue(panel.problemsText(), panel.problemsText().contains("chat.model"));
                assertTrue("Reiter KeePass gewählt", panel.tabButton(2).isSelected());
                assertTrue(actions.saved.isEmpty());

                actions.problems = Collections.singletonList("Quelle „wiki“, API-URL (source.wiki.apiUrl): keine gültige URL");
                assertFalse(panel.save());
                assertTrue("Reiter Wissensbasis gewählt", panel.tabButton(1).isSelected());
            }
        });
    }

    @Test
    public void initialProblemsAreShownBeforeAnyEdit() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(),
                        Collections.singletonList("Basis-URL des KI-Dienstes (chat.baseUrl): keine gültige URL"),
                        SettingsPanel.Mode.EDIT, actions, palette);
                assertTrue(panel.problemsText().contains("chat.baseUrl"));
            }
        });
    }

    @Test
    public void saveCallsTheActionsAndNotifies() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        final List<SettingsForm> notified = new ArrayList<SettingsForm>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                panel.setOnSaved(new Consumer<SettingsForm>() {
                    @Override
                    public void accept(SettingsForm form) {
                        notified.add(form);
                    }
                });
                panel.serviceTab().chatModel().setText("gespeichert");
                panel.saveButton().doClick();
                assertEquals(1, actions.saved.size());
                assertEquals("gespeichert", actions.saved.get(0).chatModel());
                assertEquals(1, notified.size());
                assertSame(actions.saved.get(0), notified.get(0));
                assertEquals("", panel.problemsText());
            }
        });
    }

    @Test
    public void ioFailureWhileSavingBecomesAProblemLine() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        actions.failure = new IOException("Platte voll");
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                assertFalse(panel.save());
                assertTrue(panel.problemsText(), panel.problemsText().contains("Speichern fehlgeschlagen"));
                assertTrue(panel.problemsText(), panel.problemsText().contains("Platte voll"));
            }
        });
    }

    @Test
    public void cancelNotifiesAndFirstStartLabelsItAsQuit() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        final List<String> events = new ArrayList<String>();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel first = new SettingsPanel(SettingsForm.builder().build(),
                        Collections.<String>emptyList(), SettingsPanel.Mode.FIRST_START, actions, palette);
                assertEquals(SettingsPanel.QUIT_LABEL, first.cancelButton().getText());
                assertEquals(SettingsPanel.Mode.FIRST_START, first.mode());
                first.setOnCancel(new Runnable() {
                    @Override
                    public void run() {
                        events.add("cancel");
                    }
                });
                first.cancelButton().doClick();
                assertEquals("[cancel]", events.toString());
                assertTrue(actions.saved.isEmpty());

                SettingsPanel edit = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                assertEquals(SettingsPanel.CANCEL_LABEL, edit.cancelButton().getText());
                assertEquals(SettingsPanel.SAVE_LABEL, edit.saveButton().getText());
            }
        });
    }

    @Test
    public void secretCheckUsesTheCurrentDraftAndShowsTheResult() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                panel.serviceTab().chatApiKeyRef().setText("keepass:Mein Eintrag");
                panel.serviceTab().secretCheck().button().doClick();
                assertEquals("[keepass:Mein Eintrag]", actions.checkedRefs.toString());
                assertEquals("gefunden", panel.serviceTab().secretCheck().resultText());
                assertTrue(panel.serviceTab().secretCheck().button().isEnabled());

                actions.checkResult = SecretCheckResult.failed("nicht erreichbar");
                panel.securityTab().secretCheck().button().doClick();
                assertEquals("nicht erreichbar", panel.securityTab().secretCheck().resultText());
                assertEquals(2, actions.checkedRefs.size());
            }
        });
    }

    @Test
    public void tabsSwitchTheVisibleCard() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                assertEquals(4, SettingsPanel.tabCount());
                for (int i = 0; i < SettingsPanel.tabCount(); i++) {
                    panel.tabButton(i).doClick();
                    assertTrue(panel.tabButton(i).isSelected());
                    assertEquals(SettingsPanel.tabLabel(i), panel.tabButton(i).getText());
                }
                assertNull(panel.problemsText().isEmpty() ? null : panel.problemsText());
            }
        });
    }
}
