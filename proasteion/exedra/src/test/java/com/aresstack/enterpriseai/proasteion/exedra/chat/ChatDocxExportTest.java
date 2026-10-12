package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.history.AttachmentRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.BookmarkRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.QuoteRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ResourceReferenceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.SourceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ToolCallRecord;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatExportFormat;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Akzeptanz „Chat exportieren …“ als Word-Dokument: Reihenfolge und Autoren der Nachrichten, Markdown als echte
 * Word-Struktur (Überschriften, Fett/Kursiv/Code, Listen, Tabellen, Hyperlinks), Anhänge, Seiten, Quellen und Zitate
 * unter der Nachricht, Umlaute, keine Werkzeugaufrufe und keine Zugangsdaten im entpackten DOCX.
 */
public class ChatDocxExportTest {

    private static final long T0 = 1760000000000L;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private FileChatHistoryStore store;
    private ChatExport export;

    @Before
    public void setUp() throws Exception {
        store = new FileChatHistoryStore(folder.newFolder("chats").toPath());
        ChatRecord record = new ChatRecord("chat-1", T0);
        record.setTitle("Was ist IAR?");
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER,
                "Was ist IAR? Ümlaut „ok“ ✓ & <tag> ß", T0,
                Collections.singletonList(new AttachmentRecord("att-1", "notiz.txt", 12L)))
                .withBookmarks(Collections.singletonList(new BookmarkRecord("https://wiki.example.test/IAR",
                        "IAR Überblick")))
                .withQuotes(Collections.singletonList(new QuoteRecord("eine  zitierte\nPassage", T0 - 5000L, 0, 10)))
                .withReferences(Collections.singletonList(new ResourceReferenceRecord("attachment", "att-0",
                        "frueher.pdf"))));
        String answer = "## Überblick\n\n"
                + "**IAR** ist *ein* Testbegriff mit `code` und ~~alt~~.\n\n"
                + "- erster Punkt\n- zweiter Punkt\n  - eingerückt\n\n"
                + "3. drei\n4. vier\n\n"
                + "| Spalte A | Spalte B |\n|---|:---:|\n| Wert 1 | **fett** |\n| Wert 2 | [Link](https://example.test/b) |\n\n"
                + "> Ein Zitat\n\n---\n\n"
                + "```java\nint x = 1;\n```\n\n"
                + "Mehr unter <https://example.test/auto>.\n"
                + "Token Authorization: Bearer abcdefgh12345678xyz und sk-abcdefghijklmnopqrstuv";
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, answer, T0 + 60000L, null)
                .withTrace(Collections.singletonList(new SourceRecord(1, "IAR Quelle", "Abschnitt Eins",
                                "https://wiki.example.test/IAR", "Version 3", 0.8, 1, 2)),
                        Collections.singletonList(new ToolCallRecord("knowledge_search",
                                "{\"query\":\"IAR\",\"token\":\"geheim-token\"}", true, "id: wiki:IAR",
                                T0 + 50000L))));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_INFO, "Wissenssuche ausgefallen.",
                T0 + 120000L, null));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER, "Und weiter?", T0 + 180000L,
                Collections.singletonList(new AttachmentRecord("att-1", "notiz.txt", 12L))));
        record.setModifiedAt(T0 + 180000L);
        store.save(record);
        export = new ChatExport(store, null, () -> T0 + 900000L, "9.9.9");
    }

    @Test
    public void messagesKeepTheirOrderAndAuthorsAreDistinguishable() throws Exception {
        XWPFDocument document = write();
        List<String> heads = new ArrayList<String>();
        List<String> colors = new ArrayList<String>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (!runs.isEmpty() && runs.get(0).isBold() && runs.get(0).getColor() != null
                    && Arrays.asList("Du", "Assistent", "Hinweis").contains(runs.get(0).text())) {
                heads.add(paragraph.getText());
                colors.add(runs.get(0).getColor());
            }
        }
        assertEquals(4, heads.size());
        assertTrue(heads.get(0), heads.get(0).startsWith("Du · "));
        assertTrue(heads.get(1).startsWith("Assistent · "));
        assertTrue(heads.get(2).startsWith("Hinweis · "));
        assertTrue(heads.get(3).startsWith("Du · "));
        assertEquals(ChatDocxWriter.USER_COLOR, colors.get(0));
        assertEquals(ChatDocxWriter.ASSISTANT_COLOR, colors.get(1));
        assertEquals(ChatDocxWriter.NOTICE_COLOR, colors.get(2));
        assertTrue(text(document).indexOf("Was ist IAR?") < text(document).indexOf("Testbegriff"));
        assertTrue(text(document).indexOf("Wissenssuche ausgefallen.") < text(document).indexOf("Und weiter?"));
        // Titel als Titel, keine Chat-ID im Text (nur in den Dokumenteigenschaften)
        assertEquals(ChatDocxWriter.STYLE_TITLE, document.getParagraphs().get(0).getStyle());
        assertEquals("Was ist IAR?", document.getParagraphs().get(0).getText());
        assertFalse(text(document).contains("chat-1"));
        assertEquals("chat-1", document.getProperties().getCoreProperties().getIdentifier());
    }

    @Test
    public void markdownHeadingsBecomeWordHeadings() throws Exception {
        XWPFDocument document = write();
        XWPFParagraph heading = null;
        for (XWPFParagraph candidate : document.getParagraphs()) {
            if (candidate.getText().equals("Überblick")) {
                heading = candidate;
            }
        }
        assertNotNull(heading);
        assertEquals("Heading2", heading.getStyle());
        XWPFStyle style = document.getStyles().getStyle("Heading2");
        assertNotNull(style);
        assertEquals("heading 2", style.getName());
        assertFalse(heading.getText().contains("#"));
    }

    @Test
    public void boldItalicCodeAndStrikeAreRuns() throws Exception {
        XWPFDocument document = write();
        XWPFParagraph paragraph = paragraph(document, "Testbegriff");
        assertTrue(run(paragraph, "IAR").isBold());
        assertTrue(run(paragraph, "ein").isItalic());
        assertEquals("Consolas", run(paragraph, "code").getFontFamily());
        assertTrue(run(paragraph, "alt").isStrikeThrough());
        assertFalse(paragraph.getText().contains("**"));
        assertFalse(paragraph.getText().contains("`"));
    }

    @Test
    public void listsAreWordLists() throws Exception {
        XWPFDocument document = write();
        XWPFParagraph first = paragraph(document, "erster Punkt");
        XWPFParagraph nested = paragraph(document, "eingerückt");
        XWPFParagraph three = paragraph(document, "drei");
        assertNotNull(first.getNumID());
        assertEquals(first.getNumID(), paragraph(document, "zweiter Punkt").getNumID());
        assertEquals(0, first.getNumIlvl().intValue());
        assertEquals(1, nested.getNumIlvl().intValue());
        assertNotNull(three.getNumID());
        assertFalse(three.getNumID().equals(first.getNumID()));
        assertEquals("decimal", three.getNumFmt());
        assertEquals("bullet", first.getNumFmt());
        assertFalse(first.getText().startsWith("-"));
    }

    @Test
    public void tablesAreWordTablesAndLinksAreHyperlinks() throws Exception {
        XWPFDocument document = write();
        XWPFTable table = null;
        for (XWPFTable candidate : document.getTables()) {
            if (candidate.getRow(0).getCell(0).getText().equals("Spalte A")) {
                table = candidate;
            }
        }
        assertNotNull("Markdown-Tabelle fehlt", table);
        assertEquals(3, table.getNumberOfRows());
        assertEquals(2, table.getRow(0).getTableCells().size());
        assertEquals("Spalte B", table.getRow(0).getCell(1).getText());
        assertEquals("Wert 1", table.getRow(1).getCell(0).getText());
        assertTrue(table.getRow(0).getCell(0).getParagraphs().get(0).getRuns().get(0).isBold());
        assertTrue(table.getRow(1).getCell(1).getParagraphs().get(0).getRuns().get(0).isBold());
        assertEquals("Link", table.getRow(2).getCell(1).getText());

        List<String> links = new ArrayList<String>();
        collectLinks(document, document.getParagraphs(), links);
        collectLinks(document, table.getRow(2).getCell(1).getParagraphs(), links);
        assertTrue(links.toString(), links.contains("https://example.test/b"));
        assertTrue(links.toString(), links.contains("https://example.test/auto"));
        assertTrue(links.toString(), links.contains("https://wiki.example.test/IAR"));
    }

    @Test
    public void attachmentsPagesQuotesReferencesAndSourcesFollowTheirMessage() throws Exception {
        XWPFDocument document = write();
        String text = text(document);
        int user = text.indexOf("Was ist IAR?");
        int assistant = text.indexOf("Testbegriff");
        assertTrue(text.indexOf("Anhänge") > user && text.indexOf("Anhänge") < assistant);
        assertTrue(text.indexOf("notiz.txt") > user && text.indexOf("notiz.txt") < assistant);
        assertTrue(text.indexOf("Verwendete Seiten") < assistant);
        assertTrue(text.indexOf("IAR Überblick") < assistant);
        assertTrue(text.contains("„eine zitierte Passage“"));
        assertTrue(text.indexOf("Zitate") < assistant);
        assertTrue(text.indexOf("frueher.pdf") < assistant);

        int sources = text.indexOf("Quellen", assistant);
        assertTrue(sources > assistant);
        assertTrue(text.contains("[1] IAR Quelle"));
        assertTrue(text.contains("Abschnitt: Abschnitt Eins"));
        assertTrue(text.contains("Ort: https://wiki.example.test/IAR"));
        assertTrue(text.contains("Stand: Version 3"));

        // Werkzeugaufrufe gehören in den JSON-Export, nicht in den Bericht
        assertFalse(text.contains("knowledge_search"));
        // Ressourcen des Chats: die zweimal angehängte Datei nur einmal
        int resources = text.indexOf("Ressourcen des Chats");
        assertTrue(resources > text.indexOf("Und weiter?"));
        String tail = text.substring(resources);
        assertEquals(tail.indexOf("notiz.txt"), tail.lastIndexOf("notiz.txt"));
        assertTrue(tail.contains("IAR Überblick"));
        assertTrue(tail.contains("IAR Quelle"));
    }

    @Test
    public void umlautsAndSpecialCharactersSurvive() throws Exception {
        XWPFDocument document = write();
        assertTrue(text(document).contains("Was ist IAR? Ümlaut „ok“ ✓ & <tag> ß"));
    }

    @Test
    public void secretsAreNotInTheUnpackedDocx() throws Exception {
        Path target = folder.getRoot().toPath().resolve("chat.docx");
        assertTrue(export.write("chat-1", target, ChatExportFormat.DOCX));
        StringBuilder xml = new StringBuilder();
        ZipInputStream zip = new ZipInputStream(Files.newInputStream(target));
        try {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                xml.append(new String(readAll(zip), StandardCharsets.UTF_8));
            }
        } finally {
            zip.close();
        }
        assertTrue(xml.indexOf("Testbegriff") >= 0);
        for (String secret : Arrays.asList("abcdefgh12345678xyz", "sk-abcdefghijklmnopqrstuv", "geheim-token")) {
            assertFalse("secret in docx: " + secret, xml.indexOf(secret) >= 0);
        }
        assertTrue(xml.indexOf(ChatExportRedaction.REDACTED) >= 0);
        // geschwärzt wird nur die Datei, nie der gespeicherte Chat
        assertTrue(store.load("chat-1").getMessages().get(1).getText().contains("abcdefgh12345678xyz"));
    }

    @Test
    public void jsonAndMarkdownExportsStayAsTheyWere() throws Exception {
        Path json = folder.getRoot().toPath().resolve("chat.json");
        Path markdown = folder.getRoot().toPath().resolve("chat.md");
        assertTrue(export.write("chat-1", json, ChatExportFormat.JSON));
        assertTrue(export.write("chat-1", markdown, ChatExportFormat.MARKDOWN));
        assertEquals(export.toJson(export.export("chat-1")),
                new String(Files.readAllBytes(json), StandardCharsets.UTF_8));
        assertEquals(export.toMarkdown(export.export("chat-1")),
                new String(Files.readAllBytes(markdown), StandardCharsets.UTF_8));
        assertFalse(export.write("unbekannt", folder.getRoot().toPath().resolve("x.docx"), ChatExportFormat.DOCX));
        assertEquals(ChatExportFormat.DOCX, ChatExportFormat.ofFileName("Bericht.DOCX"));
        assertEquals(ChatExportFormat.MARKDOWN, ChatExportFormat.ofFileName("a.md"));
    }

    @Test
    public void unknownOrBrokenInputKeepsReadableText() throws Exception {
        ChatRecord record = new ChatRecord("chat-2", T0);
        record.setTitle("");
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT,
                "<div>roh</div>\n\n```mermaid\ngraph TD; A-->B\n```\n\nSteuer\u0001zeichen [ref][x] ![Bild](a.png)",
                T0, null));
        record.setModifiedAt(T0);
        store.save(record);
        Path target = folder.getRoot().toPath().resolve("chat2.docx");
        assertTrue(export.write("chat-2", target, ChatExportFormat.DOCX));
        XWPFDocument document = new XWPFDocument(Files.newInputStream(target));
        String text = text(document);
        assertTrue(text, text.contains("roh"));
        assertTrue(text.contains("graph TD; A-->B"));
        assertTrue(text.contains("Steuerzeichen"));
        assertTrue(text.contains("[ref][x]"));
        assertTrue(text.contains("[Bild: Bild]"));
        assertEquals("Chat", document.getParagraphs().get(0).getText());
    }

    // ------------------------------------------------------------------ helpers

    private XWPFDocument write() throws Exception {
        Path target = folder.getRoot().toPath().resolve("chat.docx");
        assertTrue(export.write("chat-1", target, ChatExportFormat.DOCX));
        return new XWPFDocument(Files.newInputStream(target));
    }

    private static String text(XWPFDocument document) {
        StringBuilder out = new StringBuilder();
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                out.append(((XWPFParagraph) element).getText()).append('\n');
            } else if (element instanceof XWPFTable) {
                out.append(((XWPFTable) element).getText()).append('\n');
            }
        }
        return out.toString();
    }

    private static XWPFParagraph paragraph(XWPFDocument document, String containing) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getText().contains(containing)) {
                return paragraph;
            }
        }
        throw new AssertionError("kein Absatz mit " + containing);
    }

    private static XWPFRun run(XWPFParagraph paragraph, String text) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (text.equals(run.text())) {
                return run;
            }
        }
        throw new AssertionError("kein Lauf " + text + " in " + paragraph.getText());
    }

    private static void collectLinks(XWPFDocument document, List<XWPFParagraph> paragraphs, List<String> links) {
        for (XWPFParagraph paragraph : paragraphs) {
            for (XWPFRun run : paragraph.getRuns()) {
                if (run instanceof XWPFHyperlinkRun) {
                    links.add(((XWPFHyperlinkRun) run).getHyperlink(document).getURL());
                }
            }
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
