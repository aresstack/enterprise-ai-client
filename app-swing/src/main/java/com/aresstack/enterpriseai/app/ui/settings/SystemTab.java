package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.function.Supplier;

/**
 * Reiter „Netzwerk & Agent“. Proxy, TLS und HTTP folgen funktional dem ProxyPanel von AskAI (Modi und Felder von
 * win-proxy-java und win-trust-java, ein Feld „PAC URL discovery script“, das je Modus das PowerShell-Skript,
 * das VBScript oder bei PAC_URL_MANUAL die PAC-Adresse hält, Test URL, „Proxy auflösen“, „HTTPS-Verbindung
 * testen“, Protokoll darunter); die Optik ist die des Dialogs. Die Proxy-Anmeldung BASIC nennt statt Benutzer und
 * Passwort einen KeePass-Eintrag, weil Secrets nicht in die Datei gehören.
 */
final class SystemTab {

    static final String RESOLVE_LABEL = "Proxy auflösen";
    static final String HTTPS_LABEL = "HTTPS-Verbindung testen";
    static final String RESET_LABEL = "Reset default";
    static final String BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0";

    private final SettingsDialogActions actions;
    private final JTextField windowTitle;
    private final JComboBox<String> proxyMode;
    private final JTextField testUrl;
    private final JTextArea discoveryScript;
    private final JTextField proxyHost;
    private final JTextField proxyPort;
    private final JTextField resolveTimeout;
    private final ComicButton resolveButton;
    private final ComicButton httpsButton;
    private final JTextArea log;
    private final JCheckBox tlsJvmDefault;
    private final JCheckBox tlsWindowsRoot;
    private final JCheckBox tlsWindowsCaStores;
    private final JTextField caCertificatesFile;
    private final JTextField userAgent;
    private final JComboBox<String> proxyAuthMode;
    private final JTextField proxyCredentialRef;
    private final JCheckBox preferIpv6;
    private final JCheckBox agentEnabled;
    private final JTextField agentCommand;
    private final JTextField agentArgs;
    private final JTextField agentTimeout;
    private final JPanel panel;

    private String activeMode;
    private String manualPacUrlText = "";
    private String powerShellScriptText = "";
    private String wScriptText = "";
    private boolean switching;

    SystemTab(final SettingsDialogActions actions, final Supplier<SettingsForm> form, ComicPalette palette) {
        this.actions = actions;
        FormRows general = new FormRows(palette);
        windowTitle = general.textField("Fenstertitel", "Titel des Hauptfensters");

        FormRows proxy = new FormRows(palette);
        proxyMode = proxy.comboBox("Mode", null, SettingsForm.proxyModes());
        testUrl = proxy.textField("Test URL", null);
        discoveryScript = proxy.textArea("PAC URL discovery script", 3, null);
        proxyHost = proxy.textField("Manual host", null);
        proxyPort = proxy.textField("Manual port", null);
        resolveTimeout = proxy.textField("Resolve timeout (ms)", null);
        resolveButton = new ComicButton(RESOLVE_LABEL, null, ComicButton.Accent.ACTION, palette);
        httpsButton = new ComicButton(HTTPS_LABEL, null, ComicButton.Accent.ACTION, palette);
        ComicButton reset = new ComicButton(RESET_LABEL, null, ComicButton.Accent.ACTION, palette);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(resolveButton);
        buttons.add(httpsButton);
        buttons.add(reset);
        proxy.component(null, buttons, null);
        log = new JTextArea(10, 28);
        log.setEditable(false);
        log.setLineWrap(true);
        proxy.style(log);
        proxy.component(null, log, null);

        FormRows tls = new FormRows(palette);
        tlsJvmDefault = tls.checkBox("Use JVM default cacerts", null);
        tlsWindowsRoot = tls.checkBox("Use Windows-ROOT store", null);
        tlsWindowsCaStores = tls.checkBox("Use Windows Root/Intermediate stores", null);
        caCertificatesFile = new JTextField(24);
        tls.style(caCertificatesFile);
        ComicButton chooseCaFile = new ComicButton("Wählen …", null, ComicButton.Accent.ACTION, palette);
        JPanel caRow = new JPanel(new BorderLayout(6, 0));
        caRow.setOpaque(false);
        caRow.add(caCertificatesFile, BorderLayout.CENTER);
        caRow.add(chooseCaFile, BorderLayout.EAST);
        tls.component("CA-Datei (optional)", caRow, "PEM- oder DER-Datei mit weiteren CA-Zertifikaten");

        FormRows http = new FormRows(palette);
        userAgent = new JTextField(24);
        http.style(userAgent);
        ComicButton browserUa = new ComicButton("Use browser UA", null, ComicButton.Accent.ACTION, palette);
        JPanel uaRow = new JPanel(new BorderLayout(6, 0));
        uaRow.setOpaque(false);
        uaRow.add(userAgent, BorderLayout.CENTER);
        uaRow.add(browserUa, BorderLayout.EAST);
        http.component("User-Agent", uaRow, null);
        proxyAuthMode = http.comboBox("Proxy auth mode", null, SettingsForm.PROXY_AUTH_NONE, SettingsForm.PROXY_AUTH_BASIC);
        proxyCredentialRef = http.textField("Proxy credentials (BASIC, KeePass entry)",
                "Titel des KeePass-Eintrags: Benutzername im Benutzerfeld, Passwort im Passwortfeld");
        preferIpv6 = http.checkBox("Prefer IPv6 (needs restart; use when IPv4 egress is broken)", null);

        FormRows agent = new FormRows(palette);
        agentEnabled = agent.checkBox("Agent-Modus aktivieren", "Reiter „Agent“ anzeigen");
        agentCommand = agent.textField("Kommando", "Programm, z. B. java");
        agentArgs = agent.textField("Argumente", "Shell-ähnlich, Anführungszeichen erlaubt, z. B. -jar \"C:/Pfad/agent.jar\"");
        agentTimeout = agent.textField("Timeout je Anfrage (Sekunden)", "Wartezeit auf Antworten des Agenten");

        panel = FormRows.column(palette,
                FormRows.plate("Allgemein", palette.getNavigationBlue(), general.panel(), palette),
                FormRows.plate("Proxy resolution", palette.getAccentOrange(), proxy.panel(), palette),
                FormRows.plate("TLS certificate trust", palette.getAccentYellow(), tls.panel(), palette),
                FormRows.plate("HTTP client & proxy authentication", palette.getAccentRed(), http.panel(), palette),
                FormRows.plate("Agent-Modus (ACP)", palette.getAgentPetrol(), agent.panel(), palette));

        activeMode = SettingsForm.PROXY_PAC_URL_POWERSHELL;
        append("PAC_URL_POWERSHELL: field contains a PowerShell discovery script.");
        append("PAC_URL_WSCRIPT: field contains a VBScript/WScript discovery script.");
        append("PAC_URL_MANUAL: field contains the PAC/WPAD URL directly.");
        append(RESOLVE_LABEL + " checks only proxy/PAC resolution (no TLS, no certificate check).");
        append(HTTPS_LABEL + " performs a real request and validates the certificate chain.");

        proxyMode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                switchScriptTextForSelectedMode();
            }
        });
        proxyAuthMode.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateProxyAuthEnabled();
            }
        });
        browserUa.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                userAgent.setText(BROWSER_USER_AGENT);
            }
        });
        chooseCaFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseCaFile();
            }
        });
        resolveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run(true, form);
            }
        });
        httpsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run(false, form);
            }
        });
        reset.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetDefault();
            }
        });
        updateProxyAuthEnabled();
    }

    private void run(final boolean resolve, Supplier<SettingsForm> form) {
        resolveButton.setEnabled(false);
        httpsButton.setEnabled(false);
        NetworkLogListener listener = new NetworkLogListener() {
            @Override
            public void line(String text) {
                append(text);
            }

            @Override
            public void finished(boolean success) {
                resolveButton.setEnabled(true);
                httpsButton.setEnabled(true);
            }
        };
        try {
            if (resolve) {
                append(RESOLVE_LABEL + " checks only proxy/PAC resolution. Use '" + HTTPS_LABEL + "' for TLS.");
                actions.resolveProxy(form.get(), listener);
            } else {
                actions.checkHttps(form.get(), listener);
            }
        } catch (RuntimeException ex) {
            append("ERROR: " + ex.getClass().getSimpleName());
            listener.finished(false);
        }
    }

    private void append(String line) {
        log.append(line + "\n");
        log.setCaretPosition(log.getDocument().getLength());
    }

    private void updateProxyAuthEnabled() {
        proxyCredentialRef.setEnabled(SettingsForm.PROXY_AUTH_BASIC.equals(proxyAuthMode.getSelectedItem()));
    }

    private void switchScriptTextForSelectedMode() {
        if (switching) {
            return;
        }
        rememberScriptText(activeMode, discoveryScript.getText());
        activeMode = selectedMode();
        switching = true;
        try {
            discoveryScript.setText(scriptTextFor(activeMode));
            discoveryScript.setCaretPosition(0);
        } finally {
            switching = false;
        }
        updateFieldVisibility();
        append("Proxy mode: " + activeMode);
        appendModeHelp(activeMode);
    }

    private void updateFieldVisibility() {
        boolean manual = SettingsForm.PROXY_MANUAL.equals(activeMode);
        proxyHost.setEnabled(manual);
        proxyPort.setEnabled(manual);
        discoveryScript.setEnabled(isScriptMode(activeMode) || SettingsForm.PROXY_PAC_URL_MANUAL.equals(activeMode));
    }

    private static boolean isScriptMode(String mode) {
        return SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(mode) || SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode);
    }

    private void rememberScriptText(String mode, String value) {
        String text = value == null ? "" : value;
        if (SettingsForm.PROXY_PAC_URL_MANUAL.equals(mode)) {
            manualPacUrlText = text.trim();
        } else if (SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode)) {
            wScriptText = text;
        } else if (SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(mode)) {
            powerShellScriptText = text;
        }
    }

    private String scriptTextFor(String mode) {
        if (SettingsForm.PROXY_PAC_URL_MANUAL.equals(mode)) {
            return manualPacUrlText;
        }
        if (SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode)) {
            return wScriptText.trim().isEmpty() ? actions.defaultDiscoveryScript(mode) : wScriptText;
        }
        if (SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(mode)) {
            return powerShellScriptText.trim().isEmpty() ? actions.defaultDiscoveryScript(mode) : powerShellScriptText;
        }
        return "";
    }

    private void appendModeHelp(String mode) {
        if (SettingsForm.PROXY_PAC_URL_MANUAL.equals(mode)) {
            append("PAC_URL_MANUAL: paste the PAC/WPAD URL into 'PAC URL discovery script'.");
        } else if (SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode)) {
            append("PAC_URL_WSCRIPT: the field contains VBScript that prints the PAC/WPAD URL.");
        } else if (SettingsForm.PROXY_PAC_URL_WINDOWS_SETTINGS.equals(mode)) {
            append("PAC_URL_WINDOWS_SETTINGS: the PAC URL is taken from Windows proxy settings.");
        } else if (SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(mode)) {
            append("PAC_URL_POWERSHELL: the field contains the PowerShell discovery script.");
        } else if (SettingsForm.PROXY_MANUAL.equals(mode)) {
            append("MANUAL_PROXY: enter proxy host and port; PAC fields are ignored.");
        }
    }

    private String selectedMode() {
        Object selected = proxyMode.getSelectedItem();
        return selected == null ? SettingsForm.PROXY_PAC_URL_POWERSHELL : selected.toString();
    }

    private void resetDefault() {
        manualPacUrlText = "";
        powerShellScriptText = "";
        wScriptText = "";
        activeMode = SettingsForm.PROXY_PAC_URL_POWERSHELL;
        switching = true;
        try {
            proxyMode.setSelectedItem(SettingsForm.PROXY_PAC_URL_POWERSHELL);
            discoveryScript.setText(scriptTextFor(activeMode));
        } finally {
            switching = false;
        }
        testUrl.setText("");
        proxyHost.setText("");
        proxyPort.setText("");
        resolveTimeout.setText("");
        tlsJvmDefault.setSelected(true);
        tlsWindowsRoot.setSelected(true);
        tlsWindowsCaStores.setSelected(true);
        userAgent.setText("");
        proxyAuthMode.setSelectedItem(SettingsForm.PROXY_AUTH_NONE);
        proxyCredentialRef.setText("");
        preferIpv6.setSelected(false);
        updateProxyAuthEnabled();
        updateFieldVisibility();
        append("Reset to win-proxy-java defaults: " + SettingsForm.PROXY_PAC_URL_POWERSHELL);
        appendModeHelp(activeMode);
    }

    private void chooseCaFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("CA-Datei wählen (PEM oder DER)");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String current = caCertificatesFile.getText().trim();
        if (!current.isEmpty()) {
            chooser.setSelectedFile(new File(current));
        }
        if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(panel)) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
            caCertificatesFile.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        windowTitle.setText(form.windowTitle());
        String mode = known(form.proxyMode());
        manualPacUrlText = form.pacUrl();
        powerShellScriptText = SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(mode) ? form.pacDiscoveryScript() : "";
        wScriptText = SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode) ? form.pacDiscoveryScript() : "";
        activeMode = mode;
        switching = true;
        try {
            proxyMode.setSelectedItem(mode);
            discoveryScript.setText(scriptTextFor(mode));
            discoveryScript.setCaretPosition(0);
        } finally {
            switching = false;
        }
        testUrl.setText(form.testUrl());
        proxyHost.setText(form.proxyHost());
        proxyPort.setText(form.proxyPort());
        resolveTimeout.setText(form.resolveTimeoutMillis());
        tlsJvmDefault.setSelected(form.tlsJvmDefault());
        tlsWindowsRoot.setSelected(form.tlsWindowsRoot());
        tlsWindowsCaStores.setSelected(form.tlsWindowsCaStores());
        caCertificatesFile.setText(form.caCertificatesFile());
        userAgent.setText(form.userAgent());
        proxyAuthMode.setSelectedItem(SettingsForm.PROXY_AUTH_BASIC.equals(form.proxyAuthMode())
                ? SettingsForm.PROXY_AUTH_BASIC : SettingsForm.PROXY_AUTH_NONE);
        proxyCredentialRef.setText(form.proxyCredentialRef());
        preferIpv6.setSelected(form.preferIpv6());
        agentEnabled.setSelected(form.agentEnabled());
        agentCommand.setText(form.agentCommand());
        agentArgs.setText(form.agentArgs());
        agentTimeout.setText(form.agentRequestTimeoutSeconds());
        updateProxyAuthEnabled();
        updateFieldVisibility();
        appendModeHelp(mode);
    }

    /** Ein Modus der Bibliothek; Unbekanntes (auch der alte POWERSHELL_ROUTE_RESOLVER_LEGACY) wird zum Standard. */
    private static String known(String value) {
        for (String option : SettingsForm.proxyModes()) {
            if (option.equals(value)) {
                return option;
            }
        }
        return SettingsForm.PROXY_PAC_URL_POWERSHELL;
    }

    void store(SettingsForm.Builder b) {
        rememberScriptText(activeMode, discoveryScript.getText());
        String mode = selectedMode();
        String script = "";
        if (SettingsForm.PROXY_PAC_URL_POWERSHELL.equals(mode) || SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode)) {
            String text = SettingsForm.PROXY_PAC_URL_WSCRIPT.equals(mode) ? wScriptText : powerShellScriptText;
            // Der Standard der Bibliothek wird nicht in die Datei kopiert: leer heißt Standard.
            script = text.trim().equals(actions.defaultDiscoveryScript(mode).trim()) ? "" : text;
        }
        b.windowTitle(windowTitle.getText())
                .proxyMode(mode)
                .pacUrl(manualPacUrlText)
                .pacDiscoveryScript(script)
                .testUrl(testUrl.getText())
                .proxyHost(proxyHost.getText()).proxyPort(proxyPort.getText())
                .resolveTimeoutMillis(resolveTimeout.getText())
                .tlsJvmDefault(tlsJvmDefault.isSelected())
                .tlsWindowsRoot(tlsWindowsRoot.isSelected())
                .tlsWindowsCaStores(tlsWindowsCaStores.isSelected())
                .caCertificatesFile(caCertificatesFile.getText())
                .userAgent(userAgent.getText())
                .proxyAuthMode(String.valueOf(proxyAuthMode.getSelectedItem()))
                .proxyCredentialRef(proxyCredentialRef.getText())
                .preferIpv6(preferIpv6.isSelected())
                .agentEnabled(agentEnabled.isSelected()).agentCommand(agentCommand.getText())
                .agentArgs(agentArgs.getText()).agentRequestTimeoutSeconds(agentTimeout.getText());
    }

    JTextField windowTitle() {
        return windowTitle;
    }

    JComboBox<String> proxyMode() {
        return proxyMode;
    }

    JTextArea discoveryScript() {
        return discoveryScript;
    }

    JTextField testUrl() {
        return testUrl;
    }

    JTextField proxyHost() {
        return proxyHost;
    }

    JTextField proxyPort() {
        return proxyPort;
    }

    JCheckBox tlsWindowsRoot() {
        return tlsWindowsRoot;
    }

    JCheckBox tlsWindowsCaStores() {
        return tlsWindowsCaStores;
    }

    JTextField caCertificatesFile() {
        return caCertificatesFile;
    }

    JTextField userAgent() {
        return userAgent;
    }

    JComboBox<String> proxyAuthMode() {
        return proxyAuthMode;
    }

    JTextField proxyCredentialRef() {
        return proxyCredentialRef;
    }

    ComicButton resolveButton() {
        return resolveButton;
    }

    ComicButton httpsButton() {
        return httpsButton;
    }

    String logText() {
        return log.getText();
    }

    JCheckBox agentEnabled() {
        return agentEnabled;
    }

    JTextField agentCommand() {
        return agentCommand;
    }
}
