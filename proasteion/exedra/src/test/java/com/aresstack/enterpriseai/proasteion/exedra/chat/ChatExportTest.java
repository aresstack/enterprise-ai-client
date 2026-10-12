package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.history.AttachmentRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.BookmarkRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.SourceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ToolCallRecord;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatExportFormat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Akzeptanz „Chat exportieren …“: die Exportdatei ist gültiges UTF-8-JSON, ihre Nachrichten stimmen in Anzahl,
 * Reihenfolge und Inhalt mit dem gespeicherten Chat überein, ein Agent-Chat bringt Phase und Quellen seines
 * Research-Projekts mit, der Chat bleibt unverändert und Zugangsdaten stehen nicht in der Datei.
 */
public class ChatExportTest {

    private static final long T0 = 1760000000000L;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void exportOfAPlainChatMatchesTheStoredChat() throws Exception {
        Path chats = folder.newFolder("chats").toPath();
        FileChatHistoryStore store = new FileChatHistoryStore(chats);
        ChatRecord record = new ChatRecord("chat-1", T0);
        record.setTitle("Was ist IAR?");
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER, "Was ist IAR? Ümlaut „ok“ ✓",
                T0, Collections.singletonList(new AttachmentRecord("att-1", "notiz.txt", 12L)))
                .withBookmarks(Collections.singletonList(new BookmarkRecord("bookmark://wiki/IAR", "IAR"))));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, "**IAR** ist ein Testbegriff.",
                T0 + 1000L, null).withTrace(
                Collections.singletonList(new SourceRecord(1, "IAR", "Überblick", "wiki:IAR", "Version 3", 0.8, 1, 2)),
                Collections.singletonList(new ToolCallRecord("knowledge_search",
                        "{\"query\":\"IAR\",\"token\":\"geheim-token\"}", true, "id: wiki:IAR", T0 + 900L))));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_INFO, "Wissenssuche ausgefallen.",
                T0 + 2000L, null));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, "Teilant",
                T0 + 3000L, null).markCancelled());
        record.setModifiedAt(T0 + 3000L);
        store.save(record);
        byte[] before = Files.readAllBytes(chats.resolve("chat-1.json"));

        ChatExport export = new ChatExport(store, null, () -> T0 + 9000L, "9.9.9");
        Path target = folder.getRoot().toPath().resolve("export.json");
        assertTrue(export.write("chat-1", target, ChatExportFormat.JSON));

        JsonObject json = parseUtf8(target);
        assertEquals(ChatExport.FORMAT, json.get("format").getAsString());
        assertEquals(1, json.get("version").getAsInt());
        assertTrue(json.get("agent").isJsonNull());
        JsonObject chat = json.getAsJsonObject("chat");
        assertEquals("chat-1", chat.get("id").getAsString());
        assertEquals("Was ist IAR?", chat.get("title").getAsString());
        assertEquals(T0, chat.get("createdAt").getAsLong());
        assertEquals(T0 + 3000L, chat.get("modifiedAt").getAsLong());

        ChatRecord stored = store.load("chat-1");
        JsonArray messages = chat.getAsJsonArray("messages");
        assertEquals(stored.getMessages().size(), messages.size());
        for (int i = 0; i < messages.size(); i++) {
            JsonObject message = messages.get(i).getAsJsonObject();
            ChatMessageRecord original = stored.getMessages().get(i);
            assertEquals(i + 1, message.get("index").getAsInt());
            assertEquals(original.getRole(), message.get("role").getAsString());
            assertEquals(original.getText(), message.get("text").getAsString());
            assertEquals(original.getCreatedAt(), message.get("createdAt").getAsLong());
            assertEquals(original.getRole() + "@" + original.getCreatedAt(), message.get("id").getAsString());
            assertEquals(original.isCancelled() ? "CANCELLED" : "COMPLETE", message.get("state").getAsString());
            assertEquals(original.getAttachments().size(), message.getAsJsonArray("attachments").size());
            assertEquals(original.getBookmarks().size(), message.getAsJsonArray("bookmarks").size());
            assertTrue(message.has("failureMessage"));
            assertTrue(message.has("sources"));
            assertTrue(message.get("phase").isJsonNull());
        }
        JsonObject traced = messages.get(1).getAsJsonObject();
        JsonObject source = traced.getAsJsonArray("sources").get(0).getAsJsonObject();
        assertEquals("wiki:IAR", source.get("location").getAsString());
        assertEquals(1, source.get("number").getAsInt());
        JsonObject call = traced.getAsJsonArray("toolCalls").get(0).getAsJsonObject();
        assertEquals("knowledge_search", call.get("name").getAsString());
        assertEquals("id: wiki:IAR", call.get("result").getAsString());
        assertTrue(call.get("arguments").getAsString().contains("\"query\":\"IAR\""));
        assertFalse(call.get("arguments").getAsString().contains("geheim-token"));
        assertTrue(messages.get(0).getAsJsonObject().get("toolCalls").isJsonNull());
        JsonObject first = messages.get(0).getAsJsonObject();
        assertEquals("att-1", first.getAsJsonArray("attachments").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("bookmark://wiki/IAR",
                first.getAsJsonArray("bookmarks").get(0).getAsJsonObject().get("uri").getAsString());
        assertEquals("NOTICE", messages.get(2).getAsJsonObject().get("author").getAsString());

        // Export verändert den Chat nicht
        assertArrayEquals(before, Files.readAllBytes(chats.resolve("chat-1.json")));
        assertFalse(Files.exists(folder.getRoot().toPath().resolve("export.json.tmp")));

        Path markdown = folder.getRoot().toPath().resolve("export.md");
        assertTrue(export.write("chat-1", markdown, ChatExportFormat.MARKDOWN));
        String md = new String(Files.readAllBytes(markdown), StandardCharsets.UTF_8);
        assertTrue(md.startsWith("# Was ist IAR?"));
        assertTrue(md.contains("**IAR** ist ein Testbegriff."));
        assertTrue(md.contains("Anhang: notiz.txt"));
        assertTrue(md.contains("Werkzeug: `knowledge_search`"));
        assertTrue(md.contains("Quelle [1]: IAR <wiki:IAR>"));

        assertFalse(export.write("unbekannt", folder.getRoot().toPath().resolve("x.json"), ChatExportFormat.JSON));
    }

    @Test
    public void agentChatCarriesPhaseJournalAndSourcesWithoutSecrets() throws Exception {
        Path chats = folder.newFolder("chats").toPath();
        FileChatHistoryStore store = new FileChatHistoryStore(chats);
        ChatRecord record = new ChatRecord("chat-2", T0);
        record.setTitle("Recherche NIZZA");
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER, "Recherchiere NIZZA",
                T0, null));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT,
                "Aufruf mit Authorization: Bearer abcdefgh12345678xyz und Cookie: JSESSIONID=ABCDEF0123456789; "
                        + "Link https://wiki.example.test/x?otpCode=482913&page=1 und sk-abcdefghijklmnopqrstuv",
                T0 + 1000L, null));
        record.setModifiedAt(T0 + 1000L);
        store.save(record);

        File project = folder.newFolder("research", "chat-2");
        write(new File(project, "research-state"), "SOURCES\nrunning\n\n7\n\n");
        write(new File(project, "phase-journal.json"), "{\"schemaVersion\":1,\"messagePhases\":{\"user@" + T0
                + "\":\"SCOPING\",\"assistant@" + (T0 + 1000L) + "\":\"SOURCES\"},\"phaseOutcomes\":"
                + "{\"SCOPING\":\"Konzept steht.\"}}");
        write(new File(project, "sources.tsv"), "src-1\tNIZZA Übersicht\thttps://wiki.example.test/NIZZA\t"
                + "bookmark://wiki/NIZZA\t3\thash1\n");
        write(new File(project, "parked.tsv"), "https://wiki.example.test/ZAUF\tZAUF\t0.4\tAuszug\n");
        write(new File(project, "document.json"), "{\"title\":\"Bericht\",\"proxyPassword\":\"geheim-proxy\","
                + "\"apiKey\":\"geheim-key\"}");
        write(new File(project, "outline.md"), "# Gliederung\n");
        File images = new File(project, "images");
        assertTrue(images.mkdirs());
        Files.write(new File(images, "bild.png").toPath(), new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 1, 2});

        ChatExport export = new ChatExport(store, chatId -> "chat-2".equals(chatId) ? project : null,
                () -> T0 + 9000L, null);
        Path target = folder.getRoot().toPath().resolve("agent.json");
        assertTrue(export.write("chat-2", target, ChatExportFormat.JSON));
        String text = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
        JsonObject json = parseUtf8(target);

        JsonObject agent = json.getAsJsonObject("agent");
        assertEquals("SOURCES", agent.get("phase").getAsString());
        assertEquals("running", agent.get("state").getAsString());
        JsonArray sources = agent.getAsJsonArray("sources");
        assertEquals(1, sources.size());
        assertEquals("src-1", sources.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("bookmark://wiki/NIZZA", sources.get(0).getAsJsonObject().get("bookmark").getAsString());
        assertEquals(1, agent.getAsJsonArray("parked").size());
        assertEquals("Konzept steht.", agent.getAsJsonObject("phaseJournal").getAsJsonObject("phaseOutcomes")
                .get("SCOPING").getAsString());
        JsonObject files = agent.getAsJsonObject("files");
        assertEquals("Bericht", files.getAsJsonObject("document.json").get("title").getAsString());
        assertEquals("# Gliederung\n", files.get("outline.md").getAsString());
        assertTrue(files.getAsJsonObject("images/bild.png").get("binary").getAsBoolean());

        JsonArray messages = json.getAsJsonObject("chat").getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("SCOPING", messages.get(0).getAsJsonObject().get("phase").getAsString());
        assertEquals("SOURCES", messages.get(1).getAsJsonObject().get("phase").getAsString());
        assertEquals("Recherchiere NIZZA", messages.get(0).getAsJsonObject().get("text").getAsString());

        List<String> secrets = Arrays.asList("abcdefgh12345678xyz", "ABCDEF0123456789", "482913",
                "sk-abcdefghijklmnopqrstuv", "geheim-proxy", "geheim-key");
        for (String secret : secrets) {
            assertFalse("secret in export: " + secret, text.contains(secret));
        }
        assertTrue(text.contains(ChatExportRedaction.REDACTED));
        assertTrue(json.get("redactions").getAsInt() >= secrets.size());
        // der Rest der Nachricht bleibt, wie er gespeichert ist
        assertTrue(messages.get(1).getAsJsonObject().get("text").getAsString().contains("page=1"));
        // geschwärzt wird nur die Datei, nie der gespeicherte Chat
        assertTrue(store.load("chat-2").getMessages().get(1).getText().contains("abcdefgh12345678xyz"));
    }

    @Test
    public void secretFieldNamesAreRecognised() {
        assertTrue(ChatExportRedaction.isSecretKey("proxyPassword"));
        assertTrue(ChatExportRedaction.isSecretKey("api_key"));
        assertTrue(ChatExportRedaction.isSecretKey("otpCode"));
        assertTrue(ChatExportRedaction.isSecretKey("sessionCookie"));
        assertTrue(ChatExportRedaction.isSecretKey("Authorization"));
        assertFalse(ChatExportRedaction.isSecretKey("notPersisted"));
        assertFalse(ChatExportRedaction.isSecretKey("maxTokens"));
        assertFalse(ChatExportRedaction.isSecretKey("title"));
        assertFalse(ChatExportRedaction.isSecretKey("contentHash"));
    }

    private static JsonObject parseUtf8(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)); // wirft bei ungültigem UTF-8
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void write(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
