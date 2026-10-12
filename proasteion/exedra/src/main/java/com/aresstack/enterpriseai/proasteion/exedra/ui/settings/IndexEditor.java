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
 * Kategorie „Index“ des Einstellungen-Dialogs: Indexverzeichnis ({@code knowledge.indexDirectory}, mit „Wählen …“)
 * und die Wissenssuche des normalen Chats ({@code context.maxSources}, {@code retrieval.maxResults},
 * {@code context.maxTokens}; gelten nach dem Speichern ab der nächsten Frage). Eine Indexierung beim Start gibt es
 * nicht mehr; die Suche füllt den Index mit ihren Treffern (wie MainframeMate).
 */
final class IndexEditor {

    static final String NOTE = "Der Lucene-Index mit Volltext und Vektoren. Leer: Verzeichnis „index“ im "
            + "Anwendungsverzeichnis.";
    static final String DIRECTORY_LABEL = "Indexverzeichnis (optional)";
    static final String CHOOSE_LABEL = "Wählen …";
    static final String SEARCH_NOTE = "Gilt für die normale Chat-Antwort: Die Suche liefert höchstens „Max. "
            + "Retrieval-Treffer“, davon gehen höchstens „Max. Quellen im Antwortkontext“ in die Antwort, solange "
            + "das Kontextbudget reicht. Das Recherche-Budget des Agenten (Reiter Netzwerk & Agent) ist davon "
            + "getrennt. Gilt nach dem Speichern ab der nächsten Frage.";
    static final String MAX_SOURCES_LABEL = "Max. Quellen im Antwortkontext";
    static final String MAX_SOURCES_HELP = "Höchstzahl der Suchauszüge, die der normalen Chat-Antwort als "
            + "RAG-Kontext übergeben werden (context.maxSources, 1–1000; Standard 6).";
    static final String MAX_RESULTS_LABEL = "Max. Retrieval-Treffer";
    static final String MAX_RESULTS_HELP = "Höchstzahl der zusammengeführten Suchtreffer (Volltext und semantisch) "
            + "vor dem Kontextaufbau (retrieval.maxResults, 1–1000; Standard 10).";
    static final String MAX_TOKENS_LABEL = "Kontextbudget (Tokens)";
    static final String MAX_TOKENS_HELP = "Geschätzte Tokens für den ganzen Kontextblock; passt ein weiterer Auszug "
            + "nicht mehr hinein, endet der Block (context.maxTokens, 1–1000000; Standard 1500).";

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JTextField contextMaxSources;
    private final JTextField retrievalMaxResults;
    private final JTextField contextMaxTokens;
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
        FormRows search = new FormRows(palette);
        search.note(SEARCH_NOTE);
        contextMaxSources = search.textField(MAX_SOURCES_LABEL, MAX_SOURCES_HELP);
        search.note(MAX_SOURCES_HELP);
        retrievalMaxResults = search.textField(MAX_RESULTS_LABEL, MAX_RESULTS_HELP);
        search.note(MAX_RESULTS_HELP);
        contextMaxTokens = search.textField(MAX_TOKENS_LABEL, MAX_TOKENS_HELP);
        search.note(MAX_TOKENS_HELP);
        panel = FormRows.column(palette,
                FormRows.plate("Index", palette.getNavigationBlue(), rows.panel(), palette),
                FormRows.plate("Wissenssuche im Chat", palette.getAccentYellow(), search.panel(), palette));
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
        contextMaxSources.setText(form.contextMaxSources());
        retrievalMaxResults.setText(form.retrievalMaxResults());
        contextMaxTokens.setText(form.contextMaxTokens());
    }

    void store(SettingsForm.Builder b) {
        b.indexDirectory(indexDirectory.getText())
                .contextMaxSources(contextMaxSources.getText())
                .retrievalMaxResults(retrievalMaxResults.getText())
                .contextMaxTokens(contextMaxTokens.getText());
    }

    // Für Tests.

    JTextField indexDirectory() {
        return indexDirectory;
    }

    ComicButton chooseButton() {
        return chooseDirectory;
    }

    JTextField contextMaxSources() {
        return contextMaxSources;
    }

    JTextField retrievalMaxResults() {
        return retrievalMaxResults;
    }

    JTextField contextMaxTokens() {
        return contextMaxTokens;
    }
}
