package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicToggleButton;
import com.aresstack.enterpriseai.ui.comic.control.PlaceholderTextArea;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Die Eingabezeile {@code [ RAG ]  Nachricht... [Senden] [Stop]}: weiße Comic-Platte mit Tintenkontur (wie
 * der AskAI-Composer), Enter sendet, Umschalt+Enter bricht um. Send ist nur aktiv, wenn das Model Senden
 * erlaubt; Stop nur, solange eine Antwort läuft. Der RAG-Schalter ist in AP4 nur Bedienelement (AP22 füllt
 * ihn) und wird mit jeder Sendeabsicht mitgegeben.
 */
public final class ChatComposerPanel extends JPanel implements ChatShellModelListener {

    static final String PLACEHOLDER = "Nachricht…";
    private static final int ARC = 18;
    private static final String SEND_ACTION = "enterpriseai.send";

    private final ChatShellModel model;
    private final ChatShellActions actions;
    private final ComicPalette palette;
    private final ComicToggleButton ragToggle;
    private final PlaceholderTextArea editor;
    private final ComicButton sendButton;
    private final ComicButton stopButton;

    public ChatComposerPanel(ChatShellModel model, ChatShellActions actions, ComicPalette palette) {
        if (model == null || actions == null || palette == null) {
            throw new IllegalArgumentException("model, actions and palette must not be null");
        }
        this.model = model;
        this.actions = actions;
        this.palette = palette;
        this.ragToggle = new ComicToggleButton("RAG", palette);
        this.editor = new PlaceholderTextArea(PLACEHOLDER, 2, 40);
        this.sendButton = new ComicButton("Senden", null, ComicButton.Accent.ACTION, palette);
        this.stopButton = new ComicButton("Stop", null, ComicButton.Accent.CRITICAL, palette);
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

    PlaceholderTextArea editor() {
        return editor;
    }

    ComicButton sendButton() {
        return sendButton;
    }

    ComicButton stopButton() {
        return stopButton;
    }

    ComicToggleButton ragToggle() {
        return ragToggle;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D plate = new RoundRectangle2D.Float(1f, 1f, getWidth() - 3f, getHeight() - 3f, ARC, ARC);
            g2.setColor(java.awt.Color.WHITE);
            g2.fill(plate);
            g2.setColor(palette.getInk());
            g2.setStroke(new BasicStroke(editor.isFocusOwner() ? 2.2f : 1.6f));
            g2.draw(plate);
        } finally {
            g2.dispose();
        }
    }

    private void buildUi() {
        setOpaque(false);
        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        ragToggle.setToolTipText("Wissensbasis für die nächste Frage verwenden");
        sendButton.setToolTipText("Senden (Enter)");
        stopButton.setToolTipText("Antwort abbrechen");
        editor.setToolTipText("Enter: senden · Umschalt+Enter: neue Zeile");
        editor.setForeground(palette.getInk());
        editor.setCaretColor(palette.getInk());

        JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        west.setOpaque(false);
        west.add(ragToggle);

        JScrollPane editorScroll = new JScrollPane(editor, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());
        editorScroll.setOpaque(false);
        editorScroll.getViewport().setOpaque(false);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        east.setOpaque(false);
        east.add(sendButton);
        east.add(stopButton);

        add(west, BorderLayout.WEST);
        add(editorScroll, BorderLayout.CENTER);
        add(east, BorderLayout.EAST);
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
        editor.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent event) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent event) {
                repaint();
            }
        });
    }

    private void refreshControls() {
        sendButton.setEnabled(model.canSend(editor.getText()));
        stopButton.setEnabled(model.canStop());
    }
}
