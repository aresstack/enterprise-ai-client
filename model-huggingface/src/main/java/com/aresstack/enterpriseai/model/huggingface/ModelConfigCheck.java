package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
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
 * Prüft die kleinen Konfigurationsdateien eines Modells, bevor der große Graph geladen wird, mit denselben Regeln
 * wie die Leser im Client. Stimmen: Piper ({@code .onnx.json}) mit {@code phoneme_type "text"} oder deutscher
 * espeak-Stimme, VITS ({@code config.json}) mit {@code model_type "vits"}. Embeddings und Chat: {@code config.json}
 * mit {@code hidden_size} (Chat zusätzlich Decoder: {@code num_hidden_layers}, kein Encoder-Decoder) und
 * {@code tokenizer.json} mit WordPiece, BPE oder Unigram. Andere Dateien prüft sie nicht.
 */
final class ModelConfigCheck {

    private ModelConfigCheck() {
    }

    static void check(Path file, ModelCategory category) throws LocalVoiceInstallException {
        if (category == ModelCategory.STT) {
            checkAudio(file);
            return;
        }
        if (category == ModelCategory.EMBEDDING || category == ModelCategory.CHAT) {
            checkText(file, category);
            return;
        }
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

    /**
     * Spracheingabe: {@code config.json} eines Gemma-4-Modells mit Audio-Teil ({@code audio_config}); die
     * Audio-Merkmale des Clients folgen Gemma 4. Tokenizer wie bei Text-Modellen.
     */
    private static void checkAudio(Path file) throws LocalVoiceInstallException {
        String name = file.getFileName().toString();
        if (name.equals("tokenizer.json")) {
            checkText(file, ModelCategory.CHAT);
            return;
        }
        if (!name.equals("config.json")) {
            return;
        }
        JsonObject json = read(file, name);
        String type = text(json, "model_type");
        if (object(json, "audio_config") == null) {
            throw new LocalVoiceInstallException("Modell \"" + type + "\" hat keinen Audio-Eingang (audio_config "
                    + "fehlt in config.json).");
        }
        if (!"gemma4".equals(type)) {
            throw new LocalVoiceInstallException("Spracheingabe mit model_type \"" + type + "\" wird nicht "
                    + "unterstützt; der Client rechnet die Audio-Merkmale von Gemma 4.");
        }
    }

    private static void checkText(Path file, ModelCategory category) throws LocalVoiceInstallException {
        String name = file.getFileName().toString();
        if (!name.equals("config.json") && !name.equals("tokenizer.json")) {
            return;
        }
        if (file.getParent() != null && file.getParent().getFileName() != null
                && file.getParent().getFileName().toString().endsWith("_Pooling")) {
            return;
        }
        JsonObject json = read(file, name);
        if (name.equals("tokenizer.json")) {
            String type = text(object(json, "model"), "type");
            if (type.isEmpty() && object(json, "model") != null) {
                return;
            }
            if (!"WordPiece".equals(type) && !"BPE".equals(type) && !"Unigram".equals(type)) {
                throw new LocalVoiceInstallException("Tokenizer-Modell \"" + type + "\" wird nicht unterstützt "
                        + "(nur WordPiece, BPE, Unigram).");
            }
            return;
        }
        if (number(json, "hidden_size") <= 0 && number(json, "d_model") <= 0) {
            throw new LocalVoiceInstallException("config.json nennt keine hidden_size; kein Transformer-Modell.");
        }
        if (category == ModelCategory.CHAT) {
            JsonElement encoderDecoder = json.get("is_encoder_decoder");
            if (encoderDecoder != null && encoderDecoder.isJsonPrimitive() && encoderDecoder.getAsBoolean()) {
                throw new LocalVoiceInstallException("Encoder-Decoder-Modelle werden für Chat nicht unterstützt.");
            }
            if (number(json, "num_hidden_layers") <= 0) {
                throw new LocalVoiceInstallException("config.json nennt keine num_hidden_layers; kein Decoder.");
            }
        }
    }

    private static JsonObject read(Path file, String name) throws LocalVoiceInstallException {
        try {
            return JsonParser.parseString(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new LocalVoiceInstallException(name + " ist kein lesbares JSON: " + e.getMessage(), e);
        }
    }

    private static double number(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        try {
            return value != null && value.isJsonPrimitive() ? value.getAsDouble() : 0;
        } catch (NumberFormatException e) {
            return 0;
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
