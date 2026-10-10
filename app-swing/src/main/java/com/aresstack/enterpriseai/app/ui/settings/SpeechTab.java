package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import com.aresstack.enterpriseai.domain.modelcatalog.ModelCategory;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Kategorie „Sprachausgabe“ (askai arch {@code readAloudAutoStart}): automatisches Vorlesen neuer Antworten. Die
 * Stimme wählt „Modelle“ in der Kategorie TTS, den Java-21-Sidecar „Lokale Modelle“ im selben Reiter. „Lokale
 * Stimmen“ installiert kuratierte Stimmen für den Sidecar und setzt die TTS-Auswahl in „Modelle“.
 */
final class SpeechTab {

    private final JCheckBox readAloudAutoStart;
    private final LocalVoiceRow localVoice;
    private final JPanel panel;

    SpeechTab(SettingsDialogActions actions, Supplier<SettingsForm> form, final ModelsTab models,
              ComicPalette palette) {
        FormRows speech = new FormRows(palette);
        speech.note("Vorgelesen wird über den optionalen lokalen Java-21-Sidecar mit der Stimme aus „Modelle“ "
                + "(Kategorie TTS). Ohne Stimme bleibt der Play/Pause-Orb über dem Verlauf aus.");
        readAloudAutoStart = speech.checkBox("Neue Antworten automatisch vorlesen",
                "Der Orb ist beim Start aktiv: jede neue Antwort wird sofort vorgelesen (speech.readAloud.autoStart)");
        FormRows voices = new FormRows(palette);
        voices.note("Stimmen für den lokalen Sidecar, geladen vom Hugging Face Hub über die Netzwerkeinstellungen "
                + "ins Modellverzeichnis aus „Modelle“. Ohne externes Programm; die deutsche Aussprache ist "
                + "regelbasiert.");
        localVoice = new LocalVoiceRow(voices, actions, form, new Consumer<String>() {
            @Override
            public void accept(String selection) {
                models.row(ModelCategory.TTS).load(selection);
                models.refresh();
            }
        }, palette);
        panel = FormRows.column(palette,
                FormRows.plate("Sprachausgabe", palette.getAgentPetrol(), speech.panel(), palette),
                FormRows.plate("Lokale Stimmen", palette.getAccentOrange(), voices.panel(), palette));
    }

    JPanel panel() {
        return panel;
    }

    /** Beim ersten Anzeigen: Installationsstand der lokalen Stimmen lesen. */
    void shown() {
        localVoice.shown();
    }

    void load(SettingsForm form) {
        readAloudAutoStart.setSelected(form.readAloudAutoStart());
    }

    void store(SettingsForm.Builder b) {
        b.readAloudAutoStart(readAloudAutoStart.isSelected());
    }
}
