package com.aresstack.enterpriseai.proasteion.application.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Die Stimmen (Sprecher) der TTS-Modelle aus {@code tts-voices.properties} neben dieser Klasse: je Modellfamilie
 * männliche und weibliche Stimmen und eine Standardstimme. Welche Familie ein Modell ist, entscheiden Teilwörter seiner
 * Kennung. Gültige Stimmen hängen vom Modell ab, deshalb gibt es keine globale Stimme: die Auswahl wird je Modell
 * gespeichert ({@link ModelsConfig#speechVoice(com.aresstack.enterpriseai.astu.modelcatalog.ModelReference)}).
 * Die Namen sind gegen die Enterprise-API ungeprüft; ein fehlerhafter Eintrag fällt weg.
 */
public final class TtsVoiceCatalog {

    static final String RESOURCE = "tts-voices.properties";

    /** Stimmlage einer Stimme. */
    public enum Gender {
        MALE, FEMALE
    }

    private final List<Family> families;

    TtsVoiceCatalog(Properties properties) {
        List<Family> parsed = new ArrayList<Family>();
        for (String id : split(properties.getProperty("families", ""))) {
            List<String> match = new ArrayList<String>();
            for (String token : split(properties.getProperty(id + ".match", ""))) {
                match.add(token.toLowerCase(Locale.ROOT));
            }
            List<String> male = split(properties.getProperty(id + ".male", ""));
            List<String> female = split(properties.getProperty(id + ".female", ""));
            String fallback = properties.getProperty(id + ".default", "").trim();
            if (match.isEmpty() || (male.isEmpty() && female.isEmpty())) {
                continue;
            }
            parsed.add(new Family(id, match, male, female, fallback));
        }
        this.families = Collections.unmodifiableList(parsed);
    }

    /** Die Stimmen der mitgelieferten Datei. */
    public static TtsVoiceCatalog bundled() {
        Properties properties = new Properties();
        InputStream in = TtsVoiceCatalog.class.getResourceAsStream(RESOURCE);
        if (in != null) {
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                properties = new Properties();
            }
        }
        return new TtsVoiceCatalog(properties);
    }

    /** Alle bekannten Stimmen des Modells (Standard zuerst); leer, wenn die Familie unbekannt ist. */
    public List<String> voices(String modelId) {
        Family family = family(modelId);
        if (family == null) {
            return Collections.emptyList();
        }
        List<String> all = new ArrayList<String>();
        if (!family.fallback.isEmpty()) {
            all.add(family.fallback);
        }
        for (String voice : family.all()) {
            if (!all.contains(voice)) {
                all.add(voice);
            }
        }
        return Collections.unmodifiableList(all);
    }

    /** Die Standardstimme des Modells oder {@code null}, wenn die Familie unbekannt ist. */
    public String defaultVoice(String modelId) {
        Family family = family(modelId);
        if (family == null) {
            return null;
        }
        if (!family.fallback.isEmpty()) {
            return family.fallback;
        }
        return family.all().get(0);
    }

    /**
     * Die erste Stimme der Stimmlage, die Standardstimme zuerst, falls sie passt; {@code null}, wenn das Modell keine
     * solche Stimme kennt (z. B. der Zauberer: männlich, sonst lieber stumm als eine fremde Stimme).
     */
    public String voice(String modelId, Gender gender) {
        Family family = family(modelId);
        if (family == null) {
            return null;
        }
        List<String> voices = gender == Gender.MALE ? family.male : family.female;
        if (voices.contains(family.fallback)) {
            return family.fallback;
        }
        return voices.isEmpty() ? null : voices.get(0);
    }

    /** Die Stimmlage einer bekannten Stimme des Modells oder {@code null}. */
    public Gender genderOf(String modelId, String voice) {
        Family family = family(modelId);
        if (family == null || voice == null) {
            return null;
        }
        if (family.male.contains(voice.trim())) {
            return Gender.MALE;
        }
        return family.female.contains(voice.trim()) ? Gender.FEMALE : null;
    }

    /** Ob das Modell einer bekannten Familie angehört. */
    public boolean knows(String modelId) {
        return family(modelId) != null;
    }

    private Family family(String modelId) {
        if (modelId == null) {
            return null;
        }
        String id = modelId.toLowerCase(Locale.ROOT);
        Family best = null;
        int bestLength = 0;
        for (Family family : families) {
            for (String token : family.match) {
                if (id.contains(token) && token.length() > bestLength) {
                    best = family;
                    bestLength = token.length();
                }
            }
        }
        return best;
    }

    private static List<String> split(String text) {
        List<String> parts = new ArrayList<String>();
        for (String part : text.split(",")) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    @Override
    public String toString() {
        Map<String, Integer> sizes = new LinkedHashMap<String, Integer>();
        for (Family family : families) {
            sizes.put(family.id, family.all().size());
        }
        return "TtsVoiceCatalog" + sizes;
    }

    private static final class Family {

        final String id;
        final List<String> match;
        final List<String> male;
        final List<String> female;
        final String fallback;

        Family(String id, List<String> match, List<String> male, List<String> female, String fallback) {
            this.id = id;
            this.match = Collections.unmodifiableList(match);
            this.male = Collections.unmodifiableList(male);
            this.female = Collections.unmodifiableList(female);
            this.fallback = fallback;
        }

        List<String> all() {
            List<String> all = new ArrayList<String>(male);
            all.addAll(female);
            return all;
        }
    }
}
