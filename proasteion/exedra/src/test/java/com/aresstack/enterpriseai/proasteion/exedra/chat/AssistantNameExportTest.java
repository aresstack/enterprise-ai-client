package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatExportFormat;
import com.google.gson.JsonObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Der Anzeigename des Assistenten in JSON, Markdown und Word; die Rolle der Nachricht bleibt ASSISTANT. */
public class AssistantNameExportTest {

    private static final long T0 = 1760000000000L;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private FileChatHistoryStore store;
    private final AtomicReference<String> name = new AtomicReference<String>("Merlins Tochter");

    @Before
    public void setUp() throws Exception {
        store = new FileChatHistoryStore(folder.newFolder("chats").toPath());
        ChatRecord record = new ChatRecord("chat-1", T0);
        record.setTitle("Namen");
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER, "Wie heißt du?", T0, null));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, "Hallo!", T0 + 1000L,
                null));
        record.setModifiedAt(T0 + 1000L);
        store.save(record);
    }

    private ChatExport export() {
        return new ChatExport(store, null, () -> T0 + 5000L, "9.9.9", name::get);
    }

    @Test
    public void jsonCarriesTheNameAndKeepsTheRole() {
        JsonObject tree = export().export("chat-1");
        assertEquals("Merlins Tochter", tree.get(ChatExport.ASSISTANT_NAME_FIELD).getAsString());
        JsonObject answer = tree.getAsJsonObject("chat").getAsJsonArray("messages").get(1).getAsJsonObject();
        assertEquals("ASSISTANT", answer.get("author").getAsString());
    }

    @Test
    public void markdownHeadsAnswersWithTheCurrentName() {
        ChatExport export = export();
        String markdown = export.toMarkdown(export.export("chat-1"));
        assertTrue(markdown, markdown.contains("## 2. Merlins Tochter · "));
        assertFalse(markdown.contains("Assistent"));

        name.set("Morgana"); // je Export frisch aus den Einstellungen
        markdown = export.toMarkdown(export.export("chat-1"));
        assertTrue(markdown, markdown.contains("## 2. Morgana · "));
    }

    @Test
    public void wordHeadsAnswersWithTheName() throws Exception {
        Path target = folder.getRoot().toPath().resolve("chat.docx");
        assertTrue(export().write("chat-1", target, ChatExportFormat.DOCX));
        boolean found = false;
        try (InputStream in = Files.newInputStream(target); XWPFDocument document = new XWPFDocument(in)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (paragraph.getText().startsWith("Merlins Tochter · ")) {
                    found = true;
                    assertEquals(ChatDocxWriter.ASSISTANT_COLOR, paragraph.getRuns().get(0).getColor());
                }
                assertFalse(paragraph.getText(), paragraph.getText().startsWith("Assistent"));
            }
        }
        assertTrue(found);
    }

    @Test
    public void withoutANameOlderCallersKeepAssistent() {
        ChatExport export = new ChatExport(store, null, () -> T0, "9.9.9");
        String markdown = export.toMarkdown(export.export("chat-1"));
        assertTrue(markdown, markdown.contains("## 2. Assistent · "));
        JsonObject legacy = export.export("chat-1");
        legacy.remove(ChatExport.ASSISTANT_NAME_FIELD);
        assertEquals(ChatExport.DEFAULT_ASSISTANT_TITLE, ChatExport.assistantTitle(legacy));
    }
}
