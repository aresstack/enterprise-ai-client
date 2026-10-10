package com.aresstack.enterpriseai.speech.local;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Piper-Stimmen wie in {@code rhasspy/piper-voices}: ein Ordner mit {@code <name>.onnx} und {@code <name>.onnx.json}.
 * Aus dem JSON: {@code audio.sample_rate}, {@code phoneme_type}, {@code espeak.voice}, {@code phoneme_id_map},
 * {@code phoneme_map}, {@code inference} und {@code num_speakers}.
 *
 * <p>Piper-Stimmen nehmen Phonem-Ids. {@code phoneme_type "text"} nimmt die Zeichen selbst. {@code "espeak"}-Stimmen
 * sind auf espeak-ng-Phoneme trainiert; ohne externes Programm gibt es nur deutsche, phonemisiert vom regelbasierten
 * {@link GermanPhonemizer} (Näherung an espeak-ng). Andere espeak-Sprachen werden übersprungen statt Unsinn zu sprechen.
 */
final class PiperVoiceReader implements VoiceFormatReader {

    static final String FAMILY = "piper";
    private static final String CONFIG_SUFFIX = ".onnx.json";
    private static final Logger LOG = Logger.getLogger(PiperVoiceReader.class.getName());

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
            Map<String, Object> config = VoiceJson.read(configFile);
            Map<String, List<Integer>> idMap = idMap(VoiceJson.object(config.get("phoneme_id_map")));
            if (idMap.isEmpty()) {
                LOG.warning("Piper-Stimme ohne phoneme_id_map in " + directory);
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
            VoiceParameters defaults = VoiceParameters.DEFAULT;
            VoiceParameters parameters = new VoiceParameters(
                    VoiceJson.floatValue(inference.get("noise_scale"), defaults.noiseScale),
                    VoiceJson.floatValue(inference.get("length_scale"), defaults.lengthScale),
                    VoiceJson.floatValue(inference.get("noise_w"), defaults.noiseWidth),
                    speakers > 1 ? 0L : -1L);
            return new LocalVoice(directory.getFileName().toString(), FAMILY, graph, sampleRate,
                    new PiperPhonemeEncoder(phonemizer, idMap, phonemeMap), parameters);
        } catch (IOException | RuntimeException unreadable) {
            LOG.warning("Piper-Stimme in " + directory + " nicht lesbar: " + unreadable.getMessage());
            return null;
        }
    }

    private static Path configFile(Path directory) {
        List<Path> candidates = new ArrayList<Path>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(CONFIG_SUFFIX)) {
                    candidates.add(file);
                }
            }
        } catch (IOException unreadable) {
            return null;
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.sort(candidates);
        return candidates.get(0);
    }

    private static Phonemizer phonemizer(Path directory, Map<String, Object> config) {
        String type = VoiceJson.text(config.get("phoneme_type"));
        if ("text".equals(type)) {
            return new CharacterPhonemizer();
        }
        if (!type.isEmpty() && !"espeak".equals(type)) {
            LOG.warning("Piper-Stimme " + directory.getFileName() + " hat phoneme_type '" + type
                    + "', nicht unterstützt");
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
        LOG.warning("Piper-Stimme " + directory.getFileName() + " braucht espeak-ng-Phoneme für '" + language
                + "'; ohne externes Programm nur deutsche Stimmen");
        return null;
    }

    private static Map<String, List<Integer>> idMap(Map<String, Object> raw) {
        Map<String, List<Integer>> ids = new LinkedHashMap<String, List<Integer>>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            List<Integer> values = new ArrayList<Integer>();
            if (entry.getValue() instanceof List) {
                for (Object value : (List<?>) entry.getValue()) {
                    if (value instanceof Number) {
                        values.add(((Number) value).intValue());
                    }
                }
            } else if (entry.getValue() instanceof Number) {
                values.add(((Number) entry.getValue()).intValue());
            }
            if (!values.isEmpty()) {
                ids.put(entry.getKey(), Collections.unmodifiableList(values));
            }
        }
        return Collections.unmodifiableMap(ids);
    }

    private static Map<String, List<String>> phonemeMap(Map<String, Object> raw) {
        Map<String, List<String>> map = new LinkedHashMap<String, List<String>>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            List<String> values = new ArrayList<String>();
            if (entry.getValue() instanceof List) {
                for (Object value : (List<?>) entry.getValue()) {
                    values.add(String.valueOf(value));
                }
            }
            map.put(entry.getKey(), Collections.unmodifiableList(values));
        }
        return Collections.unmodifiableMap(map);
    }
}
