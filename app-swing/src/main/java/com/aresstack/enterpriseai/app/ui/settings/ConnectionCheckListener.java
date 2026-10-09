package com.aresstack.enterpriseai.app.ui.settings;

/**
 * Empfänger des Verbindungstests: jeder Schritt einzeln, sobald er feststeht, und zum Schluss genau einmal
 * {@link #onFinished}. Beide Methoden werden auf dem EDT gerufen.
 */
public interface ConnectionCheckListener {

    void onStep(ConnectionCheckStep step);

    /** @param success {@code true}, wenn kein Schritt fehlgeschlagen ist (Hinweise zählen nicht als Fehler) */
    void onFinished(boolean success);
}
