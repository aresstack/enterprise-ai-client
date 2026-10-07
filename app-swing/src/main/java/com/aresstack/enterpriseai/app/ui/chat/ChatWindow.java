package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * Das Hauptfenster im Comic-Stil: neutrale Fläche, die Shell innerhalb einer Tintenkontur. Wird von der
 * Composition Root (bzw. der Demo) erzeugt; braucht ein Display und ist daher nicht Teil der Headless-Tests.
 */
public final class ChatWindow {

    private ChatWindow() {
    }

    public static JFrame create(String title, ChatShellPanel shell, ComicPalette palette) {
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(palette.getSurface());
        content.setBorder(ComicBorder.roundedBorder(palette, 6));
        content.add(shell, BorderLayout.CENTER);
        frame.setContentPane(content);
        frame.setMinimumSize(new Dimension(480, 420));
        frame.setSize(new Dimension(820, 680));
        frame.setLocationRelativeTo(null);
        return frame;
    }
}
