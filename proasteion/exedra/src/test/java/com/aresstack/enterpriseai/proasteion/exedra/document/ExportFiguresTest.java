package com.aresstack.enterpriseai.proasteion.exedra.document;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportDocumentNode;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ExportImage;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #132: figures and Mermaid diagrams are embedded as pictures in PDF and Word, and kept in Markdown. */
public class ExportFiguresTest {

    private static final SourceAddress SOURCE = SourceAddress.of("test://wiki.example.org");
    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"120\" height=\"60\">"
            + "<rect x=\"10\" y=\"10\" width=\"100\" height=\"40\" fill=\"#3366cc\"/></svg>";

    /** A stand-in for mermaid-java: a picture as wide as asked. */
    private static final ExportGraphics graphics() {
        return new ExportGraphics((code, width) -> picture(width, 200, Color.ORANGE));
    }

    static BufferedImage picture(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    static byte[] encoded(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertTrue(format, ImageIO.write(image, format, out));
        return out.toByteArray();
    }

    private static ExportDocumentNode chapter(String title, String text, List<ExportImage> images,
                                              ExportDocumentNode... children) {
        KnowledgeResourceId id = KnowledgeResourceId.of("test", "wiki.example.org/" + title.hashCode());
        KnowledgeDocument document = KnowledgeDocument.of(KnowledgeResource.builder(id, SOURCE).title(title).build(),
                "plain text");
        return new ExportDocumentNode(title, document, text, images, Arrays.asList(children));
    }

    /** A page with PNG, JPEG, GIF and SVG figures, a Mermaid fence and a child page with its own figure. */
    private static ExportDocumentNode tree() throws Exception {
        List<ExportImage> images = Arrays.asList(
                ExportImage.loaded("https://wiki/a.png", "PNG", encoded(picture(40, 20, Color.RED), "png")),
                ExportImage.loaded("https://wiki/b.jpg", "JPEG", encoded(picture(30, 30, Color.GREEN), "jpg")),
                ExportImage.loaded("https://wiki/c.gif", "GIF", encoded(picture(20, 10, Color.BLUE), "gif")),
                ExportImage.loaded("https://wiki/d.svg", "SVG", SVG.getBytes(StandardCharsets.UTF_8)));
        String text = "Vorher.\n\n![Rot](<https://wiki/a.png>)\n\nMitte ![Grün](<https://wiki/b.jpg>) Text.\n\n"
                + "![Blau](<https://wiki/c.gif>)\n\n![Fläche](<https://wiki/d.svg>)\n\n"
                + "```mermaid\ngraph TD; A-->B\n```\n\nNachher.";
        ExportDocumentNode child = chapter("Kind", "Unten.\n\n![Kindbild](<https://wiki/e.png>)",
                Collections.singletonList(ExportImage.loaded("https://wiki/e.png", "",
                        encoded(picture(4000, 1000, Color.BLACK), "png"))));
        return chapter("Seite", text, images, child);
    }

    private static int pdfImages(byte[] pdf) throws Exception {
        PDDocument document = PDDocument.load(pdf);
        try {
            int count = 0;
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) {
                    if (page.getResources().getXObject(name) instanceof PDImageXObject) {
                        count++;
                    }
                }
            }
            return count;
        } finally {
            document.close();
        }
    }

    @Test
    public void pdfDrawsEveryFigureAndTheMermaidDiagramAsPictures() throws Exception {
        ExportGraphics graphics = graphics();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PdfExportWriter(graphics).write(tree(), out);
        assertEquals("PNG, JPEG, GIF, SVG, Mermaid and the child's figure", 6, pdfImages(out.toByteArray()));
        PDDocument document = PDDocument.load(out.toByteArray());
        try {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text, text.contains("Rot") && text.contains("Grün")); // captions stay
            assertFalse(text, text.contains("[Bild"));
            assertFalse(text, text.contains("graph TD")); // the diagram, not its code
            assertEquals("bookmarks per chapter stay", 2, countOutline(document));
        } finally {
            document.close();
        }
        assertEquals(Collections.<String>emptyList(), graphics.problems());
    }

    private static int countOutline(PDDocument document) {
        int count = 0;
        for (org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem item
                : document.getDocumentCatalog().getDocumentOutline().children()) {
            count++;
            for (Object ignored : item.children()) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void wordEmbedsEveryFigureNoWiderThanTheText() throws Exception {
        ExportGraphics graphics = graphics();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DocxExportWriter(graphics).write(tree(), out);
        XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(out.toByteArray()));
        try {
            List<XWPFPicture> pictures = new ArrayList<XWPFPicture>();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    pictures.addAll(run.getEmbeddedPictures());
                }
            }
            assertEquals(6, pictures.size());
            long maxWidth = org.apache.poi.util.Units.toEMU(450);
            for (XWPFPicture picture : pictures) {
                long width = picture.getCTPicture().getSpPr().getXfrm().getExt().getCx();
                assertTrue("width " + width, width <= maxWidth);
            }
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append('\n');
            }
            assertFalse(text.toString(), text.toString().contains("[Bild"));
            assertFalse(text.toString(), text.toString().contains("graph TD"));
        } finally {
            document.close();
        }
        assertEquals(Collections.<String>emptyList(), graphics.problems());
    }

    @Test
    public void markdownEmbedsFiguresAndKeepsTheMermaidCode() throws Exception {
        ExportGraphics graphics = graphics();
        String markdown = new MarkdownExportWriter(graphics).render(tree());
        assertFalse("no private source address", markdown.contains("https://wiki/"));
        assertTrue(markdown, markdown.contains("![Rot](data:image/png;base64,"));
        assertTrue(markdown, markdown.contains("![Kindbild](data:image/png;base64,"));
        assertTrue(markdown, markdown.contains("```mermaid\ngraph TD; A-->B\n```\n\n![Mermaid-Diagramm](data:image/png"));
    }

    @Test
    public void aMissingFigureLeavesANoticeAndIsReported() throws Exception {
        ExportDocumentNode page = chapter("Seite", "Text.\n\n![Geheim](<https://wiki/x.png>)\n\n"
                        + "![Fremd](https://example.org/web.png)",
                Collections.singletonList(ExportImage.failed("https://wiki/x.png", "Geheim", "kein Zugriff")));
        ExportGraphics graphics = graphics();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PdfExportWriter(graphics).write(page, out);
        PDDocument document = PDDocument.load(out.toByteArray());
        try {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertTrue(text, text.contains("[Abbildung nicht verfügbar: Geheim]"));
        } finally {
            document.close();
        }
        assertEquals(Arrays.asList("Abbildung „Geheim“: kein Zugriff",
                "Abbildung „Fremd“: nicht abgerufen (Adresse außerhalb der Quelle)"), graphics.problems());
    }

    @Test
    public void dataImagesOfTheContentAreEmbeddedWithoutAnyDownload() throws Exception {
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(
                encoded(picture(10, 10, Color.RED), "png"));
        ExportGraphics graphics = graphics();
        assertTrue(graphics.knows(data));
        assertEquals(10, graphics.image(data, "Inline").width());
        assertFalse(graphics.knows("https://example.org/a.png"));
    }

    @Test
    public void wordEmbedsAnImageInsideALink() throws Exception {
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(
                encoded(picture(10, 10, Color.RED), "png"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new DocxExportWriter(graphics()).write(chapter("Seite", "[![Vorschau](" + data + ")](https://example.test)",
                Collections.<ExportImage>emptyList()), out);
        XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(out.toByteArray()));
        try {
            assertEquals(1, document.getAllPictures().size());
        } finally {
            document.close();
        }
    }

    @Test
    public void withoutRendererAMermaidFenceStaysCodeAndIsReported() throws Exception {
        ExportGraphics graphics = new ExportGraphics(null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PdfExportWriter(graphics).write(chapter("Seite", "```mermaid\ngraph TD; A-->B\n```",
                Collections.<ExportImage>emptyList()), out);
        assertEquals(0, pdfImages(out.toByteArray()));
        assertEquals(1, graphics.problems().size());
    }
}
