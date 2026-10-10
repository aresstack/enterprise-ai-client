package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;

import java.util.List;

/** Ergebnis einer Hub-Suche nach lokalen Stimmen; genau ein Aufruf, auf dem EDT. */
public interface LocalVoiceSearchCallback {

    /** Die vom Sidecar ladbaren Treffer mit Installationsstand; leer, wenn nichts passt. */
    void found(List<LocalVoiceOffer> offers);

    /** Die Suche ist gescheitert; {@code message} nennt den Grund. */
    void failed(String message);
}
