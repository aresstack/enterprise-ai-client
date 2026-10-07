package com.aresstack.enterpriseai.chat.openai;

import java.net.URI;

/**
 * Unveränderliche Konfiguration des Adapters für die interne GPT-kompatible Enterprise-API. Base-URL, Modell
 * und Token sind reine Konfiguration (Composition Root); nichts davon ist im Code fest verdrahtet.
 *
 * <p>Der Adapter sendet an {@code <baseUrl>/chat/completions}. Das Bearer-Token wird über {@link TokenSource} erst beim
 * Senden geholt und nirgends gespeichert oder ausgegeben; {@link #toString()} enthält es nie.
 */
public final class OpenAiCompatibleChatConfig {

    /**
     * Liefert das Bearer-Token je Anfrage; {@code null} oder leer bedeutet "ohne Authorization-Header".
     * Die Composition Root implementiert sie über den Security-Port (z. B. {@code SecretProvider}), damit dieser
     * Adapter security-api nicht kennen muss und der Klartext nur für die Dauer einer Anfrage existiert.
     */
    public interface TokenSource {
        String token();

        /** Fester Token, z. B. für Tests oder lokale Konfiguration. */
        static TokenSource fixed(final String token) {
            return new TokenSource() {
                @Override
                public String token() {
                    return token;
                }

                @Override
                public String toString() {
                    return "TokenSource[fixed, ***]";
                }
            };
        }
    }

    private final URI baseUrl;
    private final URI endpoint;
    private final String defaultModel;
    private final TokenSource tokenSource;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final DeveloperRolePolicy developerRolePolicy;

    private OpenAiCompatibleChatConfig(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.endpoint = resolve(builder.baseUrl, "chat/completions");
        this.defaultModel = builder.defaultModel;
        this.tokenSource = builder.tokenSource;
        this.connectTimeoutMillis = builder.connectTimeoutMillis;
        this.readTimeoutMillis = builder.readTimeoutMillis;
        this.developerRolePolicy = builder.developerRolePolicy;
    }

    /**
     * @param baseUrl Basis der API, z. B. {@code https://ki.intern/v1}; der Adapter hängt {@code chat/completions} an
     * @param defaultModel Modell, falls die Anfrage keines nennt (z. B. aus der Konfiguration)
     */
    public static Builder builder(URI baseUrl, String defaultModel) {
        return new Builder(baseUrl, defaultModel);
    }

    public URI baseUrl() {
        return baseUrl;
    }

    /** @return {@code <baseUrl>/chat/completions} */
    public URI endpoint() {
        return endpoint;
    }

    public String defaultModel() {
        return defaultModel;
    }

    TokenSource tokenSource() {
        return tokenSource;
    }

    public int connectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    /** @return maximale Wartezeit auf Daten (beim Streaming: zwischen zwei Chunks), 0 = unbegrenzt */
    public int readTimeoutMillis() {
        return readTimeoutMillis;
    }

    public DeveloperRolePolicy developerRolePolicy() {
        return developerRolePolicy;
    }

    @Override
    public String toString() {
        return "OpenAiCompatibleChatConfig[baseUrl=" + baseUrl + ", defaultModel=" + defaultModel
                + ", token=" + (tokenSource == null ? "none" : "***") + ", connectTimeoutMillis="
                + connectTimeoutMillis + ", readTimeoutMillis=" + readTimeoutMillis
                + ", developerRolePolicy=" + developerRolePolicy + "]";
    }

    public static final class Builder {

        private final URI baseUrl;
        private final String defaultModel;
        private TokenSource tokenSource;
        private int connectTimeoutMillis = 10000;
        private int readTimeoutMillis = 120000;
        private DeveloperRolePolicy developerRolePolicy = DeveloperRolePolicy.REJECT;

        private Builder(URI baseUrl, String defaultModel) {
            if (baseUrl == null || baseUrl.getScheme() == null
                    || !("http".equalsIgnoreCase(baseUrl.getScheme()) || "https".equalsIgnoreCase(baseUrl.getScheme()))) {
                throw new IllegalArgumentException("baseUrl must be an absolute http(s) URI");
            }
            if (baseUrl.getHost() == null || baseUrl.getHost().isEmpty()) {
                throw new IllegalArgumentException("baseUrl must name a host");
            }
            if (baseUrl.getUserInfo() != null) {
                throw new IllegalArgumentException("baseUrl must not contain user info; use the token source");
            }
            if (baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
                throw new IllegalArgumentException("baseUrl must not contain a query or fragment");
            }
            if (defaultModel == null || defaultModel.trim().isEmpty()) {
                throw new IllegalArgumentException("defaultModel must not be blank");
            }
            this.baseUrl = baseUrl;
            this.defaultModel = defaultModel.trim();
        }

        public Builder bearerToken(TokenSource source) {
            this.tokenSource = source;
            return this;
        }

        public Builder connectTimeoutMillis(int millis) {
            if (millis < 0) {
                throw new IllegalArgumentException("connect timeout must not be negative");
            }
            this.connectTimeoutMillis = millis;
            return this;
        }

        public Builder readTimeoutMillis(int millis) {
            if (millis < 0) {
                throw new IllegalArgumentException("read timeout must not be negative");
            }
            this.readTimeoutMillis = millis;
            return this;
        }

        public Builder developerRolePolicy(DeveloperRolePolicy policy) {
            if (policy == null) {
                throw new IllegalArgumentException("policy must not be null");
            }
            this.developerRolePolicy = policy;
            return this;
        }

        public OpenAiCompatibleChatConfig build() {
            return new OpenAiCompatibleChatConfig(this);
        }
    }

    private static URI resolve(URI base, String path) {
        String text = base.toString();
        return URI.create(text.endsWith("/") ? text + path : text + "/" + path);
    }
}
