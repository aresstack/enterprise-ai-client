package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.util.Arrays;

/**
 * Die Einmalcode-Ansicht im Anzeigebereich (statt Bild oder Dokument), wie Angelos Skizze: „Zusätzliche Anmeldung
 * erforderlich“, Hinweis der Quelle, Fehler des letzten Versuchs, Codefeld, „Code erneut senden“ und „Bestätigen“;
 * weitere Knöpfe (etwa „Zurück zu Gerendert“) per {@link #addAction}. Kein Browser: die Felder der Seite sendet die
 * Quelle selbst. Nur auf dem EDT.
 */
public final class ReaderSignInPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    static final String HEADING = "Zusätzliche Anmeldung erforderlich";
    static final String SUBMIT_TEXT = "Bestätigen";
    static final String RESEND_TEXT = "Code erneut senden";
    static final String SENDING_TEXT = "Wird gesendet …";

    private final transient Runnable accepted;
    private final ComicPalette palette;
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final JPanel extra = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    private final JPasswordField codeField = new JPasswordField(16);
    private final ComicButton resendButton = new ComicButton(RESEND_TEXT);
    private final ComicButton submitButton = new ComicButton(SUBMIT_TEXT);
    private final JTextArea message;
    private final JTextArea error;
    private transient ReaderSignIn signIn;
    private boolean sending;

    /**
     * @param accepted läuft, sobald die Quelle den Code angenommen hat (lädt die Datei neu)
     */
    public ReaderSignInPanel(ReaderSignIn signIn, Runnable accepted) {
        super(new GridBagLayout());
        if (signIn == null || accepted == null) {
            throw new IllegalArgumentException("signIn and accepted must not be null");
        }
        this.accepted = accepted;
        this.palette = ComicPalette.defaultPalette();
        setOpaque(false);

        ComicSectionPanel card = new ComicSectionPanel(palette);
        card.setAccentStripe(palette.getNavigationBlue());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(HEADING);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, heading.getFont().getSize2D() + 3f));
        heading.setAlignmentX(LEFT_ALIGNMENT);
        card.add(heading);
        card.add(Box.createVerticalStrut(8));
        message = text("");
        card.add(message);
        error = text("");
        error.setForeground(palette.getAccentRed());
        card.add(Box.createVerticalStrut(4));
        card.add(error);
        card.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel codeLabel = new JLabel("OTP-Code:");
        codeLabel.setLabelFor(codeField);
        codeField.setBorder(ComicBorder.roundedBorder(palette, 4));
        row.add(codeLabel);
        row.add(Box.createHorizontalStrut(8));
        row.add(codeField);
        card.add(row);
        card.add(Box.createVerticalStrut(12));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.add(resendButton);
        actions.add(submitButton);
        card.add(actions);
        extra.setOpaque(false);
        extra.setAlignmentX(LEFT_ALIGNMENT);
        extra.setVisible(false);
        card.add(Box.createVerticalStrut(8));
        card.add(extra);
        card.setBorder(BorderFactory.createCompoundBorder(card.getBorder(),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));
        add(card);

        submitButton.addActionListener(event -> submit());
        codeField.addActionListener(event -> submit());
        resendButton.addActionListener(event -> resend());
        show(signIn);
    }

    /** Ein weiterer Knopf unter den Aktionen (etwa „Zurück zu Gerendert“). */
    public void addAction(String text, Runnable action) {
        ComicButton button = new ComicButton(text);
        button.addActionListener(event -> action.run());
        extra.add(button);
        extra.setVisible(true);
        revalidate();
    }

    /** Setzt den Fokus ins Codefeld (nach dem Einblenden). */
    public void focusCode() {
        SwingUtilities.invokeLater(codeField::requestFocusInWindow);
    }

    private void show(ReaderSignIn next) {
        signIn = next;
        String hint = next.message().isEmpty() ? "Ein Einmalcode wurde per E-Mail versendet." : next.message();
        message.setText(hint + (next.service().isEmpty() ? "" : "\n(" + next.service() + ")"));
        error.setText(next.error());
        error.setVisible(!next.error().isEmpty());
        resendButton.setVisible(next.resendAvailable());
        setSending(false);
    }

    private void submit() {
        if (sending) {
            return;
        }
        char[] code = codeField.getPassword();
        if (code.length == 0) {
            codeField.requestFocusInWindow();
            return;
        }
        codeField.setText("");
        setSending(true);
        try {
            signIn.submit(code, this::answered, this::failed);
        } finally {
            Arrays.fill(code, '\0');
        }
    }

    private void resend() {
        if (sending) {
            return;
        }
        setSending(true);
        signIn.resend(this::answered, this::failed);
    }

    private void answered(ReaderSignIn next) {
        if (next == null) {
            accepted.run();
            return;
        }
        show(next);
        focusCode();
    }

    private void failed(String reason) {
        error.setText(reason == null || reason.trim().isEmpty() ? "Senden fehlgeschlagen (Protokoll)." : reason);
        error.setVisible(true);
        setSending(false);
    }

    private void setSending(boolean value) {
        sending = value;
        submitButton.setText(value ? SENDING_TEXT : SUBMIT_TEXT);
        submitButton.setEnabled(!value);
        resendButton.setEnabled(!value);
        codeField.setEnabled(!value);
        revalidate();
        repaint();
    }

    private JTextArea text(String value) {
        JTextArea area = new JTextArea(value);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setColumns(36);
        area.setForeground(palette.getInk());
        area.setFont(new JLabel().getFont());
        area.setAlignmentX(LEFT_ALIGNMENT);
        area.setBorder(BorderFactory.createEmptyBorder());
        return area;
    }

    // ---- für Tests

    JPasswordField codeField() {
        return codeField;
    }

    ComicButton submitButton() {
        return submitButton;
    }

    ComicButton resendButton() {
        return resendButton;
    }

    String errorText() {
        return error.isVisible() ? error.getText() : "";
    }
}
