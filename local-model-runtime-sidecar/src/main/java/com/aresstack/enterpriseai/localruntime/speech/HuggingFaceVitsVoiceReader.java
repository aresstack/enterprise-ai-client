package com.aresstack.enterpriseai.localruntime.speech;

import com.aresstack.enterpriseai.localruntime.LocalJsonAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VITS voices in the Hugging Face layout (MMS-TTS exports): {@code config.json} with {@code "model_type": "vits"},
 * {@code vocab.json}, optional {@code tokenizer_config.json}, {@code onnx/model.onnx} or {@code model.onnx}.
 * Character input, no phonemizer.
 */
final class HuggingFaceVitsVoiceReader implements VoiceFormatReader {

    static final String FAMILY = "vits";

    @Override
    public LocalVoice read(Path directory) {
        Path configFile = directory.resolve("config.json");
        Path vocabFile = directory.resolve("vocab.json");
        if (!Files.isRegularFile(configFile) || !Files.isRegularFile(vocabFile)) {
            return null;
        }
        Path graph = firstExisting(directory.resolve("onnx").resolve("model.onnx"), directory.resolve("model.onnx"));
        if (graph == null) {
            return null;
        }
        try {
            Map<String, Object> config = LocalJsonAccess.object(Files.readString(configFile));
            if (!FAMILY.equals(String.valueOf(config.get("model_type")))) {
                return null;
            }
            int sampleRate = VoiceJson.intValue(config.get("sampling_rate"), 16000);
            Map<String, Integer> vocabulary = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : LocalJsonAccess.object(Files.readString(vocabFile)).entrySet()) {
                if (entry.getValue() instanceof Number number) {
                    vocabulary.put(entry.getKey(), number.intValue());
                }
            }
            if (vocabulary.isEmpty()) {
                return null;
            }
            boolean addBlank = true;
            boolean lowerCase = true;
            Path tokenizerFile = directory.resolve("tokenizer_config.json");
            if (Files.isRegularFile(tokenizerFile)) {
                Map<String, Object> tokenizer = LocalJsonAccess.object(Files.readString(tokenizerFile));
                addBlank = VoiceJson.boolValue(tokenizer.get("add_blank"), true);
                lowerCase = VoiceJson.boolValue(tokenizer.get("normalize"), true);
            }
            VoiceTextEncoder encoder = new VitsCharacterEncoder(Collections.unmodifiableMap(vocabulary), addBlank,
                    lowerCase);
            return new LocalVoice(directory.getFileName().toString(), FAMILY, directory, graph, sampleRate, encoder,
                    VoiceParameters.DEFAULT);
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("[local-runtime] unreadable voice in " + directory + ": " + unreadable.getMessage());
            return null;
        }
    }

    private static Path firstExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
