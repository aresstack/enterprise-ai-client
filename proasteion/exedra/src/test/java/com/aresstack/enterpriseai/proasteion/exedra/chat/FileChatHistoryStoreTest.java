package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ResourceReferenceRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.SourceRecord;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Die neuen Felder (Kennungen der Quellen, Verweise) sind optional: ältere Chatdateien bleiben lesbar. */
public class FileChatHistoryStoreTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void olderChatFileWithoutReferencesAndIdsStaysReadable() throws Exception {
        Path root = folder.newFolder("chats").toPath();
        Files.write(root.resolve("alt.json"), ("{\"id\":\"alt\",\"createdAt\":1,\"title\":\"Was ist IAR?\","
                + "\"messages\":[{\"role\":\"user\",\"text\":\"Was ist IAR?\",\"createdAt\":1},"
                + "{\"role\":\"assistant\",\"text\":\"Ein Verfahren.\",\"createdAt\":2,"
                + "\"sources\":[{\"number\":1,\"title\":\"IAR\",\"location\":\"wiki:IAR\"}]}]}")
                .getBytes(StandardCharsets.UTF_8));
        ChatRecord record = new FileChatHistoryStore(root).load("alt");
        assertNotNull(record);
        assertTrue(record.getMessages().get(0).getReferences().isEmpty());
        SourceRecord source = record.getMessages().get(1).getSources().get(0);
        assertEquals("IAR", source.getTitle());
        assertEquals("", source.getResourceId());
        assertEquals(1, ChatHistoryBinding.referencesOf(record.getMessages().get(1).getSources()).size());
    }

    @Test
    public void referencesAndSourceIdsRoundTrip() throws Exception {
        Path root = folder.newFolder("chats").toPath();
        FileChatHistoryStore store = new FileChatHistoryStore(root);
        ChatRecord record = new ChatRecord("neu", 1L);
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_USER, "Frage", 1L, null)
                .withReferences(Collections.singletonList(new ResourceReferenceRecord(
                        ResourceReferenceRecord.KIND_ATTACHMENT, "att-1", "IAR-Konzept.pdf"))));
        record.getMessages().add(new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, "Antwort", 2L, null)
                .withTrace(Collections.singletonList(new SourceRecord(1, "NIZZA", "", "wiki:NIZZA", "", 0.5, 1, 2,
                        "wiki", "wiki:NIZZA", "wiki:NIZZA#chunk-0")), null));
        record.setTitle("Frage");
        store.save(record);
        String json = new String(Files.readAllBytes(root.resolve("neu.json")), StandardCharsets.UTF_8);
        assertFalse("Antworten ohne Verweise ohne Feld", json.indexOf("references") != json.lastIndexOf("references"));

        ChatRecord back = store.load("neu");
        assertEquals("att-1", back.getMessages().get(0).getReferences().get(0).getId());
        assertEquals("attachment", back.getMessages().get(0).getReferences().get(0).getKind());
        SourceRecord source = back.getMessages().get(1).getSources().get(0);
        assertEquals("wiki", source.getSourceId());
        assertEquals("wiki:NIZZA", source.getResourceId());
        assertEquals("wiki:NIZZA#chunk-0", source.getChunkId());
    }
}
