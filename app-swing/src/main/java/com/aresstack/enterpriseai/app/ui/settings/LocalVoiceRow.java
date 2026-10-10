package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * „Lokale Stimme“: Dropdown der kuratierten Stimmen mit Installationsstand, ein Knopf „Installieren und
 * verwenden“ (installierte Stimme: „Verwenden“) und eine Statuszeile mit Fortschritt oder Fehlergrund. Verwenden
 * setzt die Auswahl der Kategorie TTS im Reiter „Modelle“; wirksam wie alles im Dialog nach dem Speichern.
 */
final class LocalVoiceRow {

    static final String INSTALL_LABEL = "Installieren und verwenden";
    static final String USE_LABEL = "Verwenden";
    static final String INSTALLING_LABEL = "Installiere …";
    static final String NONE = "Keine lokalen Stimmen konfiguriert.";

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final Consumer<String> useVoice;
    private final ComicPalette palette;
    private final JComboBox<Choice> combo = new JComboBox<Choice>();
    private final JLabel status = new JLabel(" ");
    private final ComicButton action;
    private String loadedRoot;
    private boolean busy;

    /** @param useVoice übernimmt den gespeicherten Wert ({@code local:<id>}) als TTS-Auswahl */
    LocalVoiceRow(FormRows rows, SettingsDialogActions actions, Supplier<SettingsForm> form, Consumer<String> useVoice,
                  ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.useVoice = useVoice;
        this.palette = palette;
        combo.setForeground(palette.getInk());
        combo.setBackground(Color.WHITE);
        combo.getAccessibleContext().setAccessibleName("Lokale Stimme");
        combo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateButton();
            }
        });
        action = new ComicButton(INSTALL_LABEL, null, ComicButton.Accent.ACTION, palette);
        action.setToolTipText("Lädt die Stimme ins Modellverzeichnis des Sidecars und wählt sie als TTS-Modell");
        action.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                run();
            }
        });
        status.setFont(status.getFont().deriveFont(Font.PLAIN, Math.max(11f, status.getFont().getSize2D() - 1f)));
        status.putClientProperty("html.disable", Boolean.TRUE);
        JPanel below = new JPanel(new BorderLayout(8, 0));
        below.setOpaque(false);
        below.add(status, BorderLayout.CENTER);
        below.add(action, BorderLayout.EAST);
        rows.component("Lokale Stimme", combo, "Kuratierte Stimmen für den lokalen Sidecar");
        rows.component("", below, null);
        updateButton();
    }

    /** Beim Anzeigen des Reiters den Installationsstand lesen, wenn sich das Modellverzeichnis geändert hat. */
    void shown() {
        if (!busy && !root().equals(loadedRoot)) {
            reload(null);
        }
    }

    private String root() {
        String root = form.get().localModelRoot();
        return root == null ? "" : root.trim();
    }

    private void reload(final String select) {
        loadedRoot = root();
        actions.localVoices(form.get(), new Consumer<List<LocalVoiceOffer>>() {
            @Override
            public void accept(List<LocalVoiceOffer> offers) {
                Choice previous = (Choice) combo.getSelectedItem();
                String keep = select != null ? select : previous == null ? null : previous.offer.id();
                combo.removeAllItems();
                for (LocalVoiceOffer offer : offers) {
                    Choice choice = new Choice(offer);
                    combo.addItem(choice);
                    if (offer.id().equals(keep)) {
                        combo.setSelectedItem(choice);
                    }
                }
                if (offers.isEmpty()) {
                    show(NONE, false);
                }
                updateButton();
            }
        });
    }

    private void run() {
        final Choice selected = (Choice) combo.getSelectedItem();
        if (selected == null || busy) {
            return;
        }
        if (selected.offer.isInstalled()) {
            use(selected.offer);
            return;
        }
        busy = true;
        updateButton();
        show("Lade " + selected.offer.displayName() + " …", true);
        actions.installLocalVoice(form.get(), selected.offer.id(), new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                show(line, true);
            }

            @Override
            public void finished(boolean installed, String message) {
                busy = false;
                if (installed) {
                    use(selected.offer);
                    reload(selected.offer.id());
                } else {
                    show(message, false);
                    updateButton();
                }
            }
        });
    }

    private void use(LocalVoiceOffer offer) {
        useVoice.accept(actions.localVoiceSelection(offer.id()));
        show("✓ " + offer.displayName() + " ist als TTS-Modell gewählt; gilt nach dem Speichern.", true);
    }

    private void updateButton() {
        Choice selected = (Choice) combo.getSelectedItem();
        action.setEnabled(!busy && selected != null);
        action.setText(busy ? INSTALLING_LABEL : selected != null && selected.offer.isInstalled() ? USE_LABEL
                : INSTALL_LABEL);
        combo.setEnabled(!busy && combo.getItemCount() > 0);
    }

    private void show(String text, boolean ok) {
        status.setText(text == null || text.isEmpty() ? " " : text);
        status.setForeground(ok ? palette.getAgentPetrol() : palette.getAccentOrange());
        status.setToolTipText(text == null || text.trim().isEmpty() ? null : text);
    }

    JComboBox<Choice> combo() {
        return combo;
    }

    /** Ein Eintrag im Dropdown. */
    static final class Choice {

        private final LocalVoiceOffer offer;

        Choice(LocalVoiceOffer offer) {
            this.offer = offer;
        }

        @Override
        public String toString() {
            return offer.displayName() + (offer.isInstalled() ? "   ✓ installiert" : "");
        }
    }
}
