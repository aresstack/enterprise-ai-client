package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.acropolis.passage.VocabularyEmbeddingPort;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Abnahme Arbeitspaket A/B: Kapitelhierarchie aus Originalüberschriften, Themenabschnitte für Text ohne
 * Zwischenüberschriften, Positionen auf genau den angezeigten Text, Original unverändert, Benenner austauschbar,
 * Rückfall bei Fehlern.
 */
public class DocumentNavigationBuilderTest {

    private static final String[] PASSAGES = {
            "Die Anwendung übernimmt Belege über die Import-Schnittstelle. Der Import prüft jeden Beleg.",
            "Belege werden validiert. Danach wird jeder Beleg vom Import verarbeitet.",
            "Die Ergebnisse landen in der Datenbank. Die Datenbank speichert alles.",
            "Der Speicher der Datenbank wird gesichert. Die Datenbank läuft nachts.",
            "Ein Urlaubsantrag ist etwas anderes. Der Antrag auf Urlaub geht an die Personalstelle.",
            "Urlaub muss genehmigt werden. Der Antrag wird geprüft."};

    private final VocabularyEmbeddingPort vocabulary = new VocabularyEmbeddingPort("beleg", "import", "datenbank",
            "speicher", "urlaub", "antrag");
    private final DocumentNavigationBuilder builder = new DocumentNavigationBuilder();

    private List<NavigationPassage> passages(String prefix, String... texts) {
        List<NavigationPassage> list = new ArrayList<NavigationPassage>();
        for (int i = 0; i < texts.length; i++) {
            list.add(new NavigationPassage("doc@cap#" + prefix + i, texts[i], vocabulary.vectorFor(texts[i])));
        }
        return list;
    }

    @Test
    public void confluenceHeadingsGiveTheChapterHierarchy() {
        String text = "# Betriebshandbuch\n\nEinleitung.\n\n## Installation\n\nSchritte.\n\n### Voraussetzungen\n\n"
                + "Java 8.\n\n### Ablauf\n\nStarten.\n\n## Betrieb\n\nTäglich.\n\n```\n# kein Kapitel\n```\n";
        DocumentNavigation navigation = builder.headingsOnly("doc", "cap", "Betriebshandbuch", text, "");

        assertEquals(DocumentNavigation.Analysis.HEADINGS, navigation.analysis());
        assertEquals("einzige Titelüberschrift wird aufgelöst", Arrays.asList("Installation", "Betrieb"),
                labels(navigation.chapters()));
        NavigationSection installation = navigation.chapters().get(0);
        assertEquals(Arrays.asList("Voraussetzungen", "Ablauf"), labels(installation.children()));
        assertEquals(installation.id(), installation.children().get(0).parentId());
        assertNull(installation.parentId());
        for (NavigationSection section : navigation.flatten()) {
            assertFalse("Originalüberschrift ist nie erzeugt", section.isGenerated());
            assertEquals("doc", section.position().resourceId());
            assertEquals("cap", section.position().captureId());
            assertTrue("Position zeigt auf die Überschrift im angezeigten Text",
                    text.startsWith("#", section.position().start()));
            assertTrue(text.substring(section.position().start()).contains(section.label()));
        }
        assertTrue("Code-Zaun ist keine Überschrift", navigation.find("doc@cap#h99") == null);
        assertEquals(4, navigation.flatten().size());
    }

    @Test
    public void textWithoutHeadingsGetsJumpableTopicSections() {
        String text = String.join("\n\n", PASSAGES);
        String original = new String(text);
        DocumentNavigation navigation = builder.build("doc", "cap", "Notiz", text, passages("p", PASSAGES),
                AnalysisLabels.KEEP);

        assertEquals(DocumentNavigation.Analysis.SEMANTIC, navigation.analysis());
        assertTrue("mindestens zwei Themen", navigation.chapters().size() >= 2);
        int last = -1;
        for (NavigationSection chapter : navigation.chapters()) {
            assertTrue(chapter.isGenerated());
            assertEquals(NavigationSection.Origin.TOPIC, chapter.origin());
            int start = chapter.position().start();
            assertTrue("Lesereihenfolge", start > last);
            last = start;
            String anchor = chapter.position().anchor();
            assertTrue("Anker steht an der Startposition im angezeigten Text",
                    text.startsWith(anchor.substring(0, Math.min(20, anchor.length())), start));
        }
        assertEquals("Thema heißt nach der ersten Passage", "doc@cap#p0", navigation.chapters().get(0).id());
        NavigationSection holiday = navigation.chapters().get(navigation.chapters().size() - 1);
        assertEquals(text.indexOf(PASSAGES[4]), holiday.position().start());
        assertEquals("Original unverändert", original, text);
    }

    @Test
    public void topicsSubdivideOnlyTheChapterTheyBelongTo() {
        String text = "# Seite\n\nKurze Einleitung.\n\n## Verarbeitung\n\n" + PASSAGES[0] + "\n\n" + PASSAGES[1]
                + "\n\n" + PASSAGES[2] + "\n\n" + PASSAGES[3] + "\n\n## Personal\n\n" + PASSAGES[4] + "\n\n"
                + PASSAGES[5] + "\n";
        List<NavigationPassage> passages = passages("q", PASSAGES);
        DocumentNavigation navigation = builder.build("doc", "cap", "Seite", text, passages, AnalysisLabels.KEEP);

        NavigationSection processing = navigation.chapters().get(navigation.chapters().size() - 2);
        assertEquals("Verarbeitung", processing.label());
        assertFalse(processing.isGenerated());
        assertEquals(2, processing.children().size());
        for (NavigationSection topic : processing.children()) {
            assertTrue(topic.isGenerated());
            assertEquals(processing.id(), topic.parentId());
            assertEquals(processing.level() + 1, topic.level());
        }
        assertEquals(text.indexOf(PASSAGES[2]), processing.children().get(1).position().start());
        NavigationSection personal = navigation.chapters().get(navigation.chapters().size() - 1);
        assertEquals("ein Thema allein unterteilt nichts", 0, personal.children().size());
    }

    @Test
    public void labelerNamesTopicsAndFailuresFallBackToAnalysisLabels() {
        String text = String.join("\n\n", PASSAGES);
        SectionLabeler named = new SectionLabeler() {
            @Override
            public List<String> labels(String documentTitle, List<String> sectionTexts) {
                List<String> out = new ArrayList<String>();
                for (int i = 0; i < sectionTexts.size(); i++) {
                    out.add("Thema " + (i + 1));
                }
                return out;
            }
        };
        DocumentNavigation labeled = builder.build("doc", "cap", "Notiz", text, passages("p", PASSAGES), named);
        assertEquals("Thema 1", labeled.chapters().get(0).label());

        SectionLabeler broken = new SectionLabeler() {
            @Override
            public List<String> labels(String documentTitle, List<String> sectionTexts) {
                throw new IllegalStateException("Modell weg");
            }
        };
        DocumentNavigation fallback = builder.build("doc", "cap", "Notiz", text, passages("p", PASSAGES), broken);
        assertEquals(DocumentNavigation.Analysis.SEMANTIC, fallback.analysis());
        assertTrue(fallback.chapters().get(0).label().startsWith("Die Anwendung übernimmt Belege"));
    }

    @Test
    public void fewPassagesKeepTheOriginalHeadings() {
        String text = "## A\n\n" + PASSAGES[0] + "\n\n## B\n\n" + PASSAGES[4];
        DocumentNavigation navigation = builder.build("doc", "cap", "T", text,
                passages("p", PASSAGES[0], PASSAGES[4]), AnalysisLabels.KEEP);
        assertEquals(DocumentNavigation.Analysis.HEADINGS, navigation.analysis());
        assertEquals(Arrays.asList("A", "B"), labels(navigation.chapters()));
        assertFalse(navigation.fallbackReason().isEmpty());
    }

    private static List<String> labels(List<NavigationSection> sections) {
        List<String> out = new ArrayList<String>();
        for (NavigationSection section : sections) {
            out.add(section.label());
        }
        return out;
    }
}
