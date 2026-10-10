package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.ui.settings.SourceForm;
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
import static org.junit.Assert.fail;

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

    private static SourceForm confluence(String id) {
        return SourceForm.builder(id, SourceForm.TYPE_CONFLUENCE).url("http://127.0.0.1:9/confluence")
                .startPoints("space:DEV").build();
    }

    @Test
    public void addingASourceAppendsItAndLeavesTheRestUntouched() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        assertTrue(actions.validate(confluence("confluence"), null).isEmpty());
        actions.save(confluence("confluence"), null);

        String text = text();
        assertTrue(text, text.startsWith("# Mein Kommentar bleibt\nchat.baseUrl=http://127.0.0.1:9/v1\n"));
        assertTrue(text, text.contains("chat.apiKeyRef=keepass:Enterprise AI API"));
        List<SourceForm> sources = actions.sources();
        assertEquals(2, sources.size());
        assertEquals("wiki", sources.get(0).id());
        assertEquals("confluence", sources.get(1).id());
        AppConfig config = AppConfigLoader.load(path);
        assertEquals(2, config.sources().size());
        assertEquals("space:DEV", String.join(",", config.sources().get(1).scope().startPoints()));
        SourceConfig saved = actions.sourceConfig("confluence");
        assertEquals("confluence", saved.sourceId().value());
    }

    @Test
    public void editingCanRenameASourceInPlace() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        SourceForm renamed = SourceForm.builder("handbuch", SourceForm.TYPE_MEDIAWIKI)
                .url("http://127.0.0.1:9/w/api.php").startPoints("Urlaub, Gleitzeit").build();
        actions.save(renamed, "wiki");

        List<SourceForm> sources = actions.sources();
        assertEquals(1, sources.size());
        assertEquals("handbuch", sources.get(0).id());
        assertEquals("Urlaub, Gleitzeit", sources.get(0).startPoints());
        String text = text();
        assertFalse(text, text.contains("\nsource.wiki.apiUrl="));
        assertEquals(1, AppConfigLoader.load(path).sources().size());
    }

    @Test
    public void draftsAreCheckedStrictlyAndDuplicatesRejected() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        assertFalse(actions.validate(SourceForm.builder("", SourceForm.TYPE_MEDIAWIKI).build(), null).isEmpty());
        List<String> duplicate = actions.validate(confluence("wiki"), null);
        assertEquals(duplicate.toString(), 1, duplicate.size());
        assertTrue(duplicate.get(0), duplicate.get(0).contains("gibt es schon"));
        List<String> broken = actions.validate(SourceForm.builder("neu", SourceForm.TYPE_MEDIAWIKI)
                .url("ftp://nicht-erlaubt").build(), null);
        assertFalse(broken.isEmpty());
        try {
            actions.save(SourceForm.builder("neu", SourceForm.TYPE_MEDIAWIKI).build(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ein fehlerhafter Entwurf landet nie in der Datei
        }
        assertEquals(1, actions.sources().size());
        assertTrue(actions.validate(actions.sources().get(0), "wiki").isEmpty());
    }

    @Test
    public void removingASourceDropsItFromTheList() throws Exception {
        FileSourceActions actions = actionsWith(withWiki());
        actions.save(confluence("confluence"), null);
        actions.remove("wiki");

        List<SourceForm> sources = actions.sources();
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
        assertFalse(actions.sources().get(0).enabled());
        assertFalse(AppConfigLoader.load(path).sources().get(0).enabled());

        actions.setEnabled("wiki", true);
        assertFalse(text(), text().contains("\nsource.wiki.enabled=false"));
        assertTrue(AppConfigLoader.load(path).sources().get(0).enabled());
        assertTrue(text(), text().startsWith("# Mein Kommentar bleibt\n"));
    }
}
