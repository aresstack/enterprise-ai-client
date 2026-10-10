package com.aresstack.enterpriseai.localruntime.speech;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Stateless view of the text-to-speech voices below the model root: every subdirectory one of the
 * {@link VoiceFormatReader}s recognises is one voice (Piper {@code .onnx} + {@code .onnx.json}, or the Hugging Face
 * VITS layout). Scanned per request like the manifest models, so a voice the client has just installed appears on
 * the next {@code /api/tags}. The sidecar never downloads anything; the client installs the files.
 */
public final class LocalVoiceStore {

    private final Path modelRoot;
    private final List<VoiceFormatReader> readers = List.of(new PiperVoiceReader(), new HuggingFaceVitsVoiceReader());

    public LocalVoiceStore(Path modelRoot) {
        this.modelRoot = modelRoot;
    }

    public List<LocalVoice> voices() {
        List<LocalVoice> voices = new ArrayList<>();
        if (!Files.isDirectory(modelRoot)) {
            return voices;
        }
        try (Stream<Path> children = Files.list(modelRoot)) {
            children.filter(Files::isDirectory).sorted().forEach(dir -> {
                LocalVoice voice = read(dir);
                if (voice != null) {
                    voices.add(voice);
                }
            });
        } catch (IOException ex) {
            System.err.println("[local-runtime] cannot scan model root for voices " + modelRoot + ": "
                    + ex.getMessage());
        }
        return voices;
    }

    public LocalVoice find(String virtualName) {
        if (virtualName == null || virtualName.isBlank()) {
            return null;
        }
        Path directory = modelRoot.resolve(virtualName).normalize();
        if (!directory.startsWith(modelRoot.normalize()) || !Files.isDirectory(directory)) {
            return null;
        }
        return read(directory);
    }

    private LocalVoice read(Path directory) {
        for (VoiceFormatReader reader : readers) {
            LocalVoice voice = reader.read(directory);
            if (voice != null) {
                return voice;
            }
        }
        return null;
    }
}
