package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Lesebereich: die Amber-Suchleiste durchsucht nur den aktiven Reiter; Umsortieren und Gruppen erhalten Vorschau/Angeheftet. */
public class KnowledgeReaderFindTest {

    @Test
    public void findSearchesOnlyTheActiveTab() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink first = new ReaderLink("wiki", "wiki:a", "Erste Seite", "", "");
            ReaderLink second = new ReaderLink("wiki", "wiki:b", "Zweite Seite", "", "");
            reader.open(first, true);
            reader.showMarkdown(first.key(), "# Zabak\n\nZabak steht hier und noch einmal zabak.");
            reader.open(second, true);
            reader.showMarkdown(second.key(), "Hier steht Zabak nur einmal.");

            assertEquals(second.key(), reader.activeKey());
            assertNotNull(reader.findBar(second.key()));
            assertEquals(1, reader.searchFor("zabak"));
            assertEquals(1, reader.findHitCount(second.key()));
            assertEquals(0, reader.findHitCount(first.key()));
            Component hit = reader.findHit(second.key(), 0);
            assertTrue(SwingUtilities.isDescendingFrom(hit, reader));

            reader.select(first.key());
            assertEquals(3, reader.searchFor("ZABAK"));
            assertEquals(1, reader.findHitCount(second.key()));
            assertEquals(0, reader.searchFor(""));
            assertEquals(0, reader.findHitCount(first.key()));
        });
    }

    @Test
    public void reorderingKeepsPreviewAndPinnedState() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink a = new ReaderLink("wiki", "wiki:a", "A", "", "");
            ReaderLink b = new ReaderLink("wiki", "wiki:b", "B", "", "");
            ReaderLink c = new ReaderLink("wiki", "wiki:c", "C", "", "");
            reader.open(a, true);
            reader.open(b, true);
            reader.open(c, false);

            reader.moveTab(c.key(), 0);
            assertEquals(Arrays.asList(c.key(), a.key(), b.key()), reader.tabKeys());
            assertEquals(c.key(), reader.previewKey());
            assertFalse(reader.isPinned(c.key()));
            reader.moveTab(a.key(), 2);
            assertEquals(Arrays.asList(c.key(), b.key(), a.key()), reader.tabKeys());
            assertTrue(reader.isPinned(a.key()));
            assertTrue(reader.isPinned(b.key()));
            assertEquals(c.key(), reader.previewKey());

            // Gruppen: Reiter einer Gruppe stehen nebeneinander, Hineinziehen tritt bei, Herausziehen verlässt sie.
            ReaderTabGroup group = reader.newGroup(c.key(), "Recherche", ReaderTabGroup.Tint.GREEN);
            reader.addToGroup(a.key(), group.id());
            assertEquals(Arrays.asList(c.key(), a.key(), b.key()), reader.tabKeys());
            assertSame(group, reader.groupOf(a.key()));
            reader.moveTab(b.key(), 1);
            assertSame(group, reader.groupOf(b.key()));
            reader.moveTab(b.key(), 2);
            assertSame(group, reader.groupOf(b.key()));
            reader.removeFromGroup(c.key());
            assertNull(reader.groupOf(c.key()));
            assertEquals(Arrays.asList(a.key(), b.key(), c.key()), reader.tabKeys());
            assertEquals(c.key(), reader.previewKey());
            assertTrue(reader.isPinned(a.key()));
            assertEquals("Recherche", reader.groups().get(0).name());
        });
    }

    static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            throw ex;
        }
    }
}
