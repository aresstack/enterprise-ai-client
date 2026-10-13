package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Composer resources open in the reader through the existing reading entry, mapped only from their stable ids. */
public class ChatResourceNavigationTest {

    private static final String RESOURCE_ID = "confluence:cf.example/confluence/page/4711";

    @Test
    public void sourceOpensItsResourceIdAsPreview() {
        RecordingReading reading = new RecordingReading();
        ChatResourceNavigation navigation = new ChatResourceNavigation(reading, id -> "confluence:cf");
        ChatResource source = new ChatResource(ChatResource.Kind.SOURCE, RESOURCE_ID, "Seite 4711",
                "https://cf.example/x");

        assertTrue(navigation.canRead(source));
        navigation.read(source);

        assertEquals(1, reading.knowledgeAreaShown);
        assertEquals(1, reading.opened.size());
        ReaderLink link = reading.opened.get(0);
        assertEquals("confluence:cf", link.sourceId());
        assertEquals(RESOURCE_ID, link.resourceId());
        assertEquals("Seite 4711", link.title());
        assertFalse("preview, not pinned", reading.pinned.get(0));
    }

    @Test
    public void pageOpensTheResourceBehindItsBookmark() {
        RecordingReading reading = new RecordingReading();
        ChatResourceNavigation navigation = new ChatResourceNavigation(reading, id -> "confluence:cf");
        String bookmark = KnowledgeBookmarks.of(KnowledgeResourceId.of(RESOURCE_ID)).toString();
        ChatResource page = new ChatResource(ChatResource.Kind.PAGE, bookmark, "Seite 4711", bookmark);

        assertTrue(navigation.canRead(page));
        navigation.read(page);

        assertEquals(RESOURCE_ID, reading.opened.get(0).resourceId());
    }

    @Test
    public void filesAndInvalidIdsOfferNoReaderAction() {
        RecordingReading reading = new RecordingReading();
        ChatResourceNavigation navigation = new ChatResourceNavigation(reading, id -> "confluence:cf");
        ChatResource file = new ChatResource(ChatResource.Kind.FILE, "att-1", "Konzept.pdf", "");
        ChatResource broken = new ChatResource(ChatResource.Kind.SOURCE, "kein gültiger Wert", "Alt", "");

        assertFalse(navigation.canRead(file));
        assertFalse(navigation.canRead(broken));
        navigation.read(file);
        navigation.read(broken);
        assertTrue(reading.opened.isEmpty());
    }

    @Test
    public void unknownSourceOpensNothing() {
        RecordingReading reading = new RecordingReading();
        ChatResourceNavigation navigation = new ChatResourceNavigation(reading, id -> "");
        navigation.read(new ChatResource(ChatResource.Kind.SOURCE, RESOURCE_ID, "Seite 4711", ""));
        assertTrue(reading.opened.isEmpty());
        assertEquals(0, reading.knowledgeAreaShown);
    }

    private static final class RecordingReading implements ChatSourceNavigation.Reading {
        final List<ReaderLink> opened = new ArrayList<ReaderLink>();
        final List<Boolean> pinned = new ArrayList<Boolean>();
        int knowledgeAreaShown;

        @Override
        public void showKnowledgeArea() {
            knowledgeAreaShown++;
        }

        @Override
        public void open(ReaderLink link, boolean pin) {
            opened.add(link);
            pinned.add(pin);
        }

        @Override
        public boolean canFavorite() {
            return false;
        }

        @Override
        public boolean addFavorite(ReaderLink link) {
            return false;
        }

        @Override
        public FavoritesStore.BulkSave addFavorites(String folderName, List<ReaderLink> links) {
            return null;
        }
    }
}
