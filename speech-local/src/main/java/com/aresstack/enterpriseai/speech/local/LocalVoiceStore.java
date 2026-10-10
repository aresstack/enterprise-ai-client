package com.aresstack.enterpriseai.speech.local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Sicht auf die Stimmen unter dem Modellverzeichnis: jeder Unterordner, den einer der {@link VoiceFormatReader}
 * erkennt, ist eine Stimme (Piper {@code .onnx} + {@code .onnx.json} oder VITS im Hugging-Face-Layout). Je Anfrage
 * gelesen, damit eine gerade installierte Stimme sofort spricht.
 */
final class LocalVoiceStore {

    private final Path modelRoot;
    private final List<VoiceFormatReader> readers = Arrays.<VoiceFormatReader>asList(new PiperVoiceReader(),
            new HuggingFaceVitsVoiceReader());

    LocalVoiceStore(Path modelRoot) {
        this.modelRoot = modelRoot;
    }

    /** Die Stimme im Ordner {@code name}; {@code null}, wenn es ihn nicht gibt oder kein Format passt. */
    LocalVoice find(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        Path root = modelRoot.toAbsolutePath().normalize();
        Path directory = root.resolve(name.trim()).normalize();
        if (!directory.startsWith(root) || directory.equals(root) || !Files.isDirectory(directory)) {
            return null;
        }
        for (VoiceFormatReader reader : readers) {
            LocalVoice voice = reader.read(directory);
            if (voice != null) {
                return voice;
            }
        }
        return null;
    }
}
