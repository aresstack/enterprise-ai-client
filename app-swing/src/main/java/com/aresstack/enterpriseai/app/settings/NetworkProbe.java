package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.net.HttpConnections;
import com.aresstack.enterpriseai.app.net.HttpRoutes;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.app.net.TrustPolicy;
import com.aresstack.enterpriseai.http.api.HttpRoute;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Die beiden Netzwerk-Knöpfe des Einstellungen-Dialogs, wie in AskAI: „Proxy auflösen“ ({@link #resolve}) prüft
 * nur die Proxy-Auflösung der Test-URL (PAC-Adresse, Skript, {@code FindProxyForURL}-Ergebnis); „HTTPS-Verbindung
 * testen“ ({@link #checkHttps}) geht dieselbe Route und baut eine echte HTTPS-Verbindung mit den TLS-Quellen auf,
 * ohne API-Key. Blockiert; läuft auf dem Arbeits-Thread. Jede Zeile steht zusätzlich im Protokoll.
 */
public final class NetworkProbe {

    private static final Logger LOG = Logger.getLogger(NetworkProbe.class.getName());

    private NetworkProbe() {
    }

    /** @return {@code true}, wenn die Route PROXY oder DIRECT ist */
    public static boolean resolve(NetworkConfig config, Consumer<String> out) {
        return resolve(HttpRoutes.from(config), out);
    }

    static boolean resolve(HttpRoutes routes, Consumer<String> out) {
        Consumer<String> log = logging(out, "Proxy auflösen");
        URI target = routes.config().testUrl();
        if (target == null) {
            log.accept("ERROR: keine Test URL (Test URL oder Basis-URL des KI-Dienstes eintragen)");
            return false;
        }
        List<String> problems = HttpRoutes.validationProblems(routes.config());
        if (!problems.isEmpty()) {
            for (String problem : problems) {
                log.accept("ERROR: " + problem);
            }
            return false;
        }
        HttpRoutes.Diagnosis diagnosis = routes.diagnose(target);
        for (String line : diagnosis.lines()) {
            log.accept(line);
        }
        return !diagnosis.route().isUnavailable();
    }

    /** @return {@code true}, wenn der Server mit einem HTTP-Status geantwortet hat (außer 407) */
    public static boolean checkHttps(NetworkConfig config, Consumer<String> out) {
        return checkHttps(NetworkServices.from(config), out);
    }

    static boolean checkHttps(NetworkServices network, Consumer<String> out) {
        Consumer<String> log = logging(out, "HTTPS-Test");
        NetworkConfig config = network.config();
        URI target = config.testUrl();
        if (target == null) {
            log.accept("ERROR: keine Test URL (Test URL oder Basis-URL des KI-Dienstes eintragen)");
            return false;
        }
        List<String> problems = HttpRoutes.validationProblems(config);
        if (!problems.isEmpty()) {
            for (String problem : problems) {
                log.accept("ERROR: " + problem);
            }
            return false;
        }
        log.accept("Testing HTTPS " + target + " ...");
        HttpRoute route = network.routes().routeFor(target);
        log.accept("Route: " + route.describe());
        if (route.isUnavailable()) {
            return false;
        }
        boolean https = "https".equalsIgnoreCase(target.getScheme());
        TrustPolicy trust = null;
        if (https) {
            try {
                trust = network.trust();
            } catch (AppConfigException e) {
                log.accept("ERROR: " + SettingsMapper.describe(e.problems()));
                return false;
            } catch (RuntimeException e) {
                log.accept("ERROR: TLS-Vertrauensquellen nicht nutzbar: " + ConnectionProbe.describe(e));
                return false;
            }
            log.accept("TLS trust: " + trust.sources());
            for (String notice : trust.notices()) {
                log.accept("  " + notice);
            }
        }
        HttpURLConnection connection = null;
        try {
            connection = HttpConnections.open(target, route, trust == null ? null : trust.socketFactory(),
                    network.userAgent());
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.connect();
            if (connection instanceof HttpsURLConnection) {
                HttpsURLConnection secure = (HttpsURLConnection) connection;
                log.accept("TLS handshake OK (" + secure.getCipherSuite() + "), server certificate "
                        + ConnectionProbe.describeCertificates(secure.getServerCertificates()));
            }
            int code = connection.getResponseCode();
            log.accept("Result: HTTP " + code + " (User-Agent: " + network.userAgent() + ", ohne API-Key)");
            if (code == HttpURLConnection.HTTP_PROXY_AUTH) {
                log.accept("ERROR: Der Proxy verlangt eine Anmeldung (Proxy auth mode BASIC; gilt nach Neustart).");
                return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.accept("ERROR: " + ConnectionProbe.describe(e));
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Consumer<String> logging(final Consumer<String> out, final String prefix) {
        return new Consumer<String>() {
            @Override
            public void accept(String line) {
                String safe = ConnectionProbe.redact(line, null);
                LOG.info(prefix + ": " + safe);
                out.accept(safe);
            }
        };
    }
}
