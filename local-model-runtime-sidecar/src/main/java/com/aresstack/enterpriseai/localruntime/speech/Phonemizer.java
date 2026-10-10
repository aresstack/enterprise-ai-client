package com.aresstack.enterpriseai.localruntime.speech;

/** Text to the phoneme string (one symbol per code point, words separated by spaces) a Piper voice was trained on. */
interface Phonemizer {

    String phonemize(String text);
}
