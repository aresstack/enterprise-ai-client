package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

/** „Zum aktuellen Chat hinzufügen“ legt nur Titel und Bookmark ab, nie den Seitentext. */
public class ReaderChatPagesTest {

    @Test
    public void pageCarriesOnlyTitleAndBookmark() {
        ReaderLink link = new ReaderLink("confluence:https://cf.intern/confluence",
                "confluence:cf.intern/confluence/page/17025995", "Betriebshandbuch", "Seitentext im Ausschnitt", "");
        ChatPage page = ReaderChatPages.pageOf(link);
        assertEquals("Betriebshandbuch", page.title());
        assertEquals("confluence://cf.intern/confluence/page/17025995", page.bookmark());
        assertFalse(page.toString().contains("Seitentext"));
    }

    @Test
    public void sourceOverviewHasNoBookmark() {
        assertNull(ReaderChatPages.pageOf(ReaderLink.source("confluence:https://cf.intern/confluence", "Quelle")));
        assertFalse(ReaderChatPages.canAdd(null));
    }
}
