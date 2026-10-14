package com.aresstack.enterpriseai.proasteion.exedra.document;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportDocumentNode;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** #115: the three formats write the same export tree with its hierarchy. */
public class ExportWritersTest {

    private static final SourceAddress SOURCE = SourceAddress.of("test://wiki.example.org");

    private static ExportDocumentNode page(String title, String text, ExportDocumentNode... children) {
        KnowledgeResourceId id = KnowledgeResourceId.of("test", "wiki.example.org/" + title.hashCode());
        KnowledgeDocument document = KnowledgeDocument.of(KnowledgeResource.builder(id, SOURCE).title(title).build(),
                text);
        return new ExportDocumentNode(title, document, Arrays.asList(children));
    }

    /** Konzept → (1. Einleitung, 2. Bausteine → (2.1 Kern, 2.2 Rand), 3. Betrieb). */
    static ExportDocumentNode tree() {
        return page("Konzept", "# Zweck\n\nDieses Konzept beschreibt **alles**.",
                page("1. Einleitung", "Text der Einleitung mit [Link](https://example.org/a)."),
                page("2. Bausteine", "## Übersicht\n\n- eins\n- zwei\n\n| A | B |\n|---|---|\n| 1 | 2 |",
                        page("2.1 Kern", "```\ncode → zeile\n```"),
                        page("2.2 Rand", "> Zitat")),
                page("3. Betrieb", "Ende."));
    }

    private static byte[] write(ExportFormat format, ExportDocumentNode root) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        format.writer().write(root, out);
        return out.toByteArray();
    }

    @Test
    public void markdownShiftsPageHeadingsBelowTheChapterInTreeOrder() throws Exception {
        String markdown = new String(write(ExportFormat.MARKDOWN, tree()), "UTF-8");
        List<String> headings = new ArrayList<String>();
        for (String line : markdown.split("\n")) {
            if (line.startsWith("#")) {
                headings.add(line);
            }
        }
        assertEquals(Arrays.asList("# Konzept", "## Zweck", "## 1. Einleitung", "## 2. Bausteine", "#### Übersicht",
                "### 2.1 Kern", "### 2.2 Rand", "## 3. Betrieb"), headings);
        assertTrue(markdown, markdown.contains("code → zeile"));
        assertTrue(markdown, markdown.contains("| 1 | 2 |"));
    }

    @Test
    public void markdownFallsBackToBoldBeyondSixLevels() {
        MarkdownExportWriter writer = new MarkdownExportWriter();
        assertEquals("###### Tief", MarkdownExportWriter.heading(6, "Tief"));
        assertEquals("**Tiefer**", MarkdownExportWriter.heading(7, "Tiefer"));
        assertEquals("**Kopf**\n\n```\n# kein Kopf\n```", writer.shiftHeadings("## Kopf\n\n```\n# kein Kopf\n```", 5));
        assertEquals("### Zwei\n\nText", writer.shiftHeadings("Zwei\n===\n\nText", 2));
    }

    @Test
    public void aSinglePageIsOneFileWithItsTitleOnTop() throws Exception {
        ExportDocumentNode single = page("Seite", "# Innen\n\nText");
        String markdown = new String(write(ExportFormat.MARKDOWN, single), "UTF-8");
        assertTrue(markdown, markdown.startsWith("# Seite\n\n## Innen\n\nText"));
        PDDocument pdf = PDDocument.load(write(ExportFormat.PDF, single));
        try {
            PDOutlineItem only = pdf.getDocumentCatalog().getDocumentOutline().getFirstChild();
            assertEquals("Seite", only.getTitle());
            assertEquals(null, only.getNextSibling());
            assertEquals(0, pdf.getPages().indexOf(page(only)));
        } finally {
            pdf.close();
        }
        XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(write(ExportFormat.DOCX, single)));
        try {
            assertEquals("Heading1", docx.getParagraphs().get(0).getStyle());
            assertEquals("Seite", docx.getParagraphs().get(0).getText());
            assertFalse(docx.getParagraphs().get(0).getCTP().getFldSimpleList().size() > 0);
        } finally {
            docx.close();
        }
    }

    @Test
    public void wordUsesHeadingStylesByDepthAndATableOfContentsField() throws Exception {
        XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(write(ExportFormat.DOCX, tree())));
        try {
            boolean toc = false;
            List<String> headings = new ArrayList<String>();
            for (XWPFParagraph paragraph : docx.getParagraphs()) {
                if (!paragraph.getCTP().getFldSimpleList().isEmpty()) {
                    toc |= paragraph.getCTP().getFldSimpleArray(0).getInstr().startsWith("TOC ");
                }
                if (paragraph.getStyle() != null && paragraph.getStyle().startsWith("Heading")) {
                    headings.add(paragraph.getStyle() + ":" + paragraph.getText());
                }
            }
            assertTrue("TOC field", toc);
            assertEquals(Arrays.asList("Heading1:Konzept", "Heading2:Zweck", "Heading2:1. Einleitung",
                    "Heading2:2. Bausteine", "Heading4:Übersicht", "Heading3:2.1 Kern", "Heading3:2.2 Rand",
                    "Heading2:3. Betrieb"), headings);
        } finally {
            docx.close();
        }
    }

    @Test
    public void pdfOutlineMirrorsTheTreeAndEveryEntryOpensItsChapter() throws Exception {
        PDDocument pdf = PDDocument.load(write(ExportFormat.PDF, tree()));
        try {
            PDOutlineNode outline = pdf.getDocumentCatalog().getDocumentOutline();
            assertEquals("Konzept[1. Einleitung, 2. Bausteine[2.1 Kern, 2.2 Rand], 3. Betrieb]",
                    describe(outline.getFirstChild()));
            assertEquals(null, outline.getFirstChild().getNextSibling());
            PDFTextStripper stripper = new PDFTextStripper();
            for (PDOutlineItem item : all(outline)) {
                PDPage target = page(item);
                int index = pdf.getPages().indexOf(target);
                stripper.setStartPage(index + 1);
                stripper.setEndPage(index + 1);
                String text = stripper.getText(pdf).trim();
                assertTrue(item.getTitle() + " starts its page: " + text, text.startsWith(item.getTitle()));
            }
        } finally {
            pdf.close();
        }
    }

    @Test
    public void pdfTableOfContentsListsEveryChapterWithPageAndLink() throws Exception {
        PDDocument pdf = PDDocument.load(write(ExportFormat.PDF, tree()));
        try {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String toc = stripper.getText(pdf);
            assertTrue(toc, toc.startsWith(PdfExportWriter.TOC_TITLE));
            List<PDOutlineItem> items = all(pdf.getDocumentCatalog().getDocumentOutline());
            List<PDAnnotationLink> links = new ArrayList<PDAnnotationLink>();
            for (PDAnnotation annotation : pdf.getPage(0).getAnnotations()) {
                if (annotation instanceof PDAnnotationLink) {
                    links.add((PDAnnotationLink) annotation);
                }
            }
            assertEquals(items.size(), links.size());
            for (int i = 0; i < items.size(); i++) {
                PDPage target = page(items.get(i));
                int number = pdf.getPages().indexOf(target) + 1;
                assertTrue(items.get(i).getTitle() + " " + number + " in " + toc,
                        toc.contains(items.get(i).getTitle() + " " + number));
                PDPageDestination destination = (PDPageDestination) links.get(i).getDestination();
                assertEquals(target, destination.getPage());
            }
            assertEquals("each chapter on its own page after the contents", 7, pdf.getNumberOfPages());
        } finally {
            pdf.close();
        }
    }

    private static PDPage page(PDOutlineItem item) throws Exception {
        PDPageDestination destination = (PDPageDestination) item.getDestination();
        assertNotNull(item.getTitle(), destination);
        return destination.getPage();
    }

    private static String describe(PDOutlineItem item) {
        StringBuilder text = new StringBuilder(item.getTitle());
        if (item.getFirstChild() != null) {
            List<String> children = new ArrayList<String>();
            for (PDOutlineItem child = item.getFirstChild(); child != null; child = child.getNextSibling()) {
                children.add(describe(child));
            }
            text.append(children);
        }
        return text.toString();
    }

    private static List<PDOutlineItem> all(PDOutlineNode node) {
        List<PDOutlineItem> items = new ArrayList<PDOutlineItem>();
        for (PDOutlineItem child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            items.add(child);
            items.addAll(all(child));
        }
        return items;
    }

    @Test
    public void allFormatsTakeTheSameTreeWithoutLoadingAnything() throws Exception {
        for (ExportFormat format : ExportFormat.values()) {
            assertTrue(format.name(), write(format, tree()).length > 0);
        }
        assertEquals(Collections.singletonList("md"), Collections.singletonList(ExportFormat.MARKDOWN.extension()));
    }
}
