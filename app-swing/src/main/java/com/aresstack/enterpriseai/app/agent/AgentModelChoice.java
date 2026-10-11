package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.domain.chat.ChatOptions;

/**
 * Was im Composer des Agent-Modus gewählt ist: Chat-Modell und Denkaufwand, wie im Chat
 * ({@code RagChatBinding.turnOptions}). Gilt ab der nächsten Modellanfrage des eingebauten Recherche-Agenten.
 */
public final class AgentModelChoice {

    private volatile String model;
    private volatile String reasoningEffort;

    /** Das im Composer gewählte Chat-Modell; {@code null} oder leer = Standard aus der Konfiguration. */
    public void modelChanged(String modelId) {
        this.model = modelId == null || modelId.trim().isEmpty() ? null : modelId.trim();
    }

    /** {@code low}, {@code medium}, {@code high} oder {@code null} für den Standard des Modells. */
    public void reasoningChanged(String effort) {
        this.reasoningEffort = effort;
    }

    /** Die Optionen der nächsten Anfrage: die Standardwerte, überschrieben mit der Auswahl. */
    public ChatOptions applyTo(ChatOptions defaults) {
        String chosenModel = model;
        String chosenEffort = reasoningEffort;
        if (chosenModel == null && chosenEffort == null) {
            return defaults;
        }
        ChatOptions.Builder options = defaults.toBuilder();
        if (chosenModel != null) {
            options.model(chosenModel);
        }
        if (chosenEffort != null) {
            options.reasoningEffort(chosenEffort);
        }
        return options.build();
    }
}
