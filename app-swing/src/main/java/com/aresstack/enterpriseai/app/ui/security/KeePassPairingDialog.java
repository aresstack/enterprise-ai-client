package com.aresstack.enterpriseai.app.ui.security;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Modaler Dialog für das KeePassRPC-Pairing: KeePass zeigt beim ersten Verbinden ein Einmal-Passwort an, das
 * der Benutzer hier eingibt. Liefert die Eingabe als {@code char[]} (Aufrufer löscht sie) oder {@code null}
 * beim Abbrechen. Muss auf dem EDT gerufen werden; blockiert bis der Dialog geschlossen ist.
 */
public final class KeePassPairingDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final JPasswordField passwordField = new JPasswordField(24);
    private char[] result;

    private KeePassPairingDialog(Frame owner, String clientDisplayName, String keePassAddress, ComicPalette palette) {
        super(owner, "KeePass verbinden", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(palette.getSurface());
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        ComicSectionPanel plate = new ComicSectionPanel(palette);
        plate.setAccentStripe(palette.getNavigationBlue());
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Pairing mit KeePass");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 2f));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        plate.add(heading);
        plate.add(Box.createVerticalStrut(6));
        JTextArea text = new JTextArea(instructions(clientDisplayName, keePassAddress));
        text.setEditable(false);
        text.setFocusable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        text.setFont(heading.getFont().deriveFont(Font.PLAIN, heading.getFont().getSize2D() - 2f));
        text.setAlignmentX(LEFT_ALIGNMENT);
        text.setPreferredSize(new Dimension(420, 110));
        plate.add(text);
        plate.add(Box.createVerticalStrut(8));

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel label = new JLabel("Einmal-Passwort aus KeePass:");
        row.add(label, BorderLayout.WEST);
        passwordField.setBorder(ComicBorder.roundedBorder(palette, 4));
        row.add(passwordField, BorderLayout.CENTER);
        plate.add(row);
        content.add(plate, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        ComicButton cancel = new ComicButton("Abbrechen", ComicButton.Accent.CRITICAL);
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
        ComicButton ok = new ComicButton("Verbinden");
        ok.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirm();
            }
        });
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);

        passwordField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirm();
            }
        });
        content.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                passwordField.requestFocusInWindow();
            }
        });
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static String instructions(String clientDisplayName, String keePassAddress) {
        StringBuilder sb = new StringBuilder();
        sb.append("Die Anwendung verbindet sich zum ersten Mal mit KeePass");
        if (keePassAddress != null && !keePassAddress.trim().isEmpty()) {
            sb.append(" (").append(keePassAddress.trim()).append(')');
        }
        sb.append(".\n\nKeePass zeigt jetzt ein Fenster \"Authorise a new connection\" für den Client \"")
                .append(clientDisplayName == null ? "" : clientDisplayName)
                .append("\" mit einem Einmal-Passwort. Bitte dieses Passwort hier eingeben. ")
                .append("Der Pairing-Schlüssel wird danach gespeichert, ein erneutes Pairing ist nicht nötig.");
        return sb.toString();
    }

    private void confirm() {
        result = passwordField.getPassword();
        if (result != null && result.length == 0) {
            result = null;
            passwordField.requestFocusInWindow();
            return;
        }
        dispose();
    }

    private void cancel() {
        result = null;
        dispose();
    }

    /** Zeigt den Dialog modal und liefert das Passwort oder {@code null} beim Abbrechen. Nur auf dem EDT. */
    public static char[] show(String clientDisplayName, String keePassAddress) {
        return show(activeFrame(), clientDisplayName, keePassAddress, ComicPalette.defaultPalette());
    }

    public static char[] show(Frame owner, String clientDisplayName, String keePassAddress, ComicPalette palette) {
        KeePassPairingDialog dialog = new KeePassPairingDialog(owner, clientDisplayName, keePassAddress,
                palette == null ? ComicPalette.defaultPalette() : palette);
        try {
            dialog.setVisible(true);
            return dialog.result;
        } finally {
            dialog.passwordField.setText("");
            dialog.result = null;
        }
    }

    private static Frame activeFrame() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing() && frame.isActive()) {
                return frame;
            }
        }
        for (Frame frame : Frame.getFrames()) {
            if (frame.isShowing()) {
                return frame;
            }
        }
        return null;
    }
}
