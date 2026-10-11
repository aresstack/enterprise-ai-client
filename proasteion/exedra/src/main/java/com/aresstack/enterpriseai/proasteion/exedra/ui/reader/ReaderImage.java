package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

/**
 * Ein Bild einer gelesenen Seite, wie es im Original-HTML steht: {@code src} ist die aufgelöste Adresse aus dem
 * {@code <img>} (daran erkennt die Ansicht das Bild wieder), {@code key} die Kennung, unter der der Controller die
 * Bytes und die Beschreibung holt (für die Oberfläche undurchsichtig).
 */
public final class ReaderImage {

    private final String key;
    private final String src;
    private final String title;

    public ReaderImage(String key, String src, String title) {
        if (key == null || key.isEmpty() || src == null || src.isEmpty()) {
            throw new IllegalArgumentException("key and src must not be empty");
        }
        this.key = key;
        this.src = src;
        this.title = title == null ? "" : title;
    }

    public String key() {
        return key;
    }

    public String src() {
        return src;
    }

    /** Titel bzw. Alternativtext; leer, wenn die Seite keinen nennt. */
    public String title() {
        return title;
    }

    /** Für Infozeile und Overlay-Titel: der Titel, sonst „Bild“. */
    public String label() {
        return title.isEmpty() ? "Bild" : title;
    }
}
