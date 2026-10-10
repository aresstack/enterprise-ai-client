package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
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
 * „Installiert und vorgeschlagen“ im Reiter „Lokale Modelle“: je vorgeschlagene oder installierte Stimme eine Zeile mit Installationsstand und
 * „Installieren“ bzw. „Aktualisieren“ und „Entfernen“, darunter eine Statuszeile mit Fortschritt oder Fehlergrund. Installieren wählt
 * nichts aus: eine installierte Stimme steht danach in der TTS-Liste darüber und wird dort gewählt. Dazu die
 * Hugging-Face-Suche ({@link #searchPanel()}): Kategorie (Chat, Embeddings, Sprachausgabe oder alle), Suchfeld und
 * Treffer, nur Modelle, die der Client ohne Umwandlung lädt; ihre Zeilen haben dieselben Knöpfe.
 */
final class LocalVoiceList {

    static final String INSTALL_LABEL = "Installieren";
    static final String REMOVE_LABEL = "Entfernen";
    static final String UPDATE_LABEL = "Aktualisieren";
    static final String NOT_INSTALLED = "Nicht installiert";
    static final String INSTALLED = "✓ installiert";
    static final String NONE = "Keine lokalen Modelle installiert oder vorgeschlagen.";
    static final String SEARCH_LABEL = "Suchen";
    static final String NO_HITS = "Keine im Client ladbaren Modelle gefunden.";
    static final String ALL_CATEGORIES = "Alle Funktionen";
    /** Kategorien der Suche, in der Reihenfolge der Auswahl nach „Alle Funktionen“. */
    private static final ModelCategory[] SEARCH_CATEGORIES = {ModelCategory.CHAT, ModelCategory.EMBEDDING,
            ModelCategory.TTS};

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
    private final JPanel searchPanel = new JPanel(new BorderLayout(0, 6));
    private final JTextField query = new JTextField(24);
    private final JComboBox<String> category = FormRows.<String>fittingComboBox();
    private final ComicButton searchButton;
    private final JPanel hits = new JPanel();
    /** {@code null}: noch nicht gesucht. */
    private List<LocalVoiceOffer> found;
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

        query.setToolTipText("Suchbegriff, z. B. „thorsten“, „multilingual-e5“ oder „qwen instruct“; leer = beliebteste");
        query.getAccessibleContext().setAccessibleName("Hugging Face durchsuchen");
        searchButton = new ComicButton(SEARCH_LABEL, null, ComicButton.Accent.ACTION, palette);
        searchButton.setToolTipText("Sucht auf dem Hugging Face Hub über die Netzwerkeinstellungen");
        ActionListener search = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                search();
            }
        };
        searchButton.addActionListener(search);
        query.addActionListener(search);
        category.addItem(ALL_CATEGORIES);
        for (ModelCategory searchable : SEARCH_CATEGORIES) {
            category.addItem(searchable.displayName());
        }
        category.setToolTipText("Nur Modelle dieser Funktion suchen");
        category.getAccessibleContext().setAccessibleName("Funktion der Suche");
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(category, BorderLayout.WEST);
        searchRow.add(query, BorderLayout.CENTER);
        searchRow.add(searchButton, BorderLayout.EAST);
        hits.setOpaque(false);
        hits.setLayout(new BoxLayout(hits, BoxLayout.Y_AXIS));
        searchPanel.setOpaque(false);
        searchPanel.add(searchRow, BorderLayout.NORTH);
        searchPanel.add(hits, BorderLayout.CENTER);
        fill();
    }

    /** Suchfeld und Treffer der Hugging-Face-Suche. */
    JPanel searchPanel() {
        return searchPanel;
    }

    private void search() {
        if (busy) {
            return;
        }
        busy = true;
        updateButtons();
        show("Suche auf dem Hugging Face Hub …", true);
        actions.searchLocalVoices(form.get(), query.getText(), selectedCategory(), new LocalVoiceSearchCallback() {
            @Override
            public void found(List<LocalVoiceOffer> result) {
                busy = false;
                found = new ArrayList<LocalVoiceOffer>(result);
                show(result.isEmpty() ? NO_HITS : result.size() + " ladbare Modelle gefunden.", true);
                fill();
            }

            @Override
            public void failed(String message) {
                busy = false;
                show(message, false);
                updateButtons();
            }
        });
    }

    /** Die gewählte Funktion der Suche; {@code null} = alle. */
    private ModelCategory selectedCategory() {
        int index = category.getSelectedIndex();
        return index <= 0 ? null : SEARCH_CATEGORIES[index - 1];
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
        hits.removeAll();
        if (found != null && found.isEmpty()) {
            JLabel none = new JLabel(NO_HITS);
            none.setForeground(FormRows.MUTED);
            none.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            hits.add(none);
        }
        if (found != null) {
            for (LocalVoiceOffer hit : found) {
                hits.add(line(withInstalledState(hit)));
            }
        }
        updateButtons();
        lines.revalidate();
        lines.repaint();
        hits.revalidate();
        hits.repaint();
    }

    /** Ein Treffer mit dem Installationsstand aus der zuletzt gelesenen Liste (nach Installieren/Entfernen). */
    private LocalVoiceOffer withInstalledState(LocalVoiceOffer hit) {
        boolean installed = false;
        for (LocalVoiceOffer offer : offers) {
            if (offer.id().equals(hit.id())) {
                installed = offer.isInstalled();
            }
        }
        return installed == hit.isInstalled() ? hit
                : new LocalVoiceOffer(hit.id(), hit.displayName(), hit.language(), installed);
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
                : "Lädt die Dateien unverändert vom Hugging Face Hub ins Modellverzeichnis; gewählt wird in der TTS-Liste");
        action.getAccessibleContext().setAccessibleName(action.getText() + ": " + offer.displayName());
        action.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (offer.isInstalled()) {
                    remove(offer);
                } else {
                    download(offer, false);
                }
            }
        });
        buttons.add(action);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(state);
        if (offer.isInstalled()) {
            ComicButton update = new ComicButton(UPDATE_LABEL, null, ComicButton.Accent.ACTION, palette);
            update.setToolTipText("Lädt die Dateien der Stimme neu vom Hugging Face Hub; die Auswahl bleibt");
            update.getAccessibleContext().setAccessibleName(UPDATE_LABEL + ": " + offer.displayName());
            update.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    download(offer, true);
                }
            });
            buttons.add(update);
            right.add(update);
        }
        right.add(action);
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        line.add(name, BorderLayout.CENTER);
        line.add(right, BorderLayout.EAST);
        line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return line;
    }

    private void download(final LocalVoiceOffer offer, final boolean update) {
        if (busy) {
            return;
        }
        busy = true;
        updateButtons();
        show("Lade " + offer.displayName() + " …", true);
        LocalVoiceInstallProgress progress = new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                show(line, true);
            }

            @Override
            public void finished(boolean installed, String message) {
                busy = false;
                if (installed) {
                    show("✓ " + offer.displayName() + (update ? " aktualisiert."
                            : " installiert; wählbar in der TTS-Liste oben."), true);
                    reload();
                } else {
                    show(message, false);
                    updateButtons();
                }
            }
        };
        if (update) {
            actions.updateLocalVoice(form.get(), offer.id(), progress);
        } else {
            actions.installLocalVoice(form.get(), offer.id(), progress);
        }
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
        if (searchButton != null) {
            searchButton.setEnabled(!busy);
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
