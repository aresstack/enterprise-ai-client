package com.aresstack.enterpriseai.proasteion.exedra.ui.security;

import com.aresstack.enterpriseai.proasteion.exedra.ui.icon.ApplicationIcons;
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
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Modaler Dialog für den Zugang „Manuell“: Benutzer (optional) und Passwort bzw. API-Key für einen Dienst, gültig
 * bis zum Beenden der Anwendung. Muss auf dem EDT gerufen werden; blockiert bis der Dialog geschlossen ist.
 */
public final class CredentialDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Eingabe; das Passwort löscht der Empfänger. */
    public static final class Result {
        private final String user;
        private final char[] password;

        Result(String user, char[] password) {
            this.user = user;
            this.password = password;
        }

        public String user() {
            return user;
        }

        public char[] password() {
            return password;
        }
    }

    private final JTextField userField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private Result result;

    private CredentialDialog(Frame owner, String label, ComicPalette palette) {
        super(owner, "Zugangsdaten", true);
        ApplicationIcons.apply(this);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(palette.getSurface());
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        ComicSectionPanel plate = new ComicSectionPanel(palette);
        plate.setAccentStripe(palette.getNavigationBlue());
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Zugangsdaten für „" + label + "“");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 2f));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        plate.add(heading);
        plate.add(Box.createVerticalStrut(4));
        JLabel hint = new JLabel("Gilt bis zum Beenden der Anwendung und wird nicht gespeichert.");
        hint.setAlignmentX(LEFT_ALIGNMENT);
        plate.add(hint);
        plate.add(Box.createVerticalStrut(8));

        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 6));
        fields.setOpaque(false);
        fields.setAlignmentX(LEFT_ALIGNMENT);
        JLabel userLabel = new JLabel("Benutzer (optional):");
        userLabel.setLabelFor(userField);
        JLabel passwordLabel = new JLabel("Passwort bzw. API-Key:");
        passwordLabel.setLabelFor(passwordField);
        userField.setBorder(ComicBorder.roundedBorder(palette, 4));
        passwordField.setBorder(ComicBorder.roundedBorder(palette, 4));
        fields.add(userLabel);
        fields.add(userField);
        fields.add(passwordLabel);
        fields.add(passwordField);
        plate.add(fields);
        content.add(plate, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        ComicButton cancel = new ComicButton("Abbrechen", ComicButton.Accent.CRITICAL);
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = null;
                dispose();
            }
        });
        ComicButton ok = new ComicButton("Übernehmen");
        ActionListener confirm = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = new Result(userField.getText().trim(), passwordField.getPassword());
                dispose();
            }
        };
        ok.addActionListener(confirm);
        passwordField.addActionListener(confirm);
        buttons.add(cancel);
        buttons.add(ok);
        content.add(buttons, BorderLayout.SOUTH);
        content.registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                result = null;
                dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                userField.requestFocusInWindow();
            }
        });
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** Zeigt den Dialog modal und liefert die Eingabe oder {@code null} beim Abbrechen. Nur auf dem EDT. */
    public static Result show(String label) {
        CredentialDialog dialog = new CredentialDialog(activeFrame(), label == null ? "" : label,
                ComicPalette.defaultPalette());
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
