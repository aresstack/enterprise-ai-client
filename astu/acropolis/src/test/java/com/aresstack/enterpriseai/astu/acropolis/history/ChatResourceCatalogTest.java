package com.aresstack.enterpriseai.astu.acropolis.history;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Die Ressourcen eines Chats als Projektion seines Datensatzes (Akzeptanztests 5 bis 8): alle Dateien, Seiten und
 * verwendeten Quellen, je stabiler Kennung einmal; ohne Kennung wird nichts erfunden.
 */
public class ChatResourceCatalogTest {

    @Test
    public void listsEveryDistinctFilePageAndUsedSourceOnce() {
        ChatRecord record = new ChatRecord("c1", 1L);
        record.getMessages().add(user("Erste Frage", Arrays.asList(new AttachmentRecord("att-1", "IAR-Konzept.pdf", 10),
                new AttachmentRecord("att-2", "Schnittstelle.xlsx", 20)))
                .withBookmarks(Collections.singletonList(new BookmarkRecord("wiki://intern/NIZZA", "NIZZA Fachkonzept"))));
        record.getMessages().add(answer(source(1, "NIZZA Schnittstellenvertrag", "wiki:NIZZA-V"),
                source(2, "IARRESTEIN", "wiki:IARRESTEIN")));
        record.getMessages().add(user("Zweite Frage", Collections.singletonList(
                new AttachmentRecord("att-1", "IAR-Konzept.pdf", 10)))
                .withBookmarks(Collections.singletonList(new BookmarkRecord("wiki://intern/NIZZA", "anderer Titel"))));
        record.getMessages().add(answer(source(1, "Umbenannt, gleiche Ressource", "wiki:NIZZA-V"),
                source(2, "ZAUF Rückmeldeschlüssel", "wiki:ZAUF")));

        ChatResourceCatalog catalog = ChatResourceCatalog.of(record);

        assertEquals(Arrays.asList("att-1", "att-2"), Arrays.asList(catalog.attachments().get(0).getId(),
                catalog.attachments().get(1).getId()));
        assertEquals(1, catalog.pages().size());
        assertEquals("NIZZA Fachkonzept", catalog.pages().get(0).getTitle());
        assertEquals(3, catalog.sources().size());
        assertEquals("wiki:NIZZA-V", catalog.sources().get(0).getResourceId());
        assertEquals("erstes Auftreten zählt", "NIZZA Schnittstellenvertrag", catalog.sources().get(0).getTitle());
        assertEquals("wiki:IARRESTEIN", catalog.sources().get(1).getResourceId());
        assertEquals("wiki:ZAUF", catalog.sources().get(2).getResourceId());
        assertEquals(6, catalog.size());
    }

    @Test
    public void inventsNoIdentityFromTitleOrLocation() {
        ChatRecord record = new ChatRecord("c2", 1L);
        record.getMessages().add(user("Frage", Collections.singletonList(new AttachmentRecord(null, "noch-nicht-abgelegt.pdf", 0))));
        record.getMessages().add(answer(new SourceRecord(1, "Alte Quelle", "", "https://wiki.example.test/IAR", "",
                0.5, 1, 0)));
        assertEquals(0, ChatResourceCatalog.of(record).size());
        assertEquals(0, ChatResourceCatalog.of(null).size());
    }

    @Test
    public void referencesAreOptionalOnAMessage() {
        ChatMessageRecord message = user("Bezug", null);
        assertTrue(message.getReferences().isEmpty());
        message.withReferences(Collections.singletonList(
                new ResourceReferenceRecord(ResourceReferenceRecord.KIND_SOURCE, "wiki:IAR", "IAR")));
        assertEquals(1, message.getReferences().size());
        assertTrue(message.getReferences().get(0).isValid());
        assertTrue(!new ResourceReferenceRecord("unbekannt", "x", "y").isValid());
        assertTrue(!new ResourceReferenceRecord(ResourceReferenceRecord.KIND_PAGE, " ", "y").isValid());
    }

    private static ChatMessageRecord user(String text, java.util.List<AttachmentRecord> attachments) {
        return new ChatMessageRecord(ChatMessageRecord.ROLE_USER, text, 1L, attachments);
    }

    private static ChatMessageRecord answer(SourceRecord... sources) {
        return new ChatMessageRecord(ChatMessageRecord.ROLE_ASSISTANT, "Antwort", 2L, null)
                .withTrace(Arrays.asList(sources), null);
    }

    private static SourceRecord source(int number, String title, String resourceId) {
        return new SourceRecord(number, title, "", resourceId, "", 0.5, 1, 1, "wiki", resourceId,
                resourceId + "#chunk-0");
    }
}
