package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Kategorie „Sprachausgabe“: oben Modell und Stimme (Sprecher) der Sprachausgabe, darunter das Verhalten beim
 * Vorlesen (askai arch {@code readAloudAutoStart}, {@code chunking}, {@code synthesisWorkers}). Das Modell ist eine
 * Sicht auf dieselbe Auswahl wie unter „Modelle“. Die Stimme gehört zum Modell ({@code speech.voices}): gültige
 * Sprecher hängen vom Modell ab, beim Modellwechsel erscheint dessen gespeicherte oder Standardstimme. Ohne feste
 * Stimme wählen Modelle mit mehreren Sprechern je Absatz selbst; deshalb spricht der Client immer mit einer.
 * Ganz oben steht der Name des Assistenten ({@link AssistantNameSection}); er hängt nie an Modell oder Stimme.
 */
final class SpeechTab {

    static final String TEST_LABEL = "Stimme testen";
    static final String TESTING_LABEL = "Spreche …";

    /** Anzeige der Einheiten in der Reihenfolge von {@link #chunkingValue(int)}. */
    private static final String CHUNKING_PARAGRAPHS = "Absätze";
    private static final String CHUNKING_SENTENCES = "Sätze";
    private static final String CHUNKING_ANSWER = "Ganze Antwort";

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final ModelSelection tts;
    private final CategoryModelRow ttsRow;
    private final JComboBox<String> voice = FormRows.<String>fittingComboBox();
    private final JTextField voiceField;
    private final ComicButton test;
    private final JLabel testStatus = new WrappingLabel(" ");
    /** Die ausdrücklich gewählten Stimmen je Modell (Schlüssel {@code <katalog>:<modell>}). */
    private final Map<String, String> voices = new LinkedHashMap<String, String>();
    private SettingsForm loaded;
    private boolean filling;
    private final JCheckBox readAloudAutoStart;
    private final JComboBox<String> chunking;
    private final JComboBox<String> workers;
    private final JComboBox<String> attempts;
    private final AssistantNameSection assistantName;
    private final JTextField merlinVoice;
    private final JTextField merlinSpeaker;
    private final JPanel panel;

    SpeechTab(ModelsTab models, SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.tts = models.selection(ModelCategory.TTS);

        FormRows voiceRows = new FormRows(palette);
        voiceRows.note("Modelle mit mehreren Sprechern wählen ohne feste Stimme bei jedem Absatz selbst, dann wechselt "
                + "die Stimme mitten in der Antwort. Die Stimme gilt je Modell: beim Wechsel des Modells erscheint "
                + "dessen eigene Stimme.");
        ttsRow = new CategoryModelRow(voiceRows, ModelCategory.TTS, false, false, tts, actions, palette);
        models.mirror(ttsRow, false);
        voice.setEditable(true);
        voice.setForeground(palette.getInk());
        voice.setBackground(Color.WHITE);
        voice.getAccessibleContext().setAccessibleName("Stimme der Sprachausgabe");
        voiceField = (JTextField) voice.getEditor().getEditorComponent();
        voiceField.setForeground(palette.getInk());
        voiceRows.component("Stimme", voice, "Sprecher des TTS-Modells, wie der Dienst ihn nennt (speech.voices); aus "
                + "der Liste wählen oder eintippen. Lokale Stimmen haben genau einen Sprecher.");
        voiceField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                voiceEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                voiceEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                voiceEdited();
            }
        });
        voice.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                voiceEdited();
            }
        });
        test = new ComicButton(TEST_LABEL, null, ComicButton.Accent.ACTION, palette);
        test.setToolTipText("Spricht einen kurzen Satz mit Modell und Stimme dieses Entwurfs");
        test.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testVoice();
            }
        });
        testStatus.putClientProperty("html.disable", Boolean.TRUE);
        testStatus.setForeground(FormRows.MUTED);
        JPanel testRow = new JPanel(new BorderLayout(10, 0));
        testRow.setOpaque(false);
        testRow.add(test, BorderLayout.WEST);
        testRow.add(testStatus, BorderLayout.CENTER);
        voiceRows.component(null, testRow, null);
        tts.onChange(new Runnable() {
            @Override
            public void run() {
                showVoice();
            }
        });

        FormRows speech = new FormRows(palette);
        speech.note("Ohne TTS-Modell bleibt der Play/Pause-Orb über dem Verlauf aus. Eine lokale Stimme wählst du "
                + "unter „Modelle“ → „Lokale Modelle“.");
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
        attempts = speech.comboBox("Versuche je Abschnitt",
                "Wie oft ein Abschnitt angefragt wird, bevor das Vorlesen mit einer Meldung abbricht; ein fehlender "
                        + "Abschnitt wird nie übersprungen (speech.readAloud.synthesisAttempts)",
                "1", "2", "3", "4", "5");
        speech.note("Absätze sind der beste Kompromiss aus Klang und Wartezeit: Sätze klingen früher, aber "
                + "abgehackter, die ganze Antwort klingt am natürlichsten, beginnt aber erst nach der ganzen Synthese.");
        FormRows merlin = new FormRows(palette);
        merlin.note("Merlin, der Begleiter des Einrichtungsassistenten, hat eine eigene Stimme, unabhängig vom "
                + "TTS-Modell des Chats. Ist sie nicht verfügbar, zeigt er seine Sätze nur als Sprechblase.");
        merlinVoice = merlin.textField("Stimme von Merlin",
                "TTS-Modell als [Katalog:]Modell, z. B. das der Enterprise-API (setup.merlin.ttsModel)");
        merlinSpeaker = merlin.textField("Sprecher von Merlin",
                "Fester Sprecher dieses Modells, damit Merlin immer gleich klingt (setup.merlin.ttsVoice)");
        assistantName = new AssistantNameSection(palette);
        panel = FormRows.column(palette, assistantName.panel(),
                FormRows.plate("Modell und Stimme", palette.getNavigationBlue(), voiceRows.panel(), palette),
                FormRows.plate("Sprachausgabe", palette.getAgentPetrol(), speech.panel(), palette),
                FormRows.plate("Merlin", palette.getAgentPetrol(), merlin.panel(), palette));
        showVoice();
    }

    JPanel panel() {
        return panel;
    }

    AssistantNameSection assistantName() {
        return assistantName;
    }

    void load(SettingsForm form) {
        assistantName.load(form);
        loaded = form;
        voices.clear();
        for (Map.Entry<String, String> entry : form.speechVoices().entrySet()) {
            String key = key(entry.getKey());
            if (key != null) {
                voices.put(key, entry.getValue());
            }
        }
        showVoice();
        readAloudAutoStart.setSelected(form.readAloudAutoStart());
        chunking.setSelectedIndex(chunkingIndex(form.readAloudChunking()));
        workers.setSelectedItem(workersItem(form.synthesisWorkers()));
        attempts.setSelectedItem(attemptsItem(form.synthesisAttempts()));
        merlinVoice.setText(form.merlinVoice());
        merlinSpeaker.setText(form.merlinSpeaker());
    }

    void store(SettingsForm.Builder b) {
        assistantName.store(b);
        b.speechVoices(voices);
        b.readAloudAutoStart(readAloudAutoStart.isSelected());
        b.readAloudChunking(chunkingValue(chunking.getSelectedIndex()));
        b.synthesisWorkers((String) workers.getSelectedItem());
        b.synthesisAttempts((String) attempts.getSelectedItem());
        b.merlinVoice(merlinVoice.getText());
        b.merlinSpeaker(merlinSpeaker.getText());
    }

    /** Die Stimme des gewählten Modells: gespeichert, sonst wie zur Laufzeit (frühere globale oder Standard). */
    private void showVoice() {
        String selection = tts.get();
        ModelReference model = actions.parseModel(selection);
        boolean speaks = model != null && !actions.isLocal(model);
        filling = true;
        try {
            voice.removeAllItems();
            List<String> known = speaks ? actions.ttsVoices(selection) : Collections.<String>emptyList();
            for (String name : known) {
                voice.addItem(name);
            }
            String stored = model == null ? null : voices.get(model.key());
            String shown = stored != null ? stored
                    : speaks && loaded != null ? actions.ttsVoice(loaded.toBuilder().speechVoices(voices).build(),
                    selection) : "";
            voiceField.setText(shown == null ? "" : shown);
        } finally {
            filling = false;
        }
        voice.setEnabled(speaks);
        test.setEnabled(model != null);
        testStatus.setText(model == null ? "Kein TTS-Modell gewählt."
                : speaks ? genderNote(selection, voiceField.getText()) : "Lokale Stimme: genau ein Sprecher.");
    }

    /** Eine Eingabe in „Stimme“ gilt als ausdrückliche Wahl für das gewählte Modell. */
    private void voiceEdited() {
        if (filling) {
            return;
        }
        ModelReference model = actions.parseModel(tts.get());
        if (model == null || actions.isLocal(model)) {
            return;
        }
        String text = currentVoice();
        if (text.isEmpty()) {
            voices.remove(model.key());
        } else {
            voices.put(model.key(), text);
        }
        testStatus.setText(genderNote(tts.get(), text));
    }

    private void testVoice() {
        test.setEnabled(false);
        test.setText(TESTING_LABEL);
        testStatus.setText(" ");
        try {
            actions.testVoice(form.get(), tts.get(), currentVoice(), new Consumer<String>() {
                @Override
                public void accept(String failure) {
                    test.setEnabled(true);
                    test.setText(TEST_LABEL);
                    testStatus.setText(failure == null ? "Gesprochen." : failure);
                }
            });
        } catch (RuntimeException e) {
            test.setEnabled(true);
            test.setText(TEST_LABEL);
            testStatus.setText("Test konnte nicht gestartet werden: " + e.getClass().getSimpleName());
        }
    }

    private String genderNote(String selection, String name) {
        String gender = actions.ttsVoiceGender(selection, name);
        return gender.isEmpty() ? " " : "Stimme " + name + ": " + gender;
    }

    private String currentVoice() {
        return voiceField.getText() == null ? "" : voiceField.getText().trim();
    }

    private String key(String selection) {
        ModelReference model = actions.parseModel(selection);
        return model == null ? null : model.key();
    }

    /** Das Feld „Stimme“ (für Tests). */
    JComboBox<String> voice() {
        return voice;
    }

    ComicButton testButton() {
        return test;
    }

    JLabel testStatus() {
        return testStatus;
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

    private static String attemptsItem(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() == 1 && trimmed.charAt(0) >= '1' && trimmed.charAt(0) <= '5') {
            return trimmed;
        }
        return "2";
    }

    private static String workersItem(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() == 1 && trimmed.charAt(0) >= '1' && trimmed.charAt(0) <= '8') {
            return trimmed;
        }
        return "2";
    }
}
