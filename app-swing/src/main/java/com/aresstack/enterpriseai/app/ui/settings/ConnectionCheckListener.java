package com.aresstack.enterpriseai.app.ui.settings;

import java.util.List;

/**
 * Empfänger des Verbindungstests: jeder Schritt einzeln, sobald er feststeht, und zum Schluss genau einmal
 * {@link #onFinished}. Alle Methoden werden auf dem EDT gerufen.
 */
public interface ConnectionCheckListener {

    void onStep(ConnectionCheckStep step);

    /**
     * Die Modelle, die {@code GET /models} geliefert hat, schon nach Eignung getrennt; kommt höchstens einmal
     * und vor {@link #onFinished}.
     */
    default void onModels(List<ModelChoice> chatModels, List<ModelChoice> embeddingModels) {
    }

    /** @param success {@code true}, wenn kein Schritt fehlgeschlagen ist (Hinweise zählen nicht als Fehler) */
    void onFinished(boolean success);
}
