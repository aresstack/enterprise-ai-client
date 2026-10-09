package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Der Knopf „In KeePass prüfen“ mit Ergebniszeile. Während die Probe läuft, ist der Knopf gesperrt; das
 * Ergebnis kommt über {@link SettingsDialogActions#checkSecret} auf dem EDT zurück und erscheint in Grün
 * (gefunden) oder Rot (nicht gefunden, nicht erreichbar, abgebrochen).
 */
final class SecretCheckRow extends JPanel {

    static final String CHECK_LABEL = "In KeePass prüfen";
    static final String RUNNING_LABEL = "Prüfe … (bei Bedarf öffnet sich der Pairing-Dialog)";

    private final ComicButton button;
    private final JLabel result = new JLabel(" ");
    private final ComicPalette palette;

    SecretCheckRow(final SettingsDialogActions actions, final Supplier<SettingsForm> form,
                   final Supplier<String> secretRef, ComicPalette palette) {
        super(new BorderLayout(8, 2));
        this.palette = palette;
        this.button = new ComicButton(CHECK_LABEL, null, ComicButton.Accent.ACTION, palette);
        setOpaque(false);
        button.setToolTipText("Verbindet sich mit KeePassRPC und sucht den Eintrag mit diesem Titel");
        result.setFont(result.getFont().deriveFont(Font.PLAIN, Math.max(11f, result.getFont().getSize2D() - 1f)));
        result.setForeground(palette.getInk());
        add(button, BorderLayout.WEST);
        add(result, BorderLayout.CENTER);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                button.setEnabled(false);
                showText(RUNNING_LABEL, false);
                try {
                    actions.checkSecret(form.get(), secretRef.get(), new Consumer<SecretCheckResult>() {
                        @Override
                        public void accept(SecretCheckResult checked) {
                            button.setEnabled(true);
                            showText(checked.message(), !checked.isSuccess());
                            result.setForeground(checked.isSuccess() ? SecretCheckRow.this.palette.getAgentPetrol()
                                    : SecretCheckRow.this.palette.getAccentRed());
                        }
                    });
                } catch (RuntimeException ex) {
                    button.setEnabled(true);
                    showText("Prüfung konnte nicht gestartet werden: " + ex.getClass().getSimpleName(), true);
                }
            }
        });
    }

    private void showText(String text, boolean problem) {
        result.setText(text);
        result.setForeground(problem ? palette.getAccentRed() : palette.getInk());
        result.setToolTipText(text);
    }

    ComicButton button() {
        return button;
    }

    String resultText() {
        return result.getText();
    }
}
