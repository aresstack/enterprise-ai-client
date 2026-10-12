package com.aresstack.enterpriseai.proasteion.help;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checks the bundled help as shipped in the jar (docs/help): every chapter parses, ids are unique and match their
 * path, no section is empty, anchors are unique per chapter, every {@code help:} link resolves, every {@code since}
 * is not newer than the version being built, and the example id of the tools exists.
 */
public class HelpDocumentationTest {

    /** Areas the help must cover; a missing area means a chapter folder was lost. */
    private static final String[] AREAS = {"overview", "getting-started", "chat", "knowledge", "agent", "settings",
            "troubleshooting", "concepts"};

    private static HelpCatalog bundled() {
        try {
            return HelpCatalog.load(ClasspathHelpDocuments.bundled());
        } catch (IOException e) {
            throw new AssertionError("help not bundled: " + e.getMessage(), e);
        } catch (HelpFormatException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    @Test
    public void everyAreaHasChapters() {
        HelpCatalog catalog = bundled();
        for (String area : AREAS) {
            boolean found = false;
            for (HelpTopic topic : catalog.topics()) {
                found |= topic.area().equals(area);
            }
            assertTrue("no chapter in area " + area, found);
        }
    }

    @Test
    public void chaptersHaveTitleKeywordsAndNonEmptyUniqueSections() {
        List<String> problems = new ArrayList<String>();
        for (HelpTopic topic : bundled().topics()) {
            if (topic.keywords().isEmpty()) {
                problems.add(topic.resource() + ": no keywords");
            }
            Set<String> anchors = new HashSet<String>();
            int headed = 0;
            for (HelpSection section : topic.sections()) {
                if (section.markdown().trim().isEmpty()) {
                    problems.add(topic.resource() + ": empty section '" + section.heading() + "'");
                }
                if (!section.isIntroduction()) {
                    headed++;
                    if (!anchors.add(section.anchor())) {
                        problems.add(topic.resource() + ": duplicate section anchor '" + section.anchor() + "'");
                    }
                }
            }
            if (headed == 0) {
                problems.add(topic.resource() + ": no ## section");
            }
        }
        assertNone(problems);
    }

    @Test
    public void everyHelpLinkResolves() {
        HelpCatalog catalog = bundled();
        HelpReadUseCase read = new HelpReadUseCase(catalog);
        List<String> problems = new ArrayList<String>();
        for (HelpTopic topic : catalog.topics()) {
            for (HelpSection section : topic.sections()) {
                for (String link : HelpMarkdown.helpLinks(section.markdown())) {
                    try {
                        read.read(link);
                    } catch (UnknownHelpTopicException dead) {
                        problems.add(topic.resource() + ": dead link help:" + link);
                    }
                }
            }
        }
        assertNone(problems);
    }

    @Test
    public void sinceIsNotNewerThanTheBuiltVersion() {
        String built = System.getProperty("help.projectVersion", "");
        if (built.isEmpty()) {
            return;
        }
        List<String> problems = new ArrayList<String>();
        for (HelpTopic topic : bundled().topics()) {
            if (!topic.since().isEmpty() && compare(topic.since(), built) > 0) {
                problems.add(topic.resource() + ": since " + topic.since() + " is newer than " + built);
            }
        }
        assertNone(problems);
    }

    @Test
    public void toolExampleIdExists() {
        // The read tool names this chapter as its example id.
        assertNotNull(bundled().topic("help:settings.speech"));
    }

    @Test
    public void typicalQuestionsFindTheRightChapter() {
        HelpSearchUseCase search = new HelpSearchUseCase(bundled());
        assertTopHits(search, "Wo stelle ich die Stimme ein?", "settings.speech");
        assertTopHits(search, "Wie hänge ich eine Datei an den Chat an?", "chat.attachments");
        assertTopHits(search, "Wo finde ich die Logs?", "settings.technical-details");
    }

    private static void assertTopHits(HelpSearchUseCase search, String question, String expected) {
        List<HelpHit> hits = search.search(question, 3, 100);
        List<String> ids = new ArrayList<String>();
        for (HelpHit hit : hits) {
            ids.add(hit.topic().id());
        }
        assertTrue(question + " → " + ids, ids.contains(expected));
    }

    private static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int diff = Integer.parseInt(a[i]) - Integer.parseInt(b[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return a.length - b.length;
    }

    private static void assertNone(List<String> problems) {
        if (!problems.isEmpty()) {
            fail(problems.size() + " problem(s) in docs/help:\n" + String.join("\n", problems));
        }
    }
}
