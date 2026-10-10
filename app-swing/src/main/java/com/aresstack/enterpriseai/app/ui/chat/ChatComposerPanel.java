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
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Der Composer nach askai-java8 (arch, {@code ChatComposerPanel}): EINE abgerundete Fläche, darin der rahmenlose
 * Editor und eine Fußzeile wie in arch. Links Modell ▾ und Denkaufwand ▾, in der Mitte die Statuszeile, rechts
 * Büroklammer, Audiodatei, Mikrofon und genau EIN Hauptknopf: „Senden“ (Akzentblau) solange nichts läuft, „Stop“
 * (Rot) während eine Antwort streamt — nie beide. Audiodatei und Mikrofon bleiben deaktiviert, solange kein
 * Spracherkennungs-Modell verfügbar ist. Einen RAG-Schalter gibt es nicht (in arch gab es ihn nie): ob Wissen
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
    static final String NO_SPEECH_MODEL = "kein Modell verfügbar";
    static final String AUDIO_FILE_TOOLTIP = "Audiodatei transkribieren (" + NO_SPEECH_MODEL + ")";
    static final String MICROPHONE_TOOLTIP = "Diktieren (" + NO_SPEECH_MODEL + ")";
    static final String MODEL_PLACEHOLDER = "Modell";
    static final String STREAMING_STATUS = "Antwort wird erstellt …";
    /** Denkaufwand: Anzeige und Wert ({@code null} = Standard des Modells), wie arch „Think: …“. */
    static final String[][] REASONING_LEVELS = {
            {"Denken: Standard", null}, {"Denken: niedrig", "low"}, {"Denken: mittel", "medium"},
            {"Denken: hoch", "high"}};

    private static final int ARC = 18;
    private static final int MIN_EDITOR_HEIGHT = 62;
    private static final Color BORDER_NORMAL = new Color(0xB8BDC5);
    private static final Color BORDER_FOCUSED = ResearchUiPalette.ACCENT_BLUE;
    private static final Color BACKGROUND_NORMAL = new Color(0xF5F6F7);
    private static final Color BACKGROUND_FOCUSED = Color.WHITE;
    private static final Color PRIMARY = ResearchUiPalette.ACCENT_BLUE;
    private static final Color DANGER = ResearchUiPalette.DANGER_RED;
    private static final Color TEXT_MUTED = ResearchUiPalette.LIGHT_TEXT_MUTED;
    private static final String SEND_ACTION = "enterpriseai.send";

    private final ChatShellModel model;
    private final ChatShellActions actions;
    private final ComposerButton modelButton;
    private final ComposerButton reasoningButton;
    private final JLabel statusLabel = new JLabel(" ");
    private final PlaceholderTextArea editor;
    private final ComposerButton sendButton;
    private final ComposerButton stopButton;
    private final ComposerButton attachButton;
    private final ComposerButton audioFileButton;
    private final ComposerButton microphoneButton;
    private final ChatAttachmentStrip attachmentStrip;
    private Runnable modelAction;
    private String reasoningEffort;
    private File lastDirectory;
    private boolean editorFocused;

    public ChatComposerPanel(ChatShellModel model, ChatShellActions actions, ComicPalette palette) {
        if (model == null || actions == null || palette == null) {
            throw new IllegalArgumentException("model, actions and palette must not be null");
        }
        this.model = model;
        this.actions = actions;
        this.modelButton = new ComposerButton(ComposerIcons.chevronDown(), MODEL_PLACEHOLDER, false,
                "Chat-Modell (Einstellungen → KI-Dienst)");
        modelButton.setHorizontalTextPosition(SwingConstants.LEFT); // Name zuerst, Chevron danach (arch)
        this.reasoningButton = new ComposerButton(ComposerIcons.chevronDown(), REASONING_LEVELS[0][0], false,
                "Denkaufwand (nur für Modelle, die ihn unterstützen)");
        reasoningButton.setHorizontalTextPosition(SwingConstants.LEFT);
        this.editor = new PlaceholderTextArea(PLACEHOLDER, 2, 40);
        this.sendButton = ComposerButton.primary(ComposerIcons.send(), SEND_LABEL, PRIMARY, "Senden (Enter)");
        this.stopButton = ComposerButton.primary(ComposerIcons.stop(), STOP_LABEL, DANGER, "Antwort abbrechen");
        this.attachButton = ComposerButton.iconButton(ComposerIcons.paperclip(), ATTACH_TOOLTIP);
        attachButton.getAccessibleContext().setAccessibleName(ATTACH_TOOLTIP);
        attachButton.setVisible(actions.supportsAttachments());
        this.audioFileButton = ComposerButton.iconButton(ComposerIcons.audioFile(), AUDIO_FILE_TOOLTIP);
        audioFileButton.getAccessibleContext().setAccessibleName(AUDIO_FILE_TOOLTIP);
        audioFileButton.setEnabled(false);
        this.microphoneButton = ComposerButton.iconButton(ComposerIcons.microphone(), MICROPHONE_TOOLTIP);
        microphoneButton.getAccessibleContext().setAccessibleName(MICROPHONE_TOOLTIP);
        microphoneButton.setEnabled(false);
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
        String draft = editor.getText();
        if (!model.canSend(draft)) {
            return;
        }
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
        modelButton.setToolTipText("Chat-Modell: " + modelButton.getText() + " (Einstellungen → KI-Dienst)");
    }

    /** Was ein Klick auf den Modellknopf tut (bis zum Modellkatalog: die Einstellungen öffnen). */
    public void setModelAction(Runnable action) {
        this.modelAction = action;
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

    /** Wählt einen Denkaufwand aus {@link #REASONING_LEVELS} (Index) und meldet ihn der Anbindung. */
    void selectReasoning(int index) {
        reasoningEffort = REASONING_LEVELS[index][1];
        reasoningButton.setText(REASONING_LEVELS[index][0]);
        actions.reasoningChanged(reasoningEffort);
        revalidate();
        repaint();
    }

    public ComposerButton audioFileButton() {
        return audioFileButton;
    }

    public ComposerButton microphoneButton() {
        return microphoneButton;
    }

    /** Die Statuszeile in der Mitte der Fußzeile. */
    public JLabel statusLabel() {
        return statusLabel;
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
            RoundRectangle2D background = new RoundRectangle2D.Float(inset, inset, getWidth() - 2 * inset,
                    getHeight() - 2 * inset, ARC, ARC);
            boolean busy = isBusy();
            g2.setColor(editorFocused ? BACKGROUND_FOCUSED : resolveBackground());
            g2.fill(background);
            g2.setColor(editorFocused || busy ? BORDER_FOCUSED : BORDER_NORMAL);
            g2.setStroke(new BasicStroke(editorFocused || busy ? 1.6f : 1f));
            g2.draw(background);
        } finally {
            g2.dispose();
        }
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
        setBorder(new EmptyBorder(9, 11, 8, 8));
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

        add(attachmentStrip, BorderLayout.NORTH);
        add(editorScroll, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        setMinimumSize(new Dimension(320, 104));
    }

    /** Die Fußzeile wie arch: links Modell und Denkaufwand, Mitte Status, rechts Anhang, Audio, Mikrofon, Senden. */
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(5, 0, 0, 0));
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        west.setOpaque(false);
        west.add(modelButton);
        west.add(reasoningButton);
        footer.add(west, BorderLayout.WEST);

        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(statusLabel.getFont().deriveFont(statusLabel.getFont().getSize2D() - 1f));
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        center.setOpaque(false);
        center.add(statusLabel);
        footer.add(center, BorderLayout.CENTER);

        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        east.add(Box.createHorizontalGlue());
        east.add(attachButton);
        east.add(Box.createHorizontalStrut(4));
        east.add(audioFileButton);
        east.add(Box.createHorizontalStrut(4));
        east.add(microphoneButton);
        east.add(Box.createHorizontalStrut(4));
        east.add(sendButton);
        east.add(stopButton);
        footer.add(east, BorderLayout.EAST);
        return footer;
    }

    private void showReasoningMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (int i = 0; i < REASONING_LEVELS.length; i++) {
            final int index = i;
            JMenuItem item = new JMenuItem(REASONING_LEVELS[i][0]);
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
        modelButton.addActionListener(event -> {
            if (modelAction != null) {
                modelAction.run();
            }
        });
        reasoningButton.addActionListener(event -> showReasoningMenu());
        attachButton.addActionListener(event -> chooseFiles());

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
    }

    /** Send/Stop: genau einer sichtbar; Send nur mit sendbarem Entwurf aktiv, Stop nur während einer Antwort. */
    private void refreshControls() {
        boolean busy = model.canStop();
        sendButton.setEnabled(model.canSend(editor.getText()));
        stopButton.setEnabled(busy);
        attachButton.setEnabled(!busy);
        reasoningButton.setEnabled(!busy);
        int pending = attachmentStrip.count();
        String status = busy ? STREAMING_STATUS
                : pending == 1 ? "1 Anhang vorgemerkt" : pending > 1 ? pending + " Anhänge vorgemerkt" : " ";
        if (!status.equals(statusLabel.getText())) {
            statusLabel.setText(status);
        }
        if (sendButton.isVisible() == busy || stopButton.isVisible() != busy) {
            sendButton.setVisible(!busy);
            stopButton.setVisible(busy);
            revalidate();
        }
        repaint();
    }
}
