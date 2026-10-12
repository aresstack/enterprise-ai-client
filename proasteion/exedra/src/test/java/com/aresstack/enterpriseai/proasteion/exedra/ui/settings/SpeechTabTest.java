package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** „Sprachausgabe“: Stimme je TTS-Modell, Modellwechsel zeigt die Stimme des neuen Modells, „Stimme testen“. */
public class SpeechTabTest {

    private static final class VoiceActions implements SettingsDialogActions {

        final List<String> tested = new ArrayList<String>();

        @Override
        public List<String> validate(SettingsForm form) {
            return Collections.emptyList();
        }

        @Override
        public void save(SettingsForm form) {
        }

        @Override
        public void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult) {
            onResult.accept(SecretCheckResult.ok("gefunden"));
        }

        @Override
        public List<String> ttsVoices(String modelSelection) {
            if (modelSelection.contains("orpheus")) {
                return Arrays.asList("Thomas", "Max", "Jana");
            }
            return modelSelection.contains("qwen") ? Arrays.asList("Vivian", "Serena", "Ryan")
                    : Collections.<String>emptyList();
        }

        @Override
        public String ttsVoice(SettingsForm form, String modelSelection) {
            for (Map.Entry<String, String> entry : form.speechVoices().entrySet()) {
                if (entry.getKey().endsWith(modelSelection)) {
                    return entry.getValue();
                }
            }
            return ttsVoices(modelSelection).isEmpty() ? "" : ttsVoices(modelSelection).get(0);
        }

        @Override
        public String ttsVoiceGender(String modelSelection, String voice) {
            return "Thomas".equals(voice) ? "männlich" : "";
        }

        @Override
        public void testVoice(SettingsForm form, String modelSelection, String voice, Consumer<String> onResult) {
            tested.add(modelSelection + "/" + voice);
            onResult.accept(null);
        }
    }

    @Test
    public void voiceFollowsTheModelAndIsStoredPerModel() throws Exception {
        final VoiceActions actions = new VoiceActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsPanel panel = new SettingsPanel(SettingsForm.builder()
                        .modelSelection(ModelCategory.TTS.key(), "orpheus-tts-3b-de").build(),
                        Collections.<String>emptyList(), SettingsPanel.Mode.EDIT, actions,
                        ComicPalette.defaultPalette());
                SpeechTab tab = panel.speechTab();
                JTextField field = (JTextField) tab.voice().getEditor().getEditorComponent();
                assertEquals("Thomas", field.getText());
                assertEquals(3, tab.voice().getItemCount());
                assertTrue(tab.voice().isEnabled());
                assertTrue("Standardstimme wird nicht als Wahl gespeichert",
                        panel.toForm().speechVoices().isEmpty());

                panel.modelsTab().selection(ModelCategory.TTS).set("qwen3-tts");
                assertEquals("Vivian", field.getText());
                field.setText("Serena");
                Map<String, String> stored = panel.toForm().speechVoices();
                assertEquals(1, stored.size());
                assertEquals("Serena", stored.values().iterator().next());
                assertTrue(stored.keySet().iterator().next().endsWith("qwen3-tts"));

                panel.modelsTab().selection(ModelCategory.TTS).set("orpheus-tts-3b-de");
                assertEquals("Orpheus spricht nicht mit der Qwen-Stimme", "Thomas", field.getText());
                panel.modelsTab().selection(ModelCategory.TTS).set("qwen3-tts");
                assertEquals("Serena", field.getText());

                tab.testButton().doClick();
                assertEquals("[qwen3-tts/Serena]", actions.tested.toString());
                assertEquals("Gesprochen.", tab.testStatus().getText());
                assertEquals(SpeechTab.TEST_LABEL, tab.testButton().getText());

                panel.modelsTab().selection(ModelCategory.TTS).set("");
                assertFalse(tab.voice().isEnabled());
                assertFalse(tab.testButton().isEnabled());
            }
        });
    }

    @Test
    public void savedVoicesSurviveLoadAndStore() throws Exception {
        final VoiceActions actions = new VoiceActions();
        onEdt(new Runnable() {
            @Override
            public void run() {
                SettingsForm form = SettingsForm.builder()
                        .modelSelection(ModelCategory.TTS.key(), "qwen3-tts")
                        .speechVoices(Collections.singletonMap("qwen3-tts", "Ryan")).build();
                SettingsPanel panel = new SettingsPanel(form, Collections.<String>emptyList(),
                        SettingsPanel.Mode.EDIT, actions, ComicPalette.defaultPalette());
                JTextField field = (JTextField) panel.speechTab().voice().getEditor().getEditorComponent();
                assertEquals("Ryan", field.getText());
                assertEquals("Ryan", panel.toForm().speechVoices().get("default:qwen3-tts"));
                assertNull(panel.toForm().speechVoices().get("default:orpheus-tts-3b-de"));
            }
        });
    }

    private static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }
}
