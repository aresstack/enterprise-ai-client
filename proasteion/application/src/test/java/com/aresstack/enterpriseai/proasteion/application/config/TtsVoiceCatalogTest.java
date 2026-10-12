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
        assertEquals("thomas", catalog.defaultVoice("orpheus-tts-3b-de"));
        assertEquals("Vivian", catalog.defaultVoice("Qwen3-TTS-12Hz-1.7B-CustomVoice"));
        assertEquals("thomas", catalog.voice("orpheus-tts-3b-de", TtsVoiceCatalog.Gender.MALE));
        assertEquals("jana", catalog.voice("orpheus-tts-3b-de", TtsVoiceCatalog.Gender.FEMALE));
        assertEquals("Ryan", catalog.voice("qwen3-tts", TtsVoiceCatalog.Gender.MALE));
        assertEquals(TtsVoiceCatalog.Gender.FEMALE, catalog.genderOf("qwen3-tts", "Vivian"));
        assertEquals("thomas", catalog.voices("orpheus-tts-3b-de").get(0));
        assertTrue(catalog.voices("unbekanntes-modell").isEmpty());
        assertNull(catalog.defaultVoice("unbekanntes-modell"));
    }

    @Test
    public void everyModelSpeaksWithItsOwnVoice() {
        Map<String, String> stored = ModelsConfig.parseSpeechVoices("orpheus-tts-3b-de=max");
        assertEquals("kipitz:orpheus-tts-3b-de", stored.keySet().iterator().next());

        assertEquals("max", ModelsConfig.resolveTtsVoice(orpheus, stored.get(orpheus.key()), null, catalog));
        // ohne gespeicherte Stimme: Standard des Modells, nie leer und nie die Stimme eines anderen Modells
        assertEquals("Vivian", ModelsConfig.resolveTtsVoice(qwen, stored.get(qwen.key()), null, catalog));
        assertEquals("Vivian", ModelsConfig.resolveTtsVoice(qwen, null, "thomas", catalog));
        // frühere globale Stimme gilt, wenn sie zum Modell passt oder das Modell unbekannt ist
        assertEquals("jana", ModelsConfig.resolveTtsVoice(orpheus, null, "jana", catalog));
        assertEquals("alloy", ModelsConfig.resolveTtsVoice(ModelsConfig.parse("tts-1"), null, "alloy", catalog));
        assertNull(ModelsConfig.resolveTtsVoice(ModelsConfig.parse("tts-1"), null, null, catalog));
        // lokale Stimmen: genau ein Sprecher
        assertNull(ModelsConfig.resolveTtsVoice(ModelsConfig.parse("local:de_DE-thorsten-medium"), "x", "y",
                catalog));
    }

    @Test
    public void speechVoicesRoundTrip() {
        Map<String, String> voices = new LinkedHashMap<String, String>();
        voices.put("kipitz:orpheus-tts-3b-de", "thomas");
        voices.put("qwen3-tts", "Vivian");
        voices.put("leer", " ");
        String text = ModelsConfig.formatSpeechVoices(voices);
        assertEquals("kipitz:orpheus-tts-3b-de=thomas;kipitz:qwen3-tts=Vivian", text);
        assertEquals(2, ModelsConfig.parseSpeechVoices(text).size());
        assertEquals("", ModelsConfig.formatSpeechVoices(null));
        assertTrue(ModelsConfig.parseSpeechVoices("kaputt;=x;y=").isEmpty());
    }
}
