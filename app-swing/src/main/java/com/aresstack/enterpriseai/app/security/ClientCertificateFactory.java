package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.app.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.app.net.NetworkServices;
import com.aresstack.enterpriseai.security.api.SecretFunction;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.confluence.ClientCertificates;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Baut die TLS-Socket-Factory für ein Client-Zertifikat (mTLS, Confluence) aus der Konfiguration: Windows-MY per
 * Alias (MainframeMate) oder eine PKCS12-Datei, deren Passwort nur für das Laden über den Security-Port geholt und
 * danach gelöscht wird. Der private Schlüssel bleibt im KeyStore.
 *
 * <p>{@link #create} lädt sofort; {@link #deferred} liefert eine Factory, die das Zertifikat erst beim ersten
 * Verbindungsaufbau lädt. Die Composition Root nutzt die verzögerte Form: So entscheidet nicht der Start, sondern
 * die einzelne Anfrage, ob das Zertifikat nutzbar ist, und ein nicht erreichbarer Tresor (KeePass aus oder
 * gesperrt) blockiert die Anwendung nicht.
 */
public final class ClientCertificateFactory {

    private ClientCertificateFactory() {
    }

    /**
     * @throws GeneralSecurityException wenn Zertifikat oder KeyStore nicht nutzbar sind
     * @throws IOException              wenn die PKCS12-Datei nicht lesbar ist
     * @throws SecretUnavailableException wenn das KeyStore-Passwort nicht bereitgestellt werden kann
     */
    public static SSLSocketFactory create(ClientCertificateConfig config, SecretProvider secrets)
            throws GeneralSecurityException, IOException, SecretUnavailableException {
        return create(config, secrets, null);
    }

    /**
     * Wie {@link #create(ClientCertificateConfig, SecretProvider)}; Serverzertifikate prüft {@code trust}
     * (die Vertrauensregel der Anwendung), {@code null} = JVM-Standard.
     */
    public static SSLSocketFactory create(ClientCertificateConfig config, SecretProvider secrets,
                                          final X509TrustManager trust)
            throws GeneralSecurityException, IOException, SecretUnavailableException {
        requireConfig(config, secrets);
        if (config.usesWindowsStore()) {
            return ClientCertificates.windowsMy(config.alias(), trust);
        }
        if (config.keyStorePasswordRef() == null) {
            return fromFile(config, null, trust);
        }
        return secrets.<SSLSocketFactory, GeneralSecurityException>withSecret(config.keyStorePasswordRef(),
                new SecretFunction<SSLSocketFactory, GeneralSecurityException>() {
                    @Override
                    public SSLSocketFactory apply(SecretMaterial material) throws GeneralSecurityException {
                        char[] password = material.copySecret();
                        try {
                            return fromFile(config, password, trust);
                        } catch (IOException e) {
                            throw new GeneralSecurityException("PKCS12-Datei nicht lesbar: " + config.keyStoreFile(), e);
                        } finally {
                            Arrays.fill(password, '\0');
                        }
                    }
                });
    }

    /**
     * Factory, die das Zertifikat erst beim ersten Verbindungsaufbau über {@link #create} lädt und danach behält.
     * Schlägt das Laden fehl (Tresor nicht erreichbar, Datei fehlt, Passwort falsch, kein Windows-Zertifikatspeicher),
     * scheitert nur dieser Verbindungsaufbau mit {@link IOException}; der nächste versucht es erneut, sodass ein
     * zwischenzeitlich entsperrter Tresor ohne Neustart greift. Das Passwort wird in keinem Fall gehalten.
     */
    public static SSLSocketFactory deferred(ClientCertificateConfig config, SecretProvider secrets) {
        requireConfig(config, secrets);
        return new Deferred(config, secrets, null);
    }

    /**
     * Wie {@link #deferred(ClientCertificateConfig, SecretProvider)}, mit der TLS-Vertrauensregel der Anwendung
     * aus {@code network} im selben Kontext (sie wird ebenfalls erst beim ersten Verbindungsaufbau gebaut).
     */
    public static SSLSocketFactory deferred(ClientCertificateConfig config, SecretProvider secrets,
                                            NetworkServices network) {
        requireConfig(config, secrets);
        if (network == null) {
            throw new IllegalArgumentException("network must not be null");
        }
        return new Deferred(config, secrets, network);
    }

    private static void requireConfig(ClientCertificateConfig config, SecretProvider secrets) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (!config.usesWindowsStore() && config.keyStorePasswordRef() != null && secrets == null) {
            throw new IllegalArgumentException("secrets must not be null when a keyStorePasswordRef is configured");
        }
    }

    private static SSLSocketFactory fromFile(ClientCertificateConfig config, char[] password, X509TrustManager trust)
            throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(config.keyStoreFile())) {
            store.load(in, password);
        }
        return ClientCertificates.fromKeyStore(store, password, config.alias(), trust);
    }

    /** Lädt die eigentliche Factory beim ersten {@code createSocket}; Cipher-Suites kommen bis dahin vom JDK-Default. */
    private static final class Deferred extends SSLSocketFactory {

        private final ClientCertificateConfig config;
        private final SecretProvider secrets;
        private final NetworkServices network;
        private SSLSocketFactory loaded;

        Deferred(ClientCertificateConfig config, SecretProvider secrets, NetworkServices network) {
            this.config = config;
            this.secrets = secrets;
            this.network = network;
        }

        private synchronized SSLSocketFactory delegate() throws IOException {
            if (loaded == null) {
                try {
                    X509TrustManager trust = network == null ? null : network.trustManager();
                    loaded = create(config, secrets, trust);
                } catch (GeneralSecurityException | SecretUnavailableException e) {
                    throw new IOException("Client-Zertifikat nicht nutzbar: " + e.getMessage(), e);
                } catch (RuntimeException e) {
                    throw new IOException("TLS-Vertrauensquellen nicht nutzbar: " + e.getMessage(), e);
                }
            }
            return loaded;
        }

        private SSLSocketFactory loadedOrDefault() {
            synchronized (this) {
                if (loaded != null) {
                    return loaded;
                }
            }
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return loadedOrDefault().getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return loadedOrDefault().getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket() throws IOException {
            return delegate().createSocket();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return delegate().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return delegate().createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return delegate().createSocket(host, port, localHost, localPort);
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return delegate().createSocket(host, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return delegate().createSocket(address, port, localAddress, localPort);
        }

        @Override
        public synchronized String toString() {
            return "DeferredClientCertificate[loaded=" + (loaded != null) + "]";
        }
    }
}
