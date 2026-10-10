package com.aresstack.enterpriseai.app.ui.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Die eine gespeicherte Auswahl einer Modellkategorie im Dialog ({@code [<katalog>:]<modell>}, leer = keine). Die
 * Reiter „Cloud-Modelle“ und „Lokale Modelle“ zeigen sie beide; wer sie in einem Reiter setzt, wählt das Modell
 * des anderen ab. Nur auf dem EDT.
 */
final class ModelSelection {

    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private String value = "";

    String get() {
        return value;
    }

    /** Setzt die Auswahl und benachrichtigt alle Sichten, wenn sie sich ändert. */
    void set(String next) {
        String text = next == null ? "" : next.trim();
        if (text.equals(value)) {
            return;
        }
        value = text;
        for (Runnable listener : new ArrayList<Runnable>(listeners)) {
            listener.run();
        }
    }

    void onChange(Runnable listener) {
        listeners.add(listener);
    }
}
