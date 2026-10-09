package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.NetworkConfig;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Entscheidet, welchen Serverzertifikaten die Anwendung vertraut. Java vertraut von Haus aus nur seinem eigenen
 * Truststore ({@code cacerts} bzw. {@code -Djavax.net.ssl.trustStore}); PowerShell, Browser und andere
 * Windows-Programme nutzen dagegen den Zertifikatspeicher des Systems. Deshalb scheiterte der erste Start gegen
 * die echte Enterprise-API mit "nicht erreichbar", obwohl derselbe Aufruf in PowerShell funktionierte: Ein
 * Firmen-Proxy mit eigenem Zertifikat oder eine Wurzel, die einem älteren Java fehlt, ist für Java unbekannt.
 *
 * <p>Die Regel vereint drei Quellen in dieser Reihenfolge: den JVM-Truststore, unter Windows den Speicher der
 * Stammzertifikate ({@code Windows-ROOT}, Provider SunMSCAPI) und optional eine PEM-/DER-Datei mit weiteren
 * CA-Zertifikaten ({@code network.tls.caCertificatesFile}). Ein Zertifikat gilt, sobald eine Quelle es
 * akzeptiert. Lehnen alle ab, nennt die Fehlermeldung die befragten Quellen und den Grund der ersten.
 *
 * <p>{@link #install()} setzt die Regel als Standard-{@link SSLSocketFactory} von {@link HttpsURLConnection};
 * damit gilt sie für Chat-, Embedding-, MediaWiki- und Confluence-Adapter (ohne Client-Zertifikat), die
 * {@code HttpURLConnection} nutzen. Keine statischen Felder; {@link #uninstall()} stellt die vorherige Factory
 * wieder her. Hostnamen-Prüfung und Protokolle bleiben die des JDK.
 */
public final class TrustPolicy {

    static final String WINDOWS_ROOT_STORE = "Windows-ROOT";
    static final String JVM_SOURCE = "JVM-Truststore";
    static final String WINDOWS_SOURCE = "Windows-Zertifikatspeicher";
    static final String FILE_SOURCE = "CA-Datei";

    private final List<String> sources;
    private final List<String> notices;
    private final CompositeTrustManager trustManager;
    private final SSLSocketFactory socketFactory;
    private SSLSocketFactory previousDefault;

    private TrustPolicy(List<String> sources, List<String> notices, CompositeTrustManager trustManager,
                        SSLSocketFactory socketFactory) {
        this.sources = Collections.unmodifiableList(sources);
        this.notices = Collections.unmodifiableList(notices);
        this.trustManager = trustManager;
        this.socketFactory = socketFactory;
    }

    /**
     * Baut die Regel aus der Konfiguration.
     *
     * @throws AppConfigException wenn {@code network.tls.caCertificatesFile} nicht lesbar ist oder kein
     *                            Zertifikat enthält (die Meldung nennt den Schlüssel, nie den Inhalt)
     */
    public static TrustPolicy from(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return build(config.useWindowsCertificateStore(), config.caCertificatesFile(),
                System.getProperty("os.name", ""));
    }

    /** Regel allein aus dem JVM-Truststore, wie das JDK sie ohne diese Klasse anwendet (Tests). */
    public static TrustPolicy jvmOnly() {
        return build(false, null, "");
    }

    static TrustPolicy build(boolean useWindowsStore, Path caCertificatesFile, String osName) {
        List<String> sources = new ArrayList<String>();
        List<String> notices = new ArrayList<String>();
        List<X509TrustManager> delegates = new ArrayList<X509TrustManager>();
        try {
            delegates.add(jvmTrustManager());
            sources.add(JVM_SOURCE);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM-Truststore nicht nutzbar: " + e.getMessage(), e);
        }
        if (useWindowsStore) {
            if (isWindows(osName)) {
                try {
                    KeyStore windowsRoot = KeyStore.getInstance(WINDOWS_ROOT_STORE);
                    windowsRoot.load(null, null);
                    int count = windowsRoot.size();
                    delegates.add(trustManagerFor(windowsRoot));
                    sources.add(WINDOWS_SOURCE + " (" + count + " Zertifikate)");
                } catch (GeneralSecurityException | IOException | RuntimeException e) {
                    notices.add(WINDOWS_SOURCE + " nicht nutzbar (" + e.getClass().getSimpleName() + ": "
                            + e.getMessage() + "); es gilt nur der JVM-Truststore.");
                }
            } else {
                notices.add(WINDOWS_SOURCE + " steht nur unter Windows zur Verfügung; es gilt der JVM-Truststore.");
            }
        }
        if (caCertificatesFile != null) {
            List<X509Certificate> certificates;
            try {
                certificates = loadCertificates(caCertificatesFile);
            } catch (IOException e) {
                throw new AppConfigException("network.tls.caCertificatesFile: Datei nicht lesbar ("
                        + e.getClass().getSimpleName() + ")");
            } catch (CertificateException e) {
                throw new AppConfigException("network.tls.caCertificatesFile: kein PEM- oder DER-Zertifikat ("
                        + e.getMessage() + ")");
            }
            if (certificates.isEmpty()) {
                throw new AppConfigException("network.tls.caCertificatesFile: Datei enthält kein Zertifikat");
            }
            try {
                KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                int i = 0;
                for (X509Certificate certificate : certificates) {
                    store.setCertificateEntry("ca-" + (i++), certificate);
                }
                delegates.add(trustManagerFor(store));
            } catch (GeneralSecurityException | IOException e) {
                throw new AppConfigException("network.tls.caCertificatesFile: Zertifikate nicht nutzbar ("
                        + e.getClass().getSimpleName() + ")");
            }
            sources.add(FILE_SOURCE + " (" + certificates.size() + " Zertifikate)");
        }
        CompositeTrustManager composite = new CompositeTrustManager(delegates, sources);
        SSLSocketFactory factory;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {composite}, null);
            factory = context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TLS-Kontext nicht erzeugbar: " + e.getMessage(), e);
        }
        return new TrustPolicy(sources, notices, composite, factory);
    }

    /** Die herangezogenen Vertrauensquellen in Prüfreihenfolge, für Log und Fehlermeldungen. */
    public List<String> sources() {
        return sources;
    }

    /** Hinweise aus dem Aufbau (z. B. Windows-Speicher nicht verfügbar); keine Fehler. */
    public List<String> notices() {
        return notices;
    }

    public X509TrustManager trustManager() {
        return trustManager;
    }

    /** Socket-Factory mit dieser Regel (Schlüssel- und Protokolleinstellungen des JDK). */
    public SSLSocketFactory socketFactory() {
        return socketFactory;
    }

    /** Setzt die Regel als Standard für alle {@link HttpsURLConnection}. Idempotent. */
    public synchronized void install() {
        SSLSocketFactory current = HttpsURLConnection.getDefaultSSLSocketFactory();
        if (current == socketFactory) {
            return;
        }
        previousDefault = current;
        HttpsURLConnection.setDefaultSSLSocketFactory(socketFactory);
    }

    /** Stellt die vorherige Standard-Factory wieder her, falls diese Regel installiert ist. */
    public synchronized void uninstall() {
        if (HttpsURLConnection.getDefaultSSLSocketFactory() == socketFactory && previousDefault != null) {
            HttpsURLConnection.setDefaultSSLSocketFactory(previousDefault);
            previousDefault = null;
        }
    }

    /** Liest alle X.509-Zertifikate einer PEM-Datei (auch mehrere hintereinander) oder einer DER-Datei. */
    static List<X509Certificate> loadCertificates(Path file) throws IOException, CertificateException {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<X509Certificate> certificates = new ArrayList<X509Certificate>();
        try (InputStream in = Files.newInputStream(file)) {
            for (Certificate certificate : factory.generateCertificates(in)) {
                if (certificate instanceof X509Certificate) {
                    certificates.add((X509Certificate) certificate);
                }
            }
        }
        return certificates;
    }

    static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static X509TrustManager jvmTrustManager() throws GeneralSecurityException {
        return trustManagerFor(null);
    }

    private static X509TrustManager trustManagerFor(KeyStore store) throws GeneralSecurityException {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        for (TrustManager manager : factory.getTrustManagers()) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new GeneralSecurityException("kein X509TrustManager verfügbar");
    }

    @Override
    public String toString() {
        return "TrustPolicy" + sources;
    }

    /**
     * Fragt die Quellen nacheinander; die erste, die akzeptiert, entscheidet. Lehnen alle ab, trägt die
     * Ausnahme die Meldung der ersten Quelle (des JVM-Truststores, meist "PKIX path building failed") und nennt
     * alle befragten Quellen; die übrigen Ablehnungen hängen als unterdrückte Ausnahmen daran.
     */
    static final class CompositeTrustManager extends X509ExtendedTrustManager {

        private final List<X509TrustManager> delegates;
        private final List<String> sources;

        CompositeTrustManager(List<X509TrustManager> delegates, List<String> sources) {
            if (delegates.isEmpty()) {
                throw new IllegalArgumentException("at least one trust manager is required");
            }
            this.delegates = new ArrayList<X509TrustManager>(delegates);
            this.sources = new ArrayList<String>(sources);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    delegate.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    first = remember(first, e);
                }
            }
            throw rejected(first);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    if (delegate instanceof X509ExtendedTrustManager) {
                        ((X509ExtendedTrustManager) delegate).checkServerTrusted(chain, authType, socket);
                    } else {
                        delegate.checkServerTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException e) {
                    first = remember(first, e);
                }
            }
            throw rejected(first);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    if (delegate instanceof X509ExtendedTrustManager) {
                        ((X509ExtendedTrustManager) delegate).checkServerTrusted(chain, authType, engine);
                    } else {
                        delegate.checkServerTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException e) {
                    first = remember(first, e);
                }
            }
            throw rejected(first);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    delegate.checkClientTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    first = remember(first, e);
                }
            }
            throw rejected(first);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    if (delegate instanceof X509ExtendedTrustManager) {
                        ((X509ExtendedTrustManager) delegate).checkClientTrusted(chain, authType, socket);
                    } else {
                        delegate.checkClientTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException e) {
                    first = remember(first, e);
                }
            }
            throw rejected(first);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            CertificateException first = null;
            for (X509TrustManager delegate : delegates) {
                try {
                    if (delegate instanceof X509ExtendedTrustManager) {
                        ((X509ExtendedTrustManager) delegate).checkClientTrusted(chain, authType, engine);
                    } else {
                        delegate.checkClientTrusted(chain, authType);
                    }
                    return;
                } catch (CertificateException e) {
                    first = remember(first, e);
                }
            }
            throw rejected(first);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<X509Certificate>();
            for (X509TrustManager delegate : delegates) {
                X509Certificate[] accepted = delegate.getAcceptedIssuers();
                if (accepted != null) {
                    Collections.addAll(issuers, accepted);
                }
            }
            return issuers.toArray(new X509Certificate[0]);
        }

        private static CertificateException remember(CertificateException first, CertificateException next) {
            if (first == null) {
                return next;
            }
            first.addSuppressed(next);
            return first;
        }

        private CertificateException rejected(CertificateException first) {
            String reason = first == null ? "" : first.getMessage();
            CertificateException rejected = new CertificateException("Serverzertifikat von keiner Vertrauensquelle "
                    + "akzeptiert " + sources + (reason == null || reason.isEmpty() ? "" : ": " + reason), first);
            return rejected;
        }
    }
}
