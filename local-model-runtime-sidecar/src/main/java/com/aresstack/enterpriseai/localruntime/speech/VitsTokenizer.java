package com.aresstack.enterpriseai.localruntime.speech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Character tokenizer of the Hugging Face {@code VitsTokenizer} (MMS-TTS) without phonemizer and uroman:
 * characters missing from the vocabulary are lower-cased, characters still missing are dropped, and with
 * {@code add_blank} token id 0 is interspersed before, between and after the characters.
 */
final class VitsTokenizer {

    private VitsTokenizer() {
    }

    static long[] tokenize(String text, LocalSpeechModel model) {
        Map<String, Integer> vocabulary = model.vocabulary();
        List<Integer> ids = new ArrayList<>();
        text.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            Integer id = vocabulary.get(character);
            if (id == null && model.lowerCase()) {
                id = vocabulary.get(character.toLowerCase(Locale.ROOT));
            }
            if (id != null) {
                ids.add(id);
            }
        });
        if (!model.addBlank()) {
            long[] plain = new long[ids.size()];
            for (int i = 0; i < plain.length; i++) {
                plain[i] = ids.get(i);
            }
            return plain;
        }
        long[] interspersed = new long[ids.size() * 2 + 1];
        for (int i = 0; i < ids.size(); i++) {
            interspersed[i * 2 + 1] = ids.get(i);
        }
        return interspersed;
    }
}
