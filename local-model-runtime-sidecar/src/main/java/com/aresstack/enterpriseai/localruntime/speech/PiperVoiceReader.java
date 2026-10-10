package com.aresstack.enterpriseai.localruntime.speech;

import com.aresstack.enterpriseai.localruntime.LocalJsonAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Piper voices as published in {@code rhasspy/piper-voices}: a directory with {@code <name>.onnx} and
 * {@code <name>.onnx.json}. Read from the JSON: {@code audio.sample_rate}, {@code phoneme_type},
 * {@code espeak.voice}, {@code phoneme_id_map}, {@code phoneme_map}, {@code inference} and {@code num_speakers}.
 *
 * <p>Piper voices take phoneme ids. Voices with {@code phoneme_type "text"} take the characters themselves.
 * {@code "espeak"} voices were trained on espeak-ng phonemes; the sidecar runs no external program, so only
 * German espeak voices are offered, phonemized by the built-in rule-based {@link GermanPhonemizer} (an
 * approximation of espeak-ng, not espeak-ng itself). Espeak voices of other languages are skipped with a line on
 * stderr instead of speaking nonsense.
 */
final class PiperVoiceReader implements VoiceFormatReader {

    static final String FAMILY = "piper";
    private static final String CONFIG_SUFFIX = ".onnx.json";

    @Override
    public LocalVoice read(Path directory) {
        Path configFile = configFile(directory);
        if (configFile == null) {
            return null;
        }
        String fileName = configFile.getFileName().toString();
        Path graph = directory.resolve(fileName.substring(0, fileName.length() - ".json".length()));
        if (!Files.isRegularFile(graph)) {
            return null;
        }
        try {
            Map<String, Object> config = LocalJsonAccess.object(Files.readString(configFile));
            Map<String, List<Integer>> idMap = idMap(VoiceJson.object(config.get("phoneme_id_map")));
            if (idMap.isEmpty()) {
                System.err.println("[local-runtime] Piper voice without phoneme_id_map in " + directory);
                return null;
            }
            Phonemizer phonemizer = phonemizer(directory, config);
            if (phonemizer == null) {
                return null;
            }
            Map<String, List<String>> phonemeMap = phonemeMap(VoiceJson.object(config.get("phoneme_map")));
            int sampleRate = VoiceJson.intValue(VoiceJson.object(config.get("audio")).get("sample_rate"), 22050);
            Map<String, Object> inference = VoiceJson.object(config.get("inference"));
            int speakers = VoiceJson.intValue(config.get("num_speakers"), 1);
            VoiceParameters parameters = new VoiceParameters(
                    VoiceJson.floatValue(inference.get("noise_scale"), VoiceParameters.DEFAULT.noiseScale()),
                    VoiceJson.floatValue(inference.get("length_scale"), VoiceParameters.DEFAULT.lengthScale()),
                    VoiceJson.floatValue(inference.get("noise_w"), VoiceParameters.DEFAULT.noiseWidth()),
                    speakers > 1 ? 0L : -1L);
            return new LocalVoice(directory.getFileName().toString(), FAMILY, directory, graph, sampleRate,
                    new PiperPhonemeEncoder(phonemizer, idMap, phonemeMap), parameters);
        } catch (IOException | RuntimeException unreadable) {
            System.err.println("[local-runtime] unreadable Piper voice in " + directory + ": "
                    + unreadable.getMessage());
            return null;
        }
    }

    private static Path configFile(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(CONFIG_SUFFIX))
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (IOException unreadable) {
            return null;
        }
    }

    private static Phonemizer phonemizer(Path directory, Map<String, Object> config) {
        String type = VoiceJson.text(config.get("phoneme_type"));
        if ("text".equals(type)) {
            return new CharacterPhonemizer();
        }
        if (!type.isEmpty() && !"espeak".equals(type)) {
            System.err.println("[local-runtime] Piper voice " + directory.getFileName() + " has phoneme_type '"
                    + type + "', not supported");
            return null;
        }
        String language = VoiceJson.text(VoiceJson.object(config.get("espeak")).get("voice"));
        if (language.isEmpty()) {
            language = VoiceJson.text(VoiceJson.object(config.get("language")).get("code"));
        }
        String normalized = language.toLowerCase(Locale.ROOT);
        if (normalized.equals("de") || normalized.startsWith("de-") || normalized.startsWith("de_")) {
            return new GermanPhonemizer();
        }
        System.err.println("[local-runtime] Piper voice " + directory.getFileName() + " needs espeak-ng phonemes for '"
                + language + "'; without an external program only German voices are supported");
        return null;
    }

    private static Map<String, List<Integer>> idMap(Map<String, Object> raw) {
        Map<String, List<Integer>> ids = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            List<Integer> values = new ArrayList<>();
            if (entry.getValue() instanceof List<?> list) {
                for (Object value : list) {
                    if (value instanceof Number number) {
                        values.add(number.intValue());
                    }
                }
            } else if (entry.getValue() instanceof Number number) {
                values.add(number.intValue());
            }
            if (!values.isEmpty()) {
                ids.put(entry.getKey(), Collections.unmodifiableList(values));
            }
        }
        return Collections.unmodifiableMap(ids);
    }

    private static Map<String, List<String>> phonemeMap(Map<String, Object> raw) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            List<String> values = new ArrayList<>();
            if (entry.getValue() instanceof List<?> list) {
                for (Object value : list) {
                    values.add(String.valueOf(value));
                }
            }
            map.put(entry.getKey(), Collections.unmodifiableList(values));
        }
        return Collections.unmodifiableMap(map);
    }
}
