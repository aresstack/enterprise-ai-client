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

    @Test
    public void findHitLandsBelowTheBannerAndKeepsItsPosition() throws Exception {
        String markdown = markdown().replace("### Installation Details", "### Installation Zabak");
        KnowledgeReaderPanel reader = reader(markdown, 700, 400);
        SwingUtilities.invokeAndWait(() -> {
            DocumentNavigationSky sky = reader.navigationSky(key());
            JViewport viewport = reader.documentPage(key()).scroll().getViewport();
            int covered = sky.coveredHeight();
            // vom Ende her nach oben springen: der Treffer darf nicht hinter dem Banner landen
            viewport.setViewPosition(new java.awt.Point(0,
                    Math.max(0, viewport.getView().getHeight() - viewport.getExtentSize().height)));
            assertTrue(viewport.getViewPosition().y > 0);
            assertEquals(1, reader.searchFor("zabak"));
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
}
