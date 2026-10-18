package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #141: the chat's browser tools ride along every copy; their readiness is asked per turn. */
public class ToolSupportBrowserToolsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void withoutBrowserThereAreNoBrowserTools() throws Exception {
        ToolSupport tools = new ToolSupport(new FakeChatCompletionPort(),
                new FileAttachmentStore(folder.getRoot().toPath()), (name, content) -> "",
                Collections.emptyList(), false);

        assertFalse(tools.browserReady());
        assertTrue(tools.browserTools("chat-1").isEmpty());
    }

    @Test
    public void readinessIsAskedPerTurnAndSurvivesCopies() throws Exception {
        AtomicBoolean ready = new AtomicBoolean(false);
        ToolSupport tools = new ToolSupport(new FakeChatCompletionPort(),
                new FileAttachmentStore(folder.getRoot().toPath()), (name, content) -> "",
                Collections.emptyList(), false)
                .withBrowserTools(chatId -> Collections.emptyList(), ready::get)
                .withMaxRounds(() -> 3)
                .withChatTools(chatId -> Collections.emptyList());

        assertFalse(tools.browserReady());
        ready.set(true);
        assertTrue(tools.browserReady());
        assertFalse("the browser alone does not open the tool path", tools.alwaysOn());
        assertEquals(0, tools.browserTools("chat-1").size());
    }
}
