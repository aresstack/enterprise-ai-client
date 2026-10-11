package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Kategorie „Sprachausgabe“ (askai arch {@code readAloudAutoStart}, {@code chunking}, {@code synthesisWorkers} und die
 * Stimmenwahl Engine + Stimme): Modell lokal oder Cloud, Standard lokal, und das Verhalten beim Vorlesen. Die Wahl
 * ist dieselbe {@link ModelSelection} wie unter „Modelle“ ({@code model.tts}), keine zweite Wahrheit.
 */
final class SpeechTab {

    static final String SOURCE_LOCAL = "Lokal (im Client)";
    static final String SOURCE_CLOUD = "Cloud (Enterprise-API)";

    /** Anzeige der Einheiten in der Reihenfolge von {@link #chunkingValue(int)}. */
    private static final String CHUNKING_PARAGRAPHS = "Absätze";
    private static final String CHUNKING_SENTENCES = "Sätze";
    private static final String CHUNKING_ANSWER = "Ganze Antwort";

    private final ModelSelection selection;
    private final SettingsDialogActions actions;
    private final JComboBox<String> source;
    private final CardLayout modelPages = new CardLayout();
    private final JPanel modelDeck = new JPanel(modelPages);
    private final JCheckBox readAloudAutoStart;
    private final JComboBox<String> chunking;
    private final JComboBox<String> workers;
    private final JPanel panel;

    SpeechTab(ModelsTab models, SettingsDialogActions actions, ComicPalette palette) {
        this.selection = models.selection(ModelCategory.TTS);
        this.actions = actions;
        FormRows model = new FormRows(palette);
        model.note("Lokale Stimmen rechnen im Client, Cloud-Modelle auf der Enterprise-API. Dieselbe Wahl wie unter "
                + "„Modelle“; ohne Modell bleibt der Play/Pause-Orb über dem Verlauf aus.");
        source = model.comboBox("Modell", "Lokale Stimme (Standard) oder Cloud-Modell der Enterprise-API",
                SOURCE_LOCAL, SOURCE_CLOUD);
        FormRows localRows = new FormRows(palette);
        models.mirror(new CategoryModelRow(localRows, ModelCategory.TTS, true, false, selection, actions, palette),
                true);
        FormRows cloudRows = new FormRows(palette);
        models.mirror(new CategoryModelRow(cloudRows, ModelCategory.TTS, false, false, selection, actions, palette),
                false);
        modelDeck.setOpaque(false);
        modelDeck.add(localRows.panel(), SOURCE_LOCAL);
        modelDeck.add(cloudRows.panel(), SOURCE_CLOUD);
        source.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                modelPages.show(modelDeck, (String) source.getSelectedItem());
            }
        });
        selection.onChange(new Runnable() {
            @Override
            public void run() {
                showSource();
            }
        });
        JPanel modelContent = new JPanel(new java.awt.BorderLayout(0, 0));
        modelContent.setOpaque(false);
        modelContent.add(model.panel(), java.awt.BorderLayout.NORTH);
        modelContent.add(modelDeck, java.awt.BorderLayout.CENTER);

        FormRows speech = new FormRows(palette);
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
        panel = FormRows.column(palette,
                FormRows.plate("Modell", palette.getNavigationBlue(), modelContent, palette),
                FormRows.plate("Sprachausgabe", palette.getAgentPetrol(), speech.panel(), palette));
    }

    /** Zeigt die Quelle der gespeicherten Wahl; ohne Wahl lokal (Standard). */
    private void showSource() {
        ModelReference reference = actions.parseModel(selection.get());
        source.setSelectedItem(reference == null || actions.isLocal(reference) ? SOURCE_LOCAL : SOURCE_CLOUD);
    }

    JComboBox<String> source() {
        return source;
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        showSource();
        readAloudAutoStart.setSelected(form.readAloudAutoStart());
        chunking.setSelectedIndex(chunkingIndex(form.readAloudChunking()));
        workers.setSelectedItem(workersItem(form.synthesisWorkers()));
    }

    void store(SettingsForm.Builder b) {
        b.readAloudAutoStart(readAloudAutoStart.isSelected());
        b.readAloudChunking(chunkingValue(chunking.getSelectedIndex()));
        b.synthesisWorkers((String) workers.getSelectedItem());
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
