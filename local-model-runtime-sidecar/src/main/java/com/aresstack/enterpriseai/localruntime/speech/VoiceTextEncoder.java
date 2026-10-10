package com.aresstack.enterpriseai.localruntime.speech;

/** Text to the token ids a voice's graph takes as input; one implementation per voice format. */
public interface VoiceTextEncoder {

    long[] encode(String text);
}
