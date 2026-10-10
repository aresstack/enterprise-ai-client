package com.aresstack.enterpriseai.localruntime.speech;

import java.text.Normalizer;
import java.util.Locale;

/** Piper's {@code phoneme_type "text"}: the lower-cased characters themselves are the phonemes. */
final class CharacterPhonemizer implements Phonemizer {

    @Override
    public String phonemize(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return lower.replaceAll("\\s+", " ").trim();
    }
}
