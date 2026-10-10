package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Macht aus einer gescheiterten Anfrage an einen Dienst (Chat, Embeddings, Wissensquelle) eine technische
 * Ursache und einen Hinweis, was zu tun ist. Beim ersten Start gegen die echte Enterprise-API stand in der
 * Oberfläche nur "nicht erreichbar", während derselbe Aufruf in PowerShell funktionierte; ob Java dem
 * Zertifikat misstraut, den Proxy nicht kennt oder der Host nicht auflösbar ist, war nicht erkennbar.
 *
 * <p>{@link #detail(Throwable)} folgt der Ursachenkette und reiht die Meldungen mit dem einfachen Klassennamen
 * aneinander, doppelte und voll qualifizierte Klassennamen des JDK entfernt, auf eine Länge begrenzt. Die
 * Adapter garantieren, dass ihre Meldungen keine Zugangsdaten enthalten (Port-Vertrag der
 * {@code ChatCompletionException}, Token wird redigiert); zusätzlich maskiert {@link #mask(String)} alles, was
 * wie ein Bearer-Token aussieht, bevor ein Text in die Oberfläche gelangt. {@link #hint(Throwable)} ordnet die
 * Kette einer bekannten Ursache zu (Zertifikat, Proxy, Namensauflösung, Zeitüberschreitung, KeePass) oder liefert
 * {@code null}.
 */
public final class ConnectionDiagnosis {

    static final int MAX_DETAIL_LENGTH = 600;
    static final String MASKED_TOKEN = "Bearer ***";

    private static final Pattern BEARER = Pattern.compile("(?i)bearer(?:\\s|%20|\\+)+[^\\s\"',;&]+");
    private static final Pattern QUALIFIED_EXCEPTION = Pattern.compile("\\b(?:[a-z][a-z0-9_]*\\.)+([A-Z][A-Za-z0-9_$]*)");
    private static final String SEPARATOR = " | ";

    private ConnectionDiagnosis() {
    }

    /** Ursachenkette als eine Zeile, ohne Secrets; leer, wenn die Ausnahme nichts sagt. */
    public static String detail(Throwable error) {
        List<String> parts = new ArrayList<String>();
        String previous = "";
        boolean outermost = true;
        for (Throwable t : chain(error)) {
            String message = t.getMessage();
            String name = t.getClass().getSimpleName();
            String part;
            if (message == null || message.trim().isEmpty()) {
                part = name;
            } else {
                message = QUALIFIED_EXCEPTION.matcher(message.trim()).replaceAll("$1");
                if (!previous.isEmpty() && previous.contains(message)) {
                    continue; // das JDK wiederholt die Meldung der Ursache in der umschließenden Ausnahme
                }
                if (outermost && t instanceof ChatCompletionException) {
                    part = message; // die Art der Ausnahme steht schon in der Überschrift der Blase
                } else {
                    part = message.startsWith(name) ? message : name + ": " + message;
                }
            }
            outermost = false;
            if (!previous.contains(part)) {
                parts.add(part);
            }
            previous = part;
        }
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (text.length() > 0) {
                text.append(SEPARATOR);
            }
            text.append(part);
        }
        String joined = mask(text.toString().replace('\r', ' ').replace('\n', ' ').trim());
        return joined.length() <= MAX_DETAIL_LENGTH ? joined : joined.substring(0, MAX_DETAIL_LENGTH) + "...";
    }

    /** Was der Benutzer tun kann, nach erkannter Ursache; {@code null}, wenn nichts Bekanntes zutrifft. */
    public static String hint(Throwable error) {
        List<Throwable> chain = chain(error);
        String text = allMessages(chain).toLowerCase(Locale.ROOT);
        if (text.contains("token source failed") || text.contains("secretaccessexception")) {
            return "Der API-Key konnte nicht aus KeePass gelesen werden (KeePass gestartet und entsperrt? Eintrag "
                    + "mit genau diesem Titel vorhanden? Pairing bestätigt?). Der Grund steht im Protokoll unter "
                    + AppPaths.defaultLogDirectory() + ".";
        }
        if (text.contains("stream ended before [done]")) {
            return "Der Server hat die Antwort beendet, ohne sie abzuschließen (Verbindung unterbrochen oder Proxy "
                    + "puffert den Stream). Erneut versuchen; bleibt es dabei, chat.readTimeoutMillis prüfen.";
        }
        if (text.contains("407") || text.contains("proxy authentication")) {
            return "Der Proxy verlangt eine Anmeldung. Unter Netzwerk die Proxy-Anmeldung BASIC mit einem "
                    + "KeePass-Eintrag (Benutzername und Passwort) einstellen (network.proxy.auth.mode, "
                    + "network.proxy.auth.credentialRef); eine Windows-integrierte Anmeldung (NTLM/Kerberos) "
                    + "unterstützt die Anwendung nicht.";
        }
        if (text.contains("proxy route for") && text.contains("unavailable")) {
            return "Die Proxy-Route konnte nicht bestimmt werden (NOT_IMPLEMENTED oder ERROR der Auflösung); es "
                    + "wurde keine Verbindung versucht. Unter Netzwerk „Proxy auflösen“ zeigt jeden Schritt "
                    + "(PAC-Adresse, Skript, Ergebnis); Modus, PAC-Adresse oder Ermittlungsskript anpassen.";
        }
        if (text.contains("unable to tunnel through proxy") || text.contains("proxy returns")) {
            return "Der Proxy hat die Verbindung zum Dienst abgelehnt. Proxy-Einstellung prüfen "
                    + "(network.proxy.mode, network.proxy.nonProxyHosts); „Proxy auflösen“ unter Netzwerk zeigt "
                    + "die Route.";
        }
        // Nur Zertifikatsursachen: eine SSLHandshakeException kann auch Protokollversion oder Cipher-Suite betreffen,
        // dafür gilt der TLS-Zweig darunter.
        if (has(chain, CertificateException.class) || text.contains("pkix") || text.contains("certification path")
                || text.contains("certificate")) {
            return "Java vertraut dem Zertifikat des Servers nicht. Häufige Ursachen: ein Firmen-Proxy mit eigenem "
                    + "Zertifikat oder ein älteres Java, dem die Stammzertifizierungsstelle fehlt. Abhilfe: unter "
                    + "Windows network.tls.useWindowsRoot und network.tls.useWindowsCaStores eingeschaltet lassen "
                    + "(Standard; nutzt die Windows-Zertifikatspeicher wie PowerShell), das CA-Zertifikat als "
                    + "PEM-Datei unter network.tls.caCertificatesFile eintragen oder Java aktualisieren.";
        }
        if (has(chain, SSLException.class) || text.contains("handshake_failure") || text.contains("protocol_version")
                || text.contains("no cipher suites")) {
            return "Die TLS-Verhandlung mit dem Server ist gescheitert (Protokollversion oder Cipher-Suite). "
                    + "Java aktualisieren; TLS 1.3 gibt es in Java 8 erst ab Update 261.";
        }
        if (has(chain, UnknownHostException.class)) {
            return "Der Hostname lässt sich nicht auflösen. Hinter einem Firmen-Proxy muss die Proxy-Route den Proxy "
                    + "nennen (Modus PAC_URL_POWERSHELL wertet das PAC-Skript des Unternehmens aus, MANUAL_PROXY "
                    + "setzt einen festen Proxy; „Proxy auflösen“ unter Netzwerk zeigt das Ergebnis, beim Start "
                    + "steht es im Protokoll unter \"Proxy-Route\"). Sonst Schreibweise der Basis-URL und "
                    + "Netzverbindung prüfen.";
        }
        if (has(chain, SocketTimeoutException.class)) {
            return "Zeitüberschreitung: Server oder Proxy antworten nicht. Netzverbindung und Proxy prüfen (die "
                    + "Route steht im Protokoll unter \"Proxy-Route\"); die Fristen stehen in "
                    + "chat.connectTimeoutMillis und chat.readTimeoutMillis.";
        }
        if (has(chain, ConnectException.class) || has(chain, NoRouteToHostException.class)) {
            return "Die Verbindung wird abgelehnt oder blockiert (Firewall, falscher Port, Proxy nötig). "
                    + "Basis-URL und Proxy-Einstellung (network.proxy.*) prüfen.";
        }
        return null;
    }

    /** Maskiert alles, was wie ein Bearer-Token aussieht, auch URL-kodiert ({@code Bearer%20…}). */
    public static String mask(String text) {
        if (text == null) {
            return "";
        }
        return BEARER.matcher(text).replaceAll(MASKED_TOKEN);
    }

    private static boolean has(List<Throwable> chain, Class<? extends Throwable> type) {
        for (Throwable t : chain) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static String allMessages(List<Throwable> chain) {
        StringBuilder text = new StringBuilder();
        for (Throwable t : chain) {
            text.append(t.getClass().getName()).append(' ');
            if (t.getMessage() != null) {
                text.append(t.getMessage()).append(' ');
            }
        }
        return text.toString();
    }

    private static List<Throwable> chain(Throwable error) {
        List<Throwable> chain = new ArrayList<Throwable>();
        Throwable current = error;
        while (current != null && chain.size() < 12 && !chain.contains(current)) {
            chain.add(current);
            current = current.getCause();
        }
        return chain;
    }
}
