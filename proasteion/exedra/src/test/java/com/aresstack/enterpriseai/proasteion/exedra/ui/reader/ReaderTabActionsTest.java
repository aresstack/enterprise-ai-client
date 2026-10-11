package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Angelos Abnahme: Reiter {@code Titel | Stecknadel | Stern | Schließen}, Stecknadel nur am Vorschau-Reiter (heftet an,
 * kein Losheften), Stern schaltet den Favoriten, Schließen ganz rechts, ungekürzter Titel im Tooltip, keine Kopfzeile,
 * „Bilder (n)“ nur im Rechtsklickmenü, Info-Symbol unten mit vollständiger Adresse, „Adresse kopieren“ immer,
 * „Im Standardbrowser öffnen“ nur für http/https.
 */
public class ReaderTabActionsTest {

    private static final String LONG_TITLE = "Dependencytrack Dokumentation für den Betrieb <intern>";
    private static final String URL = "https://confluence.invalid/confluence/pages/viewpage.action?pageId=42";

    @Test
    public void previewTabCarriesPinStarAndCloseInThisOrderAndThePinOnlyPins() throws Exception {
        run(() -> {
            KnowledgeReaderPanel reader = reader(new ArrayList<String>(), new ArrayList<String>());
            Favorites favorites = new Favorites();
            reader.setListener(favorites);
            ReaderLink link = new ReaderLink("conf", "confluence:x/page/42", LONG_TITLE, "", URL);
            reader.open(link, false);

            JComponent chip = reader.tabChip(link.key());
            assertNotNull(chip);
            List<Component> parts = new ArrayList<Component>();
            Collections.addAll(parts, chip.getComponents());
            assertEquals(4, parts.size());
            assertTrue(parts.get(0) instanceof JLabel);
            assertSame(reader.tabPinButton(link.key()), parts.get(1));
            assertSame(reader.tabFavoriteButton(link.key()), parts.get(2));
            assertSame("Schließen ganz rechts", reader.tabCloseButton(link.key()), parts.get(3));

            String tooltip = chip.getToolTipText();
            assertTrue(tooltip, tooltip.contains("Dependencytrack Dokumentation für den Betrieb &lt;intern&gt;"));
            assertTrue(tooltip, tooltip.contains(KnowledgeReaderPanel.PREVIEW_HINT));

            reader.tabPinButton(link.key()).doClick();
            assertTrue(reader.isPinned(link.key()));
            assertNull("angeheftet: keine Stecknadel mehr, kein Losheften", reader.tabPinButton(link.key()));
            String pinned = reader.tabChip(link.key()).getToolTipText();
            assertTrue(pinned, pinned.contains("&lt;intern&gt;"));
            assertFalse(pinned, pinned.contains(KnowledgeReaderPanel.PREVIEW_HINT));
            assertEquals(3, reader.tabChip(link.key()).getComponentCount());
        });
    }

    @Test
    public void starTogglesTheFavoriteOfItsOwnTab() throws Exception {
        run(() -> {
            KnowledgeReaderPanel reader = reader(new ArrayList<String>(), new ArrayList<String>());
            Favorites favorites = new Favorites();
            reader.setListener(favorites);
            ReaderLink first = new ReaderLink("conf", "confluence:x/page/1", "Erste", "", URL);
            ReaderLink second = new ReaderLink("conf", "confluence:x/page/2", "Zweite", "", URL);
            reader.open(first, true);
            reader.open(second, true);

            assertEquals(KnowledgeReaderPanel.FAVORITE_ADD, reader.tabFavoriteButton(first.key()).getToolTipText());
            reader.tabFavoriteButton(first.key()).doClick();
            assertTrue(favorites.keys.contains(first.key()));
            assertFalse("nur der eigene Reiter", favorites.keys.contains(second.key()));
            assertEquals(KnowledgeReaderPanel.FAVORITE_REMOVE, reader.tabFavoriteButton(first.key()).getToolTipText());
            reader.tabFavoriteButton(first.key()).doClick();
            assertFalse(favorites.keys.contains(first.key()));

            reader.tabCloseButton(second.key()).doClick();
            assertEquals(Collections.singletonList(first.key()), reader.tabKeys());
        });
    }

    @Test
    public void noHeaderRowAndImagesOnlyInTheTabMenu() throws Exception {
        run(() -> {
            KnowledgeReaderPanel reader = reader(new ArrayList<String>(), new ArrayList<String>());
            ReaderLink link = new ReaderLink("conf", "confluence:x/page/7", LONG_TITLE, "", URL);
            reader.open(link, true);
            reader.showHtml(link.key(), "<p><img src=\"" + URL + "&a.png\"></p>", URL,
                    Collections.singletonList(new ReaderImage("a", URL + "&a.png", "a")), (image, done) -> done.accept(null));

            assertFalse("Fundstelle steht nirgends mehr sichtbar", containsText(reader, URL));
            assertFalse("kein Knopf Bilder (n)", containsText(reader, "Bilder (1)"));
            List<String> entries = new ArrayList<String>();
            for (Component entry : reader.tabMenu(link.key()).getComponents()) {
                if (entry instanceof JMenuItem) {
                    entries.add(((JMenuItem) entry).getText());
                }
            }
            assertTrue(entries.toString(), entries.contains("Bilder (1)"));
        });
    }

    @Test
    public void infoShowsTheFullAddressCopiesItAndOpensOnlyWebAddresses() throws Exception {
        final List<String> opened = new ArrayList<String>();
        final List<String> copied = new ArrayList<String>();
        run(() -> {
            KnowledgeReaderPanel reader = reader(opened, copied);
            assertFalse("ohne Reiter nichts zu zeigen", reader.infoButton().isEnabled());
            assertEquals(ReaderPageInfo.TOOLTIP, reader.infoButton().getToolTipText());
            assertTrue("Info liegt in der unteren Zeile neben der Lupe",
                    SwingUtilities.isDescendingFrom(reader.infoButton(), reader.toolbar().tools()));

            ReaderLink web = new ReaderLink("conf", "confluence:x/page/42", "Seite", "", URL);
            reader.open(web, true);
            assertTrue(reader.infoButton().isEnabled());
            JPopupMenu menu = reader.pageInfoMenu();
            assertTrue(addressText(menu), addressText(menu).contains(URL));
            item(menu, ReaderPageInfo.COPY).doClick();
            assertEquals(Collections.singletonList(URL), copied);
            item(menu, ReaderPageInfo.OPEN_IN_BROWSER).doClick();
            assertEquals(Collections.singletonList(URL), opened);

            ReaderLink file = new ReaderLink("files", "file:///C:/Wissen/a.md", "Datei", "", "Wissen/a.md, Abschnitt 2");
            reader.open(file, true);
            JPopupMenu other = reader.pageInfoMenu();
            assertTrue(addressText(other).contains("Wissen/a.md, Abschnitt 2"));
            assertNotNull("kopieren immer", item(other, ReaderPageInfo.COPY));
            assertNull("kein Browser ohne http/https", item(other, ReaderPageInfo.OPEN_IN_BROWSER));

            reader.toolbar().open();
            assertFalse("offene Suche verdrängt das Info-Symbol", reader.infoButton().isVisible());
            reader.toolbar().close();
            assertTrue(reader.infoButton().isVisible());
        });
        assertFalse(ReaderPageInfo.isBrowsable("ftp://host/a"));
        assertFalse(ReaderPageInfo.isBrowsable("wiki:intranet/Handbuch"));
        assertTrue(ReaderPageInfo.isBrowsable("HTTP://host/a"));
    }

    // ------------------------------------------------------------------ Hilfen

    private static KnowledgeReaderPanel reader(List<String> opened, List<String> copied) {
        KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), opened::add,
                (code, width) -> null);
        reader.setClipboard(copied::add);
        return reader;
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (Component entry : menu.getComponents()) {
            if (entry instanceof JMenuItem && text.equals(((JMenuItem) entry).getText())) {
                return (JMenuItem) entry;
            }
        }
        return null;
    }

    private static String addressText(JPopupMenu menu) {
        for (Component entry : menu.getComponents()) {
            if (entry instanceof JTextArea) {
                return ((JTextArea) entry).getText();
            }
        }
        return "";
    }

    private static boolean containsText(Component component, String text) {
        if (component instanceof JLabel && ((JLabel) component).getText() != null
                && ((JLabel) component).getText().contains(text)) {
            return true;
        }
        if (component instanceof javax.swing.AbstractButton && ((javax.swing.AbstractButton) component).getText() != null
                && ((javax.swing.AbstractButton) component).getText().contains(text)) {
            return true;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                if (!(child instanceof javax.swing.text.JTextComponent) && containsText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void run(Runnable body) throws Exception {
        final Throwable[] failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                body.run();
            } catch (Throwable thrown) {
                failure[0] = thrown;
            }
        });
        if (failure[0] instanceof Error) {
            throw (Error) failure[0];
        }
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
        }
    }

    /** Merkt sich Favoriten wie der FavoritesStore, nur im Speicher. */
    private static final class Favorites implements KnowledgeReaderPanel.Listener {
        final Set<String> keys = new HashSet<String>();

        @Override
        public void linkOpened(ReaderLink link, boolean pin) {
        }

        @Override
        public boolean canFavorite() {
            return true;
        }

        @Override
        public boolean isFavorite(ReaderLink link) {
            return keys.contains(link.key());
        }

        @Override
        public void favoriteToggled(ReaderLink link) {
            if (!keys.remove(link.key())) {
                keys.add(link.key());
            }
        }
    }
}
