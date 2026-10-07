package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * Die schlichte Statuszeile zur Wissensbasis unter dem Verlauf (AP22): ein Text, z. B. der Fortschritt der
 * Indexierung, und ein Abbrechen-Knopf, solange ein Lauf aktiv ist. Folgt ausschließlich dem
 * {@link KnowledgeStatusModel}; ohne Text ist sie unsichtbar und nimmt keinen Platz ein.
 */
public final class KnowledgeStatusBar extends JPanel implements KnowledgeStatusModel.Listener {

    public static final String CANCEL_LABEL = "Abbrechen";
    public static final String CANCELLING_SUFFIX = " · wird abgebrochen";

    private final KnowledgeStatusModel model;
    private final JLabel label = new JLabel();
    private final ComicButton cancelButton;

    public KnowledgeStatusBar(KnowledgeStatusModel model, ComicPalette palette) {
        super(new BorderLayout(8, 0));
        if (model == null || palette == null) {
            throw new IllegalArgumentException("model and palette must not be null");
        }
        this.model = model;
        this.cancelButton = new ComicButton(CANCEL_LABEL, null, ComicButton.Accent.CRITICAL, palette);
        setOpaque(true);
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, palette.getInk()),
                BorderFactory.createEmptyBorder(4, 12, 4, 10)));
        label.setForeground(palette.getInk());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, Math.max(11f, label.getFont().getSize2D() - 1f)));
        label.getAccessibleContext().setAccessibleName("Status der Wissensbasis");
        cancelButton.setToolTipText("Indexierung nach der aktuellen Seite beenden");
        cancelButton.addActionListener(event -> model.requestCancel());
        add(label, BorderLayout.CENTER);
        add(cancelButton, BorderLayout.EAST);
        model.addListener(this);
        statusChanged();
    }

    @Override
    public void statusChanged() {
        String text = model.getText();
        if (model.isRunning() && model.isCancelRequested()) {
            text = text + CANCELLING_SUFFIX;
        }
        label.setText(text);
        cancelButton.setVisible(model.isRunning());
        cancelButton.setEnabled(model.isRunning() && !model.isCancelRequested());
        boolean visible = model.isVisible();
        if (visible != isVisible()) {
            setVisible(visible);
        }
        revalidate();
        repaint();
    }

    /** Der angezeigte Text (für Tests). */
    public String getText() {
        return label.getText();
    }

    /** Der Abbrechen-Knopf; sichtbar nur während eines Laufs. */
    public ComicButton cancelButton() {
        return cancelButton;
    }
}
