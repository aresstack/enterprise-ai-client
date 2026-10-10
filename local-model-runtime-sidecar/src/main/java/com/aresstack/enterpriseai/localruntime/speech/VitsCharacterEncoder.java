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
final class VitsCharacterEncoder implements VoiceTextEncoder {

    private final Map<String, Integer> vocabulary;
    private final boolean addBlank;
    private final boolean lowerCase;

    VitsCharacterEncoder(Map<String, Integer> vocabulary, boolean addBlank, boolean lowerCase) {
        this.vocabulary = vocabulary;
        this.addBlank = addBlank;
        this.lowerCase = lowerCase;
    }

    @Override
    public long[] encode(String text) {
        List<Integer> ids = new ArrayList<>();
        text.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            Integer id = vocabulary.get(character);
            if (id == null && lowerCase) {
                id = vocabulary.get(character.toLowerCase(Locale.ROOT));
            }
            if (id != null) {
                ids.add(id);
            }
        });
        if (ids.isEmpty()) {
            return new long[0];
        }
        if (!addBlank) {
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
