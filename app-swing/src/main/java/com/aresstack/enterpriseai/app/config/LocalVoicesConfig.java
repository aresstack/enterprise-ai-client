package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoice;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Die kuratierten lokalen Stimmen aus {@code local-voices.properties} neben dieser Klasse (Pilot: deutsche Piper-
 * Stimmen aus {@code rhasspy/piper-voices}). Konfiguration statt Code: Repository, Revision und Dateien stehen nur
 * in der Datei. Ein fehlerhafter Eintrag fällt weg, statt den Start zu stören.
 */
public final class LocalVoicesConfig {

    static final String RESOURCE = "local-voices.properties";

    private LocalVoicesConfig() {
    }

    public static List<HuggingFaceVoice> curated() {
        Properties properties = new Properties();
        InputStream in = LocalVoicesConfig.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            return Collections.emptyList();
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return parse(properties);
    }

    static List<HuggingFaceVoice> parse(Properties properties) {
        List<HuggingFaceVoice> voices = new ArrayList<HuggingFaceVoice>();
        for (String id : split(properties.getProperty("voices", ""))) {
            String prefix = "voice." + id + ".";
            try {
                voices.add(new HuggingFaceVoice(id, properties.getProperty(prefix + "name"),
                        properties.getProperty(prefix + "language"), properties.getProperty(prefix + "repository"),
                        properties.getProperty(prefix + "revision"),
                        split(properties.getProperty(prefix + "files", ""))));
            } catch (IllegalArgumentException invalid) {
                // unvollständiger Eintrag: nicht anbieten
            }
        }
        return voices;
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<String>();
        for (String part : Arrays.asList(value.split(","))) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }
}
