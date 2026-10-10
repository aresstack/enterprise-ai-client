package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 * Kategorie „Sprachausgabe“ (askai arch {@code readAloudAutoStart}): nur das Verhalten beim Vorlesen. Welche
 * Stimme spricht, bestimmt allein das TTS-Modell unter „Modelle“ (Cloud-Modell oder lokale Stimme); hier gibt es
 * keine zweite Auswahl.
 */
final class SpeechTab {

    private final JCheckBox readAloudAutoStart;
    private final JPanel panel;

    SpeechTab(ComicPalette palette) {
        FormRows speech = new FormRows(palette);
        speech.note("Gesprochen wird mit dem TTS-Modell aus „Modelle“: ein Cloud-Modell der Enterprise-API oder "
                + "eine lokale Stimme. Ohne TTS-Modell bleibt der Play/Pause-Orb über dem Verlauf aus.");
        readAloudAutoStart = speech.checkBox("Neue Antworten automatisch vorlesen",
                "Der Orb ist beim Start aktiv: jede neue Antwort wird sofort vorgelesen (speech.readAloud.autoStart)");
        panel = FormRows.column(palette,
                FormRows.plate("Sprachausgabe", palette.getAgentPetrol(), speech.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    void load(SettingsForm form) {
        readAloudAutoStart.setSelected(form.readAloudAutoStart());
    }

    void store(SettingsForm.Builder b) {
        b.readAloudAutoStart(readAloudAutoStart.isSelected());
    }
}
