package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.util.function.Supplier;

/** Reiter „KeePass“: Verbindung zu KeePassRPC, Pairing-Ablage und die Probe des API-Key-Eintrags. */
final class SecurityTab {

    static final String STORE_FILE_LABEL = "Datei im Anwendungsverzeichnis (dauerhaft)";
    static final String STORE_MEMORY_LABEL = "Nur im Speicher (bei jedem Start neu pairen)";

    private final JCheckBox enabled;
    private final JTextField host;
    private final JTextField port;
    private final JTextField clientDisplayName;
    private final JComboBox<String> pairingKeyStore;
    private final SecretCheckRow secretCheck;
    private final JPanel panel;

    SecurityTab(SettingsDialogActions actions, final Supplier<SettingsForm> form, ComicPalette palette) {
        FormRows rows = new FormRows(palette);
        rows.note("Secrets stehen nie in der Konfigurationsdatei. API-Key und Zugangsdaten liegen in KeePass 2.x "
                + "mit dem Plugin KeePassRPC; die Anwendung holt sie je Anfrage aus dem entsperrten Tresor. "
                + "Beim ersten Zugriff zeigt KeePass ein Einmal-Passwort, das im Pairing-Dialog eingegeben wird.");
        enabled = rows.checkBox("KeePassRPC verwenden",
                "Aus: die Anwendung startet ohne Secrets; jede Anfrage scheitert mit einem Authentifizierungsfehler");
        host = rows.textField("Host", "KeePassRPC lauscht nur lokal; Standard 127.0.0.1");
        port = rows.textField("Port", "Standard 12546 (Einstellungen des KeePassRPC-Plugins)");
        clientDisplayName = rows.textField("Anzeigename beim Pairing", "Name, den KeePass im Dialog „Authorise a new connection“ zeigt");
        pairingKeyStore = rows.comboBox("Pairing-Schlüssel", "Wo der Schlüssel des Pairings abgelegt wird",
                STORE_FILE_LABEL, STORE_MEMORY_LABEL);
        rows.note("Prüfen verbindet sich mit diesen Einstellungen, pairt bei Bedarf und sucht den Eintrag aus "
                + "„KI-Dienst → KeePass-Eintrag mit API-Key“. Der Key selbst wird nicht angezeigt.");
        secretCheck = new SecretCheckRow(actions, form, new Supplier<String>() {
            @Override
            public String get() {
                return form.get().chatApiKeyRef();
            }
        }, palette);
        rows.component(null, secretCheck, null);

        panel = FormRows.column(palette,
                FormRows.plate("KeePassRPC", palette.getAccentOrange(), rows.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        enabled.setSelected(form.keePassEnabled());
        host.setText(form.keePassHost());
        port.setText(form.keePassPort());
        clientDisplayName.setText(form.keePassClientDisplayName());
        pairingKeyStore.setSelectedItem(SettingsForm.PAIRING_KEY_STORE_MEMORY.equals(form.keePassPairingKeyStore())
                ? STORE_MEMORY_LABEL : STORE_FILE_LABEL);
    }

    void store(SettingsForm.Builder b) {
        b.keePassEnabled(enabled.isSelected()).keePassHost(host.getText()).keePassPort(port.getText())
                .keePassClientDisplayName(clientDisplayName.getText())
                .keePassPairingKeyStore(STORE_MEMORY_LABEL.equals(pairingKeyStore.getSelectedItem())
                        ? SettingsForm.PAIRING_KEY_STORE_MEMORY : SettingsForm.PAIRING_KEY_STORE_FILE);
    }

    JCheckBox enabled() {
        return enabled;
    }

    JTextField host() {
        return host;
    }

    JTextField port() {
        return port;
    }

    JComboBox<String> pairingKeyStore() {
        return pairingKeyStore;
    }

    SecretCheckRow secretCheck() {
        return secretCheck;
    }
}
