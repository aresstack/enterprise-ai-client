package com.aresstack.enterpriseai.speech.runtime;

import java.nio.file.Path;

/**
 * Eine Äußerung für {@link LocalSpeechRuntime}: Graph-Datei der Stimme, Token-Ids und die VITS-Parameter
 * (Rauschen, Länge, Rauschbreite) sowie der Sprecher einer Mehrsprecher-Stimme ({@code -1} = keiner).
 */
public final class SpeechGraphRequest {

    private final Path modelFile;
    private final long[] tokenIds;
    private final float noiseScale;
    private final float lengthScale;
    private final float noiseWidth;
    private final long speakerId;

    public SpeechGraphRequest(Path modelFile, long[] tokenIds, float noiseScale, float lengthScale, float noiseWidth,
                              long speakerId) {
        if (modelFile == null || tokenIds == null || tokenIds.length == 0) {
            throw new IllegalArgumentException("modelFile and tokenIds must not be empty");
        }
        this.modelFile = modelFile;
        this.tokenIds = tokenIds.clone();
        this.noiseScale = noiseScale;
        this.lengthScale = lengthScale;
        this.noiseWidth = noiseWidth;
        this.speakerId = speakerId;
    }

    public Path modelFile() {
        return modelFile;
    }

    public long[] tokenIds() {
        return tokenIds.clone();
    }

    public float noiseScale() {
        return noiseScale;
    }

    public float lengthScale() {
        return lengthScale;
    }

    public float noiseWidth() {
        return noiseWidth;
    }

    public long speakerId() {
        return speakerId;
    }
}
