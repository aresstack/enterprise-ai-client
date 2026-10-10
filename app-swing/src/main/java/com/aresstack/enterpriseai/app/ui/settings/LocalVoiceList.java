package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * „Lokale Stimmen“ im Reiter „Lokale Modelle“: je kuratierte Stimme eine Zeile mit Installationsstand und
 * „Installieren“ bzw. „Entfernen“, darunter eine Statuszeile mit Fortschritt oder Fehlergrund. Installieren wählt
 * nichts aus: eine installierte Stimme steht danach in der TTS-Liste darüber und wird dort gewählt.
 */
final class LocalVoiceList {

    static final String INSTALL_LABEL = "Installieren";
    static final String REMOVE_LABEL = "Entfernen";
    static final String NOT_INSTALLED = "Nicht installiert";
    static final String INSTALLED = "✓ installiert";
    static final String NONE = "Keine lokalen Stimmen konfiguriert.";

    /** Rückmeldungen an den Reiter. */
    interface Listener {

        /** Neuer Installationsstand aller angebotenen Stimmen (EDT). */
        void offers(List<LocalVoiceOffer> offers);

        /** Eine Stimme wurde entfernt (EDT). */
        void removed(String voiceId);
    }

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final Listener listener;
    private final ComicPalette palette;
    private final JPanel lines = new JPanel();
    private final JLabel status = new WrappingLabel(" ");
    private final JPanel panel = new JPanel(new BorderLayout(0, 6));
    private final List<ComicButton> buttons = new ArrayList<ComicButton>();
    private List<LocalVoiceOffer> offers = Collections.emptyList();
    private String loadedRoot;
    private boolean busy;

    LocalVoiceList(SettingsDialogActions actions, Supplier<SettingsForm> form, Listener listener,
                   ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.listener = listener;
        this.palette = palette;
        lines.setOpaque(false);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        status.setFont(status.getFont().deriveFont(Font.PLAIN, Math.max(11f, status.getFont().getSize2D() - 1f)));
        status.putClientProperty("html.disable", Boolean.TRUE);
        panel.setOpaque(false);
        panel.add(lines, BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        fill();
    }

    JPanel panel() {
        return panel;
    }

    /** Beim Anzeigen des Reiters den Installationsstand lesen, wenn sich das Modellverzeichnis geändert hat. */
    void shown() {
        if (!busy && !root().equals(loadedRoot)) {
            reload();
        }
    }

    private String root() {
        String root = form.get().localModelRoot();
        return root == null ? "" : root.trim();
    }

    private void reload() {
        loadedRoot = root();
        actions.localVoices(form.get(), new Consumer<List<LocalVoiceOffer>>() {
            @Override
            public void accept(List<LocalVoiceOffer> result) {
                offers = result == null ? Collections.<LocalVoiceOffer>emptyList()
                        : new ArrayList<LocalVoiceOffer>(result);
                fill();
                listener.offers(offers);
            }
        });
    }

    private void fill() {
        lines.removeAll();
        buttons.clear();
        if (offers.isEmpty()) {
            JLabel none = new JLabel(NONE);
            none.setForeground(FormRows.MUTED);
            none.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            lines.add(none);
        }
        for (LocalVoiceOffer offer : offers) {
            lines.add(line(offer));
        }
        updateButtons();
        lines.revalidate();
        lines.repaint();
    }

    private JPanel line(final LocalVoiceOffer offer) {
        JLabel name = new WrappingLabel(offer.displayName());
        name.putClientProperty("html.disable", Boolean.TRUE);
        name.setForeground(palette.getInk());
        name.setToolTipText(offer.id());
        JLabel state = new JLabel(offer.isInstalled() ? INSTALLED : NOT_INSTALLED);
        state.setForeground(offer.isInstalled() ? palette.getAgentPetrol() : FormRows.MUTED);
        ComicButton action = new ComicButton(offer.isInstalled() ? REMOVE_LABEL : INSTALL_LABEL, null,
                offer.isInstalled() ? ComicButton.Accent.CRITICAL : ComicButton.Accent.ACTION, palette);
        action.setToolTipText(offer.isInstalled() ? "Löscht die Dateien der Stimme aus dem Modellverzeichnis"
                : "Lädt die Stimme vom Hugging Face Hub ins Modellverzeichnis; gewählt wird sie in der TTS-Liste");
        action.getAccessibleContext().setAccessibleName(action.getText() + ": " + offer.displayName());
        action.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (offer.isInstalled()) {
                    remove(offer);
                } else {
                    install(offer);
                }
            }
        });
        buttons.add(action);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(state);
        right.add(action);
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        line.add(name, BorderLayout.CENTER);
        line.add(right, BorderLayout.EAST);
        line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return line;
    }

    private void install(final LocalVoiceOffer offer) {
        if (busy) {
            return;
        }
        busy = true;
        updateButtons();
        show("Lade " + offer.displayName() + " …", true);
        actions.installLocalVoice(form.get(), offer.id(), new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                show(line, true);
            }

            @Override
            public void finished(boolean installed, String message) {
                busy = false;
                if (installed) {
                    show("✓ " + offer.displayName() + " installiert; wählbar in der TTS-Liste oben.", true);
                    reload();
                } else {
                    show(message, false);
                    updateButtons();
                }
            }
        });
    }

    private void remove(final LocalVoiceOffer offer) {
        if (busy) {
            return;
        }
        busy = true;
        updateButtons();
        show("Entferne " + offer.displayName() + " …", true);
        actions.removeLocalVoice(form.get(), offer.id(), new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                show(line, true);
            }

            @Override
            public void finished(boolean removed, String message) {
                busy = false;
                if (removed) {
                    show("✓ " + offer.displayName() + " entfernt.", true);
                    listener.removed(offer.id());
                    reload();
                } else {
                    show(message, false);
                    updateButtons();
                }
            }
        });
    }

    private void updateButtons() {
        for (ComicButton button : buttons) {
            button.setEnabled(!busy);
        }
    }

    private void show(String text, boolean ok) {
        status.setText(text == null || text.isEmpty() ? " " : text);
        status.setForeground(ok ? palette.getAgentPetrol() : palette.getAccentOrange());
        status.setToolTipText(text == null || text.trim().isEmpty() ? null : text);
    }

    /** Die angezeigten Stimmen (Tests). */
    List<LocalVoiceOffer> offers() {
        return Collections.unmodifiableList(offers);
    }
}
