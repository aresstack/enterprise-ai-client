package com.aresstack.enterpriseai.astu.acropolis.chat;

import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatRole;
import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Anschlussfragen zu einem Zitat: Nebenanfrage ohne Werkzeuge und Historie, striktes JSON, Ausfall = keine. */
public class QuoteFollowUpsTest {

    private final FakeChatCompletionPort port = new FakeChatCompletionPort();
    private final ChatService chat = new ChatService(port);

    @Test
    public void suggestionsComeFromASideRequestWithPassageAndSurroundingText() {
        ChatConversationId conversation = chat.openConversation("Antworte knapp.");
        port.enqueueAnswer("{\"suggestions\": [\"Was bedeutet hier \\u201EVorsystem\\u201C?\", "
                + "\"Welche Rolle spielt NIZZA im Ablauf?\", \"was bedeutet hier Vorsystem\", "
                + "\"Wie hängt NIZZA mit IAR zusammen?\"]}");
        QuoteContext quote = QuoteContext.around("Kurz gesagt: NIZZA ist das Vorsystem für die Übermittlung an ZAUF.",
                "Vorsystem", -1, -1, QuoteContext.DEFAULT_SURROUNDING_CHARS);

        List<QuoteFollowUpSuggestion> suggestions = new QuoteFollowUps().suggest(chat, quote, null);

        assertEquals("Duplikat (Schreibweise, Satzzeichen) zählt einmal", Arrays.asList(
                new QuoteFollowUpSuggestion("Was bedeutet hier „Vorsystem“?"),
                new QuoteFollowUpSuggestion("Welche Rolle spielt NIZZA im Ablauf?"),
                new QuoteFollowUpSuggestion("Wie hängt NIZZA mit IAR zusammen?")), suggestions);
        List<ChatMessage> sent = port.lastRequest().messages();
        assertEquals(ChatRole.SYSTEM, sent.get(0).role());
        assertEquals(QuoteFollowUps.INSTRUCTION, sent.get(0).content());
        assertEquals(ChatRole.USER, sent.get(1).role());
        assertTrue(sent.get(1).content().contains("„Vorsystem“"));
        assertTrue("Umfeld der Antwort für kurze Auswahlen", sent.get(1).content().contains("NIZZA ist das Vorsystem"));
        assertTrue("keine Werkzeuge", port.lastRequest().tools().isEmpty());
        assertTrue("nicht in der Historie", chat.conversation(conversation).messages().isEmpty());
    }

    @Test
    public void unreadableAnswerOrFailureGivesNoSuggestions() {
        QuoteContext quote = new QuoteContext("ZAUF", "");
        port.enqueueAnswer("Gern! Hier sind ein paar Fragen: Was ist ZAUF?");
        assertTrue(new QuoteFollowUps().suggest(chat, quote, null).isEmpty());
        port.enqueueError(new ChatCompletionException(ChatErrorKind.PROTOCOL, "kaputt"));
        assertTrue(new QuoteFollowUps().suggest(chat, quote, null).isEmpty());
    }

    @Test
    public void atMostTheConfiguredNumberOfShortSuggestions() {
        QuoteFollowUps followUps = new QuoteFollowUps(2, 6);
        List<QuoteFollowUpSuggestion> parsed = followUps.parse("```json\n{\"suggestions\": [\"Eins?\", "
                + "\"Das ist eine viel zu lange Frage mit zu vielen Wörtern?\", \"Zwei?\", \"Drei?\"]}\n```");
        assertEquals(Arrays.asList(new QuoteFollowUpSuggestion("Eins?"), new QuoteFollowUpSuggestion("Zwei?")),
                parsed);
    }

    @Test
    public void surroundingTextIsAWindowAroundTheKnownPosition() {
        String answer = "0123456789Vorsystem0123456789";
        QuoteContext context = QuoteContext.around(answer, "Vorsystem", 10, 19, 3);
        assertEquals("… 789Vorsystem012 …", context.surroundingText());
        assertEquals("Vorsystem", context.selectedText());
    }
}
