package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

/**
 * Der Stern an einem Verweis (Kopf des Lesebereichs, jede Zeile der Ressourcenliste und der Trefferliste): fragt, ob
 * der Verweis schon ein Favorit ist, und schaltet ihn um. Aufrufe auf dem EDT.
 */
public interface FavoriteMarker {

    /** Ob ★ angeboten wird (nur mit einer Ablage für Favoriten). */
    default boolean canFavorite() {
        return false;
    }

    /** Ob der Verweis schon ein Favorit ist (für den Zustand von ★). */
    default boolean isFavorite(ReaderLink link) {
        return false;
    }

    /** ★ geklickt: den Verweis in den gewählten Favoritenordner übernehmen bzw. aus den Favoriten nehmen. */
    default void favoriteToggled(ReaderLink link) {
    }
}
