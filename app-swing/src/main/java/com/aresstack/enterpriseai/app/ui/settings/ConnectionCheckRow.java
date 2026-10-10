package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Der Knopf „Verbindung testen“ des Reiters KI-Dienst mit Zusammenfassung und der Liste der Schritte, wie sie
 * eintreffen: grün (in Ordnung), orange (Hinweis), rot (fehlgeschlagen), grau (Information). Während der Test
 * läuft, ist der Knopf gesperrt; Schritte und Ende kommen über {@link SettingsDialogActions#checkConnection}
 * auf dem EDT.
 */
final class ConnectionCheckRow extends JPanel {

    static final String CHECK_LABEL = "Verbindung testen";
    static final String RUNNING_LABEL = "Prüfe … (bei Bedarf öffnet sich der Pairing-Dialog)";
    static final String SUCCESS_LABEL = "Verbindung in Ordnung.";
    static final String SUCCESS_WITH_NOTES_LABEL = "Verbindung steht; bitte die Hinweise beachten.";
    static final String FAILURE_LABEL = "Verbindung fehlgeschlagen; der rote Schritt nennt die Ursache.";

    private static final int LINE_WIDTH = 440;

    private final ComicButton button;
    private final JLabel summary = new JLabel(" ");
    private final JPanel steps = new JPanel();
    private final List<ConnectionCheckStep> reported = new ArrayList<ConnectionCheckStep>();
    private final ComicPalette palette;

    ConnectionCheckRow(final SettingsDialogActions actions, final Supplier<SettingsForm> form, ComicPalette palette) {
        super(new BorderLayout(0, 4));
        this.palette = palette;
        this.button = new ComicButton(CHECK_LABEL, null, ComicButton.Accent.ACTION, palette);
        setOpaque(false);
        button.setToolTipText("Prüft mit dem aktuellen Entwurf API-Key aus KeePass, Proxy-Route, HTTPS-Verbindung, "
                + "GET /models mit HTTP-Status sowie Chat- und Embedding-Modell");
        summary.setFont(smaller(summary.getFont()));
        summary.setForeground(palette.getInk());
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(button, BorderLayout.WEST);
        head.add(summary, BorderLayout.CENTER);
        steps.setOpaque(false);
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        add(head, BorderLayout.NORTH);
        add(steps, BorderLayout.CENTER);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                start(actions, form);
            }
        });
    }

    private void start(SettingsDialogActions actions, Supplier<SettingsForm> form) {
        button.setEnabled(false);
        reported.clear();
        steps.removeAll();
        showSummary(RUNNING_LABEL, palette.getInk());
        refresh();
        try {
            actions.checkConnection(form.get(), new ConnectionCheckListener() {
                @Override
                public void onStep(ConnectionCheckStep step) {
                    reported.add(step);
                    steps.add(line(step));
                    refresh();
                }

                @Override
                public void onFinished(boolean success) {
                    button.setEnabled(true);
                    if (!success) {
                        showSummary(FAILURE_LABEL, ConnectionCheckRow.this.palette.getAccentRed());
                    } else if (hasWarnings()) {
                        showSummary(SUCCESS_WITH_NOTES_LABEL, ConnectionCheckRow.this.palette.getAccentOrange());
                    } else {
                        showSummary(SUCCESS_LABEL, ConnectionCheckRow.this.palette.getAgentPetrol());
                    }
                    refresh();
                }
            });
        } catch (RuntimeException ex) {
            button.setEnabled(true);
            showSummary("Prüfung konnte nicht gestartet werden: " + ex.getClass().getSimpleName(),
                    palette.getAccentRed());
            refresh();
        }
    }

    private boolean hasWarnings() {
        for (ConnectionCheckStep step : reported) {
            if (step.status() == ConnectionCheckStep.Status.WARNING) {
                return true;
            }
        }
        return false;
    }

    private void showSummary(String text, Color color) {
        summary.setText(text);
        summary.setForeground(color);
        summary.setToolTipText(text);
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    private JLabel line(ConnectionCheckStep step) {
        String text = marker(step.status()) + " " + step.title() + ": " + step.detail();
        JLabel label = new JLabel("<html><body style='width: " + LINE_WIDTH + "px'>" + FormRows.escape(text)
                + "</body></html>");
        label.setFont(smaller(label.getFont()));
        label.setForeground(color(step.status()));
        label.setToolTipText(text);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }

    static String marker(ConnectionCheckStep.Status status) {
        switch (status) {
            case OK:
                return "✓";
            case WARNING:
                return "!";
            case FAILED:
                return "✗";
            default:
                return "–";
        }
    }

    private Color color(ConnectionCheckStep.Status status) {
        switch (status) {
            case OK:
                return palette.getAgentPetrol();
            case WARNING:
                return palette.getAccentOrange();
            case FAILED:
                return palette.getAccentRed();
            default:
                return FormRows.MUTED;
        }
    }

    private static Font smaller(Font font) {
        return font.deriveFont(Font.PLAIN, Math.max(11f, font.getSize2D() - 1f));
    }

    ComicButton button() {
        return button;
    }

    String summaryText() {
        return summary.getText();
    }

    /** Die bisher gemeldeten Schritte des letzten Laufs, in Reihenfolge. */
    List<ConnectionCheckStep> steps() {
        return Collections.unmodifiableList(new ArrayList<ConnectionCheckStep>(reported));
    }
}
