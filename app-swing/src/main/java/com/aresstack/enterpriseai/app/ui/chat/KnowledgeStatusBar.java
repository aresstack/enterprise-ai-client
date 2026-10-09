package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Die stille Statuszeile zur Wissensbasis über dem Composer: ein kurzer Text in gedämpfter Schrift, z. B. der
 * Fortschritt der Indexierung, und ein kleiner Abbrechen-Knopf im Composer-Stil, solange ein Lauf aktiv ist.
 * Folgt ausschließlich dem {@link KnowledgeStatusModel}; ohne Text ist sie unsichtbar und nimmt keinen Platz.
 */
public final class KnowledgeStatusBar extends JPanel implements KnowledgeStatusModel.Listener {

    public static final String CANCEL_LABEL = "Abbrechen";
    public static final String CANCELLING_SUFFIX = " · wird abgebrochen";

    private final KnowledgeStatusModel model;
    private final JLabel label = new JLabel();
    private final ComposerButton cancelButton;

    public KnowledgeStatusBar(KnowledgeStatusModel model, ComicPalette palette) {
        super(new BorderLayout(8, 0));
        if (model == null || palette == null) {
            throw new IllegalArgumentException("model and palette must not be null");
        }
        this.model = model;
        this.cancelButton = new ComposerButton(ComposerIcons.close(), CANCEL_LABEL, false,
                "Indexierung nach der aktuellen Seite beenden");
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 24, 0, 20));
        label.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        label.setFont(ResearchUiTypography.regular(12f));
        label.getAccessibleContext().setAccessibleName("Status der Wissensbasis");
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
    public ComposerButton cancelButton() {
        return cancelButton;
    }
}
