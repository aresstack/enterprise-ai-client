package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Stimme je TTS-Modell: Katalog der Familien, Auflösung zur Laufzeit und Speicherform {@code speech.voices}. */
public class TtsVoiceCatalogTest {

    private final TtsVoiceCatalog catalog = TtsVoiceCatalog.bundled();
    private final ModelReference orpheus = ModelsConfig.parse("kipitz:orpheus-tts-3b-de");
    private final ModelReference qwen = ModelsConfig.parse("Qwen3-TTS-12Hz-1.7B-CustomVoice");

    @Test
    public void familiesComeFromTheBundledFile() {
        assertEquals("Jana", catalog.defaultVoice("orpheus-tts-3b-de"));
        assertEquals("Vivian", catalog.defaultVoice("Qwen3-TTS-12Hz-1.7B-CustomVoice"));
        assertEquals("Thomas", catalog.voice("orpheus-tts-3b-de", TtsVoiceCatalog.Gender.MALE));
        assertEquals("Jana", catalog.voice("orpheus-tts-3b-de", TtsVoiceCatalog.Gender.FEMALE));
        assertEquals("Ryan", catalog.voice("qwen3-tts", TtsVoiceCatalog.Gender.MALE));
        assertEquals(TtsVoiceCatalog.Gender.FEMALE, catalog.genderOf("qwen3-tts", "Vivian"));
        assertEquals("Jana", catalog.voices("orpheus-tts-3b-de").get(0));
        assertTrue(catalog.voices("unbekanntes-modell").isEmpty());
        assertNull(catalog.defaultVoice("unbekanntes-modell"));
    }

    @Test
    public void everyModelSpeaksWithItsOwnVoice() {
        Map<String, String> stored = ModelsConfig.parseSpeechVoices("orpheus-tts-3b-de=Max");
        assertEquals("kipitz:orpheus-tts-3b-de", stored.keySet().iterator().next());

        assertEquals("Max", ModelsConfig.resolveTtsVoice(orpheus, stored.get(orpheus.key()), catalog));
        // ohne gespeicherte Stimme: Standard des Modells, nie leer und nie die Stimme eines anderen Modells
        assertEquals("Vivian", ModelsConfig.resolveTtsVoice(qwen, stored.get(qwen.key()), catalog));
        assertNull(ModelsConfig.resolveTtsVoice(ModelsConfig.parse("tts-1"), null, catalog));
        // lokale Stimmen: genau ein Sprecher
        assertNull(ModelsConfig.resolveTtsVoice(ModelsConfig.parse("local:de_DE-thorsten-medium"), "x", catalog));
    }

    @Test
    public void legacySpeechVoiceBecomesTheStoredVoiceOfTheSelectedModelOnly() {
        // passt zum Modell: wird dessen gespeicherte Stimme, unverändert geschrieben
        Map<String, String> adopted = ModelsConfig.adoptLegacyVoice(null, orpheus, "Thomas", catalog);
        assertEquals("Thomas", adopted.get(orpheus.key()));
        assertEquals(1, adopted.size());
        // unbekanntes Modell: wie bisher übernommen (der Dienst entscheidet über den Namen)
        ModelReference unknown = ModelsConfig.parse("tts-1");
        assertEquals("alloy", ModelsConfig.adoptLegacyVoice(null, unknown, "alloy", catalog).get(unknown.key()));
        // passt nicht: keine zweite Quelle, das Modell spricht mit seiner Standardstimme
        assertTrue(ModelsConfig.adoptLegacyVoice(null, qwen, "Thomas", catalog).isEmpty());
        // eine gespeicherte Stimme gewinnt immer
        assertEquals("Max", ModelsConfig.adoptLegacyVoice(ModelsConfig.parseSpeechVoices("orpheus-tts-3b-de=Max"),
                orpheus, "Thomas", catalog).get(orpheus.key()));
        // lokale Stimmen haben genau einen Sprecher
        assertTrue(ModelsConfig.adoptLegacyVoice(null, ModelsConfig.parse("local:de_DE-thorsten-medium"), "Thomas",
                catalog).isEmpty());
        // Groß-/Kleinschreibung bleibt: „thomas“ ist keine Orpheus-Stimme und wird nicht still zu „Thomas“
        assertTrue(ModelsConfig.adoptLegacyVoice(null, orpheus, "thomas", catalog).isEmpty());
    }

    @Test
    public void orpheusSpeakersKeepTheExactSpellingOfTheService() {
        // The service rejects "thomas": speakers are opaque identifiers and must never be case-normalized.
        assertEquals(java.util.Arrays.asList("Jana", "Thomas", "Max"), catalog.voices("orpheus-tts-3b-de"));
        assertEquals("Thomas", catalog.voice("kipitz:orpheus-tts-3b-de", TtsVoiceCatalog.Gender.MALE));
        assertEquals("Jana", catalog.voice("ORPHEUS-TTS-3B-DE", TtsVoiceCatalog.Gender.FEMALE));
        assertEquals("Jana", catalog.defaultVoice("orpheus-tts-3b-de"));
        assertEquals(TtsVoiceCatalog.Gender.MALE, catalog.genderOf("orpheus-tts-3b-de", "Max"));
        assertEquals("Thomas", ModelsConfig.resolveTtsVoice(orpheus, "Thomas", catalog));
        assertEquals("Thomas", ModelsConfig.parseSpeechVoices(
                ModelsConfig.formatSpeechVoices(java.util.Collections.singletonMap("orpheus-tts-3b-de", "Thomas")))
                .get(orpheus.key()));
        for (String voice : catalog.voices("orpheus-tts-3b-de")) {
            assertTrue(voice, Character.isUpperCase(voice.charAt(0)));
        }
    }

    @Test
    public void merlinsDaughterSpeaksWithQwenVivianByDefault() {
        assertEquals("Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice", PreferredModels.bundled().model("tts"));
        assertEquals("Vivian", ModelsConfig.resolveTtsVoice(ModelsConfig.parse("kipitz:"
                + PreferredModels.bundled().model("tts")), null, catalog));
        // A user who picks Orpheus for the assistant still hears a female speaker.
        assertEquals("Jana", ModelsConfig.resolveTtsVoice(orpheus, null, catalog));
        // Merlin keeps his own male Orpheus speaker.
        assertEquals("Thomas", ModelsConfig.defaultMerlinSpeaker());
    }

    @Test
    public void merlinSpeaksWithTheCapitalizedOrpheusSpeaker() {
        assertEquals("kipitz:orpheus-tts-3b-de", ModelsConfig.defaultMerlinVoice());
        assertEquals("Thomas", ModelsConfig.defaultMerlinSpeaker());
    }

    @Test
    public void speechVoicesRoundTrip() {
        Map<String, String> voices = new LinkedHashMap<String, String>();
        voices.put("kipitz:orpheus-tts-3b-de", "Thomas");
        voices.put("qwen3-tts", "Vivian");
        voices.put("leer", " ");
        String text = ModelsConfig.formatSpeechVoices(voices);
        assertEquals("kipitz:orpheus-tts-3b-de=Thomas;kipitz:qwen3-tts=Vivian", text);
        assertEquals(2, ModelsConfig.parseSpeechVoices(text).size());
        assertEquals("", ModelsConfig.formatSpeechVoices(null));
        assertTrue(ModelsConfig.parseSpeechVoices("kaputt;=x;y=").isEmpty());
    }
}
