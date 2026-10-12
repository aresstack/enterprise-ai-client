package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Zitieren aus Antworten: Text in einer Antwort markieren → schwebendes „Zitieren“ → Zitat-Chip im Composer → beim
 * Senden gehen die Zitate an die Anbindung, der eingegebene Text bleibt, wie er ist.
 */
public class ChatQuoteTest {

    private static final String ANSWER = "Kurz gesagt:\n\nNIZZA ist das Vorsystem für die Übermittlung an ZAUF.";

    @Test
    public void markedTextShowsTheQuoteOverlay() throws Exception {
        Edt.run(() -> {
            Shell shell = new Shell();
            TranscriptEntry answer = shell.answer(ANSWER);
            JTextComponent pane = shell.paragraph(answer, "NIZZA");

            select(pane, "Vorsystem");

            QuoteOverlayButton overlay = shell.panel.transcript().quoteOverlay();
            assertTrue("Overlay erscheint über der Markierung", overlay.isVisible());
            assertEquals(QuoteOverlayButton.LABEL, overlay.getText());
            assertTrue("kein Unicode-Icon, eine Java2D-Glyphe", overlay.getIcon() instanceof QuoteIcon);
            assertTrue(overlay.getWidth() > 0 && overlay.getHeight() > 0);
        });
    }

    @Test
    public void withoutSelectionThereIsNoQuoteAction() throws Exception {
        Edt.run(() -> {
            Shell shell = new Shell();
            TranscriptEntry answer = shell.answer(ANSWER);
            JTextComponent pane = shell.paragraph(answer, "NIZZA");
            MarkdownMessageView view = shell.panel.transcript().markdownViewFor(answer.getId());

            pane.select(3, 3);
            release(pane);

            assertNull(view.selectionOf(pane));
            assertFalse(shell.panel.transcript().quoteOverlay().isVisible());
            assertTrue(shell.panel.composer().quotes().isEmpty());

            select(pane, "Vorsystem");
            assertTrue(shell.panel.transcript().quoteOverlay().isVisible());
            pane.select(0, 0); // Auswahl aufgehoben: das Overlay verschwindet
            assertFalse(shell.panel.transcript().quoteOverlay().isVisible());
        });
    }

    @Test
    public void quotingTakesExactlyTheMarkedTextAndItsMarkdownPosition() throws Exception {
        Edt.run(() -> {
            Shell shell = new Shell();
            TranscriptEntry answer = shell.answer(ANSWER);
            JTextComponent pane = shell.paragraph(answer, "NIZZA");
            String marked = "das Vorsystem für die Übermittlung";

            select(pane, marked);
            shell.panel.transcript().quoteOverlay().doClick();

            List<ChatQuote> quotes = shell.panel.composer().quotes();
            assertEquals(1, quotes.size());
            ChatQuote quote = quotes.get(0);
            assertEquals(marked, quote.selectedText());
            assertTrue("Position im Original-Markdown", quote.hasSourceOffsets());
            assertEquals(ANSWER.indexOf(marked), quote.sourceStart());
            assertEquals(marked, ANSWER.substring(quote.sourceStart(), quote.sourceEnd()));
            assertFalse("Overlay nach dem Klick weg", shell.panel.transcript().quoteOverlay().isVisible());
            assertNull("Markierung aufgehoben", pane.getSelectedText());
        });
    }

    @Test
    public void quoteChipAppearsInTheComposerAndCanBeRemoved() throws Exception {
        Edt.run(() -> {
            Shell shell = new Shell();
            TranscriptEntry answer = shell.answer(ANSWER);
            select(shell.paragraph(answer, "NIZZA"), "Vorsystem");
            shell.panel.transcript().quoteOverlay().doClick();

            ChatComposerPanel composer = shell.panel.composer();
            JComponent chip = (JComponent) find(composer, "composer.quoteChip");
            assertNotNull("Zitat-Chip im Composer", chip);
            assertTrue(chip.isShowing() || chip.getParent().isVisible());
            assertEquals("1 Zitat vorgemerkt", composer.statusText());

            JButton remove = firstButton(chip);
            assertNotNull("✕ am Chip", remove);
            remove.doClick();

            assertTrue(composer.quotes().isEmpty());
            assertNull("Chip entfernt", find(composer, "composer.quoteChip"));
        });
    }

    @Test
    public void quotingLeavesTheComposerTextUntouchedAndSendsQuotesAlongside() throws Exception {
        Edt.run(() -> {
            CapturingActions actions = new CapturingActions();
            Shell shell = new Shell(actions);
            TranscriptEntry answer = shell.answer(ANSWER);
            ChatComposerPanel composer = shell.panel.composer();
            composer.editor().setText("Was meinst du hier mit „Vorsystem“?");

            select(shell.paragraph(answer, "NIZZA"), "Vorsystem");
            shell.panel.transcript().quoteOverlay().doClick();
            assertEquals("Was meinst du hier mit „Vorsystem“?", composer.editor().getText());

            composer.sendButton().doClick();

            assertEquals("Was meinst du hier mit „Vorsystem“?", actions.text);
            assertEquals(1, actions.quotes.size());
            assertEquals("Vorsystem", actions.quotes.get(0).selectedText());
            assertTrue("vorgemerkte Zitate nach dem Senden geleert", composer.quotes().isEmpty());
        });
    }

    @Test
    public void quoteRefersToTheAnswerItWasMarkedIn() throws Exception {
        Edt.run(() -> {
            Shell shell = new Shell();
            TranscriptEntry first = shell.answer("IAR ist die Plattform.");
            shell.model.addUserMessage("Und NIZZA?");
            TranscriptEntry second = shell.answer(ANSWER);

            select(shell.paragraph(second, "NIZZA"), "Vorsystem");
            shell.panel.transcript().quoteOverlay().doClick();
            select(shell.paragraph(first, "IAR"), "Plattform");
            shell.panel.transcript().quoteOverlay().doClick();

            List<ChatQuote> quotes = shell.panel.composer().quotes();
            assertEquals(2, quotes.size());
            assertEquals(second.getId(), quotes.get(0).messageId());
            assertEquals(second.getCreatedAtMillis(), quotes.get(0).messageCreatedAtMillis());
            assertEquals(first.getId(), quotes.get(1).messageId());
            assertEquals("Plattform", quotes.get(1).selectedText());
        });
    }

    @Test
    public void inlineFormattingKeepsTheTextButLeavesThePositionUnknown() throws Exception {
        Edt.run(() -> {
            Shell shell = new Shell();
            TranscriptEntry answer = shell.answer("**NIZZA** ist das Vorsystem.");
            JTextComponent pane = shell.paragraph(answer, "NIZZA");

            select(pane, "NIZZA ist");
            shell.panel.transcript().quoteOverlay().doClick();

            ChatQuote quote = shell.panel.composer().quotes().get(0);
            assertEquals("NIZZA ist", quote.selectedText());
            assertFalse("über ** hinweg nicht eindeutig", quote.hasSourceOffsets());
        });
    }

    @Test
    public void followUpSuggestionsAppearAsYellowPillsAndOnlyFillTheEditor() throws Exception {
        Edt.run(() -> {
            CapturingActions actions = new CapturingActions();
            Shell shell = new Shell(actions);
            TranscriptEntry answer = shell.answer(ANSWER);
            ChatComposerPanel composer = shell.panel.composer();

            select(shell.paragraph(answer, "NIZZA"), "Vorsystem");
            shell.panel.transcript().quoteOverlay().doClick();
            assertEquals("erst nach „Zitieren“ angefragt, einmal", 1, actions.followUpRequests.size());
            assertEquals("Vorsystem", actions.followUpQuotes.get(0).selectedText());
            assertTrue("Zitat ist sofort benutzbar, ohne Vorschläge", composer.quoteSuggestions().isEmpty());
            assertFalse(composer.quoteSuggestionStrip().isVisible());

            actions.followUpRequests.get(0).accept(java.util.Arrays.asList("Was bedeutet hier \u201EVorsystem\u201C?",
                    "Welche Rolle spielt NIZZA?", "Erklär mir das genauer."));
            assertTrue(composer.quoteSuggestionStrip().isVisible());
            List<JButton> pills = buttons(composer.quoteSuggestionStrip());
            assertEquals(3, pills.size());
            assertTrue("gelbe Research-Pille", pills.get(0) instanceof
                    com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton);

            pills.get(1).doClick();
            assertEquals("Welche Rolle spielt NIZZA?", composer.editor().getText());
            assertNull("Klick sendet nicht", actions.text);

            composer.editor().setText("Und außerdem:");
            pills.get(2).doClick();
            assertEquals("vorhandener Text bleibt", "Und außerdem:\nErklär mir das genauer.",
                    composer.editor().getText());
            assertNull(actions.text);
        });
    }

    @Test
    public void lateSuggestionsOfARemovedOrReplacedQuoteAreDropped() throws Exception {
        Edt.run(() -> {
            CapturingActions actions = new CapturingActions();
            Shell shell = new Shell(actions);
            TranscriptEntry answer = shell.answer(ANSWER);
            ChatComposerPanel composer = shell.panel.composer();
            JTextComponent pane = shell.paragraph(answer, "NIZZA");

            select(pane, "NIZZA");
            shell.panel.transcript().quoteOverlay().doClick();
            select(pane, "Vorsystem");
            shell.panel.transcript().quoteOverlay().doClick();
            assertEquals(2, actions.followUpRequests.size());

            actions.followUpRequests.get(0).accept(java.util.Collections.singletonList("Wofür steht NIZZA?"));
            assertTrue("ersetzt durch das neuere Zitat", composer.quoteSuggestions().isEmpty());

            actions.followUpRequests.get(1).accept(java.util.Collections.singletonList("Was bedeutet Vorsystem?"));
            assertEquals(java.util.Collections.singletonList("Was bedeutet Vorsystem?"), composer.quoteSuggestions());

            composer.removeQuote(actions.followUpQuotes.get(1));
            assertTrue("Vorschläge gehen mit ihrem Zitat", composer.quoteSuggestions().isEmpty());
            assertFalse(composer.quoteSuggestionStrip().isVisible());
            actions.followUpRequests.get(1).accept(java.util.Collections.singletonList("Zu spät?"));
            assertTrue("verspätetes Ergebnis wird verworfen", composer.quoteSuggestions().isEmpty());
        });
    }

    // ---------------------------------------------------------------------------------------------------------

    private static void select(JTextComponent pane, String text) {
        int start = pane.getText().indexOf(text);
        assertTrue("Text in der Komponente: " + text, start >= 0);
        pane.select(start, start + text.length());
        release(pane);
    }

    private static void release(JTextComponent pane) {
        MouseEvent released = new MouseEvent(pane, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, 5, 5,
                1, false, MouseEvent.BUTTON1);
        pane.dispatchEvent(released);
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> found = new ArrayList<JButton>();
        for (Component child : container.getComponents()) {
            if (child instanceof JButton) {
                found.add((JButton) child);
            }
        }
        return found;
    }

    private static Component find(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (name.equals(child.getName())) {
                return child;
            }
            if (child instanceof Container) {
                Component nested = find((Container) child, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static JButton firstButton(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton) {
                return (JButton) child;
            }
            if (child instanceof Container) {
                JButton nested = firstButton((Container) child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static JTextComponent textContaining(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTextComponent && ((JTextComponent) child).getText().contains(text)) {
                return (JTextComponent) child;
            }
            if (child instanceof Container) {
                JTextComponent nested = textContaining((Container) child, text);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static final class Shell {
        final ChatShellModel model = new ChatShellModel(() -> 1_700_000_000_000L);
        final ChatShellPanel panel;

        Shell() {
            this(new CapturingActions());
        }

        Shell(ChatShellActions actions) {
            panel = new ChatShellPanel(model, actions, ComicPalette.defaultPalette(),
                    BubblePalette.windowsPhoneInspired());
            panel.setSize(900, 700);
            layout(panel);
        }

        TranscriptEntry answer(String markdown) {
            TranscriptEntry answer = model.beginAssistantMessage();
            model.appendAssistantDelta(markdown);
            model.completeAssistantMessage();
            panel.transcript().flushPendingUpdates();
            layout(panel);
            return answer;
        }

        JTextComponent paragraph(TranscriptEntry answer, String containing) {
            MarkdownMessageView view = panel.transcript().markdownViewFor(answer.getId());
            assertNotNull("Markdown-Blase der Antwort", view);
            JTextComponent pane = textContaining(view, containing);
            assertNotNull("Absatz mit " + containing, pane);
            return pane;
        }

        private static void layout(Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                if (child instanceof Container) {
                    layout((Container) child);
                }
            }
        }
    }

    /** Merkt sich die letzte Nachricht mit ihren Zitaten. */
    private static final class CapturingActions implements ChatShellActions {
        String text;
        List<ChatQuote> quotes = new ArrayList<ChatQuote>();
        final List<java.util.function.Consumer<List<String>>> followUpRequests =
                new ArrayList<java.util.function.Consumer<List<String>>>();
        final List<ChatQuote> followUpQuotes = new ArrayList<ChatQuote>();

        @Override
        public void quoteFollowUpsRequested(ChatQuote quote, java.util.function.Consumer<List<String>> suggestions) {
            followUpQuotes.add(quote);
            followUpRequests.add(suggestions);
        }

        @Override
        public void sendRequested(String value, boolean ragEnabled) {
            text = value;
        }

        @Override
        public void sendRequested(ChatDraft draft) {
            text = draft.text();
            quotes = new ArrayList<ChatQuote>(draft.quotes());
        }

        @Override
        public void stopRequested() {
        }
    }
}
