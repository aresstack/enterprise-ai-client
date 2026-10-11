package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigException;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.proasteion.application.config.ProxyAuthMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.winproxy.ProxyMode;
import org.junit.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Formular ↔ Properties: der Loader muss das Ergebnis des Dialogs genauso lesen wie eine von Hand gepflegte Datei. */
public class SettingsMapperTest {

    static Properties valid() {
        Properties p = new Properties();
        p.setProperty("ui.windowTitle", "Test-Client");
        p.setProperty("chat.baseUrl", "http://127.0.0.1:9/v1");
        p.setProperty("chat.model", "test-chat");
        p.setProperty("chat.apiKeyRef", "keepass:Enterprise AI API");
        p.setProperty("chat.systemPrompt", "Antworte kurz.");
        p.setProperty("embedding.model", "test-embedding");
        p.setProperty("embedding.dimension", "8");
        p.setProperty("knowledge.indexOnStartup", "false");
        p.setProperty("retrieval.maxResults", "5");
        p.setProperty("source.1.type", "mediawiki");
        p.setProperty("source.1.apiUrl", "http://127.0.0.1:9/w/api.php");
        p.setProperty("source.1.displayName", "Intranet-Wiki");
        p.setProperty("source.1.startPoints", "Hauptseite,Handbuch");
        p.setProperty("source.1.maxDepth", "2");
        p.setProperty("source.1.connectTimeoutMillis", "15000");
        p.setProperty("source.2.type", "confluence");
        p.setProperty("source.2.baseUrl", "http://127.0.0.1:9/confluence");
        p.setProperty("source.2.credentialRef", "keepass:Confluence");
        p.setProperty("source.2.startPoints", "space:DEV");
        p.setProperty("source.2.searchSpaceKeys", "DEV");
        p.setProperty("source.2.includeAttachments", "true");
        p.setProperty("source.2.allowInsecureHttp", "true");
        p.setProperty("security.keepass.enabled", "false");
        p.setProperty("network.proxy.mode", "DISABLED");
        return p;
    }

    static Properties template() throws Exception {
        Properties p = new Properties();
        p.load(new StringReader(AppConfigLoader.exampleConfiguration()));
        return p;
    }

    @Test
    public void readsTheManagedKeysIntoTheForm() {
        SettingsForm form = SettingsMapper.fromProperties(valid());
        assertEquals("Test-Client", form.windowTitle());
        assertEquals("http://127.0.0.1:9/v1", form.chatBaseUrl());
        assertEquals("test-chat", form.chatModel());
        assertEquals("keepass:Enterprise AI API", form.chatApiKeyRef());
        assertEquals("", form.embeddingBaseUrl());
        assertEquals("8", form.embeddingDimension());
        assertEquals("", form.indexDirectory());
        assertFalse(form.keePassEnabled());
        assertEquals(SettingsForm.PROXY_DISABLED, form.proxyMode());
    }

    @Test
    public void unchangedFormLeavesTheFileSemanticallyUntouched() {
        Properties current = valid();
        SettingsForm form = SettingsMapper.fromProperties(current);
        Properties merged = SettingsMapper.merge(current, form);
        for (String key : current.stringPropertyNames()) {
            assertEquals(key, current.getProperty(key), merged.getProperty(key));
        }
        // Der Dialog schreibt, was er zeigt: Standardwerte des Loaders für Felder, die in der Datei fehlten.
        Properties added = new Properties();
        for (String key : merged.stringPropertyNames()) {
            if (!current.containsKey(key)) {
                added.setProperty(key, merged.getProperty(key));
            }
        }
        Properties expected = new Properties();
        expected.setProperty("security.keepass.host", "127.0.0.1");
        expected.setProperty("security.keepass.port", "12546");
        expected.setProperty("security.keepass.clientDisplayName", "Enterprise AI Client");
        expected.setProperty("security.keepass.pairingKeyStore", "file");
        expected.setProperty("network.proxy.auth.mode", "NONE");
        expected.setProperty("network.http.preferIPv6", "false");
        expected.setProperty("network.tls.useJvmDefault", "true");
        expected.setProperty("network.tls.useWindowsRoot", "true");
        expected.setProperty("network.tls.useWindowsCaStores", "true");
        expected.setProperty("agent.enabled", "false");
        expected.setProperty("agent.requestTimeoutSeconds", "30");
        assertEquals(expected, added);
        AppConfig config = AppConfigLoader.fromProperties(merged);
        assertEquals("test-chat", config.chat().model());
    }

    @Test
    public void legacyProxyAndTlsKeysAreMigratedAndRemovedOnSave() {
        Properties current = valid();
        current.setProperty("network.proxy.mode", "auto");
        current.setProperty("network.proxy.pacUrl", "file:///C:/wpad.dat");
        current.setProperty("network.proxy.pacDiscovery", "powershell");
        current.setProperty("network.tls.useWindowsCertificateStore", "false");
        current.setProperty("network.tls.caCertificatesFile", "C:/Zertifikate/firmen-ca.pem");
        SettingsForm form = SettingsMapper.fromProperties(current);
        assertEquals("AUTO mit PAC-URL wird PAC_URL_MANUAL", SettingsForm.PROXY_PAC_URL_MANUAL, form.proxyMode());
        assertEquals("file:///C:/wpad.dat", form.pacUrl());
        assertTrue(form.tlsJvmDefault());
        assertFalse(form.tlsWindowsRoot());
        assertFalse(form.tlsWindowsCaStores());
        assertEquals("C:/Zertifikate/firmen-ca.pem", form.caCertificatesFile());

        Set<String> removals = SettingsMapper.removals(form, current);
        assertTrue(removals.contains("network.proxy.pacDiscovery"));
        assertTrue(removals.contains("network.tls.useWindowsCertificateStore"));
        Properties merged = SettingsMapper.merge(current, form);
        for (String key : removals) {
            merged.remove(key);
        }
        assertEquals("PAC_URL_MANUAL", merged.getProperty("network.proxy.mode"));
        AppConfig config = AppConfigLoader.fromProperties(merged);
        assertEquals(ProxyMode.PAC_URL_MANUAL, config.network().proxyMode());
        assertEquals("file:///C:/wpad.dat", config.network().pacUrl());
        assertFalse(config.network().tlsUseWindowsRoot());
        assertEquals("C:/Zertifikate/firmen-ca.pem", config.network().caCertificatesFile().toString());
        for (String warning : config.warnings()) {
            assertFalse("nach dem Speichern keine Altlast-Warnungen: " + warning, warning.startsWith("network."));
        }

        SettingsForm cleared = form.toBuilder().pacUrl("").caCertificatesFile("").build();
        removals = SettingsMapper.removals(cleared, current);
        assertTrue(removals.contains("network.proxy.pacUrl"));
        assertTrue(removals.contains("network.tls.caCertificatesFile"));
    }

    @Test
    public void legacyModeNamesMapToLibraryModes() {
        String[][] cases = {{"NONE", "DISABLED"}, {"MANUAL", "MANUAL_PROXY"}, {"SYSTEM", "WINDOWS_STATIC_PROXY"},
                {"AUTO", "PAC_URL_POWERSHELL"}};
        for (String[] c : cases) {
            Properties p = valid();
            p.setProperty("network.proxy.mode", c[0]);
            assertEquals(c[0], c[1], SettingsMapper.fromProperties(p).proxyMode());
        }
    }

    @Test
    public void multiLineDiscoveryScriptAndProxyAuthRoundTrip() {
        Properties current = valid();
        SettingsForm form = SettingsMapper.fromProperties(current).toBuilder()
                .proxyMode(SettingsForm.PROXY_PAC_URL_POWERSHELL)
                .pacDiscoveryScript("$p = 'http://wpad.intern.example/wpad.dat'\nWrite-Output $p\n")
                .proxyAuthMode(SettingsForm.PROXY_AUTH_BASIC).proxyCredentialRef("keepass:Firmen-Proxy")
                .userAgent("Mozilla/5.0 Test").preferIpv6(true).build();
        Properties merged = SettingsMapper.merge(current, form);
        assertEquals("$p = 'http://wpad.intern.example/wpad.dat'\nWrite-Output $p\n",
                merged.getProperty("network.proxy.pacDiscoveryScript"));
        assertFalse("kein Secret in der Datei", merged.toString().toLowerCase().contains("password"));
        AppConfig config = AppConfigLoader.fromProperties(merged);
        assertEquals(ProxyAuthMode.BASIC, config.network().proxyAuthMode());
        assertEquals("keepass:Firmen-Proxy", config.network().proxyCredentialRef().id());
        assertEquals("Mozilla/5.0 Test", config.network().userAgent());
        assertTrue(config.network().preferIpv6());
        SettingsForm back = SettingsMapper.fromProperties(merged);
        assertEquals(form.pacDiscoveryScript(), back.pacDiscoveryScript());
        assertEquals(SettingsForm.PROXY_AUTH_BASIC, back.proxyAuthMode());
    }

    @Test
    public void emptyFileYieldsTheLoaderDefaultsForProxyAndTls() {
        SettingsForm form = SettingsMapper.fromProperties(new Properties());
        assertEquals(SettingsForm.PROXY_PAC_URL_POWERSHELL, form.proxyMode());
        assertEquals("", form.pacDiscoveryScript());
        assertEquals(SettingsForm.PROXY_AUTH_NONE, form.proxyAuthMode());
        assertTrue(form.tlsJvmDefault());
        assertTrue(form.tlsWindowsRoot());
        assertTrue(form.tlsWindowsCaStores());
        assertEquals("", form.caCertificatesFile());
    }

    @Test
    public void templateRoundTripsAndStillLoads() throws Exception {
        Properties current = template();
        SettingsForm form = SettingsMapper.fromProperties(current);
        Properties merged = SettingsMapper.merge(current, form);
        for (String key : current.stringPropertyNames()) {
            if (current.getProperty(key).isEmpty()) {
                // leere Werte der Vorlage: leer und fehlend sind für den Loader dasselbe; der Dialog
                // kommentiert leere Werte aus, die Quellen-Schlüssel verwaltet er nicht.
                String value = merged.getProperty(key);
                assertTrue(key, value == null || value.isEmpty());
                continue;
            }
            assertEquals(key, current.getProperty(key), merged.getProperty(key));
        }
        AppConfigLoader.fromProperties(merged);
    }

    @Test
    public void firstStartDefaultsComplainOnlyAboutRequiredFields() throws Exception {
        Properties merged = SettingsMapper.merge(template(), SettingsMapper.firstStartDefaults());
        try {
            AppConfigLoader.fromProperties(merged);
            fail("leere Pflichtfelder müssen Probleme ergeben");
        } catch (AppConfigException e) {
            List<String> described = SettingsMapper.describe(e.problems());
            // mit KeePass-Eintrag kommt die Basis-URL aus KeePass: keine Pflichtangabe
            assertFalse(described.toString(), described.toString().contains("(chat.baseUrl)"));
            assertTrue(described.toString(), described.toString().contains("(chat.model)"));
            for (String problem : described) {
                assertFalse(problem, problem.contains("(source."));
                assertFalse(problem, problem.contains("(security."));
            }
        }
        assertNull("Beispielquellen der Vorlage gehören nicht in die erste eigene Konfiguration",
                merged.getProperty("source.1.type"));
    }

    @Test
    public void emptyOptionalFieldsRemoveTheirKeys() {
        Properties current = valid();
        SettingsForm form = SettingsMapper.fromProperties(current).toBuilder().chatSystemPrompt("  ").build();
        assertTrue(SettingsMapper.removals(form, current).contains("chat.systemPrompt"));
        assertNull(SettingsMapper.merge(current, form).getProperty("chat.systemPrompt"));
    }

    @Test
    public void writesLeaveOutEmptyValues() {
        SettingsForm form = SettingsMapper.fromProperties(valid()).toBuilder().chatSystemPrompt("").build();
        Map<String, String> writes = SettingsMapper.writes(form);
        assertFalse(writes.containsKey("chat.systemPrompt"));
        assertEquals("test-chat", writes.get("chat.model"));
        for (String value : writes.values()) {
            assertFalse(value.isEmpty());
        }
    }

    @Test
    public void describePutsFieldLabelsInFront() {
        assertEquals("Basis-URL des KI-Dienstes (chat.baseUrl): fehlt (Pflichtangabe)",
                SettingsMapper.describe("chat.baseUrl: fehlt (Pflichtangabe)"));
        assertEquals("Quelle Nr. 1, API-URL (source.1.apiUrl): keine gültige URL",
                SettingsMapper.describe("source.1.apiUrl: keine gültige URL"));
        assertEquals("Quelle Nr. 2, Startpunkte (source.2.startPoints): fehlt",
                SettingsMapper.describe("source.2.startPoints: fehlt"));
        assertEquals("CA-Datei (network.tls.caCertificatesFile): Datei nicht lesbar (NoSuchFileException)",
                SettingsMapper.describe("network.tls.caCertificatesFile: Datei nicht lesbar (NoSuchFileException)"));
        assertEquals("retrieval.maxResults: keine ganze Zahl", SettingsMapper.describe("retrieval.maxResults: keine ganze Zahl"));
        assertEquals("ohne Doppelpunkt", SettingsMapper.describe("ohne Doppelpunkt"));
    }

    /** Einrichtungsassistent: Feldnamen statt Properties-Schlüssel, auch im Text der Meldung. */
    @Test
    public void plainShowsNoPropertyKeys() {
        assertEquals("Basis-URL des KI-Dienstes: fehlt (Pflichtangabe)",
                SettingsMapper.plain("chat.baseUrl: fehlt (Pflichtangabe)"));
        assertEquals("Manual host: fehlt (Pflicht bei Mode=MANUAL_PROXY)",
                SettingsMapper.plain("network.proxy.host: fehlt (Pflicht bei network.proxy.mode=MANUAL_PROXY)"));
        assertEquals("Chat-Parameter ungültig (siehe Beispielkonfiguration)",
                SettingsMapper.plain("chat.*: Chat-Parameter ungültig (siehe Beispielkonfiguration)"));
        assertEquals("Basis-URL für Embeddings: darf keine Zugangsdaten enthalten (KeePass-Eintrag verwenden)",
                SettingsMapper.plain("embedding.baseUrl: darf keine Zugangsdaten enthalten (SecretRef verwenden)"));
        assertEquals("Einstellung: keine ganze Zahl", SettingsMapper.plain("retrieval.maxResults: keine ganze Zahl"));
        assertEquals("Konfigurationsdatei nicht lesbar: /tmp/x.properties (IOException)",
                SettingsMapper.plain("Konfigurationsdatei nicht lesbar: /tmp/x.properties (IOException)"));
        for (String problem : SettingsMapper.plain(java.util.Arrays.asList("chat.model: fehlt (Pflichtangabe)",
                "embedding.dimension: fehlt (Pflichtangabe, z. B. 768)", "chat.apiKeyRef: fehlt"))) {
            assertFalse(problem, problem.matches("(?s).*\\b(chat|embedding)\\.[a-zA-Z]+.*"));
        }
    }
}
