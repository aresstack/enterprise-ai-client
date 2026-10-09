package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Reiter „Netzwerk & Agent“: Fenstertitel, Proxy und der optionale Agent-Modus über ACP. */
final class SystemTab {

    private final JTextField windowTitle;
    private final JComboBox<String> proxyMode;
    private final JTextField proxyHost;
    private final JTextField proxyPort;
    private final JTextField nonProxyHosts;
    private final JCheckBox agentEnabled;
    private final JTextField agentCommand;
    private final JTextField agentArgs;
    private final JTextField agentTimeout;
    private final JPanel panel;

    SystemTab(ComicPalette palette) {
        FormRows general = new FormRows(palette);
        windowTitle = general.textField("Fenstertitel", "Titel des Hauptfensters");

        FormRows proxy = new FormRows(palette);
        proxy.note("SYSTEM übernimmt die Proxy-Einstellungen der JVM bzw. des Systems, NONE verbindet immer "
                + "direkt, MANUAL nutzt den angegebenen Proxy. Loopback-Adressen gehen nie über den Proxy.");
        proxyMode = proxy.comboBox("Modus", "Proxy-Regel für alle HTTP-Zugriffe",
                SettingsForm.PROXY_SYSTEM, SettingsForm.PROXY_NONE, SettingsForm.PROXY_MANUAL);
        proxyHost = proxy.textField("Proxy-Host (bei MANUAL)", "Hostname oder Adresse des Proxys");
        proxyPort = proxy.textField("Proxy-Port (bei MANUAL)", "Port des Proxys");
        nonProxyHosts = proxy.textField("Ohne Proxy (optional)", "Kommagetrennte Hostmuster, z. B. *.intern.example");

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
                FormRows.plate("Agent-Modus (ACP)", palette.getAgentPetrol(), agent.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        windowTitle.setText(form.windowTitle());
        String mode = form.proxyMode();
        proxyMode.setSelectedItem(SettingsForm.PROXY_NONE.equals(mode) || SettingsForm.PROXY_MANUAL.equals(mode)
                ? mode : SettingsForm.PROXY_SYSTEM);
        proxyHost.setText(form.proxyHost());
        proxyPort.setText(form.proxyPort());
        nonProxyHosts.setText(form.nonProxyHosts());
        agentEnabled.setSelected(form.agentEnabled());
        agentCommand.setText(form.agentCommand());
        agentArgs.setText(form.agentArgs());
        agentTimeout.setText(form.agentRequestTimeoutSeconds());
    }

    void store(SettingsForm.Builder b) {
        Object mode = proxyMode.getSelectedItem();
        b.windowTitle(windowTitle.getText())
                .proxyMode(mode == null ? SettingsForm.PROXY_SYSTEM : mode.toString())
                .proxyHost(proxyHost.getText()).proxyPort(proxyPort.getText()).nonProxyHosts(nonProxyHosts.getText())
                .agentEnabled(agentEnabled.isSelected()).agentCommand(agentCommand.getText())
                .agentArgs(agentArgs.getText()).agentRequestTimeoutSeconds(agentTimeout.getText());
    }

    JTextField windowTitle() {
        return windowTitle;
    }

    JComboBox<String> proxyMode() {
        return proxyMode;
    }

    JTextField proxyHost() {
        return proxyHost;
    }

    JCheckBox agentEnabled() {
        return agentEnabled;
    }

    JTextField agentCommand() {
        return agentCommand;
    }
}
