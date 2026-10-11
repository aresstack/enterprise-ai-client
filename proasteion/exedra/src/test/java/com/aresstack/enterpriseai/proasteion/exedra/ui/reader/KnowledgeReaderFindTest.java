package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Lesebereich: die Amber-Suchleiste durchsucht nur den aktiven Reiter. */
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
