package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** #115: a right click on a page of the page tree offers „Exportieren“ and never opens the page. */
public class PageNavigationExportMenuTest {

    private static final String ROOT = "wiki\nspace:DEV";
    private static final String PAGE = "wiki\nnav:wiki/a";

    private final List<String> events = new ArrayList<String>();

    private PageNavigationPanel panel() {
        PageNavigationPanel panel = new PageNavigationPanel(ComicPalette.defaultPalette());
        panel.setActions(new PageNavigationPanel.Actions() {
            @Override
            public void pageOpened(ReaderLink page, boolean pin) {
                events.add("open " + page.resourceId());
            }

            @Override
            public void childrenRequested(String key) {
            }

            @Override
            public void shown() {
            }

            @Override
            public void exportRequested(String key) {
                events.add("export " + key.replace('\n', '|'));
            }
        });
        panel.showRoot(PageNavigationPanel.Item.root(ROOT, "Development", ReaderLink.source("wiki", "Development")));
        panel.addChildren(ROOT, Collections.singletonList(PageNavigationPanel.Item.page(PAGE, "Grobkonzept",
                new ReaderLink("wiki", "nav:wiki/a", "Grobkonzept", "", "nav:wiki/a"), true)), true);
        panel.reveal(Arrays.asList(ROOT, PAGE));
        return panel;
    }

    @Test
    public void aPageOffersOneExportEntryThatAsksForTheFileType() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageNavigationPanel panel = panel();
            JPopupMenu menu = panel.treeMenu(PageNavigationPanel.Item.page(PAGE, "Grobkonzept",
                    new ReaderLink("wiki", "nav:wiki/a", "Grobkonzept", "", "nav:wiki/a"), true));
            assertNotNull(menu);
            assertEquals(1, menu.getComponentCount());
            JMenuItem export = (JMenuItem) menu.getComponent(0);
            assertFalse("no submenu of formats", export instanceof JMenu);
            assertEquals("Exportieren …", export.getText());
            export.doClick();
            assertEquals(Arrays.asList("export wiki|nav:wiki/a"), events);
            assertNull("the space root is no page", panel.treeMenu(PageNavigationPanel.Item.root(ROOT, "Development",
                    ReaderLink.source("wiki", "Development"))));
        });
    }

    @Test
    public void aRightClickSelectsTheRowWithoutOpeningThePage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageNavigationPanel panel = panel();
            panel.tree().setSize(300, 200);
            panel.tree().doLayout();
            panel.tree().clearSelection();
            int row = panel.tree().getRowCount() - 1;
            Rectangle bounds = panel.tree().getRowBounds(row);
            assertNotNull(bounds);
            int x = bounds.x + 10;
            int y = bounds.y + bounds.height / 2;
            for (int id : new int[]{MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED}) {
                panel.tree().dispatchEvent(new MouseEvent(panel.tree(), id, System.currentTimeMillis(),
                        InputEvent.BUTTON3_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON3));
            }
            assertTrue("selected", panel.tree().isRowSelected(row));
            assertTrue("never opened: " + events, events.isEmpty());
        });
    }
}
