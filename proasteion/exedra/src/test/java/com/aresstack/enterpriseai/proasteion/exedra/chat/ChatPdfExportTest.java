package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.SourceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ToolCallRecord;
import com.aresstack.enterpriseai.proasteion.exedra.document.ExportGraphics;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatExportFormat;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #132: chat as PDF from the same redacted export tree, with the images and diagrams of its messages. */
public class ChatPdfExportTest {

    private static final long T0 = 1760000000000L;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static BufferedImage picture(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private ChatExport export() throws Exception {
        Path chats = folder.newFolder("chats").toPath();
        FileChatHistoryStore store = new FileChatHistoryStore(chats);
        ChatRecord record = new ChatRecord("chat-1", T0);
        record.setTitle("Diagramme");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(picture(20, 20), "png", png);
        String data = "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER, "Zeig ein Bild.", T0, null));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT,
                "Hier:\n\n![Quadrat](" + data + ")\n\n```mermaid\ngraph TD; A-->B\n```\n\nFertig.", T0 + 1000L, null)
                .withTrace(Collections.singletonList(new SourceRecord(1, "IAR", "Überblick", "wiki:IAR", "3", 0.8,
                        1, 2)), Collections.singletonList(new ToolCallRecord("knowledge_search",
                        "{\"query\":\"IAR\",\"token\":\"geheim-token\"}", true, "ok", T0 + 900L))));
        store.save(record);
        return new ChatExport(store, null, () -> T0 + 9000L, "9.9.9");
    }

    private static ExportGraphics graphics() {
        return new ExportGraphics((code, width) -> picture(width, 100));
    }

    @Test
    public void pdfIsAConversationReportWithEmbeddedImagesAndDiagrams() throws Exception {
        ChatExport export = export();
        Path target = folder.getRoot().toPath().resolve("Diagramme.pdf");
        ExportGraphics graphics = graphics();
        assertTrue(export.write("chat-1", target, ChatExportFormat.PDF, graphics));
        PDDocument document = PDDocument.load(target.toFile());
        try {
            int images = 0;
            for (PDPage page : document.getPages()) {
                for (COSName name : page.getResources().getXObjectNames()) {
                    if (page.getResources().getXObject(name) instanceof PDImageXObject) {
                        images++;
                    }
                }
            }
            assertEquals("the data image and the Mermaid diagram", 2, images);
            String text = new PDFTextStripper().getText(document);
            assertTrue(text, text.contains("Diagramme"));
            assertTrue(text, text.contains("Zeig ein Bild."));
            assertTrue(text, text.contains("IAR wiki:IAR"));
            assertFalse("redacted before rendering", text.contains("geheim-token"));
            assertFalse(text, text.contains("graph TD"));
        } finally {
            document.close();
        }
        assertEquals(Collections.<String>emptyList(), graphics.problems());
        assertFalse(Files.exists(folder.getRoot().toPath().resolve("Diagramme.pdf.tmp")));
    }

    @Test
    public void wordEmbedsTheImagesOfTheMessages() throws Exception {
        ChatExport export = export();
        Path target = folder.getRoot().toPath().resolve("Diagramme.docx");
        assertTrue(export.write("chat-1", target, ChatExportFormat.DOCX, graphics()));
        XWPFDocument document = new XWPFDocument(new FileInputStream(target.toFile()));
        try {
            assertEquals(2, document.getAllPictures().size());
        } finally {
            document.close();
        }
    }
}
