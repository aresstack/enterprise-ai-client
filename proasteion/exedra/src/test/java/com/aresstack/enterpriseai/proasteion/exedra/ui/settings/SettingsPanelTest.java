package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

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
        java.io.File folder;
        String offlineName;
        java.io.File offlineTarget;
        com.aresstack.enterpriseai.astu.localruntime.InstallCancellation offlineCancellation;
        LocalVoiceInstallProgress offlineProgress;

        @Override
        public java.io.File settingsFolder() {
            return folder;
        }

        @Override
        public String offlinePackageFileName() {
            return offlineName;
        }

        @Override
        public void exportOfflinePackage(SettingsForm form, java.io.File target,
                                         com.aresstack.enterpriseai.astu.localruntime.InstallCancellation cancellation,
                                         LocalVoiceInstallProgress progress) {
            offlineTarget = target;
            offlineCancellation = cancellation;
            offlineProgress = progress;
        }

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
                assertFalse(panel.securityTab().enabled().isSelected());
                assertEquals(SettingsForm.PROXY_DISABLED, panel.systemTab().proxyMode().getSelectedItem());

                panel.modelsTab().chatModel().setText("  anderes-modell ");
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

                panel.setForm(sample());
                assertEquals("test-chat", panel.modelsTab().chatModel().getText());
            }
        });
    }

    @Test
    public void openFolderButtonOnlyWithAKnownFolder() throws Exception {
        final ScriptedActions without = new ScriptedActions();
        final ScriptedActions with = new ScriptedActions();
        with.folder = new java.io.File(System.getProperty("java.io.tmpdir"));
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertFalse(new SettingsPanel(sample(), Collections.<String>emptyList(), SettingsPanel.Mode.EDIT,
                        without, palette).openFolderButton().isVisible());
                assertTrue(new SettingsPanel(sample(), Collections.<String>emptyList(), SettingsPanel.Mode.EDIT,
                        with, palette).openFolderButton().isVisible());
            }
        });
    }

    @Test
    public void offlinePackageButtonSitsRightOfTheFolderButtonAndExportsWithProgressAndCancel() throws Exception {
        final ScriptedActions without = new ScriptedActions();
        final ScriptedActions actions = new ScriptedActions();
        actions.folder = new java.io.File(System.getProperty("java.io.tmpdir"));
        actions.offlineName = "enterprise-ai-client-resources-20261012-210000.zip";
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertFalse(new SettingsPanel(sample(), Collections.<String>emptyList(), SettingsPanel.Mode.EDIT,
                        without, palette).offlinePackageButton().isVisible());
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, palette);
                javax.swing.JButton button = panel.offlinePackageButton();
                assertTrue(button.isVisible());
                assertEquals("Offline-Paket erstellen …", button.getText());
                assertEquals("Packt alle installierten lokalen KI- und Sprachmodelle in ein ZIP für eine "
                        + "Offline-Installation.", button.getToolTipText());
                java.awt.Container bar = panel.openFolderButton().getParent();
                assertSame("next to the folder button", bar, button.getParent());
                List<java.awt.Component> order = Arrays.asList(bar.getComponents());
                assertTrue(order.indexOf(button) > order.indexOf(panel.openFolderButton()));

                java.io.File target = new java.io.File(actions.folder, actions.offlineName);
                panel.exportOfflinePackage(target);
                assertSame(target, actions.offlineTarget);
                assertFalse("one export at a time", button.isEnabled());
                assertTrue(panel.offlineProgress().isVisible());
                actions.offlineProgress.file("model.onnx", 2, 5, 50, 100);
                assertEquals(50, panel.offlineProgress().bar().getValue());
                panel.offlineProgress().cancelButton().doClick();
                assertTrue(actions.offlineCancellation.isCancelled());
                actions.offlineProgress.cancelled("Erstellen abgebrochen. Es wurde kein Offline-Paket geschrieben.");
                assertFalse(panel.offlineProgress().isVisible());
                assertTrue(button.isEnabled());
                assertEquals("Erstellen abgebrochen. Es wurde kein Offline-Paket geschrieben.", panel.offlineStatus());
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
                panel.modelsTab().chatModel().setText("gespeichert");
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
                assertEquals(10, SettingsPanel.tabCount());
                for (int i = 0; i < SettingsPanel.tabCount(); i++) {
                    panel.tabButton(i).doClick();
                    assertTrue(panel.tabButton(i).isSelected());
                    assertEquals(SettingsPanel.tabLabel(i), panel.tabButton(i).getText());
                }
                assertNull(panel.problemsText().isEmpty() ? null : panel.problemsText());
            }
        });
    }

    @Test
    public void toolsTabListsEveryRegisteredToolWithAvailabilityAndOrigin() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        final List<ToolOverviewEntry> registry = new ArrayList<ToolOverviewEntry>();
        registry.add(new ToolOverviewEntry("knowledge_search", "Sucht in den Wissensquellen.",
                "Wissenswerkzeuge (KnowledgeMcpTools)", "Agent"));
        registry.add(new ToolOverviewEntry("knowledge_read", "Liest ein Dokument.",
                "Wissenswerkzeuge (KnowledgeMcpTools)", "Chat + Agent"));
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertEquals("Tools", SettingsPanel.tabLabel(SettingsPanel.TOOLS_TAB));
                assertEquals("Darstellung", SettingsPanel.tabLabel(SettingsPanel.TOOLS_TAB + 1));
                assertEquals("Technische Details", SettingsPanel.tabLabel(SettingsPanel.TECHNICAL_TAB));
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, new ToolOverview() {
                            @Override
                            public List<ToolOverviewEntry> tools() {
                                return new ArrayList<ToolOverviewEntry>(registry);
                            }
                        }, palette);
                panel.tabButton(SettingsPanel.TOOLS_TAB).doClick();
                String shown = texts(panel.toolsTab().lines());
                assertTrue(shown, shown.contains("knowledge_search") && shown.contains("knowledge_read"));
                assertTrue(shown, shown.contains("Chat + Agent") && shown.contains("Agent"));
                assertTrue(shown, shown.contains(ToolsTab.ORIGIN_PREFIX + "Wissenswerkzeuge (KnowledgeMcpTools)"));
                assertEquals(2, panel.toolsTab().lines().getComponentCount());
                // Ein neu registriertes Werkzeug erscheint beim nächsten Anzeigen ohne Änderung der Oberfläche.
                registry.add(new ToolOverviewEntry("future_tool", "Neu.", "Irgendwo", "Chat"));
                panel.tabButton(0).doClick();
                panel.tabButton(SettingsPanel.TOOLS_TAB).doClick();
                assertEquals(3, panel.toolsTab().lines().getComponentCount());
                assertTrue(texts(panel.toolsTab().lines()).contains("future_tool"));
                // Häkchen „aktiv“: Abschalten und Wiedereinschalten landen im Entwurf tools.disabled.
                assertTrue(panel.toolsTab().activeBox("knowledge_search").isSelected());
                panel.toolsTab().activeBox("knowledge_search").doClick();
                panel.toolsTab().activeBox("knowledge_read").doClick();
                assertEquals("knowledge_search,knowledge_read", panel.toForm().disabledTools());
                panel.toolsTab().activeBox("knowledge_search").doClick();
                assertEquals("knowledge_read", panel.toForm().disabledTools());
                // Gespeicherter Stand: abgeschaltet heißt ohne Häkchen; ein gerade nicht registrierter Name bleibt.
                panel.setForm(sample().toBuilder().disabledTools("knowledge_read,source_accept").build());
                assertFalse(panel.toolsTab().activeBox("knowledge_read").isSelected());
                assertTrue(panel.toolsTab().activeBox("future_tool").isSelected());
                panel.toolsTab().activeBox("future_tool").doClick();
                assertEquals("knowledge_read,source_accept,future_tool", panel.toForm().disabledTools());
            }
        });
    }

    @Test
    public void requiredToolsCannotBeSwitchedOffInTheToolsTab() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        final List<ToolOverviewEntry> registry = new ArrayList<ToolOverviewEntry>();
        registry.add(new ToolOverviewEntry("research_status", "Zustand.", "Recherche-Agent", "Recherche-Agent",
                true, true));
        registry.add(new ToolOverviewEntry("knowledge_search", "Sucht.", "Wissenswerkzeuge", "Agent"));
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample().toBuilder()
                        .disabledTools("research_status,knowledge_search").build(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, new ToolOverview() {
                            @Override
                            public List<ToolOverviewEntry> tools() {
                                return new ArrayList<ToolOverviewEntry>(registry);
                            }
                        }, palette);
                panel.tabButton(SettingsPanel.TOOLS_TAB).doClick();
                javax.swing.JCheckBox required = panel.toolsTab().activeBox("research_status");
                assertTrue("Pflichtwerkzeug immer aktiv", required.isSelected());
                assertFalse("nicht abwählbar", required.isEnabled());
                assertEquals(ToolsTab.REQUIRED_TOOLTIP, required.getToolTipText());
                assertFalse(panel.toolsTab().activeBox("knowledge_search").isSelected());
                // Ein alter Eintrag für das Pflichtwerkzeug wird nicht wieder gespeichert.
                assertEquals("knowledge_search", panel.toForm().disabledTools());
                panel.setForm(sample().toBuilder().disabledTools("research_status").build());
                assertTrue(panel.toolsTab().activeBox("research_status").isSelected());
                assertEquals("", panel.toForm().disabledTools());
            }
        });
    }

    @Test
    public void toolsTabWithoutRunningApplicationSaysSo() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(sample(), Collections.<String>emptyList(),
                        SettingsPanel.Mode.FIRST_START, actions, palette);
                panel.selectTab(SettingsPanel.TOOLS_TAB);
                assertTrue(texts(panel.toolsTab().lines()).contains("Keine Werkzeuge registriert"));
            }
        });
    }

    /** Alle Beschriftungstexte unter einer Komponente. */
    private static String texts(java.awt.Container container) {
        StringBuilder out = new StringBuilder();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof javax.swing.JLabel) {
                out.append(((javax.swing.JLabel) child).getText()).append('\n');
            } else if (child instanceof javax.swing.AbstractButton) {
                out.append(((javax.swing.AbstractButton) child).getText()).append('\n');
            }
            if (child instanceof java.awt.Container) {
                out.append(texts((java.awt.Container) child));
            }
        }
        return out.toString();
    }

    @Test
    public void indexTabSitsBeforeTechnicalDetailsAndEditsTheIndexDirectory() throws Exception {
        final ScriptedActions actions = new ScriptedActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                assertEquals("Index", SettingsPanel.tabLabel(SettingsPanel.INDEX_TAB));
                assertEquals("Sprachausgabe", SettingsPanel.tabLabel(SettingsPanel.INDEX_TAB - 1));
                assertEquals("Tools", SettingsPanel.tabLabel(SettingsPanel.INDEX_TAB + 1));
                SettingsPanel panel = new SettingsPanel(sample().toBuilder().indexDirectory("C:/Daten/index").build(),
                        Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions, palette);
                assertEquals("C:/Daten/index", panel.indexTab().indexDirectory().getText());
                assertEquals(IndexEditor.CHOOSE_LABEL, panel.indexTab().chooseButton().getText());
                panel.indexTab().indexDirectory().setText("  D:/neu  ");
                assertEquals("D:/neu", panel.toForm().indexDirectory());
                panel.indexTab().indexDirectory().setText("");
                assertEquals("", panel.toForm().indexDirectory());
            }
        });
    }
}
