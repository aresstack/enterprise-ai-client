package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.ProxyMode;
import com.aresstack.enterpriseai.app.net.ConnectionDiagnosis;
import com.aresstack.enterpriseai.app.net.ProxyPolicy;
import com.aresstack.enterpriseai.app.net.TrustPolicy;
import com.aresstack.enterpriseai.app.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.domain.security.SecretRef;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Geht mit einer geladenen Konfiguration denselben Weg wie der Start und meldet jeden Schritt einzeln: die
 * Proxy-Route für {@code chat.baseUrl} ({@link ProxyPolicy}, bei AUTO samt PAC-Auswertung), die Namensauflösung
 * des Hosts, der tatsächlich angesprochen wird (Ziel oder Proxy), den API-Key aus KeePass, den TLS-Handshake mit
 * der Vertrauensregel der Konfiguration ({@link TrustPolicy}) und schließlich {@code GET <baseUrl>/models}, dessen
 * Antwort mit {@code chat.model} verglichen wird. Ein fehlgeschlagener Schritt beendet den Test; Hinweise
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
    static final String STEP_ROUTE = "Proxy-Route";
    static final String STEP_RESOLVE = "Namensauflösung";
    static final String STEP_KEY = "API-Key";
    static final String STEP_TLS = "Verbindung und TLS";
    static final String STEP_HTTP = "GET /models";

    private static final Logger LOG = Logger.getLogger(ConnectionProbe.class.getName());
    private static final int MAX_BODY_BYTES = 256 * 1024;
    private static final int MAX_EXCERPT = 160;
    private static final int MAX_LISTED = 6;
    private static final String REDACTED = "***";
    private static final Pattern COMMON_NAME = Pattern.compile("(?:^|,)\\s*CN=([^,]+)");

    private final AppConfig config;
    private final TokenLookup tokens;

    public ConnectionProbe(AppConfig config, TokenLookup tokens) {
        if (config == null || tokens == null) {
            throw new IllegalArgumentException("config and tokens must not be null");
        }
        this.config = config;
        this.tokens = tokens;
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
        if (onStep == null) {
            throw new IllegalArgumentException("onStep must not be null");
        }
        Reporter out = new Reporter(onStep);
        URI target = target();

        Proxy proxy;
        try {
            ProxyPolicy policy = new ProxyPolicy(config.network());
            proxy = policy.proxyFor(target);
            String route = policy.describeRoute(target);
            String detail = "Ziel " + target + ": " + route;
            boolean undecided = undecided(policy, route);
            if (policy.systemSettingsDeferred() && (undecided || policy.mode() == ProxyMode.SYSTEM)) {
                out.report(ConnectionCheckStep.warning(STEP_ROUTE, detail + ". Die Proxy-Einstellungen des "
                        + "Systems liest diese laufende Anwendung erst nach einem Neustart (sie wurde ohne AUTO/SYSTEM "
                        + "gestartet); der Test kann sie jetzt nicht prüfen. Speichern, neu starten und erneut prüfen."));
            } else if (undecided) {
                out.report(ConnectionCheckStep.warning(STEP_ROUTE, detail + ". Das PAC-Skript lieferte kein "
                        + "Ergebnis, es gelten die Systemeinstellungen. Hinter einem Firmen-Proxy: PAC-Adresse "
                        + "eintragen, PAC-Ermittlung POWERSHELL versuchen oder den Proxy mit MANUAL setzen."));
            } else {
                out.report(ConnectionCheckStep.ok(STEP_ROUTE, detail));
            }
        } catch (RuntimeException e) {
            out.report(ConnectionCheckStep.failed(STEP_ROUTE, describe(e)));
            return false;
        }

        boolean viaProxy = proxy.type() != Proxy.Type.DIRECT;
        String host = viaProxy ? hostOf(proxy.address()) : target.getHost();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            String detail = host + " -> " + joinAddresses(addresses);
            if (viaProxy) {
                detail += " (Proxy; den Zielhost " + target.getHost() + " löst der Proxy auf)";
            }
            out.report(ConnectionCheckStep.ok(STEP_RESOLVE, detail));
        } catch (UnknownHostException e) {
            out.report(ConnectionCheckStep.failed(STEP_RESOLVE, host + ": " + describe(e)));
            return false;
        } catch (RuntimeException e) {
            out.report(ConnectionCheckStep.failed(STEP_RESOLVE, host + ": " + describe(e)));
            return false;
        }

        String token = lookupToken(out);
        out.secret = token;

        boolean https = "https".equalsIgnoreCase(target.getScheme());
        HttpURLConnection connection = null;
        try {
            TrustPolicy trust = https ? TrustPolicy.from(config.network()) : null;
            connection = (HttpURLConnection) target.toURL().openConnection(proxy);
            connection.setConnectTimeout(config.chat().connectTimeoutMillis());
            connection.setReadTimeout(config.chat().readTimeoutMillis());
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            if (token != null) {
                connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (connection instanceof HttpsURLConnection && trust != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(trust.socketFactory());
            }
            connection.connect();
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
        } catch (AppConfigException e) {
            out.report(ConnectionCheckStep.failed(STEP_TLS, "TLS-Vertrauensregel nicht baubar: "
                    + join(SettingsMapper.describe(e.problems()))));
            disconnect(connection);
            return false;
        } catch (IOException e) {
            out.report(ConnectionCheckStep.failed(STEP_TLS, describe(e)));
            disconnect(connection);
            return false;
        } catch (RuntimeException e) {
            out.report(ConnectionCheckStep.failed(STEP_TLS, describe(e)));
            disconnect(connection);
            return false;
        }

        try {
            int code = connection.getResponseCode();
            String body = readBody(connection, code);
            ConnectionCheckStep step = evaluate(code, body, token, connection.getHeaderField("Location"));
            out.report(step);
            return !step.isFailure();
        } catch (IOException e) {
            out.report(ConnectionCheckStep.failed(STEP_HTTP, describe(e)));
            return false;
        } catch (RuntimeException e) {
            out.report(ConnectionCheckStep.failed(STEP_HTTP, describe(e)));
            return false;
        } finally {
            disconnect(connection);
        }
    }

    /** AUTO ohne PAC-Entscheidung: {@link ProxyPolicy#describeRoute} hängt dann die Systemeinstellungen an. */
    private static boolean undecided(ProxyPolicy policy, String route) {
        return policy.mode() == ProxyMode.AUTO && route.contains("; Systemeinstellungen:");
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
            return ConnectionCheckStep.failed(STEP_HTTP, prefix + "Der Proxy verlangt eine Anmeldung. "
                    + "Proxy-Authentifizierung unterstützt die Anwendung noch nicht; ein Proxy ohne Anmeldung "
                    + "(MANUAL) oder eine direkte Verbindung (NONE) ist nötig.");
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

    private static String hostOf(SocketAddress address) {
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getHostString();
        }
        return String.valueOf(address);
    }

    private static String joinAddresses(InetAddress[] addresses) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < addresses.length && i < 3; i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(addresses[i].getHostAddress());
        }
        if (addresses.length > 3) {
            text.append(", …");
        }
        return text.toString();
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
