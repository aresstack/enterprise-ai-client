package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.File;

/**
 * Kategorie „Index“ des Einstellungen-Dialogs: Indexverzeichnis ({@code knowledge.indexDirectory}, mit „Wählen …“).
 * Eine Indexierung beim Start gibt es nicht mehr; die Suche füllt den Index mit ihren Treffern (wie MainframeMate).
 */
final class IndexEditor {

    static final String NOTE = "Der Lucene-Index mit Volltext und Vektoren. Leer: Verzeichnis „index“ im "
            + "Anwendungsverzeichnis.";
    static final String DIRECTORY_LABEL = "Indexverzeichnis (optional)";
    static final String CHOOSE_LABEL = "Wählen …";

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JPanel panel;

    IndexEditor(ComicPalette palette) {
        FormRows rows = new FormRows(palette);
        rows.note(NOTE);
        indexDirectory = new JTextField(24);
        rows.style(indexDirectory);
        chooseDirectory = new ComicButton(CHOOSE_LABEL, null, ComicButton.Accent.ACTION, palette);
        JPanel directoryRow = new JPanel(new BorderLayout(6, 0));
        directoryRow.setOpaque(false);
        directoryRow.add(indexDirectory, BorderLayout.CENTER);
        directoryRow.add(chooseDirectory, BorderLayout.EAST);
        rows.component(DIRECTORY_LABEL, directoryRow, "Verzeichnis für Lucene-Index und Vektoren");
        chooseDirectory.addActionListener(event -> chooseDirectory());
        panel = FormRows.column(palette, FormRows.plate("Index", palette.getNavigationBlue(), rows.panel(), palette));
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Indexverzeichnis wählen");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = indexDirectory.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(panel)) == JFileChooser.APPROVE_OPTION
                && chooser.getSelectedFile() != null) {
            indexDirectory.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        indexDirectory.setText(form.indexDirectory());
    }

    void store(SettingsForm.Builder b) {
        b.indexDirectory(indexDirectory.getText());
    }

    // Für Tests.

    JTextField indexDirectory() {
        return indexDirectory;
    }

    ComicButton chooseButton() {
        return chooseDirectory;
    }
}
