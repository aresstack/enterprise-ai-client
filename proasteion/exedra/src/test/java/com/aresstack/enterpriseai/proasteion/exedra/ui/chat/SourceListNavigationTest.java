package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.proasteion.exedra.knowledge.ChatSourceNavigation;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.FavoritesStore;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Chat sources open in the internal reader and land in the favorites through their stable ids only. */
public class SourceListNavigationTest {

    private static final String SOURCE = "confluence:https://cf.example/confluence";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static SourceReference page(int number, String pageId) {
        return new SourceReference(number, "Seite " + pageId, "Kapitel " + number,
                "https://cf.example/confluence/pages/viewpage.action?pageId=" + pageId, "", 0.5, 1, 1, SOURCE,
                "confluence:cf.example/confluence/page/" + pageId, "confluence:cf.example/confluence/page/" + pageId
                + "#p" + number);
    }

    private static SourceReference legacy(int number) {
        return new SourceReference(number, "Alte Quelle", "", "https://cf.example/alt", "", 0.4, 1, 0);
    }

    /** Record what reaches the reader; save favorites in a real store. */
    private static final class RecordingReading implements ChatSourceNavigation.Reading {
        final List<ReaderLink> opened = new ArrayList<ReaderLink>();
        final List<Boolean> pinned = new ArrayList<Boolean>();
        final FavoritesStore store;
        final String targetFolder;

        RecordingReading(FavoritesStore store, String targetFolder) {
            this.store = store;
            this.targetFolder = targetFolder;
        }

        @Override
        public void open(ReaderLink link, boolean pin) {
            opened.add(link);
            pinned.add(pin);
        }

        @Override
        public boolean canFavorite() {
            return true;
        }

        @Override
        public boolean addFavorite(ReaderLink link) {
            return store.add(link, targetFolder) != null;
        }

        @Override
        public FavoritesStore.BulkSave addFavorites(String folderName, List<ReaderLink> links) {
            return store.addAllToNewFolder(folderName, links);
        }
    }

    private Path favoritesFile() {
        return temp.getRoot().toPath().resolve("favorites.json");
    }

    private static SourceListPanel panel(List<SourceReference> sources, ChatSourceNavigation navigation) {
        SourceListPanel panel = new SourceListPanel(sources, ComicPalette.defaultPalette(),
                BubblePalette.windowsPhoneInspired());
        panel.setActions(navigation);
        panel.setExpanded(true);
        return panel;
    }

    private static void click(JPopupMenu menu, String text) {
        for (int i = 0; i < menu.getComponentCount(); i++) {
            if (menu.getComponent(i) instanceof JMenuItem && text.equals(((JMenuItem) menu.getComponent(i)).getText())) {
                JMenuItem item = (JMenuItem) menu.getComponent(i);
                assertTrue(text + " must be enabled", item.isEnabled());
                item.doClick();
                return;
            }
        }
        throw new AssertionError("no menu item " + text);
    }

    private static boolean enabled(JPopupMenu menu, String text) {
        for (int i = 0; i < menu.getComponentCount(); i++) {
            if (menu.getComponent(i) instanceof JMenuItem && text.equals(((JMenuItem) menu.getComponent(i)).getText())) {
                return menu.getComponent(i).isEnabled();
            }
        }
        throw new AssertionError("no menu item " + text);
    }

    @Test
    public void confluenceSourceBecomesAReaderLinkWithItsChunk() {
        ReaderLink link = ChatSourceNavigation.linkOf(page(1, "4711"));
        assertEquals(SOURCE, link.sourceId());
        assertEquals("confluence:cf.example/confluence/page/4711", link.resourceId());
        assertEquals("Seite 4711", link.title());
        assertEquals("https://cf.example/confluence/pages/viewpage.action?pageId=4711", link.location());
        assertEquals("confluence:cf.example/confluence/page/4711#p1", link.passageId());
        assertEquals(new ReaderLink(SOURCE, "confluence:cf.example/confluence/page/4711", "", "", ""), link);
    }

    @Test
    public void bookmarkIsTheConfluenceUriWhileTheAddressStaysHttp() throws Exception {
        assertEquals("confluence://cf.example/confluence/page/4711", ChatSourceNavigation.bookmarkUriOf(page(1, "4711")));
        final List<String> copied = new ArrayList<String>();
        Edt.run(() -> {
            SourceListPanel panel = panel(Arrays.asList(page(1, "4711")),
                    new ChatSourceNavigation(new RecordingReading(new FavoritesStore(favoritesFile()), null), null));
            panel.setClipboard(copied::add);
            click(panel.sourceMenu(0), SourceListPanel.MENU_COPY_BOOKMARK);
            click(panel.sourceMenu(0), SourceListPanel.MENU_COPY_ADDRESS);
            assertTrue(SourceListPanel.detailLine(page(1, "4711")).contains("https://cf.example"));
        });
        assertEquals(Arrays.asList("confluence://cf.example/confluence/page/4711",
                "https://cf.example/confluence/pages/viewpage.action?pageId=4711"), copied);
    }

    @Test
    public void leftClickOnTheTitleOpensTheInternalReader() throws Exception {
        final RecordingReading reading = new RecordingReading(new FavoritesStore(favoritesFile()), null);
        Edt.run(() -> {
            SourceListPanel panel = panel(Arrays.asList(page(1, "4711")), new ChatSourceNavigation(reading, null));
            javax.swing.JLabel title = panel.titleLabel(0);
            title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_CLICKED, 0L, InputEvent.BUTTON1_DOWN_MASK, 2, 2,
                    1, false, MouseEvent.BUTTON1));
        });
        assertEquals(1, reading.opened.size());
        assertEquals("confluence:cf.example/confluence/page/4711", reading.opened.get(0).resourceId());
        assertFalse(reading.pinned.get(0)); // preview, like a click on a hit
    }

    @Test
    public void legacySourceWithoutResourceIdIsNotClickable() throws Exception {
        assertNull(ChatSourceNavigation.linkOf(legacy(1)));
        assertEquals("", ChatSourceNavigation.bookmarkUriOf(legacy(1)));
        final RecordingReading reading = new RecordingReading(new FavoritesStore(favoritesFile()), null);
        Edt.run(() -> {
            SourceListPanel panel = panel(Arrays.asList(legacy(1)), new ChatSourceNavigation(reading, null));
            assertFalse(panel.isOpenable(0));
            javax.swing.JLabel title = panel.titleLabel(0);
            title.dispatchEvent(new MouseEvent(title, MouseEvent.MOUSE_CLICKED, 0L, InputEvent.BUTTON1_DOWN_MASK, 2, 2,
                    1, false, MouseEvent.BUTTON1));
            assertFalse(enabled(panel.sourceMenu(0), SourceListPanel.MENU_OPEN));
            assertFalse(enabled(panel.sourceMenu(0), SourceListPanel.MENU_FAVORITE));
            assertFalse(enabled(panel.sourceMenu(0), SourceListPanel.MENU_COPY_BOOKMARK));
            assertTrue(enabled(panel.sourceMenu(0), SourceListPanel.MENU_COPY_ADDRESS));
            assertFalse(enabled(panel.headerMenu(), SourceListPanel.MENU_FAVORITE_ALL));
        });
        assertTrue(reading.opened.isEmpty());
    }

    @Test
    public void rightClickSavesOneSourceAndASecondSaveKeepsIt() throws Exception {
        final FavoritesStore store = new FavoritesStore(favoritesFile());
        final String folder = store.createFolder("Architektur", null);
        Edt.run(() -> {
            SourceListPanel panel = panel(Arrays.asList(page(1, "4711")),
                    new ChatSourceNavigation(new RecordingReading(store, folder), null));
            click(panel.sourceMenu(0), SourceListPanel.MENU_FAVORITE);
            assertEquals("Als Favorit gespeichert.", panel.note());
            click(panel.sourceMenu(0), SourceListPanel.MENU_FAVORITE);
            assertEquals("War bereits ein Favorit.", panel.note());
        });
        assertEquals(1, store.all().size());
        assertEquals(1, store.tree().get(0).children().size());
    }

    @Test
    public void bulkSaveCreatesAFolderKeepsExistingFavoritesWhereTheyAreAndCountsPagesOnce() throws Exception {
        final FavoritesStore store = new FavoritesStore(favoritesFile());
        String architecture = store.createFolder("Architektur", null);
        store.add(ChatSourceNavigation.linkOf(page(2, "2")), architecture);
        store.createFolder("Quellen – IAR-Analyse", null);
        final List<SourceReference> sources = Arrays.asList(page(1, "1"), page(2, "2"), page(3, "3"), page(4, "1"),
                legacy(5));
        Edt.run(() -> {
            SourceListPanel panel = panel(sources,
                    new ChatSourceNavigation(new RecordingReading(store, null), () -> "IAR-Analyse"));
            click(panel.headerMenu(), SourceListPanel.MENU_FAVORITE_ALL);
            assertEquals("2 Quellen gespeichert in „Quellen – IAR-Analyse (2)“, 1 war bereits Favorit.",
                    panel.note());
            // A second run must not toggle anything off.
            click(panel.headerMenu(), SourceListPanel.MENU_FAVORITE_ALL);
            assertEquals("Alle 3 Quellen waren bereits Favoriten.", panel.note());
        });
        assertEquals(3, store.all().size());
        assertEquals("Architektur", store.tree().get(0).name());
        assertEquals(1, store.tree().get(0).children().size()); // still there, not moved
        assertEquals("Quellen – IAR-Analyse (2)", store.tree().get(2).name());
        assertEquals(2, store.tree().get(2).children().size());
        assertEquals(3, store.tree().size()); // no empty folder from the second run
    }

    @Test
    public void openAllPinsEachPageOnce() throws Exception {
        final RecordingReading reading = new RecordingReading(new FavoritesStore(favoritesFile()), null);
        Edt.run(() -> {
            SourceListPanel panel = panel(Arrays.asList(page(1, "1"), page(2, "1"), page(3, "3"), legacy(4)),
                    new ChatSourceNavigation(reading, null));
            click(panel.headerMenu(), SourceListPanel.MENU_OPEN_ALL);
        });
        assertEquals(2, reading.opened.size());
        assertEquals(Arrays.asList(true, true), reading.pinned);
    }

    @Test
    public void savedSourcesReopenFromTheFavoritesTree() throws Exception {
        final FavoritesStore store = new FavoritesStore(favoritesFile());
        new ChatSourceNavigation(new RecordingReading(store, null), () -> "Analyse")
                .saveAllFavorites(Arrays.asList(page(1, "4711")));
        ReaderLink favorite = new FavoritesStore(favoritesFile()).all().get(0);
        assertEquals(SOURCE, favorite.sourceId());
        assertEquals("confluence:cf.example/confluence/page/4711", favorite.resourceId());
        assertEquals(ChatSourceNavigation.linkOf(page(1, "4711")), favorite); // same reader tab as the click
    }
}
