package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import java.util.function.Consumer;

/**
 * Holt für den Lesebereich die Bytes eines Bildes (und auf Wunsch seine Beschreibung) aus der Quelle. Die
 * Rückrufe kommen auf dem EDT; blockierende Arbeit erledigt der Controller abseits davon.
 */
public interface ReaderImageLoader {

    /** Lädt die Bytes; {@code done} bekommt {@code null}, wenn das Bild nicht ladbar ist. */
    void load(ReaderImage image, Consumer<byte[]> done);

    /**
     * Wie {@link #load(ReaderImage, Consumer)}, meldet einen Fehlschlag aber mit Grund an {@code failed} (z. B.
     * „Bild: erwartet Bild, erhalten application/json …“), damit die Oberfläche ihn statt eines allgemeinen Textes
     * zeigt; {@code null} heißt: Grund unbekannt.
     */
    default void load(ReaderImage image, Consumer<byte[]> done, Consumer<String> failed) {
        load(image, bytes -> {
            if (bytes == null) {
                failed.accept(null);
            } else {
                done.accept(bytes);
            }
        });
    }

    /**
     * Wie {@link #load(ReaderImage, Consumer, Consumer)}; verlangt die Quelle einen Einmalcode, bekommt {@code signIn}
     * die Abfrage (danach lädt der Aufrufer neu).
     */
    default void load(ReaderImage image, Consumer<byte[]> done, Consumer<String> failed,
                      Consumer<ReaderSignIn> signIn) {
        load(image, done, failed);
    }

    /**
     * Beschreibt genau dieses Bild (nur auf Abruf, nie beim Laden der Seite); {@code done} bekommt das Ergebnis.
     * Ohne Beschreibungsweg: ein Hinweis statt einer Beschreibung.
     *
     * @param mediaType Medientyp von {@code bytes} (Rasterbild; SVG kommt schon gerastert als PNG)
     */
    default void describe(ReaderImage image, String mediaType, byte[] bytes, Consumer<ReaderImageDescription> done) {
        done.accept(ReaderImageDescription.unavailable("Bildbeschreibung ist hier nicht verfügbar."));
    }
}
