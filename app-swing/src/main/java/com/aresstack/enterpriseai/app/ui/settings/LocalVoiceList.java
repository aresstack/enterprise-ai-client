package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Component;
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
 * Gruppen „Installiert“ ({@link #panel()}) und „Vorschläge“ ({@link #suggestionsPanel()}) im Reiter „Lokale Modelle“:
 * je Modell eine Zeile mit Installationsstand und „Installieren“ bzw. „Aktualisieren“ und „Entfernen“. Installieren
 * wählt nichts aus: ein installiertes Modell steht danach unter „Installiert“ und wird oben je Funktion gewählt. Dazu
 * die Hugging-Face-Suche ({@link #searchPanel()}): Funktion, Suchfeld mit den letzten Suchbegriffen als Dropdown und
 * Treffer, nur Modelle, die der Client ohne Umwandlung lädt; ihre Zeilen haben dieselben Knöpfe. Fortschritt und
 * Ergebnis stehen unter der Zeile, aus der installiert wurde: je Datei ein Balken bis 100 % mit „Datei n von m“ darin.
 */
final class LocalVoiceList {

    static final String INSTALL_LABEL = "Installieren";
    static final String REMOVE_LABEL = "Entfernen";
    static final String UPDATE_LABEL = "Aktualisieren";
    static final String NOT_INSTALLED = "Nicht installiert";
    static final String INSTALLED = "✓ installiert";
    static final String NONE = "Noch keine lokalen Modelle installiert.";
    static final String NO_SUGGESTIONS = "Alle Vorschläge sind installiert.";
    static final String SEARCH_LABEL = "Suchen";
    static final String NO_HITS = "Keine im Client ladbaren Modelle gefunden.";
    static final String ALL_CATEGORIES = "Alle Funktionen";
    private static final String HIT = "hit:";
    private static final String OFFER = "offer:";
    /** Kategorien der Suche, in der Reihenfolge der Auswahl nach „Alle Funktionen“. */
    private static final ModelCategory[] SEARCH_CATEGORIES = {ModelCategory.CHAT, ModelCategory.EMBEDDING,
            ModelCategory.TTS, ModelCategory.STT};

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
    private final JPanel panel = new JPanel(new BorderLayout(0, 6));
    private final List<ComicButton> buttons = new ArrayList<ComicButton>();
    private List<LocalVoiceOffer> offers = Collections.emptyList();
    private final JPanel searchPanel = new JPanel(new BorderLayout(0, 6));
    /** Suchfeld mit den letzten Suchbegriffen als Dropdown (editierbar). */
    private final JComboBox<String> query = new JComboBox<String>();
    private final JLabel searchStatus = new WrappingLabel(" ");
    private boolean fillingHistory;
    /** Vorgeschlagene, nicht installierte Modelle (eigene Gruppe „Vorschläge“). */
    private final JPanel suggested = new JPanel();
    private final JComboBox<String> category = FormRows.<String>fittingComboBox();
    private final ComicButton searchButton;
    private final JPanel hits = new JPanel();
    /** {@code null}: noch nicht gesucht. */
    private List<LocalVoiceOffer> found;
    private String loadedRoot;
    private boolean busy;
    /** Zeile, aus der gerade installiert/entfernt wird bzw. zuletzt wurde: {@code hit:<id>} oder {@code offer:<id>}. */
    private String rowKey;
    private String rowMessage;
    private boolean rowOk = true;
    /** Download läuft in {@link #rowKey}: Balken und Text der aktuellen Datei. */
    private boolean downloading;
    private int filePercent = -1;
    private String fileText = "";
    private JProgressBar rowBar;
    private JLabel rowLabel;
    private boolean rowRendered;

    LocalVoiceList(SettingsDialogActions actions, Supplier<SettingsForm> form, Listener listener,
                   ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.listener = listener;
        this.palette = palette;
        lines.setOpaque(false);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        searchStatus.setFont(small(searchStatus.getFont()));
        searchStatus.putClientProperty("html.disable", Boolean.TRUE);
        panel.setOpaque(false);
        panel.add(lines, BorderLayout.CENTER);

        suggested.setOpaque(false);
        suggested.setLayout(new BoxLayout(suggested, BoxLayout.Y_AXIS));
        query.setEditable(true);
        query.setRenderer(new HistoryRenderer());
        query.setToolTipText("Suchbegriff, z. B. „thorsten“, „multilingual-e5“ oder „qwen instruct“; leer = beliebteste. "
                + "Das Dropdown zeigt die letzten Suchbegriffe.");
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
        query.getEditor().addActionListener(search);
        query.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // ein Begriff aus der Historie gewählt (nicht getippt): sofort suchen
                if (!fillingHistory && query.isPopupVisible() && "comboBoxChanged".equals(e.getActionCommand())
                        && query.getSelectedIndex() >= 0) {
                    search();
                }
            }
        });
        category.addItem(ALL_CATEGORIES);
        for (ModelCategory searchable : SEARCH_CATEGORIES) {
            category.addItem(searchable.displayName());
        }
        fillHistory(actions.hubSearchHistory());
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
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(searchRow, BorderLayout.NORTH);
        top.add(searchStatus, BorderLayout.SOUTH);
        searchPanel.add(top, BorderLayout.NORTH);
        searchPanel.add(hits, BorderLayout.CENTER);
        fill();
    }

    /** Gruppe „Vorschläge“: vorgeschlagene Modelle, die noch nicht installiert sind. */
    JPanel suggestionsPanel() {
        return suggested;
    }

    private static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(FormRows.MUTED);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
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
        showSearch("Suche auf dem Hugging Face Hub …", true);
        String text = queryText();
        if (!text.isEmpty()) {
            fillHistory(actions.rememberHubSearch(text));
        }
        actions.searchLocalVoices(form.get(), text, selectedCategory(), new LocalVoiceSearchCallback() {
            @Override
            public void found(List<LocalVoiceOffer> result) {
                busy = false;
                found = new ArrayList<LocalVoiceOffer>(result);
                if (rowKey != null && rowKey.startsWith(HIT)) {
                    rowKey = null;
                }
                showSearch(result.isEmpty() ? NO_HITS : result.size() + " ladbare Modelle gefunden.", true);
                fill();
            }

            @Override
            public void failed(String message) {
                busy = false;
                showSearch(message, false);
                updateButtons();
            }
        });
    }

    /** Der Text im Suchfeld (getippt oder aus einem Vorschlag). */
    private String queryText() {
        Object item = query.getEditor().getItem();
        return item == null ? "" : item.toString().trim();
    }

    /** Das Dropdown mit den letzten Suchbegriffen; der Text im Feld bleibt. */
    private void fillHistory(List<String> history) {
        Object typed = query.getEditor().getItem();
        fillingHistory = true;
        try {
            query.removeAllItems();
            if (history != null) {
                for (String entry : history) {
                    query.addItem(entry);
                }
            }
            query.setSelectedIndex(-1);
            query.getEditor().setItem(typed == null ? "" : typed.toString());
        } finally {
            fillingHistory = false;
        }
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
        suggested.removeAll();
        rowBar = null;
        rowLabel = null;
        rowRendered = false;
        for (LocalVoiceOffer offer : offers) {
            (offer.isInstalled() ? lines : suggested).add(line(offer, OFFER + offer.id()));
        }
        if (lines.getComponentCount() == 0) {
            lines.add(muted(NONE));
        }
        if (suggested.getComponentCount() == 0) {
            suggested.add(muted(NO_SUGGESTIONS));
        }
        hits.removeAll();
        if (found != null && found.isEmpty()) {
            hits.add(muted(NO_HITS));
        }
        if (found != null) {
            for (LocalVoiceOffer hit : found) {
                hits.add(line(withInstalledState(hit), HIT + hit.id()));
            }
        }
        if (rowKey != null && !rowRendered && !downloading) {
            // ein installierter Vorschlag steht jetzt unter „Installiert“ (ggf. unter seinem Ordnernamen)
            JComponent state = rowState();
            state.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            (rowKey.startsWith(HIT) ? hits : lines).add(state);
        }
        updateButtons();
        lines.revalidate();
        lines.repaint();
        suggested.revalidate();
        suggested.repaint();
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
                : new LocalVoiceOffer(hit.id(), hit.displayName(), hit.language(), installed, hit.category());
    }

    private JPanel line(final LocalVoiceOffer offer, final String key) {
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
                    remove(offer, key);
                } else {
                    download(offer, key, false);
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
                    download(offer, key, true);
                }
            });
            buttons.add(update);
            right.add(update);
        }
        right.add(action);
        JPanel head = new JPanel(new BorderLayout(8, 0));
        head.setOpaque(false);
        head.add(name, BorderLayout.CENTER);
        head.add(right, BorderLayout.EAST);
        JPanel line = new JPanel(new BorderLayout(0, 3));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        line.add(head, BorderLayout.NORTH);
        if (key.equals(rowKey)) {
            line.add(rowState(), BorderLayout.CENTER);
            rowRendered = true;
        }
        line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return line;
    }

    /** Unter der aktiven Zeile: Balken der aktuellen Datei („Datei n von m“ darin) oder Meldung. */
    private JComponent rowState() {
        if (downloading) {
            rowBar = new JProgressBar(0, 100);
            rowBar.setStringPainted(true);
            rowBar.setForeground(palette.getAgentPetrol());
            rowBar.getAccessibleContext().setAccessibleName("Download-Fortschritt");
            paintBar();
            return rowBar;
        }
        rowLabel = new WrappingLabel(rowMessage == null || rowMessage.isEmpty() ? " " : rowMessage);
        rowLabel.setFont(small(rowLabel.getFont()));
        rowLabel.putClientProperty("html.disable", Boolean.TRUE);
        rowLabel.setForeground(rowOk ? palette.getAgentPetrol() : palette.getAccentOrange());
        rowLabel.setToolTipText(rowMessage == null || rowMessage.trim().isEmpty() ? null : rowMessage);
        return rowLabel;
    }

    private void paintBar() {
        if (rowBar == null) {
            return;
        }
        rowBar.setIndeterminate(filePercent < 0);
        rowBar.setValue(Math.max(0, filePercent));
        rowBar.setString(fileText);
        rowBar.setToolTipText(fileText);
    }

    private void download(final LocalVoiceOffer offer, String key, final boolean update) {
        if (busy) {
            return;
        }
        busy = true;
        rowKey = key;
        downloading = true;
        filePercent = -1;
        fileText = "Lade " + offer.displayName() + " …";
        fill();
        LocalVoiceInstallProgress progress = new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                filePercent = -1;
                fileText = line;
                paintBar();
            }

            @Override
            public void file(String name, int number, int count, long done, long total) {
                filePercent = DownloadText.percent(done, total);
                fileText = DownloadText.line(name, number, count, done, total);
                paintBar();
            }

            @Override
            public void finished(boolean installed, String message) {
                busy = false;
                downloading = false;
                if (installed) {
                    showRow("✓ " + offer.displayName() + (update ? " aktualisiert."
                            : " installiert; wählbar oben unter „" + offer.category().displayName() + "“."), true);
                    reload();
                } else {
                    showRow(message, false);
                }
            }
        };
        if (update) {
            actions.updateLocalVoice(form.get(), offer.id(), progress);
        } else {
            actions.installLocalVoice(form.get(), offer.id(), progress);
        }
    }

    private void remove(final LocalVoiceOffer offer, String key) {
        if (busy) {
            return;
        }
        busy = true;
        rowKey = key;
        downloading = false;
        rowMessage = "Entferne " + offer.displayName() + " …";
        rowOk = true;
        fill();
        actions.removeLocalVoice(form.get(), offer.id(), new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                showRow(line, true);
            }

            @Override
            public void finished(boolean removed, String message) {
                busy = false;
                if (removed) {
                    showRow("✓ " + offer.displayName() + " entfernt.", true);
                    listener.removed(offer.id());
                    reload();
                } else {
                    showRow(message, false);
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

    /** Meldung unter der aktiven Zeile; baut die Zeilen neu (Knöpfe wieder frei). */
    private void showRow(String text, boolean ok) {
        rowMessage = text;
        rowOk = ok;
        fill();
    }

    private void showSearch(String text, boolean ok) {
        searchStatus.setText(text == null || text.isEmpty() ? " " : text);
        searchStatus.setForeground(ok ? palette.getAgentPetrol() : palette.getAccentOrange());
        searchStatus.setToolTipText(text == null || text.trim().isEmpty() ? null : text);
    }

    private static Font small(Font font) {
        return font.deriveFont(Font.PLAIN, Math.max(11f, font.getSize2D() - 1f));
    }

    /** Historie im Dropdown: nur Text, nie HTML. */
    private static final class HistoryRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.putClientProperty("html.disable", Boolean.TRUE);
            return label;
        }
    }

    /** Die angezeigten Stimmen (Tests). */
    List<LocalVoiceOffer> offers() {
        return Collections.unmodifiableList(offers);
    }
}
