package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Reiter „KI-Dienst“: Chat-Endpunkt, Modell, KeePass-Eintrag, System-Prompt; darunter Embeddings und „Verbindung
 * testen“. Nach einem Verbindungstest erscheinen unter beiden Modellfeldern Auswahllisten aus {@code GET /models}.
 */
final class ServiceTab {

    private final JTextField chatBaseUrl;
    private final JTextField chatModel;
    private final JTextField chatApiKeyRef;
    private final SecretCheckRow secretCheck;
    private final JTextArea chatSystemPrompt;
    private final JTextField embeddingBaseUrl;
    private final JTextField embeddingModel;
    private final ModelPicker chatModelChoice;
    private final ModelPicker embeddingModelChoice;
    private final JTextField embeddingDimension;
    private final JTextField embeddingApiKeyRef;
    private final ConnectionCheckRow connectionCheck;
    private final JPanel panel;

    ServiceTab(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        FormRows chat = new FormRows(palette);
        chat.note("Die Enterprise-API im OpenAI-kompatiblen Format. Die Basis-URL endet ohne Endpunkt (meist auf "
                + "/v1); die Anwendung hängt /chat/completions, /embeddings und /models selbst an.");
        chatBaseUrl = chat.textField("Basis-URL", "Absolute http(s)-URL ohne Zugangsdaten, Query oder Fragment");
        chatModel = chat.textField("Chat-Modell", "Modellname, wie ihn der Dienst unter GET /models nennt");
        chatModelChoice = new ModelPicker(chat, chatModel, "Verfügbare Chat-Modelle aus GET /models; "
                + "„Tool-Calling“ heißt, der Dienst meldet tool_calling (nötig für Dateianhänge)", palette);
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
        embeddingModel = embedding.textField("Embedding-Modell", "Modellname des Embedding-Endpunkts");
        embeddingModelChoice = new ModelPicker(embedding, embeddingModel,
                "Verfügbare Embedding-Modelle aus GET /models", palette);
        embeddingDimension = embedding.textField("Dimension", "Länge der Vektoren, die der Dienst liefert (ganze Zahl)");
        embeddingApiKeyRef = embedding.textField("KeePass-Eintrag (optional)", "Leer: wie Chat");

        FormRows connection = new FormRows(palette);
        connectionCheck = new ConnectionCheckRow(actions, form, palette,
                new BiConsumer<List<ModelChoice>, List<ModelChoice>>() {
                    @Override
                    public void accept(List<ModelChoice> chatModels, List<ModelChoice> embeddingModels) {
                        chatModelChoice.show(chatModels);
                        embeddingModelChoice.show(embeddingModels);
                    }
                });
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
        chatModel.setText(form.chatModel());
        chatApiKeyRef.setText(form.chatApiKeyRef());
        chatSystemPrompt.setText(form.chatSystemPrompt());
        embeddingBaseUrl.setText(form.embeddingBaseUrl());
        embeddingModel.setText(form.embeddingModel());
        embeddingDimension.setText(form.embeddingDimension());
        embeddingApiKeyRef.setText(form.embeddingApiKeyRef());
    }

    void store(SettingsForm.Builder b) {
        b.chatBaseUrl(chatBaseUrl.getText()).chatModel(chatModel.getText()).chatApiKeyRef(chatApiKeyRef.getText())
                .chatSystemPrompt(chatSystemPrompt.getText())
                .embeddingBaseUrl(embeddingBaseUrl.getText()).embeddingModel(embeddingModel.getText())
                .embeddingDimension(embeddingDimension.getText()).embeddingApiKeyRef(embeddingApiKeyRef.getText());
    }

    JTextField chatBaseUrl() {
        return chatBaseUrl;
    }

    JTextField chatModel() {
        return chatModel;
    }

    JTextField chatApiKeyRef() {
        return chatApiKeyRef;
    }

    JTextField embeddingModel() {
        return embeddingModel;
    }

    JTextField embeddingDimension() {
        return embeddingDimension;
    }

    ModelPicker chatModelChoice() {
        return chatModelChoice;
    }

    ModelPicker embeddingModelChoice() {
        return embeddingModelChoice;
    }

    ConnectionCheckRow connectionCheck() {
        return connectionCheck;
    }

    SecretCheckRow secretCheck() {
        return secretCheck;
    }
}
