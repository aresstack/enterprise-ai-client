package com.aresstack.enterpriseai.proasteion.exedra.ui.security;

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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Comic-Dialog „Anmeldung erforderlich“ für den Einmalcode (OTP), den eine Quelle für Dateien verlangt, etwa ein
 * Zwei-Faktor-Plugin vor Confluence-Downloads. Zeigt Überschrift, Hinweis und Fehler der Seite, ein Codefeld und
 * „Abbrechen“, „Neuen Code senden“ (wenn die Seite das anbietet) und „Weiter“. Kein Browser und keine HTML-Ansicht:
 * die Felder der Seite übernimmt die Quelle. Modal; nur auf dem EDT.
 */
public final class OneTimeCodeDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Was der Benutzer gewählt hat. */
    public enum Choice {
        SUBMIT, RESEND, CANCEL
    }

    /** Ergebnis; den Code löscht der Empfänger. */
    public static final class Result {
        private final Choice choice;
        private final char[] code;

        Result(Choice choice, char[] code) {
            this.choice = choice;
            this.code = code;
        }

        public Choice choice() {
            return choice;
        }

        public char[] code() {
            return code;
        }
    }

    private final JPasswordField codeField = new JPasswordField(14);
    private final ComicButton resendButton = new ComicButton("Neuen Code senden");
    private final ComicButton cancelButton = new ComicButton("Abbrechen", ComicButton.Accent.CRITICAL);
    private final ComicButton submitButton = new ComicButton("Weiter");
    private Result result = new Result(Choice.CANCEL, new char[0]);

    private OneTimeCodeDialog(Frame owner, String service, String title, String message, String error,
                              boolean resendAvailable, ComicPalette palette) {
        super(owner, "Anmeldung erforderlich", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(palette.getSurface());
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        ComicSectionPanel plate = new ComicSectionPanel(palette);
        plate.setAccentStripe(palette.getNavigationBlue());
        plate.setLayout(new BoxLayout(plate, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(service.isEmpty() ? "Anmeldung erforderlich"
                : "Anmeldung bei " + service + " erforderlich");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 2f));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        plate.add(heading);
        plate.add(Box.createVerticalStrut(4));
        plate.add(text("Die Quelle verlangt für Dateien einen Einmalcode. Er gilt für diese Sitzung der Anwendung "
                + "und wird weder gespeichert noch protokolliert.", palette));
        if (!title.isEmpty()) {
            plate.add(Box.createVerticalStrut(8));
            JLabel pageTitle = new JLabel(title);
            pageTitle.setFont(pageTitle.getFont().deriveFont(Font.BOLD));
            pageTitle.setAlignmentX(LEFT_ALIGNMENT);
            plate.add(pageTitle);
        }
        if (!message.isEmpty()) {
            plate.add(Box.createVerticalStrut(4));
            plate.add(text(message, palette));
        }
        if (!error.isEmpty()) {
            plate.add(Box.createVerticalStrut(6));
            JTextArea problem = text(error, palette);
            problem.setForeground(palette.getAccentRed());
            plate.add(problem);
        }
        plate.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel codeLabel = new JLabel("Einmalcode:");
        codeLabel.setLabelFor(codeField);
        codeField.setBorder(ComicBorder.roundedBorder(palette, 4));
        row.add(codeLabel);
        row.add(Box.createHorizontalStrut(8));
        row.add(codeField);
        plate.add(row);
        content.add(plate, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        ActionListener cancel = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close(Choice.CANCEL);
            }
        };
        cancelButton.addActionListener(cancel);
        resendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close(Choice.RESEND);
            }
        });
        ActionListener submit = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (codeField.getPassword().length == 0) {
                    codeField.requestFocusInWindow();
                    return;
                }
                close(Choice.SUBMIT);
            }
        };
        submitButton.addActionListener(submit);
        codeField.addActionListener(submit);
        buttons.add(cancelButton);
        if (resendAvailable) {
            buttons.add(resendButton);
        }
        buttons.add(submitButton);
        content.add(buttons, BorderLayout.SOUTH);
        content.registerKeyboardAction(cancel, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                codeField.requestFocusInWindow();
            }
        });
        setContentPane(content);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static JTextArea text(String value, ComicPalette palette) {
        JTextArea area = new JTextArea(value);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setColumns(42);
        area.setForeground(palette.getInk());
        area.setFont(new JLabel().getFont());
        area.setAlignmentX(LEFT_ALIGNMENT);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    private void close(Choice choice) {
        result = new Result(choice, choice == Choice.SUBMIT ? codeField.getPassword() : new char[0]);
        dispose();
    }

    /**
     * Zeigt den Dialog modal; liefert die Wahl, bei „Weiter“ mit Code. Schließen per Fenster oder Escape gilt als
     * Abbruch. Nur auf dem EDT.
     */
    public static Result show(String service, String title, String message, String error, boolean resendAvailable) {
        OneTimeCodeDialog dialog = create(service, title, message, error, resendAvailable);
        try {
            dialog.setVisible(true);
            return dialog.result;
        } finally {
            dialog.codeField.setText("");
        }
    }

    /** Ohne Anzeigen (Tests): der Dialog, wie {@link #show} ihn zeigt. */
    static OneTimeCodeDialog create(String service, String title, String message, String error,
                                    boolean resendAvailable) {
        return new OneTimeCodeDialog(activeFrame(), nonNull(service), nonNull(title), nonNull(message),
                nonNull(error), resendAvailable, ComicPalette.defaultPalette());
    }

    JPasswordField codeField() {
        return codeField;
    }

    ComicButton submitButton() {
        return submitButton;
    }

    ComicButton resendButton() {
        return resendButton;
    }

    ComicButton cancelButton() {
        return cancelButton;
    }

    Result result() {
        return result;
    }

    private static String nonNull(String value) {
        return value == null ? "" : value.trim();
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
