package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.application.rag.RetrievalSettings;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSourceProvider;
import com.aresstack.winproxy.ProxyMode;
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
        assertEquals(ProxyMode.PAC_URL_POWERSHELL, config.network().proxyMode());
        assertNull(config.network().pacUrl());
        assertTrue(config.network().tlsUseJvmDefault());
        assertTrue(config.network().tlsUseWindowsRoot());
        assertTrue(config.network().tlsUseWindowsCaStores());
        assertNull(config.network().caCertificatesFile());
        assertFalse(config.agent().enabled());
        assertEquals("keine Warnungen erwartet: " + config.warnings(), 0, config.warnings().size());
    }

    @Test
    public void exampleSourceBlocksLoadOnceUncommented() throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(AppConfigLoader.exampleConfiguration().replace("\n#source.", "\nsource.")));
        p.setProperty("sources", "wiki,confluence,dateien,cobol");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(4, config.sources().size());
        assertEquals("mediawiki", config.sources().get(0).typeId());
        assertEquals("confluence", config.sources().get(1).typeId());
        assertEquals("files", config.sources().get(2).typeId());
        assertEquals("ftp", config.sources().get(3).typeId());
        assertEquals("keine Warnungen erwartet: " + config.warnings(), 0, config.warnings().size());
    }

    @Test
    public void proxyModeDefaultsToPowerShellDiscoveryAndPacKeysAreValidated() throws Exception {
        NetworkConfig network = AppConfigLoader.fromProperties(minimal()).network();
        assertEquals(ProxyMode.PAC_URL_POWERSHELL, network.proxyMode());
        assertNull(network.pacUrl());
        assertNull("leer = Standardskript der Bibliothek", network.pacDiscoveryScript());
        assertEquals(ProxyAuthMode.NONE, network.proxyAuthMode());
        assertFalse(network.preferIpv6());

        Properties p = minimal();
        p.setProperty("network.proxy.mode", "PAC_URL_MANUAL");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.proxy.pacUrl: fehlt"));
        }
        p.setProperty("network.proxy.pacUrl", "http://wpad.intern.example/wpad.dat");
        network = AppConfigLoader.fromProperties(p).network();
        assertEquals(ProxyMode.PAC_URL_MANUAL, network.proxyMode());
        assertEquals("http://wpad.intern.example/wpad.dat", network.pacUrl());
        assertTrue(network.toString(), network.toString().contains("pacUrl=http://wpad.intern.example/wpad.dat"));

        p.setProperty("network.proxy.pacUrl", "C:\\Skripte\\proxy.pac");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.proxy.pacUrl: keine absolute"));
            assertFalse("der Wert steht nicht in der Meldung", e.problems().toString().contains("Skripte"));
        }
        p.setProperty("network.proxy.pacUrl", "http://wpad.intern.example/wpad.dat");
        p.setProperty("network.proxy.mode", "REGISTRY");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.proxy.mode"));
        }
    }

    @Test
    public void everyLibraryModeNameIsAccepted() throws Exception {
        for (ProxyMode mode : ProxyMode.values()) {
            Properties p = minimal();
            p.setProperty("network.proxy.mode", mode.name());
            p.setProperty("network.proxy.host", "proxy.intern.example");
            p.setProperty("network.proxy.port", "8080");
            p.setProperty("network.proxy.pacUrl", "http://wpad.intern.example/wpad.dat");
            assertEquals(mode, AppConfigLoader.fromProperties(p).network().proxyMode());
        }
    }

    @Test
    public void legacyModeNamesAreMigratedWithAWarning() throws Exception {
        assertEquals(ProxyMode.DISABLED, AppConfigLoader.legacyMode("NONE", null));
        assertEquals(ProxyMode.MANUAL_PROXY, AppConfigLoader.legacyMode("MANUAL", null));
        assertEquals(ProxyMode.WINDOWS_STATIC_PROXY, AppConfigLoader.legacyMode("SYSTEM", null));
        assertEquals(ProxyMode.PAC_URL_POWERSHELL, AppConfigLoader.legacyMode("AUTO", null));
        assertEquals(ProxyMode.PAC_URL_MANUAL, AppConfigLoader.legacyMode("AUTO", "http://wpad.intern.example/wpad.dat"));
        assertNull(AppConfigLoader.legacyMode("DISABLED", null));

        Properties p = minimal();
        p.setProperty("network.proxy.mode", "AUTO");
        p.setProperty("network.proxy.pacDiscovery", "POWERSHELL");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(ProxyMode.PAC_URL_POWERSHELL, config.network().proxyMode());
        String all = config.warnings().toString();
        assertTrue(all, all.contains("network.proxy.mode=AUTO"));
        assertTrue(all, all.contains("network.proxy.pacDiscovery"));
    }

    @Test
    public void testUrlDefaultsToTheModelsEndpointOfTheChatService() throws Exception {
        NetworkConfig network = AppConfigLoader.fromProperties(minimal()).network();
        assertTrue(network.testUrl().toString(), network.testUrl().toString().endsWith("/models"));
        Properties p = minimal();
        p.setProperty("network.proxy.testUrl", "https://ki.intern.example/v1/models");
        assertEquals("https://ki.intern.example/v1/models",
                AppConfigLoader.fromProperties(p).network().testUrl().toString());
    }

    @Test
    public void basicProxyAuthNeedsAKeePassEntry() throws Exception {
        Properties p = minimal();
        p.setProperty("network.proxy.auth.mode", "BASIC");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.proxy.auth.credentialRef"));
        }
        p.setProperty("network.proxy.auth.credentialRef", "Firmen-Proxy");
        NetworkConfig network = AppConfigLoader.fromProperties(p).network();
        assertEquals(ProxyAuthMode.BASIC, network.proxyAuthMode());
        assertEquals("Firmen-Proxy", network.proxyCredentialRef().id());
    }

    @Test
    public void tlsKeysAreReadWithDefaults() throws Exception {
        Properties p = minimal();
        p.setProperty("network.tls.useJvmDefault", "false");
        p.setProperty("network.tls.useWindowsCaStores", "false");
        p.setProperty("network.tls.caCertificatesFile", "C:/Zertifikate/firmen-ca.pem");
        NetworkConfig network = AppConfigLoader.fromProperties(p).network();
        assertFalse(network.tlsUseJvmDefault());
        assertTrue(network.tlsUseWindowsRoot());
        assertFalse(network.tlsUseWindowsCaStores());
        assertEquals("firmen-ca.pem", network.caCertificatesFile().getFileName().toString());
        p.setProperty("network.tls.useWindowsRoot", "vielleicht");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("network.tls.useWindowsRoot"));
            assertFalse(e.problems().toString().contains("vielleicht"));
        }
    }

    @Test
    public void legacyWindowsStoreSwitchStillAppliesToBothWindowsSources() throws Exception {
        Properties p = minimal();
        p.setProperty("network.tls.useWindowsCertificateStore", "false");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertFalse(config.network().tlsUseWindowsRoot());
        assertFalse(config.network().tlsUseWindowsCaStores());
        assertTrue(config.network().tlsUseJvmDefault());
        assertTrue(config.warnings().toString(), config.warnings().toString().contains("useWindowsCertificateStore"));
    }

    @Test
    public void baseUrlsMustNotNameAnEndpointPath() throws Exception {
        Properties p = minimal();
        p.setProperty("chat.baseUrl", "https://ki.intern.example/v1/chat/completions");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().get(0).startsWith("chat.baseUrl"));
            assertTrue(e.problems().toString(), e.problems().get(0).contains("Endpunktpfad"));
        }
        p = minimal();
        p.setProperty("embedding.baseUrl", "https://ki.intern.example/v1/embeddings");
        try {
            AppConfigLoader.fromProperties(p);
            fail();
        } catch (AppConfigException e) {
            assertTrue(e.problems().toString(), e.problems().toString().contains("embedding.baseUrl"));
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
        try {
            AppConfigLoader.fromProperties(p);
            fail("expected AppConfigException");
        } catch (AppConfigException e) {
            String message = e.getMessage();
            assertTrue(message, message.contains("chat.baseUrl"));
            assertTrue(message, message.contains("embedding.dimension"));
            assertTrue(message, message.contains("chat.temperature"));
            assertFalse(message, message.contains("geheimer-host"));
            assertFalse(message, message.contains("secret-path"));
            assertFalse(message, message.contains("siebenhundert"));
            assertFalse(message, message.contains("9.9"));
        }
    }

    @Test
    public void brokenSourcesAreSkippedWithAWarningThatNamesKeysButNoValues() throws Exception {
        Properties p = minimal();
        p.setProperty("sources", "wiki, ok");
        p.setProperty("source.wiki.type", "mediawiki");
        p.setProperty("source.wiki.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.wiki.maxDepth", "-7");
        p.setProperty("source.ok.type", "mediawiki");
        p.setProperty("source.ok.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.ok.startPoints", "Hauptseite");
        // Der Loader liest Quellen typneutral; ob die Einstellungen stimmen, prüft der Adapter des Typs.
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertEquals(config.warnings().toString(), 2, config.sources().size());
        assertEquals("ok", config.sources().get(1).id());
        assertEquals("-7", config.sources().get(0).settings().get("maxDepth"));
        String all = String.join("\n", config.warnings());
        assertFalse("gelesene Quell-Schlüssel sind keine unbekannten Schlüssel: " + all, all.contains("ignoriert"));
        java.util.List<String> problems = new MediaWikiSourceProvider(ref -> null, null, null, "Test")
                .validate(config.sources().get(0).settings());
        assertFalse(problems.isEmpty());
        assertFalse(problems.toString(), problems.toString().contains("-7"));
    }

    @Test
    public void sourceEnabledDefaultsToTrueAndCanBeSwitchedOff() throws Exception {
        Properties p = minimal();
        p.setProperty("sources", "wiki");
        p.setProperty("source.wiki.type", "mediawiki");
        p.setProperty("source.wiki.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.wiki.startPoints", "Hauptseite");
        assertTrue(AppConfigLoader.fromProperties(p).sources().get(0).enabled());
        p.setProperty("source.wiki.enabled", "false");
        AppConfig config = AppConfigLoader.fromProperties(p);
        assertFalse(config.sources().get(0).enabled());
        for (String warning : config.warnings()) {
            assertFalse(warning, warning.contains("source.wiki"));
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
        SourceDefinition wiki = config.sources().get(0);
        assertEquals("wiki", wiki.id());
        assertEquals("Intranet-Wiki", wiki.settings().get("credentialRef"));
        assertEquals("Hauptseite, Handbuch", wiki.settings().get("startPoints"));
        SourceDefinition confluence = config.sources().get(1);
        assertEquals("confluence", confluence.typeId());
        assertEquals("mein-zertifikat", confluence.settings().get("clientCertificate.alias"));
        assertEquals(0, config.warnings().size());
    }

    @Test
    public void duplicateOrUnknownSourceTypesAreSkippedWithWarnings() throws Exception {
        Properties p = minimal();
        p.setProperty("sources", "a,a,b,Ungültig!");
        p.setProperty("source.a.type", "mediawiki");
        p.setProperty("source.a.apiUrl", "https://wiki.example/w/api.php");
        p.setProperty("source.a.startPoints", "Hauptseite");
        p.setProperty("source.b.type", "sharepoint");
        AppConfig config = AppConfigLoader.fromProperties(p);
        // Unbekannte Typen bleiben stehen (der Reiter zeigt sie als fehlerhaft); doppelte und ungültige IDs nicht.
        assertEquals(config.warnings().toString(), 2, config.sources().size());
        assertEquals("a", config.sources().get(0).id());
        assertEquals("sharepoint", config.sources().get(1).typeId());
        String all = String.join("\n", config.warnings());
        assertTrue(all, all.contains("„a“ steht doppelt"));
        assertTrue(all, all.contains("„Ungültig!“ wird übersprungen"));
    }

    @Test
    public void manualProxyNeedsHostAndPort() throws Exception {
        Properties p = minimal();
        p.setProperty("network.proxy.mode", "MANUAL_PROXY");
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
        assertEquals(ProxyMode.MANUAL_PROXY, config.network().proxyMode());
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
