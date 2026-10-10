package com.aresstack.enterpriseai.speech.local;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/** Nachsichtiger Zugriff auf die Konfigurationsdateien der Stimmen (Gson bleibt hier). */
final class VoiceJson {

    private static final Gson GSON = new Gson();

    private VoiceJson() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> read(Path file) throws IOException {
        Object parsed = GSON.fromJson(new String(Files.readAllBytes(file), StandardCharsets.UTF_8), Object.class);
        return parsed instanceof Map ? (Map<String, Object>) parsed : Collections.<String, Object>emptyMap();
    }

    static int intValue(Object value, int fallback) {
        return value instanceof Number && ((Number) value).intValue() > 0 ? ((Number) value).intValue() : fallback;
    }

    static float floatValue(Object value, float fallback) {
        return value instanceof Number ? ((Number) value).floatValue() : fallback;
    }

    static boolean boolValue(Object value, boolean fallback) {
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    static String text(Object value) {
        return value instanceof String ? (String) value : "";
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.<String, Object>emptyMap();
    }
}
