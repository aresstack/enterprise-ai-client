package com.aresstack.enterpriseai.proasteion.help;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HelpSearchTest {

    private static final String SPEECH = "---\n"
            + "id: settings.speech\n"
            + "title: Sprachausgabe einstellen\n"
            + "keywords: Stimme, TTS, Vorlesen, voice\n"
            + "since: 0.46.0\n"
            + "---\n\n"
            + "# Sprachausgabe einstellen\n\n"
            + "Im Reiter „Sprachausgabe“ wählst du Modell und Stimme.\n\n"
            + "## Stimme wechseln\n\n"
            + "Öffne Einstellungen → Sprachausgabe und wähle die Stimme. Siehe [Vorlesen](help:chat.read-aloud).\n\n"
            + "### Unterpunkt\n\nGehört zum Abschnitt davor.\n\n"
            + "## Name des Assistenten\n\nDer Anzeigename steht ganz oben.\n";
    private static final String ATTACHMENTS = "---\n"
            + "id: chat.attachments\n"
            + "title: Dateien im Chat verwenden\n"
            + "keywords: Datei, PDF, Word\n"
            + "---\n\n"
            + "# Dateien im Chat verwenden\n\n"
            + "## Dateianhänge hinzufügen\n\nZiehe die Datei in das Eingabefeld.\n\n"
            + "```\n## keine Überschrift im Codeblock\n```\n";
    private static final String READ_ALOUD = "---\n"
            + "id: chat.read-aloud\n"
            + "title: Antworten vorlesen lassen\n"
            + "keywords: Vorlesen, Lautsprecher\n"
            + "---\n\n"
            + "# Antworten vorlesen lassen\n\n"
            + "## Vorlesen starten\n\nKlicke auf den Lautsprecher unter der Antwort.\n";

    private final HelpCatalog catalog = catalog(SPEECH, ATTACHMENTS, READ_ALOUD);

    @Test
    public void parsesFrontMatterAndSectionsPerSecondLevelHeading() {
        HelpTopic topic = catalog.topic("help:settings.speech");
        assertEquals("Sprachausgabe einstellen", topic.title());
        assertEquals(Arrays.asList("Stimme", "TTS", "Vorlesen", "voice"), topic.keywords());
        assertEquals("0.46.0", topic.since());
        assertEquals("settings", topic.area());
        assertEquals(3, topic.sections().size());
        assertTrue(topic.sections().get(0).isIntroduction());
        HelpSection voice = topic.section("stimme-wechseln");
        assertTrue(voice.markdown().contains("### Unterpunkt"));
        assertEquals(Arrays.asList("chat.read-aloud"), HelpMarkdown.helpLinks(voice.markdown()));
    }

    @Test
    public void headingInsideCodeBlockIsNoSection() {
        assertEquals(1, catalog.topic("chat.attachments").sections().size());
    }

    @Test
    public void slugFoldsUmlautsAndPunctuation() {
        assertEquals("der-knopf-weiter-bleibt-grau", HelpMarkdown.slug("Der Knopf „Weiter“ bleibt grau"));
        assertEquals("groesse-und-schluessel", HelpMarkdown.slug("Größe und Schlüssel!"));
    }

    @Test
    public void findsCompoundsByTheirParts() {
        List<HelpHit> hits = new HelpSearchUseCase(catalog).search("Wie füge ich Anhänge hinzu?", 5, 200);
        assertFalse(hits.isEmpty());
        assertEquals("chat.attachments", hits.get(0).topic().id());
        assertEquals(1.0, hits.get(0).score(), 1e-9);
    }

    @Test
    public void returnsAtMostOneHitPerTopicBestFirst() {
        List<HelpHit> hits = new HelpSearchUseCase(catalog).search("Stimme Vorlesen", 5, 200);
        List<String> ids = new ArrayList<String>();
        for (HelpHit hit : hits) {
            ids.add(hit.topic().id());
        }
        assertEquals(2, ids.size());
        assertEquals("settings.speech", ids.get(0));
        assertTrue(ids.contains("chat.read-aloud"));
        assertEquals("help:settings.speech#stimme-wechseln", hits.get(0).sectionId());
    }

    @Test
    public void questionOfStopWordsFindsNothing() {
        assertTrue(new HelpSearchUseCase(catalog).search("wie und was", 5, 200).isEmpty());
    }

    @Test
    public void readsWholeTopicOrOneSection() {
        HelpReadUseCase read = new HelpReadUseCase(catalog);
        assertNull(read.read("settings.speech").section());
        assertEquals("Stimme wechseln", read.read("help:settings.speech#stimme-wechseln").section().heading());
        assertEquals("Stimme wechseln", read.read("help:settings.speech#Stimme wechseln").section().heading());
        try {
            read.read("help:settings.nothing");
            fail();
        } catch (UnknownHelpTopicException expected) {
            assertNull(expected.topic());
        }
        try {
            read.read("help:settings.speech#nichts");
            fail();
        } catch (UnknownHelpTopicException expected) {
            assertEquals("settings.speech", expected.topic().id());
        }
    }

    @Test
    public void rejectsBrokenChapters() {
        assertRejected("# Ohne Frontmatter\n");
        assertRejected("---\nid: chat.x\n---\n# X\n");
        assertRejected("---\nid: x\ntitle: X\n---\n# X\n");
        assertRejected("---\nid: chat.x\ntitle: X\nsince: 1.0\n---\n# X\n");
        assertRejected("---\nid: chat.x\ntitle: X\n---\n# Y\n");
        assertRejected("---\nid: chat.x\ntitle: X\n---\n# X\n# X\n");
    }

    @Test
    public void idMustMatchPathAndBeUnique() throws IOException {
        Map<String, String> wrongPath = new LinkedHashMap<String, String>();
        wrongPath.put("chat/other.md", ATTACHMENTS);
        try {
            HelpCatalog.load(documents(wrongPath));
            fail();
        } catch (HelpFormatException expected) {
            assertTrue(expected.getMessage().contains("must be 'chat.other'"));
        }
    }

    private static void assertRejected(String text) {
        try {
            HelpMarkdown.parse("chat/x.md", text);
            fail("accepted: " + text);
        } catch (HelpFormatException expected) {
            assertEquals("chat/x.md", expected.resource());
        }
    }

    private static HelpCatalog catalog(String... chapters) {
        Map<String, String> byName = new LinkedHashMap<String, String>();
        for (String chapter : chapters) {
            String id = chapter.substring(chapter.indexOf("id: ") + 4, chapter.indexOf('\n', chapter.indexOf("id: ")));
            byName.put(id.replace('.', '/') + ".md", chapter);
        }
        try {
            return HelpCatalog.load(documents(byName));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HelpDocuments documents(final Map<String, String> byName) {
        return new HelpDocuments() {
            @Override
            public List<String> names() {
                return new ArrayList<String>(byName.keySet());
            }

            @Override
            public String read(String name) {
                return byName.get(name);
            }
        };
    }
}
