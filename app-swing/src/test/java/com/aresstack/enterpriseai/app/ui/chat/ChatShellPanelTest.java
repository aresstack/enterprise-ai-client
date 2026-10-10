package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.TranscriptBubble;
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

                TranscriptBubble user = shell.transcript().bubbleFor(entries.get(0).getId());
                TranscriptBubble assistant = shell.transcript().bubbleFor(entries.get(1).getId());
                assertTrue("Antworten sind Markdown-Blasen", assistant instanceof AssistantMarkdownBubble);
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
                TranscriptBubble bubble = shell.transcript().bubbleFor(answer.getId());
                assertEquals("Ich denke", bubble.getText());
                assertFalse(composer.stopButton().isEnabled());
                assertTrue(composer.sendButton().isEnabled());
            }
        });
    }

    @Test
    public void ragFlagOfTheModelTravelsWithTheSendIntent() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                RecordingActions actions = new RecordingActions();
                ChatShellPanel shell = new ChatShellPanel(model, actions, comic, bubbles);
                ChatComposerPanel composer = shell.composer();

                composer.editor().setText("ohne");
                composer.submit();
                model.setRagEnabled(true);
                composer.editor().setText("mit");
                composer.submit();

                assertEquals(java.util.Arrays.asList("ohne", "mit"), actions.sent);
                assertEquals(java.util.Arrays.asList(false, true), actions.ragFlags);
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

                SpeechBubblePanel bubble = (SpeechBubblePanel) shell.transcript().bubbleFor(answer.getId());
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
    public void failureDetailsFoldBehindTheHeadlineAndRespectTheBubbleWidth() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(), comic, bubbles);
                shell.setSize(700, 500);
                model.addUserMessage("Frage");
                TranscriptEntry answer = model.beginAssistantMessage();
                model.failAssistantMessage("Der KI-Dienst ist nicht erreichbar.\n"
                        + "Technische Ursache: connection to demo2.example failed: UnknownHostException\n"
                        + "Hinweis: Proxy-Modus in den Einstellungen prüfen (AUTO/MANUAL).");

                SpeechBubblePanel bubble = (SpeechBubblePanel) shell.transcript().bubbleFor(answer.getId());
                assertEquals("nur die Überschrift steht in der Blase", "Der KI-Dienst ist nicht erreichbar.",
                        bubble.getText());
                assertTrue(bubble.hasDetails());
                assertFalse("Details beginnen eingeklappt", bubble.isDetailsExpanded());
                assertTrue(bubble.getDetails().startsWith("Technische Ursache: "));
                assertTrue(bubble.getDetails().contains("Hinweis: Proxy-Modus"));
                assertEquals(ChatTranscriptPanel.SHOW_DETAILS_LABEL + " \u25be", bubble.detailsToggle().getText());

                shell.doLayout();
                layoutTree(shell);
                int folded = bubble.getHeight();
                int limit = (int) Math.round(shell.transcript().getWidth() * 0.92);
                assertTrue("Blase bleibt innerhalb der üblichen Breite", bubble.getWidth() <= limit);

                bubble.detailsToggle().doClick();
                assertTrue(bubble.isDetailsExpanded());
                assertEquals(ChatTranscriptPanel.HIDE_DETAILS_LABEL + " \u25b4", bubble.detailsToggle().getText());
                layoutTree(shell);
                assertTrue("aufgeklappt wächst die Blase nach unten", bubble.getHeight() > folded);
                assertTrue(bubble.getWidth() <= limit);
                paint(shell);
            }
        });
    }

    @Test
    public void clearEmptiesTheTranscriptButRefusesWhileStreaming() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(), comic, bubbles);
                model.addUserMessage("Frage");
                TranscriptEntry answer = model.beginAssistantMessage();
                try {
                    model.clear();
                    throw new AssertionError("während einer Antwort darf nicht geleert werden");
                } catch (IllegalStateException expected) {
                    // erwartet
                }
                model.completeAssistantMessage();
                assertNotNull(shell.transcript().bubbleFor(answer.getId()));

                model.clear();
                assertTrue(model.getEntries().isEmpty());
                assertEquals(null, shell.transcript().bubbleFor(answer.getId()));
                assertEquals(0, countRows(shell));

                model.addUserMessage("Neue Frage");
                assertEquals(1, model.getEntries().size());
                assertNotNull(shell.transcript().bubbleFor(model.getEntries().get(0).getId()));
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

    @Test
    public void sourcesAppearUnderTheAnswerAndExpandOnDemand() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(), comic, bubbles);
                shell.setSize(700, 500);
                model.addUserMessage("Wie lange ist die Kündigungsfrist?");
                TranscriptEntry answer = model.beginAssistantMessage();
                model.appendAssistantDelta("Drei Monate [1].");
                assertEquals(null, shell.transcript().sourcesFor(answer.getId()));

                model.attachSources(answer, java.util.Arrays.asList(
                        new SourceReference(1, "Kündigungsfrist", "Regeln > Fristen", "https://wiki.intern/Fristen",
                                "Version 7, 2026-10-01 08:00", 0.0328, 1, 1),
                        new SourceReference(2, "Urlaubsregelung", "", "memory:handbuch/urlaub", "", 0.0161, 2, 0)));
                model.completeAssistantMessage();

                SourceListPanel sources = shell.transcript().sourcesFor(answer.getId());
                assertNotNull("Quellenliste unter der Antwort", sources);
                assertEquals("Quellen (2)", sources.toggle().getText());
                assertFalse("zunächst eingeklappt", sources.isExpanded());
                assertEquals("Nutzer, Antwort, Quellen: drei Zeilen plus drei Abstände", 6, countRows(shell));
                assertEquals("Quellen direkt unter der Antwort", 4, rowIndexOf(shell, sources));

                shell.doLayout();
                int collapsed = sources.preferredHeightForWidth(500);
                sources.toggle().doClick();
                assertTrue(sources.isExpanded());
                assertTrue("aufgeklappt höher als eingeklappt", sources.preferredHeightForWidth(500) > collapsed);
                shell.doLayout();
                paint(shell);
                sources.setExpanded(false);
                assertFalse(sources.toggle().isSelected());

                assertEquals("[1] Kündigungsfrist – Regeln > Fristen", SourceListPanel.titleLine(sources.getSources().get(0)));
                assertEquals("Ort: https://wiki.intern/Fristen  ·  Stand: Version 7, 2026-10-01 08:00  ·  Score 0.033"
                        + "  ·  Volltext #1  ·  Semantik #1", SourceListPanel.detailLine(sources.getSources().get(0)));
                assertEquals("Ort: memory:handbuch/urlaub  ·  Score 0.016  ·  Volltext #2",
                        SourceListPanel.detailLine(sources.getSources().get(1)));
            }
        });
    }

    @Test
    public void sourcesSurviveAFailedAnswerAndShowForEarlierEntries() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                TranscriptEntry answer = model.beginAssistantMessage();
                model.attachSources(answer, java.util.Collections.singletonList(
                        new SourceReference(1, "Seite", "", "memory:x", "", 0.02, 1, 0)));
                ChatTranscriptPanel transcript = new ChatTranscriptPanel(model, comic, bubbles);
                assertNotNull("Quellen aus der Zeit vor der Ansicht", transcript.sourcesFor(answer.getId()));

                model.failAssistantMessage("Server nicht erreichbar");
                assertNotNull("Fehlerblase ersetzt nur die Antwortzeile", transcript.sourcesFor(answer.getId()));
                assertEquals(ChatTranscriptPanel.FAILED_HEADER, ChatTranscriptPanel.header(answer));
            }
        });
    }

    @Test
    public void noticeIsItsOwnBubbleOnTheLeftAndActivityFillsTheEmptyAnswer() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(), comic, bubbles);
                model.addUserMessage("Frage");
                TranscriptEntry answer = model.beginAssistantMessage();
                model.setAssistantActivity("Wissen wird gesucht …");
                assertEquals("Wissen wird gesucht …", shell.transcript().bubbleFor(answer.getId()).getText());

                TranscriptEntry notice = model.addNotice("Die Wissenssuche ist ausgefallen.");
                SpeechBubblePanel bubble = (SpeechBubblePanel) shell.transcript().bubbleFor(notice.getId());
                assertNotNull(bubble);
                assertEquals(BubbleSide.LEFT, bubble.getSide());
                assertEquals(ChatTranscriptPanel.NOTICE_HEADER, ChatTranscriptPanel.header(notice));
                assertEquals("Die Wissenssuche ist ausgefallen.", bubble.getText());
                assertTrue("Antwort läuft weiter", shell.composer().stopButton().isEnabled());

                model.appendAssistantDelta("Antwort");
                shell.transcript().flushPendingUpdates(); // Deltas kurz nach der Aktivität werden gebündelt
                assertEquals("Antwort", shell.transcript().bubbleFor(answer.getId()).getText());
                model.completeAssistantMessage();
                shell.setSize(700, 500);
                shell.doLayout();
                paint(shell);
            }
        });
    }

    @Test
    public void statusBarFollowsTheKnowledgeStatusModel() throws Exception {
        Edt.run(new Runnable() {
            @Override
            public void run() {
                ChatShellModel model = new ChatShellModel(() -> 0L);
                KnowledgeStatusModel status = new KnowledgeStatusModel();
                ChatShellPanel shell = new ChatShellPanel(model, new RecordingActions(), status, comic, bubbles);
                KnowledgeStatusBar bar = shell.statusBar();
                assertFalse("ohne Text unsichtbar", bar.isVisible());

                status.started("Indexierung von handbuch …");
                assertTrue(bar.isVisible());
                assertTrue(bar.cancelButton().isVisible());
                assertTrue(bar.cancelButton().isEnabled());
                status.progressed("Indexierung: 1 von 3 Seiten · Urlaub");
                assertEquals("Indexierung: 1 von 3 Seiten · Urlaub", bar.getText());

                bar.cancelButton().doClick();
                assertTrue(status.isCancelRequested());
                assertFalse(bar.cancelButton().isEnabled());
                assertTrue(bar.getText().endsWith(KnowledgeStatusBar.CANCELLING_SUFFIX));

                status.finished("Indexierung abgebrochen: 1 von 3 Seiten indexiert (Stand 16:30)");
                assertTrue(bar.isVisible());
                assertFalse(bar.cancelButton().isVisible());
                assertEquals("Indexierung abgebrochen: 1 von 3 Seiten indexiert (Stand 16:30)", bar.getText());
                shell.setSize(700, 500);
                shell.doLayout();
                paint(shell);

                status.show("");
                assertFalse(bar.isVisible());
                assertFalse("Agent-Ansicht ohne Statuszeile",
                        new ChatShellPanel(model, new RecordingActions(), comic, bubbles).statusBar().isVisible());
            }
        });
    }

    private static int rowIndexOf(ChatShellPanel shell, java.awt.Component bubble) {
        javax.swing.JScrollPane scroll = shell.transcript().scrollPane();
        java.awt.Container list = (java.awt.Container) scroll.getViewport().getView();
        return list.getComponentZOrder(bubble.getParent());
    }

    private static int countRows(ChatShellPanel shell) {
        javax.swing.JScrollPane scroll = shell.transcript().scrollPane();
        int rows = 0;
        for (java.awt.Component row : ((java.awt.Container) scroll.getViewport().getView()).getComponents()) {
            if (!"transcript.emptyState".equals(row.getName())) {
                rows++; // die Infozeile des leeren Verlaufs zählt nicht
            }
        }
        return rows;
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

    private static void layoutTree(java.awt.Component component) {
        component.invalidate();
        component.doLayout();
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                layoutTree(child);
            }
        }
    }
}
