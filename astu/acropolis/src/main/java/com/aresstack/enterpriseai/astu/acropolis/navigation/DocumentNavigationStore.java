package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;

/**
 * Hält die analysierte Navigation je Fassung über den Neustart hinaus: dieselbe Capture wird nie zweimal geclustert
 * oder benannt. Je Ressource gilt nur die zuletzt gespeicherte Fassung; ältere werden verworfen, nicht migriert.
 */
public interface DocumentNavigationStore {

    /** Die gespeicherte Navigation genau dieser Fassung oder {@code null}. Wirft nie. */
    DocumentNavigation load(String resourceId, String captureId);

    /** Speichert (ersetzt die Fassung davor). Fehler werden geschluckt: dann wird nach dem Neustart neu analysiert. */
    void save(DocumentNavigation navigation);
}
