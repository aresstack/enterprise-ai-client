package com.aresstack.enterpriseai.speech.local;

import java.nio.file.Path;

/**
 * Eine lokal installierte Stimme, unabhängig von Dateiformat und Backend. Der Name ist der Ordnername unter dem
 * Modellverzeichnis, also die Modellkennung der Auswahl {@code model.tts} ohne Katalogpräfix.
 */
final class LocalVoice {

    private final String name;
    private final String family;
    private final Path modelFile;
    private final int sampleRate;
    private final VoiceTextEncoder encoder;
    private final VoiceParameters parameters;

    LocalVoice(String name, String family, Path modelFile, int sampleRate, VoiceTextEncoder encoder,
               VoiceParameters parameters) {
        this.name = name;
        this.family = family;
        this.modelFile = modelFile;
        this.sampleRate = sampleRate;
        this.encoder = encoder;
        this.parameters = parameters;
    }

    String name() {
        return name;
    }

    /** Stimmenformat ({@code piper} oder {@code vits}). */
    String family() {
        return family;
    }

    Path modelFile() {
        return modelFile;
    }

    int sampleRate() {
        return sampleRate;
    }

    VoiceTextEncoder encoder() {
        return encoder;
    }

    VoiceParameters parameters() {
        return parameters;
    }
}
