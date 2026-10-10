package com.aresstack.enterpriseai.app.net;

import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.wintrust.CertificateTrustConfiguration;
import com.aresstack.wintrust.SystemTrustSslSocketFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
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
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * Entscheidet, welchen Serverzertifikaten die Anwendung vertraut. Java vertraut von Haus aus nur seinem eigenen
 * Truststore ({@code cacerts}); PowerShell, Browser und andere Windows-Programme nutzen den Zertifikatspeicher des
 * Systems. Die Quellen liefert win-trust-java ({@link SystemTrustSslSocketFactory#build}, je
 * {@link CertificateTrustConfiguration} zwischengespeichert): JVM-Truststore, {@code Windows-ROOT} (SunMSCAPI) und
 * der PowerShell-Export der Windows-Speicher Root und CA, drei getrennte Schalter. Dazu optional eine PEM-/DER-Datei
 * mit weiteren CA-Zertifikaten ({@code network.tls.caCertificatesFile}). Ein Zertifikat gilt, sobald eine Quelle
 * es akzeptiert; lehnen alle ab, nennt die Fehlermeldung die befragten Quellen und den Grund der ersten.
 *
 * <p>Die Regel wird nie prozessweit gesetzt: Die Adapter bekommen die {@link SSLSocketFactory} und setzen sie je
 * {@code HttpsURLConnection}. Der Aufbau kann unter Windows Sekunden dauern (PowerShell-Export); deshalb baut
 * {@link #deferred} erst beim ersten Verbindungsaufbau, nie beim Start und nie auf dem EDT, und {@link #verify}
 * prüft für den Einstellungen-Dialog nur die CA-Datei. Client-Schlüssel aus {@code javax.net.ssl.keyStore} bleiben
 * wie beim Standard-SSLContext erhalten.
 */
public final class TrustPolicy {

    static final String JVM_SOURCE = "JVM-Truststore";
    static final String WINDOWS_ROOT_SOURCE = "Windows-ROOT";
    static final String WINDOWS_CA_SOURCE = "Windows Root+CA";
    static final String FILE_SOURCE = "CA-Datei";
    /** JVM-Property des Standard-SSLContexts für Client-Zertifikate; wird hier genauso ausgewertet. */
    static final String KEY_STORE_PROPERTY = "javax.net.ssl.keyStore";

    private final List<String> sources;
    private final List<String> notices;
    private final List<String> diagnostics;
    private final CompositeTrustManager trustManager;
    private final SSLSocketFactory socketFactory;

    private TrustPolicy(List<String> sources, List<String> notices, List<String> diagnostics,
                        CompositeTrustManager trustManager, SSLSocketFactory socketFactory) {
        this.sources = Collections.unmodifiableList(sources);
        this.notices = Collections.unmodifiableList(notices);
        this.diagnostics = Collections.unmodifiableList(new ArrayList<String>(diagnostics));
        this.trustManager = trustManager;
        this.socketFactory = socketFactory;
    }

    /**
     * Baut die Regel jetzt (blockierend; unter Windows mit PowerShell-Export, sofern eingeschaltet). Nie auf dem
     * EDT rufen.
     *
     * @throws AppConfigException wenn {@code network.tls.caCertificatesFile} nicht lesbar ist oder kein
     *                            Zertifikat enthält (die Meldung nennt den Schlüssel, nie den Inhalt)
     */
    public static TrustPolicy from(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return build(config.tlsUseJvmDefault(), config.tlsUseWindowsRoot(), config.tlsUseWindowsCaStores(),
                config.caCertificatesFile(), System.getProperty("os.name", ""));
    }

    /** Regel allein aus dem JVM-Truststore, wie das JDK sie ohne diese Klasse anwendet (Tests). */
    public static TrustPolicy jvmOnly() {
        return build(true, false, false, null, "");
    }

    /**
     * Die billige Prüfung für Dialog und Start: liest die CA-Datei, lädt keine Windows-Speicher.
     *
     * @throws AppConfigException wie {@link #from}
     */
    public static void verify(NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (config.caCertificatesFile() != null) {
            caFileTrustManager(config.caCertificatesFile(), new ArrayList<String>());
        }
    }

    /**
     * Socket-Factory, die die Regel beim ersten Verbindungsaufbau über {@link #from} baut und dann behält;
     * schlägt der Aufbau fehl, scheitert nur dieser Verbindungsaufbau, der nächste versucht es erneut.
     */
    public static SSLSocketFactory deferred(final NetworkConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return new LazySslSocketFactory("TrustPolicy(" + config.describeTrust() + ")") {
            @Override
            protected SSLSocketFactory load() throws IOException {
                try {
                    return from(config).socketFactory();
                } catch (AppConfigException | IllegalStateException e) {
                    throw new IOException("TLS-Vertrauensquellen nicht nutzbar: " + e.getMessage(), e);
                }
            }
        };
    }

    static TrustPolicy build(boolean useJvmDefault, boolean useWindowsRoot, boolean useWindowsCaStores,
                             Path caCertificatesFile, String osName) {
        List<String> sources = new ArrayList<String>();
        List<String> notices = new ArrayList<String>();
        List<X509TrustManager> delegates = new ArrayList<X509TrustManager>();
        boolean windows = isWindows(osName);
        if ((useWindowsRoot || useWindowsCaStores) && !windows) {
            notices.add("Windows-Zertifikatspeicher stehen nur unter Windows zur Verfügung; sie bleiben hier aus.");
        }
        CertificateTrustConfiguration trust = CertificateTrustConfiguration.builder()
                .useJvmDefault(useJvmDefault)
                .useWindowsRoot(useWindowsRoot && windows)
                .useWindowsCaStores(useWindowsCaStores && windows)
                .build();
        SystemTrustSslSocketFactory.Result result = SystemTrustSslSocketFactory.build(trust);
        if (result.isJvmDefaultTrusted()) {
            sources.add(JVM_SOURCE);
        }
        if (result.isWindowsRootTrusted()) {
            sources.add(WINDOWS_ROOT_SOURCE);
        }
        if (result.isWindowsCaStoresTrusted()) {
            sources.add(WINDOWS_CA_SOURCE + " (" + result.getWindowsRootAnchorCount() + " Root, "
                    + result.getWindowsIntermediateCount() + " Zwischenzertifikate)");
        }
        X509TrustManager library = result.getTrustManager();
        if (result.isFallbackToJvmDefault() || library == null) {
            notices.add("Keine konfigurierte TLS-Quelle nutzbar; es gilt der JVM-Truststore.");
            try {
                library = trustManagerFor(null);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("JVM-Truststore nicht nutzbar: " + e.getMessage(), e);
            }
            if (!sources.contains(JVM_SOURCE)) {
                sources.add(JVM_SOURCE + " (Rückfall)");
            }
        }
        delegates.add(library);
        if (caCertificatesFile != null) {
            delegates.add(caFileTrustManager(caCertificatesFile, sources));
        }
        CompositeTrustManager composite = new CompositeTrustManager(delegates, sources);
        KeyManager[] keyManagers;
        try {
            keyManagers = defaultKeyManagers(notices);
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            throw new AppConfigException(KEY_STORE_PROPERTY + ": Client-Schlüsselspeicher nicht ladbar ("
                    + e.getClass().getSimpleName() + ")");
        }
        SSLSocketFactory factory;
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, new TrustManager[] {composite}, null);
            factory = context.getSocketFactory();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TLS-Kontext nicht erzeugbar: " + e.getMessage(), e);
        }
        return new TrustPolicy(sources, notices, result.getDiagnostics(), composite, factory);
    }

    /** Trust-Manager aus der CA-Datei; hängt die Quelle mit Zertifikatzahl an {@code sources}. */
    private static X509TrustManager caFileTrustManager(Path caCertificatesFile, List<String> sources) {
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
            sources.add(FILE_SOURCE + " (" + certificates.size() + " Zertifikate)");
            return trustManagerFor(store);
        } catch (GeneralSecurityException | IOException e) {
            throw new AppConfigException("network.tls.caCertificatesFile: Zertifikate nicht nutzbar ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * Client-Schlüssel wie beim Standard-SSLContext des JDK: aus {@code javax.net.ssl.keyStore} mit
     * {@code keyStoreType}, {@code keyStoreProvider} und {@code keyStorePassword}, sonst keine. So verliert der
     * Austausch der Vertrauensregel kein per JVM-Property eingerichtetes Client-Zertifikat. Das Passwort gelangt
     * weder in Hinweise noch in Fehlermeldungen.
     */
    static KeyManager[] defaultKeyManagers(List<String> notices) throws GeneralSecurityException, IOException {
        String location = System.getProperty(KEY_STORE_PROPERTY, "").trim();
        if (location.isEmpty()) {
            return null;
        }
        String type = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType());
        String provider = System.getProperty("javax.net.ssl.keyStoreProvider", "");
        String password = System.getProperty("javax.net.ssl.keyStorePassword", "");
        char[] secret = password.isEmpty() ? null : password.toCharArray();
        KeyStore store = provider.isEmpty() ? KeyStore.getInstance(type) : KeyStore.getInstance(type, provider);
        if ("NONE".equals(location)) {
            store.load(null, secret);
        } else {
            try (InputStream in = Files.newInputStream(Paths.get(location))) {
                store.load(in, secret);
            }
        }
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, secret);
        int keys = 0;
        for (Enumeration<String> aliases = store.aliases(); aliases.hasMoreElements();) {
            if (store.isKeyEntry(aliases.nextElement())) {
                keys++;
            }
        }
        notices.add("Client-Zertifikate aus " + KEY_STORE_PROPERTY + ": " + keys + " Schlüssel");
        return factory.getKeyManagers();
    }

    /** Die herangezogenen Vertrauensquellen in Prüfreihenfolge, für Log und Fehlermeldungen. */
    public List<String> sources() {
        return sources;
    }

    /** Hinweise aus dem Aufbau (Windows-Speicher nicht verfügbar, Rückfälle, Client-Schlüssel); keine Fehler. */
    public List<String> notices() {
        return notices;
    }

    /** Die Diagnosezeilen von win-trust-java (geladene Quellen, Zahl der Zertifikate, Exportfehler). */
    public List<String> diagnostics() {
        return diagnostics;
    }

    public X509TrustManager trustManager() {
        return trustManager;
    }

    /** Socket-Factory mit dieser Regel (Schlüssel- und Protokolleinstellungen des JDK). */
    public SSLSocketFactory socketFactory() {
        return socketFactory;
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
