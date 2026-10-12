package com.aresstack.enterpriseai.proasteion.application.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Holds the preferred chat and embedding model of the setup wizard, read from {@code setup-models.properties} next to
 * this class. Use them only while nothing is chosen yet; model names are configuration, so keep them out of the code.
 */
public final class PreferredModels {

    static final String RESOURCE = "setup-models.properties";

    private final Properties properties;

    PreferredModels(Properties properties) {
        this.properties = properties;
    }

    /** Read the preferred models of the bundled file. */
    public static PreferredModels bundled() {
        Properties properties = new Properties();
        InputStream in = PreferredModels.class.getResourceAsStream(RESOURCE);
        if (in != null) {
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                properties = new Properties();
            }
        }
        return new PreferredModels(properties);
    }

    /** Return the preferred model of a kind ({@code chat}, {@code embedding}, {@code tts}) or an empty string. */
    public String model(String kind) {
        return properties.getProperty(kind, "").trim();
    }

    /**
     * Return the words a fallback model id must all contain ({@code <kind>.match}); empty means the part of the
     * preferred id after its last slash.
     */
    public List<String> match(String kind) {
        List<String> words = new ArrayList<String>();
        for (String word : properties.getProperty(kind + ".match", "").split(",")) {
            if (!word.trim().isEmpty()) {
                words.add(word.trim());
            }
        }
        return Collections.unmodifiableList(words);
    }
}
