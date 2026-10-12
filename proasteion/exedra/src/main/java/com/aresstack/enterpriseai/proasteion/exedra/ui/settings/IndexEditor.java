package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JCheckBox;
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
            + "Retrieval-Treffer“. Das Modell bewertet sie gegen die Frage und übernimmt die passenden einzeln, die "
            + "besten zuerst, bis die Evidenz reicht; höchstens „Max. Quellen im Antwortkontext“, solange das "
            + "Kontextbudget reicht. Reicht es nicht, sucht der Chat bis zu „Max. Nachsuchen“ Mal genauer weiter. Das "
            + "Recherche-Budget des Agenten (Reiter Netzwerk & Agent) ist davon getrennt. Gilt nach dem Speichern ab "
            + "der nächsten Frage.";
    static final String MAX_SOURCES_LABEL = "Max. Quellen im Antwortkontext";
    static final String MAX_SOURCES_HELP = "Höchstzahl der Suchauszüge, die der normalen Chat-Antwort als "
            + "RAG-Kontext übergeben werden (context.maxSources, 1–1000; Standard 6).";
    static final String MAX_RESULTS_LABEL = "Max. Retrieval-Treffer";
    static final String MAX_RESULTS_HELP = "Höchstzahl der zusammengeführten Suchtreffer (Volltext und semantisch) "
            + "vor dem Kontextaufbau (retrieval.maxResults, 1–1000; Standard 10).";
    static final String MAX_TOKENS_LABEL = "Kontextbudget (Tokens)";
    static final String MIN_SOURCES_LABEL = "Mindestzahl unabhängiger Quellen";
    static final String MIN_SOURCES_HELP = "So viele verschiedene Seiten müssen relevante Abschnitte liefern, bevor "
            + "die Prüfung „reicht“ gelten lässt; 0 = keine Mindestzahl, nur die Prüfung entscheidet "
            + "(context.minSources, 0–20; Standard 1).";
    static final String MAX_FOLLOW_UPS_LABEL = "Max. Nachsuchen";
    static final String MAX_FOLLOW_UPS_HELP = "So oft sucht der Chat mit einer genaueren Anfrage weiter, wenn die "
            + "Treffer die Frage nicht beantworten (retrieval.maxFollowUps, 0–5; Standard 2).";
    static final String RERANK_LABEL = "Treffer gegen die Frage bewerten (Reranking)";
    static final String RERANK_HELP = "Das Chat-Modell bewertet die Suchtreffer gegen die Frage; nur passende kommen "
            + "in die Antwort, die besten zuerst, und die Suche endet, sobald sie reichen (retrieval.rerank).";
    static final String MAX_TOKENS_HELP = "Geschätzte Tokens für den ganzen Kontextblock; passt ein weiterer Auszug "
            + "nicht mehr hinein, endet der Block (context.maxTokens, 1–1000000; Standard 1500).";

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JTextField contextMaxSources;
    private final JTextField retrievalMaxResults;
    private final JTextField contextMaxTokens;
    private final JTextField contextMinSources;
    private final JTextField retrievalMaxFollowUps;
    private final JCheckBox retrievalRerank;
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
        contextMinSources = search.textField(MIN_SOURCES_LABEL, MIN_SOURCES_HELP);
        search.note(MIN_SOURCES_HELP);
        retrievalMaxFollowUps = search.textField(MAX_FOLLOW_UPS_LABEL, MAX_FOLLOW_UPS_HELP);
        search.note(MAX_FOLLOW_UPS_HELP);
        retrievalRerank = search.checkBox(RERANK_LABEL, RERANK_HELP);
        search.note(RERANK_HELP);
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
        contextMinSources.setText(form.contextMinSources());
        retrievalMaxFollowUps.setText(form.retrievalMaxFollowUps());
        retrievalRerank.setSelected(form.retrievalRerank());
    }

    void store(SettingsForm.Builder b) {
        b.indexDirectory(indexDirectory.getText())
                .contextMaxSources(contextMaxSources.getText())
                .retrievalMaxResults(retrievalMaxResults.getText())
                .contextMaxTokens(contextMaxTokens.getText())
                .contextMinSources(contextMinSources.getText())
                .retrievalMaxFollowUps(retrievalMaxFollowUps.getText())
                .retrievalRerank(retrievalRerank.isSelected());
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

    JTextField contextMinSources() {
        return contextMinSources;
    }

    JTextField retrievalMaxFollowUps() {
        return retrievalMaxFollowUps;
    }

    JCheckBox retrievalRerank() {
        return retrievalRerank;
    }
}
