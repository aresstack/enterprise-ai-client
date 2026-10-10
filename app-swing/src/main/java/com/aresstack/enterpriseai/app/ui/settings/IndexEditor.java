package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.File;

/**
 * Die beiden Felder des Index-Dialogs: Indexverzeichnis (mit „Wählen …“) und das Häkchen für die Indexierung
 * beim Start, wie sie bis zum Umzug in die Seitenleiste der Reiter „Wissensbasis“ des Einstellungen-Dialogs
 * zeigte. Lädt einen {@link IndexForm} und liefert den bearbeiteten Stand zurück.
 */
final class IndexEditor {

    static final String NOTE = "Der Lucene-Index mit Volltext und Vektoren. Leer: Verzeichnis „index“ im "
            + "Anwendungsverzeichnis.";
    static final String DIRECTORY_LABEL = "Indexverzeichnis (optional)";
    static final String CHOOSE_LABEL = "Wählen …";
    static final String ON_STARTUP_LABEL = "Alle Quellen beim Start im Hintergrund indexieren";

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JCheckBox indexOnStartup;
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
        indexOnStartup = rows.checkBox(ON_STARTUP_LABEL,
                "Unveränderte Seiten werden übersprungen; die Statuszeile zeigt den Fortschritt");
        chooseDirectory.addActionListener(event -> chooseDirectory());
        panel = rows.panel();
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

    void load(IndexForm form) {
        indexDirectory.setText(form.indexDirectory());
        indexOnStartup.setSelected(form.indexOnStartup());
    }

    IndexForm toForm() {
        return new IndexForm(indexDirectory.getText(), indexOnStartup.isSelected());
    }

    void focusFirstField() {
        indexDirectory.requestFocusInWindow();
    }

    // Für Tests.

    JTextField indexDirectory() {
        return indexDirectory;
    }

    JCheckBox indexOnStartup() {
        return indexOnStartup;
    }

    ComicButton chooseButton() {
        return chooseDirectory;
    }
}
