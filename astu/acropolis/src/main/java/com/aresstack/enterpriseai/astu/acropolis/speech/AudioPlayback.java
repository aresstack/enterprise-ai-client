package com.aresstack.enterpriseai.astu.acropolis.speech;

import java.io.IOException;

/** Spielt WAV-Audio ab (Implementierung in der Composition Root, z. B. Java Sound). */
public interface AudioPlayback {

    /**
     * Beginnt einen Vorlesevorgang: ein {@link #stop()} davor gilt nicht mehr. Ein {@link #stop()} danach bleibt bis
     * zum nächsten {@code begin()} wirksam, auch für ein {@link #play}, das erst nach dem Stopp beginnt.
     */
    default void begin() {
    }

    /**
     * Spielt {@code wav} ab und blockiert, bis es an das Ausgabegerät übergeben ist oder {@link #stop()} kommt. Mehrere
     * Stücke hintereinander dürfen nahtlos über dieselbe Ausgabe laufen; {@link #finish()} beendet die Folge.
     */
    void play(byte[] wav) throws IOException;

    /** Wartet, bis die Folge von {@link #play} ausgeklungen ist, und gibt das Ausgabegerät frei. */
    void finish();

    /** Bricht die laufende Wiedergabe sofort ab; ohne Wiedergabe wirkungslos. */
    void stop();
}
