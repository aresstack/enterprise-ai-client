package com.aresstack.enterpriseai.proasteion.exedra.document;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportDocumentNode;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** #121: a source table in the canonical text stays a real table with its cell grid in every export format. */
public class ExportTablesTest {

    /** Canonical text as the HTML source adapters write it: header, separator, empty cells, an escaped pipe. */
    private static final String TABLE = "Vorher.\n\n"
            + "| Name | Wert | Hinweis |\n| --- | --- | --- |\n| | 1 | |\n| B | | x\\|y |\n\nNachher.";

    private static ExportDocumentNode page(String text) {
        KnowledgeResourceId id = KnowledgeResourceId.of("test", "wiki.example.org/tabelle");
        KnowledgeDocument document = KnowledgeDocument.of(
                KnowledgeResource.builder(id, SourceAddress.of("test://wiki.example.org")).title("Tabelle").build(),
                text);
        return new ExportDocumentNode("Tabelle", document, Collections.<ExportDocumentNode>emptyList());
    }

    private static byte[] write(ExportFormat format, ExportDocumentNode root) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        format.writer().write(root, out);
        return out.toByteArray();
    }

    @Test
    public void markdownExportStaysAParsableTable() throws Exception {
        String markdown = new String(write(ExportFormat.MARKDOWN, page(TABLE)), "UTF-8");

        assertTrue(markdown, markdown.contains("| Name | Wert | Hinweis |\n| --- | --- | --- |\n| | 1 | |\n"));
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
        Node root = Parser.builder(options).build().parse(markdown);
        boolean table = false;
        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            table |= node instanceof TableBlock;
        }
        assertTrue(markdown, table);
    }

    @Test
    public void wordExportHasARealTableWithEmptyCellsInPlace() throws Exception {
        XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(write(ExportFormat.DOCX, page(TABLE))));
        try {
            assertEquals(1, docx.getTables().size());
            XWPFTable table = docx.getTables().get(0);
            assertEquals(3, table.getNumberOfRows());
            String[][] expected = {{"Name", "Wert", "Hinweis"}, {"", "1", ""}, {"B", "", "x|y"}};
            for (int r = 0; r < expected.length; r++) {
                assertEquals(3, table.getRow(r).getTableCells().size());
                for (int c = 0; c < 3; c++) {
                    assertEquals("cell " + r + "/" + c, expected[r][c], table.getRow(r).getCell(c).getText().trim());
                }
            }
        } finally {
            docx.close();
        }
    }

    @Test
    public void pdfContinuesARowTallerThanAPageInsteadOfCuttingIt() throws Exception {
        StringBuilder words = new StringBuilder();
        for (int i = 1; i <= 1500; i++) {
            words.append("wort").append(i).append(' ');
        }
        String text = "| Lang | Kurz |\n| --- | --- |\n| " + words.toString().trim() + " | k |";

        PDDocument pdf = PDDocument.load(write(ExportFormat.PDF, page(text)));
        try {
            String extracted = new PDFTextStripper().getText(pdf);
            assertTrue(extracted.contains("wort1 "));
            assertTrue(extracted.contains("wort1500"));
            assertTrue(extracted.contains("Kurz"));
        } finally {
            pdf.close();
        }
    }
}
