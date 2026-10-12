package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reiter „KI-Dienst“: Zugang (KeePass oder manuell), KeePass-Eintrag, System-Prompt; darunter Embeddings und
 * „Verbindung testen“. Mit KeePass genügt der Eintrag: API-Key und URL stehen dort (wie MainframeMate); die URL
 * lässt sich unter „Erweitert“ überschreiben. Die Modelle selbst wählt der Reiter „Modelle“ ({@link ModelsTab}).
 */
final class ServiceTab {

    /** Name der manuellen Zugangsdaten des KI-Dienstes im Abfrage-Dialog. */
    private static final String MANUAL_NAME = "KI-Dienst";

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final JComboBox<String> access;
    private final JComboBox<String> entry;
    private final FormRows.Row entryRow;
    private final SecretCheckRow secretCheck;
    private final FormRows.Row secretCheckRow;
    private final JTextField manualUrl = new JTextField(28);
    private final FormRows.Row manualUrlRow;
    private final JTextField overrideUrl = new JTextField(28);
    private final FormRows.Row overrideUrlRow;
    private final JButton advancedToggle;
    private final JTextArea chatSystemPrompt;
    private final JTextField embeddingBaseUrl;
    private final JTextField embeddingDimension;
    private final JTextField embeddingApiKeyRef;
    private final ConnectionCheckRow connectionCheck;
    private final JPanel panel;
    private boolean expanded;
    private boolean configured;

    ServiceTab(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        FormRows chat = new FormRows(palette);
        chat.note("Die Enterprise-API im OpenAI-kompatiblen Format. Mit KeePass genügt der Eintrag: API-Key "
                + "(Passwortfeld) und Basis-URL (URL-Feld, meist auf /v1) stehen dort. Die Modelle wählt der Reiter "
                + "„Modelle“.");
        access = VaultAccess.modeChoice(palette);
        chat.component("Zugang", access, "KeePass: API-Key und URL aus dem KeePass-Eintrag. Manuell: URL hier "
                + "angeben, der API-Key wird beim ersten Zugriff abgefragt und nicht gespeichert.");
        entry = VaultAccess.entryChoice(palette);
        entryRow = chat.component("KeePass-Eintrag", VaultAccess.withNewEntry(entry,
                () -> actions == null ? PasswordStore.unavailable("In dieser Umgebung nicht verfügbar.")
                : actions.passwordStore(form.get()), () -> "KI-Dienst", palette), "Eintrag mit dem API-Key im Passwortfeld und der "
                + "Basis-URL im URL-Feld");
        secretCheck = new SecretCheckRow(actions, form, new Supplier<String>() {
            @Override
            public String get() {
                return VaultAccess.text(entry);
            }
        }, palette);
        secretCheckRow = chat.component(null, secretCheck, null);
        manualUrlRow = chat.textField(manualUrl, "Basis-URL *",
                "Absolute http(s)-URL ohne Zugangsdaten, Query oder Fragment; meist bis /v1");
        chatSystemPrompt = chat.textArea("System-Prompt", 6, "Erste Nachricht jeder Konversation; leer = Standard");
        advancedToggle = VaultAccess.advancedToggle(palette);
        chat.component(null, advancedToggle, null);
        overrideUrlRow = chat.textField(overrideUrl, "Basis-URL (sonst aus KeePass)",
                "Leer: die URL des KeePass-Eintrags; sonst überschreibt dieser Wert sie");
        advancedToggle.addActionListener(event -> {
            expanded = !expanded;
            showRows();
        });
        access.addActionListener(event -> {
            boolean manual = isManual();
            // die URL wandert mit: Pflichtfeld bei „Manuell“, Überschreibung bei „KeePass“
            if (manual && manualUrl.getText().trim().isEmpty()) {
                manualUrl.setText(overrideUrl.getText());
            } else if (!manual && overrideUrl.getText().trim().isEmpty()) {
                overrideUrl.setText(manualUrl.getText());
            }
            showRows();
        });

        FormRows embedding = new FormRows(palette);
        embedding.note("Embeddings vektorisieren die Wissensbasis. Basis-URL und KeePass-Eintrag dürfen leer "
                + "bleiben, dann gelten die Werte des Chats.");
        embeddingBaseUrl = embedding.textField("Basis-URL (optional)", "Leer: wie Chat");
        embeddingDimension = embedding.textField("Dimension", "Länge der Vektoren, die der Dienst liefert (ganze Zahl)");
        embeddingApiKeyRef = embedding.textField("KeePass-Eintrag (optional)", "Leer: wie Chat");

        FormRows connection = new FormRows(palette);
        connectionCheck = new ConnectionCheckRow(actions, form, palette);
        connection.component(null, connectionCheck, null);

        panel = FormRows.column(palette,
                FormRows.plate("Chat", palette.getNavigationBlue(), chat.panel(), palette),
                FormRows.plate("Embeddings", palette.getAgentPetrol(), embedding.panel(), palette),
                FormRows.plate("Verbindung", palette.getAccentRed(), connection.panel(), palette));
        showRows();
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm loaded) {
        String stored = loaded.chatApiKeyRef();
        boolean manual = VaultAccess.isManual(stored);
        configured = stored != null && !stored.trim().isEmpty();
        access.setSelectedItem(manual ? VaultAccess.MODE_MANUAL : VaultAccess.MODE_KEEPASS);
        VaultAccess.setText(entry, VaultAccess.title(stored));
        manualUrl.setText(manual ? loaded.chatBaseUrl() : "");
        overrideUrl.setText(manual ? "" : loaded.chatBaseUrl());
        chatSystemPrompt.setText(loaded.chatSystemPrompt());
        embeddingBaseUrl.setText(loaded.embeddingBaseUrl());
        embeddingDimension.setText(loaded.embeddingDimension());
        embeddingApiKeyRef.setText(loaded.embeddingApiKeyRef());
        showRows();
        requestEntries(loaded);
    }

    void store(SettingsForm.Builder b) {
        boolean manual = isManual();
        b.chatBaseUrl(manual ? manualUrl.getText() : overrideUrl.getText())
                .chatApiKeyRef(VaultAccess.stored(manual, VaultAccess.text(entry), MANUAL_NAME))
                .chatSystemPrompt(chatSystemPrompt.getText())
                .embeddingBaseUrl(embeddingBaseUrl.getText())
                .embeddingDimension(embeddingDimension.getText()).embeddingApiKeyRef(embeddingApiKeyRef.getText());
    }

    private boolean isManual() {
        return VaultAccess.MODE_MANUAL.equals(access.getSelectedItem());
    }

    private void showRows() {
        boolean manual = isManual();
        entryRow.setVisible(!manual);
        secretCheckRow.setVisible(!manual);
        manualUrlRow.setVisible(manual);
        overrideUrlRow.setVisible(!manual && expanded);
        advancedToggle.setVisible(!manual);
        advancedToggle.setText(expanded ? VaultAccess.ADVANCED_OPEN : VaultAccess.ADVANCED_CLOSED);
        panel().revalidate();
        panel().repaint();
    }

    /** KeePass-Titel zur Auswahl; ein eindeutiger passender Eintrag wird vorgewählt, solange nichts gesetzt ist. */
    private void requestEntries(SettingsForm loaded) {
        if (actions == null || loaded == null) {
            return;
        }
        actions.keePassEntries(loaded, new Consumer<List<String>>() {
            @Override
            public void accept(List<String> titles) {
                VaultAccess.setEntries(entry, titles);
                if (!configured && VaultAccess.text(entry).isEmpty()) {
                    String match = VaultAccess.unique(titles, "Enterprise AI", "KIPITZ", "KI-Dienst");
                    if (match != null) {
                        VaultAccess.setText(entry, match);
                    }
                }
            }
        });
    }

    /** Das Feld, das gerade die Basis-URL trägt (Pflichtfeld bei „Manuell“, Überschreibung bei „KeePass“). */
    JTextField chatBaseUrl() {
        return isManual() ? manualUrl : overrideUrl;
    }

    /** Eingabefeld des KeePass-Eintrags. */
    JTextComponent chatApiKeyRef() {
        return VaultAccess.editorField(entry);
    }

    JComboBox<String> access() {
        return access;
    }

    JTextField embeddingDimension() {
        return embeddingDimension;
    }

    ConnectionCheckRow connectionCheck() {
        return connectionCheck;
    }

    SecretCheckRow secretCheck() {
        return secretCheck;
    }

    /** Fokus beim Öffnen: der KeePass-Eintrag bzw. bei „Manuell“ die URL. */
    void focusFirstField() {
        if (isManual()) {
            manualUrl.requestFocusInWindow();
        } else {
            entry.requestFocusInWindow();
        }
    }
}
