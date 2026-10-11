package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;

import static com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderFindTest.onEdt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Treffer in Beschriftungen, Tabellenzellen und Textflächen: alle leicht markiert, der aktuelle stärker. */
public class ReaderFindMarkingTest {

    @Test
    public void labelsCellsAndTextGetALightMarkAndTheCurrentHitAStrongOne() throws Exception {
        onEdt(() -> {
            JPanel root = new JPanel();
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            JLabel label = new JLabel("Zabak im Titel");
            JTable table = new JTable(new Object[][] {{"kein Treffer", "und zabak"}}, new Object[] {"Zabak", "Wert"});
            JTextArea text = new JTextArea("hier zabak");
            root.add(label);
            root.add(table);
            root.add(text);
            ReaderFind find = new ReaderFind(root);

            assertEquals(4, find.search("zabak"));
            assertTrue(find.hitComponent(0) == label);
            assertTrue(find.hitComponent(1) == table);
            assertTrue(find.hitComponent(2) == table);
            assertTrue(find.hitComponent(3) == text);
            assertTrue(ReaderFind.HIT.getRGB() != Color.YELLOW.getRGB());

            // erster Treffer stark, die übrigen leicht
            assertTrue(label.isOpaque());
            assertEquals(ReaderFind.CURRENT_HIT, label.getBackground());
            assertEquals(2, cellColors(table).size());
            assertFalse(cellColors(table).containsValue(ReaderFind.CURRENT_HIT));
            assertEquals(ReaderFind.HIT, textColor(text));

            find.next(); // Spaltentitel
            assertEquals(ReaderFind.HIT, label.getBackground());
            assertTrue(cellColors(table).containsValue(ReaderFind.CURRENT_HIT));
            find.next();
            find.next(); // Textfläche
            assertFalse(cellColors(table).containsValue(ReaderFind.CURRENT_HIT));
            assertEquals(ReaderFind.CURRENT_HIT, textColor(text));

            find.clear();
            assertFalse("Beschriftung zurückgesetzt", label.isOpaque());
            assertNull(table.getClientProperty(ReaderFind.TABLE_HITS));
            assertEquals(0, text.getHighlighter().getHighlights().length);
        });
    }

    @Test
    public void aMarkdownTableCellIsFoundInTheReader() throws Exception {
        onEdt(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette());
            ReaderLink page = new ReaderLink("wiki", "wiki:t", "Tabelle", "", "");
            reader.open(page, true);
            reader.showMarkdown(page.key(), "| Name | Wert |\n|---|---|\n| zabak | 1 |\n");
            assertEquals(1, reader.searchFor("zabak"));
            assertTrue(reader.findHit(page.key(), 0) instanceof JTable);
        });
    }

    @Test
    public void theMagnifierIsSquare() throws Exception {
        onEdt(() -> {
            Dimension size = new ReaderToolbar().findButton().getPreferredSize();
            assertEquals(size.width, size.height);
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Color> cellColors(JTable table) {
        return (Map<Object, Color>) table.getClientProperty(ReaderFind.TABLE_HITS);
    }

    private static Color textColor(JTextArea text) {
        Highlighter.Highlight[] marks = text.getHighlighter().getHighlights();
        assertEquals(1, marks.length);
        return ((DefaultHighlighter.DefaultHighlightPainter) marks[0].getPainter()).getColor();
    }
}
