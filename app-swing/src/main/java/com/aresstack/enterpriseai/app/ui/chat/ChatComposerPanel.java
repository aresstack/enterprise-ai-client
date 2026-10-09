package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.control.ComposerToggleButton;
import com.aresstack.enterpriseai.ui.comic.control.PlaceholderTextArea;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
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

/**
 * Der Composer nach askai-java8 (arch, {@code ChatComposerPanel}): EINE abgerundete Fläche, darin der rahmenlose
 * Editor und eine Fußzeile mit den Nebenaktionen. Links der kleine RAG-Schalter als integrierte Pille, rechts
 * genau EIN Hauptknopf: „Senden“ (Akzentblau) solange nichts läuft, „Stop“ (Rot) während eine Antwort streamt —
 * nie beide. Die Fläche wird weiß und bekommt den blauen Rand, sobald der Editor den Fokus hat oder eine
 * Antwort läuft; Enter sendet, Umschalt+Enter bricht um.
 *
 * <p>Farben und Icons kommen aus {@link ResearchUiPalette} und {@link ComposerIcons}; die wenigen eigenen
 * Töne (Rand und Fläche in Ruhe, Platzhalter) sind die der Referenz.</p>
 */
public final class ChatComposerPanel extends JPanel implements ChatShellModelListener {

    static final String PLACEHOLDER = "Nachricht…";
    static final String SEND_LABEL = "Senden";
    static final String STOP_LABEL = "Stop";
    static final String RAG_LABEL = "RAG";

    private static final int ARC = 18;
    private static final int MIN_EDITOR_HEIGHT = 62;
    private static final Color BORDER_NORMAL = new Color(0xB8BDC5);
    private static final Color BORDER_FOCUSED = ResearchUiPalette.ACCENT_BLUE;
    private static final Color BACKGROUND_NORMAL = new Color(0xF5F6F7);
    private static final Color BACKGROUND_FOCUSED = Color.WHITE;
    private static final Color PRIMARY = ResearchUiPalette.ACCENT_BLUE;
    private static final Color DANGER = ResearchUiPalette.DANGER_RED;
    private static final String SEND_ACTION = "enterpriseai.send";

    private final ChatShellModel model;
    private final ChatShellActions actions;
    private final ComposerToggleButton ragToggle;
    private final PlaceholderTextArea editor;
    private final ComposerButton sendButton;
    private final ComposerButton stopButton;
    private boolean editorFocused;

    public ChatComposerPanel(ChatShellModel model, ChatShellActions actions, ComicPalette palette) {
        if (model == null || actions == null || palette == null) {
            throw new IllegalArgumentException("model, actions and palette must not be null");
        }
        this.model = model;
        this.actions = actions;
        this.ragToggle = new ComposerToggleButton(null, RAG_LABEL, "Wissensbasis für die nächste Frage verwenden");
        ragToggle.setAccent(ResearchUiPalette.SECONDARY_SURFACE); // ein Werkzeug-Schalter: dunkel wie die Pillen, Blau bleibt „Senden“
        this.editor = new PlaceholderTextArea(PLACEHOLDER, 2, 40);
        this.sendButton = ComposerButton.primary(ComposerIcons.send(), SEND_LABEL, PRIMARY, "Senden (Enter)");
        this.stopButton = ComposerButton.primary(ComposerIcons.stop(), STOP_LABEL, DANGER, "Antwort abbrechen");
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
        if (ragToggle.isSelected() != model.isRagEnabled()) {
            ragToggle.setSelected(model.isRagEnabled());
        }
        refreshControls();
    }

    /** Sendet den Entwurf, wenn das Model es erlaubt; der Editor wird danach geleert. */
    void submit() {
        String draft = editor.getText();
        if (!model.canSend(draft)) {
            return;
        }
        editor.setText("");
        actions.sendRequested(draft.trim(), model.isRagEnabled());
    }

    /**
     * Blendet den RAG-Schalter aus, z. B. in der Agent-Ansicht, wo der Agent seinen Kontext selbst beschafft.
     * Der Zustand im Model bleibt unberührt.
     */
    public void setRagToggleVisible(boolean visible) {
        ragToggle.setVisible(visible);
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

    /** Der RAG-Schalter; sein Zustand liegt im {@link ChatShellModel}. */
    public ComposerToggleButton ragToggle() {
        return ragToggle;
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

        add(editorScroll, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        setMinimumSize(new Dimension(320, 104));
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(5, 0, 0, 0));
        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        west.setOpaque(false);
        west.add(ragToggle);
        footer.add(west, BorderLayout.WEST);

        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.X_AXIS));
        east.add(Box.createHorizontalGlue());
        east.add(sendButton);
        east.add(stopButton);
        footer.add(east, BorderLayout.EAST);
        return footer;
    }

    private void wireBehaviour() {
        sendButton.addActionListener(event -> submit());
        stopButton.addActionListener(event -> {
            if (model.canStop()) {
                actions.stopRequested();
            }
        });
        ragToggle.addActionListener(event -> model.setRagEnabled(ragToggle.isSelected()));

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
        if (sendButton.isVisible() == busy || stopButton.isVisible() != busy) {
            sendButton.setVisible(!busy);
            stopButton.setVisible(busy);
            revalidate();
        }
        repaint();
    }
}
