package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Anmeldung am Proxy (BASIC) über den Security-Port: Benutzername und Passwort kommen je Anfrage aus dem
 * KeePass-Eintrag {@code network.proxy.auth.credentialRef}; kein Feld hält sie, die Properties-Datei nie.
 * Antwortet nur auf Anfragen des Proxys ({@link RequestorType#PROXY}); eine Server-Anmeldung bleibt den
 * Adaptern (Bearer-Token) überlassen.
 *
 * <p>Warum prozessweit: Für den HTTPS-Tunnel ({@code CONNECT}) fragt das JDK die Anmeldung ausschließlich beim
 * {@link Authenticator#setDefault Standard-Authenticator} ab; ein {@code Proxy-Authorization}-Header auf der
 * {@code HttpURLConnection} erreicht den Tunnelaufbau nicht. Seit JDK 8u111 ist BASIC für den Tunnel zudem
 * abgeschaltet ({@code jdk.http.auth.tunneling.disabledSchemes=Basic}); {@link #install} leert die Property, sofern
 * sie nicht gesetzt ist. Das ist die einzige prozessweite Netzeinstellung der Anwendung.
 */
public final class ProxyAuthenticator extends Authenticator {

    static final String TUNNELING_DISABLED_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";

    private static final Logger LOG = Logger.getLogger(ProxyAuthenticator.class.getName());

    private static volatile boolean installed;

    private final SecretProvider secrets;
    private final SecretRef ref;

    public ProxyAuthenticator(SecretProvider secrets, SecretRef ref) {
        if (secrets == null || ref == null) {
            throw new IllegalArgumentException("secrets and ref must not be null");
        }
        this.secrets = secrets;
        this.ref = ref;
    }

    /** Setzt diese Anmeldung als Standard-Authenticator und erlaubt BASIC für den HTTPS-Tunnel. */
    public static ProxyAuthenticator install(SecretProvider secrets, SecretRef ref) {
        ProxyAuthenticator authenticator = new ProxyAuthenticator(secrets, ref);
        if (System.getProperty(TUNNELING_DISABLED_SCHEMES) == null) {
            System.setProperty(TUNNELING_DISABLED_SCHEMES, "");
        }
        Authenticator.setDefault(authenticator);
        installed = true;
        LOG.info("Proxy-Anmeldung BASIC aus KeePass-Eintrag " + ref + " eingerichtet");
        return authenticator;
    }

    /** Ob im Prozess schon eine Proxy-Anmeldung gesetzt ist ({@code Authenticator.getDefault} gibt es erst ab Java 9). */
    public static boolean installed() {
        return installed;
    }

    public SecretRef ref() {
        return ref;
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
        if (getRequestorType() != RequestorType.PROXY) {
            return null;
        }
        try {
            return secrets.<PasswordAuthentication, RuntimeException>withSecret(ref,
                    new SecretFunction<PasswordAuthentication, RuntimeException>() {
                        @Override
                        public PasswordAuthentication apply(SecretMaterial material) {
                            char[] password = material.copySecret();
                            try {
                                // Der Konstruktor kopiert das Passwort; das Original wird gleich gelöscht.
                                return new PasswordAuthentication(material.principal(), password);
                            } finally {
                                java.util.Arrays.fill(password, '\0');
                            }
                        }
                    });
        } catch (SecretUnavailableException e) {
            LOG.log(Level.WARNING, "Proxy-Anmeldung: Zugangsdaten aus " + ref + " nicht verfügbar (" + e.reason()
                    + ")");
            return null;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Proxy-Anmeldung: Zugangsdaten aus " + ref + " nicht lesbar", e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "ProxyAuthenticator[" + ref + "]";
    }
}
