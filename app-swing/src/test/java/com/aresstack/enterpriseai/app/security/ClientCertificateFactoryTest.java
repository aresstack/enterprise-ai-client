package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.ClientCertificateConfig;
import com.aresstack.enterpriseai.app.config.ConfluenceSourceConfig;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Die verzögerte Factory holt das KeyStore-Passwort erst beim Verbindungsaufbau; ein nicht erreichbarer Tresor
 * lässt nur die Verbindung scheitern (je Versuch erneut), nie den Start.
 */
public class ClientCertificateFactoryTest {

    private static final SecretRef REF = SecretRef.of("Client-Zertifikat");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private ClientCertificateConfig certificateConfig(Path keyStoreFile, boolean withPasswordRef) throws Exception {
        Properties p = new Properties();
        // Die Vorlage liefert die Beispielquellen auskommentiert; hier wird der Confluence-Block aktiviert.
        p.load(new StringReader(AppConfigLoader.exampleConfiguration().replace("\n#source.", "\nsource.")));
        p.setProperty("sources", "confluence");
        p.setProperty("knowledge.indexDirectory", temp.getRoot().toPath().resolve("index").toString());
        p.setProperty("source.confluence.clientCertificate.alias", "client");
        p.setProperty("source.confluence.clientCertificate.keyStoreFile", keyStoreFile.toString());
        if (withPasswordRef) {
            p.setProperty("source.confluence.clientCertificate.keyStorePasswordRef", REF.id());
        }
        AppConfig config = AppConfigLoader.fromProperties(p);
        for (SourceConfig source : config.sources()) {
            if (source instanceof ConfluenceSourceConfig) {
                return ((ConfluenceSourceConfig) source).clientCertificate();
            }
        }
        throw new AssertionError("Beispielkonfiguration ohne Confluence-Quelle");
    }

    @Test
    public void deferredFactoryResolvesThePasswordOnlyOnConnectAndRetriesPerAttempt() throws Exception {
        ClientCertificateConfig config = certificateConfig(temp.newFile("client.p12").toPath(), true);
        RecordingSecretProvider secrets = new RecordingSecretProvider(new SecretUnavailableException(
                SecretUnavailableException.Reason.NOT_AVAILABLE, REF, "KeePass gesperrt"));

        SSLSocketFactory factory = ClientCertificateFactory.deferred(config, secrets);
        assertTrue(factory.getDefaultCipherSuites().length > 0);
        assertTrue(factory.getSupportedCipherSuites().length > 0);
        assertEquals("Cipher-Suites holen kein Secret", 0, secrets.requests.size());

        try {
            factory.createSocket("127.0.0.1", 1);
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(String.valueOf(e.getCause()), e.getCause() instanceof SecretUnavailableException);
            assertTrue(e.getMessage(), e.getMessage().contains("Client-Zertifikat nicht nutzbar"));
        }
        assertEquals(1, secrets.requests.size());
        assertEquals(REF, secrets.requests.get(0));

        try {
            factory.createSocket("127.0.0.1", 1);
            fail("expected IOException");
        } catch (IOException e) {
            assertEquals("jeder Verbindungsaufbau fragt erneut, damit ein entsperrter Tresor ohne Neustart greift",
                    2, secrets.requests.size());
        }
        assertFalse(factory.toString(), factory.toString().contains("gesperrt"));
        assertTrue(factory.toString(), factory.toString().contains("loaded=false"));
    }

    @Test
    public void deferredFactoryWithoutPasswordFailsOnlyOnConnectWhenTheFileIsMissing() throws Exception {
        ClientCertificateConfig config = certificateConfig(temp.getRoot().toPath().resolve("fehlt.p12"), false);
        SSLSocketFactory factory = ClientCertificateFactory.deferred(config, null);
        try {
            factory.createSocket("127.0.0.1", 1);
            fail("expected IOException");
        } catch (IOException e) {
            assertTrue(String.valueOf(e), e instanceof java.nio.file.NoSuchFileException
                    || e.getCause() instanceof java.nio.file.NoSuchFileException
                    || e.getMessage().contains("fehlt.p12"));
        }
    }

    @Test
    public void deferredFactoryRequiresASecretProviderWhenAPasswordIsConfigured() throws Exception {
        ClientCertificateConfig config = certificateConfig(temp.newFile("client.p12").toPath(), true);
        try {
            ClientCertificateFactory.deferred(config, null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("secrets"));
        }
    }
}
