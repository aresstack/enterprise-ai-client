package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupVoices;

import java.util.function.Consumer;

/**
 * Der Verbindungstest des Einstellungen-Dialogs über einer fertig geladenen Konfiguration. Produktiv
 * {@code app.composition.ServiceConnectionChecker} (KeePass für den API-Key plus {@link ConnectionProbe});
 * Tests setzen eine Attrappe ein. Blockiert und wird deshalb von {@link FileSettingsActions} auf dem
 * Arbeits-Executor gerufen; {@code onStep} kommt auf demselben Thread.
 */
public interface ConnectionChecker {

    /** @return {@code true}, wenn kein Schritt fehlgeschlagen ist */
    boolean check(AppConfig config, Consumer<ConnectionCheckStep> onStep);

    /**
     * Der Embedding-Test des Einrichtungsassistenten: ein echter kleiner Aufruf mit {@code embedding.model}; die
     * Dimension ist die Länge des zurückgegebenen Vektors ({@code embedding.dimension} der Konfiguration zählt nicht).
     * Blockiert; wirft mit der Ursache, wenn der Aufruf scheitert.
     */
    default int embeddingDimension(AppConfig config) throws Exception {
        throw new UnsupportedOperationException("Embedding-Test in dieser Umgebung nicht verfügbar");
    }

    /**
     * Merlins freie Frage im Einrichtungsassistenten: ein einzelner Aufruf von {@code chat.model} mit Rahmen und
     * Frage, ohne Werkzeuge. Blockiert; wirft mit der Ursache, wenn der Aufruf scheitert.
     */
    default String ask(AppConfig config, String system, String question) throws Exception {
        throw new UnsupportedOperationException("Freie Fragen in dieser Umgebung nicht verfügbar");
    }

    /**
     * Die Stimmen der Übergabe am Ende der ersten Einrichtung: Merlin ({@code setup.merlin.ttsModel}) und der
     * Chat-Assistent ({@code model.tts}, {@code chat.assistant.displayName}). Ohne Sprachausgabe in dieser Umgebung:
     * nur Sprechblasen.
     */
    default SetupVoices voices(AppConfig config) {
        return SetupVoices.silent(null);
    }

    /** Nur Merlins Stimme (Einrichtungsassistent nach dem KI-Dienst-Test); ohne Sprachausgabe: keine. */
    default SetupVoices merlinVoice(AppConfig config) {
        return SetupVoices.silent(null);
    }
}
