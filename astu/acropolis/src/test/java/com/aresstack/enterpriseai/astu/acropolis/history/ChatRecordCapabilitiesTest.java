package com.aresstack.enterpriseai.astu.acropolis.history;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #141: composer switches of a chat; older chats without them get browser off and knowledge on. */
public class ChatRecordCapabilitiesTest {

    @Test
    public void defaultsAreBrowserOffAndKnowledgeOn() {
        ChatRecord record = new ChatRecord("chat-1", 1L);
        assertFalse(record.isBrowserAllowed());
        assertTrue(record.isKnowledgeAllowed());
    }

    @Test
    public void savedSwitchesAreKept() {
        ChatRecord record = new ChatRecord("chat-1", 1L);
        record.setBrowserAllowed(true);
        record.setKnowledgeAllowed(false);
        assertTrue(record.isBrowserAllowed());
        assertFalse(record.isKnowledgeAllowed());
    }
}
