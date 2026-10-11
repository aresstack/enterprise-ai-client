package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JPanel;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;

import static com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderFindTest.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Zurück/Vor über die Reiter wie MainframeMates TabbedPaneManager, mit Knöpfen in der Leiste und Maustasten 4/5. */
public class ReaderNavigationTest {

    private final ReaderLink a = new ReaderLink("wiki", "wiki:a", "A", "", "");
    private final ReaderLink b = new ReaderLink("wiki", "wiki:b", "B", "", "");
    private final ReaderLink c = new ReaderLink("wiki", "wiki:c", "C", "", "");

    @Test
    public void backClosesTheCurrentTabAndForwardReopensIt() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            reader.open(a, true);
            reader.showMarkdown(a.key(), "Seite A");
            reader.open(b, true);
            reader.showMarkdown(b.key(), "Seite B mit wapok");
            ReaderToolbar toolbar = reader.toolbar();
            assertTrue(toolbar.backButton().isEnabled());
            assertFalse(toolbar.forwardButton().isEnabled());

            toolbar.backButton().doClick();
            assertEquals(a.key(), reader.activeKey());
            assertEquals(Collections.singletonList(a.key()), reader.tabKeys());
            assertFalse(toolbar.backButton().isEnabled());
            assertTrue(toolbar.forwardButton().isEnabled());

            toolbar.forwardButton().doClick();
            assertEquals(b.key(), reader.activeKey());
            assertEquals(Arrays.asList(a.key(), b.key()), reader.tabKeys());
            assertEquals("Inhalt bleibt", 1, reader.searchFor("wapok"));
            assertFalse(toolbar.forwardButton().isEnabled());
            assertTrue(toolbar.backButton().isEnabled());
        });
    }

    @Test
    public void backBringsAReplacedPreviewBack() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            reader.open(a, false);
            reader.open(b, false); // ersetzt die Vorschau A
            assertEquals(Collections.singletonList(b.key()), reader.tabKeys());

            assertTrue(reader.back());
            assertEquals(Collections.singletonList(a.key()), reader.tabKeys());
            assertEquals(a.key(), reader.previewKey());
            assertTrue(reader.forward());
            assertEquals(Collections.singletonList(b.key()), reader.tabKeys());
            assertEquals(b.key(), reader.previewKey());
        });
    }

    @Test
    public void aTabClosedByHandLeavesTheHistory() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            reader.open(a, true);
            reader.open(b, true);
            reader.open(c, true);
            reader.close(b.key());
            assertEquals(c.key(), reader.activeKey());
            assertTrue(reader.back());
            assertEquals(a.key(), reader.activeKey());
            assertFalse(reader.back());
        });
    }

    @Test
    public void mouseButtonsFourAndFiveGoBackAndForwardInsideTheReaderOnly() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            reader.open(a, true);
            reader.showMarkdown(a.key(), "Seite A");
            reader.open(b, true);
            reader.showMarkdown(b.key(), "Seite B");
            java.awt.Component inside = reader.toolbar().field();

            assertFalse("Linksklick", reader.mouseButton(1, inside));
            assertFalse("außerhalb", reader.mouseButton(KnowledgeReaderPanel.MOUSE_BACK_BUTTON, new JPanel()));
            assertEquals(b.key(), reader.activeKey());

            assertTrue(reader.mouseButton(KnowledgeReaderPanel.MOUSE_BACK_BUTTON, inside));
            assertEquals(a.key(), reader.activeKey());
            assertTrue(reader.mouseButton(KnowledgeReaderPanel.MOUSE_FORWARD_BUTTON, inside));
            assertEquals(b.key(), reader.activeKey());
        });
    }

    @Test
    public void buttonsSitInTheMiddleOfTheRowAndStepBackForAnOpenSearch() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            reader.open(a, true);
            reader.showMarkdown(a.key(), "Seite A");
            ReaderToolbar toolbar = reader.toolbar();
            toolbar.setSize(600, 40);
            toolbar.doLayout();
            toolbar.backButton().getParent().doLayout();
            Rectangle back = javax.swing.SwingUtilities.convertRectangle(toolbar.backButton().getParent(),
                    toolbar.backButton().getBounds(), toolbar);
            Rectangle forward = javax.swing.SwingUtilities.convertRectangle(toolbar.forwardButton().getParent(),
                    toolbar.forwardButton().getBounds(), toolbar);
            int middle = (back.x + forward.x + forward.width) / 2;
            assertTrue("mittig: " + middle, Math.abs(middle - 300) <= 2);

            toolbar.open();
            assertFalse(toolbar.isNavigationVisible());
            toolbar.close();
            assertTrue(toolbar.isNavigationVisible());
        });
    }
}
