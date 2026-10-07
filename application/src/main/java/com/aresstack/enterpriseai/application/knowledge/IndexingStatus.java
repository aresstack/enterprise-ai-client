package com.aresstack.enterpriseai.application.knowledge;

/** Ergebnis der Indexierung einer Ressource. */
public enum IndexingStatus {

    /** Geladen, gechunkt, vektorisiert; die Chunks ersetzen die bisherigen der Ressource im Namespace. */
    INDEXED,

    /** Geladen, aber ohne indexierbaren Text; bisherige Chunks der Ressource wurden entfernt. */
    EMPTY,

    /** Die Quelle meldet die Ressource als nicht (mehr) vorhanden; ihre Chunks wurden aus dem Index entfernt. */
    REMOVED,

    /** Die Ressource ist ein Ziel, das in diesem Lauf schon indexiert wurde (z. B. zwei Weiterleitungen). */
    DUPLICATE,

    /** Ein Schritt ist gescheitert; der bisherige Indexstand der Ressource bleibt unverändert. */
    FAILED
}
