package com.aresstack.enterpriseai.knowledge.lucene.archfixture.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Absichtlicher Verstoß: ein Adapter lädt eine Properties-Datei. */
public final class IndexLoadingProperties {

    public Properties load(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        return properties;
    }
}
