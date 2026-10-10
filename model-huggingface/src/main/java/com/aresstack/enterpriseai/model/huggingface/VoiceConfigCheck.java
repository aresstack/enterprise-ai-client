package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Prüft die kleine Konfigurationsdatei einer Stimme, bevor das große Modell geladen wird, mit denselben Regeln
 * wie die Stimmenleser von speech-local: Piper ({@code .onnx.json}) mit {@code phoneme_type "text"} oder deutscher
 * espeak-Stimme, VITS ({@code config.json}) mit {@code model_type "vits"}. Andere Dateien prüft sie nicht.
 */
final class VoiceConfigCheck {

    private VoiceConfigCheck() {
    }

    static void check(Path file) throws LocalVoiceInstallException {
        String name = file.getFileName().toString();
        if (!name.endsWith(".onnx.json") && !name.equals("config.json")) {
            return;
        }
        JsonObject config;
        try {
            config = JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new LocalVoiceInstallException(name + " ist kein lesbares JSON: " + e.getMessage(), e);
        }
        if (name.equals("config.json")) {
            if (!"vits".equals(text(config, "model_type"))) {
                throw new LocalVoiceInstallException("Kein VITS-Modell (model_type \"" + text(config, "model_type")
                        + "\"); der Client lädt in diesem Layout nur VITS-Stimmen.");
            }
            return;
        }
        String type = text(config, "phoneme_type");
        if ("text".equals(type)) {
            return;
        }
        if (!type.isEmpty() && !"espeak".equals(type)) {
            throw new LocalVoiceInstallException("Piper-Stimme mit phoneme_type \"" + type
                    + "\" wird nicht unterstützt.");
        }
        String language = text(object(config, "espeak"), "voice");
        if (language.isEmpty()) {
            language = text(object(config, "language"), "code");
        }
        String normalized = language.toLowerCase(Locale.ROOT);
        if (!normalized.equals("de") && !normalized.startsWith("de-") && !normalized.startsWith("de_")) {
            throw new LocalVoiceInstallException("Piper-Stimme für espeak-Sprache \"" + language
                    + "\": ohne externes Programm spricht der Client nur deutsche espeak-Stimmen.");
        }
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String text(JsonObject parent, String name) {
        JsonElement value = parent == null ? null : parent.get(name);
        return value == null || !value.isJsonPrimitive() ? "" : value.getAsString().trim();
    }
}
