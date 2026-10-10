package com.aresstack.enterpriseai.speech.local;

/** Text zur Phonemfolge (ein Symbol je Code Point, Wörter durch Leerzeichen getrennt), auf die eine Piper-Stimme trainiert ist. */
interface Phonemizer {

    String phonemize(String text);
}
