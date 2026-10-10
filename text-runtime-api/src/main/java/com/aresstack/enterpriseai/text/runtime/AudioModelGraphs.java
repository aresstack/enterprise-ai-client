package com.aresstack.enterpriseai.text.runtime;

import java.nio.file.Path;

/** Die drei Graphen eines Sprachmodells mit Audio-Eingang: Audio-Encoder, Token-Einbettung, Decoder. */
public final class AudioModelGraphs {

    private final Path audioEncoder;
    private final Path embedTokens;
    private final Path decoder;

    public AudioModelGraphs(Path audioEncoder, Path embedTokens, Path decoder) {
        if (audioEncoder == null || embedTokens == null || decoder == null) {
            throw new IllegalArgumentException("all graphs are required");
        }
        this.audioEncoder = audioEncoder;
        this.embedTokens = embedTokens;
        this.decoder = decoder;
    }

    public Path audioEncoder() {
        return audioEncoder;
    }

    public Path embedTokens() {
        return embedTokens;
    }

    public Path decoder() {
        return decoder;
    }
}
