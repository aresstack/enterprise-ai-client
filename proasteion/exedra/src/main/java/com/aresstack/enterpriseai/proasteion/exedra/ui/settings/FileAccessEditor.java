package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plate „Dateizugriff“ of the index settings (issue #120): the folders Enterprise AI may read
 * ({@code files.access.roots}), added with the folder chooser or typed (UNC paths), each removable, and the opt-in
 * „Alle lokalen Dateien und Netzlaufwerke freigeben“ ({@code files.access.allowAll}) that only turns on after a second,
 * explicit confirmation. Turning it off needs no confirmation. Granting never starts indexing.
 */
final class FileAccessEditor {

    static final String HEADING = "Dateizugriff";
    static final String NOTE = "Nur freigegebene Ordner kann Enterprise AI lesen. Gemappte Laufwerke (P:\\Projekt) und "
            + "Netzpfade (\\\\server\\freigabe\\ordner) zählen wie lokale Ordner; Verknüpfungen aus einem Ordner hinaus "
            + "erweitern die Freigabe nicht. Der Ordner einer Wissensquelle „Lokale Dateien“ ist immer freigegeben. "
            + "Welche Ordner indexiert werden, legst du weiterhin unter Wissensquellen fest. Gilt nach dem Speichern "
            + "ohne Neustart (files.access.roots).";
    static final String EMPTY_LABEL = "Noch kein Ordner freigegeben.";
    static final String PATH_LABEL = "Ordner oder Netzpfad";
    static final String PATH_HELP = "Pfad eintragen und „Freigeben“ drücken, z. B. \\\\server\\team\\wissen";
    static final String ADD_LABEL = "Freigeben";
    static final String CHOOSE_LABEL = "Ordner wählen …";
    static final String REMOVE_LABEL = "Entfernen";
    static final String ALLOW_ALL_LABEL = "Alle lokalen Dateien und Netzlaufwerke freigeben";
    static final String ALLOW_ALL_HELP = "Standardmäßig aus. Startet keine Indexierung (files.access.allowAll).";
    static final String CONFIRM_TITLE = "Alle Dateizugriffe freigeben";
    static final String CONFIRM_QUESTION = "Wirklich alle Dateien freigeben?";
    static final String CONFIRM_TEXT = "Dadurch darf Enterprise AI auf alle Dateien zugreifen, die dein "
            + "Windows-Benutzerkonto lesen kann. Das kann persönliche, vertrauliche und dienstliche Dateien sowie "
            + "erreichbare Netzlaufwerke umfassen. Die Freigabe betrifft den KI-/Dateizugriff; eine Vollindexierung "
            + "aller Laufwerke wird dadurch nicht automatisch gestartet.";
    static final String CONFIRM_LABEL = "Alle Dateizugriffe freigeben";

    /** The second confirmation before everything is granted; tests replace it. */
    interface Confirmation {
        boolean confirm(Component parent);
    }

    private final ComicPalette palette;
    private final Confirmation confirmation;
    private final List<String> roots = new ArrayList<String>();
    private final JPanel list;
    private final JTextField path;
    private final ComicButton add;
    private final ComicButton choose;
    private final JCheckBox allowAll;
    private final JPanel panel;

    FileAccessEditor(final ComicPalette palette) {
        this(palette, parent -> SourceDialog.confirm(SwingUtilities.getWindowAncestor(parent), CONFIRM_TITLE,
                CONFIRM_QUESTION, CONFIRM_TEXT, CONFIRM_LABEL, palette));
    }

    FileAccessEditor(ComicPalette palette, Confirmation confirmation) {
        this.palette = palette;
        this.confirmation = confirmation;
        FormRows rows = new FormRows(palette);
        rows.note(NOTE);
        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        rows.component(null, list, null);
        path = new JTextField(24);
        rows.style(path);
        add = new ComicButton(ADD_LABEL, null, ComicButton.Accent.ACTION, palette);
        choose = new ComicButton(CHOOSE_LABEL, null, ComicButton.Accent.ACTION, palette);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(add);
        buttons.add(choose);
        JPanel pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.setOpaque(false);
        pathRow.add(path, BorderLayout.CENTER);
        pathRow.add(buttons, BorderLayout.EAST);
        rows.component(PATH_LABEL, pathRow, PATH_HELP);
        rows.note(PATH_HELP);
        allowAll = rows.checkBox(ALLOW_ALL_LABEL, ALLOW_ALL_HELP);
        rows.note(ALLOW_ALL_HELP);
        add.addActionListener(event -> addTyped());
        path.addActionListener(event -> addTyped());
        choose.addActionListener(event -> chooseFolder());
        allowAll.addActionListener(event -> allowAllToggled());
        panel = FormRows.plate(HEADING, palette.getAccentOrange(), rows.panel(), palette);
        refresh();
    }

    JComponent panel() {
        return panel;
    }

    void load(SettingsForm form) {
        roots.clear();
        roots.addAll(form.fileAccessRoots());
        allowAll.setSelected(form.fileAccessAllowAll());
        path.setText("");
        refresh();
    }

    void store(SettingsForm.Builder b) {
        b.fileAccessRoots(new ArrayList<String>(roots)).fileAccessAllowAll(allowAll.isSelected());
    }

    /** Grant a folder unless it is empty or already granted. */
    void grant(String folder) {
        String trimmed = folder == null ? "" : folder.trim();
        if (!trimmed.isEmpty() && !roots.contains(trimmed)) {
            roots.add(trimmed);
            refresh();
        }
    }

    void revoke(String folder) {
        if (roots.remove(folder)) {
            refresh();
        }
    }

    private void addTyped() {
        grant(path.getText());
        path.setText("");
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Ordner freigeben");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(panel)) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
            grant(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void allowAllToggled() {
        if (allowAll.isSelected() && !confirmation.confirm(panel)) {
            allowAll.setSelected(false);
        }
    }

    private void refresh() {
        list.removeAll();
        if (roots.isEmpty()) {
            JLabel empty = new JLabel(EMPTY_LABEL);
            empty.setForeground(FormRows.MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(empty);
        }
        for (final String root : roots) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel label = new JLabel(root);
            label.setForeground(palette.getInk());
            label.setToolTipText(root);
            ComicButton remove = new ComicButton(REMOVE_LABEL, null, ComicButton.Accent.CRITICAL, palette);
            remove.addActionListener(event -> revoke(root));
            row.add(label, BorderLayout.CENTER);
            row.add(remove, BorderLayout.EAST);
            list.add(row);
        }
        list.revalidate();
        list.repaint();
    }

    // For tests.

    List<String> roots() {
        return Collections.unmodifiableList(roots);
    }

    JTextField path() {
        return path;
    }

    ComicButton addButton() {
        return add;
    }

    JCheckBox allowAll() {
        return allowAll;
    }

    JPanel list() {
        return list;
    }
}
