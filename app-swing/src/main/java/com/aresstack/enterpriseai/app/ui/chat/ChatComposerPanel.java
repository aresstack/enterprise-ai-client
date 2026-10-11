package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.control.PlaceholderTextArea;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.JLayeredPane;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Der Composer nach askai-java8 (arch, {@code ChatComposerPanel}): EINE abgerundete Fläche, darin der rahmenlose
 * Editor und eine Fußzeile wie in arch. Links Modell ▾ und Denkaufwand ▾ (beim Diktat dahinter Verwerfen ✕ bzw.
 * „Erneut“), rechts Pegelbalken, Büroklammer, Mikrofon und genau EIN Hauptknopf: „Senden“ (Akzentblau) solange nichts
 * läuft, „Stop“ (Rot) während eine Antwort streamt — nie beide. Die Statuszeile liegt als zentrierte volle Zeile über
 * der Fußzeile. Spracheingabe wie arch: Mikrofon → Aufnahme (Knopf wird zum orangen Stopp-Quadrat, Fläche orange,
 * Wellenform mit ziehbarer Signalschwelle über dem Editor, Pegelbalken) → Stopp erkennt (Knopf wird zum ✕ =
 * Abbrechen); Esc verwirft, Strg+Umschalt+M schaltet um. Die Audiodatei-Abschrift aus arch fehlt (keine
 * Audio-Pipeline). Einen RAG-Schalter gibt es nicht (in arch gab es ihn nie): ob Wissen
 * gesucht wird, folgt aus den aktivierten Wissensquellen. Die Fläche wird weiß und bekommt den blauen Rand,
 * sobald der Editor den Fokus hat oder eine Antwort läuft; Enter sendet, Umschalt+Enter bricht um.
 *
 * <p>Farben und Icons kommen aus {@link ResearchUiPalette} und {@link ComposerIcons}; die wenigen eigenen
 * Töne (Rand und Fläche in Ruhe, Platzhalter) sind die der Referenz.</p>
 */
public final class ChatComposerPanel extends JPanel implements ChatShellModelListener {

    static final String PLACEHOLDER = "Nachricht…";
    static final String SEND_LABEL = "Senden";
    static final String STOP_LABEL = "Stop";
    static final String ATTACH_TOOLTIP = "Dateien anhängen";
    static final String MODEL_PLACEHOLDER = "Modell";
    static final String STREAMING_STATUS = "Antwort wird erstellt …";
    static final String DICTATE_TOOLTIP = "Diktieren (Strg+Umschalt+M)";
    static final String RECORDING_TOOLTIP = "Aufnahme beenden und erkennen";
    static final String CANCEL_TOOLTIP = "Erkennung abbrechen";
    static final String DISCARD_TOOLTIP = "Aufnahme verwerfen (Esc)";
    static final String RETRY_TOOLTIP = "Letzte Aufnahme erneut erkennen";
    static final String RECOGNIZING_STATUS = "Erkenne Sprache …";
    static final String RECOGNIZING_SEND_STATUS = "Erkenne Sprache … danach wird gesendet";
    private static final String SEND_DICTATION_TOOLTIP = "Aufnahme beenden, erkennen und direkt senden";
    static final String RECOGNIZED_STATUS = "Abschrift eingefügt. Text prüfen und senden.";
    static final String EMPTY_STATUS = "Das Modell hat keinen Text geliefert – bitte noch einmal sprechen.";
    static final String DISCARDED_STATUS = "Diktat verworfen.";
    static final String CANCELLED_STATUS = "Erkennung abgebrochen.";
    static final String FAILED_STATUS = "Spracheingabe fehlgeschlagen: ";
    /** Denkaufwand: Anzeige und Wert ({@code null} = Standard des Modells), wie arch „Think: …“. */
    private static final List<String> REASONING_LABELS = Collections.unmodifiableList(Arrays.asList(
            "Denken: Standard", "Denken: niedrig", "Denken: mittel", "Denken: hoch"));
    private static final List<String> REASONING_EFFORTS = Collections.unmodifiableList(Arrays.asList(
            null, "low", "medium", "high"));

    private static final int ARC = 18;
    private static final int MIN_EDITOR_HEIGHT = 62;
    private static final Color BORDER_NORMAL = new Color(0xB8BDC5);
    private static final Color BORDER_FOCUSED = ResearchUiPalette.ACCENT_BLUE;
    private static final Color BACKGROUND_NORMAL = new Color(0xF5F6F7);
    private static final Color BACKGROUND_FOCUSED = Color.WHITE;
    private static final Color BORDER_DICTATION = ResearchUiPalette.RECORDING_ORANGE;
    private static final Color BACKGROUND_DICTATION = ResearchUiPalette.RECORDING_SURFACE;
    private static final int LEVEL_INTERVAL_MILLIS = 100;
    private static final Color PRIMARY = ResearchUiPalette.ACCENT_BLUE;
    private static final Color DANGER = ResearchUiPalette.DANGER_RED;
    private static final String SEND_ACTION = "enterpriseai.send";
    private static final String DICTATION_ACTION = "enterpriseai.dictation";
    private static final String DISCARD_ACTION = "enterpriseai.discard";

    private final ChatShellModel model;
    private final ChatShellActions actions;
    private final ComposerButton modelButton;
    private final ComposerButton reasoningButton;
    private final ComposerStatusLine statusLine = new ComposerStatusLine();
    private final ComposerWaveformOverlay waveform = new ComposerWaveformOverlay();
    private final JProgressBar levelBar = new JProgressBar(0, 100);
    private final Timer levelTimer;
    private final PlaceholderTextArea editor;
    private final ComposerButton sendButton;
    private final ComposerButton stopButton;
    private final ComposerButton attachButton;
    private final ComposerButton micButton;
    private final ComposerButton discardButton;
    private final ComposerButton retryButton;
    private final ChatAttachmentStrip attachmentStrip;
    private Runnable modelAction;
    private Supplier<List<String>> modelChoices;
    private Consumer<String> modelSelected;
    private String reasoningEffort;
    private File lastDirectory;
    private boolean editorFocused;
    private DictationControl dictation;
    private DictationPhase dictationPhase = DictationPhase.IDLE;
    private String dictationNote;
    private ComposerStatusLine.Tone dictationNoteTone = ComposerStatusLine.Tone.MUTED;
    private long recordingStartedMillis;

    private boolean sendAfterRecognition;
    private StatusKind currentStatus;
    private StatusKind dismissedStatus;

    /** Welche Meldung die Statuszeile gerade zeigt (für das ✕). */
    private enum StatusKind {
        STREAMING, RECORDING, RECOGNIZING, ATTACHMENTS, NOTE
    }

    /** Wo das Diktat steht; der Mikrofon-Knopf und die Statuszeile folgen ihm. */
    private enum DictationPhase {
        IDLE, RECORDING, RECOGNIZING
    }

    public ChatComposerPanel(ChatShellModel model, ChatShellActions actions, ComicPalette palette) {
        if (model == null || actions == null || palette == null) {
            throw new IllegalArgumentException("model, actions and palette must not be null");
        }
        this.model = model;
        this.actions = actions;
        this.modelButton = new ComposerButton(ComposerIcons.chevronDown(), MODEL_PLACEHOLDER, false,
                "Chat-Modell (Einstellungen → KI-Dienst)");
        modelButton.setHorizontalTextPosition(SwingConstants.LEFT); // Name zuerst, Chevron danach (arch)
        this.reasoningButton = new ComposerButton(ComposerIcons.chevronDown(), REASONING_LABELS.get(0), false,
                "Denkaufwand (nur für Modelle, die ihn unterstützen)");
        reasoningButton.setHorizontalTextPosition(SwingConstants.LEFT);
        this.editor = new PlaceholderTextArea(PLACEHOLDER, 2, 40);
        this.sendButton = ComposerButton.primary(ComposerIcons.send(), SEND_LABEL, PRIMARY, "Senden (Enter)");
        this.stopButton = ComposerButton.primary(ComposerIcons.stop(), STOP_LABEL, DANGER, "Antwort abbrechen");
        this.attachButton = ComposerButton.iconButton(ComposerIcons.paperclip(), ATTACH_TOOLTIP);
        attachButton.getAccessibleContext().setAccessibleName(ATTACH_TOOLTIP);
        attachButton.setVisible(actions.supportsAttachments());
        this.micButton = ComposerButton.iconButton(ComposerIcons.microphone(), DICTATE_TOOLTIP);
        micButton.getAccessibleContext().setAccessibleName(DICTATE_TOOLTIP);
        micButton.setAccent(BORDER_DICTATION); // während der Aufnahme orange gefüllt (arch RECORDING)
        micButton.setVisible(false);
        this.discardButton = ComposerButton.iconButton(ComposerIcons.close(), DISCARD_TOOLTIP);
        discardButton.getAccessibleContext().setAccessibleName(DISCARD_TOOLTIP);
        discardButton.setVisible(false);
        this.retryButton = new ComposerButton(ComposerIcons.refresh(), "Erneut", false, RETRY_TOOLTIP);
        retryButton.setVisible(false);
        levelBar.setOpaque(false);
        levelBar.setBorderPainted(false);
        levelBar.setPreferredSize(new Dimension(64, 6));
        levelBar.setMaximumSize(new Dimension(64, 6));
        levelBar.setToolTipText("Mikrofonpegel");
        levelBar.setVisible(false);
        this.levelTimer = new Timer(LEVEL_INTERVAL_MILLIS, event -> sampleLevel());
        this.attachmentStrip = new ChatAttachmentStrip(() -> refreshAttachmentState());
        buildUi();
        wireBehaviour();
        model.addListener(this);
        refreshControls();
    }

    @Override
    public void entryAdded(TranscriptEntry entry) {
        // Verlauf ist Sache des Transcripts.
    }

    @Override
    public void entryUpdated(TranscriptEntry entry) {
        // Verlauf ist Sache des Transcripts.
    }

    @Override
    public void stateChanged() {
        refreshControls();
    }

    /** Sendet den Entwurf, wenn das Model es erlaubt; der Editor wird danach geleert. */
    void submit() {
        if (isDictationActive()) {
            // Senden während der Aufnahme: Aufnahme beenden, erkennen und die Nachricht danach direkt abschicken.
            if (model.canStop()) {
                return;
            }
            sendAfterRecognition = true;
            if (dictationPhase == DictationPhase.RECORDING) {
                dictationPhase = DictationPhase.RECOGNIZING;
                stopLevelMeter();
                refreshControls();
                dictation.finish();
            } else {
                refreshControls();
            }
            return;
        }
        String draft = editor.getText();
        if (!model.canSend(draft)) {
            return;
        }
        setDictationNote(null, ComposerStatusLine.Tone.MUTED); // die Diktat-Meldung endet spätestens mit dem Senden
        editor.setText("");
        List<Path> attachments = attachmentStrip.getAttachments();
        attachmentStrip.clear();
        if (attachments.isEmpty()) {
            actions.sendRequested(draft.trim(), model.isRagEnabled());
        } else {
            actions.sendRequested(draft.trim(), model.isRagEnabled(), attachments);
        }
    }

    /** Merkt Dateien für die nächste Nachricht vor (bereits vorgemerkte werden übergangen). */
    public void addAttachments(List<Path> files) {
        attachmentStrip.addAttachments(files);
    }

    /** Die vorgemerkten Anhänge. */
    public List<Path> attachments() {
        return attachmentStrip.getAttachments();
    }

    /** Die Büroklammer (nur sichtbar, wenn die Anbindung Anhänge annimmt). */
    public ComposerButton attachButton() {
        return attachButton;
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle(ATTACH_TOOLTIP);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File[] selected = chooser.getSelectedFiles();
        List<Path> files = new ArrayList<Path>();
        if (selected != null && selected.length > 0) {
            for (File file : selected) {
                files.add(file.toPath());
            }
        } else if (chooser.getSelectedFile() != null) {
            files.add(chooser.getSelectedFile().toPath());
        }
        if (!files.isEmpty()) {
            lastDirectory = files.get(0).toFile().getParentFile();
            attachmentStrip.addAttachments(files);
        }
        editor.requestFocusInWindow();
    }

    private void refreshAttachmentState() {
        refreshControls();
        int count = attachmentStrip.count();
        attachButton.setToolTipText(count > 0 ? ATTACH_TOOLTIP + " (" + count + " vorgemerkt)" : ATTACH_TOOLTIP);
        revalidate();
        repaint();
    }

    /**
     * Blendet Modell- und Denkaufwand-Auswahl aus, z. B. in der Agent-Ansicht, wo der Agent sein Modell selbst
     * wählt.
     */
    public void setModelControlsVisible(boolean visible) {
        modelButton.setVisible(visible);
        reasoningButton.setVisible(visible);
    }

    /** Der Name des Chat-Modells im Modellknopf (leer: „Modell“). */
    public void setModelName(String name) {
        modelButton.setText(name == null || name.trim().isEmpty() ? MODEL_PLACEHOLDER : name.trim());
        modelButton.setToolTipText("Chat-Modell: " + modelButton.getText());
    }

    /** „Modelle verwalten …“ am Ende der Modellliste (die Einstellungen, Reiter „Modelle“). */
    public void setModelAction(Runnable action) {
        this.modelAction = action;
    }

    /**
     * Die Chat-Modelle aus dem Modellkatalog (Kategorie CHAT) für die Liste des Modellknopfs und was eine Wahl
     * bewirkt. Ohne Liste öffnet der Knopf nur „Modelle verwalten …“.
     */
    public void setModelChoices(Supplier<List<String>> choices, Consumer<String> onSelect) {
        this.modelChoices = choices;
        this.modelSelected = onSelect;
    }

    public ComposerButton modelButton() {
        return modelButton;
    }

    public ComposerButton reasoningButton() {
        return reasoningButton;
    }

    /** Der gewählte Denkaufwand ({@code low}, {@code medium}, {@code high}) oder {@code null} für den Standard. */
    public String reasoningEffort() {
        return reasoningEffort;
    }

    /** Wählt einen Denkaufwand aus {@link #REASONING_LABELS} (Index) und meldet ihn der Anbindung. */
    void selectReasoning(int index) {
        reasoningEffort = REASONING_EFFORTS.get(index);
        reasoningButton.setText(REASONING_LABELS.get(index));
        actions.reasoningChanged(reasoningEffort);
        revalidate();
        repaint();
    }

    /** Der Text der Statuszeile (eigene Zeile über dem Composer-Rahmen); leer, wenn nichts zu melden ist. */
    public String statusText() {
        return statusLine.text();
    }

    /** Das Eingabefeld (für Tests, Demo und Composition Root, z. B. um den Fokus zu setzen). */
    public PlaceholderTextArea editor() {
        return editor;
    }

    /** „Senden“: sichtbar, solange keine Antwort läuft; aktiv nur mit sendbarem Entwurf. */
    public ComposerButton sendButton() {
        return sendButton;
    }

    /** „Stop“: sichtbar und aktiv nur, solange eine Antwort läuft. */
    public ComposerButton stopButton() {
        return stopButton;
    }

    /**
     * Bindet die Spracheingabe: der Mikrofon-Knopf erscheint, ist aber nur aktiv, wenn ein STT-Modell nutzbar ist
     * (sonst nennt sein Tooltip den Grund). Einmal je Composer; auf dem EDT.
     */
    public void setDictation(DictationControl control) {
        if (control == null || dictation != null) {
            return;
        }
        dictation = control;
        micButton.setVisible(true);
        control.addListener(new DictationControl.Listener() {
            @Override
            public void dictationChanged() {
                refreshControls();
            }
        });
        refreshControls();
        revalidate();
    }

    public ComposerButton micButton() {
        return micButton;
    }

    /** Verwerfen ✕ (nur während der Aufnahme sichtbar, wie arch). */
    public ComposerButton discardButton() {
        return discardButton;
    }

    /** „Erneut“: nach einem Fehlschlag die letzte Aufnahme noch einmal erkennen. */
    public ComposerButton retryButton() {
        return retryButton;
    }

    /** Mikrofon wie arch: in Ruhe aufnehmen, während der Aufnahme beenden und erkennen, während der Erkennung abbrechen. */
    private void toggleDictation() {
        if (dictation == null) {
            return;
        }
        if (dictationPhase == DictationPhase.RECORDING) {
            dictationPhase = DictationPhase.RECOGNIZING;
            stopLevelMeter();
            refreshControls();
            dictation.finish();
            return;
        }
        if (dictationPhase == DictationPhase.RECOGNIZING) {
            discardDictation();
            return;
        }
        if (!dictation.isAvailable() || model.canStop()) {
            return;
        }
        setDictationNote(null, ComposerStatusLine.Tone.MUTED);
        dictationPhase = DictationPhase.RECORDING;
        startLevelMeter();
        refreshControls();
        dictation.start(dictationResult());
    }

    /** Esc bzw. ✕: Aufnahme verwerfen oder laufende Erkennung abbrechen, ohne Ergebnis. */
    private void discardDictation() {
        if (dictation == null || dictationPhase == DictationPhase.IDLE) {
            return;
        }
        boolean recording = dictationPhase == DictationPhase.RECORDING;
        sendAfterRecognition = false;
        dictation.cancel();
        dictationPhase = DictationPhase.IDLE;
        stopLevelMeter();
        setDictationNote(recording ? DISCARDED_STATUS : CANCELLED_STATUS, ComposerStatusLine.Tone.MUTED);
        refreshControls();
    }

    private void retryDictation() {
        if (dictation == null || dictationPhase != DictationPhase.IDLE || !dictation.canRetry() || model.canStop()) {
            return;
        }
        setDictationNote(null, ComposerStatusLine.Tone.MUTED);
        dictationPhase = DictationPhase.RECOGNIZING;
        refreshControls();
        dictation.retry(dictationResult());
    }

    private DictationControl.Result dictationResult() {
        return new DictationControl.Result() {
            @Override
            public void recognized(String text) {
                dictationPhase = DictationPhase.IDLE;
                stopLevelMeter();
                boolean send = sendAfterRecognition;
                sendAfterRecognition = false;
                if (text == null || text.trim().isEmpty()) {
                    setDictationNote(EMPTY_STATUS, ComposerStatusLine.Tone.MUTED);
                } else {
                    DictationInsertion insertion = DictationInsertion.insert(editor.getText(),
                            editor.getSelectionStart(), editor.getSelectionEnd(), text);
                    editor.setText(insertion.text);
                    editor.setCaretPosition(insertion.caret);
                    editor.requestFocusInWindow();
                    setDictationNote(RECOGNIZED_STATUS, ComposerStatusLine.Tone.MUTED);
                    if (send) {
                        refreshControls();
                        submit();
                        return;
                    }
                }
                refreshControls();
            }

            @Override
            public void failed(String message) {
                dictationPhase = DictationPhase.IDLE;
                sendAfterRecognition = false; // bei einem Fehler wird nichts gesendet
                stopLevelMeter();
                String reason = message == null || message.trim().isEmpty() ? "unbekannter Fehler" : message.trim();
                setDictationNote(FAILED_STATUS + reason, ComposerStatusLine.Tone.ERROR);
                refreshControls();
                showDictationFailure(reason);
            }
        };
    }

    private void setDictationNote(String note, ComposerStatusLine.Tone tone) {
        dictationNote = note;
        dictationNoteTone = tone;
    }

    /**
     * Ein Fehler der Spracheingabe verpufft nicht im Protokoll (wie das Vorlesen seit 0.1.29): Meldung mit dem Grund
     * und dem gewählten Modell, umbrochen, damit auch lange Laufzeit-Fehler lesbar bleiben.
     */
    private void showDictationFailure(String reason) {
        if (!isShowing()) {
            return;
        }
        JTextArea text = new JTextArea("Die Spracheingabe ist fehlgeschlagen.\n\n" + reason + "\n\n"
                + dictation.description(), 0, 60);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane scroll = new JScrollPane(text, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(520, Math.min(260, text.getPreferredSize().height + 40)));
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), scroll, "Spracheingabe",
                JOptionPane.WARNING_MESSAGE);
    }

    private void startLevelMeter() {
        recordingStartedMillis = System.currentTimeMillis();
        levelBar.setValue(0);
        waveform.setActive(true);
        levelTimer.restart();
    }

    private void stopLevelMeter() {
        levelTimer.stop();
        waveform.setActive(false);
        levelBar.setValue(0);
    }

    /** Alle 100 ms während der Aufnahme: Pegel in Balken und Wellenform, Dauer und Signal in die Statuszeile. */
    private void sampleLevel() {
        if (dictation == null || dictationPhase != DictationPhase.RECORDING) {
            levelTimer.stop();
            return;
        }
        int level = dictation.level();
        levelBar.setValue(level);
        waveform.pushLevel(level);
        refreshStatus();
    }

    /** Ohne „Signal“-Text: dafür gibt es Wellenform und Pegelbalken; so ändert die Zeile beim Sprechen ihre Breite nicht. */
    private String recordingStatus() {
        long seconds = Math.max(0, (System.currentTimeMillis() - recordingStartedMillis) / 1000L);
        return "● Aufnahme " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    /** Ob gerade eine Antwort läuft (dann zeigt die Fläche Stop und den Akzentrand). */
    public boolean isBusy() {
        return model.canStop();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = 1;
            int top = statusLine.getHeight(); // die Statuszeile steht außerhalb des Rahmens darüber
            RoundRectangle2D background = new RoundRectangle2D.Float(inset, top + inset, getWidth() - 2 * inset,
                    getHeight() - top - 2 * inset, ARC, ARC);
            boolean busy = isBusy();
            boolean dictating = isDictationActive();
            g2.setColor(dictating ? BACKGROUND_DICTATION : editorFocused ? BACKGROUND_FOCUSED : resolveBackground());
            g2.fill(background);
            g2.setColor(dictating ? BORDER_DICTATION : editorFocused || busy ? BORDER_FOCUSED : BORDER_NORMAL);
            g2.setStroke(new BasicStroke(editorFocused || busy || dictating ? 1.6f : 1f));
            g2.draw(background);
        } finally {
            g2.dispose();
        }
    }

    /** Aufnahme oder Erkennung läuft (arch: Fläche orange, Senden gesperrt). */
    private boolean isDictationActive() {
        return dictationPhase != DictationPhase.IDLE;
    }

    private static Color resolveBackground() {
        Color color = UIManager.getColor("TextArea.background");
        return color == null ? BACKGROUND_NORMAL : blend(color, BACKGROUND_NORMAL, 0.35f);
    }

    private static Color blend(Color first, Color second, float secondWeight) {
        float firstWeight = 1f - secondWeight;
        return new Color(
                Math.round(first.getRed() * firstWeight + second.getRed() * secondWeight),
                Math.round(first.getGreen() * firstWeight + second.getGreen() * secondWeight),
                Math.round(first.getBlue() * firstWeight + second.getBlue() * secondWeight));
    }

    private void buildUi() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        editor.setToolTipText("Enter: senden · Umschalt+Enter: neue Zeile");
        // Der Platzhalter ist nur gemalt; Screenreader brauchen einen echten Namen.
        editor.getAccessibleContext().setAccessibleName("Nachricht");
        editor.getAccessibleContext().setAccessibleDescription("Enter sendet, Umschalt+Enter fügt eine neue Zeile ein");
        editor.setOpaque(false);
        editor.setBorder(new EmptyBorder(3, 4, 3, 4));
        editor.setFont(editor.getFont().deriveFont(13f));
        editor.setForeground(ResearchUiPalette.TEXT_DARK);
        editor.setCaretColor(ResearchUiPalette.TEXT_DARK);

        JScrollPane editorScroll = new JScrollPane(editor, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        editorScroll.setOpaque(false);
        editorScroll.getViewport().setOpaque(false);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        editorScroll.setPreferredSize(new Dimension(520, MIN_EDITOR_HEIGHT));

        // Wie arch: der Editor in einer Schichtung, damit die Wellenform ÜBER dem Text schwebt (lässt Klicks durch).
        final JScrollPane scroll = editorScroll;
        JLayeredPane editorLayers = new JLayeredPane() {
            private static final long serialVersionUID = 1L;

            @Override
            public void doLayout() {
                for (Component child : getComponents()) {
                    child.setBounds(0, 0, getWidth(), getHeight());
                }
            }

            @Override
            public Dimension getPreferredSize() {
                return scroll.getPreferredSize();
            }

            @Override
            public Dimension getMinimumSize() {
                return scroll.getMinimumSize();
            }
        };
        editorLayers.add(editorScroll, JLayeredPane.DEFAULT_LAYER);
        editorLayers.add(waveform, JLayeredPane.PALETTE_LAYER);

        // Die Statuszeile steht als eigene Zeile über dem Rahmen (außerhalb), ihr Platz ist immer reserviert,
        // damit der Composer beim Erscheinen nicht springt; das ✕ schließt sie.
        statusLine.onClose(this::dismissStatus);
        JPanel body = new JPanel(new BorderLayout(0, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(9, 11, 8, 8));
        body.add(attachmentStrip, BorderLayout.NORTH);
        body.add(editorLayers, BorderLayout.CENTER);
        body.add(buildFooter(), BorderLayout.SOUTH);
        add(statusLine, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        setMinimumSize(new Dimension(320, 104 + statusLine.getPreferredSize().height));
    }

    /** Die Fußzeile wie arch: links Modell, Denkaufwand, Verwerfen/Erneut; rechts Pegel, Anhang, Mikrofon, Senden/Stop. */
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(5, 0, 0, 0));
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        west.setOpaque(false);
        west.add(modelButton);
        west.add(reasoningButton);
        JPanel dictationActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        dictationActions.setOpaque(false);
        dictationActions.add(discardButton);
        dictationActions.add(retryButton);
        west.add(dictationActions);
        footer.add(west, BorderLayout.WEST);

        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        east.add(Box.createHorizontalGlue());
        east.add(levelBar);
        east.add(Box.createHorizontalStrut(4));
        east.add(attachButton);
        east.add(Box.createHorizontalStrut(4));
        east.add(micButton); // Mikrofon direkt links von Senden (arch)
        east.add(Box.createHorizontalStrut(4));
        east.add(sendButton);
        east.add(stopButton);
        footer.add(east, BorderLayout.EAST);
        return footer;
    }

    private void showModelMenu() {
        JPopupMenu menu = new JPopupMenu();
        List<String> choices = modelChoices == null ? null : modelChoices.get();
        String current = modelButton.getText();
        if (choices == null || choices.isEmpty()) {
            JMenuItem empty = new JMenuItem("Noch keine Chat-Modelle abgefragt");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (final String id : choices) {
                JMenuItem item = new JMenuItem((id.equals(current) ? "✓ " : "    ") + id);
                item.addActionListener(event -> {
                    setModelName(id);
                    if (modelSelected != null) {
                        modelSelected.accept(id);
                    }
                });
                menu.add(item);
            }
        }
        if (modelAction != null) {
            menu.addSeparator();
            JMenuItem manage = new JMenuItem("Modelle verwalten …");
            manage.addActionListener(event -> modelAction.run());
            menu.add(manage);
        }
        menu.show(modelButton, 0, modelButton.getHeight());
    }

    private void showReasoningMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (int i = 0; i < REASONING_LABELS.size(); i++) {
            final int index = i;
            JMenuItem item = new JMenuItem(REASONING_LABELS.get(i));
            item.addActionListener(event -> selectReasoning(index));
            menu.add(item);
        }
        menu.show(reasoningButton, 0, reasoningButton.getHeight());
    }

    private void wireBehaviour() {
        sendButton.addActionListener(event -> submit());
        stopButton.addActionListener(event -> {
            if (model.canStop()) {
                actions.stopRequested();
            }
        });
        modelButton.addActionListener(event -> showModelMenu());
        reasoningButton.addActionListener(event -> showReasoningMenu());
        attachButton.addActionListener(event -> chooseFiles());
        micButton.addActionListener(event -> toggleDictation());
        discardButton.addActionListener(event -> discardDictation());
        retryButton.addActionListener(event -> retryDictation());
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_M,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), DICTATION_ACTION);
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), DISCARD_ACTION);
        editor.getActionMap().put(DICTATION_ACTION, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                if (micButton.isVisible() && micButton.isEnabled()) {
                    toggleDictation();
                }
            }
        });
        editor.getActionMap().put(DISCARD_ACTION, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                discardDictation();
            }
        });

        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), SEND_ACTION);
        editor.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK),
                DefaultEditorKit.insertBreakAction);
        editor.getActionMap().put(SEND_ACTION, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent event) {
                submit();
            }
        });
        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshControls();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                // Wer den Entwurf leert, braucht den Hinweis „Abschrift eingefügt“ nicht mehr (Fehler bleiben).
                if (editor.getText().isEmpty() && dictationNoteTone != ComposerStatusLine.Tone.ERROR) {
                    setDictationNote(null, ComposerStatusLine.Tone.MUTED);
                }
                refreshControls();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshControls();
            }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                editorFocused = true;
                repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                editorFocused = false;
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                editor.requestFocusInWindow(); // ein Klick irgendwo auf die Fläche setzt den Cursor (arch)
            }
        });
    }

    /** Knopflogik wie arch {@code setDictationView}: Mikrofon / Stopp-Quadrat / ✕, Verwerfen, Erneut, Pegel. */
    private void refreshDictation(boolean busy) {
        if (dictation == null) {
            return;
        }
        boolean recording = dictationPhase == DictationPhase.RECORDING;
        boolean recognizing = dictationPhase == DictationPhase.RECOGNIZING;
        boolean idle = dictationPhase == DictationPhase.IDLE;
        micButton.setEnabled(recording || recognizing || (!busy && dictation.isAvailable()));
        micButton.setIcon(recording ? ComposerIcons.stop() : recognizing ? ComposerIcons.close()
                : ComposerIcons.microphone());
        micButton.setEmphasized(recording);
        micButton.setToolTipText(recording ? RECORDING_TOOLTIP : recognizing ? CANCEL_TOOLTIP
                : DICTATE_TOOLTIP + " – " + dictation.description());
        boolean layoutChanged = retryButton.isVisible() != (idle && dictation.canRetry())
                || levelBar.isVisible() != recording;
        discardButton.setVisible(false); // verworfen wird über das ✕ an der Statuszeile oder Esc (kein eigener Knopf)
        retryButton.setVisible(idle && dictation.canRetry());
        retryButton.setEnabled(!busy);
        levelBar.setVisible(recording);
        if (layoutChanged) {
            revalidate();
        }
    }

    /** Send/Stop: genau einer sichtbar; Send nur mit sendbarem Entwurf aktiv, Stop nur während einer Antwort. */
    private void refreshControls() {
        boolean busy = model.canStop();
        // Während des Diktats bleibt Senden aktiv: es beendet die Aufnahme und schickt die Abschrift direkt ab.
        sendButton.setEnabled(isDictationActive() ? !busy && !sendAfterRecognition : model.canSend(editor.getText()));
        sendButton.setToolTipText(isDictationActive() ? SEND_DICTATION_TOOLTIP : "Senden (Enter)");
        stopButton.setEnabled(busy);
        attachButton.setEnabled(!busy);
        reasoningButton.setEnabled(!busy);
        refreshDictation(busy);
        refreshStatus();
        if (sendButton.isVisible() == busy || stopButton.isVisible() != busy) {
            sendButton.setVisible(!busy);
            stopButton.setVisible(busy);
            revalidate();
        }
        repaint();
    }

    /** Die Statuszeile: Antwort, Aufnahme, Erkennung, vorgemerkte Anhänge, sonst die letzte Diktat-Meldung. */
    private void refreshStatus() {
        int pending = attachmentStrip.count();
        StatusKind kind;
        String text;
        ComposerStatusLine.Tone tone = ComposerStatusLine.Tone.MUTED;
        if (model.canStop()) {
            kind = StatusKind.STREAMING;
            text = STREAMING_STATUS;
        } else if (dictationPhase == DictationPhase.RECORDING) {
            kind = StatusKind.RECORDING;
            text = recordingStatus();
            tone = ComposerStatusLine.Tone.DICTATION;
        } else if (dictationPhase == DictationPhase.RECOGNIZING) {
            kind = StatusKind.RECOGNIZING;
            text = sendAfterRecognition ? RECOGNIZING_SEND_STATUS : RECOGNIZING_STATUS;
            tone = ComposerStatusLine.Tone.DICTATION;
        } else if (dictationNote != null && dictationNoteTone == ComposerStatusLine.Tone.ERROR) {
            kind = StatusKind.NOTE;
            text = dictationNote;
            tone = dictationNoteTone;
        } else if (pending > 0) {
            kind = StatusKind.ATTACHMENTS;
            text = pending == 1 ? "1 Anhang vorgemerkt" : pending + " Anhänge vorgemerkt";
        } else {
            kind = StatusKind.NOTE;
            text = dictationNote;
            tone = dictationNoteTone;
        }
        currentStatus = kind;
        if (dismissedStatus != null && dismissedStatus != kind) {
            dismissedStatus = null; // eine neue Art Meldung erscheint wieder
        }
        statusLine.show(kind == dismissedStatus ? null : text, tone);
        setToolTipText(statusLine.text().isEmpty() ? null : statusLine.text());
    }

    /** ✕ an der Statuszeile: Aufnahme/Erkennung abbrechen, eine Diktat-Meldung schließen, sonst bis zur nächsten Art ausblenden. */
    private void dismissStatus() {
        if (currentStatus == StatusKind.RECORDING || currentStatus == StatusKind.RECOGNIZING) {
            discardDictation(); // das ✕ verwirft die Aufnahme bzw. bricht die Erkennung ab
            setDictationNote(null, ComposerStatusLine.Tone.MUTED); // ohne Folgemeldung: die Zeile verschwindet
            refreshStatus();
            return;
        }
        if (currentStatus == StatusKind.NOTE) {
            setDictationNote(null, ComposerStatusLine.Tone.MUTED);
        } else {
            dismissedStatus = currentStatus;
        }
        refreshStatus();
    }
}
