package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.net.ConnectionDiagnosis;
import com.aresstack.enterpriseai.app.net.HttpConnections;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.net.TrustPolicy;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.app.ui.settings.ModelChoice;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.http.api.HttpRoute;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * „Verbindung testen“ im Reiter KI-Dienst: geht mit einer geladenen Konfiguration denselben Weg wie die Adapter und
 * meldet jeden Schritt einzeln: API-Key aus KeePass, Proxy-Route für {@code <chat.baseUrl>/models}
 * ({@code HttpRoutes} über win-proxy-java), HTTPS-Verbindung mit der Vertrauensregel der Konfiguration
 * ({@link TrustPolicy} über win-trust-java), {@code GET <baseUrl>/models} mit HTTP-Status und Vergleich mit
 * {@code chat.model}, zuletzt der Abgleich mit {@code embedding.model}. Ein fehlgeschlagener Schritt beendet den Test; Hinweise
 * (WARNING, INFO) nicht. Der API-Key erscheint in keiner Meldung: Jede Meldung geht durch {@link #redact}, das den
 * gesendeten Key (auch URL-kodiert) und alles, was wie ein Bearer-Token aussieht, ersetzt; eine Umleitung wird ohne
 * Query, Fragment und Anmeldedaten gezeigt. Die Schritte stehen zusätzlich im Protokoll.
 */
public final class ConnectionProbe {

    /** Liefert den API-Key für den Testaufruf; produktiv aus KeePass. */
    public interface TokenLookup {
        /**
         * @return der API-Key oder {@code null}/leer, wenn keiner vorliegt
         * @throws IOException mit einer Meldung für den Benutzer (Grund, nie das Secret); der Test geht dann ohne
         *                     API-Key weiter
         */
        String token() throws IOException;
    }

    static final String MODELS_PATH = "/models";
    static final String STEP_KEY = "API-Key";
    static final String STEP_ROUTE = "Proxy-Route";
    static final String STEP_TLS = "HTTPS-Verbindung";
    static final String STEP_HTTP = "GET /models";
    static final String STEP_EMBEDDING = "Embedding-Modell";

    private static final Logger LOG = Logger.getLogger(ConnectionProbe.class.getName());
    private static final int MAX_BODY_BYTES = 256 * 1024;
    private static final int MAX_EXCERPT = 160;
    private static final int MAX_LISTED = 6;
    private static final String REDACTED = "***";
    private static final Pattern COMMON_NAME = Pattern.compile("(?:^|,)\\s*CN=([^,]+)");

    private final AppConfig config;
    private final TokenLookup tokens;
    private final NetworkServices network;

    public ConnectionProbe(AppConfig config, TokenLookup tokens) {
        this(config, tokens, config == null ? null : NetworkServices.from(config.network()));
    }

    /** Mit eigener Netzschicht (Tests: Route ohne PowerShell). */
    public ConnectionProbe(AppConfig config, TokenLookup tokens, NetworkServices network) {
        if (config == null || tokens == null || network == null) {
            throw new IllegalArgumentException("config, tokens and network must not be null");
        }
        this.config = config;
        this.tokens = tokens;
        this.network = network;
    }

    /** Der geprüfte Endpunkt: {@code chat.baseUrl} ohne Schrägstrich am Ende plus {@value #MODELS_PATH}. */
    public URI target() {
        return modelsUrl(config.chat().baseUrl());
    }

    static URI modelsUrl(URI baseUrl) {
        String text = baseUrl.toString();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return URI.create(text + MODELS_PATH);
    }

    /**
     * Läuft blockierend durch; {@code onStep} wird auf dem rufenden Thread für jeden Schritt einmal gerufen.
     *
     * @return {@code true}, wenn kein Schritt fehlgeschlagen ist
     */
    public boolean run(Consumer<ConnectionCheckStep> onStep) {
        return run(onStep, null);
    }

    /**
     * Wie {@link #run(Consumer)}; meldet nach erfolgreichem {@code GET /models} zusätzlich die Modelle, getrennt
     * nach Chat (Chat-Endpunkt) und Embeddings (Embedding-Endpunkt), höchstens einmal.
     *
     * @param onModels darf {@code null} sein
     */
    public boolean run(Consumer<ConnectionCheckStep> onStep,
                       BiConsumer<List<ModelChoice>, List<ModelChoice>> onModels) {
        if (onStep == null) {
            throw new IllegalArgumentException("onStep must not be null");
        }
        Reporter out = new Reporter(onStep);
        URI target = target();

        String token = lookupToken(out);
        out.secret = token;

        HttpRoute route;
        try {
            route = network.routes().routeFor(target);
        } catch (RuntimeException e) {
            out.report(ConnectionCheckStep.failed(STEP_ROUTE, describe(e)));
            return false;
        }
        if (route.isUnavailable()) {
            out.report(ConnectionCheckStep.failed(STEP_ROUTE, "Ziel " + target + ": " + route.describe()
                    + ". Es wurde keine Verbindung versucht; „Proxy auflösen“ im Reiter Netzwerk zeigt jeden Schritt."));
            return false;
        }
        out.report(ConnectionCheckStep.ok(STEP_ROUTE, "Ziel " + target + ": " + route.describe()));

        Fetched models;
        try {
            models = fetch(target, route, token, out, true);
        } catch (AppConfigException e) {
            out.report(ConnectionCheckStep.failed(STEP_TLS, "TLS-Vertrauensregel nicht baubar: "
                    + join(SettingsMapper.describe(e.problems()))));
            return false;
        } catch (ReportedException e) {
            return false;
        } catch (IOException | RuntimeException e) {
            out.report(ConnectionCheckStep.failed(STEP_HTTP, describe(e)));
            return false;
        }
        ConnectionCheckStep step = evaluate(models.code, models.body, token, models.location);
        out.report(step);
        if (step.isFailure()) {
            return false;
        }
        List<ModelChoice> embeddingModels = checkEmbeddingModel(models, route, token, out);
        if (onModels != null) {
            List<ModelChoice> chatModels = new ArrayList<ModelChoice>();
            for (ModelChoice choice : models(models.body)) {
                if (choice.suitableForChat()) {
                    chatModels.add(choice);
                }
            }
            if (!chatModels.isEmpty() || !embeddingModels.isEmpty()) {
                onModels.accept(chatModels, embeddingModels);
            }
        }
        return true;
    }

    /** Antwort eines GET: Status, Körper (begrenzt), Umleitungsziel. */
    private static final class Fetched {
        final int code;
        final String body;
        final String location;

        Fetched(int code, String body, String location) {
            this.code = code;
            this.body = body;
            this.location = location;
        }
    }

    private Fetched fetch(URI target, HttpRoute route, String token, Reporter out, boolean reportTls)
            throws IOException {
        boolean https = "https".equalsIgnoreCase(target.getScheme());
        TrustPolicy trust;
        try {
            trust = https ? network.trust() : null;
        } catch (AppConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            if (reportTls) {
                out.report(ConnectionCheckStep.failed(STEP_TLS, "TLS-Vertrauensquellen nicht nutzbar: " + describe(e)));
            }
            throw new ReportedException(e);
        }
        HttpURLConnection connection = HttpConnections.open(target, route,
                trust == null ? null : trust.socketFactory(), network.userAgent());
        try {
            connection.setConnectTimeout(config.chat().connectTimeoutMillis());
            connection.setReadTimeout(config.chat().readTimeoutMillis());
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            try {
                connection.connect();
            } catch (IOException | RuntimeException e) {
                if (reportTls) {
                    out.report(ConnectionCheckStep.failed(STEP_TLS, describe(e)));
                }
                throw new ReportedException(e);
            }
            if (reportTls) {
                if (connection instanceof HttpsURLConnection && trust != null) {
                    HttpsURLConnection secure = (HttpsURLConnection) connection;
                    out.report(ConnectionCheckStep.ok(STEP_TLS, "TLS-Handshake mit " + target.getHost()
                            + " erfolgreich (" + secure.getCipherSuite() + "); Serverzertifikat "
                            + describeCertificates(secure.getServerCertificates()) + "; Vertrauensquellen: "
                            + join(trust.sources()) + "."));
                } else {
                    out.report(ConnectionCheckStep.info(STEP_TLS, "Verbindung steht; unverschlüsselt (http), "
                            + "keine TLS-Prüfung."));
                }
            }
            int code = connection.getResponseCode();
            return new Fetched(code, readBody(connection, code), connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    /** Markiert eine Ausnahme, deren Schritt schon gemeldet ist. */
    private static final class ReportedException extends IOException {
        ReportedException(Throwable cause) {
            super(cause.getMessage(), cause);
        }
    }

    /** @return die für Embeddings geeigneten Modelle des Embedding-Endpunkts; leer, wenn keine Liste kam */
    private List<ModelChoice> checkEmbeddingModel(Fetched chatModels, HttpRoute chatRoute, String token, Reporter out) {
        List<ModelChoice> suitable = new ArrayList<ModelChoice>();
        String body = chatModels.body;
        if (config.embedding() == null) {
            collectEmbeddingModels(body, suitable);
            return suitable;
        }
        String model = config.embedding().model();
        URI embeddingModels = modelsUrl(config.embedding().baseUrl());
        List<String> ids;
        if (embeddingModels.equals(target())) {
            ids = modelIds(body);
        } else {
            try {
                HttpRoute route = network.routes().routeFor(embeddingModels);
                if (route.isUnavailable()) {
                    out.report(ConnectionCheckStep.warning(STEP_EMBEDDING, embeddingModels + ": " + route.describe()));
                    return suitable;
                }
                // Den Chat-Key nur an den Embedding-Dienst schicken, wenn er derselbe Eintrag ist.
                boolean sameKey = config.embedding().apiKeyRef() == null
                        || config.embedding().apiKeyRef().equals(config.chat().apiKeyRef());
                Fetched fetched = fetch(embeddingModels, route, sameKey ? token : null, out, false);
                if (fetched.code != HttpURLConnection.HTTP_OK) {
                    out.report(ConnectionCheckStep.warning(STEP_EMBEDDING, "GET " + embeddingModels + ": HTTP "
                            + fetched.code + "; Embedding-Modell nicht geprüft."));
                    return suitable;
                }
                body = fetched.body;
                ids = modelIds(body);
            } catch (IOException | RuntimeException e) {
                out.report(ConnectionCheckStep.warning(STEP_EMBEDDING, embeddingModels + ": " + describe(e)));
                return suitable;
            }
        }
        collectEmbeddingModels(body, suitable);
        if (ids.isEmpty()) {
            out.report(ConnectionCheckStep.info(STEP_EMBEDDING, "Keine Modellliste; „" + model + "“ nicht geprüft."));
        } else if (ids.contains(model)) {
            out.report(ConnectionCheckStep.ok(STEP_EMBEDDING, "„" + model + "“ ist vorhanden."));
        } else {
            out.report(ConnectionCheckStep.warning(STEP_EMBEDDING, "„" + model + "“ fehlt (embedding.model prüfen). "
                    + "Verfügbar: " + listed(ids)));
        }
        return suitable;
    }

    private static void collectEmbeddingModels(String body, List<ModelChoice> into) {
        for (ModelChoice choice : models(body)) {
            if (choice.suitableForEmbedding()) {
                into.add(choice);
            }
        }
    }

    private String lookupToken(Reporter out) {
        SecretRef ref = config.chat().apiKeyRef();
        if (!config.keePass().enabled()) {
            out.report(ConnectionCheckStep.info(STEP_KEY, "KeePassRPC ist ausgeschaltet "
                    + "(security.keepass.enabled=false); der Aufruf geht ohne API-Key, also nur bis zur HTTP-Antwort."));
            return null;
        }
        if (ref == null) {
            out.report(ConnectionCheckStep.info(STEP_KEY, "Kein KeePass-Eintrag konfiguriert (chat.apiKeyRef); "
                    + "der Aufruf geht ohne API-Key."));
            return null;
        }
        try {
            String found = tokens.token();
            if (found == null || found.trim().isEmpty()) {
                out.report(ConnectionCheckStep.warning(STEP_KEY, "Eintrag „" + title(ref)
                        + "“ gefunden, aber das Passwortfeld ist leer; der Aufruf geht ohne API-Key weiter."));
                return null;
            }
            out.report(ConnectionCheckStep.ok(STEP_KEY, "Aus dem KeePass-Eintrag „" + title(ref)
                    + "“ gelesen (wird nicht angezeigt)."));
            return found.trim();
        } catch (IOException e) {
            out.report(ConnectionCheckStep.warning(STEP_KEY, message(e) + " Der Aufruf geht ohne API-Key weiter."));
            return null;
        } catch (RuntimeException e) {
            out.report(ConnectionCheckStep.warning(STEP_KEY, describe(e) + " Der Aufruf geht ohne API-Key weiter."));
            return null;
        }
    }

    /** @param token der gesendete API-Key oder {@code null}; er wird aus allen Auszügen entfernt */
    ConnectionCheckStep evaluate(int code, String rawBody, String token, String location) {
        String model = config.chat().model();
        String prefix = "HTTP " + code + ": ";
        boolean withToken = token != null;
        String body = redact(rawBody, token);
        if (code == HttpURLConnection.HTTP_OK) {
            List<String> ids = modelIds(rawBody);
            if (ids.isEmpty()) {
                return ConnectionCheckStep.warning(STEP_HTTP, prefix + "Antwort ohne erkennbare Modellliste (data[].id); "
                        + "Auszug: " + excerpt(body));
            }
            if (ids.contains(model)) {
                return ConnectionCheckStep.ok(STEP_HTTP, prefix + ids.size() + " Modell(e), darunter „" + model
                        + "“.");
            }
            return ConnectionCheckStep.warning(STEP_HTTP, prefix + ids.size() + " Modell(e), aber „" + model
                    + "“ fehlt (chat.model prüfen). Verfügbar: " + listed(ids));
        }
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
            if (withToken) {
                return ConnectionCheckStep.failed(STEP_HTTP, prefix + "Der Dienst lehnt den API-Key ab. Passwortfeld "
                        + "des KeePass-Eintrags prüfen (vollständiger Key, ohne Leerzeichen). Antwort: " + excerpt(body));
            }
            return ConnectionCheckStep.ok(STEP_HTTP, prefix + "Der Dienst ist erreichbar und verlangt eine Anmeldung; "
                    + "ohne API-Key geprüft.");
        }
        if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_BAD_METHOD) {
            return ConnectionCheckStep.warning(STEP_HTTP, prefix + "Der Dienst antwortet, kennt aber " + MODELS_PATH
                    + " unter dieser Basis-URL nicht. Basis-URL prüfen (endet meist auf /v1); manche Dienste bieten "
                    + MODELS_PATH + " nicht an, dann kann der Chat trotzdem gehen. Antwort: " + excerpt(body));
        }
        if (code == HttpURLConnection.HTTP_PROXY_AUTH) {
            return ConnectionCheckStep.failed(STEP_HTTP, prefix + "Der Proxy verlangt eine Anmeldung. Im Reiter "
                    + "Netzwerk „Proxy auth mode“ BASIC mit einem KeePass-Eintrag (Benutzername und Passwort) "
                    + "einstellen; die Anmeldung gilt nach einem Neustart.");
        }
        if (code >= 300 && code < 400) {
            return ConnectionCheckStep.warning(STEP_HTTP, prefix + "Umleitung"
                    + (location == null ? "" : " nach " + describeLocation(location, token))
                    + "; Basis-URL entsprechend anpassen.");
        }
        if (code >= 500) {
            return ConnectionCheckStep.failed(STEP_HTTP, prefix + "Serverfehler von Dienst oder Proxy. Antwort: "
                    + excerpt(body));
        }
        return ConnectionCheckStep.warning(STEP_HTTP, prefix + "Unerwartete Antwort: " + excerpt(body));
    }

    /**
     * Die Modell-IDs einer {@code GET /models}-Antwort: die String-Werte {@code id} der Objekte direkt im Array
     * {@code data} (oder eines Arrays auf oberster Ebene). Verschachtelte {@code id}s (etwa {@code permission})
     * und andere Schlüssel zählen nicht; JSON-Escapes werden aufgelöst. Ohne JSON-Bibliothek in app-swing ein
     * kleiner Scanner, der nur Strings und Klammertiefe kennt; Unlesbares ergibt eine leere Liste.
     */
    /**
     * Die Einträge der Modellliste mit {@code tool_calling} und {@code capabilities}; Format wie bei
     * {@link #modelIds(String)}. Einträge ohne Kennung fallen weg, doppelte Kennungen zählen einmal.
     */
    static List<ModelChoice> models(String body) {
        List<ModelChoice> models = new ArrayList<ModelChoice>();
        if (body == null) {
            return models;
        }
        Set<String> seen = new LinkedHashSet<String>();
        int depth = 0;
        int arrayDepth = -1;
        int objectStart = -1;
        int i = skipWhitespace(body, 0);
        if (i < body.length() && body.charAt(i) == '[') {
            arrayDepth = 1;
        }
        StringBuilder text = new StringBuilder();
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '{' || c == '[') {
                if (c == '{' && arrayDepth >= 0 && depth == arrayDepth) {
                    objectStart = i;
                }
                depth++;
                i++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (arrayDepth >= 0 && depth < arrayDepth) {
                    break;
                }
                if (c == '}' && objectStart >= 0 && depth == arrayDepth) {
                    ModelChoice choice = entry(body.substring(objectStart, i + 1));
                    if (choice != null && seen.add(choice.id())) {
                        models.add(choice);
                    }
                    objectStart = -1;
                }
                i++;
            } else if (c == '"') {
                text.setLength(0);
                i = readString(body, i, text);
                if (i < 0) {
                    break;
                }
                int next = skipWhitespace(body, i);
                if (next < body.length() && body.charAt(next) == ':') {
                    int value = skipWhitespace(body, next + 1);
                    if (arrayDepth < 0 && depth == 1 && text.toString().equals("data") && value < body.length()
                            && body.charAt(value) == '[') {
                        arrayDepth = depth + 1;
                        i = value;
                    } else {
                        i = next + 1;
                    }
                }
            } else {
                i++;
            }
        }
        return models;
    }

    /** Ein Objekt der Modellliste; {@code null} ohne Kennung. */
    private static ModelChoice entry(String object) {
        String id = null;
        boolean toolCalling = false;
        List<String> capabilities = new ArrayList<String>();
        boolean inCapabilities = false;
        int depth = 0;
        int i = 0;
        StringBuilder text = new StringBuilder();
        while (i < object.length()) {
            char c = object.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                i++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth <= 1) {
                    inCapabilities = false;
                }
                i++;
            } else if (c == '"') {
                text.setLength(0);
                i = readString(object, i, text);
                if (i < 0) {
                    break;
                }
                int next = skipWhitespace(object, i);
                if (next < object.length() && object.charAt(next) == ':') {
                    String key = text.toString();
                    int value = skipWhitespace(object, next + 1);
                    if (depth == 1 && key.equals("id") && value < object.length() && object.charAt(value) == '"') {
                        text.setLength(0);
                        i = readString(object, value, text);
                        if (i < 0) {
                            break;
                        }
                        id = text.toString();
                    } else if (depth == 1 && key.equals("tool_calling")) {
                        toolCalling = object.startsWith("true", value);
                        i = value;
                    } else if (depth == 1 && key.equals("capabilities") && value < object.length()
                            && object.charAt(value) == '[') {
                        inCapabilities = true;
                        i = value;
                    } else {
                        i = next + 1;
                    }
                } else if (inCapabilities && depth == 2) {
                    capabilities.add(text.toString().toLowerCase(Locale.ROOT));
                }
            } else {
                i++;
            }
        }
        return id == null || id.isEmpty() ? null : new ModelChoice(id, toolCalling, capabilities);
    }

    static List<String> modelIds(String body) {
        Set<String> ids = new LinkedHashSet<String>();
        if (body == null) {
            return new ArrayList<String>(ids);
        }
        int depth = 0;
        int arrayDepth = -1; // Tiefe innerhalb des data-Arrays; seine Objekte haben ihre Schlüssel bei arrayDepth + 1
        int i = skipWhitespace(body, 0);
        if (i < body.length() && body.charAt(i) == '[') {
            arrayDepth = 1;
        }
        StringBuilder text = new StringBuilder();
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '{' || c == '[') {
                depth++;
                i++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (arrayDepth >= 0 && depth < arrayDepth) {
                    break; // das data-Array ist zu Ende
                }
                i++;
            } else if (c == '"') {
                text.setLength(0);
                i = readString(body, i, text);
                if (i < 0) {
                    break; // unvollständiger String
                }
                int next = skipWhitespace(body, i);
                if (next < body.length() && body.charAt(next) == ':') {
                    String key = text.toString();
                    int value = skipWhitespace(body, next + 1);
                    if (arrayDepth < 0 && depth == 1 && key.equals("data") && value < body.length()
                            && body.charAt(value) == '[') {
                        arrayDepth = depth + 1;
                        i = value; // das '[' zählt die Schleife
                    } else if (arrayDepth >= 0 && depth == arrayDepth + 1 && key.equals("id") && value < body.length()
                            && body.charAt(value) == '"') {
                        text.setLength(0);
                        i = readString(body, value, text);
                        if (i < 0) {
                            break;
                        }
                        ids.add(text.toString());
                    } else {
                        i = next + 1;
                    }
                }
            } else {
                i++;
            }
        }
        return new ArrayList<String>(ids);
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Liest den JSON-String ab dem öffnenden Anführungszeichen nach {@code into}; liefert den Index danach oder -1. */
    private static int readString(String text, int openingQuote, StringBuilder into) {
        int i = openingQuote + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '"') {
                return i + 1;
            }
            if (c == '\\' && i + 1 < text.length()) {
                char escaped = text.charAt(i + 1);
                switch (escaped) {
                    case 'n':
                        into.append('\n');
                        break;
                    case 't':
                        into.append('\t');
                        break;
                    case 'r':
                        into.append('\r');
                        break;
                    case 'b':
                        into.append('\b');
                        break;
                    case 'f':
                        into.append('\f');
                        break;
                    case 'u':
                        if (i + 5 < text.length()) {
                            try {
                                into.append((char) Integer.parseInt(text.substring(i + 2, i + 6), 16));
                                i += 6;
                                continue;
                            } catch (NumberFormatException e) {
                                return -1;
                            }
                        }
                        return -1;
                    default:
                        into.append(escaped); // \" \\ \/
                }
                i += 2;
            } else {
                into.append(c);
                i++;
            }
        }
        return -1;
    }

    /**
     * Entfernt den gesendeten API-Key (roh und URL-kodiert) und alles, was wie ein Bearer-Token aussieht, aus einem
     * fremden Text (Antwortkörper, Header, Ausnahme).
     */
    static String redact(String text, String secret) {
        if (text == null) {
            return "";
        }
        String result = ConnectionDiagnosis.mask(text);
        if (secret != null && !secret.isEmpty()) {
            for (String variant : variants(secret)) {
                result = result.replace(variant, REDACTED);
            }
        }
        return result;
    }

    /** Der Key roh, wie {@link URLEncoder} ihn schreibt (mit {@code +} und mit {@code %20}) und streng prozentkodiert. */
    private static List<String> variants(String secret) {
        Set<String> variants = new LinkedHashSet<String>();
        variants.add(secret);
        try {
            String encoded = URLEncoder.encode(secret, StandardCharsets.UTF_8.name());
            variants.add(encoded);
            variants.add(encoded.replace("+", "%20"));
        } catch (UnsupportedEncodingException e) {
            // UTF-8 gibt es immer; dann bleibt die rohe Form.
        }
        String strict = percentEncodeStrict(secret);
        variants.add(strict);
        variants.add(strict.toLowerCase(Locale.ROOT));
        return new ArrayList<String>(variants);
    }

    /** Jedes Zeichen außer A-Z, a-z, 0-9 als {@code %XX} (UTF-8), wie es manche Dienste und Proxys zurückgeben. */
    private static String percentEncodeStrict(String text) {
        StringBuilder out = new StringBuilder();
        for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
            int value = b & 0xFF;
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9')) {
                out.append((char) value);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(value >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(value & 0xF, 16)));
            }
        }
        return out.toString();
    }

    /** Eine Umleitung ohne Query, Fragment und Anmeldedaten; Unlesbares nur maskiert und gekürzt. */
    static String describeLocation(String location, String secret) {
        try {
            URI uri = new URI(location.trim());
            if (uri.getScheme() != null && uri.getHost() != null) {
                String shown = uri.getScheme() + "://" + uri.getHost() + (uri.getPort() >= 0 ? ":" + uri.getPort() : "")
                        + (uri.getRawPath() == null ? "" : uri.getRawPath());
                return redact(shown, secret) + (uri.getRawQuery() != null ? " (ohne Query)" : "");
            }
        } catch (URISyntaxException e) {
            // unten gekürzt zeigen
        }
        return excerpt(redact(location, secret));
    }

    private static String listed(List<String> ids) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < ids.size() && i < MAX_LISTED; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(ids.get(i));
        }
        if (ids.size() > MAX_LISTED) {
            text.append(", … (").append(ids.size() - MAX_LISTED).append(" weitere)");
        }
        return text.toString();
    }

    static String excerpt(String body) {
        if (body == null) {
            return "(leer)";
        }
        String flat = ConnectionDiagnosis.mask(body.replaceAll("\\s+", " ").trim());
        if (flat.isEmpty()) {
            return "(leer)";
        }
        return flat.length() <= MAX_EXCERPT ? flat : flat.substring(0, MAX_EXCERPT) + "…";
    }

    private static String readBody(HttpURLConnection connection, int code) {
        InputStream in = null;
        try {
            in = code >= HttpURLConnection.HTTP_BAD_REQUEST ? connection.getErrorStream() : connection.getInputStream();
            if (in == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while (buffer.size() < MAX_BODY_BYTES && (read = in.read(chunk)) >= 0) {
                buffer.write(chunk, 0, read);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // Der Körper ist gelesen oder nicht lesbar; mehr gibt es hier nicht zu tun.
                }
            }
        }
    }

    private static void disconnect(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }

    private static String join(List<String> parts) {
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(part);
        }
        return text.toString();
    }

    static String describeCertificates(Certificate[] chain) {
        if (chain == null || chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
            return "unbekannt";
        }
        X509Certificate leaf = (X509Certificate) chain[0];
        return "für „" + commonName(leaf.getSubjectX500Principal().getName()) + "“, ausgestellt von „"
                + commonName(leaf.getIssuerX500Principal().getName()) + "“";
    }

    static String commonName(String distinguishedName) {
        if (distinguishedName == null) {
            return "?";
        }
        Matcher matcher = COMMON_NAME.matcher(distinguishedName);
        return matcher.find() ? matcher.group(1).trim() : distinguishedName;
    }

    /** Titel, wie KeePass ihn kennt: die Referenz ohne das optionale Präfix {@code keepass:}. */
    static String title(SecretRef ref) {
        String id = ref.id();
        return id.toLowerCase(Locale.ROOT).startsWith("keepass:") ? id.substring("keepass:".length()).trim() : id;
    }

    /** Ursache und Hinweis wie in der Fehlerblase des Chats, ohne Secrets. */
    static String describe(Throwable error) {
        String detail = ConnectionDiagnosis.detail(error);
        String hint = ConnectionDiagnosis.hint(error);
        if (hint == null || hint.isEmpty()) {
            return detail;
        }
        return detail + " Hinweis: " + hint;
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return ConnectionDiagnosis.mask(message.trim());
    }

    /** Gibt Schritte weiter, nachdem der gesendete API-Key aus jeder Meldung entfernt wurde, und protokolliert sie. */
    private static final class Reporter {
        private final Consumer<ConnectionCheckStep> onStep;
        String secret;

        Reporter(Consumer<ConnectionCheckStep> onStep) {
            this.onStep = onStep;
        }

        void report(ConnectionCheckStep step) {
            String detail = redact(step.detail(), secret);
            ConnectionCheckStep safe = detail.equals(step.detail()) ? step
                    : ConnectionCheckStep.of(step.title(), step.status(), detail);
            LOG.info("Verbindungstest, " + safe.title() + ": " + safe.status() + " - " + safe.detail());
            onStep.accept(safe);
        }
    }
}
