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
 * und die Wissenssuche des normalen Chats (Kandidaten, {@code retrieval.maxResults}, {@code context.maxSources},
 * {@code context.maxTokens}, Mindestzahl, Nachrecherchen, Live-Suche, Reranking; gelten nach dem Speichern ab der
 * nächsten Frage). Eine Indexierung beim Start gibt es
 * nicht mehr; die Suche füllt den Index mit ihren Treffern (wie MainframeMate).
 */
final class IndexEditor {

    static final String NOTE = "Der Lucene-Index mit Volltext und Vektoren. Leer: Verzeichnis „index“ im "
            + "Anwendungsverzeichnis.";
    static final String DIRECTORY_LABEL = "Indexverzeichnis (optional)";
    static final String CHOOSE_LABEL = "Wählen …";
    static final String SEARCH_NOTE = "Gilt für die normale Chat-Antwort. Die Suche liefert höchstens „Max. "
            + "Retrieval-Kandidaten“; das Modell bewertet sie in einem Aufruf gegen die Frage und nimmt die kleinste "
            + "Menge, die reicht, höchstens „Max. Quellen“. Reicht nichts, lädt die Live-Suche weitere Seiten oder "
            + "der Chat sucht genauer nach; danach antwortet er mit dem Vorhandenen und nennt die Lücke. Das "
            + "Recherche-Budget des Agenten (Reiter Netzwerk & Agent, vollständig gelesene Seiten) ist davon getrennt. "
            + "Alle Werte gelten nach dem Speichern ab der nächsten Frage.";
    static final String KEYWORD_CANDIDATES_LABEL = "Volltext-Kandidaten";
    static final String KEYWORD_CANDIDATES_HELP = "Treffer der Volltextsuche (BM25) je Suche vor der Zusammenführung "
            + "(retrieval.keywordCandidates; Standard 20).";
    static final String SEMANTIC_CANDIDATES_LABEL = "Semantische Kandidaten";
    static final String SEMANTIC_CANDIDATES_HELP = "Treffer der semantischen Suche (Cosine) je Suche vor der "
            + "Zusammenführung (retrieval.semanticCandidates; Standard 20).";
    static final String MAX_RESULTS_LABEL = "Max. Retrieval-Kandidaten";
    static final String MAX_RESULTS_HELP = "Suchergebnisse vor der Evidenzbewertung: so viele zusammengeführte "
            + "Treffer (Volltext und semantisch) bewertet das Modell je Runde (retrieval.maxResults, 1–1000; "
            + "Standard 10).";
    static final String MAX_SOURCES_LABEL = "Max. Quellen im Antwortkontext";
    static final String MAX_SOURCES_HELP = "Harte Obergrenze: höchstens so viele Quellen gelangen in die Antwort "
            + "(context.maxSources, 1–1000; Standard 6).";
    static final String MAX_TOKENS_LABEL = "Kontextbudget (Tokens)";
    static final String MAX_TOKENS_HELP = "Geschätzte Tokens für den ganzen Kontextblock; passt ein weiterer Auszug "
            + "nicht mehr hinein, endet der Block (context.maxTokens, 1–1000000; Standard 1500).";
    static final String MIN_SOURCES_LABEL = "Mindestzahl unabhängiger Quellen";
    static final String MIN_SOURCES_HELP = "So viele verschiedene Seiten (nicht Abschnitte derselben Seite) müssen "
            + "die Antwort stützen; 0 = keine Mindestzahl, nur die Evidenzprüfung entscheidet "
            + "(retrieval.minIndependentSources, 0–20; Standard 1).";
    static final String MAX_FOLLOW_UPS_LABEL = "Max. Nachrecherchen";
    static final String MAX_FOLLOW_UPS_HELP = "So oft sucht der Chat mit einer genaueren Anfrage weiter, wenn die "
            + "Treffer nicht reichen; 0 = keine automatische Nachrecherche (retrieval.maxFollowUps, 0–5; Standard 2).";
    static final String INITIAL_PAGES_LABEL = "Live-Suche: erste Seiten";
    static final String INITIAL_PAGES_HELP = "Auf so viele geladene Live-Treffer wartet die erste Evidenzbewertung "
            + "einer Suche (retrieval.liveSearch.initialPages, 0–100; Standard 1).";
    static final String MAX_PAGES_LABEL = "Live-Suche: max. abzuwartende Seiten";
    static final String MAX_PAGES_HELP = "Auf höchstens so viele Live-Treffer wartet eine Suche, wenn die Evidenz "
            + "nicht reicht; das Vorladen im Hintergrund läuft unabhängig weiter (retrieval.liveSearch.maxPages, "
            + "0–100; Standard 4).";
    static final String HITS_PER_SOURCE_LABEL = "Live-Suche: Treffer je Quelle";
    static final String HITS_PER_SOURCE_HELP = "So viele Treffer holt die Live-Suche je angehakter Quelle "
            + "(retrieval.liveSearch.hitsPerSource, 1–100; Standard 10).";
    static final String RAW_WINDOWS_LABEL = "Textfenster je geladener Seite";
    static final String RAW_WINDOWS_HELP = "Geladene Seiten sind sofort im Volltext durchsuchbar, noch bevor Passagen "
            + "und Embeddings fertig sind. Die Bewertung sieht je Seite so viele passende Textfenster statt der ganzen "
            + "Seite (retrieval.raw.windowsPerDocument, 1–10; Standard 2).";
    static final String RAW_WINDOW_CHARS_LABEL = "Zeichen je Textfenster";
    static final String RAW_WINDOW_CHARS_HELP = "Länge eines Textfensters aus einer geladenen Seite "
            + "(retrieval.raw.windowChars, 100–10000; Standard 700).";
    static final String PREPARATION_NOTE = "Geladene Seiten landen sofort im Rohindex und sind für den Chat nutzbar. "
            + "Passagen, Embeddings und Navigation entstehen danach im Hintergrund, parallel und ohne den Chat "
            + "aufzuhalten. Wie viele Seiten gleichzeitig geladen werden, steht unter Netzwerk & Agent › Vorabladen (Prefetch). "
            + "Alle Werte gelten nach dem Speichern ohne Neustart.";
    static final String RAW_WORKERS_LABEL = "Rohindex-Worker";
    static final String RAW_WORKERS_HELP = "So viele geladene Seiten werden gleichzeitig in den Rohindex geschrieben "
            + "(knowledge.raw.workers, 1–8; Standard 2).";
    static final String ENRICHMENT_WORKERS_LABEL = "Aufbereitungs-Worker";
    static final String ENRICHMENT_WORKERS_HELP = "So viele Seiten werden gleichzeitig in Passagen zerlegt und "
            + "eingebettet; dieselbe Seite nie doppelt, die neueste Fassung gewinnt (knowledge.enrichment.workers, "
            + "1–16; Standard 4).";
    static final String EMBEDDING_CONCURRENCY_LABEL = "Embedding-Parallelität";
    static final String EMBEDDING_CONCURRENCY_HELP = "Höchstens so viele Embedding-Aufrufe der Aufbereitung laufen "
            + "gleichzeitig; das Limit gilt je Embedding-Modell, getrennt vom Chat-Modell "
            + "(embedding.maxConcurrency, 1–32; Standard 4).";
    static final String EMBEDDING_BATCH_LABEL = "Embedding-Batchgröße";
    static final String EMBEDDING_BATCH_HELP = "So viele Texte gehen in einen Embedding-Aufruf "
            + "(knowledge.embeddingBatchSize, 1–1000; Standard 16).";
    static final String RERANK_LABEL = "Treffer gegen die Frage bewerten (Reranking)";
    static final String RERANK_HELP = "Das Chat-Modell bewertet die Treffer gegen die Frage und wählt die kleinste "
            + "ausreichende Menge (retrieval.rerank). Aus: feste Reihenfolge der Suche mit Nachrecherche.";

    private final JTextField indexDirectory;
    private final ComicButton chooseDirectory;
    private final JTextField contextMaxSources;
    private final JTextField retrievalMaxResults;
    private final JTextField contextMaxTokens;
    private final JTextField minIndependentSources;
    private final JTextField retrievalMaxFollowUps;
    private final JTextField keywordCandidates;
    private final JTextField semanticCandidates;
    private final JTextField liveSearchInitialPages;
    private final JTextField liveSearchMaxPages;
    private final JTextField liveSearchHitsPerSource;
    private final JTextField rawWindows;
    private final JTextField rawWindowChars;
    private final JTextField rawWorkers;
    private final JTextField enrichmentWorkers;
    private final JTextField embeddingConcurrency;
    private final JTextField embeddingBatchSize;
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
        keywordCandidates = field(search, KEYWORD_CANDIDATES_LABEL, KEYWORD_CANDIDATES_HELP);
        semanticCandidates = field(search, SEMANTIC_CANDIDATES_LABEL, SEMANTIC_CANDIDATES_HELP);
        retrievalMaxResults = field(search, MAX_RESULTS_LABEL, MAX_RESULTS_HELP);
        contextMaxSources = field(search, MAX_SOURCES_LABEL, MAX_SOURCES_HELP);
        contextMaxTokens = field(search, MAX_TOKENS_LABEL, MAX_TOKENS_HELP);
        minIndependentSources = field(search, MIN_SOURCES_LABEL, MIN_SOURCES_HELP);
        retrievalMaxFollowUps = field(search, MAX_FOLLOW_UPS_LABEL, MAX_FOLLOW_UPS_HELP);
        liveSearchInitialPages = field(search, INITIAL_PAGES_LABEL, INITIAL_PAGES_HELP);
        liveSearchMaxPages = field(search, MAX_PAGES_LABEL, MAX_PAGES_HELP);
        liveSearchHitsPerSource = field(search, HITS_PER_SOURCE_LABEL, HITS_PER_SOURCE_HELP);
        rawWindows = field(search, RAW_WINDOWS_LABEL, RAW_WINDOWS_HELP);
        rawWindowChars = field(search, RAW_WINDOW_CHARS_LABEL, RAW_WINDOW_CHARS_HELP);
        retrievalRerank = search.checkBox(RERANK_LABEL, RERANK_HELP);
        search.note(RERANK_HELP);
        FormRows preparation = new FormRows(palette);
        preparation.note(PREPARATION_NOTE);
        rawWorkers = field(preparation, RAW_WORKERS_LABEL, RAW_WORKERS_HELP);
        enrichmentWorkers = field(preparation, ENRICHMENT_WORKERS_LABEL, ENRICHMENT_WORKERS_HELP);
        embeddingConcurrency = field(preparation, EMBEDDING_CONCURRENCY_LABEL, EMBEDDING_CONCURRENCY_HELP);
        embeddingBatchSize = field(preparation, EMBEDDING_BATCH_LABEL, EMBEDDING_BATCH_HELP);
        panel = FormRows.column(palette,
                FormRows.plate("Index", palette.getNavigationBlue(), rows.panel(), palette),
                FormRows.plate("Wissenssuche im Chat", palette.getAccentYellow(), search.panel(), palette),
                FormRows.plate("Laden und Aufbereitung", palette.getNavigationBlue(), preparation.panel(), palette));
    }

    /** Ein Feld mit seinem Hilfetext darunter. */
    private static JTextField field(FormRows rows, String label, String help) {
        JTextField field = rows.textField(label, help);
        rows.note(help);
        return field;
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
        minIndependentSources.setText(form.minIndependentSources());
        keywordCandidates.setText(form.keywordCandidates());
        semanticCandidates.setText(form.semanticCandidates());
        liveSearchInitialPages.setText(form.liveSearchInitialPages());
        liveSearchMaxPages.setText(form.liveSearchMaxPages());
        liveSearchHitsPerSource.setText(form.liveSearchHitsPerSource());
        retrievalMaxFollowUps.setText(form.retrievalMaxFollowUps());
        rawWindows.setText(form.rawWindows());
        rawWindowChars.setText(form.rawWindowChars());
        rawWorkers.setText(form.rawWorkers());
        enrichmentWorkers.setText(form.enrichmentWorkers());
        embeddingConcurrency.setText(form.embeddingConcurrency());
        embeddingBatchSize.setText(form.embeddingBatchSize());
        retrievalRerank.setSelected(form.retrievalRerank());
    }

    void store(SettingsForm.Builder b) {
        b.indexDirectory(indexDirectory.getText())
                .contextMaxSources(contextMaxSources.getText())
                .retrievalMaxResults(retrievalMaxResults.getText())
                .contextMaxTokens(contextMaxTokens.getText())
                .minIndependentSources(minIndependentSources.getText())
                .keywordCandidates(keywordCandidates.getText())
                .semanticCandidates(semanticCandidates.getText())
                .liveSearchInitialPages(liveSearchInitialPages.getText())
                .liveSearchMaxPages(liveSearchMaxPages.getText())
                .liveSearchHitsPerSource(liveSearchHitsPerSource.getText())
                .retrievalMaxFollowUps(retrievalMaxFollowUps.getText())
                .rawWindows(rawWindows.getText())
                .rawWindowChars(rawWindowChars.getText())
                .rawWorkers(rawWorkers.getText())
                .enrichmentWorkers(enrichmentWorkers.getText())
                .embeddingConcurrency(embeddingConcurrency.getText())
                .embeddingBatchSize(embeddingBatchSize.getText())
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

    JTextField minIndependentSources() {
        return minIndependentSources;
    }

    JTextField keywordCandidates() {
        return keywordCandidates;
    }

    JTextField semanticCandidates() {
        return semanticCandidates;
    }

    JTextField liveSearchInitialPages() {
        return liveSearchInitialPages;
    }

    JTextField liveSearchMaxPages() {
        return liveSearchMaxPages;
    }

    JTextField liveSearchHitsPerSource() {
        return liveSearchHitsPerSource;
    }

    JTextField retrievalMaxFollowUps() {
        return retrievalMaxFollowUps;
    }

    JTextField rawWindows() {
        return rawWindows;
    }

    JTextField rawWindowChars() {
        return rawWindowChars;
    }

    JTextField rawWorkers() {
        return rawWorkers;
    }

    JTextField enrichmentWorkers() {
        return enrichmentWorkers;
    }

    JTextField embeddingConcurrency() {
        return embeddingConcurrency;
    }

    JTextField embeddingBatchSize() {
        return embeddingBatchSize;
    }

    JCheckBox retrievalRerank() {
        return retrievalRerank;
    }
}
