package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #141: a ready browser opens the tool path per turn, without {@code chat.tools.enabled}. */
public class ToolSupportToolPathTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void toolPathFollowsTheConditionOnEveryTurn() throws Exception {
        AtomicBoolean browserReady = new AtomicBoolean(false);
        ToolSupport tools = new ToolSupport(new FakeChatCompletionPort(),
                new FileAttachmentStore(folder.getRoot().toPath()), (name, content) -> "",
                Collections.emptyList(), false)
                .withToolPathWhen(browserReady::get)
                .withMaxRounds(() -> 3);

        assertFalse(tools.alwaysOn());
        browserReady.set(true);
        assertTrue(tools.alwaysOn());
        browserReady.set(false);
        assertFalse(tools.alwaysOn());
    }

    @Test
    public void configuredSwitchStaysOn() throws Exception {
        ToolSupport tools = new ToolSupport(new FakeChatCompletionPort(),
                new FileAttachmentStore(folder.getRoot().toPath()), (name, content) -> "",
                Collections.emptyList(), true)
                .withToolPathWhen(() -> false);

        assertTrue(tools.alwaysOn());
    }
}
