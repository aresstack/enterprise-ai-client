package com.aresstack.enterpriseai.speech.opennlp;

import com.aresstack.enterpriseai.speech.api.WordClass;

/**
 * Map the tags of OpenNLP part-of-speech models to the neutral {@link WordClass}: STTS (German models), Penn Treebank
 * (English models) and Universal Dependencies. Linguistics only; what a word class means for a request is decided
 * elsewhere.
 */
final class PosTags {

    private PosTags() {
    }

    static WordClass wordClass(String tag) {
        if (tag == null || tag.isEmpty()) {
            return WordClass.OTHER;
        }
        if (tag.startsWith("$")) {
            return WordClass.PUNCTUATION; // STTS $. $, $(
        }
        switch (tag) {
            // STTS
            case "VVFIN":
            case "VAFIN":
            case "VMFIN":
                return WordClass.VERB_FINITE;
            case "VVIMP":
            case "VAIMP":
                return WordClass.VERB_IMPERATIVE;
            case "VVINF":
            case "VVIZU":
            case "VVPP":
            case "VAINF":
            case "VAPP":
            case "VMINF":
            case "VMPP":
                return WordClass.VERB_NONFINITE;
            case "NN":
                return WordClass.NOUN;
            case "NE":
                return WordClass.PROPER_NOUN;
            case "PPER":
            case "PRF":
                return WordClass.PERSONAL_PRONOUN;
            case "PWS":
            case "PWAT":
            case "PWAV":
                return WordClass.INTERROGATIVE;
            case "ITJ":
            case "PTKANT":
                return WordClass.INTERJECTION;
            case "FM":
                return WordClass.FOREIGN;
            case "CARD":
                return WordClass.CARDINAL;
            // Penn Treebank
            case "VBZ":
            case "VBP":
            case "VBD":
            case "MD":
                return WordClass.VERB_FINITE;
            case "VB":
            case "VBG":
            case "VBN":
                return WordClass.VERB_NONFINITE;
            case "NNS":
                return WordClass.NOUN;
            case "NNP":
            case "NNPS":
                return WordClass.PROPER_NOUN;
            case "PRP":
                return WordClass.PERSONAL_PRONOUN;
            case "WP":
            case "WP$":
            case "WDT":
            case "WRB":
                return WordClass.INTERROGATIVE;
            case "UH":
                return WordClass.INTERJECTION;
            case "FW":
                return WordClass.FOREIGN;
            case "CD":
                return WordClass.CARDINAL;
            case ".":
            case ",":
            case ":":
            case "``":
            case "''":
            case "-LRB-":
            case "-RRB-":
                return WordClass.PUNCTUATION;
            // Universal Dependencies
            case "VERB":
            case "AUX":
                return WordClass.VERB_FINITE;
            case "NOUN":
                return WordClass.NOUN;
            case "PROPN":
                return WordClass.PROPER_NOUN;
            case "PRON":
                return WordClass.PERSONAL_PRONOUN;
            case "INTJ":
                return WordClass.INTERJECTION;
            case "NUM":
                return WordClass.CARDINAL;
            case "PUNCT":
                return WordClass.PUNCTUATION;
            default:
                return WordClass.OTHER;
        }
    }
}
