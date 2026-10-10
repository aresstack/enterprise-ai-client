package com.aresstack.enterpriseai.localruntime.speech;

/**
 * VITS sampling parameters of a voice ({@code inference} block of a Piper voice) and the speaker of a
 * multi-speaker voice ({@code -1} = none).
 */
public record VoiceParameters(float noiseScale, float lengthScale, float noiseWidth, long speakerId) {

    /** Piper's defaults. */
    public static final VoiceParameters DEFAULT = new VoiceParameters(0.667f, 1.0f, 0.8f, -1L);
}
