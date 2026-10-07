package com.aresstack.enterpriseai.chat.openai.archfixture.config;

/** Absichtlicher Verstoß: ein Adapter liest Umgebungsvariablen statt Konfiguration per Konstruktor. */
public final class AdapterReadingEnvironment {

    public String apiKey() {
        return System.getenv("API_KEY");
    }
}
