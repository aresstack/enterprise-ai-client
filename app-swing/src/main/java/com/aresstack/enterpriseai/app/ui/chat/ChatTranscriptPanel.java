package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubbleMessageRow;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

/**
 * Der Chat-Verlauf als Sprechblasen im AskAI-Stil: Nutzer rechts (blau), Assistent links (petrol), Fehler
 * links in der Fehlerfarbe. Folgt ausschließlich dem {@link ChatShellModel}; Streaming-Deltas aktualisieren
 * die vorhandene Blase, statt neue anzulegen.
 *
 * <p>Zeilenlayout, Blasenbreite und Zeilenhöhe kommen unverändert aus der Comic-Bibliothek
 * ({@link BubbleMessageRow}), die Breitenführung durch den Viewport aus AskAIs {@code BubbleTranscriptPanel}.
 */
public final class ChatTranscriptPanel extends JPanel implements ChatShellModelListener {

    static final String USER_HEADER = "Du";
    static final String ASSISTANT_HEADER = "Assistent";
    static final String CANCELLED_SUFFIX = " · abgebrochen";
    static final String FAILED_HEADER = "Fehler";
    static final String STREAMING_PLACEHOLDER = "…";

    private static final int NEAR_BOTTOM_PIXELS = 48;

    private final ChatShellModel model;
    private final BubblePalette bubblePalette;
    private final JPanel messageList = new WidthTrackingList();
    private final ComicScrollPane scrollPane;
    private final Map<Long, BubbleMessageRow> rows = new HashMap<Long, BubbleMessageRow>();

    public ChatTranscriptPanel(ChatShellModel model, ComicPalette comicPalette, BubblePalette bubblePalette) {
        if (model == null || comicPalette == null || bubblePalette == null) {
            throw new IllegalArgumentException("model and palettes must not be null");
        }
        this.model = model;
        this.bubblePalette = bubblePalette;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(bubblePalette.getTranscriptBackground());
        messageList.setLayout(new BoxLayout(messageList, BoxLayout.Y_AXIS));
        messageList.setBackground(bubblePalette.getTranscriptBackground());
        messageList.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        scrollPane = new ComicScrollPane(messageList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, comicPalette);
        scrollPane.getViewport().setBackground(bubblePalette.getTranscriptBackground());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        add(scrollPane, BorderLayout.CENTER);
        for (TranscriptEntry entry : model.getEntries()) {
            entryAdded(entry);
        }
        model.addListener(this);
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        BubbleMessageRow row = createRow(entry);
        rows.put(entry.getId(), row);
        row.setAlignmentX(LEFT_ALIGNMENT);
        messageList.add(row);
        JComponent spacer = (JComponent) Box.createVerticalStrut(2);
        spacer.setAlignmentX(LEFT_ALIGNMENT);
        messageList.add(spacer);
        refresh(true);
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
        BubbleMessageRow row = rows.get(entry.getId());
        if (row == null) {
            return;
        }
        boolean followBottom = isNearBottom();
        SpeechBubblePanel bubble = (SpeechBubblePanel) row.getBubble();
        if (entry.getState() == TranscriptEntry.State.FAILED) {
            // Die Blasenfarbe ist unveränderlich: eine fehlgeschlagene Antwort bekommt eine neue Fehlerblase.
            replaceRow(row, entry);
        } else {
            bubble.setText(displayText(entry));
            bubble.setHeader(header(entry));
        }
        refresh(followBottom);
    }

    @Override
    public void stateChanged() {
        // Der Verlauf hängt nur an Einträgen; Send/Stop bewertet der Composer.
    }

    /** Die Blase eines Eintrags (für Tests und spätere Kontextaktionen), oder {@code null}. */
    SpeechBubblePanel bubbleFor(long entryId) {
        BubbleMessageRow row = rows.get(entryId);
        return row == null ? null : (SpeechBubblePanel) row.getBubble();
    }

    static String displayText(TranscriptEntry entry) {
        String text = entry.getText();
        switch (entry.getState()) {
            case STREAMING:
                return text.isEmpty() ? STREAMING_PLACEHOLDER : text;
            case FAILED:
                return text.isEmpty() ? entry.getFailureMessage() : text + "\n\n" + entry.getFailureMessage();
            default:
                return text;
        }
    }

    static String header(TranscriptEntry entry) {
        if (entry.getAuthor() == TranscriptEntry.Author.USER) {
            return USER_HEADER;
        }
        switch (entry.getState()) {
            case CANCELLED:
                return ASSISTANT_HEADER + CANCELLED_SUFFIX;
            case FAILED:
                return FAILED_HEADER;
            default:
                return ASSISTANT_HEADER;
        }
    }

    private BubbleMessageRow createRow(TranscriptEntry entry) {
        boolean user = entry.getAuthor() == TranscriptEntry.Author.USER;
        Color background;
        Color foreground;
        if (user) {
            background = bubblePalette.getUserBackground();
            foreground = bubblePalette.getUserForeground();
        } else if (entry.getState() == TranscriptEntry.State.FAILED) {
            background = bubblePalette.getFailureAccent();
            foreground = Color.WHITE;
        } else {
            background = bubblePalette.getAssistantBackground();
            foreground = bubblePalette.getAssistantForeground();
        }
        SpeechBubblePanel bubble = new SpeechBubblePanel(user ? BubbleSide.RIGHT : BubbleSide.LEFT,
                background, foreground, header(entry), displayText(entry));
        bubble.setHeaderTimestamp(entry.getCreatedAtMillis());
        return new BubbleMessageRow(bubble, user ? BubbleSide.RIGHT : BubbleSide.LEFT);
    }

    private void replaceRow(BubbleMessageRow oldRow, TranscriptEntry entry) {
        int index = messageList.getComponentZOrder(oldRow);
        BubbleMessageRow newRow = createRow(entry);
        newRow.setAlignmentX(LEFT_ALIGNMENT);
        messageList.remove(index);
        messageList.add(newRow, index);
        rows.put(entry.getId(), newRow);
    }

    private boolean isNearBottom() {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - NEAR_BOTTOM_PIXELS;
    }

    private void refresh(boolean scrollToBottom) {
        messageList.revalidate();
        messageList.repaint();
        if (scrollToBottom) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    JScrollBar bar = scrollPane.getVerticalScrollBar();
                    bar.setValue(bar.getMaximum());
                }
            });
        }
    }

    /** Der Verlauf folgt der Viewport-Breite, damit Blasen beim Verkleinern umbrechen (aus AskAI übernommen). */
    private static final class WidthTrackingList extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
