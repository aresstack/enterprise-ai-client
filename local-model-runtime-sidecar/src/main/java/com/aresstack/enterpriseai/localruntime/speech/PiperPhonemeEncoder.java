package com.aresstack.enterpriseai.localruntime.speech;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Piper's {@code phonemes_to_ids}: BOS {@code ^}, then every phoneme followed by the pad {@code _}, then EOS
 * {@code $}. Like piper-phonemize the phoneme string is decomposed to NFD first (e.g. {@code ç} becomes {@code c} plus
 * the combining cedilla), then every code point is one phoneme; {@code phoneme_map} replaces a phoneme first. Symbols the voice does
 * not know fall back to a close ASCII symbol (script g, uvular r) or are dropped.
 */
final class PiperPhonemeEncoder implements VoiceTextEncoder {

    private static final String PAD = "_";
    private static final String BOS = "^";
    private static final String EOS = "$";
    private static final Map<String, String> FALLBACK = Map.of(
            "ɡ", "g", "ʁ", "r", "ɐ", "a", "ʏ", "y", "œ", "ø");

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
        List<Integer> ids = new ArrayList<>();
        List<Integer> pad = idMap.getOrDefault(PAD, List.of());
        ids.addAll(idMap.getOrDefault(BOS, List.of()));
        ids.addAll(pad);
        int spoken = 0;
        int[] codePoints = phonemes.codePoints().toArray();
        for (int codePoint : codePoints) {
            String phoneme = new String(Character.toChars(codePoint));
            for (String mapped : phonemeMap.getOrDefault(phoneme, List.of(phoneme))) {
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
        ids.addAll(idMap.getOrDefault(EOS, List.of()));
        if (spoken == 0) {
            return new long[0];
        }
        long[] result = new long[ids.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = ids.get(i);
        }
        return result;
    }
}
