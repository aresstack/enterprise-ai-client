package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;

import java.util.List;

/**
 * Ergebnis von „Installiert“ und „Vorschläge“ der lokalen Modelle; genau ein Aufruf, auf dem EDT. Eine leere Liste
 * heißt „wirklich nichts angeboten“; ließen sich die Angebote nicht ermitteln, kommt {@link #failed} mit dem Grund.
 */
public interface LocalVoiceOffersCallback {

    /** Die angebotenen Modelle mit Installationsstand (installierte und Vorschläge); leer, wenn es keine gibt. */
    void offers(List<LocalVoiceOffer> offers);

    /** Die Angebote ließen sich nicht ermitteln; {@code message} nennt den Grund verständlich. */
    void failed(String message);
}
