package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;

import java.util.List;

/** Ergebnis einer Hub-Suche nach lokalen Stimmen; genau ein Aufruf, auf dem EDT. */
public interface LocalVoiceSearchCallback {

    /** Die im Client ladbaren Treffer mit Installationsstand; leer, wenn nichts passt. */
    void found(List<LocalVoiceOffer> offers);

    /** Die Suche ist gescheitert; {@code message} nennt den Grund. */
    void failed(String message);
}
