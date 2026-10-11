package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Kategorie „Sprachausgabe“ (askai arch {@code readAloudAutoStart}, {@code chunking}, {@code synthesisWorkers}): nur
 * das Verhalten beim Vorlesen. Welche Stimme spricht, bestimmt allein das TTS-Modell unter „Modelle“ (Cloud-Modell
 * oder lokale Stimme); hier gibt es keine zweite Auswahl. Dazu das Embedding-Modell, mit dem die Einheit „Absätze“
 * nach Thema umbricht ({@code speech.readAloud.embeddingModel}, wie die semantische Absatzbildung in arch): lokal
 * (Standard) oder Cloud, das Embedding-Modell der Wissensbasis.
 */
final class SpeechTab {

    static final String SOURCE_LOCAL = "Lokal (im Client)";
    static final String SOURCE_CLOUD = "Cloud (Enterprise-API)";

    /** Anzeige der Einheiten in der Reihenfolge von {@link #chunkingValue(int)}. */
    private static final String CHUNKING_PARAGRAPHS = "Absätze";
    private static final String CHUNKING_SENTENCES = "Sätze";
    private static final String CHUNKING_ANSWER = "Ganze Antwort";

    private final ModelSelection localEmbedding = new ModelSelection();
    private final JComboBox<String> embeddingSource;
    private final CardLayout embeddingPages = new CardLayout();
    private final JPanel embeddingDeck = new JPanel(embeddingPages);
    private final JCheckBox readAloudAutoStart;
    private final JComboBox<String> chunking;
    private final JComboBox<String> workers;
    private final JPanel panel;

    SpeechTab(ModelsTab models, SettingsDialogActions actions, ComicPalette palette) {
        FormRows speech = new FormRows(palette);
        speech.note("Gesprochen wird mit dem TTS-Modell aus „Modelle“: ein Cloud-Modell der Enterprise-API oder "
                + "eine lokale Stimme. Ohne TTS-Modell bleibt der Play/Pause-Orb über dem Verlauf aus.");
        readAloudAutoStart = speech.checkBox("Neue Antworten automatisch vorlesen",
                "Der Orb ist beim Start aktiv: jede neue Antwort wird sofort vorgelesen (speech.readAloud.autoStart)");
        chunking = speech.comboBox("Einheit der Synthese",
                "Wie die Antwort zum Vorlesen geteilt wird: je Absatz, je Satz oder die ganze Antwort. Alle Stücke "
                        + "rechnen im Hintergrund, gespielt wird in Lesereihenfolge; das erste klingt, sobald es "
                        + "fertig ist (speech.readAloud.chunking)",
                CHUNKING_PARAGRAPHS, CHUNKING_SENTENCES, CHUNKING_ANSWER);
        workers = speech.comboBox("Worker",
                "Parallele Synthesen, die die Stücke im Hintergrund vorbereiten (speech.readAloud.synthesisWorkers)",
                "1", "2", "3", "4", "5", "6", "7", "8");
        speech.note("Absätze sind der beste Kompromiss aus Klang und Wartezeit: Sätze klingen früher, aber "
                + "abgehackter, die ganze Antwort klingt am natürlichsten, beginnt aber erst nach der ganzen Synthese.");

        FormRows embedding = new FormRows(palette);
        embedding.note("Die Einheit „Absätze“ bricht zusätzlich dort um, wo das Thema wechselt; dafür rechnet ein "
                + "Embedding-Modell je Satz. Lokal rechnet im Client, Cloud schickt jeden Satz an die Enterprise-API. "
                + "Ohne Modell trennen nur Leerzeilen die Absätze.");
        embeddingSource = embedding.comboBox("Embeddings für Absätze",
                "Lokales Embedding-Modell (Standard) oder das Cloud-Embedding-Modell der Wissensbasis "
                        + "(speech.readAloud.embeddingModel)", SOURCE_LOCAL, SOURCE_CLOUD);
        FormRows localRows = new FormRows(palette);
        models.mirror(new CategoryModelRow(localRows, ModelCategory.EMBEDDING, true, false, localEmbedding, actions,
                palette), true);
        FormRows cloudRows = new FormRows(palette);
        cloudRows.note("Das Embedding-Modell der Wissensbasis (Einstellungen → Modelle).");
        embeddingDeck.setOpaque(false);
        embeddingDeck.add(localRows.panel(), SOURCE_LOCAL);
        embeddingDeck.add(cloudRows.panel(), SOURCE_CLOUD);
        embeddingSource.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                embeddingPages.show(embeddingDeck, (String) embeddingSource.getSelectedItem());
            }
        });
        JPanel embeddingContent = new JPanel(new BorderLayout(0, 0));
        embeddingContent.setOpaque(false);
        embeddingContent.add(embedding.panel(), BorderLayout.NORTH);
        embeddingContent.add(embeddingDeck, BorderLayout.CENTER);

        panel = FormRows.column(palette,
                FormRows.plate("Sprachausgabe", palette.getAgentPetrol(), speech.panel(), palette),
                FormRows.plate("Absätze", palette.getNavigationBlue(), embeddingContent, palette));
    }

    JComboBox<String> embeddingSource() {
        return embeddingSource;
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        readAloudAutoStart.setSelected(form.readAloudAutoStart());
        chunking.setSelectedIndex(chunkingIndex(form.readAloudChunking()));
        workers.setSelectedItem(workersItem(form.synthesisWorkers()));
        String embeddingModel = form.readAloudEmbedding();
        boolean cloud = "cloud".equals(embeddingModel);
        localEmbedding.set(cloud ? "" : embeddingModel);
        embeddingSource.setSelectedItem(cloud ? SOURCE_CLOUD : SOURCE_LOCAL);
    }

    void store(SettingsForm.Builder b) {
        b.readAloudAutoStart(readAloudAutoStart.isSelected());
        b.readAloudChunking(chunkingValue(chunking.getSelectedIndex()));
        b.synthesisWorkers((String) workers.getSelectedItem());
        b.readAloudEmbedding(SOURCE_CLOUD.equals(embeddingSource.getSelectedItem()) ? "cloud" : localEmbedding.get());
    }

    private static int chunkingIndex(String value) {
        if ("SENTENCES".equals(value)) {
            return 1;
        }
        if ("ANSWER".equals(value)) {
            return 2;
        }
        return 0;
    }

    private static String chunkingValue(int index) {
        switch (index) {
            case 1:
                return "SENTENCES";
            case 2:
                return "ANSWER";
            default:
                return "PARAGRAPHS";
        }
    }

    private static String workersItem(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() == 1 && trimmed.charAt(0) >= '1' && trimmed.charAt(0) <= '8') {
            return trimmed;
        }
        return "2";
    }
}
