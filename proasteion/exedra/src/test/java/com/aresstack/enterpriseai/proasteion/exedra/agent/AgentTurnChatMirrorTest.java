package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Chat und Agent teilen eine Unterhaltung: ein abgeschlossener Agenten-Turn steht danach auch im Verlauf, mit dem
 * der Chat-Modus weiterfragt; abgebrochene und fehlgeschlagene Turns und Chat-Turns selbst werden nicht gespiegelt.
 */
public class AgentTurnChatMirrorTest {

    private final ChatService chatService = new ChatService(new FakeChatCompletionPort());
    private final ChatConversationId conversation = chatService.openConversation("System");
    private final ShellModeModel modes = new ShellModeModel(true);
    private final ChatShellModel model = new ChatShellModel(System::currentTimeMillis);

    @Test
    public void completedAgentTurnBecomesPartOfTheChatConversation() {
        new AgentTurnChatMirror(chatService, () -> conversation, modes, model);
        modes.select(ShellMode.AGENT);

        model.addUserMessage("Was ist IAR?");
        model.beginAssistantMessage();
        model.appendAssistantDelta("IAR bündelt Anträge.");
        model.completeAssistantMessage();

        List<ChatMessage> history = chatService.conversation(conversation).messages();
        assertEquals(2, history.size());
        assertEquals(ChatRole.USER, history.get(0).role());
        assertEquals("Was ist IAR?", history.get(0).content());
        assertEquals(ChatRole.ASSISTANT, history.get(1).role());
        assertEquals("IAR bündelt Anträge.", history.get(1).content());
        assertEquals("System", chatService.conversation(conversation).systemPrompt());
    }

    @Test
    public void cancelledFailedAndChatTurnsAreNotMirrored() {
        new AgentTurnChatMirror(chatService, () -> conversation, modes, model);
        modes.select(ShellMode.AGENT);
        model.addUserMessage("abgebrochen");
        model.beginAssistantMessage();
        model.appendAssistantDelta("halb");
        model.cancelAssistantMessage();
        model.addUserMessage("fehlgeschlagen");
        model.beginAssistantMessage();
        model.failAssistantMessage("Der Agent hat den Auftrag nicht abgeschlossen.");

        // Chat-Modus: die Antwort legt der ChatService selbst ab, der Spiegel bleibt draußen
        modes.select(ShellMode.CHAT);
        model.addUserMessage("Chatfrage");
        model.beginAssistantMessage();
        model.appendAssistantDelta("Chatantwort");
        model.completeAssistantMessage();

        assertTrue(chatService.conversation(conversation).messages().isEmpty());
    }
}
