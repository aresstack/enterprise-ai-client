package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.navigation.DocumentNavigationBuilder;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Abnahme Banner (läuft headless): Klick springt und das gewählte Kapitel wird aktiv, Zentrierung des aktiven
 * Kapitels, Mausrad rollt waagrecht ohne das Dokument, Aufklappen zeigt nur die Unterabschnitte des aktiven Kapitels,
 * kleines Fenster mit gekürzten Bezeichnungen, Original unverändert.
 */
public class DocumentNavigationSkyTest {

    private static final String LONG = "Dieser Absatz ist absichtlich lang, damit das Dokument rollen kann. ";

    private static String markdown() {
        StringBuilder md = new StringBuilder("# Handbuch\n\n");
        String[] chapters = {"Einleitung", "Installation", "Betrieb und Überwachung im laufenden Rechenzentrum",
                "Fehlerbehebung", "Anhang"};
        for (String chapter : chapters) {
            md.append("## ").append(chapter).append("\n\n");
            for (int i = 0; i < 6; i++) {
                md.append(LONG).append(LONG).append(LONG).append("\n\n");
            }
            md.append("### ").append(chapter).append(" Details\n\n").append(LONG).append("\n\n");
        }
        return md.toString();
    }

    private static DocumentNavigation navigation(String markdown) {
        return new DocumentNavigationBuilder().headingsOnly("doc", "cap", "Handbuch", markdown, "");
    }

    private static void layout(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static KnowledgeReaderPanel reader(String markdown, int width, int height) throws Exception {
        final KnowledgeReaderPanel[] holder = new KnowledgeReaderPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink link = ReaderLink.source("wiki", "Handbuch");
            reader.open(link, true);
            reader.showDocument(link.key(), markdown, navigation(markdown));
            reader.setSize(width, height);
            for (int i = 0; i < 4; i++) {
                reader.validate();
                layout(reader);
            }
            holder[0] = reader;
        });
        return holder[0];
    }

    private static String key() {
        return ReaderLink.source("wiki", "Handbuch").key();
    }

    @Test
    public void clickJumpsAndTheChosenChapterBecomesActive() throws Exception {
        String markdown = markdown();
        KnowledgeReaderPanel reader = reader(markdown, 700, 400);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            NavigableDocumentPage page = reader.documentPage(key());
            assertNotNull(sky);
            assertTrue("Banner sichtbar", sky.isVisible() && sky.coveredHeight() > 0);
            JViewport viewport = page.scroll().getViewport();
            assertEquals(0, viewport.getViewPosition().y);

            sky.clickChapterForTest(3);
            NavigationSection target = sky.navigation().chapters().get(3);
            assertEquals("Fehlerbehebung", target.label());
            assertTrue("gerollt", viewport.getViewPosition().y > 0);
            assertEquals(target.id(), sky.activeChapterId());
            int y = page.positionOf(target);
            int below = y - viewport.getViewPosition().y;
            assertTrue("Abschnitt steht direkt unter dem Banner: " + below + " vs " + sky.coveredHeight(),
                    below >= sky.coveredHeight() && below <= sky.coveredHeight() + 3 * NavigableDocumentPage.TARGET_GAP);

            // Scroll-Synchronisierung: ein Stück zurückrollen macht das vorige Kapitel aktiv.
            int previous = page.positionOf(sky.navigation().chapters().get(1));
            viewport.setViewPosition(new java.awt.Point(0, previous));
            assertEquals(sky.navigation().chapters().get(1).id(), sky.activeChapterId());
            assertEquals("aktives Kapitel zentriert", sky.targetOffsetForTest(sky.activeChapterId()),
                    sky.stripOffsetForTest());
        });
        assertEquals("Original unverändert", markdown(), markdown);
    }

    /** Dieselben Kapitel als Confluence-Lese-Ansicht (HTML); die Navigation stammt aus dem Text der Seite. */
    private static String html() {
        StringBuilder html = new StringBuilder("<h1>Handbuch</h1>");
        String[] chapters = {"Einleitung", "Installation", "Betrieb und Überwachung im laufenden Rechenzentrum",
                "Fehlerbehebung", "Anhang"};
        for (String chapter : chapters) {
            html.append("<h2>").append(chapter).append("</h2>");
            for (int i = 0; i < 6; i++) {
                html.append("<p>").append(LONG).append(LONG).append(LONG).append("</p>");
            }
            html.append("<h3>").append(chapter).append(" Details</h3><p>").append(LONG).append("</p>");
            if ("Installation".equals(chapter)) {
                html.append("<p>Hier steht Wapok einmal.</p>");
            }
        }
        return html.toString();
    }

    private static KnowledgeReaderPanel htmlReader() throws Exception {
        final KnowledgeReaderPanel[] holder = new KnowledgeReaderPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink link = ReaderLink.source("wiki", "Handbuch");
            reader.open(link, true);
            reader.showHtml(link.key(), html(), "https://confluence.invalid/", java.util.Collections.emptyList(),
                    (image, done) -> done.accept(null));
            reader.updateNavigation(link.key(), navigation(markdown()));
            reader.setSize(700, 400);
            for (int i = 0; i < 4; i++) {
                reader.validate();
                layout(reader);
            }
            holder[0] = reader;
        });
        return holder[0];
    }

    @Test
    public void findHitInTheHtmlReadingViewLandsBelowTheBanner() throws Exception {
        KnowledgeReaderPanel reader = htmlReader();
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            ReaderHtmlView view = reader.htmlView(key());
            JViewport viewport = reader.documentPage(key()).scroll().getViewport();
            int covered = sky.coveredHeight();
            assertTrue(covered > 0);
            viewport.setViewPosition(new java.awt.Point(0,
                    Math.max(0, viewport.getView().getHeight() - viewport.getExtentSize().height)));
            assertTrue(viewport.getViewPosition().y > 0);
            assertEquals(1, reader.searchFor("wapok"));
            assertSame(view, reader.findHit(key(), 0));
            javax.swing.text.Highlighter.Highlight[] marks = view.getHighlighter().getHighlights();
            assertEquals(1, marks.length);
            int top;
            try {
                @SuppressWarnings("deprecation")
                java.awt.Rectangle rect = view.modelToView(marks[0].getStartOffset());
                top = SwingUtilities.convertPoint(view, rect.getLocation(), viewport).y;
            } catch (javax.swing.text.BadLocationException e) {
                throw new AssertionError(e);
            }
            assertTrue("Treffer unter dem Banner: " + top + " vs " + covered, top >= covered);
            assertTrue("Treffer im Bild", top < viewport.getExtentSize().height);
            assertEquals("Banner bleibt oben", covered, sky.coveredHeight());
        });
    }

    @Test
    public void clickInTheHtmlReadingViewHitsTheHeading() throws Exception {
        KnowledgeReaderPanel reader = htmlReader();
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            NavigableDocumentPage page = reader.documentPage(key());
            ReaderHtmlView view = reader.htmlView(key());
            assertNotNull(sky);
            assertNotNull(view);
            assertTrue("Banner sichtbar", sky.isVisible() && sky.coveredHeight() > 0);
            JViewport viewport = page.scroll().getViewport();

            sky.clickChapterForTest(3);
            NavigationSection target = sky.navigation().chapters().get(3);
            assertEquals("Fehlerbehebung", target.label());
            assertTrue("gerollt", viewport.getViewPosition().y > 0);
            int offset = view.headingOffset(NavigableDocumentPage.normalize("Fehlerbehebung"));
            assertTrue(offset > 0);
            int headingY;
            try {
                @SuppressWarnings("deprecation")
                java.awt.Rectangle rect = view.modelToView(offset);
                headingY = SwingUtilities.convertPoint(view, rect.getLocation(), viewport.getView()).y;
            } catch (javax.swing.text.BadLocationException e) {
                throw new AssertionError(e);
            }
            int below = headingY - viewport.getViewPosition().y;
            assertTrue("Überschrift steht direkt unter dem Banner: " + below + " vs " + sky.coveredHeight(),
                    below >= sky.coveredHeight() && below <= sky.coveredHeight() + 3 * NavigableDocumentPage.TARGET_GAP);
            assertEquals(target.id(), sky.activeChapterId());
        });
    }

    @Test
    public void findHitLandsBelowTheBannerAndKeepsItsPosition() throws Exception {
        String markdown = markdown().replace("### Installation Details", "### Installation Wapok");
        KnowledgeReaderPanel reader = reader(markdown, 700, 400);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            JViewport viewport = reader.documentPage(key()).scroll().getViewport();
            int covered = sky.coveredHeight();
            // vom Ende her nach oben springen: der Treffer darf nicht hinter dem Banner landen
            viewport.setViewPosition(new java.awt.Point(0,
                    Math.max(0, viewport.getView().getHeight() - viewport.getExtentSize().height)));
            assertTrue(viewport.getViewPosition().y > 0);
            assertEquals(1, reader.searchFor("wapok"));
            Component hit = reader.findHit(key(), 0);
            int top = SwingUtilities.convertPoint(hit, 0, 0, viewport).y;
            assertTrue("Treffer unter dem Banner: " + top + " vs " + covered, top >= covered);
            assertTrue("Treffer im Bild", top < viewport.getExtentSize().height);
            assertEquals("Banner bleibt oben", covered, sky.coveredHeight());
        });
    }

    @Test
    public void wheelOverTheBannerScrollsSidewaysNotTheDocument() throws Exception {
        KnowledgeReaderPanel reader = reader(markdown(), 360, 400);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            JViewport viewport = reader.documentPage(key()).scroll().getViewport();
            int before = sky.stripOffsetForTest();
            MouseWheelEvent wheel = new MouseWheelEvent(sky, MouseWheelEvent.MOUSE_WHEEL, 0L, 0, 50, 10, 0, false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 1);
            sky.dispatchEvent(wheel);
            assertTrue("Rad verbraucht", wheel.isConsumed());
            assertTrue(sky.manualForTest());
            assertTrue("waagrecht gerollt", sky.stripOffsetForTest() > before);
            assertEquals("Dokument steht", 0, viewport.getViewPosition().y);
        });
    }

    @Test
    public void expandingShowsOnlyTheActiveChaptersSubsections() throws Exception {
        KnowledgeReaderPanel reader = reader(markdown(), 700, 400);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            sky.clickChapterForTest(1);
            sky.setExpanded(true);
            assertEquals(1, sky.outlineSectionsForTest().size());
            assertEquals("Installation Details", sky.outlineSectionsForTest().get(0).label());
            sky.setExpanded(false);
            assertTrue(sky.outlineSectionsForTest().isEmpty());
        });
    }

    @Test
    public void smallWindowShortensLongLabels() throws Exception {
        KnowledgeReaderPanel reader = reader(markdown(), 260, 220);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            assertTrue(sky.isVisible());
            String text = sky.chipTextForTest(2);
            assertTrue("gekürzt: " + text, text.endsWith("…"));
            assertFalse(text.equals(sky.navigation().chapters().get(2).label()));
            assertTrue("Banner lässt Platz für das Dokument", sky.coveredHeight() < 220 / 2);
        });
    }

    private static String manySteps() {
        StringBuilder md = new StringBuilder("# Handbuch\n\n## Einleitung\n\n").append(LONG).append("\n\n");
        md.append("## Installation\n\n").append(LONG).append("\n\n");
        for (int i = 1; i <= 12; i++) {
            md.append("### Schritt ").append(i).append("\n\n").append(LONG).append(LONG).append("\n\n");
        }
        md.append("## Betrieb\n\n").append(LONG).append("\n");
        return md.toString();
    }

    private static KnowledgeReaderPanel reader(String markdown, DocumentNavigation navigation, int width, int height)
            throws Exception {
        final KnowledgeReaderPanel[] holder = new KnowledgeReaderPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink link = ReaderLink.source("wiki", "Handbuch");
            reader.open(link, true);
            reader.showDocument(link.key(), markdown, navigation);
            reader.setSize(width, height);
            for (int i = 0; i < 4; i++) {
                reader.validate();
                layout(reader);
            }
            holder[0] = reader;
        });
        return holder[0];
    }

    private static void relayout(Component component) {
        for (int i = 0; i < 3; i++) {
            ((Container) component).validate();
            layout(component);
        }
    }

    @Test
    public void expandedOutlineIsCappedScrollsInsideAndClickJumpsToTheSubsection() throws Exception {
        String markdown = manySteps();
        KnowledgeReaderPanel reader = reader(markdown, navigation(markdown), 700, 420);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            NavigableDocumentPage page = reader.documentPage(key());
            JViewport document = page.scroll().getViewport();
            sky.clickChapterForTest(1); // Installation
            sky.setExpanded(true);
            relayout(reader);
            int documentY = document.getViewPosition().y;

            // FA-13: begrenzte Höhe, innen rollen
            javax.swing.JScrollPane outline = sky.outlineScrollForTest();
            assertEquals(12, sky.outlineSectionsForTest().size());
            int natural = outline.getViewport().getView().getPreferredSize().height;
            assertTrue("Gliederung begrenzt: " + outline.getHeight() + " < " + natural, outline.getHeight() < natural);
            int row = natural / 12;
            int cap = Math.max(2 * row, sky.getHeight() * com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics
                    .SKY_EXPANDED_MAX_PERCENT / 100 - outline.getY());
            assertTrue("Höhe höchstens " + cap + ": " + outline.getHeight(), outline.getHeight() <= cap);
            MouseWheelEvent wheel = new MouseWheelEvent(outline, MouseWheelEvent.MOUSE_WHEEL, 0L, 0, 20, 20, 0,
                    false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, 2);
            outline.dispatchEvent(wheel);
            assertTrue("innen gerollt", outline.getVerticalScrollBar().getValue() > 0);
            assertEquals("Dokument steht", documentY, document.getViewPosition().y);

            // FA-12: Klick auf einen Unterabschnitt springt dorthin, der Abschnitt wird aktiv
            NavigationSection step8 = sky.outlineSectionsForTest().get(7);
            assertEquals("Schritt 8", step8.label());
            Component cloud = ((Container) outline.getViewport().getView()).getComponent(7);
            cloud.dispatchEvent(new java.awt.event.MouseEvent(cloud, java.awt.event.MouseEvent.MOUSE_PRESSED, 0L,
                    0, 5, 5, 1, false));
            assertEquals(step8.id(), sky.activeSectionId());
            assertEquals(sky.navigation().chapters().get(1).id(), sky.activeChapterId());
            int below = page.positionOf(step8) - document.getViewPosition().y;
            assertTrue("Unterabschnitt direkt unter dem Banner: " + below + " vs " + sky.coveredHeight(),
                    below >= sky.coveredHeight() && below <= sky.coveredHeight() + 3 * NavigableDocumentPage.TARGET_GAP);
        });
    }

    @Test
    public void detectedTopicSectionsAreJumpable() throws Exception {
        String[] passages = {
                "Die Anwendung übernimmt Belege über die Import-Schnittstelle. Der Import prüft jeden Beleg. " + LONG,
                "Belege werden validiert. Danach wird jeder Beleg vom Import verarbeitet. " + LONG,
                "Ein Urlaubsantrag ist etwas anderes. Der Antrag auf Urlaub geht an die Personalstelle. " + LONG,
                "Urlaub muss genehmigt werden. Der Antrag wird geprüft. " + LONG};
        StringBuilder text = new StringBuilder();
        for (String passage : passages) {
            for (int i = 0; i < 4; i++) {
                text.append(passage).append("\n\n");
            }
        }
        String markdown = text.toString();
        com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity world =
                com.aresstack.enterpriseai.astu.embedding.EmbeddingModelIdentity.of("test", 2);
        java.util.List<com.aresstack.enterpriseai.astu.acropolis.navigation.NavigationPassage> located =
                new java.util.ArrayList<com.aresstack.enterpriseai.astu.acropolis.navigation.NavigationPassage>();
        int n = 0;
        for (String passage : passages) {
            boolean receipts = passage.contains("Beleg");
            for (int i = 0; i < 4; i++) {
                located.add(new com.aresstack.enterpriseai.astu.acropolis.navigation.NavigationPassage(
                        "doc@cap#p" + n++, passage, com.aresstack.enterpriseai.astu.embedding.EmbeddingVector.of(
                        world, receipts ? new float[]{1f, 0f} : new float[]{0f, 1f})));
            }
        }
        DocumentNavigation semantic = new DocumentNavigationBuilder().build("doc", "cap", "Notiz", markdown, located,
                (title, texts) -> null);
        assertEquals(DocumentNavigation.Analysis.SEMANTIC, semantic.analysis());
        assertEquals(2, semantic.chapters().size());

        KnowledgeReaderPanel reader = reader(markdown, semantic, 700, 400);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            NavigableDocumentPage page = reader.documentPage(key());
            JViewport document = page.scroll().getViewport();
            NavigationSection holiday = sky.navigation().chapters().get(1);
            assertTrue(holiday.isGenerated());
            sky.clickChapterForTest(1);
            assertEquals(holiday.id(), sky.activeChapterId());
            assertTrue("gerollt", document.getViewPosition().y > 0);
            int below = page.positionOf(holiday) - document.getViewPosition().y;
            assertTrue("Themenabschnitt direkt unter dem Banner: " + below,
                    below >= sky.coveredHeight() && below <= sky.coveredHeight() + 3 * NavigableDocumentPage.TARGET_GAP);
        });
    }
}
