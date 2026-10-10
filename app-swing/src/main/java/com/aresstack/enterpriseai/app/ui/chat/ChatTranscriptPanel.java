package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.app.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.app.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.app.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.app.ui.markdown.MarkdownTheme;
import com.aresstack.enterpriseai.app.ui.markdown.MermaidImageRenderer;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleMessageRow;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.TranscriptBubble;
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
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Der Chat-Verlauf als Sprechblasen im AskAI-Stil: Nutzer rechts (blau), Assistent links (petrol), Fehler
 * links in der Fehlerfarbe, Hinweise der Anwendung links in der gelben Aktivitätsfarbe. Folgt ausschließlich dem
 * {@link ChatShellModel}; Streaming-Deltas aktualisieren die vorhandene Blase, statt neue anzulegen.
 *
 * <p>Antworten des Assistenten sind Markdown-Blasen ({@link AssistantMarkdownBubble} mit
 * {@link MarkdownMessageView}, wie askai-java8 {@code arch}): Überschriften, Listen, Tabellen, Code mit
 * Kopieraktion, Links und Mermaid-Diagramme (als Bild, Klick öffnet den Betrachter) werden nativ gerendert;
 * {@code ```markdown}-Umhüllungen fallen weg. Nutzer, Hinweise und Fehler bleiben Text-Sprechblasen.
 *
 * <p>Bekommt eine Antwort Quellen (AP22), erscheint direkt unter ihrer Blase eine eigene Zeile mit der
 * ein- und ausklappbaren {@link SourceListPanel Quellenliste}.
 *
 * <p>Fehler bleiben Sprechblasen in normaler Geometrie: die Blase zeigt nur die erste Zeile der Fehlermeldung
 * (die Überschrift, z. B. „Der KI-Dienst ist nicht erreichbar.“); „Technische Ursache“ und „Hinweis“ liegen als
 * Details in der Blase und lassen sich dort aufklappen — kein Banner über die volle Breite.
 *
 * <p>Streaming-Deltas werden gebündelt: während eine Antwort läuft, wird ihre Blase höchstens einmal je
 * {@value #FLUSH_INTERVAL_MILLIS} ms aktualisiert (das erste Delta sofort, weitere gesammelt und mit einem
 * nachlaufenden Timer), und jede Aktualisierung hängt nur den neuen Text an, statt die Blase neu zu setzen.
 * Damit wächst die Zeit je Delta nicht mit der Textlänge. Abschluss, Abbruch, Fehler und Quellen werden sofort
 * angewendet; {@link #flushPendingUpdates()} wendet Gesammeltes auf Wunsch sofort an.
 *
 * <p>Zeilenlayout, Blasenbreite und Zeilenhöhe kommen unverändert aus der Comic-Bibliothek
 * ({@link BubbleMessageRow}), die Breitenführung durch den Viewport aus AskAIs {@code BubbleTranscriptPanel}.
 */
public final class ChatTranscriptPanel extends JPanel implements ChatShellModelListener {

    static final String USER_HEADER = "Du";
    /** Vor jedem Dateinamen in der Nutzerblase (Anhänge). */
    static final String ATTACHMENT_PREFIX = "Anhang: ";
    static final String ASSISTANT_HEADER = "Assistent";
    static final String CANCELLED_SUFFIX = " · abgebrochen";
    static final String FAILED_HEADER = "Fehler";
    static final String NOTICE_HEADER = "Hinweis";
    static final String STREAMING_PLACEHOLDER = "…";
    static final String SHOW_DETAILS_LABEL = "Details anzeigen";
    static final String HIDE_DETAILS_LABEL = "Details ausblenden";

    /** Höchstens eine Blasen-Aktualisierung je Intervall, solange eine Antwort streamt. */
    static final int FLUSH_INTERVAL_MILLIS = 30;

    private static final int NEAR_BOTTOM_PIXELS = 48;
    private static final long FLUSH_INTERVAL_NANOS = FLUSH_INTERVAL_MILLIS * 1_000_000L;

    private final ChatShellModel model;
    private final ComicPalette comicPalette;
    private final BubblePalette bubblePalette;
    private final MarkdownTheme assistantTheme;
    private final DesktopLinkOpener linkOpener;
    private final MermaidImageRenderer mermaidImageRenderer;
    private final JPanel messageList = new WidthTrackingList();
    private final ComicScrollPane scrollPane;
    private final Map<Long, RowState> rows = new HashMap<Long, RowState>();
    private final Map<Long, BubbleMessageRow> sourceRows = new HashMap<Long, BubbleMessageRow>();
    private final Map<Long, TranscriptEntry> pending = new LinkedHashMap<Long, TranscriptEntry>();
    private final Timer flushTimer;
    private long lastFlushNanos;
    private boolean flushedBefore;
    private int flushCount;

    public ChatTranscriptPanel(ChatShellModel model, ComicPalette comicPalette, BubblePalette bubblePalette) {
        this(model, comicPalette, bubblePalette, DesktopLinkOpener.systemDefault(),
                CachingMermaidImageRenderer.forChat());
    }

    /**
     * @param linkOpener           öffnet angeklickte Links der Antworten
     * @param mermaidImageRenderer rendert Mermaid-Zäune der Antworten zu Bildern (je Verlauf ein eigener Cache)
     */
    public ChatTranscriptPanel(ChatShellModel model, ComicPalette comicPalette, BubblePalette bubblePalette,
                               DesktopLinkOpener linkOpener, MermaidImageRenderer mermaidImageRenderer) {
        if (model == null || comicPalette == null || bubblePalette == null) {
            throw new IllegalArgumentException("model and palettes must not be null");
        }
        if (linkOpener == null || mermaidImageRenderer == null) {
            throw new IllegalArgumentException("linkOpener and mermaidImageRenderer must not be null");
        }
        this.model = model;
        this.comicPalette = comicPalette;
        this.bubblePalette = bubblePalette;
        this.linkOpener = linkOpener;
        this.mermaidImageRenderer = mermaidImageRenderer;
        this.assistantTheme = MarkdownTheme.forBubble(bubblePalette.getAssistantBackground(),
                bubblePalette.getAssistantForeground());
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
        flushTimer = new Timer(FLUSH_INTERVAL_MILLIS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                flushPendingUpdates();
            }
        });
        flushTimer.setRepeats(false);
        for (TranscriptEntry entry : model.getEntries()) {
            entryAdded(entry);
        }
        model.addListener(this);
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        flushPendingUpdates();
        RowState state = createRow(entry);
        rows.put(entry.getId(), state);
        state.row.setAlignmentX(LEFT_ALIGNMENT);
        messageList.add(state.row);
        messageList.add(spacer());
        if (entry.hasSources()) {
            addSourcesRow(entry);
        }
        refresh(true);
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
        if (!rows.containsKey(entry.getId())) {
            return;
        }
        pending.put(entry.getId(), entry);
        if (entry.getState() != TranscriptEntry.State.STREAMING) {
            flushPendingUpdates();
            return;
        }
        if (flushTimer.isRunning()) {
            return; // der nachlaufende Timer wendet das Gesammelte an
        }
        if (flushedBefore && System.nanoTime() - lastFlushNanos < FLUSH_INTERVAL_NANOS) {
            flushTimer.start();
            return;
        }
        flushPendingUpdates();
    }

    /** Wendet gesammelte Streaming-Aktualisierungen sofort an (UI-Thread). */
    void flushPendingUpdates() {
        flushTimer.stop();
        if (pending.isEmpty()) {
            return;
        }
        boolean followBottom = isNearBottom();
        for (TranscriptEntry entry : pending.values()) {
            apply(entry);
        }
        pending.clear();
        lastFlushNanos = System.nanoTime();
        flushedBefore = true;
        flushCount++;
        refresh(followBottom);
    }

    /** Wie oft Aktualisierungen angewendet wurden (für Tests: gebündelte Deltas zählen einmal). */
    int flushCount() {
        return flushCount;
    }

    @Override
    public void stateChanged() {
        // Der Verlauf hängt nur an Einträgen; Send/Stop bewertet der Composer.
    }

    @Override
    public void entriesCleared() {
        flushTimer.stop();
        pending.clear();
        rows.clear();
        sourceRows.clear();
        messageList.removeAll();
        refresh(false);
    }

    /**
     * Die Blase eines Eintrags (für Tests und spätere Kontextaktionen), oder {@code null}: eine
     * {@link SpeechBubblePanel} für Nutzer, Hinweise und Fehler, sonst die Markdown-Blase des Assistenten.
     */
    public TranscriptBubble bubbleFor(long entryId) {
        RowState state = rows.get(entryId);
        return state == null ? null : state.bubble;
    }

    /** Bringt die Blase eines Eintrags auf seinen aktuellen Stand; Streaming-Text wird nur angehängt. */
    private void apply(TranscriptEntry entry) {
        RowState state = rows.get(entry.getId());
        if (state == null) {
            return;
        }
        if (entry.getState() == TranscriptEntry.State.FAILED) {
            // Die Blasenfarbe ist unveränderlich: eine fehlgeschlagene Antwort bekommt eine neue Fehlerblase.
            replaceRow(state, entry);
        } else {
            String text = displayText(entry);
            boolean showsEntryText = !entry.getText().isEmpty();
            // Der Text eines Eintrags wächst nur durch Anhängen (TranscriptEntry.append), daher reicht der Rest.
            boolean appendable = state.showsEntryText && showsEntryText && text.length() >= state.shownLength;
            if (state.bubble instanceof AssistantMarkdownBubble) {
                applyMarkdown(((AssistantMarkdownBubble) state.bubble).view(), entry, text,
                        appendable ? text.substring(state.shownLength) : null);
            } else if (appendable) {
                ((SpeechBubblePanel) state.bubble).appendText(text.substring(state.shownLength));
            } else {
                ((SpeechBubblePanel) state.bubble).setText(text);
            }
            state.showsEntryText = showsEntryText;
            state.shownLength = text.length();
            String header = header(entry);
            if (!header.equals(state.header)) {
                state.bubble.setHeader(header);
                state.header = header;
            }
        }
        if (entry.hasSources() && !sourceRows.containsKey(entry.getId())) {
            addSourcesRow(entry);
        }
    }

    /**
     * Bringt den Markdown-Körper einer Antwort auf den Stand des Eintrags: ein Delta wird angehängt (gedrosselt neu
     * gerendert), ein geänderter Text (Platzhalter, Aktivität, Neustart) ersetzt den Inhalt; mit dem Ende des
     * Streamings rendert die Ansicht vollständig und erst dann die Mermaid-Diagramme.
     */
    private static void applyMarkdown(MarkdownMessageView view, TranscriptEntry entry, String text, String delta) {
        boolean streaming = entry.getState() == TranscriptEntry.State.STREAMING;
        if (delta != null) {
            view.appendMarkdownDelta(delta);
        } else if (streaming) {
            view.startStreaming();
            view.appendMarkdownDelta(text);
        } else {
            view.setMarkdown(text);
        }
        if (!streaming && view.isStreaming()) {
            view.finishStreaming();
        }
    }

    /** Die Quellenliste unter der Antwort eines Eintrags, oder {@code null}, wenn er keine Quellen hat. */
    public SourceListPanel sourcesFor(long entryId) {
        BubbleMessageRow row = sourceRows.get(entryId);
        return row == null ? null : (SourceListPanel) row.getBubble();
    }

    static String displayText(TranscriptEntry entry) {
        String text = entry.getText();
        if (entry.getAuthor() == TranscriptEntry.Author.USER && !entry.getAttachments().isEmpty()) {
            StringBuilder withAttachments = new StringBuilder(text).append('\n');
            for (String name : entry.getAttachments()) {
                withAttachments.append('\n').append(ATTACHMENT_PREFIX).append(name);
            }
            return withAttachments.toString();
        }
        switch (entry.getState()) {
            case STREAMING:
                if (!text.isEmpty()) {
                    return text;
                }
                return entry.getActivity().isEmpty() ? STREAMING_PLACEHOLDER : entry.getActivity();
            case FAILED:
                String headline = failureHeadline(entry.getFailureMessage());
                return text.isEmpty() ? headline : text + "\n\n" + headline;
            default:
                return text;
        }
    }

    /** Die erste Zeile der Fehlermeldung: die Überschrift, die in der Blase steht. */
    static String failureHeadline(String failureMessage) {
        String message = failureMessage == null ? "" : failureMessage.trim();
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline).trim();
    }

    /** Alles nach der ersten Zeile („Technische Ursache: …“, „Hinweis: …“): die aufklappbaren Details. */
    static String failureDetails(String failureMessage) {
        String message = failureMessage == null ? "" : failureMessage.trim();
        int newline = message.indexOf('\n');
        return newline < 0 ? "" : message.substring(newline + 1).trim();
    }

    static String header(TranscriptEntry entry) {
        if (entry.getAuthor() == TranscriptEntry.Author.USER) {
            return USER_HEADER;
        }
        if (entry.getAuthor() == TranscriptEntry.Author.NOTICE) {
            return NOTICE_HEADER;
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

    private RowState createRow(TranscriptEntry entry) {
        boolean user = entry.getAuthor() == TranscriptEntry.Author.USER;
        boolean notice = entry.getAuthor() == TranscriptEntry.Author.NOTICE;
        boolean failed = entry.getState() == TranscriptEntry.State.FAILED;
        BubbleSide side = user ? BubbleSide.RIGHT : BubbleSide.LEFT;
        String header = header(entry);
        String text = displayText(entry);
        if (!user && !notice && !failed) {
            AssistantMarkdownBubble bubble = createMarkdownBubble(entry, header, text);
            bubble.setHeaderTimestamp(entry.getCreatedAtMillis());
            return new RowState(new BubbleMessageRow(bubble, side), bubble, header, text.length(),
                    !entry.getText().isEmpty());
        }
        Color background;
        Color foreground;
        if (user) {
            background = bubblePalette.getUserBackground();
            foreground = bubblePalette.getUserForeground();
        } else if (notice) {
            background = bubblePalette.getActivityBackground();
            foreground = bubblePalette.getActivityForeground();
        } else {
            background = bubblePalette.getFailureAccent();
            foreground = Color.WHITE;
        }
        SpeechBubblePanel bubble = new SpeechBubblePanel(side, background, foreground, header, text);
        bubble.setHeaderTimestamp(entry.getCreatedAtMillis());
        if (failed) {
            bubble.setDetailsLabels(SHOW_DETAILS_LABEL, HIDE_DETAILS_LABEL);
            bubble.setDetails(failureDetails(entry.getFailureMessage()));
        }
        return new RowState(new BubbleMessageRow(bubble, side), bubble, header, text.length(),
                !entry.getText().isEmpty());
    }

    /** Eine Antwortblase mit Markdown-Körper; eine laufende Antwort beginnt im Streaming-Modus (gedrosselt gerendert). */
    private AssistantMarkdownBubble createMarkdownBubble(TranscriptEntry entry, String header, String text) {
        MarkdownMessageView view = new MarkdownMessageView(assistantTheme, linkOpener, mermaidImageRenderer);
        if (entry.getState() == TranscriptEntry.State.STREAMING) {
            view.startStreaming();
            view.appendMarkdownDelta(text);
        } else {
            view.setMarkdown(text);
        }
        return new AssistantMarkdownBubble(BubbleSide.LEFT, bubblePalette, header, view);
    }

    /** Hängt die Quellenliste als eigene, links ausgerichtete Zeile direkt unter die Blase des Eintrags. */
    private void addSourcesRow(TranscriptEntry entry) {
        BubbleMessageRow answerRow = rows.get(entry.getId()).row;
        SourceListPanel panel = new SourceListPanel(entry.getSources(), comicPalette, bubblePalette);
        BubbleMessageRow row = new BubbleMessageRow(panel, BubbleSide.LEFT);
        row.setAlignmentX(LEFT_ALIGNMENT);
        int index = messageList.getComponentZOrder(answerRow) + 2; // hinter Blase und Abstand
        messageList.add(row, index);
        messageList.add(spacer(), index + 1);
        sourceRows.put(entry.getId(), row);
    }

    private static JComponent spacer() {
        JComponent spacer = (JComponent) Box.createVerticalStrut(2);
        spacer.setAlignmentX(LEFT_ALIGNMENT);
        return spacer;
    }

    private void replaceRow(RowState old, TranscriptEntry entry) {
        int index = messageList.getComponentZOrder(old.row);
        RowState replacement = createRow(entry);
        replacement.row.setAlignmentX(LEFT_ALIGNMENT);
        messageList.remove(index);
        messageList.add(replacement.row, index);
        rows.put(entry.getId(), replacement);
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

    /** Eine Zeile des Verlaufs mit dem, was ihre Blase gerade zeigt (für das Anhängen von Deltas). */
    private static final class RowState {
        final BubbleMessageRow row;
        final TranscriptBubble bubble;
        String header;
        int shownLength;
        boolean showsEntryText; // Blase zeigt den Eintragstext (nicht Platzhalter oder Aktivität)

        RowState(BubbleMessageRow row, TranscriptBubble bubble, String header, int shownLength,
                 boolean showsEntryText) {
            this.row = row;
            this.bubble = bubble;
            this.header = header;
            this.shownLength = shownLength;
            this.showsEntryText = showsEntryText;
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
