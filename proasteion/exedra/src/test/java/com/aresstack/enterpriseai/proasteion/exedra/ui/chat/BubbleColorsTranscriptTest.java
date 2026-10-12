package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.proasteion.application.config.BubbleColorsConfig;
import com.aresstack.enterpriseai.proasteion.exedra.assembly.BubblePalettes;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JTable;
import javax.swing.text.AttributeSet;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Einstellbare Blasenfarben im Verlauf: Vorgaben wie bisher, Nutzer- und Antwortblasen in den gewählten Farben, die
 * Schriftfarbe der Antwort bis in jedes Markdown-Element, und neue Farben ohne Neustart.
 */
public class BubbleColorsTranscriptTest {

    private static final String MARKDOWN = "# Überschrift\n\n"
            + "Normaler **fetter** Text mit [Link](https://example.org) und `code`.\n\n"
            + "- Punkt eins\n- Punkt zwei\n\n"
            + "> Ein Zitat\n\n"
            + "| Spalte | Wert |\n|---|---|\n| a | 1 |\n\n"
            + "```\nblock()\n```\n";

    private final ComicPalette comic = ComicPalette.defaultPalette();
    private final BubblePalette whiteOnBlack = BubblePalettes.from(
            new BubbleColorsConfig(0xFFFFFF, 0x000000, 0xFFFFFF, 0x000000));

    @Test
    public void defaultsAreTheFormerPalette() {
        assertEquals(BubblePalette.windowsPhoneInspired(), BubblePalettes.from(BubbleColorsConfig.defaults()));
        assertEquals(BubblePalette.windowsPhoneInspired(), BubblePalettes.from(null));
    }

    @Test
    public void whiteUserBubbleWithBlackText() throws Exception {
        final AtomicReference<SpeechBubblePanel> bubble = new AtomicReference<SpeechBubblePanel>();
        Edt.run(() -> {
            ChatShellModel model = new ChatShellModel(() -> 0L);
            ChatTranscriptPanel transcript = new ChatTranscriptPanel(model, comic, whiteOnBlack);
            TranscriptEntry user = model.addUserMessage("Hallo");
            bubble.set((SpeechBubblePanel) transcript.bubbleFor(user.getId()));
        });
        assertEquals(Color.WHITE, bubble.get().getBubbleColor());
        assertEquals(Color.BLACK, bubble.get().getTextColor());
        for (JTextComponent text : textComponents(bubble.get())) {
            assertEquals(Color.BLACK, opaque(text.getForeground()));
        }
    }

    @Test
    public void whiteAssistantBubbleWithBlackTextReachesEveryMarkdownElement() throws Exception {
        final AtomicReference<ChatTranscriptPanel> transcript = new AtomicReference<ChatTranscriptPanel>();
        final AtomicReference<TranscriptEntry> answer = new AtomicReference<TranscriptEntry>();
        Edt.run(() -> {
            ChatShellModel model = new ChatShellModel(() -> 0L);
            transcript.set(new ChatTranscriptPanel(model, comic, whiteOnBlack));
            model.addUserMessage("Frage");
            answer.set(model.beginAssistantMessage());
            model.appendAssistantDelta(MARKDOWN);
            model.completeAssistantMessage();
            transcript.get().flushPendingUpdates();
        });
        Edt.run(() -> {
            AssistantMarkdownBubble bubble = (AssistantMarkdownBubble) transcript.get().bubbleFor(answer.get().getId());
            assertEquals(Color.WHITE, bubble.bubbleColor());
            assertEquals(Color.BLACK, bubble.headerForeground());
            assertMarkdownForeground(transcript.get().markdownViewFor(answer.get().getId()), Color.BLACK);
        });
    }

    @Test
    public void newColorsApplyWithoutRestart() throws Exception {
        final AtomicReference<ChatTranscriptPanel> transcript = new AtomicReference<ChatTranscriptPanel>();
        final AtomicReference<TranscriptEntry> user = new AtomicReference<TranscriptEntry>();
        final AtomicReference<TranscriptEntry> answer = new AtomicReference<TranscriptEntry>();
        final AtomicReference<Object> before = new AtomicReference<Object>();
        Edt.run(() -> {
            ChatShellModel model = new ChatShellModel(() -> 0L);
            transcript.set(new ChatTranscriptPanel(model, comic, BubblePalette.windowsPhoneInspired()));
            user.set(model.addUserMessage("Frage"));
            answer.set(model.beginAssistantMessage());
            model.appendAssistantDelta(MARKDOWN);
            model.completeAssistantMessage();
            transcript.get().flushPendingUpdates();
            before.set(transcript.get().bubbleFor(answer.get().getId()));

            transcript.get().setBubblePalette(BubblePalette.windowsPhoneInspired());
            assertSame("gleiche Farben bauen nichts neu", before.get(),
                    transcript.get().bubbleFor(answer.get().getId()));

            transcript.get().setBubblePalette(whiteOnBlack);
            assertEquals(whiteOnBlack, transcript.get().bubblePalette());
            assertNotSame(before.get(), transcript.get().bubbleFor(answer.get().getId()));
            SpeechBubblePanel userBubble = (SpeechBubblePanel) transcript.get().bubbleFor(user.get().getId());
            assertEquals(Color.WHITE, userBubble.getBubbleColor());
            assertEquals(Color.BLACK, userBubble.getTextColor());
            assertEquals("Frage", userBubble.getText());
            MarkdownMessageView view = transcript.get().markdownViewFor(answer.get().getId());
            assertEquals(MARKDOWN, view.getMarkdown());
            assertMarkdownForeground(view, Color.BLACK);
        });
    }

    @Test
    public void aStreamingAnswerKeepsStreamingInItsNewBubble() throws Exception {
        Edt.run(() -> {
            ChatShellModel model = new ChatShellModel(() -> 0L);
            ChatTranscriptPanel transcript = new ChatTranscriptPanel(model, comic, BubblePalette.windowsPhoneInspired());
            model.addUserMessage("Frage");
            TranscriptEntry answer = model.beginAssistantMessage();
            model.appendAssistantDelta("Erster Teil. ");
            transcript.flushPendingUpdates();
            transcript.setBubblePalette(whiteOnBlack);
            model.appendAssistantDelta("Zweiter Teil.");
            model.completeAssistantMessage();
            transcript.flushPendingUpdates();
            assertEquals("Erster Teil. Zweiter Teil.", transcript.markdownViewFor(answer.getId()).getMarkdown());
            assertFalse(transcript.markdownViewFor(answer.getId()).isStreaming());
        });
    }

    private static void assertMarkdownForeground(MarkdownMessageView view, Color expected) {
        List<JTextComponent> texts = textComponents(view);
        assertTrue("Markdown hat Textkomponenten", texts.size() >= 4);
        for (JTextComponent text : texts) {
            assertEquals(text.getText(), expected, opaque(text.getForeground()));
            if (text.getDocument() instanceof StyledDocument) {
                StyledDocument document = (StyledDocument) text.getDocument();
                for (int i = 0; i < document.getLength(); i++) {
                    AttributeSet attributes = document.getCharacterElement(i).getAttributes();
                    if (attributes.isDefined(StyleConstants.Foreground)
                            && !StyleConstants.isUnderline(attributes)) {
                        assertEquals(text.getText(), expected, StyleConstants.getForeground(attributes));
                    }
                }
            }
        }
        for (JTable table : tables(view)) {
            assertEquals(expected, table.getForeground());
        }
    }

    private static Color opaque(Color color) {
        return new Color(color.getRGB() & 0xFFFFFF);
    }

    private static List<JTextComponent> textComponents(Container root) {
        List<JTextComponent> found = new ArrayList<JTextComponent>();
        collect(root, JTextComponent.class, found);
        return found;
    }

    private static List<JTable> tables(Container root) {
        List<JTable> found = new ArrayList<JTable>();
        collect(root, JTable.class, found);
        return found;
    }

    private static <T> void collect(Container root, Class<T> type, List<T> found) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                found.add(type.cast(child));
            }
            if (child instanceof Container) {
                collect((Container) child, type, found);
            }
        }
    }
}
