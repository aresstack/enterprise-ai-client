package com.aresstack.enterpriseai.localruntime.speech;

import com.aresstack.enterpriseai.localruntime.LocalJsonAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Stateless view of the text-to-speech voices below the model root: every subdirectory with a VITS
 * {@code config.json}, a {@code vocab.json} and an ONNX graph is one voice. Scanned per request like the
 * manifest models, so a voice copied into the folder appears on the next {@code /api/tags}. No download,
 * no installer: the user places the exported model there himself.
 */
public final class LocalSpeechModelStore {

    private static final String MODEL_TYPE = "vits";

    private final Path modelRoot;

    public LocalSpeechModelStore(Path modelRoot) {
        this.modelRoot = modelRoot;
    }

    public List<LocalSpeechModel> models() {
        List<LocalSpeechModel> models = new ArrayList<>();
        if (!Files.isDirectory(modelRoot)) {
            return models;
        }
        try (Stream<Path> children = Files.list(modelRoot)) {
            children.filter(Files::isDirectory).sorted().forEach(dir -> {
                LocalSpeechModel model = read(dir);
                if (model != null) {
                    models.add(model);
                }
            });
        } catch (IOException ex) {
            System.err.println("[local-runtime] cannot scan model root for voices " + modelRoot + ": "
                    + ex.getMessage());
        }
        return models;
    }

    public LocalSpeechModel find(String virtualName) {
        if (virtualName == null) {
            return null;
        }
        for (LocalSpeechModel model : models()) {
            if (model.virtualName().equals(virtualName)) {
                return model;
            }
        }
        return null;
    }

    static LocalSpeechModel read(Path directory) {
        Path configFile = directory.resolve("config.json");
        Path vocabFile = directory.resolve("vocab.json");
        if (!Files.isRegularFile(configFile) || !Files.isRegularFile(vocabFile)) {
            return null;
        }
        Path onnx = firstExisting(directory.resolve("onnx").resolve("model.onnx"), directory.resolve("model.onnx"));
        if (onnx == null) {
            return null;
        }
        try {
            Map<String, Object> config = LocalJsonAccess.object(Files.readString(configFile));
            if (!MODEL_TYPE.equals(String.valueOf(config.get("model_type")))) {
                return null;
            }
            int sampleRate = intValue(config.get("sampling_rate"), 16000);
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
                addBlank = boolValue(tokenizer.get("add_blank"), true);
                lowerCase = boolValue(tokenizer.get("normalize"), true);
            }
            return new LocalSpeechModel(directory.getFileName().toString(), directory, onnx, sampleRate,
                    Collections.unmodifiableMap(vocabulary), addBlank, lowerCase);
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

    private static int intValue(Object value, int fallback) {
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : fallback;
    }

    private static boolean boolValue(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }
}
