package com.aresstack.enterpriseai.speech.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * VITS-Stimmen im Hugging-Face-Layout (MMS-TTS-Exporte): {@code config.json} mit {@code "model_type": "vits"},
 * {@code vocab.json}, optional {@code tokenizer_config.json}, {@code onnx/model.onnx} oder {@code model.onnx}.
 * Zeichen als Eingabe, kein Phonemizer.
 */
final class HuggingFaceVitsVoiceReader implements VoiceFormatReader {

    static final String FAMILY = "vits";
    private static final Logger LOG = Logger.getLogger(HuggingFaceVitsVoiceReader.class.getName());

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
            Map<String, Object> config = VoiceJson.read(configFile);
            if (!FAMILY.equals(String.valueOf(config.get("model_type")))) {
                return null;
            }
            int sampleRate = VoiceJson.intValue(config.get("sampling_rate"), 16000);
            Map<String, Integer> vocabulary = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Object> entry : VoiceJson.read(vocabFile).entrySet()) {
                if (entry.getValue() instanceof Number) {
                    vocabulary.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                }
            }
            if (vocabulary.isEmpty()) {
                return null;
            }
            boolean addBlank = true;
            boolean lowerCase = true;
            Path tokenizerFile = directory.resolve("tokenizer_config.json");
            if (Files.isRegularFile(tokenizerFile)) {
                Map<String, Object> tokenizer = VoiceJson.read(tokenizerFile);
                addBlank = VoiceJson.boolValue(tokenizer.get("add_blank"), true);
                lowerCase = VoiceJson.boolValue(tokenizer.get("normalize"), true);
            }
            VoiceTextEncoder encoder = new VitsCharacterEncoder(Collections.unmodifiableMap(vocabulary), addBlank,
                    lowerCase);
            return new LocalVoice(directory.getFileName().toString(), FAMILY, graph, sampleRate, encoder,
                    VoiceParameters.defaults());
        } catch (IOException | RuntimeException unreadable) {
            LOG.warning("Stimme in " + directory + " nicht lesbar: " + unreadable.getMessage());
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
