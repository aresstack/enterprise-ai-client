package com.aresstack.enterpriseai.localruntime.speech;

import java.text.Normalizer;
import java.util.ArrayList;
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
    private static final Set<String> DIPHTHONGS = Set.of("ei", "ai", "ey", "ay", "eu", "äu", "au");
    private static final List<String> CLUSTERS = List.of("tsch", "sch", "chs", "ch", "ck", "ph", "th", "qu", "dt",
            "tz", "ng", "pf", "ss");
    private static final Set<String> HEAVY = Set.of("tsch", "sch", "chs", "ch", "ck", "qu", "dt", "tz", "ng", "pf",
            "ss", "x");
    private static final List<String> STRONG_PREFIXES = List.of("ver", "zer", "ent", "emp");
    private static final List<String> WEAK_PREFIXES = List.of("be", "ge");

    private static final Map<String, String> LEXICON = Map.ofEntries(
            Map.entry("und", "ʊnt"), Map.entry("ist", "ɪst"), Map.entry("der", "dɛɐ"), Map.entry("die", "diː"),
            Map.entry("das", "das"), Map.entry("dass", "das"), Map.entry("den", "deːn"), Map.entry("dem", "deːm"),
            Map.entry("des", "dɛs"), Map.entry("ein", "aɪn"), Map.entry("eine", "aɪnə"), Map.entry("einen", "aɪnən"),
            Map.entry("einem", "aɪnəm"), Map.entry("einer", "aɪnɐ"), Map.entry("nicht", "nɪçt"),
            Map.entry("ich", "ɪç"), Map.entry("du", "duː"), Map.entry("er", "eːɐ"), Map.entry("sie", "ziː"),
            Map.entry("es", "ɛs"), Map.entry("wir", "viːɐ"), Map.entry("ihr", "iːɐ"), Map.entry("mit", "mɪt"),
            Map.entry("von", "fɔn"), Map.entry("vom", "fɔm"), Map.entry("zu", "tsuː"), Map.entry("zum", "tsʊm"),
            Map.entry("zur", "tsuːɐ"), Map.entry("im", "ɪm"), Map.entry("in", "ɪn"), Map.entry("an", "an"),
            Map.entry("am", "am"), Map.entry("auf", "aʊf"), Map.entry("aus", "aʊs"), Map.entry("bei", "baɪ"),
            Map.entry("für", "fyːɐ"), Map.entry("um", "ʊm"), Map.entry("oder", "ˈoːdɐ"), Map.entry("aber", "ˈaːbɐ"),
            Map.entry("wie", "viː"), Map.entry("was", "vas"), Map.entry("wo", "voː"), Map.entry("so", "zoː"),
            Map.entry("auch", "aʊx"), Map.entry("noch", "nɔx"), Map.entry("nur", "nuːɐ"), Map.entry("hat", "hat"),
            Map.entry("habe", "hˈaːbə"), Map.entry("haben", "hˈaːbən"), Map.entry("sind", "zɪnt"),
            Map.entry("bin", "bɪn"), Map.entry("bist", "bɪst"), Map.entry("wird", "vɪʁt"),
            Map.entry("werden", "vˈeːɐdən"), Map.entry("kann", "kan"), Map.entry("man", "man"),
            Map.entry("mein", "maɪn"), Map.entry("dein", "daɪn"), Map.entry("sein", "zaɪn"), Map.entry("als", "als"),
            Map.entry("ob", "ɔp"), Map.entry("bis", "bɪs"), Map.entry("hier", "hˈiːɐ"), Map.entry("da", "daː"),
            Map.entry("ja", "jˈaː"), Map.entry("nein", "nˈaɪn"), Map.entry("mehr", "mˈeːɐ"),
            Map.entry("sehr", "zˈeːɐ"), Map.entry("gut", "ɡˈuːt"), Map.entry("heute", "hˈɔøtə"),
            Map.entry("jetzt", "jˈɛtst"), Map.entry("schon", "ʃˈoːn"), Map.entry("doch", "dɔx"),
            Map.entry("mal", "maːl"), Map.entry("dann", "dan"), Map.entry("wenn", "vɛn"), Map.entry("denn", "dɛn"),
            Map.entry("weil", "vaɪl"), Map.entry("über", "ˈyːbɐ"), Map.entry("unter", "ˈʊntɐ"),
            Map.entry("nach", "naːx"), Map.entry("vor", "foːɐ"), Map.entry("durch", "dʊʁç"),
            Map.entry("gegen", "ɡˈeːɡən"), Map.entry("ohne", "ˈoːnə"), Map.entry("sich", "zɪç"),
            Map.entry("uns", "ʊns"), Map.entry("euch", "ɔøç"), Map.entry("mich", "mɪç"), Map.entry("dich", "dɪç"),
            Map.entry("ihm", "iːm"), Map.entry("ihn", "iːn"), Map.entry("ihnen", "ˈiːnən"),
            Map.entry("uhr", "ˈuːɐ"), Map.entry("hallo", "hˈaloː"), Map.entry("welt", "vˈɛlt"));

    private static final Map<Character, String> LETTERS = Map.ofEntries(
            Map.entry('a', "aː"), Map.entry('b', "beː"), Map.entry('c', "tseː"), Map.entry('d', "deː"),
            Map.entry('e', "eː"), Map.entry('f', "ɛf"), Map.entry('g', "ɡeː"), Map.entry('h', "haː"),
            Map.entry('i', "iː"), Map.entry('j', "jɔt"), Map.entry('k', "kaː"), Map.entry('l', "ɛl"),
            Map.entry('m', "ɛm"), Map.entry('n', "ɛn"), Map.entry('o', "oː"), Map.entry('p', "peː"),
            Map.entry('q', "kuː"), Map.entry('r', "ɛɐ"), Map.entry('s', "ɛs"), Map.entry('t', "teː"),
            Map.entry('u', "uː"), Map.entry('v', "faʊ"), Map.entry('w', "veː"), Map.entry('x', "ɪks"),
            Map.entry('y', "ʏpsilɔn"), Map.entry('z', "tsɛt"), Map.entry('ä', "ɛː"), Map.entry('ö', "øː"),
            Map.entry('ü', "yː"));

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
        List<Integer> nuclei = new ArrayList<>();
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
            out.append(stressed ? STRESS : "").append(switch (text) {
                case "au" -> "aʊ";
                case "eu", "äu" -> "ɔø";
                default -> "aɪ";
            });
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
        List<Unit> cluster = new ArrayList<>();
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
        return switch (v) {
            case 'a' -> longVowel ? "aː" : "a";
            case 'e' -> longVowel ? "eː" : "ɛ";
            case 'i' -> longVowel ? "iː" : "ɪ";
            case 'o' -> longVowel ? "oː" : "ɔ";
            case 'u' -> longVowel ? "uː" : "ʊ";
            case 'ä' -> longVowel ? "ɛː" : "ɛ";
            case 'ö' -> longVowel ? "øː" : "œ";
            default -> longVowel ? "yː" : "ʏ";
        };
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
                return vowelBefore != null && previous.vowel && Set.of("a", "o", "u", "au").contains(vowelBefore.text)
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
        List<Unit> units = new ArrayList<>();
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
    record Unit(String text, boolean vowel, boolean longMark, boolean doubled) {
    }
}
