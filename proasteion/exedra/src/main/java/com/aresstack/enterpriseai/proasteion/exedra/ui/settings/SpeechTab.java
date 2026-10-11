package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * Kategorie „Sprachausgabe“ (askai arch {@code readAloudAutoStart}, {@code chunking}, {@code synthesisWorkers}): nur
 * das Verhalten beim Vorlesen. Welche Stimme spricht, bestimmt allein das TTS-Modell unter „Modelle“ (Cloud-Modell
 * oder lokale Stimme); hier gibt es keine zweite Auswahl.
 */
final class SpeechTab {

    /** Anzeige der Einheiten in der Reihenfolge von {@link #chunkingValue(int)}. */
    private static final String CHUNKING_PARAGRAPHS = "Absätze";
    private static final String CHUNKING_SENTENCES = "Sätze";
    private static final String CHUNKING_ANSWER = "Ganze Antwort";

    private final JCheckBox readAloudAutoStart;
    private final JComboBox<String> chunking;
    private final JComboBox<String> workers;
    private final JPanel panel;

    SpeechTab(ComicPalette palette) {
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
        panel = FormRows.column(palette,
                FormRows.plate("Sprachausgabe", palette.getAgentPetrol(), speech.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
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
