package com.aresstack.enterpriseai.speech.local;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rule-based German grapheme-to-phoneme conversion into the espeak-ng IPA symbols German Piper voices were trained
 * on ({@code Guten Tag} → {@code ɡˈuːtən tˈaːk}). It replaces espeak-ng, which the sidecar must not start as an
 * external program, and is deliberately an approximation: spelling rules for vowel length, final devoicing,
 * {@code ch}/{@code sch}/{@code st}/{@code sp}, vocalised {@code r}, reduced {@code e}, stress on the first
 * syllable apart from common prefixes and suffixes, a small lexicon of frequent words, numbers spelled out and
 * short upper-case abbreviations spelled letter by letter. Loanwords and compounds can be stressed or lengthened
 * wrongly.
 */
final class GermanPhonemizer implements Phonemizer {

    private static final String STRESS = "ˈ";
    private static final String VOWELS = "aeiouäöüy";
    private static final Set<String> DIPHTHONGS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "ei", "ai", "ey", "ay", "eu", "äu", "au")));
    private static final List<String> CLUSTERS = Collections.unmodifiableList(Arrays.asList("tsch", "sch", "chs", "ch", "ck", "ph", "th", "qu", "dt",
            "tz", "ng", "pf", "ss"));
    private static final Set<String> HEAVY = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("tsch", "sch", "chs", "ch", "ck", "qu", "dt", "tz", "ng", "pf",
            "ss", "x")));
    private static final List<String> STRONG_PREFIXES = Collections.unmodifiableList(Arrays.asList("ver", "zer", "ent", "emp"));
    private static final List<String> WEAK_PREFIXES = Collections.unmodifiableList(Arrays.asList("be", "ge"));
    private static final Set<String> BACK_VOWELS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "a", "o", "u", "au")));

    private static final Map<String, String> LEXICON;

    static {
        Map<String, String> lexicon = new HashMap<String, String>();
        lexicon.put("und", "ʊnt");
        lexicon.put("ist", "ɪst");
        lexicon.put("der", "dɛɐ");
        lexicon.put("die", "diː");
        lexicon.put("das", "das");
        lexicon.put("dass", "das");
        lexicon.put("den", "deːn");
        lexicon.put("dem", "deːm");
        lexicon.put("des", "dɛs");
        lexicon.put("ein", "aɪn");
        lexicon.put("eine", "aɪnə");
        lexicon.put("einen", "aɪnən");
        lexicon.put("einem", "aɪnəm");
        lexicon.put("einer", "aɪnɐ");
        lexicon.put("nicht", "nɪçt");
        lexicon.put("ich", "ɪç");
        lexicon.put("du", "duː");
        lexicon.put("er", "eːɐ");
        lexicon.put("sie", "ziː");
        lexicon.put("es", "ɛs");
        lexicon.put("wir", "viːɐ");
        lexicon.put("ihr", "iːɐ");
        lexicon.put("mit", "mɪt");
        lexicon.put("von", "fɔn");
        lexicon.put("vom", "fɔm");
        lexicon.put("zu", "tsuː");
        lexicon.put("zum", "tsʊm");
        lexicon.put("zur", "tsuːɐ");
        lexicon.put("im", "ɪm");
        lexicon.put("in", "ɪn");
        lexicon.put("an", "an");
        lexicon.put("am", "am");
        lexicon.put("auf", "aʊf");
        lexicon.put("aus", "aʊs");
        lexicon.put("bei", "baɪ");
        lexicon.put("für", "fyːɐ");
        lexicon.put("um", "ʊm");
        lexicon.put("oder", "ˈoːdɐ");
        lexicon.put("aber", "ˈaːbɐ");
        lexicon.put("wie", "viː");
        lexicon.put("was", "vas");
        lexicon.put("wo", "voː");
        lexicon.put("so", "zoː");
        lexicon.put("auch", "aʊx");
        lexicon.put("noch", "nɔx");
        lexicon.put("nur", "nuːɐ");
        lexicon.put("hat", "hat");
        lexicon.put("habe", "hˈaːbə");
        lexicon.put("haben", "hˈaːbən");
        lexicon.put("sind", "zɪnt");
        lexicon.put("bin", "bɪn");
        lexicon.put("bist", "bɪst");
        lexicon.put("wird", "vɪʁt");
        lexicon.put("werden", "vˈeːɐdən");
        lexicon.put("kann", "kan");
        lexicon.put("man", "man");
        lexicon.put("mein", "maɪn");
        lexicon.put("dein", "daɪn");
        lexicon.put("sein", "zaɪn");
        lexicon.put("als", "als");
        lexicon.put("ob", "ɔp");
        lexicon.put("bis", "bɪs");
        lexicon.put("hier", "hˈiːɐ");
        lexicon.put("da", "daː");
        lexicon.put("ja", "jˈaː");
        lexicon.put("nein", "nˈaɪn");
        lexicon.put("mehr", "mˈeːɐ");
        lexicon.put("sehr", "zˈeːɐ");
        lexicon.put("gut", "ɡˈuːt");
        lexicon.put("heute", "hˈɔøtə");
        lexicon.put("jetzt", "jˈɛtst");
        lexicon.put("schon", "ʃˈoːn");
        lexicon.put("doch", "dɔx");
        lexicon.put("mal", "maːl");
        lexicon.put("dann", "dan");
        lexicon.put("wenn", "vɛn");
        lexicon.put("denn", "dɛn");
        lexicon.put("weil", "vaɪl");
        lexicon.put("über", "ˈyːbɐ");
        lexicon.put("unter", "ˈʊntɐ");
        lexicon.put("nach", "naːx");
        lexicon.put("vor", "foːɐ");
        lexicon.put("durch", "dʊʁç");
        lexicon.put("gegen", "ɡˈeːɡən");
        lexicon.put("ohne", "ˈoːnə");
        lexicon.put("sich", "zɪç");
        lexicon.put("uns", "ʊns");
        lexicon.put("euch", "ɔøç");
        lexicon.put("mich", "mɪç");
        lexicon.put("dich", "dɪç");
        lexicon.put("ihm", "iːm");
        lexicon.put("ihn", "iːn");
        lexicon.put("ihnen", "ˈiːnən");
        lexicon.put("uhr", "ˈuːɐ");
        lexicon.put("hallo", "hˈaloː");
        lexicon.put("welt", "vˈɛlt");
        LEXICON = Collections.unmodifiableMap(lexicon);
    }

    private static final Map<Character, String> LETTERS;

    static {
        Map<Character, String> letters = new HashMap<Character, String>();
        letters.put('a', "aː");
        letters.put('b', "beː");
        letters.put('c', "tseː");
        letters.put('d', "deː");
        letters.put('e', "eː");
        letters.put('f', "ɛf");
        letters.put('g', "ɡeː");
        letters.put('h', "haː");
        letters.put('i', "iː");
        letters.put('j', "jɔt");
        letters.put('k', "kaː");
        letters.put('l', "ɛl");
        letters.put('m', "ɛm");
        letters.put('n', "ɛn");
        letters.put('o', "oː");
        letters.put('p', "peː");
        letters.put('q', "kuː");
        letters.put('r', "ɛɐ");
        letters.put('s', "ɛs");
        letters.put('t', "teː");
        letters.put('u', "uː");
        letters.put('v', "faʊ");
        letters.put('w', "veː");
        letters.put('x', "ɪks");
        letters.put('y', "ʏpsilɔn");
        letters.put('z', "tsɛt");
        letters.put('ä', "ɛː");
        letters.put('ö', "øː");
        letters.put('ü', "yː");
        LETTERS = Collections.unmodifiableMap(letters);
    }

    @Override
    public String phonemize(String text) {
        String normalized = GermanNumbers.expand(Normalizer.normalize(text, Normalizer.Form.NFC));
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < normalized.length()) {
            char c = normalized.charAt(i);
            if (Character.isLetter(c)) {
                int end = i;
                while (end < normalized.length() && Character.isLetter(normalized.charAt(end))) {
                    end++;
                }
                String token = normalized.substring(i, end);
                separate(out);
                out.append(isAbbreviation(token) ? spellLetters(token) : word(token.toLowerCase(Locale.GERMAN)));
                i = end;
                continue;
            }
            if (".,;:!?".indexOf(c) >= 0) {
                trimTrailingSpace(out);
                if (out.length() > 0 && ".,;:!?".indexOf(out.charAt(out.length() - 1)) < 0) {
                    out.append(c);
                }
            } else if (c == '\n' && out.length() > 0) {
                trimTrailingSpace(out);
                if (".,;:!?".indexOf(out.charAt(out.length() - 1)) < 0) {
                    out.append(',');
                }
            }
            i++;
        }
        return out.toString().trim();
    }

    private static void separate(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
        }
    }

    private static void trimTrailingSpace(StringBuilder out) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
    }

    private static boolean isAbbreviation(String token) {
        return token.length() >= 2 && token.length() <= 4 && token.equals(token.toUpperCase(Locale.GERMAN))
                && !token.equals(token.toLowerCase(Locale.GERMAN));
    }

    private static String spellLetters(String token) {
        StringBuilder spelled = new StringBuilder();
        String lower = token.toLowerCase(Locale.GERMAN);
        for (int i = 0; i < lower.length(); i++) {
            String letter = LETTERS.get(lower.charAt(i));
            if (letter != null) {
                if (i == lower.length() - 1) {
                    spelled.append(STRESS);
                }
                spelled.append(letter);
            }
        }
        return spelled.toString();
    }

    static String word(String word) {
        String known = LEXICON.get(word);
        if (known != null) {
            return known;
        }
        if (word.length() > 4 && word.endsWith("tion")) {
            return convert(word.substring(0, word.length() - 4), false) + "tsjˈoːn";
        }
        if (word.length() > 6 && word.endsWith("tionen")) {
            return convert(word.substring(0, word.length() - 6), false) + "tsjˈoːnən";
        }
        return convert(word, true);
    }

    private static String convert(String word, boolean stressAllowed) {
        List<Unit> units = segment(word);
        List<Integer> nuclei = new ArrayList<Integer>();
        for (int i = 0; i < units.size(); i++) {
            if (units.get(i).vowel) {
                nuclei.add(i);
            }
        }
        int stressed = stressAllowed && !nuclei.isEmpty() ? nuclei.get(stressedNucleus(word, units, nuclei)) : -1;
        StringBuilder out = new StringBuilder();
        boolean[] consumed = new boolean[units.size()];
        for (int i = 0; i < units.size(); i++) {
            if (consumed[i]) {
                continue;
            }
            Unit unit = units.get(i);
            if (unit.vowel) {
                vowel(out, units, i, i == stressed, nuclei.size(), consumed);
            } else {
                out.append(consonant(units, i));
            }
        }
        return out.toString();
    }

    private static int stressedNucleus(String word, List<Unit> units, List<Integer> nuclei) {
        int count = nuclei.size();
        if (count < 2) {
            return 0;
        }
        if (word.endsWith("ieren") || word.endsWith("ierung") || word.endsWith("ierte") || word.endsWith("iert")) {
            for (int n = count - 1; n >= 0; n--) {
                if ("ie".equals(units.get(nuclei.get(n)).text)) {
                    return n;
                }
            }
        }
        if (word.endsWith("tät") || word.endsWith("ei")) {
            return count - 1;
        }
        for (String prefix : STRONG_PREFIXES) {
            if (word.startsWith(prefix) && word.length() > prefix.length() + 2) {
                return 1;
            }
        }
        if (word.startsWith("er") && word.length() > 5 && isConsonant(word.charAt(2)) && word.charAt(2) != 'h'
                && !word.startsWith("erst") && !word.startsWith("erd") && !word.startsWith("ernst")) {
            return 1;
        }
        for (String prefix : WEAK_PREFIXES) {
            if (word.startsWith(prefix) && word.length() > prefix.length() + 2
                    && isConsonant(word.charAt(prefix.length())) && word.charAt(prefix.length()) != 'h'
                    && (count >= 3 || !(word.endsWith("en") || word.endsWith("er") || word.endsWith("el")
                    || word.endsWith("e")))) {
                return 1;
            }
        }
        return 0;
    }

    private static void vowel(StringBuilder out, List<Unit> units, int i, boolean stressed, int nucleusCount,
                              boolean[] consumed) {
        Unit unit = units.get(i);
        Unit next = i + 1 < units.size() ? units.get(i + 1) : null;
        boolean nextIsLast = i + 2 == units.size();
        String text = unit.text;
        if (DIPHTHONGS.contains(text)) {
            String diphthong;
            switch (text) {
                case "au":
                    diphthong = "aʊ";
                    break;
                case "eu":
                case "äu":
                    diphthong = "ɔø";
                    break;
                default:
                    diphthong = "aɪ";
                    break;
            }
            out.append(stressed ? STRESS : "").append(diphthong);
            vocalisedR(out, units, i, consumed, true);
            return;
        }
        if ("ie".equals(text)) {
            out.append(stressed ? STRESS : "").append("iː");
            vocalisedR(out, units, i, consumed, true);
            return;
        }
        char v = text.charAt(0);
        if (v == 'e' && !stressed && !unit.longMark) {
            if (next != null && "r".equals(next.text) && (rBeforeConsonantOrEnd(units, i + 1) || i == 1)) {
                out.append("ɐ");
                consumed[i + 1] = true;
            } else {
                out.append("ə");
            }
            return;
        }
        if (v == 'i' && !stressed && next != null && "g".equals(next.text) && nextIsLast) {
            out.append("ɪç");
            consumed[i + 1] = true;
            return;
        }
        boolean longVowel = unit.longMark || isLong(units, i, stressed, nucleusCount);
        out.append(stressed ? STRESS : "").append(quality(v, longVowel));
        vocalisedR(out, units, i, consumed, longVowel);
    }

    /** {@code r} after a vowel before a consonant or at the end: {@code ɐ} after long vowels, else {@code ʁ}. */
    private static void vocalisedR(StringBuilder out, List<Unit> units, int i, boolean[] consumed, boolean longVowel) {
        if (i + 1 < units.size() && "r".equals(units.get(i + 1).text) && rBeforeConsonantOrEnd(units, i + 1)) {
            boolean atEnd = i + 2 == units.size();
            out.append(longVowel || atEnd ? "ɐ" : "ʁ");
            consumed[i + 1] = true;
        }
    }

    private static boolean rBeforeConsonantOrEnd(List<Unit> units, int r) {
        return r + 1 >= units.size() || !units.get(r + 1).vowel;
    }

    private static boolean isLong(List<Unit> units, int i, boolean stressed, int nucleusCount) {
        List<Unit> cluster = new ArrayList<Unit>();
        boolean vowelFollows = false;
        for (int k = i + 1; k < units.size(); k++) {
            if (units.get(k).vowel) {
                vowelFollows = true;
                break;
            }
            cluster.add(units.get(k));
        }
        char v = units.get(i).text.charAt(0);
        if (cluster.isEmpty()) {
            return vowelFollows || stressed || v != 'a';
        }
        Unit first = cluster.get(0);
        if ("ß".equals(first.text)) {
            return true;
        }
        if (cluster.size() > 1 || HEAVY.contains(first.text) || first.doubled) {
            return false;
        }
        if (vowelFollows) {
            return true;
        }
        if (nucleusCount == 1) {
            return "bdglr".contains(first.text);
        }
        return false;
    }

    private static String quality(char v, boolean longVowel) {
        switch (v) {
            case 'a':
                return longVowel ? "aː" : "a";
            case 'e':
                return longVowel ? "eː" : "ɛ";
            case 'i':
                return longVowel ? "iː" : "ɪ";
            case 'o':
                return longVowel ? "oː" : "ɔ";
            case 'u':
                return longVowel ? "uː" : "ʊ";
            case 'ä':
                return longVowel ? "ɛː" : "ɛ";
            case 'ö':
                return longVowel ? "øː" : "œ";
            default:
                return longVowel ? "yː" : "ʏ";
        }
    }

    private static String consonant(List<Unit> units, int i) {
        Unit unit = units.get(i);
        Unit previous = i > 0 ? units.get(i - 1) : null;
        Unit next = i + 1 < units.size() ? units.get(i + 1) : null;
        String text = unit.doubled ? unit.text.substring(0, 1) : unit.text;
        switch (text) {
            case "tsch":
                return "tʃ";
            case "sch":
                return "ʃ";
            case "chs":
                return "ks";
            case "ch":
                if (previous == null) {
                    return next != null && "raol".contains(next.text.substring(0, 1)) ? "k" : "ç";
                }
                Unit vowelBefore = lastVowelBefore(units, i);
                return vowelBefore != null && previous.vowel && BACK_VOWELS.contains(vowelBefore.text)
                        ? "x" : "ç";
            case "ck":
                return "k";
            case "ph":
                return "f";
            case "th":
            case "dt":
                return "t";
            case "qu":
                return "kv";
            case "tz":
            case "z":
                return "ts";
            case "ng":
                return "ŋ";
            case "pf":
                return "pf";
            case "ss":
            case "ß":
                return "s";
            case "b":
                return devoiced(next) ? "p" : "b";
            case "d":
                return devoiced(next) ? "t" : "d";
            case "g":
                return devoiced(next) ? "k" : "ɡ";
            case "s":
                if (previous == null && next != null && ("p".equals(next.text) || "t".equals(next.text))) {
                    return "ʃ";
                }
                if (next != null && next.vowel && (previous == null || previous.vowel
                        || "lmnr".contains(previous.text))) {
                    return "z";
                }
                return "s";
            case "n":
                return next != null && next.text.startsWith("k") ? "ŋ" : "n";
            case "v":
                return "f";
            case "w":
                return "v";
            case "x":
                return "ks";
            case "c":
                return next != null && next.vowel && "eiäy".indexOf(next.text.charAt(0)) >= 0 ? "ts" : "k";
            case "r":
                return "ʁ";
            case "q":
                return "k";
            default:
                return text;
        }
    }

    private static Unit lastVowelBefore(List<Unit> units, int i) {
        for (int k = i - 1; k >= 0; k--) {
            if (units.get(k).vowel) {
                return units.get(k);
            }
        }
        return null;
    }

    private static boolean devoiced(Unit next) {
        return next == null || (!next.vowel && !"l".equals(next.text) && !"r".equals(next.text));
    }

    static List<Unit> segment(String word) {
        List<Unit> units = new ArrayList<Unit>();
        int i = 0;
        int n = word.length();
        while (i < n) {
            char c = word.charAt(i);
            if (VOWELS.indexOf(c) >= 0) {
                String two = i + 1 < n ? word.substring(i, i + 2) : "";
                Unit unit;
                if (DIPHTHONGS.contains(two)) {
                    unit = new Unit(two, true, true, false);
                    i += 2;
                } else if ("ie".equals(two)) {
                    unit = new Unit(two, true, true, false);
                    i += 2;
                } else if (two.length() == 2 && two.charAt(1) == c && "aeo".indexOf(c) >= 0) {
                    unit = new Unit(String.valueOf(c), true, true, false);
                    i += 2;
                } else {
                    unit = new Unit(String.valueOf(c), true, false, false);
                    i += 1;
                }
                // Dehnungs-h: silent and lengthening before a consonant, at the end and before an "e".
                if (i < n && word.charAt(i) == 'h' && (i + 1 == n || !isVowel(word.charAt(i + 1))
                        || word.charAt(i + 1) == 'e')) {
                    unit = new Unit(unit.text, true, true, false);
                    i += 1;
                }
                units.add(unit);
                continue;
            }
            String cluster = null;
            for (String candidate : CLUSTERS) {
                if (word.startsWith(candidate, i)) {
                    cluster = candidate;
                    break;
                }
            }
            if (cluster != null) {
                units.add(new Unit(cluster, false, false, false));
                i += cluster.length();
            } else if (i + 1 < n && word.charAt(i + 1) == c && Character.isLetter(c)) {
                units.add(new Unit(word.substring(i, i + 2), false, false, true));
                i += 2;
            } else {
                units.add(new Unit(String.valueOf(c), false, false, false));
                i += 1;
            }
        }
        return units;
    }

    private static boolean isVowel(char c) {
        return VOWELS.indexOf(c) >= 0;
    }

    private static boolean isConsonant(char c) {
        return Character.isLetter(c) && !isVowel(c);
    }

    /** One grapheme unit: a vowel nucleus (possibly a diphthong or lengthened) or a consonant cluster. */
    static final class Unit {
        final String text;
        final boolean vowel;
        final boolean longMark;
        final boolean doubled;

        Unit(String text, boolean vowel, boolean longMark, boolean doubled) {
            this.text = text;
            this.vowel = vowel;
            this.longMark = longMark;
            this.doubled = doubled;
        }

        String text() {
            return text;
        }

        boolean vowel() {
            return vowel;
        }

        boolean longMark() {
            return longMark;
        }

        boolean doubled() {
            return doubled;
        }
    }
}
