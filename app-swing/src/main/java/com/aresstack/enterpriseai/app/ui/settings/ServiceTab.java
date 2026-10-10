package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.util.function.Supplier;

/**
 * Reiter „KI-Dienst“: Chat-Endpunkt, KeePass-Eintrag, System-Prompt; darunter Embeddings und „Verbindung testen“.
 * Die Modelle selbst wählt der Reiter „Modelle“ ({@link ModelsTab}) je Kategorie.
 */
final class ServiceTab {

    private final JTextField chatBaseUrl;
    private final JTextField chatApiKeyRef;
    private final SecretCheckRow secretCheck;
    private final JTextArea chatSystemPrompt;
    private final JTextField embeddingBaseUrl;
    private final JTextField embeddingDimension;
    private final JTextField embeddingApiKeyRef;
    private final ConnectionCheckRow connectionCheck;
    private final JPanel panel;

    ServiceTab(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        FormRows chat = new FormRows(palette);
        chat.note("Die Enterprise-API im OpenAI-kompatiblen Format. Die Basis-URL endet ohne Endpunkt (meist auf "
                + "/v1); die Anwendung hängt /chat/completions, /embeddings und /models selbst an. Die Modelle wählt der Reiter "
                + "„Modelle“.");
        chatBaseUrl = chat.textField("Basis-URL", "Absolute http(s)-URL ohne Zugangsdaten, Query oder Fragment");
        chatApiKeyRef = chat.textField("KeePass-Eintrag mit API-Key",
                "Titel des KeePass-Eintrags; der API-Key steht in dessen Passwortfeld. Präfix keepass: ist erlaubt.");
        secretCheck = new SecretCheckRow(actions, form, new Supplier<String>() {
            @Override
            public String get() {
                return chatApiKeyRef.getText();
            }
        }, palette);
        chat.component(null, secretCheck, null);
        chatSystemPrompt = chat.textArea("System-Prompt", 3, "Optional: erste Nachricht jeder Konversation");

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
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        chatBaseUrl.setText(form.chatBaseUrl());
        chatApiKeyRef.setText(form.chatApiKeyRef());
        chatSystemPrompt.setText(form.chatSystemPrompt());
        embeddingBaseUrl.setText(form.embeddingBaseUrl());
        embeddingDimension.setText(form.embeddingDimension());
        embeddingApiKeyRef.setText(form.embeddingApiKeyRef());
    }

    void store(SettingsForm.Builder b) {
        b.chatBaseUrl(chatBaseUrl.getText()).chatApiKeyRef(chatApiKeyRef.getText())
                .chatSystemPrompt(chatSystemPrompt.getText())
                .embeddingBaseUrl(embeddingBaseUrl.getText())
                .embeddingDimension(embeddingDimension.getText()).embeddingApiKeyRef(embeddingApiKeyRef.getText());
    }

    JTextField chatBaseUrl() {
        return chatBaseUrl;
    }

    JTextField chatApiKeyRef() {
        return chatApiKeyRef;
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
}
