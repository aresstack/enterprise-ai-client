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
        final List<SettingsForm> connectionForms = new ArrayList<SettingsForm>();
        List<ConnectionCheckStep> connectionSteps = Arrays.asList(
                ConnectionCheckStep.ok("Proxy-Route", "direkt (NONE)"),
                ConnectionCheckStep.ok("GET /models", "HTTP 200: 1 Modell(e)"));
        boolean connectionSuccess = true;

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

        @Override
        public void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
            connectionForms.add(form);
            for (ConnectionCheckStep step : connectionSteps) {
                listener.onStep(step);
            }
            listener.onFinished(connectionSuccess);
        }

        final List<String> networkCalls = new ArrayList<String>();

        @Override
        public void resolveProxy(SettingsForm form, NetworkLogListener listener) {
            networkCalls.add("resolve " + form.proxyMode());
            listener.line("Resolving " + form.testUrl() + " ...");
            listener.line("Result: DIRECT (disabled)");
            listener.finished(true);
        }

        @Override
        public void checkHttps(SettingsForm form, NetworkLogListener listener) {
            networkCalls.add("https " + form.proxyMode());
            listener.line("Result: HTTP 200");
            listener.finished(true);
        }

        @Override
        public String defaultDiscoveryScript(String proxyMode) {
            return SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(proxyMode) ? "# PowerShell-Vorgabe" : "' WScript-Vorgabe";
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
                .keePassEnabled(false).proxyMode(SettingsForm.PROXY_DISABLED).build();
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
                assertEquals(1, panel.toForm().sources().size());
                assertFalse(panel.securityTab().enabled().isSelected());
                assertEquals(SettingsForm.PROXY_DISABLED, panel.systemTab().proxyMode().getSelectedItem());

                panel.serviceTab().chatModel().setText("  anderes-modell ");
                panel.systemTab().windowTitle().setText("Mein Client");
                panel.securityTab().enabled().setSelected(true);
                panel.securityTab().port().setText("12999");
                panel.systemTab().agentEnabled().setSelected(true);
                panel.systemTab().agentCommand().setText("agent.cmd");
                panel.systemTab().proxyMode().setSelectedItem(SettingsForm.PROXY_PAC_URL_MANUAL);
                panel.systemTab().discoveryScript().setText("file:///C:/wpad.dat");
                panel.systemTab().tlsWindowsCaStores().setSelected(false);
                panel.systemTab().proxyAuthMode().setSelectedItem(SettingsForm.PROXY_AUTH_BASIC);
                panel.systemTab().proxyCredentialRef().setText("keepass:Firmen-Proxy");
                panel.systemTab().userAgent().setText("Mozilla/5.0 Test");
                panel.systemTab().caCertificatesFile().setText("C:/ca.pem");
                SettingsForm form = panel.toForm();
                assertEquals("anderes-modell", form.chatModel());
                assertEquals("Mein Client", form.windowTitle());
                assertTrue(form.keePassEnabled());
                assertEquals("12999", form.keePassPort());
                assertTrue(form.agentEnabled());
                assertEquals("agent.cmd", form.agentCommand());
                assertEquals(SettingsForm.PROXY_PAC_URL_MANUAL, form.proxyMode());
                assertEquals("file:///C:/wpad.dat", form.pacUrl());
                assertTrue(form.tlsJvmDefault());
                assertTrue(form.tlsWindowsRoot());
                assertFalse(form.tlsWindowsCaStores());
                assertEquals(SettingsForm.PROXY_AUTH_BASIC, form.proxyAuthMode());
                assertEquals("keepass:Firmen-Proxy", form.proxyCredentialRef());
                assertEquals("Mozilla/5.0 Test", form.userAgent());
                assertEquals("C:/ca.pem", form.caCertificatesFile());
                assertEquals("http://127.0.0.1:9/w/api.php", form.sources().get(0).url());

                panel.setForm(sample());
                assertEquals("test-chat", panel.serviceTab().chatModel().getText());
            }
        });
    }

    @Test
    public void sourcesPassThroughTheDialogUnchanged() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SourceForm wiki = SourceForm.builder("wiki", SourceForm.TYPE_MEDIAWIKI)
                        .url("http://127.0.0.1:9/w/api.php").startPoints("Hauptseite").build();
                SourceForm confluence = SourceForm.builder("confluence", SourceForm.TYPE_CONFLUENCE)
                        .url("http://127.0.0.1:9/confluence").enabled(false).build();
                SettingsPanel panel = new SettingsPanel(SettingsForm.builder().sources(Arrays.asList(wiki, confluence))
                        .build(), Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions, palette);
                List<SourceForm> sources = panel.toForm().sources();
                assertEquals(2, sources.size());
                assertEquals("http://127.0.0.1:9/w/api.php", sources.get(0).url());
                assertEquals("Hauptseite", sources.get(0).startPoints());
                assertTrue(sources.get(0).enabled());
                assertFalse(sources.get(1).enabled());
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
                assertTrue("Reiter KeePass gewählt", panel.tabButton(1).isSelected());
                assertTrue(actions.saved.isEmpty());

                actions.problems = Collections.singletonList("Quelle „wiki“, API-URL (source.wiki.apiUrl): keine gültige URL");
                assertFalse(panel.save());
                assertTrue("Quellen haben keinen Reiter: der erste wird gewählt", panel.tabButton(0).isSelected());
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
    public void proxyModesAreExactlyTheLibraryModesAndTheScriptFieldIsRememberedPerMode() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                SystemTab tab = panel.systemTab();
                List<String> modes = new ArrayList<String>();
                for (int i = 0; i < tab.proxyMode().getItemCount(); i++) {
                    modes.add(String.valueOf(tab.proxyMode().getItemAt(i)));
                }
                List<String> library = new ArrayList<String>();
                for (com.aresstack.winproxy.ProxyMode mode : com.aresstack.winproxy.ProxyMode.values()) {
                    if (!mode.name().endsWith("_LEGACY")) {
                        library.add(mode.name());
                    }
                }
                Collections.sort(library);
                List<String> sorted = new ArrayList<String>(modes);
                Collections.sort(sorted);
                assertEquals(library, sorted);

                tab.proxyMode().setSelectedItem(SettingsForm.PROXY_PAC_URL_POWERSHELL);
                assertEquals("# PowerShell-Vorgabe", tab.discoveryScript().getText());
                tab.discoveryScript().setText("Write-Output 'http://wpad.intern.example/wpad.dat'");
                tab.proxyMode().setSelectedItem(SettingsForm.PROXY_PAC_URL_WSCRIPT);
                assertEquals("' WScript-Vorgabe", tab.discoveryScript().getText());
                tab.proxyMode().setSelectedItem(SettingsForm.PROXY_PAC_URL_POWERSHELL);
                assertEquals("Write-Output 'http://wpad.intern.example/wpad.dat'", tab.discoveryScript().getText());
                assertEquals("Write-Output 'http://wpad.intern.example/wpad.dat'",
                        panel.toForm().pacDiscoveryScript());

                tab.proxyMode().setSelectedItem(SettingsForm.PROXY_DISABLED);
                tab.resolveButton().doClick();
                tab.httpsButton().doClick();
                assertEquals("[resolve DISABLED, https DISABLED]", actions.networkCalls.toString());
                assertTrue(tab.logText(), tab.logText().contains("Result: DIRECT (disabled)"));
                assertTrue(tab.logText(), tab.logText().contains("Result: HTTP 200"));
                assertTrue(tab.resolveButton().isEnabled());
            }
        });
    }

    @Test
    public void aDefaultScriptIsStoredAsEmptySoTheLibraryDefaultApplies() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                panel.systemTab().proxyMode().setSelectedItem(SettingsForm.PROXY_PAC_URL_POWERSHELL);
                assertEquals("", panel.toForm().pacDiscoveryScript());
            }
        });
    }

    @Test
    public void connectionCheckUsesTheCurrentDraftAndShowsEveryStepWithASummary() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                ConnectionCheckRow row = panel.serviceTab().connectionCheck();
                panel.systemTab().proxyMode().setSelectedItem(SettingsForm.PROXY_MANUAL);
                row.button().doClick();
                assertEquals(1, actions.connectionForms.size());
                assertEquals(SettingsForm.PROXY_MANUAL, actions.connectionForms.get(0).proxyMode());
                assertEquals(2, row.steps().size());
                assertEquals("GET /models", row.steps().get(1).title());
                assertEquals(ConnectionCheckRow.SUCCESS_LABEL, row.summaryText());
                assertTrue(row.button().isEnabled());

                actions.connectionSteps = Arrays.asList(ConnectionCheckStep.ok("Proxy-Route", "direkt"),
                        ConnectionCheckStep.warning("GET /models", "HTTP 200, Modell fehlt"));
                row.button().doClick();
                assertEquals(ConnectionCheckRow.SUCCESS_WITH_NOTES_LABEL, row.summaryText());

                actions.connectionSteps = Arrays.asList(
                        ConnectionCheckStep.failed("Namensauflösung", "UnknownHostException"));
                actions.connectionSuccess = false;
                row.button().doClick();
                assertEquals(1, row.steps().size());
                assertEquals(ConnectionCheckRow.FAILURE_LABEL, row.summaryText());
                assertTrue(row.button().isEnabled());
                assertEquals(3, actions.connectionForms.size());
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
                assertEquals(3, SettingsPanel.tabCount());
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
