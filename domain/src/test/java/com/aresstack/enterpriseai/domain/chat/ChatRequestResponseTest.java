package com.aresstack.enterpriseai.domain.chat;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatRequestResponseTest {

    @Test
    public void requestFromConversationCarriesSystemPromptAndHistory() {
        ChatConversation conversation = ChatConversation.empty(new ChatConversationId("c"))
                .withSystemPrompt("sys").append(ChatMessage.user("hallo"));
        ChatRequest request = ChatRequest.of(conversation, null);
        assertEquals(2, request.messages().size());
        assertEquals(ChatRole.SYSTEM, request.messages().get(0).role());
        assertTrue(request.options().stop().isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyRequestIsRejected() {
        new ChatRequest(Collections.<ChatMessage>emptyList(), null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void responseMustCarryAnAssistantMessage() {
        new ChatResponse(ChatMessage.user("x"), ChatFinishReason.STOP, null, null);
    }

    @Test
    public void responseDefaultsUnknownFinishReasonAndUsage() {
        ChatResponse response = new ChatResponse(ChatMessage.assistant("x"), null, null, null);
        assertEquals(ChatFinishReason.UNKNOWN, response.finishReason());
        assertFalse(response.usage().isReported());
        assertEquals(-1, response.usage().totalTokens());
        assertEquals("x", response.content());
    }

    @Test
    public void usageReportsCounts() {
        ChatUsage usage = ChatUsage.of(3, 4, 7);
        assertTrue(usage.isReported());
        assertEquals(7, usage.totalTokens());
    }
}
