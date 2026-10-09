package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.application.rag.RetrievalSettings;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Konfiguration: Beispieldatei vollständig, Pflichtfelder, Fehlermeldungen ohne Werte, keine Secrets in toString. */
public class AppConfigLoaderTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static Properties minimal() throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(""
                + "chat.baseUrl=https://ki.example/v1\n"
                + "chat.model=openai/gpt-oss-120b\n"
                + "chat.apiKeyRef=Enterprise AI API\n"
                + "embedding.model=danielheinz/e5-base-sts-en-de\n"
                + "embedding.dimension=768\n"
                + "security.keepass.enabled=false\n"));
        return p;
    }

    @Test
    public void exampleConfigurationLoadsWithoutProblemsOrWarnings() throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals("Enterprise AI Client", config.windowTitle());
        assertEquals("openai/gpt-oss-120b", config.chat().model());
        assertEquals(768, config.embedding().dimension());
        assertTrue("Beispielquellen sind auskommentiert, damit ein frischer Client nicht gegen Beispielhosts indexiert",
                config.sources().isEmpty());
        assertTrue(config.keePass().enabled());
        assertEquals(ProxyMode.AUTO, config.network().proxyMode());
        assertNull(config.network().pacUrl());
        assertEquals(PacDiscovery.WINDOWS_SETTINGS, config.network().pacDiscovery());
        assertTrue(config.network().useWindowsCertificateStore());
        assertNull(config.network().caCertificatesFile());
        assertFalse(config.agent().enabled());
        assertEquals("keine Warnungen erwartet: " + config.warnings(), 0, config.warnings().size());
    }

    @Test
    public void exampleSourceBlocksLoadOnceUncommented() throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(AppConfigLoader.exampleConfiguration().replace("\n#source.", "\nsource.")));
        p.setProperty("sources", "wiki,confluence");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(2, config.sources().size());
        assertEquals("mediawiki", config.sources().get(0).type());
        assertEquals("confluence", config.sources().get(1).type());
        assertEquals("keine Warnungen erwartet: " + config.warnings(), 0, config.warnings().size());
    }

    @Test
    public void proxyModeDefaultsToAutoAndPacKeysAreValidated() throws Exception {
        NetworkConfig network = AppConfigLoader.fromProperties(minimal()).network();
        assertEquals(ProxyMode.AUTO, network.proxyMode());
        assertNull(network.pacUrl());
        assertEquals(PacDiscovery.WINDOWS_SETTINGS, network.pacDiscovery());

        Properties p = minimal();
        p.setProperty("network.proxy.pacUrl", "http://wpad.intern.example/wpad.dat");
        p.setProperty("network.proxy.pacDiscovery", "POWERSHELL");
        network = AppConfigLoader.fromProperties(p).network();
        assertEquals("http://wpad.intern.example/wpad.dat", network.pacUrl());
        assertEquals(PacDiscovery.POWERSHELL, network.pacDiscovery());
        assertTrue(network.toString(), network.toString().contains("pac=http://wpad.intern.example/wpad.dat"));

        p.setProperty("network.proxy.pacUrl", "C:\\Skripte\\proxy.pac");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.proxy.pacUrl: keine absolute"));
            assertFalse("der Wert steht nicht in der Meldung", e.problems().toString().contains("Skripte"));
        }
        p.setProperty("network.proxy.pacUrl", "file:///C:/Skripte/proxy.pac");
        p.setProperty("network.proxy.pacDiscovery", "REGISTRY");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.proxy.pacDiscovery"));
        }
    }

    @Test
    public void tlsKeysAreReadWithDefaults() throws Exception {
        Properties p = minimal();
        p.setProperty("network.tls.useWindowsCertificateStore", "false");
        p.setProperty("network.tls.caCertificatesFile", "C:/Zertifikate/firmen-ca.pem");
        NetworkConfig network = AppConfigLoader.fromProperties(p).network();
        assertFalse(network.useWindowsCertificateStore());
        assertEquals("firmen-ca.pem", network.caCertificatesFile().getFileName().toString());
        assertTrue(AppConfigLoader.fromProperties(minimal()).network().useWindowsCertificateStore());
        p.setProperty("network.tls.useWindowsCertificateStore", "vielleicht");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.tls.useWindowsCertificateStore"));
            assertFalse(e.problems().toString().contains("vielleicht"));
        }
    }

    @Test
    public void minimalConfigurationFallsBackToDefaults() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(minimal());
        assertEquals("https://ki.example/v1", config.chat().baseUrl().toString());
        assertEquals(SecretRef.of("Enterprise AI API"), config.chat().apiKeyRef());
        assertEquals("Embeddings erben Basis-URL und API-Key vom Chat",
                config.chat().baseUrl(), config.embedding().baseUrl());
        assertEquals(config.chat().apiKeyRef(), config.embedding().apiKeyRef());
        assertEquals(RetrievalSettings.DEFAULT_MAX_RESULTS, config.knowledge().retrieval().maxResults());
        assertTrue(config.knowledge().indexOnStartup());
        assertNotNull(config.knowledge().indexDirectory());
        assertTrue(config.sources().isEmpty());
        assertFalse(config.keePass().enabled());
        assertNull(config.agent().command());
    }

    @Test
    public void keePassDisabledWithSecretReferencesIsWarnedNotRejected() throws Exception {
        AppConfig config = AppConfigLoader.fromProperties(minimal());
        assertEquals(1, config.warnings().size());
        assertTrue(config.warnings().get(0).contains("chat.apiKeyRef"));
        assertFalse("Referenz-Werte gehören nicht in Warnungen", config.warnings().get(0).contains("Enterprise AI API"));
    }

    @Test
    public void missingRequiredKeysAreAllReportedAtOnce() {
        try {
            AppConfigLoader.fromProperties(new Properties());
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            String all = String.join("\n", e.problems());
            assertTrue(all, all.contains("chat.baseUrl"));
            assertTrue(all, all.contains("chat.model"));
            assertTrue(all, all.contains("chat.apiKeyRef"));
            assertTrue(all, all.contains("embedding.model"));
            assertTrue(all, all.contains("embedding.dimension"));
        }
    }

    @Test
    public void problemsNameTheKeyButNeverTheValue() throws Exception {
        Properties p = minimal();
        p.setProperty("chat.baseUrl", "ftp://geheimer-host.example/secret-path");
        p.setProperty("embedding.dimension", "siebenhundert");
        p.setProperty("chat.temperature", "9.9");
        p.setProperty("sources", "wiki");
        p.setProperty("source.wiki.type", "mediawiki");
        p.setProperty("source.wiki.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.wiki.maxDepth", "-7");
        // Wird erst vom Builder der Adapter-Konfiguration abgelehnt (Großbuchstaben); dessen Meldung nennt den Wert.
        p.setProperty("source.wiki.siteKey", "Geheimer-SiteKey");
        try {
            AppConfigLoader.fromProperties(p);
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            String message = e.getMessage();
            assertTrue(message, message.contains("chat.baseUrl"));
            assertTrue(message, message.contains("embedding.dimension"));
            assertTrue(message, message.contains("chat.temperature"));
            assertTrue(message, message.contains("source.wiki.maxDepth"));
            assertTrue(message, message.contains("source.wiki.*"));
            assertFalse(message, message.contains("geheimer-host"));
            assertFalse(message, message.contains("secret-path"));
            assertFalse(message, message.contains("siebenhundert"));
            assertFalse(message, message.contains("9.9"));
            assertFalse(message, message.contains("-7"));
            assertFalse("verschachtelte Adapter-Meldungen dürfen den Wert nicht durchreichen: " + message,
                    message.contains("Geheimer-SiteKey"));
        }
    }

    @Test
    public void unknownKeysBecomeWarningsWithoutValues() throws Exception {
        Properties p = minimal();
        p.setProperty("chat.apiKey", "sk-sollte-nie-erscheinen");
        AppConfig config = AppConfigLoader.fromProperties(p);
        boolean found = false;
        for (String warning : config.warnings()) {
            assertFalse(warning, warning.contains("sk-sollte-nie-erscheinen"));
            found |= warning.contains("chat.apiKey");
        }
        assertTrue(config.warnings().toString(), found);
    }

    @Test
    public void sourcesAreParsedWithScopeAndCredentials() throws Exception {
        Properties p = minimal();
        p.setProperty("security.keepass.enabled", "true");
        p.setProperty("sources", "wiki, confluence");
        p.setProperty("source.wiki.type", "mediawiki");
        p.setProperty("source.wiki.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.wiki.siteKey", "intern");
        p.setProperty("source.wiki.credentialRef", "Intranet-Wiki");
        p.setProperty("source.wiki.startPoints", "Hauptseite, Handbuch");
        p.setProperty("source.wiki.maxDepth", "1");
        p.setProperty("source.confluence.type", "confluence");
        p.setProperty("source.confluence.baseUrl", "https://confluence.example/c");
        p.setProperty("source.confluence.credentialRef", "Confluence");
        p.setProperty("source.confluence.startPoints", "space:DEV");
        p.setProperty("source.confluence.clientCertificate.alias", "mein-zertifikat");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(2, config.sources().size());
        MediaWikiSourceConfig wiki = (MediaWikiSourceConfig) config.sources().get(0);
        assertEquals("wiki", wiki.sourceId().value());
        assertEquals(SecretRef.of("Intranet-Wiki"), wiki.credentialRef());
        assertEquals(2, wiki.scope().startPoints().size());
        assertEquals(1, wiki.scope().maxDepth());
        ConfluenceSourceConfig confluence = (ConfluenceSourceConfig) config.sources().get(1);
        assertEquals(SecretRef.of("Confluence"), confluence.confluence().credentialRef());
        assertNotNull(confluence.clientCertificate());
        assertTrue(confluence.clientCertificate().usesWindowsStore());
        assertEquals(0, config.warnings().size());
    }

    @Test
    public void duplicateOrUnknownSourceTypesAreProblems() throws Exception {
        Properties p = minimal();
        p.setProperty("sources", "a,a,b");
        p.setProperty("source.a.type", "mediawiki");
        p.setProperty("source.a.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.b.type", "sharepoint");
        try {
            AppConfigLoader.fromProperties(p);
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            String all = String.join("\n", e.problems());
            assertTrue(all, all.contains("sources"));
            assertTrue(all, all.contains("source.b.type"));
        }
    }

    @Test
    public void manualProxyNeedsHostAndPort() throws Exception {
        Properties p = minimal();
        p.setProperty("network.proxy.mode", "MANUAL");
        try {
            AppConfigLoader.fromProperties(p);
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            String all = String.join("\n", e.problems());
            assertTrue(all, all.contains("network.proxy.host"));
        }
        p.setProperty("network.proxy.host", "proxy.example");
        p.setProperty("network.proxy.port", "3128");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(ProxyMode.MANUAL, config.network().proxyMode());
        assertEquals(3128, config.network().proxyPort());
    }

    @Test
    public void agentNeedsCommandWhenEnabledAndSplitsArguments() throws Exception {
        Properties p = minimal();
        p.setProperty("agent.enabled", "true");
        try {
            AppConfigLoader.fromProperties(p);
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("agent.command"));
        }
        p.setProperty("agent.command", "java");
        p.setProperty("agent.args", "-jar \"C:/Programme/mein agent/agent.jar\" --stdio");
        p.setProperty("agent.tools.defaultMaxResults", "3");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals("java", config.agent().command());
        assertEquals(3, config.agent().args().size());
        assertEquals("C:/Programme/mein agent/agent.jar", config.agent().args().get(1));
        assertEquals(3, config.agent().toolSettings().defaultMaxResults());
    }

    @Test
    public void snapshotsShowReferencesButNoSecretValues() throws Exception {
        Properties p = minimal();
        p.setProperty("security.keepass.enabled", "true");
        AppConfig config = AppConfigLoader.fromProperties(p);
        String text = config.toString();
        assertTrue(text, text.contains("apiKeyRef"));
        assertTrue(text, text.contains("Enterprise AI API"));
        assertFalse(text.toLowerCase(), text.toLowerCase().contains("password"));
        assertFalse(text, text.contains("sk-"));
    }

    @Test
    public void missingFileIsReportedWithItsPath() throws Exception {
        Path file = temp.getRoot().toPath().resolve("gibt-es-nicht.properties");
        try {
            AppConfigLoader.load(file);
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(file.toAbsolutePath().toString()));
        }
    }

    @Test
    public void fileIsReadAsUtf8() throws Exception {
        Path file = temp.getRoot().toPath().resolve("app.properties");
        Files.write(file, ("chat.baseUrl=https://ki.example/v1\nchat.model=m\nchat.apiKeyRef=Schlüssel\n"
                + "embedding.model=e\nembedding.dimension=8\nui.windowTitle=Größe\n").getBytes(StandardCharsets.UTF_8));
        AppConfig config = AppConfigLoader.load(file);
        assertEquals("Größe", config.windowTitle());
        assertEquals(SecretRef.of("Schlüssel"), config.chat().apiKeyRef());
    }
}
