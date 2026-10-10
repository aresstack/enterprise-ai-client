package com.aresstack.enterpriseai.speech.local;

/** Text zu den Token-Ids, die der Graph einer Stimme erwartet; eine Implementierung je Stimmenformat. */
interface VoiceTextEncoder {

    long[] encode(String text);
}
