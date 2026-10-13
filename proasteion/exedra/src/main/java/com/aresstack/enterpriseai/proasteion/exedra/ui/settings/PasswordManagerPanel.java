package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.exedra.ui.icon.ApplicationIcons;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowShape;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Die Passwortverwaltung aus MainframeMate ({@code ShowPasswordsMenuCommand}, Dialog „Passwörter verwalten“) als
 * Teil des Reiters „KeePass“: Tabelle aller KeePass-Einträge mit denselben Spalten, darunter „Alle Passwörter
 * anzeigen“, Kopieren, Neu, Bearbeiten, Löschen. Passwörter werden erst beim Anzeigen oder Kopieren einzeln gelesen
 * und beim Verbergen überschrieben. Fehler (KeePass aus, Datenbank gesperrt, Ablehnung) stehen rot unter der
 * Tabelle. Geladen wird beim ersten Sichtbarwerden.
 */
final class PasswordManagerPanel {

    private static final int PASSWORD_COLUMN = 5;
    private static final int EYE_COLUMN = 12;
    private static final String HIDDEN = "••••••••";

    private final Supplier<PasswordStore> store;
    private final ComicPalette palette;
    private final EntryModel model = new EntryModel();
    private final JTable table = new JTable(model);
    private final JLabel heading = new JLabel("Passwörter");
    private final JLabel status = new JLabel(" ");
    private final JCheckBox showAll = new JCheckBox("Alle Passwörter anzeigen");
    private final JPanel panel = new JPanel(new BorderLayout(0, 6));
    private boolean loaded;

    PasswordManagerPanel(Supplier<PasswordStore> store, ComicPalette palette) {
        this.store = store;
        this.palette = palette;
        panel.setOpaque(false);
        heading.setForeground(palette.getInk());
        heading.setFont(ResearchUiTypography.semiBold(13f));
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(EYE_COLUMN).setMaxWidth(36);
        table.getColumnModel().getColumn(EYE_COLUMN).setHeaderValue("");
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                int viewRow = table.rowAtPoint(event.getPoint());
                int viewColumn = table.columnAtPoint(event.getPoint());
                if (viewRow < 0 || viewColumn < 0) {
                    return;
                }
                int row = table.convertRowIndexToModel(viewRow);
                if (table.convertColumnIndexToModel(viewColumn) == EYE_COLUMN) {
                    toggle(row);
                } else if (event.getClickCount() == 2) {
                    edit();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(640, 220));

        showAll.setOpaque(false);
        showAll.setForeground(palette.getInk());
        showAll.addActionListener(event -> showAllChanged());
        ComposerButton copy = new ComposerButton(null, "📋 Kopieren", false, "Passwort in die Zwischenablage");
        ComposerButton add = new ComposerButton(null, "➕ Neu", false, "Neuen KeePass-Eintrag anlegen");
        ComposerButton edit = new ComposerButton(null, "✏️ Bearbeiten", false, "Ausgewählten KeePass-Eintrag bearbeiten");
        ComposerButton remove = new ComposerButton(null, "🗑 Löschen", false, "Ausgewählten KeePass-Eintrag löschen");
        copy.addActionListener(event -> copy());
        add.addActionListener(event -> create());
        edit.addActionListener(event -> edit());
        remove.addActionListener(event -> remove());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(showAll);
        buttons.add(copy);
        buttons.add(add);
        buttons.add(edit);
        buttons.add(remove);
        status.setForeground(ResearchUiPalette.DANGER_RED);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.setOpaque(false);
        south.add(buttons, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        panel.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing() && !loaded) {
                refresh(null);
            }
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !panel.isShowing()) {
                model.hideAll();
            }
        });
    }

    JPanel panel() {
        return panel;
    }

    /** Lädt die Einträge neu und wählt danach {@code select} aus. */
    void refresh(final String select) {
        loaded = true;
        status.setText("Lade Einträge aus KeePass …");
        store.get().list((entries, error) -> {
            if (error != null) {
                model.set(Collections.<PasswordEntry>emptyList());
                showError("Fehler beim Laden der Passwort-Einträge: " + error);
                return;
            }
            model.set(entries);
            status.setText(" ");
            heading.setText("Passwörter  (" + entries.size() + " Einträge)");
            if (select != null) {
                for (int row = 0; row < entries.size(); row++) {
                    if (entries.get(row).title().equalsIgnoreCase(select)) {
                        int view = table.convertRowIndexToView(row);
                        table.getSelectionModel().setSelectionInterval(view, view);
                    }
                }
            }
            if (showAll.isSelected()) {
                showAllChanged();
            }
        });
    }

    private void showError(String text) {
        status.setText(FormRows.html(text));
        panel.revalidate();
    }

    private int selectedRow() {
        int view = table.getSelectedRow();
        if (view < 0) {
            showError("Bitte zuerst einen Eintrag auswählen.");
            return -1;
        }
        return table.convertRowIndexToModel(view);
    }

    private void create() {
        String saved = PasswordEntryDialog.show(panel, store.get(), null, null, palette);
        if (saved != null) {
            refresh(saved);
        }
    }

    private void edit() {
        int row = selectedRow();
        if (row < 0) {
            return;
        }
        final String title = model.entry(row).title();
        store.get().read(title, (entry, error) -> {
            if (error != null) {
                showError("Eintrag konnte nicht gelesen werden: " + error);
                return;
            }
            try {
                String saved = PasswordEntryDialog.show(panel, store.get(), entry, null, palette);
                if (saved != null) {
                    refresh(saved);
                }
            } finally {
                entry.wipe();
            }
        });
    }

    private void remove() {
        int row = selectedRow();
        if (row < 0) {
            return;
        }
        final String title = model.entry(row).title();
        if (!confirm(panel, "Eintrag löschen", "Eintrag „" + title + "“ wirklich löschen? Diese Aktion kann nicht "
                + "rückgängig gemacht werden.", "Löschen", "Abbrechen", palette)) {
            return;
        }
        store.get().remove(title, (value, error) -> {
            if (error != null) {
                showError("Eintrag konnte nicht gelöscht werden: " + error);
                return;
            }
            refresh(null);
        });
    }

    private void copy() {
        int row = selectedRow();
        if (row < 0) {
            return;
        }
        store.get().read(model.entry(row).title(), (entry, error) -> {
            if (error != null) {
                showError("Passwort konnte nicht gelesen werden: " + error);
                return;
            }
            char[] secret = entry.copyPassword();
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(new String(secret == null ? new char[0] : secret)), null);
                status.setText(" ");
            } finally {
                if (secret != null) {
                    Arrays.fill(secret, '\0');
                }
                entry.wipe();
            }
        });
    }

    private void toggle(int row) {
        if (model.isRevealed(row)) {
            model.hide(row);
            return;
        }
        reveal(row);
    }

    private void reveal(final int row) {
        final String title = model.entry(row).title();
        store.get().read(title, (entry, error) -> {
            if (error != null) {
                showError("Passwort konnte nicht gelesen werden: " + error);
                return;
            }
            model.reveal(title, entry);
        });
    }

    private void showAllChanged() {
        if (!showAll.isSelected()) {
            model.hideAll();
            return;
        }
        for (int row = 0; row < model.getRowCount(); row++) {
            if (!model.isRevealed(row)) {
                reveal(row);
            }
        }
    }

    /** Rückfrage im Stil der übrigen Dialoge; {@code true} bei {@code yes}. Nur auf dem EDT. */
    static boolean confirm(Component parent, String title, String text, String yes, String no, ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        Window owner = parent == null ? null : (parent instanceof Window ? (Window) parent
                : SwingUtilities.getWindowAncestor(parent));
        final boolean[] confirmed = {false};
        final JDialog dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
        ApplicationIcons.apply(dialog);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(colors.getSurface());
        content.setBorder(BorderFactory.createCompoundBorder(
                ComicBorder.windowBorder(colors, SourceDialog.WINDOW_PADDING),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel heading = new JLabel(title);
        heading.setFont(ResearchUiTypography.semiBold(15f));
        heading.setForeground(colors.getInk());
        JLabel body = new JLabel(FormRows.html(text));
        body.setForeground(colors.getInk());
        ComposerButton accept = ComposerButton.primary(null, yes, ResearchUiPalette.DANGER_RED, null);
        ComposerButton cancel = new ComposerButton(null, no, false);
        accept.addActionListener(event -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        cancel.addActionListener(event -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(accept);
        content.add(heading, BorderLayout.NORTH);
        content.add(body, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        content.registerKeyboardAction(event -> dialog.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        ComicWindowDragger.install(heading);
        dialog.setContentPane(content);
        dialog.pack();
        ComicWindowShape.install(dialog, content, colors, SourceDialog.WINDOW_PADDING, ResearchUiMetrics.RADIUS_WINDOW);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return confirmed[0];
    }

    private static String[] columns() {
        return new String[] {"Netzwerk", "Kategorie", "ID", "Anzeigename", "Benutzername", "Passwort", "URL",
                "Login", "Proxy", "Auto-Idx", "PW-Speicher", "Session", ""};
    }

    // Für Tests.

    JTable table() {
        return table;
    }

    JLabel status() {
        return status;
    }

    /** Tabelle der Einträge; aufgedeckte Passwörter je Titel, beim Verbergen überschrieben. */
    private static final class EntryModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final List<PasswordEntry> entries = new ArrayList<PasswordEntry>();
        private final Map<String, PasswordEntry> revealed = new HashMap<String, PasswordEntry>();
        private final String[] columns = columns();

        void set(List<PasswordEntry> next) {
            hideAll();
            entries.clear();
            entries.addAll(next);
            fireTableDataChanged();
        }

        PasswordEntry entry(int row) {
            return entries.get(row);
        }

        boolean isRevealed(int row) {
            return revealed.containsKey(entries.get(row).title());
        }

        void reveal(String title, PasswordEntry withPassword) {
            PasswordEntry old = revealed.put(title, withPassword);
            if (old != null) {
                old.wipe();
            }
            fireTableDataChanged();
        }

        void hide(int row) {
            PasswordEntry old = revealed.remove(entries.get(row).title());
            if (old != null) {
                old.wipe();
            }
            fireTableRowsUpdated(row, row);
        }

        void hideAll() {
            for (PasswordEntry entry : revealed.values()) {
                entry.wipe();
            }
            revealed.clear();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column >= 7 && column <= 11 ? Boolean.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            PasswordEntry entry = entries.get(row);
            switch (column) {
                case 0:
                    return entry.intern() ? PasswordEntryDialog.INTERN_LABEL : PasswordEntryDialog.EXTERN_LABEL;
                case 1:
                    return entry.category();
                case 2:
                    return entry.title();
                case 3:
                    return entry.displayName();
                case 4:
                    return entry.userName();
                case PASSWORD_COLUMN: {
                    PasswordEntry shown = revealed.get(entry.title());
                    char[] secret = shown == null ? null : shown.copyPassword();
                    if (secret == null) {
                        return HIDDEN;
                    }
                    String text = new String(secret);
                    Arrays.fill(secret, '\0');
                    return text;
                }
                case 6:
                    return entry.url();
                case 7:
                    return entry.flag(PasswordEntry.REQUIRES_LOGIN, true);
                case 8:
                    return entry.flag(PasswordEntry.USE_PROXY, false);
                case 9:
                    return entry.flag(PasswordEntry.AUTO_INDEX, false);
                case 10:
                    return entry.flag(PasswordEntry.SAVE_PASSWORD, true);
                case 11:
                    return entry.flag(PasswordEntry.SESSION_CACHE, false);
                case EYE_COLUMN:
                    return revealed.containsKey(entry.title()) ? "🔓" : "👁";
                default:
                    return "";
            }
        }
    }
}
