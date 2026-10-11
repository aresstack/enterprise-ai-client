package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.CopyrightFooter;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderFindTest.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Die eine Zeile unter dem Lesebereich: Ruhezustand nur Lupe und Branding, Suche klappt in derselben Zeile auf. */
public class ReaderToolbarTest {

    private static final String TEXT = "# Zabak\n\nZabak steht hier und noch einmal zabak.";

    @Test
    public void restingRowShowsOnlyTheMagnifierAndTheBrandingWhereTheChatHasIt() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = readerWith("wiki:a", TEXT);
            ReaderToolbar toolbar = reader.toolbar();
            assertFalse(toolbar.isSearchOpen());
            assertTrue(toolbar.isBrandingVisible());
            assertEquals(1, toolbar.tools().getComponentCount());
            assertTrue(toolbar.findButton().isEnabled());
            assertFalse(toolbar.findButton().isEmphasized());

            // Wie unter dem Chat: rechts SIDE_MARGIN, unten BOTTOM_MARGIN Abstand, auch in der höheren Zeile.
            toolbar.setSize(600, 40);
            layout(toolbar);
            CopyrightFooter footer = find(toolbar, CopyrightFooter.class);
            assertNotNull(footer);
            Rectangle bounds = SwingUtilities.convertRectangle(footer.getParent(), footer.getBounds(), toolbar);
            assertEquals(600 - CopyrightFooter.SIDE_MARGIN, bounds.x + bounds.width);
            assertEquals(40 - CopyrightFooter.BOTTOM_MARGIN, bounds.y + bounds.height);
        });
    }

    @Test
    public void magnifierOpensTheSearchInTheSameRowAndHidesTheBranding() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = readerWith("wiki:a", TEXT);
            ReaderToolbar toolbar = reader.toolbar();
            toolbar.findButton().doClick();
            assertTrue(toolbar.isSearchOpen());
            assertFalse(toolbar.isBrandingVisible());
            assertTrue("Amber-Akzent", toolbar.findButton().isEmphasized());
            assertTrue(SwingUtilities.isDescendingFrom(toolbar.field(), toolbar));

            toolbar.findButton().doClick();
            assertFalse(toolbar.isSearchOpen());
            assertTrue(toolbar.isBrandingVisible());
        });
    }

    @Test
    public void enterShiftEnterAndEscapeWalkTheHitsAndCloseTheSearch() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = readerWith("wiki:a", TEXT);
            String key = reader.activeKey();
            ReaderToolbar toolbar = reader.toolbar();
            toolbar.open();
            JTextField field = toolbar.field();
            field.setText("zabak");

            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            assertEquals("erste Enter sucht", "1 / 3", toolbar.counterText());
            assertEquals(3, reader.findHitCount(key));
            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            assertEquals("2 / 3", toolbar.counterText());
            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
            assertEquals("1 / 3", toolbar.counterText());
            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK));
            assertEquals("vor dem ersten der letzte", "3 / 3", toolbar.counterText());

            field.setText("nirgends");
            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            assertEquals("0 / 0", toolbar.counterText());

            field.setText("zabak");
            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            press(field, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            assertFalse(toolbar.isSearchOpen());
            assertTrue(toolbar.isBrandingVisible());
            assertEquals("Markierungen weg", 0, reader.findHitCount(key));
            toolbar.open();
            assertEquals("Begriff bleibt", "zabak", field.getText());
            assertEquals("", toolbar.counterText());
        });
    }

    @Test
    public void ctrlFOpensTheSearchOfTheActiveTab() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = readerWith("wiki:a", TEXT);
            Object name = reader.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
            assertNotNull(name);
            assertNotNull(reader.getActionMap().get(name));
            reader.focusFindBar();
            assertTrue(reader.toolbar().isSearchOpen());
            assertFalse(reader.toolbar().isBrandingVisible());
        });
    }

    @Test
    public void everyTabKeepsItsOwnSearch() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink first = new ReaderLink("wiki", "wiki:a", "Erste Seite", "", "");
            ReaderLink second = new ReaderLink("wiki", "wiki:b", "Zweite Seite", "", "");
            reader.open(first, true);
            reader.showMarkdown(first.key(), TEXT);
            reader.open(second, true);
            reader.showMarkdown(second.key(), "Hier steht nichts davon.");
            ReaderToolbar toolbar = reader.toolbar();

            reader.select(first.key());
            assertEquals(3, reader.searchFor("zabak"));
            press(toolbar.field(), KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            assertEquals("2 / 3", toolbar.counterText());

            reader.select(second.key());
            assertFalse(toolbar.isSearchOpen());
            assertTrue(toolbar.isBrandingVisible());
            assertEquals("", toolbar.field().getText());

            reader.select(first.key());
            assertTrue(toolbar.isSearchOpen());
            assertEquals("zabak", toolbar.field().getText());
            assertEquals("2 / 3", toolbar.counterText());
            assertEquals(3, reader.findHitCount(first.key()));
        });
    }

    @Test
    public void tabWithoutContentHasNoSearch() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            assertFalse(reader.toolbar().findButton().isEnabled());
            ReaderLink broken = new ReaderLink("wiki", "wiki:x", "Kaputt", "", "");
            reader.open(broken, true);
            reader.showProblem(broken.key(), "Quelle nicht erreichbar");
            assertFalse(reader.toolbar().findButton().isEnabled());
            assertEquals(0, reader.searchFor("zabak"));
            assertFalse(reader.toolbar().isSearchOpen());
        });
    }

    private static KnowledgeReaderPanel readerWith(String id, String markdown) {
        KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
        ReaderLink link = new ReaderLink("wiki", id, "Seite", "", "");
        reader.open(link, true);
        reader.showMarkdown(link.key(), markdown);
        return reader;
    }

    private static void press(JComponent component, KeyStroke key) {
        Object name = component.getInputMap(JComponent.WHEN_FOCUSED).get(key);
        assertNotNull("Taste " + key + " gebunden", name);
        component.getActionMap().get(name).actionPerformed(new ActionEvent(component, ActionEvent.ACTION_PERFORMED,
                String.valueOf(name)));
    }

    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static <T> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
