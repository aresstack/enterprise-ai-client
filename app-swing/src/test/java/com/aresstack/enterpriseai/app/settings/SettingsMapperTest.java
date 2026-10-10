package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.app.ui.settings.SourceForm;
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
        p.setProperty("sources", "wiki,confluence");
        p.setProperty("source.wiki.type", "mediawiki");
        p.setProperty("source.wiki.apiUrl", "http://127.0.0.1:9/w/api.php");
        p.setProperty("source.wiki.siteKey", "intern");
        p.setProperty("source.wiki.displayName", "Intranet-Wiki");
        p.setProperty("source.wiki.startPoints", "Hauptseite,Handbuch");
        p.setProperty("source.wiki.maxDepth", "2");
        p.setProperty("source.wiki.connectTimeoutMillis", "15000");
        p.setProperty("source.confluence.type", "confluence");
        p.setProperty("source.confluence.baseUrl", "http://127.0.0.1:9/confluence");
        p.setProperty("source.confluence.credentialRef", "keepass:Confluence");
        p.setProperty("source.confluence.startPoints", "space:DEV");
        p.setProperty("source.confluence.searchSpaceKeys", "DEV");
        p.setProperty("source.confluence.includeAttachments", "true");
        p.setProperty("source.confluence.allowInsecureHttp", "true");
        p.setProperty("security.keepass.enabled", "false");
        p.setProperty("network.proxy.mode", "NONE");
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
        assertFalse(form.indexOnStartup());
        assertFalse(form.keePassEnabled());
        assertEquals(SettingsForm.PROXY_NONE, form.proxyMode());
        assertEquals(2, form.sources().size());
        SourceForm wiki = form.sources().get(0);
        assertTrue(wiki.isMediaWiki());
        assertEquals("http://127.0.0.1:9/w/api.php", wiki.url());
        assertEquals("Hauptseite,Handbuch", wiki.startPoints());
        assertEquals("2", wiki.maxDepth());
        SourceForm confluence = form.sources().get(1);
        assertTrue(confluence.isConfluence());
        assertEquals("http://127.0.0.1:9/confluence", confluence.url());
        assertEquals("DEV", confluence.searchSpaceKeys());
        assertTrue(confluence.includeAttachments());
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
        expected.setProperty("source.wiki.requiresLogin", "false");
        expected.setProperty("source.confluence.maxDepth", "1");
        expected.setProperty("security.keepass.host", "127.0.0.1");
        expected.setProperty("security.keepass.port", "12546");
        expected.setProperty("security.keepass.clientDisplayName", "Enterprise AI Client");
        expected.setProperty("security.keepass.pairingKeyStore", "file");
        expected.setProperty("network.proxy.pacDiscovery", "WINDOWS_SETTINGS");
        expected.setProperty("network.tls.useWindowsCertificateStore", "true");
        expected.setProperty("agent.enabled", "false");
        expected.setProperty("agent.requestTimeoutSeconds", "30");
        assertEquals(expected, added);
        AppConfig config = AppConfigLoader.fromProperties(merged);
        assertEquals("test-chat", config.chat().model());
    }

    @Test
    public void proxyAutoAndTlsKeysRoundTripThroughTheLoader() {
        Properties current = valid();
        current.setProperty("network.proxy.mode", "auto");
        current.setProperty("network.proxy.pacUrl", "file:///C:/wpad.dat");
        current.setProperty("network.proxy.pacDiscovery", "powershell");
        current.setProperty("network.tls.useWindowsCertificateStore", "false");
        current.setProperty("network.tls.caCertificatesFile", "C:/Zertifikate/firmen-ca.pem");
        SettingsForm form = SettingsMapper.fromProperties(current);
        assertEquals(SettingsForm.PROXY_AUTO, form.proxyMode());
        assertEquals("file:///C:/wpad.dat", form.pacUrl());
        assertEquals(SettingsForm.PAC_POWERSHELL, form.pacDiscovery());
        assertFalse(form.useWindowsCertificateStore());
        assertEquals("C:/Zertifikate/firmen-ca.pem", form.caCertificatesFile());

        Properties merged = SettingsMapper.merge(current, form);
        AppConfig config = AppConfigLoader.fromProperties(merged);
        assertEquals("file:///C:/wpad.dat", config.network().pacUrl());
        assertEquals("POWERSHELL", config.network().pacDiscovery().name());
        assertFalse(config.network().useWindowsCertificateStore());
        assertEquals("C:/Zertifikate/firmen-ca.pem", config.network().caCertificatesFile().toString());

        SettingsForm cleared = form.toBuilder().pacUrl("").caCertificatesFile("").build();
        Set<String> removals = SettingsMapper.removals(cleared, current);
        assertTrue(removals.contains("network.proxy.pacUrl"));
        assertTrue(removals.contains("network.tls.caCertificatesFile"));
        assertNull(AppConfigLoader.fromProperties(SettingsMapper.merge(current, cleared)).network().caCertificatesFile());
    }

    @Test
    public void emptyFileYieldsTheLoaderDefaultsForProxyAndTls() {
        SettingsForm form = SettingsMapper.fromProperties(new Properties());
        assertEquals(SettingsForm.PROXY_AUTO, form.proxyMode());
        assertEquals(SettingsForm.PAC_WINDOWS_SETTINGS, form.pacDiscovery());
        assertTrue(form.useWindowsCertificateStore());
        assertEquals("", form.caCertificatesFile());
    }

    @Test
    public void templateRoundTripsAndStillLoads() throws Exception {
        Properties current = template();
        SettingsForm form = SettingsMapper.fromProperties(current);
        Properties merged = SettingsMapper.merge(current, form);
        for (String key : current.stringPropertyNames()) {
            if (current.getProperty(key).isEmpty()) {
                // "sources=" (leer) der Vorlage: leer und fehlend sind für den Loader dasselbe; der Dialog
                // kommentiert leere Werte aus.
                assertNull(key, merged.getProperty(key));
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
            assertTrue(described.toString(), described.toString().contains("(chat.baseUrl)"));
            assertTrue(described.toString(), described.toString().contains("(chat.model)"));
            for (String problem : described) {
                assertFalse(problem, problem.contains("(source."));
                assertFalse(problem, problem.contains("(security."));
            }
        }
        assertNull("Beispielquellen der Vorlage gehören nicht in die erste eigene Konfiguration",
                merged.getProperty("source.wiki.type"));
        assertNull(merged.getProperty("sources"));
    }

    @Test
    public void removingASourceDropsAllOfItsKeys() {
        Properties current = valid();
        SettingsForm form = SettingsMapper.fromProperties(current).toBuilder()
                .sources(java.util.Collections.<SourceForm>emptyList()).build();
        Set<String> removals = SettingsMapper.removals(form, current);
        assertTrue(removals.contains("source.wiki.type"));
        assertTrue(removals.contains("source.wiki.connectTimeoutMillis"));
        assertTrue(removals.contains("source.confluence.baseUrl"));
        Properties merged = SettingsMapper.merge(current, form);
        assertNull(merged.getProperty("sources"));
        assertNull(merged.getProperty("source.wiki.apiUrl"));
        assertEquals("5", merged.getProperty("retrieval.maxResults"));
        AppConfigLoader.fromProperties(merged);
    }

    @Test
    public void deselectedSourceRoundTripsAsEnabledFalse() {
        Properties current = valid();
        current.setProperty("source.confluence.enabled", "false");
        SettingsForm form = SettingsMapper.fromProperties(current);
        assertTrue(form.sources().get(0).enabled());
        assertFalse(form.sources().get(1).enabled());
        Properties merged = SettingsMapper.merge(current, form);
        assertEquals("false", merged.getProperty("source.confluence.enabled"));
        assertNull("angehakt ist der Standard und braucht keinen Schlüssel", merged.getProperty("source.wiki.enabled"));
        assertFalse(AppConfigLoader.fromProperties(merged).sources().get(1).enabled());

        SettingsForm ticked = form.toBuilder().sources(java.util.Arrays.asList(form.sources().get(0),
                form.sources().get(1).toBuilder().enabled(true).build())).build();
        assertNull(SettingsMapper.merge(current, ticked).getProperty("source.confluence.enabled"));
    }

    @Test
    public void changingTheSourceTypeDropsKeysOfTheOldType() {
        Properties current = valid();
        SettingsForm form = SettingsMapper.fromProperties(current).toBuilder()
                .sources(java.util.Collections.singletonList(
                        SourceForm.builder("wiki", SourceForm.TYPE_CONFLUENCE).url("http://127.0.0.1:9/c")
                                .startPoints("space:DEV").build()))
                .build();
        Map<String, String> changes = SettingsMapper.changes(form);
        assertEquals("confluence", changes.get("source.wiki.type"));
        assertEquals("http://127.0.0.1:9/c", changes.get("source.wiki.baseUrl"));
        Set<String> removals = SettingsMapper.removals(form, current);
        assertTrue(removals.contains("source.wiki.apiUrl"));
        assertTrue(removals.contains("source.wiki.siteKey"));
        assertTrue(removals.contains("source.wiki.connectTimeoutMillis"));
        AppConfigLoader.fromProperties(SettingsMapper.merge(current, form));
    }

    @Test
    public void emptyOptionalFieldsRemoveTheirKeys() {
        Properties current = valid();
        SettingsForm form = SettingsMapper.fromProperties(current).toBuilder().chatSystemPrompt("  ").build();
        assertTrue(SettingsMapper.removals(form, current).contains("chat.systemPrompt"));
        assertNull(SettingsMapper.merge(current, form).getProperty("chat.systemPrompt"));
    }

    @Test
    public void unknownSourceTypeIsShownAsWikiSoTheUserCanFixIt() {
        Properties current = valid();
        current.setProperty("sources", "ftp");
        current.setProperty("source.ftp.type", "ftp");
        SettingsForm form = SettingsMapper.fromProperties(current);
        assertEquals(1, form.sources().size());
        assertTrue(form.sources().get(0).isMediaWiki());
        assertEquals("", form.sources().get(0).url());
    }

    @Test
    public void dottedSourceIdsKeepTheirUnmanagedKeys() {
        Properties current = valid();
        current.setProperty("sources", "team.wiki,confluence");
        current.setProperty("source.team.wiki.type", "mediawiki");
        current.setProperty("source.team.wiki.apiUrl", "http://127.0.0.1:9/w/api.php");
        current.setProperty("source.team.wiki.startPoints", "Hauptseite");
        current.setProperty("source.team.wiki.connectTimeoutMillis", "15000");
        current.setProperty("source.old.apiUrl", "http://127.0.0.1:9/alt");
        SettingsForm form = SettingsMapper.fromProperties(current);
        assertEquals("team.wiki", form.sources().get(0).id());
        Set<String> removals = SettingsMapper.removals(form, current);
        for (String key : current.stringPropertyNames()) {
            if (key.startsWith("source.team.wiki.")) {
                assertFalse("vorhandener Schlüssel der unveränderten Quelle: " + key, removals.contains(key));
            }
        }
        assertFalse("nirgends gelistete Quellen bleiben unberührt", removals.contains("source.old.apiUrl"));
        Properties merged = SettingsMapper.merge(current, form);
        assertEquals("15000", merged.getProperty("source.team.wiki.connectTimeoutMillis"));
        AppConfigLoader.fromProperties(merged);

        SettingsForm without = form.toBuilder().sources(java.util.Collections.<SourceForm>emptyList()).build();
        Set<String> gone = SettingsMapper.removals(without, current);
        assertTrue(gone.contains("source.team.wiki.type"));
        assertTrue(gone.contains("source.team.wiki.connectTimeoutMillis"));
        assertTrue(gone.contains("sources"));
        assertEquals("team.wiki", SettingsMapper.sourceIdOf("source.team.wiki.apiUrl", current.stringPropertyNames()
                .contains("x") ? java.util.Collections.<String>emptySet() : java.util.Arrays.asList("team", "team.wiki")));
        assertNull(SettingsMapper.sourceIdOf("source.team.wiki.apiUrl", java.util.Collections.singleton("wiki")));
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
        assertEquals("Quelle \u201ewiki\u201c, API-URL (source.wiki.apiUrl): keine gültige URL",
                SettingsMapper.describe("source.wiki.apiUrl: keine gültige URL"));
        assertEquals("Quelle \u201eteam.wiki\u201c, Startpunkte (source.team.wiki.startPoints): fehlt",
                SettingsMapper.describe("source.team.wiki.startPoints: fehlt"));
        assertEquals("CA-Datei (network.tls.caCertificatesFile): Datei nicht lesbar (NoSuchFileException)",
                SettingsMapper.describe("network.tls.caCertificatesFile: Datei nicht lesbar (NoSuchFileException)"));
        assertEquals("retrieval.maxResults: keine ganze Zahl", SettingsMapper.describe("retrieval.maxResults: keine ganze Zahl"));
        assertEquals("ohne Doppelpunkt", SettingsMapper.describe("ohne Doppelpunkt"));
    }
}
