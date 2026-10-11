package com.aresstack.enterpriseai.proasteion.exedra.settings;

import com.aresstack.enterpriseai.proasteion.application.settings.ConfigurationFile;

import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfigLoader;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Der Drawer-Reiter schreibt über diese Klasse nur Quell-Schlüssel; der Rest der Datei bleibt, wie er ist. */
public class FileSourceActionsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path path;

    private FileSourceActions actionsWith(String text) throws Exception {
        path = tmp.getRoot().toPath().resolve("app.properties");
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        return new FileSourceActions(new ConfigurationFile(path));
    }

    private String text() throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String baseText() {
        return "# Mein Kommentar bleibt\n"
                + "chat.baseUrl=http://127.0.0.1:9/v1\n"
                + "chat.model=test-chat\n"
                + "chat.apiKeyRef=keepass:Enterprise AI API\n"
                + "embedding.model=test-embedding\n"
                + "embedding.dimension=8\n"
                + "security.keepass.enabled=false\n";
    }

    private static final String WIKI = "wiki://127.0.0.1:9/w";
    private static final String CONFLUENCE = "confluence://127.0.0.1:9/confluence";

    private static String withWiki() {
        return baseText()
                + "source.1.address=" + WIKI + "\n"
                + "source.1.type=mediawiki\n"
                + "source.1.apiUrl=http://127.0.0.1:9/w/api.php\n"
                + "source.1.startPoints=Hauptseite\n";
    }

    private static SourceDefinition confluence(String address) {
        return new SourceDefinition(address, "confluence", true, SourceSettings.empty()
                .with("baseUrl", "http://127.0.0.1:9/confluence").with("startPoints", "space:DEV"));
    }

    @Test
    public void addingASourceAppendsItAndLeavesTheRestUntouched() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.save(confluence(CONFLUENCE), null);

        String text = text();
        assertTrue(text, text.startsWith("# Mein Kommentar bleibt\nchat.baseUrl=http://127.0.0.1:9/v1\n"));
        assertTrue(text, text.contains("chat.apiKeyRef=keepass:Enterprise AI API"));
        assertTrue(text, text.contains("source.2.address=" + CONFLUENCE));
        List<SourceDefinition> sources = actions.definitions();
        assertEquals(2, sources.size());
        assertEquals(WIKI, sources.get(0).address());
        assertEquals(CONFLUENCE, sources.get(1).address());
        AppConfig config = AppConfigLoader.load(path);
        assertEquals(2, config.sources().size());
        assertEquals("space:DEV", config.sources().get(1).settings().get("startPoints"));
        assertEquals("confluence", config.sources().get(1).typeId());
    }

    @Test
    public void editingCanChangeTheAddressInPlace() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        SourceDefinition moved = new SourceDefinition("wiki://127.0.0.1:9/handbuch", "mediawiki", true,
                SourceSettings.empty().with("apiUrl", "http://127.0.0.1:9/handbuch/api.php")
                        .with("startPoints", "Urlaub, Gleitzeit"));
        actions.save(moved, WIKI);

        List<SourceDefinition> sources = actions.definitions();
        assertEquals(1, sources.size());
        assertEquals("wiki://127.0.0.1:9/handbuch", sources.get(0).address());
        assertEquals("Urlaub, Gleitzeit", sources.get(0).settings().get("startPoints"));
        assertEquals(1, AppConfigLoader.load(path).sources().size());
    }

    @Test
    public void removingASourceDropsItFromTheList() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.save(confluence(CONFLUENCE), null);
        actions.remove(WIKI);

        List<SourceDefinition> sources = actions.definitions();
        assertEquals(1, sources.size());
        assertEquals(CONFLUENCE, sources.get(0).address());
        AppConfig config = AppConfigLoader.load(path);
        assertEquals(1, config.sources().size());
        for (String warning : config.warnings()) {
            assertFalse(warning, warning.contains("Wissensquelle"));
        }
    }

    @Test
    public void legacyEntriesWithFreeIdsAreDropped() throws Exception {
        FileSourceActions actions = actionsWith(withWiki() + "sources=alt\nsource.alt.type=mediawiki\n");
        assertEquals(2, actions.dropLegacyEntries());
        assertEquals(1, actions.definitions().size());
        assertFalse(text(), text().contains("\nsource.alt.type="));
    }

    @Test
    public void checkboxWritesOnlyTheEnabledKey() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.setEnabled(WIKI, false);
        assertTrue(text(), text().contains("source.1.enabled=false"));
        assertFalse(actions.definitions().get(0).enabled());
        assertFalse(AppConfigLoader.load(path).sources().get(0).enabled());

        actions.setEnabled(WIKI, true);
        assertFalse(text(), text().contains("\nsource.1.enabled=false"));
        assertTrue(AppConfigLoader.load(path).sources().get(0).enabled());
        assertTrue(text(), text().startsWith("# Mein Kommentar bleibt\n"));
    }
}
