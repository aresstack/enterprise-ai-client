package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;

import java.util.function.Consumer;

/**
 * Wie Merlin etwas sagt: immer als Sprechblase beim Zauberer, zusätzlich gesprochen, wenn eine Stimme (TTS-Modell,
 * {@code model.tts}) ausgewählt und verfügbar ist. Fehlt sie, bleibt es bei der Sprechblase; der Assistent hängt nie
 * von der Sprachausgabe ab. Nur auf dem EDT.
 */
final class WizardNarrator {

    private final Consumer<String> bubble;
    private ReadAloudControl voice;

    /** @param bubble zeigt den Satz an ({@code null} = Sprechblase leeren) */
    WizardNarrator(Consumer<String> bubble) {
        if (bubble == null) {
            throw new IllegalArgumentException("bubble must not be null");
        }
        this.bubble = bubble;
    }

    /** Die Stimme der Anwendung oder {@code null} (dann nur Sprechblasen). */
    void setVoice(ReadAloudControl voice) {
        this.voice = voice;
    }

    /** Zeigt den Satz und spricht ihn, wenn möglich; {@code null} oder leer leert die Sprechblase. */
    void say(String text) {
        if (text == null || text.trim().isEmpty()) {
            bubble.accept(null);
            return;
        }
        bubble.accept(text);
        if (canSpeak()) {
            voice.speak(text);
        }
    }

    /** Nur anzeigen, nicht sprechen (Zwischenstand wie „Moment, ich denke kurz nach …“). */
    void show(String text) {
        bubble.accept(text);
    }

    boolean canSpeak() {
        return voice != null && voice.isAvailable();
    }

    /** Beendet eine laufende Ausgabe (Assistent geschlossen); idempotent. */
    void stop() {
        if (voice != null) {
            voice.stop();
        }
    }
}
