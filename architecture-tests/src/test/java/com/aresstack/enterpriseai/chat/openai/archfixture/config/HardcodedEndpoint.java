package com.aresstack.enterpriseai.chat.openai.archfixture.config;

/** Absichtlicher Verstoß (Nachtrag 1, 18): Base-URL, Modellname, API-Key und Bind-All-Adresse als Literale. */
public final class HardcodedEndpoint {

    public String endpoint() {
        return "https://api.openai.com/v1/chat/completions";
    }

    public String ipv6Endpoint() {
        return "http://[2001:db8::1]/v1/embeddings";
    }

    public String model() {
        return "openai/gpt-oss-120b";
    }

    public String key() {
        return "sk-abcdefghijklmnopqrstuvwxyz0123456789";
    }

    public String bind() {
        return "0.0.0.0";
    }
}
