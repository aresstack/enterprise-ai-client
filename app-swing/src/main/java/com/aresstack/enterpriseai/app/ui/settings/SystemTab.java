package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.function.Supplier;

/**
 * Reiter „Netzwerk & Agent“: Fenstertitel, Proxy, TLS-Vertrauen, der Verbindungstest gegen den KI-Dienst und
 * der optionale Agent-Modus über ACP.
 */
final class SystemTab {

    private final JTextField windowTitle;
    private final JComboBox<String> proxyMode;
    private final JTextField pacUrl;
    private final JComboBox<String> pacDiscovery;
    private final JTextField proxyHost;
    private final JTextField proxyPort;
    private final JTextField nonProxyHosts;
    private final JCheckBox useWindowsCertificateStore;
    private final JTextField caCertificatesFile;
    private final ComicButton chooseCaFile;
    private final ConnectionCheckRow connectionCheck;
    private final JCheckBox agentEnabled;
    private final JTextField agentCommand;
    private final JTextField agentArgs;
    private final JTextField agentTimeout;
    private final JPanel panel;

    SystemTab(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        FormRows general = new FormRows(palette);
        windowTitle = general.textField("Fenstertitel", "Titel des Hauptfensters");

        FormRows proxy = new FormRows(palette);
        proxy.note("AUTO verhält sich wie Browser und PowerShell auf diesem Rechner: Gibt es ein PAC-/WPAD-Skript, "
                + "entscheidet es je Ziel, sonst gelten die Systemeinstellungen. SYSTEM nutzt nur die "
                + "Proxy-Einstellungen des Systems, NONE verbindet immer direkt, MANUAL nutzt den angegebenen Proxy. "
                + "Loopback-Adressen gehen nie über den Proxy.");
        proxyMode = proxy.comboBox("Modus", "Proxy-Regel für alle HTTP-Zugriffe",
                SettingsForm.PROXY_AUTO, SettingsForm.PROXY_SYSTEM, SettingsForm.PROXY_NONE, SettingsForm.PROXY_MANUAL);
        pacUrl = proxy.textField("PAC-Skript (bei AUTO, optional)",
                "Adresse des PAC-Skripts (http, https oder file); leer: aus den Windows-Einstellungen");
        pacDiscovery = proxy.comboBox("PAC-Ermittlung (bei AUTO ohne Adresse)",
                "WINDOWS_SETTINGS liest die Adresse per reg.exe, POWERSHELL per PowerShell-Einzeiler",
                SettingsForm.PAC_WINDOWS_SETTINGS, SettingsForm.PAC_POWERSHELL);
        proxyHost = proxy.textField("Proxy-Host (bei MANUAL)", "Hostname oder Adresse des Proxys");
        proxyPort = proxy.textField("Proxy-Port (bei MANUAL)", "Port des Proxys");
        nonProxyHosts = proxy.textField("Ohne Proxy (optional)", "Kommagetrennte Hostmuster, z. B. *.intern.example");

        FormRows tls = new FormRows(palette);
        tls.note("Java vertraut von sich aus nur seinem eigenen Truststore. Mit dem Windows-Zertifikatspeicher gelten "
                + "zusätzlich die Stammzertifikate des Systems (Firmen-Proxy, interne CA), wie in PowerShell und "
                + "Browser; eine CA-Datei ergänzt weitere Zertifikate, auch unter Linux und macOS.");
        useWindowsCertificateStore = tls.checkBox("Windows-Zertifikatspeicher mitverwenden",
                "Unter Windows zusätzlich die Stammzertifikate aus Windows-ROOT anerkennen; sonst ohne Wirkung");
        caCertificatesFile = new JTextField(24);
        tls.style(caCertificatesFile);
        chooseCaFile = new ComicButton("Wählen …", null, ComicButton.Accent.ACTION, palette);
        JPanel caRow = new JPanel(new BorderLayout(6, 0));
        caRow.setOpaque(false);
        caRow.add(caCertificatesFile, BorderLayout.CENTER);
        caRow.add(chooseCaFile, BorderLayout.EAST);
        tls.component("CA-Datei (optional)", caRow,
                "PEM- oder DER-Datei mit weiteren CA-Zertifikaten, z. B. dem Zertifikat des Firmen-Proxys");
        chooseCaFile.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseCaFile();
            }
        });

        FormRows connection = new FormRows(palette);
        connection.note("Prüft mit dem aktuellen Entwurf (auch ungespeichert) Schritt für Schritt, was beim Start "
                + "passiert: Proxy-Route für die Basis-URL, Namensauflösung, API-Key aus KeePass, TLS-Handshake und "
                + "ein Aufruf GET /models, dessen Modellliste mit dem Chat-Modell verglichen wird. Ein roter Schritt "
                + "nennt die Ursache; dieselben Zeilen stehen in der Protokolldatei.");
        connectionCheck = new ConnectionCheckRow(actions, form, palette);
        connection.component(null, connectionCheck, null);

        FormRows agent = new FormRows(palette);
        agent.note("Der Agent-Modus startet einen externen ACP-Agenten als Kindprozess und zeigt den Reiter "
                + "„Agent“. Der Agent bekommt die Wissenswerkzeuge über einen eigenen MCP-Endpoint.");
        agentEnabled = agent.checkBox("Agent-Modus aktivieren", "Reiter „Agent“ anzeigen");
        agentCommand = agent.textField("Kommando", "Programm, z. B. java");
        agentArgs = agent.textField("Argumente", "Shell-ähnlich, Anführungszeichen erlaubt, z. B. -jar \"C:/Pfad/agent.jar\"");
        agentTimeout = agent.textField("Timeout je Anfrage (Sekunden)", "Wartezeit auf Antworten des Agenten");

        panel = FormRows.column(palette,
                FormRows.plate("Allgemein", palette.getNavigationBlue(), general.panel(), palette),
                FormRows.plate("Netzwerk (Proxy)", palette.getAccentOrange(), proxy.panel(), palette),
                FormRows.plate("Zertifikate (TLS)", palette.getAccentYellow(), tls.panel(), palette),
                FormRows.plate("Verbindungstest", palette.getAccentRed(), connection.panel(), palette),
                FormRows.plate("Agent-Modus (ACP)", palette.getAgentPetrol(), agent.panel(), palette));
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
        proxyMode.setSelectedItem(known(form.proxyMode(), SettingsForm.PROXY_AUTO, SettingsForm.PROXY_SYSTEM,
                SettingsForm.PROXY_NONE, SettingsForm.PROXY_MANUAL));
        pacUrl.setText(form.pacUrl());
        pacDiscovery.setSelectedItem(known(form.pacDiscovery(), SettingsForm.PAC_WINDOWS_SETTINGS,
                SettingsForm.PAC_POWERSHELL));
        proxyHost.setText(form.proxyHost());
        proxyPort.setText(form.proxyPort());
        nonProxyHosts.setText(form.nonProxyHosts());
        useWindowsCertificateStore.setSelected(form.useWindowsCertificateStore());
        caCertificatesFile.setText(form.caCertificatesFile());
        agentEnabled.setSelected(form.agentEnabled());
        agentCommand.setText(form.agentCommand());
        agentArgs.setText(form.agentArgs());
        agentTimeout.setText(form.agentRequestTimeoutSeconds());
    }

    /** Der Wert, wenn er einer der Auswahlmöglichkeiten entspricht, sonst die erste (der Standard). */
    private static String known(String value, String... options) {
        for (String option : options) {
            if (option.equals(value)) {
                return option;
            }
        }
        return options[0];
    }

    void store(SettingsForm.Builder b) {
        Object mode = proxyMode.getSelectedItem();
        Object discovery = pacDiscovery.getSelectedItem();
        b.windowTitle(windowTitle.getText())
                .proxyMode(mode == null ? SettingsForm.PROXY_AUTO : mode.toString())
                .pacUrl(pacUrl.getText())
                .pacDiscovery(discovery == null ? SettingsForm.PAC_WINDOWS_SETTINGS : discovery.toString())
                .proxyHost(proxyHost.getText()).proxyPort(proxyPort.getText()).nonProxyHosts(nonProxyHosts.getText())
                .useWindowsCertificateStore(useWindowsCertificateStore.isSelected())
                .caCertificatesFile(caCertificatesFile.getText())
                .agentEnabled(agentEnabled.isSelected()).agentCommand(agentCommand.getText())
                .agentArgs(agentArgs.getText()).agentRequestTimeoutSeconds(agentTimeout.getText());
    }

    JTextField windowTitle() {
        return windowTitle;
    }

    JComboBox<String> proxyMode() {
        return proxyMode;
    }

    JTextField pacUrl() {
        return pacUrl;
    }

    JTextField proxyHost() {
        return proxyHost;
    }

    JCheckBox useWindowsCertificateStore() {
        return useWindowsCertificateStore;
    }

    JTextField caCertificatesFile() {
        return caCertificatesFile;
    }

    ConnectionCheckRow connectionCheck() {
        return connectionCheck;
    }

    JCheckBox agentEnabled() {
        return agentEnabled;
    }

    JTextField agentCommand() {
        return agentCommand;
    }
}
