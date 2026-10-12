package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.chat.openai.DeveloperRolePolicy;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import java.net.URI;

/**
 * Chat gegen die interne GPT-kompatible Enterprise-API: Basis-URL, Modell und Verweis auf den API-Key. Der Key
 * selbst steht nie in der Konfiguration; {@link #apiKeyRef()} ist der Titel des KeePass-Eintrags.
 */
public final class ChatConfig {

    /**
     * Standard-System-Prompt des normalen Chats, wenn {@code chat.systemPrompt} fehlt oder leer ist; dieselbe
     * Fassung steht in der Beispielkonfiguration (und damit in jeder vom Einrichtungsassistenten neu angelegten
     * Datei) und als Startwert in den Einstellungen. Ein gesetzter Prompt bleibt unverändert.
     */
    public static final String DEFAULT_SYSTEM_PROMPT = ""
            + "Du bist ein hilfreicher Assistent. Antworte auf Deutsch, sofern der Benutzer keine andere Sprache verlangt.\n\n"
            + "Dir können Werkzeuge zum Durchsuchen und Lesen von Wissensquellen, Seiten und Anhängen zur Verfügung stehen. Nutze verfügbare Werkzeuge selbstständig, wenn sie für eine belastbare Antwort erforderlich sind.\n\n"
            + "Bei internen, organisationsspezifischen, projektbezogenen oder mehrdeutigen Begriffen und Abkürzungen bevorzuge verfügbare Wissensquellen gegenüber einer Antwort allein aus allgemeinem Modellwissen. Nimm bei Mehrdeutigkeit nicht einfach die allgemein bekannteste Bedeutung an.\n\n"
            + "Wenn die bereits gelesenen Quellen eine neue oder detailliertere Folgefrage nicht ausreichend beantworten, suche selbstständig erneut mit einer präziseren Suchanfrage. Warte nicht darauf, dass der Benutzer dich ausdrücklich zum erneuten Suchen auffordert.\n\n"
            + "Behaupte nicht, dass eine gesuchte Information in den verfügbaren Wissensquellen nicht vorhanden sei, bevor du – soweit passende Suchwerkzeuge verfügbar sind – gezielt nach der konkreten Information gesucht hast.\n\n"
            + "Verwende den bisherigen Gesprächskontext, um Suchanfragen zu präzisieren. Wenn zum Beispiel zunächst ein System beschrieben wurde und danach nach dem genauen Format eines darin vorkommenden Dateityps gefragt wird, suche gezielt nach diesem Dateityp, Aufbau, Schema, Feldern oder Format, statt nur bisherige Treffer erneut zusammenzufassen.\n\n"
            + "Wenn eine gezielte Suche keine ausreichenden Informationen liefert, sage klar, dass trotz Suche keine belastbare Antwort gefunden wurde. Erfinde keine fehlenden Details.\n\n"
            + "Erfinde niemals Werkzeugaufrufe, Werkzeugergebnisse, Quellen oder eigene Fähigkeiten. Behaupte keinen Modellnamen, Wissensstichtag oder verfügbare Werkzeuge, wenn dir diese Information nicht tatsächlich im aktuellen Laufzeitkontext bereitgestellt wurde.\n\n"
            + "Fragen nach deinen eigenen Fähigkeiten, Werkzeugen oder dem Client sind keine fachlichen Fragen an die Wissensbasis. Beantworte sie anhand der tatsächlich im aktuellen Laufzeitkontext verfügbaren Fähigkeiten und Werkzeuge.";

    private final URI baseUrl;
    private final String model;
    private final SecretRef apiKeyRef;
    private final String systemPrompt;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final DeveloperRolePolicy developerRolePolicy;
    private final ChatOptions defaultOptions;
    private final boolean toolsEnabled;
    private final int maxToolRounds;

    ChatConfig(URI baseUrl, String model, SecretRef apiKeyRef, String systemPrompt, int connectTimeoutMillis,
               int readTimeoutMillis, DeveloperRolePolicy developerRolePolicy, ChatOptions defaultOptions,
               boolean toolsEnabled, int maxToolRounds) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeyRef = apiKeyRef;
        this.systemPrompt = systemPrompt;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.developerRolePolicy = developerRolePolicy;
        this.defaultOptions = defaultOptions;
        this.toolsEnabled = toolsEnabled;
        this.maxToolRounds = maxToolRounds;
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

    /** System-Prompt der Chat-Konversation; ohne Eintrag {@link #DEFAULT_SYSTEM_PROMPT}. */
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
     * {@code chat.tools.enabled}: jede Frage läuft mit Werkzeugen über {@code /chat/completions}. Ohne den Schalter nur
     * Unterhaltungen mit Anhängen; alle anderen streamen wie bisher über {@code /chat/completions}.
     */
    public boolean toolsEnabled() {
        return toolsEnabled;
    }

    /**
     * {@code chat.tools.maxRounds} (1–32, Standard 8): Werkzeugrunden je Antwort im normalen Chat; danach antwortet
     * das Modell in einer Abschlussrunde ohne Werkzeuge aus den bisherigen Ergebnissen.
     */
    public int maxToolRounds() {
        return maxToolRounds;
    }

    @Override
    public String toString() {
        return "ChatConfig[baseUrl=" + baseUrl + ", model=" + model + ", apiKeyRef="
                + (apiKeyRef == null ? "keine" : apiKeyRef) + ", systemPrompt="
                + (systemPrompt == null ? "keiner" : systemPrompt.length() + " Zeichen") + ", developerRolePolicy="
                + developerRolePolicy + ", toolsEnabled=" + toolsEnabled + ", maxToolRounds=" + maxToolRounds + "]";
    }
}
