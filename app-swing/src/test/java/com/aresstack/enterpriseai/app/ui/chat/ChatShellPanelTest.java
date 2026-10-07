package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Die ganze Shell headless gegen ein Fake-Backend: Eingabe → Senden → gestreamte Antwort in Blasen → Stop
 * → Fehlerdarstellung, inklusive Send/Stop-Freigabe und RAG-Schalter.
 */
public class ChatShellPanelTest {

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();

    @Test
    public void sendingStreamsTheAnswerIntoBubbles() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model,
                        new ScriptedStreamingActions(model, false, "Hallo", ", ", "Welt"), comic, bubbles);
                ChatComposerPanel composer = shell.composer();

                assertFalse("leerer Entwurf", composer.sendButton().isEnabled());
                assertFalse("nichts zu stoppen", composer.stopButton().isEnabled());

                composer.editor().setText("  Wer bist du?  ");
                assertTrue(composer.sendButton().isEnabled());
                composer.sendButton().doClick();

                List<TranscriptEntry> entries = model.getEntries();
                assertEquals(2, entries.size());
                assertEquals("Wer bist du?", entries.get(0).getText());
                assertEquals("", composer.editor().getText());

                SpeechBubblePanel user = shell.transcript().bubbleFor(entries.get(0).getId());
                SpeechBubblePanel assistant = shell.transcript().bubbleFor(entries.get(1).getId());
                assertEquals(BubbleSide.RIGHT, user.getSide());
                assertEquals(BubbleSide.LEFT, assistant.getSide());
                assertEquals("Hallo, Welt", assistant.getText());
                assertFalse(composer.stopButton().isEnabled());

                shell.setSize(700, 500);
                shell.doLayout();
                paint(shell);
            }
        });
    }

    @Test
    public void stopCancelsTheRunningAnswerAndReenablesSend() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model,
                        new ScriptedStreamingActions(model, true, "Ich denke"), comic, bubbles);
                ChatComposerPanel composer = shell.composer();
                composer.editor().setText("Frage");
                composer.submit();

                assertTrue(composer.stopButton().isEnabled());
                composer.editor().setText("Nächste Frage");
                assertFalse("kein Senden während des Streamings", composer.sendButton().isEnabled());

                composer.stopButton().doClick();

                TranscriptEntry answer = model.getEntries().get(1);
                assertEquals(TranscriptEntry.State.CANCELLED, answer.getState());
                SpeechBubblePanel bubble = shell.transcript().bubbleFor(answer.getId());
                assertEquals("Ich denke", bubble.getText());
                assertFalse(composer.stopButton().isEnabled());
                assertTrue(composer.sendButton().isEnabled());
            }
        });
    }

    @Test
    public void ragToggleTravelsWithTheSendIntent() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                RecordingActions actions = new RecordingActions();
                ChatShellPanel shell = new ChatShellPanel(model, actions, comic, bubbles);
                ChatComposerPanel composer = shell.composer();

                composer.editor().setText("ohne");
                composer.submit();
                composer.ragToggle().doClick();
                assertTrue(model.isRagEnabled());
                composer.editor().setText("mit");
                composer.submit();

                assertEquals(java.util.Arrays.asList("ohne", "mit"), actions.sent);
                assertEquals(java.util.Arrays.asList(false, true), actions.ragFlags);

                model.setRagEnabled(false);
                assertFalse("Model führt den Schalter", composer.ragToggle().isSelected());
            }
        });
    }

    @Test
    public void enterSendsWhileShiftEnterIsBoundToANewLine() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                RecordingActions actions = new RecordingActions();
                ChatComposerPanel composer = new ChatComposerPanel(model, actions, comic);
                composer.editor().setText("per Enter");

                Object enter = composer.editor().getInputMap().get(
                        javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
                composer.editor().getActionMap().get(enter).actionPerformed(
                        new java.awt.event.ActionEvent(composer.editor(), 0, ""));
                assertEquals(java.util.Collections.singletonList("per Enter"), actions.sent);

                Object shiftEnter = composer.editor().getInputMap().get(javax.swing.KeyStroke.getKeyStroke(
                        java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.SHIFT_DOWN_MASK));
                assertEquals(javax.swing.text.DefaultEditorKit.insertBreakAction, shiftEnter);
            }
        });
    }

    @Test
    public void failureIsShownAsAnErrorBubble() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(), comic, bubbles);
                model.addUserMessage("Frage");
                TranscriptEntry answer = model.beginAssistantMessage();
                assertEquals(ChatTranscriptPanel.STREAMING_PLACEHOLDER,
                        shell.transcript().bubbleFor(answer.getId()).getText());
                model.appendAssistantDelta("Teil");
                model.failAssistantMessage("Server nicht erreichbar");

                SpeechBubblePanel bubble = shell.transcript().bubbleFor(answer.getId());
                assertNotNull(bubble);
                assertTrue(bubble.getText().startsWith("Teil"));
                assertTrue(bubble.getText().endsWith("Server nicht erreichbar"));
                assertEquals(BubbleSide.LEFT, bubble.getSide());
                assertEquals("Fehlerblase ersetzt die Antwortzeile: zwei Zeilen plus zwei Abstände", 4,
                        countRows(shell));
                assertFalse(shell.composer().stopButton().isEnabled());
            }
        });
    }

    @Test
    public void transcriptShowsEntriesThatExistedBeforeTheView() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                TranscriptEntry early = model.addUserMessage("vorher");
                ChatTranscriptPanel transcript = new ChatTranscriptPanel(model, comic, bubbles);
                assertEquals("vorher", transcript.bubbleFor(early.getId()).getText());
            }
        });
    }

    @Test
    public void headersNameAuthorAndOutcome() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        TranscriptEntry user = model.addUserMessage("x");
        TranscriptEntry answer = model.beginAssistantMessage();
        assertEquals(ChatTranscriptPanel.USER_HEADER, ChatTranscriptPanel.header(user));
        assertEquals(ChatTranscriptPanel.ASSISTANT_HEADER, ChatTranscriptPanel.header(answer));
        model.cancelAssistantMessage();
        assertEquals(ChatTranscriptPanel.ASSISTANT_HEADER + ChatTranscriptPanel.CANCELLED_SUFFIX,
                ChatTranscriptPanel.header(answer));
    }

    private static int countRows(ChatShellPanel shell) {
        javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) shell.transcript().getComponent(0);
        return ((java.awt.Container) scroll.getViewport().getView()).getComponentCount();
    }

    private static void paint(java.awt.Component component) {
        BufferedImage image = new BufferedImage(Math.max(1, component.getWidth()),
                Math.max(1, component.getHeight()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            component.paint(g2);
        } finally {
            g2.dispose();
        }
    }
}
