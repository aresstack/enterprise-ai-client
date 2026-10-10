package com.aresstack.enterpriseai.speech.local;

/** VITS-Parameter einer Stimme ({@code inference} einer Piper-Stimme) und Sprecher ({@code -1} = keiner). */
final class VoiceParameters {

    /** @return die Piper-Standardwerte */
    static VoiceParameters defaults() {
        return new VoiceParameters(0.667f, 1.0f, 0.8f, -1L);
    }

    final float noiseScale;
    final float lengthScale;
    final float noiseWidth;
    final long speakerId;

    VoiceParameters(float noiseScale, float lengthScale, float noiseWidth, long speakerId) {
        this.noiseScale = noiseScale;
        this.lengthScale = lengthScale;
        this.noiseWidth = noiseWidth;
        this.speakerId = speakerId;
    }
}
