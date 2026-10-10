package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reiter „Wissensbasis“: Indexverzeichnis und Indexierung beim Start. Die Wissensquellen selbst verwaltet der
 * Drawer-Reiter „Wissensquellen“ (hinzufügen, bearbeiten, entfernen, an- und abwählen, indexieren); hier stehen sie
 * nur als Hinweis und laufen beim Speichern unverändert mit.
 */
final class KnowledgeTab {

    static final String SOURCES_NOTE = "Wissensquellen werden in der Seitenleiste verwaltet (☰, Reiter „Wissensquellen“): "
            + "hinzufügen, bearbeiten, entfernen, per Häkchen an- und abwählen und einzeln indexieren.";

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JCheckBox indexOnStartup;
    private final JLabel sourcesSummary;
    private final JPanel panel;
    private List<SourceForm> sources = Collections.emptyList();

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

        FormRows hint = new FormRows(palette);
        hint.note(SOURCES_NOTE);
        sourcesSummary = hint.note("");

        panel = FormRows.column(palette,
                FormRows.plate("Index", palette.getNavigationBlue(), index.panel(), palette),
                FormRows.plate("Wissensquellen", palette.getAgentPetrol(), hint.panel(), palette));
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
        sources = new ArrayList<SourceForm>(form.sources());
        sourcesSummary.setText(FormRows.html(summary(sources)));
    }

    /** „Konfiguriert: wiki, confluence (abgewählt)“ bzw. „Noch keine Wissensquelle konfiguriert.“ */
    static String summary(List<SourceForm> sources) {
        if (sources.isEmpty()) {
            return "Noch keine Wissensquelle konfiguriert.";
        }
        StringBuilder text = new StringBuilder("Konfiguriert: ");
        for (int i = 0; i < sources.size(); i++) {
            SourceForm source = sources.get(i);
            if (i > 0) {
                text.append(", ");
            }
            text.append(source.id().isEmpty() ? "(ohne ID)" : source.id());
            if (!source.enabled()) {
                text.append(" (abgewählt)");
            }
        }
        return text.toString();
    }

    void store(SettingsForm.Builder b) {
        b.indexDirectory(indexDirectory.getText()).indexOnStartup(indexOnStartup.isSelected())
                .sources(sources);
    }

    List<SourceForm> sources() {
        return Collections.unmodifiableList(sources);
    }

    String sourcesSummary() {
        return summary(sources);
    }

    JTextField indexDirectory() {
        return indexDirectory;
    }

    JCheckBox indexOnStartup() {
        return indexOnStartup;
    }
}
