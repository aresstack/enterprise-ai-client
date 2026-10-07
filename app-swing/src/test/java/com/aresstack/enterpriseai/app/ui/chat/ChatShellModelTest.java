package com.aresstack.enterpriseai.app.ui.chat;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChatShellModelTest {

    private final List<String> events = new ArrayList<String>();
    private long now = 1000L;
    private ChatShellModel model;

    @Before
    public void setUp() {
        model = new ChatShellModel(new LongSupplier() {
            @Override
            public long getAsLong() {
                return now;
            }
        });
        model.addListener(new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
                events.add("added:" + entry.getAuthor() + ":" + entry.getState());
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
                events.add("updated:" + entry.getState() + ":" + entry.getText());
            }

            @Override
            public void stateChanged() {
                events.add("state:" + model.isStreaming());
            }
        });
    }

    @Test
    public void conversationKeepsOrderAndTimestamps() {
        model.addUserMessage("Hallo");
        now = 2000L;
        model.beginAssistantMessage();
        model.appendAssistantDelta("Hi");
        model.completeAssistantMessage();
        now = 3000L;
        model.addUserMessage("Noch eine Frage");

        List<TranscriptEntry> entries = model.getEntries();
        assertEquals(3, entries.size());
        assertEquals(TranscriptEntry.Author.USER, entries.get(0).getAuthor());
        assertEquals(TranscriptEntry.Author.ASSISTANT, entries.get(1).getAuthor());
        assertEquals("Hi", entries.get(1).getText());
        assertEquals("Noch eine Frage", entries.get(2).getText());
        assertEquals(1000L, entries.get(0).getCreatedAtMillis());
        assertEquals(2000L, entries.get(1).getCreatedAtMillis());
        assertTrue(entries.get(0).getId() < entries.get(1).getId());
        assertTrue(entries.get(1).getId() < entries.get(2).getId());
    }

    @Test
    public void streamingLifecycleFiresEventsInOrder() {
        model.addUserMessage("Frage");
        TranscriptEntry answer = model.beginAssistantMessage();
        assertTrue(model.isStreaming());
        assertEquals(TranscriptEntry.State.STREAMING, answer.getState());

        model.appendAssistantDelta("Ant");
        model.appendAssistantDelta(null);
        model.appendAssistantDelta("");
        model.appendAssistantDelta("wort");
        model.completeAssistantMessage();

        assertFalse(model.isStreaming());
        assertEquals("Antwort", answer.getText());
        assertEquals(TranscriptEntry.State.COMPLETE, answer.getState());
        assertEquals(java.util.Arrays.asList(
                "added:USER:COMPLETE",
                "added:ASSISTANT:STREAMING",
                "state:true",
                "updated:STREAMING:Ant",
                "updated:STREAMING:Antwort",
                "updated:COMPLETE:Antwort",
                "state:false"), events);
    }

    @Test
    public void cancelKeepsPartialTextAndAllowsSendingAgain() {
        model.addUserMessage("Frage");
        TranscriptEntry answer = model.beginAssistantMessage();
        model.appendAssistantDelta("Teil");
        assertTrue(model.canStop());
        assertFalse(model.canSend("weiter"));

        model.cancelAssistantMessage();

        assertEquals(TranscriptEntry.State.CANCELLED, answer.getState());
        assertEquals("Teil", answer.getText());
        assertFalse(model.canStop());
        assertTrue(model.canSend("weiter"));
    }

    @Test
    public void failureCarriesAReadableMessage() {
        model.addUserMessage("Frage");
        TranscriptEntry answer = model.beginAssistantMessage();
        model.failAssistantMessage("Server nicht erreichbar");
        assertEquals(TranscriptEntry.State.FAILED, answer.getState());
        assertEquals("Server nicht erreichbar", answer.getFailureMessage());

        TranscriptEntry second = model.beginAssistantMessage();
        model.failAssistantMessage("  ");
        assertEquals("Die Antwort konnte nicht erzeugt werden.", second.getFailureMessage());
    }

    @Test
    public void canSendRejectsBlankDraftsAndRunningAnswers() {
        assertFalse(model.canSend(null));
        assertFalse(model.canSend("   \n"));
        assertTrue(model.canSend(" x "));
        model.beginAssistantMessage();
        assertFalse(model.canSend("x"));
    }

    @Test
    public void invalidTransitionsAreProgrammingErrors() {
        expectIllegalState(new Runnable() {
            @Override
            public void run() {
                model.appendAssistantDelta("x");
            }
        });
        expectIllegalState(new Runnable() {
            @Override
            public void run() {
                model.completeAssistantMessage();
            }
        });
        model.beginAssistantMessage();
        expectIllegalState(new Runnable() {
            @Override
            public void run() {
                model.beginAssistantMessage();
            }
        });
        try {
            model.addUserMessage(" ");
            fail("blank user message accepted");
        } catch (IllegalArgumentException expected) {
            // erwartet
        }
    }

    @Test
    public void ragToggleNotifiesOnlyOnChange() {
        model.setRagEnabled(false);
        assertTrue(events.isEmpty());
        model.setRagEnabled(true);
        model.setRagEnabled(true);
        assertTrue(model.isRagEnabled());
        assertEquals(java.util.Collections.singletonList("state:false"), events);
    }

    @Test
    public void entriesViewIsReadOnlyAndToStringHidesText() {
        TranscriptEntry entry = model.addUserMessage("geheimer Inhalt");
        try {
            model.getEntries().clear();
            fail("entries view is modifiable");
        } catch (UnsupportedOperationException expected) {
            // erwartet
        }
        assertSame(entry, model.getEntries().get(0));
        assertFalse(entry.toString().contains("geheim"));
    }

    private static void expectIllegalState(Runnable action) {
        try {
            action.run();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // erwartet
        }
    }
}
