package com.aresstack.enterpriseai.speech.local;

import java.text.Normalizer;
import java.util.Locale;

/** Piper {@code phoneme_type "text"}: die kleingeschriebenen Zeichen selbst sind die Phoneme. */
final class CharacterPhonemizer implements Phonemizer {

    @Override
    public String phonemize(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return lower.replaceAll("\\s+", " ").trim();
    }
}
