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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URI;
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
 * (WARNING, INFO) nicht. Der API-Key erscheint in keiner Meldung; fremde Texte (Antwortkörper, Ausnahmen) werden
 * mit {@link ConnectionDiagnosis#mask} maskiert. Die Schritte stehen zusätzlich im Protokoll.
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
    private static final Pattern MODEL_ID = Pattern.compile("\"id\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
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
        URI target = target();

        Proxy proxy;
        try {
            ProxyPolicy policy = new ProxyPolicy(config.network());
            proxy = policy.proxyFor(target);
            String route = policy.describeRoute(target);
            String detail = "Ziel " + target + ": " + route;
            if (undecided(policy, route)) {
                report(onStep, ConnectionCheckStep.warning(STEP_ROUTE, detail + ". Das PAC-Skript lieferte kein "
                        + "Ergebnis, es gelten die Systemeinstellungen. Hinter einem Firmen-Proxy: PAC-Adresse "
                        + "eintragen, PAC-Ermittlung POWERSHELL versuchen oder den Proxy mit MANUAL setzen."));
            } else {
                report(onStep, ConnectionCheckStep.ok(STEP_ROUTE, detail));
            }
        } catch (RuntimeException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_ROUTE, describe(e)));
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
            report(onStep, ConnectionCheckStep.ok(STEP_RESOLVE, detail));
        } catch (UnknownHostException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_RESOLVE, host + ": " + describe(e)));
            return false;
        } catch (RuntimeException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_RESOLVE, host + ": " + describe(e)));
            return false;
        }

        String token = lookupToken(onStep);

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
                report(onStep, ConnectionCheckStep.ok(STEP_TLS, "TLS-Handshake mit " + target.getHost()
                        + " erfolgreich (" + secure.getCipherSuite() + "); Serverzertifikat "
                        + describeCertificates(secure.getServerCertificates()) + "; Vertrauensquellen: "
                        + join(trust.sources()) + "."));
            } else {
                report(onStep, ConnectionCheckStep.info(STEP_TLS, "Verbindung steht; unverschlüsselt (http), "
                        + "keine TLS-Prüfung."));
            }
        } catch (AppConfigException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_TLS, "TLS-Vertrauensregel nicht baubar: "
                    + join(SettingsMapper.describe(e.problems()))));
            disconnect(connection);
            return false;
        } catch (IOException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_TLS, describe(e)));
            disconnect(connection);
            return false;
        } catch (RuntimeException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_TLS, describe(e)));
            disconnect(connection);
            return false;
        }

        try {
            int code = connection.getResponseCode();
            String body = readBody(connection, code);
            ConnectionCheckStep step = evaluate(code, body, token != null, connection.getHeaderField("Location"));
            report(onStep, step);
            return !step.isFailure();
        } catch (IOException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_HTTP, describe(e)));
            return false;
        } catch (RuntimeException e) {
            report(onStep, ConnectionCheckStep.failed(STEP_HTTP, describe(e)));
            return false;
        } finally {
            disconnect(connection);
        }
    }

    /** AUTO ohne PAC-Entscheidung: {@link ProxyPolicy#describeRoute} hängt dann die Systemeinstellungen an. */
    private static boolean undecided(ProxyPolicy policy, String route) {
        return policy.mode() == ProxyMode.AUTO && route.contains("; Systemeinstellungen:");
    }

    private String lookupToken(Consumer<ConnectionCheckStep> onStep) {
        SecretRef ref = config.chat().apiKeyRef();
        if (!config.keePass().enabled()) {
            report(onStep, ConnectionCheckStep.info(STEP_KEY, "KeePassRPC ist ausgeschaltet "
                    + "(security.keepass.enabled=false); der Aufruf geht ohne API-Key, also nur bis zur HTTP-Antwort."));
            return null;
        }
        if (ref == null) {
            report(onStep, ConnectionCheckStep.info(STEP_KEY, "Kein KeePass-Eintrag konfiguriert (chat.apiKeyRef); "
                    + "der Aufruf geht ohne API-Key."));
            return null;
        }
        try {
            String found = tokens.token();
            if (found == null || found.trim().isEmpty()) {
                report(onStep, ConnectionCheckStep.warning(STEP_KEY, "Eintrag „" + title(ref)
                        + "“ gefunden, aber das Passwortfeld ist leer; der Aufruf geht ohne API-Key weiter."));
                return null;
            }
            report(onStep, ConnectionCheckStep.ok(STEP_KEY, "Aus dem KeePass-Eintrag „" + title(ref)
                    + "“ gelesen (wird nicht angezeigt)."));
            return found.trim();
        } catch (IOException e) {
            report(onStep, ConnectionCheckStep.warning(STEP_KEY, message(e) + " Der Aufruf geht ohne API-Key weiter."));
            return null;
        } catch (RuntimeException e) {
            report(onStep, ConnectionCheckStep.warning(STEP_KEY, describe(e) + " Der Aufruf geht ohne API-Key weiter."));
            return null;
        }
    }

    ConnectionCheckStep evaluate(int code, String body, boolean withToken, String location) {
        String model = config.chat().model();
        String prefix = "HTTP " + code + ": ";
        if (code == HttpURLConnection.HTTP_OK) {
            List<String> ids = modelIds(body);
            if (ids.isEmpty()) {
                return ConnectionCheckStep.warning(STEP_HTTP, prefix + "Antwort ohne erkennbare Modellliste; Auszug: "
                        + excerpt(body));
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
                    + (location == null ? "" : " nach " + ConnectionDiagnosis.mask(location))
                    + "; Basis-URL entsprechend anpassen.");
        }
        if (code >= 500) {
            return ConnectionCheckStep.failed(STEP_HTTP, prefix + "Serverfehler von Dienst oder Proxy. Antwort: "
                    + excerpt(body));
        }
        return ConnectionCheckStep.warning(STEP_HTTP, prefix + "Unerwartete Antwort: " + excerpt(body));
    }

    static List<String> modelIds(String body) {
        Set<String> ids = new LinkedHashSet<String>();
        if (body != null) {
            Matcher matcher = MODEL_ID.matcher(body);
            while (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return new ArrayList<String>(ids);
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

    private static void report(Consumer<ConnectionCheckStep> onStep, ConnectionCheckStep step) {
        LOG.info("Verbindungstest, " + step.title() + ": " + step.status() + " - " + step.detail());
        onStep.accept(step);
    }
}
