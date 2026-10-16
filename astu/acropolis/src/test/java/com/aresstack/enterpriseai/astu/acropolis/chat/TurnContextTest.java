package com.aresstack.enterpriseai.astu.acropolis.chat;

import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.InformationNeed;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TurnContextTest {

    @Test
    public void composesDynamicDataThenSystemPromptThenClientRules() {
        TurnContext context = TurnContext.empty()
                .withRule("RULE")
                .with(TurnContext.Section.QUOTES, "QUOTE")
                .with(TurnContext.Section.KNOWLEDGE, "RAG")
                .with(TurnContext.Section.ATTACHMENTS, "FILES")
                .with(TurnContext.Section.BOOKMARKS, "PAGES")
                .with(TurnContext.Section.REFERENCES, "CHATS")
                .with(TurnContext.Section.TURN_READING, "PLAN")
                .with(TurnContext.Section.PROJECT, "PROJECT");

        assertEquals("PROJECT\n\nPLAN\n\nRAG\n\nFILES\n\nPAGES\n\nCHATS\n\nQUOTE\n\nEDITABLE\n\n"
                + TurnContext.RULES_HEADER + "\nRULE", context.compose("EDITABLE"));
    }

    @Test
    public void orderDoesNotDependOnInsertionOrder() {
        TurnContext a = TurnContext.empty().with(TurnContext.Section.KNOWLEDGE, "K")
                .with(TurnContext.Section.TURN_READING, "P");
        TurnContext b = TurnContext.empty().with(TurnContext.Section.TURN_READING, "P")
                .with(TurnContext.Section.KNOWLEDGE, "K");
        assertEquals(a.compose("S"), b.compose("S"));
    }

    @Test
    public void blankPartsAndMissingPromptAreLeftOut() {
        TurnContext context = TurnContext.empty().with(TurnContext.Section.KNOWLEDGE, "  ").withRule(null);
        assertTrue(context.isEmpty());
        assertNull(context.compose(null));
        assertEquals("S", context.compose("S"));
        assertEquals("K", TurnContext.of("K").compose(" "));
    }

    @Test
    public void routingAddsARuleOnlyForAKnownPlan() {
        TurnContext context = TurnContext.empty().withPlanRouting(plan -> "ROUTE " + plan.informationNeed());
        AnswerPlan known = AnswerPlan.builder("Wie lege ich eine Quelle an?", MessageOrigin.TYPED).known(true)
                .informationNeed(InformationNeed.APPLICATION_HELP).build();
        assertEquals(1, context.routed(known).rules().size());
        assertEquals("ROUTE APPLICATION_HELP", context.routed(known).rules().get(0));
        assertSame(context, context.routed(AnswerPlan.unknown("x", MessageOrigin.TYPED)));
        assertSame(context, context.routed(null));
    }

    @Test
    public void chatServicePutsTurnContextBeforeTheEditablePromptAndKeepsTheUserMessage() {
        FakeChatCompletionPort port = new FakeChatCompletionPort();
        ChatService chat = new ChatService(port);
        ChatConversationId id = chat.openConversation("Sei knapp.");
        chat.sendInContext(id, "Was ist IAR?", TurnContext.empty()
                .with(TurnContext.Section.TURN_READING, "PLAN").withRule("RULE"), null, new RecordingTurnListener(), null);

        List<ChatMessage> sent = port.lastRequest().messages();
        assertEquals(ChatMessage.system("PLAN\n\nSei knapp.\n\n" + TurnContext.RULES_HEADER + "\nRULE"),
                sent.get(0));
        assertEquals(ChatMessage.user("Was ist IAR?"), sent.get(sent.size() - 1));
        // The answer streams on its own thread and may already follow the user message in the history.
        assertTrue(chat.conversation(id).messages().contains(ChatMessage.user("Was ist IAR?")));
    }
}
