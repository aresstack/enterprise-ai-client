package com.aresstack.enterpriseai.model.huggingface;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Herkunft einer installierten Stimme ({@value #FILE_NAME} im Stimmenordner): Repository, Revision und Dateien.
 * Damit bleiben Stimmen aus der Hub-Suche nach einem Neustart sichtbar und lassen sich aktualisieren und
 * entfernen. Der Sidecar ignoriert die Datei.
 */
final class VoiceSource {

    static final String FILE_NAME = "huggingface-source.properties";

    private VoiceSource() {
    }

    static void write(HuggingFaceVoice voice, Path directory) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("name", voice.displayName());
        properties.setProperty("language", voice.language());
        properties.setProperty("repository", voice.repository());
        properties.setProperty("revision", voice.revision());
        StringBuilder files = new StringBuilder();
        for (String file : voice.files()) {
            files.append(files.length() == 0 ? "" : ",").append(file);
        }
        properties.setProperty("files", files.toString());
        try (OutputStream out = Files.newOutputStream(directory.resolve(FILE_NAME))) {
            properties.store(out, "Hugging Face source of this voice (enterprise-ai-client)");
        }
    }

    /** Die Stimme aus dem Ordner; {@code null}, wenn keine oder eine unbrauchbare Herkunftsdatei da ist. */
    static HuggingFaceVoice read(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            return null;
        }
        List<String> files = new ArrayList<String>();
        for (String part : Arrays.asList(properties.getProperty("files", "").split(","))) {
            if (!part.trim().isEmpty()) {
                files.add(part.trim());
            }
        }
        try {
            return new HuggingFaceVoice(directory.getFileName().toString(), properties.getProperty("name"),
                    properties.getProperty("language"), properties.getProperty("repository"),
                    properties.getProperty("revision"), files);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
