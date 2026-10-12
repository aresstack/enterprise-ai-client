package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.chat.openai.DeveloperRolePolicy;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlanningPolicy;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;

import java.net.URI;

/**
 * Chat gegen die interne GPT-kompatible Enterprise-API: Basis-URL, Modell und Verweis auf den API-Key. Der Key
 * selbst steht nie in der Konfiguration; {@link #apiKeyRef()} ist der Titel des KeePass-Eintrags.
 */
public final class ChatConfig {

    /** {@code chat.connectTimeoutMillis}: Frist für den Verbindungsaufbau. */
    public static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;
    /** {@code chat.readTimeoutMillis}: Frist zwischen zwei empfangenen Datenstücken. */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 120_000;
    /** {@code chat.retryCount}: Wiederholungen einer Modellanfrage nach einem vorübergehenden Fehler. */
    public static final int DEFAULT_RETRY_COUNT = 2;
    /** {@code chat.retryDelayMillis}: Pause vor der ersten Wiederholung (jede weitere länger). */
    public static final int DEFAULT_RETRY_DELAY_MILLIS = 500;

    /**
     * Standard-System-Prompt des normalen Chats, wenn {@code chat.systemPrompt} fehlt oder leer ist; dieselbe
     * Fassung steht in der Beispielkonfiguration (und damit in jeder vom Einrichtungsassistenten neu angelegten
     * Datei) und als Startwert in den Einstellungen. Ein gesetzter Prompt bleibt unverändert.
     */
    public static final String DEFAULT_SYSTEM_PROMPT = ""
            + "Du bist ein hilfreicher Assistent. Antworte auf Deutsch, sofern der Benutzer keine andere Sprache verlangt.\n\n"
            + "Dir können Werkzeuge zum Durchsuchen und Lesen von Wissensquellen, Seiten und Anhängen zur Verfügung stehen. Nutze verfügbare Werkzeuge selbstständig, wenn sie für eine belastbare Antwort erforderlich sind.\n\n"
            + "Der Client hängt an die aktuelle Nachricht einen vorab erstellten Antwortplan (<client-answer-plan>: Original, wahrscheinlich gemeinte Nachricht, Absicht, Informationsbedarf, Sicherheit, geplanter nächster Schritt) oder eine automatische Analyse (<client-turn-analysis>): nutze sie als Hinweis, nicht als Wahrheit; die Nachricht des Nutzers bleibt das Original. Entscheide anhand der Äußerung, des Gesprächsverlaufs und der verfügbaren Werkzeuge, ob zusätzliche Informationen nötig sind. Kein Werkzeug nur deshalb, weil ein Begriff unbekannt ist. Fragen zu diesem Programm beantwortest du mit der eingebauten Programmhilfe. Will der Nutzer etwas über einen Begriff wissen, der im Kontext nicht eindeutig aufgelöst ist und organisationsspezifisch sein könnte, sieh in den Wissensquellen nach, auch wenn du eine öffentliche Bedeutung kennst. Bei einer auffälligen Spracheingabe prüfe zuerst eine Fehlerkennung.\n\n"
            + "Bei internen, organisationsspezifischen, projektbezogenen oder mehrdeutigen Begriffen und Abkürzungen bevorzuge verfügbare Wissensquellen gegenüber einer Antwort allein aus allgemeinem Modellwissen. Nimm bei Mehrdeutigkeit nicht einfach die allgemein bekannteste Bedeutung an.\n\n"
            + "Wenn die bereits gelesenen Quellen eine neue oder detailliertere Folgefrage nicht ausreichend beantworten, suche selbstständig erneut mit einer präziseren Suchanfrage. Warte nicht darauf, dass der Benutzer dich ausdrücklich zum erneuten Suchen auffordert.\n\n"
            + "Behaupte nicht, dass eine gesuchte Information in den verfügbaren Wissensquellen nicht vorhanden sei, bevor du – soweit passende Suchwerkzeuge verfügbar sind – gezielt nach der konkreten Information gesucht hast.\n\n"
            + "Verwende den bisherigen Gesprächskontext, um Suchanfragen zu präzisieren. Wenn zum Beispiel zunächst ein System beschrieben wurde und danach nach dem genauen Format eines darin vorkommenden Dateityps gefragt wird, suche gezielt nach diesem Dateityp, Aufbau, Schema, Feldern oder Format, statt nur bisherige Treffer erneut zusammenzufassen.\n\n"
            + "Wenn eine gezielte Suche keine ausreichenden Informationen liefert, sage klar, dass trotz Suche keine belastbare Antwort gefunden wurde. Erfinde keine fehlenden Details.\n\n"
            + "Erfinde niemals Werkzeugaufrufe, Werkzeugergebnisse, Quellen oder eigene Fähigkeiten. Behaupte keinen Modellnamen, Wissensstichtag oder verfügbare Werkzeuge, wenn dir diese Information nicht tatsächlich im aktuellen Laufzeitkontext bereitgestellt wurde.\n\n"
            + "Fragen nach deinen eigenen Fähigkeiten, Werkzeugen oder dem Client sind keine fachlichen Fragen an die Wissensbasis. Beantworte sie anhand der tatsächlich im aktuellen Laufzeitkontext verfügbaren Fähigkeiten und Werkzeuge.\n\n"
            + "Fragen zur Bedienung, zu Funktionen, Einstellungen, Einrichtung oder Fehlern dieses Clients beantworte mit der eingebauten Programmhilfe (zuerst app_help_search, dann app_help_read), sofern diese Werkzeuge verfügbar sind und ein Werkzeugergebnis der aktuellen Anfrage die Antwort nicht schon liefert. Verlasse dich dabei nicht auf Annahmen über Menüs oder Verhalten des Programms.";

    /** Schlüssel des Anzeigenamens, mit dem Antworten im Verlauf und in Exporten überschrieben sind. */
    public static final String ASSISTANT_DISPLAY_NAME_KEY = "chat.assistant.displayName";

    /** Diagnostic log of the linguistic analysis per turn; off by default. */
    public static final String TURN_ANALYSIS_LOG_KEY = "chat.diagnostics.turnAnalysis";

    /** Whether every chat turn runs the answer planning step before the answer; on by default. */
    public static final String ANSWER_PLANNING_ENABLED_KEY = "chat.answerPlanning.enabled";
    /** Confidence in percent from which the reading of the answer planning step is used. */
    public static final String ANSWER_PLANNING_USE_AT_KEY = "chat.answerPlanning.useAtPercent";
    /** Confidence in percent below which the answer asks back instead of acting on the reading. */
    public static final String ANSWER_PLANNING_ASK_BELOW_KEY = "chat.answerPlanning.askBelowPercent";

    /**
     * Anzeigename ohne Eintrag: „Merlins Tochter“ passt zur weiblichen Standardstimme (Qwen3-TTS „Vivian“), während Merlin
     * die Figur des Einrichtungsassistenten bleibt. Nur Beschriftung; das Modell erfährt den Namen nicht, und ein
     * Wechsel der Stimme ändert ihn nie.
     */
    public static final String DEFAULT_ASSISTANT_DISPLAY_NAME = "Merlins Tochter";

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
    private final RetryPolicy retry;
    private final String assistantDisplayName;
    private final boolean turnAnalysisLog;
    private final AnswerPlanningPolicy answerPlanning;

    ChatConfig(URI baseUrl, String model, SecretRef apiKeyRef, String systemPrompt, int connectTimeoutMillis,
               int readTimeoutMillis, DeveloperRolePolicy developerRolePolicy, ChatOptions defaultOptions,
               boolean toolsEnabled, int maxToolRounds, RetryPolicy retry, String assistantDisplayName) {
        this(baseUrl, model, apiKeyRef, systemPrompt, connectTimeoutMillis, readTimeoutMillis, developerRolePolicy,
                defaultOptions, toolsEnabled, maxToolRounds, retry, assistantDisplayName, false);
    }

    ChatConfig(URI baseUrl, String model, SecretRef apiKeyRef, String systemPrompt, int connectTimeoutMillis,
               int readTimeoutMillis, DeveloperRolePolicy developerRolePolicy, ChatOptions defaultOptions,
               boolean toolsEnabled, int maxToolRounds, RetryPolicy retry, String assistantDisplayName,
               boolean turnAnalysisLog) {
        this(baseUrl, model, apiKeyRef, systemPrompt, connectTimeoutMillis, readTimeoutMillis, developerRolePolicy,
                defaultOptions, toolsEnabled, maxToolRounds, retry, assistantDisplayName, turnAnalysisLog,
                AnswerPlanningPolicy.defaults());
    }

    ChatConfig(URI baseUrl, String model, SecretRef apiKeyRef, String systemPrompt, int connectTimeoutMillis,
               int readTimeoutMillis, DeveloperRolePolicy developerRolePolicy, ChatOptions defaultOptions,
               boolean toolsEnabled, int maxToolRounds, RetryPolicy retry, String assistantDisplayName,
               boolean turnAnalysisLog, AnswerPlanningPolicy answerPlanning) {
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
        this.retry = retry == null ? RetryPolicy.none() : retry;
        this.assistantDisplayName = assistantDisplayName == null || assistantDisplayName.trim().isEmpty()
                ? DEFAULT_ASSISTANT_DISPLAY_NAME : assistantDisplayName.trim();
        this.turnAnalysisLog = turnAnalysisLog;
        this.answerPlanning = answerPlanning == null ? AnswerPlanningPolicy.defaults() : answerPlanning;
    }

    /**
     * The answer planning step before every answer ({@value #ANSWER_PLANNING_ENABLED_KEY}) and its confidence thresholds
     * ({@value #ANSWER_PLANNING_USE_AT_KEY}, {@value #ANSWER_PLANNING_ASK_BELOW_KEY}).
     */
    public AnswerPlanningPolicy answerPlanning() {
        return answerPlanning;
    }

    /**
     * Whether each chat turn writes its linguistic analysis (user text, source, frame, the block sent to the model)
     * and the tool calls that follow into the log ({@value #TURN_ANALYSIS_LOG_KEY}, default off: it logs user text).
     */
    public boolean logsTurnAnalysis() {
        return turnAnalysisLog;
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

    /**
     * {@code chat.retryCount} und {@code chat.retryDelayMillis}: wie oft eine einzelne Modellanfrage nach einem
     * vorübergehenden Fehler (Verbindung, HTTP 429, 5xx) erneut gesendet wird.
     */
    public RetryPolicy retry() {
        return retry;
    }

    /**
     * {@code chat.assistant.displayName}: wie Antworten im Verlauf, im Zitat- und im Word-/Markdown-Export
     * überschrieben sind; ohne Eintrag {@link #DEFAULT_ASSISTANT_DISPLAY_NAME}. Die Rolle bleibt {@code ASSISTANT}.
     */
    public String assistantDisplayName() {
        return assistantDisplayName;
    }

    /** Der Anzeigename einer Konfiguration, auch wenn deren Chat-Abschnitt fehlt (dann der Standard). */
    public static String assistantDisplayNameOf(AppConfig config) {
        return config == null || config.chat() == null ? DEFAULT_ASSISTANT_DISPLAY_NAME
                : config.chat().assistantDisplayName();
    }

    @Override
    public String toString() {
        return "ChatConfig[baseUrl=" + baseUrl + ", model=" + model + ", apiKeyRef="
                + (apiKeyRef == null ? "keine" : apiKeyRef) + ", systemPrompt="
                + (systemPrompt == null ? "keiner" : systemPrompt.length() + " Zeichen") + ", developerRolePolicy="
                + developerRolePolicy + ", toolsEnabled=" + toolsEnabled + ", maxToolRounds=" + maxToolRounds + ", retry=" + retry + "]";
    }
}
