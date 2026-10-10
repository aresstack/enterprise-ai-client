package com.aresstack.enterpriseai.application.speech;

import java.io.IOException;

/** Spielt WAV-Audio ab (Implementierung in der Composition Root, z. B. Java Sound). */
public interface AudioPlayback {

    /** Spielt {@code wav} ab und blockiert bis zum Ende oder bis {@link #stop()}. */
    void play(byte[] wav) throws IOException;

    /** Bricht die laufende Wiedergabe sofort ab; ohne Wiedergabe wirkungslos. */
    void stop();
}
