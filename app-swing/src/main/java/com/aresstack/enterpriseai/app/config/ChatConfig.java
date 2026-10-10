package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.chat.openai.DeveloperRolePolicy;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.security.SecretRef;

import java.net.URI;

/**
 * Chat gegen die interne GPT-kompatible Enterprise-API: Basis-URL, Modell und Verweis auf den API-Key. Der Key
 * selbst steht nie in der Konfiguration; {@link #apiKeyRef()} ist der Titel des KeePass-Eintrags.
 */
public final class ChatConfig {

    private final URI baseUrl;
    private final String model;
    private final SecretRef apiKeyRef;
    private final String systemPrompt;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final DeveloperRolePolicy developerRolePolicy;
    private final ChatOptions defaultOptions;
    private final boolean toolsEnabled;

    ChatConfig(URI baseUrl, String model, SecretRef apiKeyRef, String systemPrompt, int connectTimeoutMillis,
               int readTimeoutMillis, DeveloperRolePolicy developerRolePolicy, ChatOptions defaultOptions,
               boolean toolsEnabled) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeyRef = apiKeyRef;
        this.systemPrompt = systemPrompt;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.developerRolePolicy = developerRolePolicy;
        this.defaultOptions = defaultOptions;
        this.toolsEnabled = toolsEnabled;
    }

    /** Basis der API, z. B. {@code https://ki.intern.example/v1}; der Adapter hängt {@code chat/completions} an. */
    public URI baseUrl() {
        return baseUrl;
    }

    public String model() {
        return model;
    }

    /** Verweis auf den API-Key im Security-Backend (Pflichtangabe; der Loader lehnt eine Konfiguration ohne ihn ab). */
    public SecretRef apiKeyRef() {
        return apiKeyRef;
    }

    /** System-Prompt der Chat-Konversation oder {@code null}. */
    public String systemPrompt() {
        return systemPrompt;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    public DeveloperRolePolicy developerRolePolicy() {
        return developerRolePolicy;
    }

    /** Standardparameter (temperature, top_p, ...) jedes Turns; Modell kommt aus {@link #model()}. */
    public ChatOptions defaultOptions() {
        return defaultOptions;
    }

    /**
     * {@code chat.tools.enabled}: jede Frage läuft mit Werkzeugen über {@code /responses}. Ohne den Schalter nur
     * Unterhaltungen mit Anhängen; alle anderen streamen wie bisher über {@code /chat/completions}.
     */
    public boolean toolsEnabled() {
        return toolsEnabled;
    }

    @Override
    public String toString() {
        return "ChatConfig[baseUrl=" + baseUrl + ", model=" + model + ", apiKeyRef="
                + (apiKeyRef == null ? "keine" : apiKeyRef) + ", systemPrompt="
                + (systemPrompt == null ? "keiner" : systemPrompt.length() + " Zeichen") + ", developerRolePolicy="
                + developerRolePolicy + ", toolsEnabled=" + toolsEnabled + "]";
    }
}
