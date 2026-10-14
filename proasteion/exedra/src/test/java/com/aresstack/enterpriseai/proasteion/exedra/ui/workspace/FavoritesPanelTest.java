package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** #112: several favorites are selected with Ctrl and Shift and move together. */
public class FavoritesPanelTest {

    private static FavoriteEntry favorite(String id) {
        return FavoriteEntry.favorite(id, new ReaderLink("confluence://host", "confluence:host/page/" + id,
                "Seite " + id, "", null));
    }

    private static List<FavoriteEntry> tree() {
        return Arrays.asList(FavoriteEntry.folder("f", "Projekt X", Collections.singletonList(favorite("in"))),
                favorite("a"), favorite("b"), favorite("c"), favorite("d"));
    }

    private static ComicPalette palette() {
        return ComicPalette.defaultPalette();
    }

    private static void onEdt(Runnable runnable) throws Exception {
        SwingUtilities.invokeAndWait(runnable);
    }

    @Test
    public void selectionKeepsSeveralRowsInRowOrderAcrossARebuild() throws Exception {
        onEdt(() -> {
            FavoritesPanel panel = new FavoritesPanel(palette());
            panel.setFavorites(tree());
            assertEquals(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                    panel.tree().getSelectionModel().getSelectionMode());
            panel.selectAll(Arrays.asList("d", "b"));
            assertEquals(Arrays.asList("b", "d"), ids(panel.selectedEntries()));

            panel.setFavorites(tree()); // the store publishes a new tree after every change
            assertEquals(Arrays.asList("b", "d"), ids(panel.selectedEntries()));
            assertNull("a favorite is selected: the next star lands next to it", panel.targetFolderId());
        });
    }

    @Test
    public void ctrlAndShiftClicksOnlySelectAPlainClickOpens() throws Exception {
        onEdt(() -> {
            FavoritesPanel panel = new FavoritesPanel(palette());
            panel.setFavorites(tree());
            final List<String> opened = new ArrayList<String>();
            panel.setActions(new RecordingActions(opened));
            panel.tree().setSize(320, 400);
            panel.tree().doLayout();

            click(panel, 2, 0);
            click(panel, 3, InputEvent.CTRL_DOWN_MASK);
            click(panel, 4, InputEvent.SHIFT_DOWN_MASK);
            assertEquals("a plain click opens, Ctrl and Shift only select",
                    Collections.singletonList("confluence:host/page/a"), opened);
        });
    }

    @Test
    public void aSelectionWithAFolderNeverDropsIntoThatFolder() {
        List<FavoriteEntry> entries = tree();
        List<FavoriteEntry> moved = Arrays.asList(entries.get(0), entries.get(2));
        assertNull(FavoritesPanel.dropTarget(moved, entries.get(0), null, -1, 0));
        FavoritesPanel.DropTarget between = FavoritesPanel.dropTarget(moved, null, null, -1, 3);
        assertNotNull(between);
        assertNull(between.folderId);
        assertEquals(3, between.index);
        FavoritesPanel.DropTarget behind = FavoritesPanel.dropTarget(moved, entries.get(4), null, 4, -1);
        assertEquals(5, behind.index);
    }

    private static void click(FavoritesPanel panel, int row, int modifiers) {
        Rectangle bounds = panel.tree().getRowBounds(row);
        assertNotNull("row " + row, bounds);
        int x = bounds.x + 10;
        int y = bounds.y + bounds.height / 2;
        // Only the click: the tree UI asks the toolkit for the menu shortcut on a press, which fails headless.
        panel.tree().dispatchEvent(new MouseEvent(panel.tree(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                modifiers | InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
    }

    private static List<String> ids(List<FavoriteEntry> entries) {
        List<String> ids = new ArrayList<String>();
        for (FavoriteEntry entry : entries) {
            ids.add(entry.id());
        }
        return ids;
    }

    private static final class RecordingActions implements FavoritesPanel.Actions {
        private final List<String> opened;

        RecordingActions(List<String> opened) {
            this.opened = opened;
        }

        @Override
        public void favoriteOpened(ReaderLink favorite, boolean pin) {
            opened.add(favorite.resourceId());
        }

        @Override
        public void entryRemoveRequested(String id) {
        }

        @Override
        public String folderCreateRequested(String parentId, String name) {
            return null;
        }

        @Override
        public void folderRenameRequested(String id, String name) {
        }

        @Override
        public boolean moveRequested(List<String> ids, String targetFolderId, int index) {
            return false;
        }
    }
}
