package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/** Reiter „Wissensbasis“: Indexverzeichnis, Indexierung beim Start und die Wissensquellen. */
final class KnowledgeTab {

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JCheckBox indexOnStartup;
    private final SourcesEditor sources;
    private final JPanel panel;

    KnowledgeTab(ComicPalette palette) {
        FormRows index = new FormRows(palette);
        index.note("Der Lucene-Index mit Volltext und Vektoren. Leer: Verzeichnis „index“ im Anwendungsverzeichnis.");
        indexDirectory = new JTextField(24);
        index.style(indexDirectory);
        chooseDirectory = new ComicButton("Wählen …", null, ComicButton.Accent.ACTION, palette);
        JPanel directoryRow = new JPanel(new BorderLayout(6, 0));
        directoryRow.setOpaque(false);
        directoryRow.add(indexDirectory, BorderLayout.CENTER);
        directoryRow.add(chooseDirectory, BorderLayout.EAST);
        index.component("Indexverzeichnis (optional)", directoryRow, "Verzeichnis für Lucene-Index und Vektoren");
        indexOnStartup = index.checkBox("Alle Quellen beim Start im Hintergrund indexieren",
                "Unveränderte Seiten werden übersprungen; die Statuszeile zeigt den Fortschritt");
        chooseDirectory.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                chooseDirectory();
            }
        });

        sources = new SourcesEditor(palette);

        panel = FormRows.column(palette,
                FormRows.plate("Index", palette.getNavigationBlue(), index.panel(), palette),
                FormRows.plate("Wissensquellen", palette.getAgentPetrol(), sources, palette));
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
        indexOnStartup.setSelected(form.indexOnStartup());
        sources.setSources(form.sources());
    }

    void store(SettingsForm.Builder b) {
        b.indexDirectory(indexDirectory.getText()).indexOnStartup(indexOnStartup.isSelected())
                .sources(sources.sources());
    }

    SourcesEditor sources() {
        return sources;
    }

    JTextField indexDirectory() {
        return indexDirectory;
    }

    JCheckBox indexOnStartup() {
        return indexOnStartup;
    }
}
