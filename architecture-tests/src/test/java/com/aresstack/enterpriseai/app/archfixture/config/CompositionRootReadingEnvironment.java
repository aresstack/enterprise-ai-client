package com.aresstack.enterpriseai.app.archfixture.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Gegenprobe: die Composition Root darf Umgebung und Properties lesen. */
public final class CompositionRootReadingEnvironment {

    public Properties load(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        properties.put("home", System.getenv("HOME"));
        return properties;
    }
}
