package com.aresstack.enterpriseai.application.chat;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;
import com.aresstack.enterpriseai.domain.chat.ChatMessage;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChatServiceTest {

    private final FakeChatCompletionPort port = new FakeChatCompletionPort();
    private final ChatService service = new ChatService(port);

    @Test
    public void streamingTurnDeliversDeltasInOrderThenCompletes() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueAnswer("Hal", "lo ", "Welt");

        RecordingTurnListener listener = new RecordingTurnListener();
        ChatTurn turn = service.send(id, "Hi", listener);
        listener.awaitTerminal();

        assertEquals(Arrays.asList("delta:Hal", "delta:lo ", "delta:Welt", "completed"), listener.events());
        assertEquals("Hallo Welt", listener.response().content());
        assertEquals(ChatTurnState.COMPLETED, turn.state());
        assertFalse(service.isBusy(id));
    }

    @Test
    public void historyKeepsTurnOrderAndIsSentWithEveryRequest() throws Exception {
        ChatConversationId id = service.openConversation("Sei knapp.");
        port.enqueueAnswer("A1").enqueueAnswer("A2");

        service.send(id, "F1", new RecordingTurnListener()).conversationId();
        awaitIdle(id);
        RecordingTurnListener second = new RecordingTurnListener();
        service.send(id, "F2", second);
        second.awaitTerminal();

        assertEquals(Arrays.asList(ChatMessage.user("F1"), ChatMessage.assistant("A1"),
                ChatMessage.user("F2"), ChatMessage.assistant("A2")), service.conversation(id).messages());
        assertEquals(Arrays.asList(ChatMessage.system("Sei knapp."), ChatMessage.user("F1"),
                ChatMessage.assistant("A1"), ChatMessage.user("F2")), port.lastRequest().messages());
    }

    @Test
    public void systemPromptChangeAppliesToNextTurnAndStaysFirst() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueAnswer("A1").enqueueAnswer("A2");
        RecordingTurnListener first = new RecordingTurnListener();
        service.send(id, "F1", first);
        first.awaitTerminal();

        service.setSystemPrompt(id, "Neu");
        RecordingTurnListener second = new RecordingTurnListener();
        service.send(id, "F2", second);
        second.awaitTerminal();

        List<ChatMessage> sent = port.lastRequest().messages();
        assertEquals(ChatMessage.system("Neu"), sent.get(0));
        assertEquals(4, sent.size());
    }

    @Test
    public void cancelEndsTurnOnceKeepsUserMessageAndDropsPartialAnswer() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueHanging("Teil");

        RecordingTurnListener listener = new RecordingTurnListener();
        ChatTurn turn = service.send(id, "Frage", listener);
        listener.awaitFirstDelta();
        assertTrue(service.isBusy(id));

        service.cancel(id);
        turn.cancel();
        listener.awaitTerminal();
        Thread.sleep(50L); // dem Fake-Thread Zeit für (ignorierte) späte Callbacks geben

        assertEquals(Arrays.asList("delta:Teil", "cancelled"), listener.events());
        assertEquals(ChatTurnState.CANCELLED, turn.state());
        assertFalse(service.isBusy(id));
        assertEquals(Collections.singletonList(ChatMessage.user("Frage")), service.conversation(id).messages());
    }

    @Test
    public void conversationIsUsableAgainAfterCancel() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueHanging().enqueueAnswer("ok");
        RecordingTurnListener first = new RecordingTurnListener();
        ChatTurn turn = service.send(id, "F1", first);
        turn.cancel();
        first.awaitTerminal();

        RecordingTurnListener second = new RecordingTurnListener();
        service.send(id, "F2", second);
        second.awaitTerminal();

        assertEquals("ok", second.response().content());
        assertEquals(Arrays.asList(ChatMessage.user("F1"), ChatMessage.user("F2"), ChatMessage.assistant("ok")),
                service.conversation(id).messages());
    }

    @Test
    public void failureIsReportedAndOnlyTheUserMessageStays() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueError(new ChatCompletionException(ChatErrorKind.PROVIDER_ERROR, 500, "boom", null));

        RecordingTurnListener listener = new RecordingTurnListener();
        ChatTurn turn = service.send(id, "Frage", listener);
        listener.awaitTerminal();

        assertEquals(Collections.singletonList("failed"), listener.events());
        assertEquals(ChatErrorKind.PROVIDER_ERROR, listener.error().kind());
        assertEquals(500, listener.error().statusCode());
        assertEquals(ChatTurnState.FAILED, turn.state());
        assertFalse(service.isBusy(id));
        assertEquals(Collections.singletonList(ChatMessage.user("Frage")), service.conversation(id).messages());
    }

    @Test
    public void portThrowingSynchronouslyFailsTheTurn() throws Exception {
        ChatService failing = new ChatService(new ChatCompletionPort() {
            @Override
            public ChatResponse complete(ChatRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
                throw new ChatCompletionException(ChatErrorKind.AUTHENTICATION, "kein Token");
            }
        });
        ChatConversationId id = failing.openConversation();
        RecordingTurnListener listener = new RecordingTurnListener();
        ChatTurn turn = failing.send(id, "x", listener);

        assertEquals(Collections.singletonList("failed"), listener.events());
        assertEquals(ChatErrorKind.AUTHENTICATION, listener.error().kind());
        assertEquals(ChatTurnState.FAILED, turn.state());
        assertFalse(failing.isBusy(id));
    }

    @Test
    public void onlyOneTurnPerConversationAtATime() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueHanging();
        ChatTurn running = service.send(id, "F1", new RecordingTurnListener());
        try {
            service.send(id, "F2", new RecordingTurnListener());
            fail("second turn must be rejected while busy");
        } catch (IllegalStateException expected) {
            assertEquals(1, service.conversation(id).messages().size());
        }
        try {
            service.clearHistory(id);
            fail("clear must be rejected while busy");
        } catch (IllegalStateException expected) {
            // erwartet
        }
        running.cancel();
    }

    @Test
    public void conversationsAreIndependentAndMayStreamInParallel() throws Exception {
        ChatConversationId a = service.openConversation("A");
        ChatConversationId b = service.openConversation("B");
        assertNotEquals(a, b);
        assertEquals(Arrays.asList(a, b), service.conversationIds());

        port.enqueueHanging("a-teil").enqueueAnswer("b-fertig");
        RecordingTurnListener la = new RecordingTurnListener();
        RecordingTurnListener lb = new RecordingTurnListener();
        ChatTurn turnA = service.send(a, "frage-a", la);
        la.awaitFirstDelta();
        service.send(b, "frage-b", lb);
        lb.awaitTerminal();

        assertTrue(service.isBusy(a));
        assertEquals(Arrays.asList(ChatMessage.user("frage-b"), ChatMessage.assistant("b-fertig")),
                service.conversation(b).messages());
        assertEquals(Collections.singletonList(ChatMessage.user("frage-a")), service.conversation(a).messages());

        turnA.cancel();
        la.awaitTerminal();
    }

    @Test
    public void closingAConversationCancelsItsRunningTurn() throws Exception {
        ChatConversationId id = service.openConversation();
        port.enqueueHanging();
        RecordingTurnListener listener = new RecordingTurnListener();
        service.send(id, "x", listener);

        service.closeConversation(id);
        listener.awaitTerminal();

        assertEquals(Collections.singletonList("cancelled"), listener.events());
        assertFalse(service.conversationIds().contains(id));
        service.closeConversation(id); // idempotent
    }

    @Test
    public void clearHistoryKeepsSystemPrompt() throws Exception {
        ChatConversationId id = service.openConversation("sys");
        RecordingTurnListener listener = new RecordingTurnListener();
        service.send(id, "x", listener);
        listener.awaitTerminal();

        service.clearHistory(id);
        assertTrue(service.conversation(id).isEmpty());
        assertEquals("sys", service.conversation(id).systemPrompt());
    }

    @Test
    public void turnOptionsOverrideServiceDefaults() throws Exception {
        ChatService configured = new ChatService(port,
                ChatOptions.builder().model("gpt-intern").temperature(0.1).maxTokens(256).build());
        ChatConversationId id = configured.openConversation();
        RecordingTurnListener listener = new RecordingTurnListener();
        configured.send(id, "x", ChatOptions.builder().temperature(0.8).build(), listener);
        listener.awaitTerminal();

        ChatOptions sent = port.lastRequest().options();
        assertEquals("gpt-intern", sent.model());
        assertEquals(Double.valueOf(0.8), sent.temperature());
        assertEquals(Integer.valueOf(256), sent.maxTokens());
    }

    @Test
    public void cancelAfterCompletionChangesNothing() throws Exception {
        ChatConversationId id = service.openConversation();
        RecordingTurnListener listener = new RecordingTurnListener();
        ChatTurn turn = service.send(id, "x", listener);
        listener.awaitTerminal();

        turn.cancel();
        service.cancel(id);

        assertEquals(ChatTurnState.COMPLETED, turn.state());
        assertEquals(2, service.conversation(id).messages().size());
        assertEquals("completed", listener.events().get(listener.events().size() - 1));
    }

    @Test
    public void lateCallbacksOfAPortAreIgnoredAfterCancel() {
        CapturingPort capturing = new CapturingPort();
        ChatService manual = new ChatService(capturing);
        ChatConversationId id = manual.openConversation();
        RecordingTurnListener listener = new RecordingTurnListener();
        ChatTurn turn = manual.send(id, "x", listener);

        turn.cancel();
        capturing.listener.onDelta("spät");
        capturing.listener.onComplete(new ChatResponse(ChatMessage.assistant("spät"), null, null, null));
        capturing.listener.onCancelled();

        assertTrue(capturing.cancelled);
        assertEquals(Collections.singletonList("cancelled"), listener.events());
        assertEquals(Collections.singletonList(ChatMessage.user("x")), manual.conversation(id).messages());
    }

    @Test
    public void emptyDeltasAreNotForwarded() {
        CapturingPort capturing = new CapturingPort();
        ChatService manual = new ChatService(capturing);
        ChatConversationId id = manual.openConversation();
        RecordingTurnListener listener = new RecordingTurnListener();
        manual.send(id, "x", listener);

        capturing.listener.onStart();
        capturing.listener.onDelta("");
        capturing.listener.onDelta(null);
        capturing.listener.onDelta("a");
        capturing.listener.onComplete(new ChatResponse(ChatMessage.assistant("a"), null, null, null));

        assertEquals(Arrays.asList("delta:a", "completed"), listener.events());
    }

    @Test
    public void rejectsBlankTextAndUnknownConversation() {
        ChatConversationId id = service.openConversation();
        try {
            service.send(id, "  ", new RecordingTurnListener());
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(service.conversation(id).isEmpty());
        }
        try {
            service.send(new ChatConversationId("unbekannt"), "x", new RecordingTurnListener());
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(port.requests().isEmpty());
        }
    }

    @Test
    public void conversationSnapshotIsStable() throws Exception {
        ChatConversationId id = service.openConversation();
        com.aresstack.enterpriseai.domain.chat.ChatConversation before = service.conversation(id);
        RecordingTurnListener listener = new RecordingTurnListener();
        service.send(id, "x", listener);
        listener.awaitTerminal();
        assertTrue(before.isEmpty());
        assertSame(id, before.id());
    }

    private void awaitIdle(ChatConversationId id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000L;
        while (service.isBusy(id)) {
            if (System.currentTimeMillis() > deadline) {
                fail("conversation stayed busy");
            }
            Thread.sleep(5L);
        }
    }

    /** Port, der den Listener für manuelle Callbacks festhält. */
    private static final class CapturingPort implements ChatCompletionPort {
        ChatStreamListener listener;
        boolean cancelled;

        @Override
        public ChatResponse complete(ChatRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChatTask stream(ChatRequest request, ChatStreamListener streamListener) {
            this.listener = streamListener;
            return new ChatTask() {
                @Override
                public void cancel() {
                    cancelled = true;
                }

                @Override
                public boolean isDone() {
                    return cancelled;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled;
                }
            };
        }
    }
}
