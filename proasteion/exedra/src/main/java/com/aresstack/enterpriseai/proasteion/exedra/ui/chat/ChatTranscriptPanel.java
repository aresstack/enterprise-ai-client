package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DiagramOverlayHost;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownTheme;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MermaidImageRenderer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MermaidViewerPanel;
import com.aresstack.enterpriseai.ui.comic.bubble.AgentActivityBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.AnimatedThoughtBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.AssistantThinkingBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleMessageRow;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.BubbleSide;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.bubble.TranscriptBubble;
import com.aresstack.enterpriseai.ui.comic.control.ComicOverlayPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ReadAloudOrb;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Der Chat-Verlauf als Sprechblasen im AskAI-Stil: Nutzer rechts (blau), Assistent links (petrol), Fehler
 * links in der Fehlerfarbe; Hinweise der Anwendung und der leere Verlauf sind zentrierte kursive Infozeilen ohne
 * Blase wie in arch. Folgt ausschließlich dem {@link ChatShellModel}; Streaming-Deltas aktualisieren die vorhandene
 * Blase, statt neue anzulegen.
 *
 * <p>Antworten des Assistenten sind Markdown-Blasen ({@link AssistantMarkdownBubble} mit
 * {@link MarkdownMessageView}, wie askai-java8 {@code arch}): Überschriften, Listen, Tabellen, Code mit
 * Kopieraktion, Links und Mermaid-Diagramme (als Bild, Klick öffnet den Betrachter) werden nativ gerendert;
 * {@code ```markdown}-Umhüllungen fallen weg. Nutzer und Fehler bleiben Text-Sprechblasen.
 *
 * <p>Hat eine Nutzernachricht Anhänge, stehen sie als Chips (Dateiname) in einer eigenen Zeile unter ihrer Blase,
 * nach dem Senden wie nach dem Laden eines gespeicherten Chats.
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
 * <p>Mit einer {@link ReadAloudControl} liegt oben rechts über dem Verlauf der Play/Pause-Orb aus askai-java8
 * {@code arch} ({@code ResearchOutOfScopeSky}, „Gemini-artig“): Play liest die letzte Antwort vor und bleibt aktiv,
 * jede neue live gestreamte Antwort wird dann automatisch vorgelesen, bis Pause es beendet. Ist automatisches
 * Vorlesen eingestellt, ist der Orb von Anfang an aktiv; beim Laden gespeicherter Chats wird nichts vorgelesen.
 * Ohne verfügbare Sprachausgabe bleibt der Orb deaktiviert und nennt im Tooltip den Grund.
 *
 * <p>Arbeitsschritte des Agenten ({@link TranscriptEntry.Author#ACTIVITY}) sind die animierten Gedankenwolken aus
 * askai-java8 {@code arch}: Werkzeugaufrufe gelb ({@link AgentActivityBubblePanel}), Denkschritte des Modells grün
 * ({@link AssistantThinkingBubblePanel}). Ist ein Schritt beendet, platzt die Wolke und ihre Ergebniszeile steigt auf;
 * danach bleibt von einem Werkzeugaufruf eine schmale Ergebniszeile in seiner Farbe (gelb, bei Fehler rot) stehen,
 * ein Denkschritt verschwindet wie in arch.
 *
 * <p>Zeilenlayout, Blasenbreite und Zeilenhöhe kommen unverändert aus der Comic-Bibliothek
 * ({@link BubbleMessageRow}), die Breitenführung durch den Viewport aus AskAIs {@code BubbleTranscriptPanel}.
 */
public final class ChatTranscriptPanel extends JPanel implements ChatShellModelListener, DiagramOverlayHost {

    static final String USER_HEADER = "Du";
    static final String ASSISTANT_HEADER = "Assistent";
    static final String CANCELLED_SUFFIX = " · abgebrochen";
    static final String FAILED_HEADER = "Fehler";
    static final String NOTICE_HEADER = "Hinweis";
    /** Die Infozeile eines leeren Verlaufs (askai arch {@code showEmptyState}). */
    static final String EMPTY_STATE_TEXT = "Neue Unterhaltung. Nachricht unten eingeben und Enter drücken.";
    static final String STREAMING_PLACEHOLDER = "…";
    static final String SHOW_DETAILS_LABEL = "Details anzeigen";
    static final String HIDE_DETAILS_LABEL = "Details ausblenden";
    static final String READ_ALOUD_PLAY_TOOLTIP =
            "Letzte Antwort vorlesen (neue Antworten werden automatisch vorgelesen)";
    static final String READ_ALOUD_PAUSE_TOOLTIP = "Vorlesen pausieren";
    static final String READ_ALOUD_UNAVAILABLE_TOOLTIP = "Vorlesen nicht verfügbar";

    /**
     * Client-Property, die eine durchsichtige Ebene über dem Verlauf (der Wolkenhimmel des Agent-Modus) an sich
     * setzt: so viel Platz braucht der Verlauf oben, damit die erste Nachricht bei Bildlauf 0 lesbar bleibt
     * (askai-java8 {@code ComposerAccessory.TRANSCRIPT_TOP_INSET_PROPERTY}).
     */
    public static final String TRANSCRIPT_TOP_INSET_PROPERTY = "enterpriseai.transcriptTopInset";

    /** Zwischen Verlauf (DEFAULT) und Orb (PALETTE), wie in askai-java8. */
    private static final Integer TRANSCRIPT_SKY_LAYER = Integer.valueOf(50);
    private static final String CLOSE_OVERLAY_ACTION = "closeTranscriptOverlay";

    private static final int BASE_PADDING_TOP = 10;
    private static final int BASE_PADDING_BOTTOM = 10;

    /** Abstand des Orbs vom oberen und rechten Rand des Verlaufs. */
    private static final int ORB_INSET = 10;

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
    private final JLabel emptyState = new JLabel(EMPTY_STATE_TEXT, SwingConstants.CENTER);
    private final ComicScrollPane scrollPane;
    private final Map<Long, RowState> rows = new HashMap<Long, RowState>();
    private final Map<Long, BubbleMessageRow> sourceRows = new HashMap<Long, BubbleMessageRow>();
    private final Map<Long, TranscriptEntry> pending = new LinkedHashMap<Long, TranscriptEntry>();
    private final ReadAloudOrb readAloudOrb = new ReadAloudOrb();
    private final JLayeredPane layers = new JLayeredPane();
    private JComponent transcriptSkyAccessory;
    private ComicOverlayPanel transcriptOverlay;
    private int topInset;
    /** Beobachtet die Inset-Property der Ebene (an ihr und ihren direkten Kindern). */
    private final PropertyChangeListener skyInsetListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            applyTranscriptTopInsetFromSky();
        }
    };
    private ReadAloudControl readAloud;
    private boolean readAloudActive;
    private String latestAnswer;
    private final Timer flushTimer;
    private long lastFlushNanos;
    private boolean flushedBefore;
    private int flushCount;
    /** Wohin der Kopierknopf einer Blase schreibt (Systemzwischenablage; Tests setzen einen Ersatz). */
    private Consumer<String> clipboard = new Consumer<String>() {
        @Override
        public void accept(String text) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    };

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
        messageList.setBorder(BorderFactory.createEmptyBorder(BASE_PADDING_TOP, 0, BASE_PADDING_BOTTOM, 0));
        scrollPane = new ComicScrollPane(messageList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, comicPalette);
        scrollPane.getViewport().setBackground(bubblePalette.getTranscriptBackground());
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        layers.setLayout(new OrbOverlayLayout());
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                layers.doLayout(); // Bildlaufleiste erscheint oder verschwindet: Orb bleibt links daneben
            }
        });
        layers.add(scrollPane, JLayeredPane.DEFAULT_LAYER);
        layers.add(readAloudOrb, JLayeredPane.PALETTE_LAYER);
        readAloudOrb.setVisible(false);
        readAloudOrb.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleReadAloud();
            }
        });
        add(layers, BorderLayout.CENTER);
        flushTimer = new Timer(FLUSH_INTERVAL_MILLIS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                flushPendingUpdates();
            }
        });
        flushTimer.setRepeats(false);
        emptyState.setName("transcript.emptyState");
        styleInfoLine(emptyState);
        messageList.add(emptyState);
        for (TranscriptEntry entry : model.getEntries()) {
            entryAdded(entry);
        }
        model.addListener(this);
    }

    @Override
    public void entryRemoved(TranscriptEntry entry) {
        flushPendingUpdates();
        pending.remove(entry.getId());
        RowState state = rows.remove(entry.getId());
        if (state == null) {
            return;
        }
        if (state.thought != null) {
            state.thought.stopAnimation();
        }
        removeRowAndSpacer(state.row);
        BubbleMessageRow sources = sourceRows.remove(entry.getId());
        if (sources != null) {
            removeRowAndSpacer(sources);
        }
        if (messageList.getComponentCount() == 0) {
            messageList.add(emptyState);
        }
        refresh(false);
    }

    private void removeRowAndSpacer(JComponent row) {
        int index = messageList.getComponentZOrder(row);
        if (index < 0) {
            return;
        }
        messageList.remove(index);
        if (index < messageList.getComponentCount()
                && !(messageList.getComponent(index) instanceof BubbleMessageRow)
                && !(messageList.getComponent(index) instanceof JLabel)) {
            messageList.remove(index); // der Abstand hinter der Zeile
        }
    }

    /** Die Gedankenwolke eines laufenden Arbeitsschritts (für Tests), sonst {@code null}. */
    AnimatedThoughtBubblePanel thoughtFor(long entryId) {
        RowState state = rows.get(entryId);
        return state == null ? null : state.thought;
    }

    /** Die Ergebniszeile eines beendeten Werkzeugaufrufs (für Tests), sonst {@code null}. */
    JLabel activityLineFor(long entryId) {
        RowState state = rows.get(entryId);
        return state == null || state.thought != null || !(state.row instanceof JLabel) ? null : (JLabel) state.row;
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        flushPendingUpdates();
        messageList.remove(emptyState);
        RowState state = createRow(entry);
        state.streamed = entry.getState() == TranscriptEntry.State.STREAMING;
        rows.put(entry.getId(), state);
        state.row.setAlignmentX(LEFT_ALIGNMENT);
        messageList.add(state.row);
        messageList.add(spacer());
        if (entry.getAuthor() == TranscriptEntry.Author.USER
                && (!entry.getAttachments().isEmpty() || !entry.getPages().isEmpty())) {
            addAttachmentsRow(entry);
        }
        if (entry.hasSources()) {
            addSourcesRow(entry);
        }
        if (isCompleteAnswer(entry)) {
            latestAnswer = entry.getText(); // gezeigt oder geladen, aber nie von selbst vorgelesen
        }
        refresh(true);
    }

    /**
     * Zeigt Inhalt als schließbares Comic-Overlay über dem Verlauf (dem „Antwortfenster“), etwa den
     * Mermaid-Betrachter oder eine Mindmap; ersetzt ein vorheriges Overlay, das ✕ oben rechts oder Esc schließt es.
     * Aus askai-java8 {@code OllamaChatPanel.showTranscriptOverlay}. Nur auf dem EDT.
     */
    @Override
    public void showTranscriptOverlay(JComponent content, String title) {
        closeTranscriptOverlay();
        transcriptOverlay = new ComicOverlayPanel(title, content, new Runnable() {
            @Override
            public void run() {
                closeTranscriptOverlay();
            }
        }, comicPalette);
        transcriptOverlay.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE_OVERLAY_ACTION);
        transcriptOverlay.getActionMap().put(CLOSE_OVERLAY_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                closeTranscriptOverlay();
            }
        });
        layers.add(transcriptOverlay, JLayeredPane.MODAL_LAYER);
        layers.revalidate();
        layers.repaint();
    }

    /** Zeigt einen Mermaid-Quelltext im vollen Betrachter ({@link MermaidViewerPanel}) als Overlay. */
    @Override
    public void showDiagramOverlay(String mermaidSource, String title) {
        showTranscriptOverlay(new MermaidViewerPanel(mermaidSource, mermaidImageRenderer), title);
    }

    /** Schließt das Overlay über dem Verlauf (ohne Overlay nichts). Nur auf dem EDT. */
    @Override
    public void closeTranscriptOverlay() {
        if (transcriptOverlay != null) {
            layers.remove(transcriptOverlay);
            transcriptOverlay = null;
            layers.revalidate();
            layers.repaint();
        }
    }

    /**
     * Legt eine DURCHSICHTIGE Ebene über den Verlauf (den Wolkenhimmel des Agent-Modus aus askai-java8) und ersetzt
     * eine vorherige. Die Ebene füllt den Verlauf und beansprucht über {@code contains} nur ihre bedienbare Zone, so
     * dass Klicks und Bildlauf darunter weiter beim Verlauf ankommen; den nötigen Platz oben meldet sie über
     * {@link #TRANSCRIPT_TOP_INSET_PROPERTY}. Aus askai-java8 {@code OllamaChatPanel.setTranscriptSkyAccessory}.
     * Nur auf dem EDT.
     */
    public void setTranscriptSkyAccessory(JComponent accessory) {
        clearTranscriptSkyAccessory();
        if (accessory != null) {
            transcriptSkyAccessory = accessory;
            layers.add(accessory, TRANSCRIPT_SKY_LAYER);
            accessory.addPropertyChangeListener(TRANSCRIPT_TOP_INSET_PROPERTY, skyInsetListener);
            for (Component child : accessory.getComponents()) {
                if (child instanceof JComponent) {
                    ((JComponent) child).addPropertyChangeListener(TRANSCRIPT_TOP_INSET_PROPERTY,
                            skyInsetListener);
                }
            }
            applyTranscriptTopInsetFromSky();
            layers.revalidate();
            layers.repaint();
        }
    }

    /** Entfernt die Ebene über dem Verlauf (ohne Ebene nichts). Nur auf dem EDT. */
    public void clearTranscriptSkyAccessory() {
        if (transcriptSkyAccessory != null) {
            transcriptSkyAccessory.removePropertyChangeListener(TRANSCRIPT_TOP_INSET_PROPERTY, skyInsetListener);
            for (Component child : transcriptSkyAccessory.getComponents()) {
                if (child instanceof JComponent) {
                    ((JComponent) child).removePropertyChangeListener(TRANSCRIPT_TOP_INSET_PROPERTY,
                            skyInsetListener);
                }
            }
            layers.remove(transcriptSkyAccessory);
            transcriptSkyAccessory = null;
            setTopInset(0); // ohne Himmel bekommt der Verlauf seine volle Höhe zurück
            layers.revalidate();
            layers.repaint();
        }
    }

    /** Der größte gemeldete Platzbedarf der Ebene und ihrer direkten Kinder. */
    private void applyTranscriptTopInsetFromSky() {
        int inset = 0;
        if (transcriptSkyAccessory != null) {
            inset = insetProperty(transcriptSkyAccessory);
            for (Component child : transcriptSkyAccessory.getComponents()) {
                if (child instanceof JComponent) {
                    inset = Math.max(inset, insetProperty((JComponent) child));
                }
            }
        }
        setTopInset(inset);
    }

    private static int insetProperty(JComponent component) {
        Object value = component.getClientProperty(TRANSCRIPT_TOP_INSET_PROPERTY);
        return value instanceof Integer ? Math.max(0, (Integer) value) : 0;
    }

    /**
     * Zusätzlicher Platz oben IN der Bildlauf-Geometrie (unter dem Wolkenhimmel): ganz nach oben gescrollt beginnt
     * die erste Nachricht unterhalb der deckenden Ebene; {@code 0} stellt den normalen Rand wieder her.
     */
    void setTopInset(int pixels) {
        int inset = Math.max(0, pixels);
        if (inset == topInset) {
            return;
        }
        topInset = inset;
        messageList.setBorder(BorderFactory.createEmptyBorder(BASE_PADDING_TOP + topInset, 0, BASE_PADDING_BOTTOM, 0));
        messageList.revalidate();
        messageList.repaint();
    }

    /**
     * Schließt die Sprachausgabe an und zeigt den Play/Pause-Orb; mit automatischem Vorlesen ist er sofort aktiv.
     * Einmal je Verlauf (UI-Thread).
     */
    public void setReadAloud(ReadAloudControl control) {
        if (control == null || readAloud != null) {
            throw new IllegalStateException("read-aloud control must be set exactly once");
        }
        readAloud = control;
        readAloudActive = control.isAvailable() && control.autoStart();
        readAloudOrb.setVisible(true);
        styleReadAloudOrb();
        control.addListener(new ReadAloudControl.Listener() {
            @Override
            public void readAloudChanged() {
                if (!readAloud.isAvailable()) {
                    readAloudActive = false; // neue Stimme fehlt: der Orb fällt auf Play zurück
                }
                styleReadAloudOrb();
            }
        });
    }

    /** Der Bildlaufbereich des Verlaufs (für Tests). */
    ComicScrollPane scrollPane() {
        return scrollPane;
    }

    /** Der Play/Pause-Orb (für Tests). */
    ReadAloudOrb readAloudOrb() {
        return readAloudOrb;
    }

    /** Play liest die letzte Antwort sofort vor und bleibt aktiv; Pause beendet die laufende Ausgabe. */
    private void toggleReadAloud() {
        if (readAloud == null || !readAloud.isAvailable()) {
            return;
        }
        if (readAloudActive) {
            readAloudActive = false;
            readAloud.stop();
        } else {
            readAloudActive = true;
            if (latestAnswer != null) {
                readAloud.speak(latestAnswer);
            }
        }
        styleReadAloudOrb();
    }

    private void styleReadAloudOrb() {
        readAloudOrb.setEnabled(readAloud.isAvailable());
        readAloudOrb.setActive(readAloudActive);
        String tooltip = !readAloud.isAvailable() ? READ_ALOUD_UNAVAILABLE_TOOLTIP
                : readAloudActive ? READ_ALOUD_PAUSE_TOOLTIP : READ_ALOUD_PLAY_TOOLTIP;
        readAloudOrb.setToolTipText(tooltip + " (" + readAloud.description() + ")");
    }

    private static boolean isCompleteAnswer(TranscriptEntry entry) {
        return entry.getAuthor() == TranscriptEntry.Author.ASSISTANT
                && entry.getState() == TranscriptEntry.State.COMPLETE && !entry.getText().trim().isEmpty();
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

    /** Die Infozeile eines Hinweises (für Tests), oder {@code null}. */
    JLabel noticeLineFor(long entryId) {
        RowState state = rows.get(entryId);
        return state == null || state.bubble != null ? null : (JLabel) state.row;
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
        latestAnswer = null;
        if (readAloud != null) {
            readAloud.stop(); // die Stimme überlebt ihren Chat nicht
        }
        messageList.removeAll();
        messageList.add(emptyState);
        refresh(false);
    }

    /**
     * Die Blase eines Eintrags (für Tests und spätere Kontextaktionen), oder {@code null} (auch für Hinweiszeilen):
     * eine {@link SpeechBubblePanel} für Nutzer und Fehler, sonst die Markdown-Blase des Assistenten.
     */
    public TranscriptBubble bubbleFor(long entryId) {
        RowState state = rows.get(entryId);
        return state == null ? null : state.bubble;
    }

    /** Bringt die Blase eines Eintrags auf seinen aktuellen Stand; Streaming-Text wird nur angehängt. */
    private void apply(TranscriptEntry entry) {
        RowState state = rows.get(entry.getId());
        if (state != null && entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
            applyActivity(state, entry);
            return;
        }
        if (state == null || state.bubble == null) {
            return; // Hinweiszeilen ändern sich nicht
        }
        state.entry = entry;
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
        boolean finishedLive = state.streamed && entry.getState() != TranscriptEntry.State.STREAMING;
        state.streamed = entry.getState() == TranscriptEntry.State.STREAMING;
        if (finishedLive && isCompleteAnswer(entry)) {
            latestAnswer = entry.getText();
            if (readAloudActive) {
                readAloud.speak(latestAnswer); // eine neue Antwort, während Vorlesen aktiv ist
            }
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

    /**
     * Läuft der Schritt noch, zeigt die Wolke Titel und Erläuterung; ist er beendet, platzt sie (Erfolg in der
     * Akzentfarbe, Fehler rot) und wird danach zur Ergebniszeile bzw. verschwindet (Denkschritt).
     */
    private void applyActivity(final RowState state, final TranscriptEntry entry) {
        final AnimatedThoughtBubblePanel thought = state.thought;
        if (thought == null) {
            return; // schon zur Ergebniszeile geworden
        }
        if (entry.getState() == TranscriptEntry.State.STREAMING) {
            thought.updateActivity(entry.getActivityTitle(), entry.getText());
            return;
        }
        if (thought.getVisualState() != AnimatedThoughtBubblePanel.VisualState.RUNNING) {
            return;
        }
        Runnable settle = new Runnable() {
            @Override
            public void run() {
                settleActivity(entry);
            }
        };
        String summary = entry.getActivitySummary().isEmpty() ? entry.getActivityTitle() : entry.getActivitySummary();
        if (entry.getState() == TranscriptEntry.State.FAILED) {
            thought.completeWithFailure(summary, settle);
        } else if (entry.getState() == TranscriptEntry.State.CANCELLED) {
            thought.cancel(summary, settle);
        } else {
            thought.completeSuccessfully(summary, settle);
        }
    }

    /** Nach dem Platzen: Denkschritte verschwinden, Werkzeugaufrufe bleiben als schmale Ergebniszeile. */
    private void settleActivity(TranscriptEntry entry) {
        RowState state = rows.get(entry.getId());
        if (state == null || state.thought == null) {
            return;
        }
        if (entry.isThinking() || entry.getState() == TranscriptEntry.State.CANCELLED) {
            rows.remove(entry.getId());
            removeRowAndSpacer(state.row);
        } else {
            replaceRow(state, entry);
        }
        refresh(isNearBottom());
    }

    /** Die schmale Ergebniszeile eines beendeten Werkzeugaufrufs: „Titel „Eingabe“: Ergebnis“ in Wolkenfarbe. */
    private JLabel activityLine(TranscriptEntry entry) {
        StringBuilder text = new StringBuilder(entry.getActivityTitle());
        // Die Recherche-Karte trägt mehrere Zeilen und einen Balken-Marker; als Zeile bleibt nur ihre Bilanz.
        String input = "research".equals(entry.getActivityKind()) ? ""
                : entry.getText().replaceAll("\\s*\\[\\[bar:\\d+/\\d+\\]\\]\\s*", " ").trim();
        if (!input.isEmpty()) {
            int newline = input.indexOf('\n');
            text.append(" \u201E").append(newline < 0 ? input : input.substring(0, newline)).append("\u201C");
        }
        if (!entry.getActivitySummary().isEmpty()) {
            text.append(": ").append(entry.getActivitySummary());
        }
        JLabel line = new JLabel(text.toString(), SwingConstants.LEFT);
        line.putClientProperty("html.disable", Boolean.TRUE);
        line.setName("transcript.activityLine");
        line.setFont(line.getFont().deriveFont(Font.BOLD, Math.max(11f, line.getFont().getSize2D() - 1f)));
        boolean failed = entry.getState() == TranscriptEntry.State.FAILED;
        line.setForeground(failed ? bubblePalette.getFailureAccent()
                : bubblePalette.getActivityAccent().darker());
        line.setBorder(BorderFactory.createEmptyBorder(2, 22, 2, 12));
        line.setAlignmentX(LEFT_ALIGNMENT);
        line.setToolTipText(text.toString());
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
        return line;
    }

    private RowState createRow(TranscriptEntry entry) {
        if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY) {
            if (entry.getState() != TranscriptEntry.State.STREAMING) {
                JLabel line = activityLine(entry);
                return new RowState(line, null, "", 0, true);
            }
            AnimatedThoughtBubblePanel thought = entry.isThinking()
                    ? new AssistantThinkingBubblePanel(BubbleSide.LEFT, bubblePalette, entry.getActivityTitle(),
                    entry.getText())
                    : new AgentActivityBubblePanel(BubbleSide.LEFT, bubblePalette, entry.getActivityTitle(),
                    entry.getText());
            thought.setHeaderTimestamp(entry.getCreatedAtMillis());
            RowState state = new RowState(new BubbleMessageRow(thought, BubbleSide.LEFT), null, "", 0, true);
            state.thought = thought;
            return state;
        }
        boolean user = entry.getAuthor() == TranscriptEntry.Author.USER;
        boolean notice = entry.getAuthor() == TranscriptEntry.Author.NOTICE;
        boolean failed = entry.getState() == TranscriptEntry.State.FAILED;
        BubbleSide side = user ? BubbleSide.RIGHT : BubbleSide.LEFT;
        String header = header(entry);
        String text = displayText(entry);
        if (notice) {
            JLabel line = infoLine(text);
            return new RowState(line, null, header, text.length(), true);
        }
        if (!user && !failed) {
            AssistantMarkdownBubble bubble = createMarkdownBubble(entry, header, text);
            bubble.setHeaderTimestamp(entry.getCreatedAtMillis());
            bubble.setHeaderAccessory(copyButton(entry, bubblePalette.getAssistantForeground()));
            return withEntry(new RowState(new BubbleMessageRow(bubble, side), bubble, header, text.length(),
                    !entry.getText().isEmpty()), entry);
        }
        Color background;
        Color foreground;
        if (user) {
            background = bubblePalette.getUserBackground();
            foreground = bubblePalette.getUserForeground();
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
        bubble.setHeaderAccessory(copyButton(entry, foreground));
        return withEntry(new RowState(new BubbleMessageRow(bubble, side), bubble, header, text.length(),
                !entry.getText().isEmpty()), entry);
    }

    private static RowState withEntry(RowState state, TranscriptEntry entry) {
        state.entry = entry;
        return state;
    }

    /**
     * Der Kopierknopf einer persistenten Nachricht (Nutzer, Antwort, Fehler); er liest beim Klick den aktuellen Stand
     * des Eintrags, nicht das Rendering.
     */
    private MessageCopyButton copyButton(TranscriptEntry entry, Color foreground) {
        final long id = entry.getId();
        final TranscriptEntry created = entry;
        return new MessageCopyButton(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 200),
                new java.util.function.Supplier<String>() {
                    @Override
                    public String get() {
                        RowState state = rows.get(id);
                        return copyText(state == null || state.entry == null ? created : state.entry);
                    }
                },
                new Consumer<String>() {
                    @Override
                    public void accept(String text) {
                        clipboard.accept(text);
                    }
                });
    }

    /**
     * Was der Kopierknopf in die Zwischenablage legt: der ursprüngliche Text der Nachricht (bei Antworten der
     * Markdown-Quelltext), ohne Kopfzeile, Zeitstempel und Quellenleiste; bei einem Fehler zusätzlich die ganze
     * Fehlermeldung.
     */
    static String copyText(TranscriptEntry entry) {
        String text = entry.getText();
        if (entry.getState() == TranscriptEntry.State.FAILED) {
            String failure = entry.getFailureMessage() == null ? "" : entry.getFailureMessage().trim();
            return text.trim().isEmpty() ? failure : failure.isEmpty() ? text : text + "\n\n" + failure;
        }
        return text;
    }

    /** Leitet die Kopieraktion der Blasen um (Tests ohne Systemzwischenablage). */
    void setClipboard(Consumer<String> clipboard) {
        this.clipboard = clipboard;
    }

    /** Der Kopierknopf in der Kopfzeile einer Blase (für Tests), oder {@code null}. */
    MessageCopyButton copyButtonFor(long entryId) {
        RowState state = rows.get(entryId);
        return state == null || state.bubble == null ? null
                : findCopyButton((java.awt.Container) state.bubble);
    }

    private static MessageCopyButton findCopyButton(java.awt.Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof MessageCopyButton) {
                return (MessageCopyButton) child;
            }
            if (child instanceof java.awt.Container) {
                MessageCopyButton nested = findCopyButton((java.awt.Container) child);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
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
        JComponent answerRow = rows.get(entry.getId()).row;
        SourceListPanel panel = new SourceListPanel(entry.getSources(), comicPalette, bubblePalette);
        BubbleMessageRow row = new BubbleMessageRow(panel, BubbleSide.LEFT);
        row.setAlignmentX(LEFT_ALIGNMENT);
        int index = messageList.getComponentZOrder(answerRow) + 2; // hinter Blase und Abstand
        messageList.add(row, index);
        messageList.add(spacer(), index + 1);
        sourceRows.put(entry.getId(), row);
    }

    /** Hängt die Anhang-Chips einer Nutzernachricht als eigene, rechts ausgerichtete Zeile unter ihre Blase. */
    private void addAttachmentsRow(TranscriptEntry entry) {
        AttachmentChipsPanel chips = new AttachmentChipsPanel(entry.getAttachments(), entry.getPages());
        BubbleMessageRow row = new BubbleMessageRow(chips, BubbleSide.RIGHT);
        row.setAlignmentX(LEFT_ALIGNMENT);
        messageList.add(row);
        messageList.add(spacer());
    }

    /**
     * Eine Infozeile wie askai arch ({@code BubbleTranscriptPanel.appendInfo}): zentriert, kursiv, ohne Blase, in der
     * Info-Farbe; so erscheinen Hinweise der Anwendung und der leere Verlauf.
     */
    private JLabel infoLine(String text) {
        JLabel line = new JLabel(text, SwingConstants.CENTER);
        styleInfoLine(line);
        line.putClientProperty("info.plainText", text);
        return line;
    }

    private void styleInfoLine(JLabel line) {
        line.putClientProperty("html.disable", Boolean.TRUE); // Hinweistext ist Klartext
        line.setFont(line.getFont().deriveFont(Font.ITALIC, Math.max(11f, line.getFont().getSize2D() - 1f)));
        line.setForeground(bubblePalette.getInfoForeground());
        line.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        line.setAlignmentX(LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
    }

    private static JComponent spacer() {
        JComponent spacer = (JComponent) Box.createVerticalStrut(2);
        spacer.setAlignmentX(LEFT_ALIGNMENT);
        return spacer;
    }

    private void replaceRow(RowState old, TranscriptEntry entry) {
        if (old.thought != null) {
            old.thought.stopAnimation();
        }
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
        final JComponent row;
        final TranscriptBubble bubble; // null bei einer Hinweiszeile
        String header;
        int shownLength;
        boolean showsEntryText; // Blase zeigt den Eintragstext (nicht Platzhalter oder Aktivität)
        boolean streamed; // zuletzt im Zustand STREAMING gesehen (Ende live erlebt → automatisches Vorlesen)
        AnimatedThoughtBubblePanel thought; // die Wolke eines laufenden Arbeitsschritts, sonst null
        TranscriptEntry entry; // der zuletzt angewandte Stand (Quelle des Kopierknopfs), bei Blasen

        RowState(JComponent row, TranscriptBubble bubble, String header, int shownLength,
                 boolean showsEntryText) {
            this.row = row;
            this.bubble = bubble;
            this.header = header;
            this.shownLength = shownLength;
            this.showsEntryText = showsEntryText;
        }
    }

    /**
     * Legt den Verlauf über die ganze Fläche und den Orb oben rechts darüber (wie askai {@code arch}: der Orb sitzt
     * am rechten Rand über dem Verlauf), links neben einer sichtbaren Bildlaufleiste.
     */
    private final class OrbOverlayLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component component) {
        }

        @Override
        public void removeLayoutComponent(Component component) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return scrollPane.getPreferredSize();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return scrollPane.getMinimumSize();
        }

        @Override
        public void layoutContainer(Container parent) {
            int width = parent.getWidth();
            scrollPane.setBounds(0, 0, width, parent.getHeight());
            scrollPane.doLayout();
            Rectangle viewport = scrollPane.getViewport().getBounds();
            readAloudOrb.setBounds(viewport.x + viewport.width - ORB_INSET - ReadAloudOrb.SIZE,
                    viewport.y + ORB_INSET, ReadAloudOrb.SIZE, ReadAloudOrb.SIZE);
            if (transcriptOverlay != null) {
                transcriptOverlay.setBounds(0, 0, width, parent.getHeight()); // das Overlay deckt den ganzen Verlauf
                transcriptOverlay.doLayout();
            }
            if (transcriptSkyAccessory != null) {
                // Der Himmel liegt über dem Sichtbereich, die Bildlaufleiste bleibt frei.
                transcriptSkyAccessory.setBounds(viewport.x, viewport.y, viewport.width, viewport.height);
                transcriptSkyAccessory.doLayout();
            }
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
