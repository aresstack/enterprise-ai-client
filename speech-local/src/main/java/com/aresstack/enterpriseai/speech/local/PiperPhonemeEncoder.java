package com.aresstack.enterpriseai.speech.local;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Piper {@code phonemes_to_ids}: BOS {@code ^}, dann jedes Phonem gefolgt vom Pad {@code _}, dann EOS {@code $}. Wie
 * piper-phonemize wird die Phonemfolge zuerst nach NFD zerlegt, dann ist jeder Code Point ein Phonem;
 * {@code phoneme_map} ersetzt zuerst. Unbekannte Symbole fallen auf ein nahes ASCII-Symbol zurück oder entfallen.
 */
final class PiperPhonemeEncoder implements VoiceTextEncoder {

    private static final String PAD = "_";
    private static final String BOS = "^";
    private static final String EOS = "$";
    private static final Map<String, String> FALLBACK;

    static {
        Map<String, String> fallback = new HashMap<String, String>();
        fallback.put("ɡ", "g");
        fallback.put("ʁ", "r");
        fallback.put("ɐ", "a");
        fallback.put("ʏ", "y");
        fallback.put("œ", "ø");
        FALLBACK = Collections.unmodifiableMap(fallback);
    }

    private final Phonemizer phonemizer;
    private final Map<String, List<Integer>> idMap;
    private final Map<String, List<String>> phonemeMap;

    PiperPhonemeEncoder(Phonemizer phonemizer, Map<String, List<Integer>> idMap,
                        Map<String, List<String>> phonemeMap) {
        this.phonemizer = phonemizer;
        this.idMap = idMap;
        this.phonemeMap = phonemeMap;
    }

    @Override
    public long[] encode(String text) {
        String phonemes = Normalizer.normalize(phonemizer.phonemize(text), Normalizer.Form.NFD);
        List<Integer> ids = new ArrayList<Integer>();
        List<Integer> pad = ids(PAD);
        ids.addAll(ids(BOS));
        ids.addAll(pad);
        int spoken = 0;
        int offset = 0;
        while (offset < phonemes.length()) {
            int codePoint = phonemes.codePointAt(offset);
            offset += Character.charCount(codePoint);
            String phoneme = new String(Character.toChars(codePoint));
            List<String> mappedPhonemes = phonemeMap.get(phoneme);
            if (mappedPhonemes == null) {
                mappedPhonemes = Collections.singletonList(phoneme);
            }
            for (String mapped : mappedPhonemes) {
                List<Integer> id = idMap.get(mapped);
                if (id == null && FALLBACK.containsKey(mapped)) {
                    id = idMap.get(FALLBACK.get(mapped));
                }
                if (id == null) {
                    continue;
                }
                ids.addAll(id);
                ids.addAll(pad);
                if (!Character.isWhitespace(codePoint)) {
                    spoken++;
                }
            }
        }
        ids.addAll(ids(EOS));
        if (spoken == 0) {
            return new long[0];
        }
        long[] result = new long[ids.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = ids.get(i);
        }
        return result;
    }

    private List<Integer> ids(String symbol) {
        List<Integer> ids = idMap.get(symbol);
        return ids == null ? Collections.<Integer>emptyList() : ids;
    }
}
