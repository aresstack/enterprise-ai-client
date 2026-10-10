package com.aresstack.enterpriseai.model.huggingface;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Herkunft einer installierten Stimme ({@value #FILE_NAME} im Stimmenordner): Repository, Revision und Dateien.
 * Damit bleiben Stimmen aus der Hub-Suche nach einem Neustart sichtbar und lassen sich aktualisieren und
 * entfernen. Die Leser der Stimmenformate ignorieren die Datei.
 */
final class VoiceSource {

    static final String FILE_NAME = "huggingface-source.json";

    private VoiceSource() {
    }

    static void write(HuggingFaceVoice voice, Path directory) throws IOException {
        JsonObject source = new JsonObject();
        source.addProperty("name", voice.displayName());
        source.addProperty("language", voice.language());
        source.addProperty("repository", voice.repository());
        source.addProperty("revision", voice.revision());
        JsonArray files = new JsonArray();
        for (String file : voice.files()) {
            files.add(file);
        }
        source.add("files", files);
        Files.write(directory.resolve(FILE_NAME), source.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Die Stimme aus dem Ordner; {@code null}, wenn keine oder eine unbrauchbare Herkunftsdatei da ist. */
    static HuggingFaceVoice read(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonObject source = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            List<String> files = new ArrayList<String>();
            if (source.has("files") && source.get("files").isJsonArray()) {
                for (JsonElement entry : source.getAsJsonArray("files")) {
                    files.add(entry.getAsString());
                }
            }
            return new HuggingFaceVoice(directory.getFileName().toString(), text(source, "name"),
                    text(source, "language"), text(source, "repository"), text(source, "revision"), files);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static String text(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
