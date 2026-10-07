package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Streaming darf nicht mit der Textlänge teurer werden (CI-Befund aus Strang F: ein Blasen-Update je Delta mit
 * komplettem Neusetzen des Textes war quadratisch und blockierte den Event-Thread minutenlang). Viele Deltas
 * müssen in begrenzter Zeit durch den Verlauf laufen, gebündelt zu höchstens einer Aktualisierung je Intervall,
 * und am Ende steht der vollständige Text in der Blase.
 */
public class ChatTranscriptStreamingTest {

    private static final int DELTAS = 20_000;
    private static final long TIME_LIMIT_MILLIS = TimeUnit.SECONDS.toMillis(20);

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette bubbles = BubblePalette.windowsPhoneInspired();

    @Test
    public void twentyThousandDeltasArriveBundledAndInBoundedTime() throws Exception {
        stream(true);
    }

    /** Ohne Zeilenumbrüche bleibt alles ein Absatz: die Messung darf trotzdem nicht mit der Länge wachsen. */
    @Test
    public void twentyThousandDeltasWithoutNewlinesStayBounded() throws Exception {
        stream(false);
    }

    private void stream(final boolean withNewlines) throws Exception {
        final ChatShellModel model = new ChatShellModel(() -> 0L);
        final AtomicReference<ChatShellPanel> shell = new AtomicReference<ChatShellPanel>();
        final AtomicReference<TranscriptEntry> answer = new AtomicReference<TranscriptEntry>();
        Edt.run(new Runnable() {
            @Override
            public void run() {
                shell.set(new ChatShellPanel(model, new RecordingActions(), comic, bubbles));
                shell.get().setSize(800, 600);
                shell.get().doLayout();
                model.addUserMessage("Erzähl mir alles.");
                answer.set(model.beginAssistantMessage());
            }
        });

        final StringBuilder expected = new StringBuilder();
        long start = System.nanoTime();
        for (int i = 0; i < DELTAS; i++) {
            // Wörter mit gelegentlichem Absatz, wie ein echter Stream: jedes Delta ein eigenes UI-Ereignis.
            final String delta = "Wort" + i + (withNewlines && i % 40 == 39 ? "\n" : " ");
            expected.append(delta);
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    model.appendAssistantDelta(delta);
                }
            });
        }
        Edt.run(new Runnable() {
            @Override
            public void run() {
                model.completeAssistantMessage();
            }
        });
        final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        Edt.run(new Runnable() {
            @Override
            public void run() {
                SpeechBubblePanel bubble = shell.get().transcript().bubbleFor(answer.get().getId());
                assertEquals(expected.toString(), bubble.getText());
                assertEquals(expected.toString(), answer.get().getText());
                int flushes = shell.get().transcript().flushCount();
                assertTrue("höchstens eine Aktualisierung je " + ChatTranscriptPanel.FLUSH_INTERVAL_MILLIS
                        + " ms, aber " + flushes + " in " + elapsedMillis + " ms",
                        flushes <= elapsedMillis / ChatTranscriptPanel.FLUSH_INTERVAL_MILLIS + 2);
                assertTrue("deutlich weniger Aktualisierungen als Deltas: " + flushes, flushes < DELTAS / 10);
                // Die Höhe der Blase folgt dem vollständigen Text, nicht einem veralteten Stand.
                int height = bubble.preferredHeightForWidth(500);
                SpeechBubblePanel fresh = new SpeechBubblePanel(bubble.getSide(), java.awt.Color.BLUE,
                        java.awt.Color.WHITE, ChatTranscriptPanel.ASSISTANT_HEADER, expected.toString());
                assertEquals(fresh.preferredHeightForWidth(500), height);
            }
        });
        assertTrue(DELTAS + " Deltas brauchten " + elapsedMillis + " ms", elapsedMillis < TIME_LIMIT_MILLIS);
    }
}
