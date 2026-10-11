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
     * Beschreibt genau dieses Bild (nur auf Abruf, nie beim Laden der Seite); {@code done} bekommt das Ergebnis.
     * Ohne Beschreibungsweg: ein Hinweis statt einer Beschreibung.
     *
     * @param mediaType Medientyp von {@code bytes} (Rasterbild; SVG kommt schon gerastert als PNG)
     */
    default void describe(ReaderImage image, String mediaType, byte[] bytes, Consumer<ReaderImageDescription> done) {
        done.accept(ReaderImageDescription.unavailable("Bildbeschreibung ist hier nicht verfügbar."));
    }
}
