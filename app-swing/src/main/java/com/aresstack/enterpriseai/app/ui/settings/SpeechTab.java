package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JPanel;

/**
 * Kategorie „Sprachausgabe“ (askai arch {@code readAloudAutoStart}): automatisches Vorlesen neuer Antworten. Die
 * Stimme wählt „Modelle“ in der Kategorie TTS, den Java-21-Sidecar „Lokale Modelle“ im selben Reiter.
 */
final class SpeechTab {

    private final JCheckBox readAloudAutoStart;
    private final JPanel panel;

    SpeechTab(ComicPalette palette) {
        FormRows speech = new FormRows(palette);
        speech.note("Vorgelesen wird über den optionalen lokalen Java-21-Sidecar mit der Stimme aus „Modelle“ "
                + "(Kategorie TTS). Ohne Stimme bleibt der Play/Pause-Orb über dem Verlauf aus.");
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
