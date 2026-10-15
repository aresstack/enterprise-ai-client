package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * #115: the reader exports the page of the active tab from a button left of the paper clip; since #132 the button
 * opens the save dialog directly (the format is a file type there), no format menu.
 */
public class ReaderPageExportButtonTest {

    private final List<String> exported = new ArrayList<String>();

    private KnowledgeReaderPanel reader() {
        return new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
    }

    private final KnowledgeReaderPanel.PageExporter exporter = new KnowledgeReaderPanel.PageExporter() {
        @Override
        public boolean canExport(ReaderLink link) {
            return !link.isSource();
        }

        @Override
        public void export(ReaderLink link) {
            exported.add(link.title());
        }
    };

    @Test
    public void theButtonSitsLeftOfThePaperClipAndFollowsTheActiveTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KnowledgeReaderPanel reader = reader();
            ReaderLink page = new ReaderLink("wiki", "wiki:page/1", "Roadmap", "", "wiki:page/1");
            reader.open(page, true);
            assertFalse("no exporter, no button", reader.exportButton().isVisible());

            reader.setPageExporter(exporter);
            assertTrue(reader.exportButton().isVisible());
            assertSame(reader.attachmentsButton().getParent(), reader.exportButton().getParent());
            List<java.awt.Component> row = Arrays.asList(reader.exportButton().getParent().getComponents());
            assertTrue("left of the paper clip",
                    row.indexOf(reader.exportButton()) < row.indexOf(reader.attachmentsButton()));

            reader.open(ReaderLink.source("wiki", "Übersicht"), true);
            assertFalse("a source overview is no page", reader.exportButton().isVisible());
            reader.exportActivePage();
            assertTrue("nothing exported for a source overview", exported.isEmpty());
        });
    }

    @Test
    public void theButtonExportsOnlyTheActivePageWithoutAFormatMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            KnowledgeReaderPanel reader = reader();
            reader.setPageExporter(exporter);
            reader.open(new ReaderLink("wiki", "wiki:page/1", "Roadmap", "", "wiki:page/1"), true);
            reader.open(new ReaderLink("wiki", "wiki:page/2", "Betrieb", "", "wiki:page/2"), true);
            assertEquals("Exportieren …", reader.exportButton().getToolTipText());
            reader.exportButton().doClick();
            assertEquals(Arrays.asList("Betrieb"), exported);
        });
    }
}
