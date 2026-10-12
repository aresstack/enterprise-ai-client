package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.AgentActivityBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.AnimatedThoughtBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.AssistantThinkingBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JLabel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Agenten-Arbeitsschritte im Verlauf wie in askai arch: Werkzeugaufrufe als gelbe Wolke, Denkschritte als grüne
 * Wolke, beide an der Stelle des Agenten im Verlauf. Nach dem Platzen bleibt vom Werkzeug eine farbige
 * Ergebniszeile (Fehler in Fehlerfarbe), die Denkwolke verschwindet.
 */
public class ChatTranscriptActivityTest {

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();

    @Test
    public void activitiesSitBetweenAnswerSectionsInTurnOrderAndVanishWithTheTurn() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        model.addUserMessage("Was ist IAR?");
        model.beginAssistantMessage();
        model.appendAssistantDelta("Ich suche.");
        TranscriptEntry search = model.startActivity("call-1", "Suche in den Wissensquellen", "search", "IAR");
        model.finishActivity("call-1", true, "1 Treffer: IAR Übersicht");
        TranscriptEntry think = model.startActivity("call-2", "Denke nach", "think", "");
        model.finishActivity("call-2", true, "fertig");
        model.appendAssistantDelta("IAR bündelt die Auswertung.");

        // während des Turns: die Wolken stehen zwischen den Abschnitten der Antwort
        List<TranscriptEntry> entries = model.getEntries();
        assertEquals(5, entries.size());
        assertEquals(TranscriptEntry.Author.USER, entries.get(0).getAuthor());
        assertEquals("Ich suche.", entries.get(1).getText());
        assertEquals(TranscriptEntry.State.COMPLETE, entries.get(1).getState());
        assertEquals(search, entries.get(2));
        assertEquals(think, entries.get(3));
        assertEquals("IAR bündelt die Auswertung.", entries.get(4).getText());
        assertEquals("1 Treffer: IAR Übersicht", search.getActivitySummary());
        assertTrue(think.isThinking());
        assertFalse(search.isThinking());

        // nach dem Turn (askai): erfolgreiche Wolken sind aus dem Modell entfernt, die Antwort ist eine Blase
        final List<TranscriptEntry> removed = new java.util.ArrayList<TranscriptEntry>();
        model.addListener(new ChatShellModelListener() {
            @Override
            public void entryAdded(TranscriptEntry entry) {
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
            }

            @Override
            public void stateChanged() {
            }

            @Override
            public void entryRemoved(TranscriptEntry entry) {
                removed.add(entry);
            }
        });
        model.completeAssistantMessage();
        entries = model.getEntries();
        assertEquals(2, entries.size());
        assertEquals(TranscriptEntry.Author.USER, entries.get(0).getAuthor());
        assertEquals(TranscriptEntry.Author.ASSISTANT, entries.get(1).getAuthor());
        assertEquals("Ich suche.\n\nIAR bündelt die Auswertung.", entries.get(1).getText());
        assertTrue(removed.toString(), removed.contains(search) && removed.contains(think));
        assertFalse(model.isStreaming());
    }

    @Test
    public void aFailedActivityStaysWhenTheTurnFails() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        model.addUserMessage("Was ist IAR?");
        model.beginAssistantMessage();
        TranscriptEntry ok = model.startActivity("call-1", "Suche in den Wissensquellen", "search", "IAR");
        model.finishActivity("call-1", true, "1 Treffer");
        TranscriptEntry broken = model.startActivity("call-2", "Lese Seite", "read", "IAR Übersicht");
        model.finishActivity("call-2", false, "Seite nicht lesbar");
        model.failAssistantMessage("Die Agentensitzung wurde beendet.");

        List<TranscriptEntry> entries = model.getEntries();
        assertFalse("erfolgreiche Wolke entfernt", entries.contains(ok));
        assertTrue("der Fehler bleibt sichtbar", entries.contains(broken));
    }

    @Test
    public void emptyPlaceholderMovesBehindTheCloudAndDisappearsAtTheEnd() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        model.addUserMessage("Was ist IAR?");
        model.beginAssistantMessage();
        TranscriptEntry running = model.startActivity("call-1", "Suche in den Wissensquellen", "search", "IAR");
        List<TranscriptEntry> entries = model.getEntries();
        assertEquals(TranscriptEntry.Author.ACTIVITY, entries.get(1).getAuthor());
        assertEquals(TranscriptEntry.Author.ASSISTANT, entries.get(2).getAuthor());
        model.completeAssistantMessage();
        assertEquals("laufender Schritt endet mit dem Auftrag", TranscriptEntry.State.CANCELLED, running.getState());
        assertEquals("leerer Platzhalter und Wolke entfallen", 1, model.getEntries().size());
    }

    @Test
    public void toolCloudIsYellowThinkingCloudIsGreenAndBothSettle() throws Exception {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final AtomicReference<ChatShellPanel> shell = new AtomicReference<ChatShellPanel>();
        final AtomicReference<TranscriptEntry> search = new AtomicReference<TranscriptEntry>();
        final AtomicReference<TranscriptEntry> read = new AtomicReference<TranscriptEntry>();
        final AtomicReference<TranscriptEntry> think = new AtomicReference<TranscriptEntry>();
        Edt.run(() -> {
            shell.set(new ChatShellPanel(model, new RecordingActions(), comic, bubbles));
            shell.get().setSize(800, 600);
            shell.get().doLayout();
            model.addUserMessage("Kannst du mir was über IAR aus Confluence finden?");
            model.beginAssistantMessage();
            search.set(model.startActivity("call-1", "Suche in den Wissensquellen", "search", "IAR"));
            think.set(model.startActivity("call-2", "Denke nach", "think", ""));
            read.set(model.startActivity("call-3", "Lese Seite", "read", "IAR Übersicht"));
        });
        Edt.run(() -> {
            ChatTranscriptPanel transcript = shell.get().transcript();
            assertTrue(transcript.thoughtFor(search.get().getId()) instanceof AgentActivityBubblePanel);
            assertTrue(transcript.thoughtFor(read.get().getId()) instanceof AgentActivityBubblePanel);
            assertTrue(transcript.thoughtFor(think.get().getId()) instanceof AssistantThinkingBubblePanel);
            model.finishActivity("call-1", true, "1 Treffer: IAR Übersicht");
            model.finishActivity("call-2", true, "fertig");
            model.finishActivity("call-3", false, "Seite nicht lesbar");
        });

        JLabel searchLine = awaitLine(shell.get(), search.get());
        assertEquals("Suche in den Wissensquellen „IAR“: 1 Treffer: IAR Übersicht", searchLine.getText());
        JLabel readLine = awaitLine(shell.get(), read.get());
        assertEquals("Lese Seite „IAR Übersicht“: Seite nicht lesbar", readLine.getText());
        assertNotEquals("Fehler in eigener Farbe", searchLine.getForeground(), readLine.getForeground());
        assertEquals(bubbles.getFailureAccent(), readLine.getForeground());
        awaitGone(shell.get(), think.get());
    }

    @Test
    public void researchCardIsUpdatedInPlaceAndSettlesToItsSummary() throws Exception {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final AtomicReference<ChatShellPanel> shell = new AtomicReference<ChatShellPanel>();
        final AtomicReference<TranscriptEntry> card = new AtomicReference<TranscriptEntry>();
        Edt.run(() -> {
            shell.set(new ChatShellPanel(model, new RecordingActions(), comic, bubbles));
            shell.get().setSize(800, 600);
            shell.get().doLayout();
            model.addUserMessage("Suche in Confluence nach IAR.");
            model.beginAssistantMessage();
            card.set(model.startActivity("research-1", "Recherche in den Wissensquellen", "research",
                    "Suche nach: \u201eIAR\u201c\nSeiten: 0 / 4 [[bar:0/4]]"));
            model.startActivity("research-1", "Recherche in Confluence", "",
                    "Suche nach: \u201eIAR\u201c\nTreffer: 8\nSeiten: 2 / 4 [[bar:2/4]]");
        });
        assertEquals("ersetzt, nicht angehängt", "Suche nach: \u201eIAR\u201c\nTreffer: 8\nSeiten: 2 / 4 [[bar:2/4]]",
                card.get().getText());
        assertEquals("Recherche in Confluence", card.get().getActivityTitle());
        assertEquals("research", card.get().getActivityKind());
        Edt.run(() -> {
            assertTrue(shell.get().transcript().thoughtFor(card.get().getId()) instanceof AgentActivityBubblePanel);
            model.finishActivity("research-1", true, "abgeschlossen: 4 Seiten gelesen, 8 Treffer, 4 Quellen übernommen");
        });
        JLabel line = awaitLine(shell.get(), card.get());
        assertEquals("Recherche in Confluence: abgeschlossen: 4 Seiten gelesen, 8 Treffer, 4 Quellen übernommen",
                line.getText());
    }

    private static JLabel awaitLine(final ChatShellPanel shell, final TranscriptEntry entry) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        final AtomicReference<JLabel> line = new AtomicReference<JLabel>();
        while (System.nanoTime() < deadline) {
            Edt.run(() -> line.set(shell.transcript().activityLineFor(entry.getId())));
            if (line.get() != null) {
                return line.get();
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("Ergebniszeile fehlt für " + entry.getActivityTitle());
    }

    private static void awaitGone(final ChatShellPanel shell, final TranscriptEntry entry) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        final AtomicReference<AnimatedThoughtBubblePanel> thought = new AtomicReference<AnimatedThoughtBubblePanel>();
        final AtomicReference<JLabel> line = new AtomicReference<JLabel>();
        while (System.nanoTime() < deadline) {
            Edt.run(() -> {
                thought.set(shell.transcript().thoughtFor(entry.getId()));
                line.set(shell.transcript().activityLineFor(entry.getId()));
            });
            if (thought.get() == null) {
                assertNull("Denkschritt hinterlässt keine Zeile", line.get());
                return;
            }
            Thread.sleep(20L);
        }
        assertNotNull(thought.get());
        throw new AssertionError("Denkwolke verschwindet nicht");
    }
}
