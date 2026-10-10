package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
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

    private static String withWiki() {
        return baseText()
                + "sources=wiki\n"
                + "source.wiki.type=mediawiki\n"
                + "source.wiki.apiUrl=http://127.0.0.1:9/w/api.php\n"
                + "source.wiki.startPoints=Hauptseite\n";
    }

    private static SourceDefinition confluence(String id) {
        return new SourceDefinition(id, "confluence", true, SourceSettings.empty()
                .with("baseUrl", "http://127.0.0.1:9/confluence").with("startPoints", "space:DEV"));
    }

    @Test
    public void addingASourceAppendsItAndLeavesTheRestUntouched() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.save(confluence("confluence"), null);

        String text = text();
        assertTrue(text, text.startsWith("# Mein Kommentar bleibt\nchat.baseUrl=http://127.0.0.1:9/v1\n"));
        assertTrue(text, text.contains("chat.apiKeyRef=keepass:Enterprise AI API"));
        List<SourceDefinition> sources = actions.definitions();
        assertEquals(2, sources.size());
        assertEquals("wiki", sources.get(0).id());
        assertEquals("confluence", sources.get(1).id());
        AppConfig config = AppConfigLoader.load(path);
        assertEquals(2, config.sources().size());
        assertEquals("space:DEV", config.sources().get(1).settings().get("startPoints"));
        assertEquals("confluence", config.sources().get(1).typeId());
    }

    @Test
    public void editingCanRenameASourceInPlace() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        SourceDefinition renamed = new SourceDefinition("handbuch", "mediawiki", true, SourceSettings.empty()
                .with("apiUrl", "http://127.0.0.1:9/w/api.php").with("startPoints", "Urlaub, Gleitzeit"));
        actions.save(renamed, "wiki");

        List<SourceDefinition> sources = actions.definitions();
        assertEquals(1, sources.size());
        assertEquals("handbuch", sources.get(0).id());
        assertEquals("Urlaub, Gleitzeit", sources.get(0).settings().get("startPoints"));
        String text = text();
        assertFalse(text, text.contains("\nsource.wiki.apiUrl="));
        assertEquals(1, AppConfigLoader.load(path).sources().size());
    }

    @Test
    public void removingASourceDropsItFromTheList() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.save(confluence("confluence"), null);
        actions.remove("wiki");

        List<SourceDefinition> sources = actions.definitions();
        assertEquals(1, sources.size());
        assertEquals("confluence", sources.get(0).id());
        AppConfig config = AppConfigLoader.load(path);
        assertEquals(1, config.sources().size());
        for (String warning : config.warnings()) {
            assertFalse(warning, warning.contains("Wissensquelle"));
        }
    }

    @Test
    public void checkboxWritesOnlyTheEnabledKey() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.setEnabled("wiki", false);
        assertTrue(text(), text().contains("source.wiki.enabled=false"));
        assertFalse(actions.definitions().get(0).enabled());
        assertFalse(AppConfigLoader.load(path).sources().get(0).enabled());

        actions.setEnabled("wiki", true);
        assertFalse(text(), text().contains("\nsource.wiki.enabled=false"));
        assertTrue(AppConfigLoader.load(path).sources().get(0).enabled());
        assertTrue(text(), text().startsWith("# Mein Kommentar bleibt\n"));
    }
}
