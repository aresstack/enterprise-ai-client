package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import java.awt.Color;

/**
 * Eine Reitergruppe im Lesebereich wie die Tab-Gruppen in Firefox: Farbe und optionaler Name; die Reiter einer Gruppe
 * stehen nebeneinander, die Gruppe lässt sich einklappen. Neue Funktion des Enterprise-Clients (weder MainframeMate
 * noch corenth oder askai haben Reitergruppen); sie ist reine Tab-Metadaten und lebt so lange wie ihre Reiter.
 */
public final class ReaderTabGroup {

    /** Die neun Gruppenfarben von Firefox. */
    public enum Tint {
        BLUE("Blau", new Color(0x1676D2)),
        PURPLE("Lila", new Color(0x8E44AD)),
        CYAN("Türkis", new Color(0x15A0B4)),
        ORANGE("Orange", new Color(0xF57C00)),
        YELLOW("Gelb", new Color(0xE0B000)),
        PINK("Rosa", new Color(0xD6457A)),
        GREEN("Grün", new Color(0x2E9D4F)),
        GRAY("Grau", new Color(0x7A7A7A)),
        RED("Rot", new Color(0xC94C4C));

        private final String label;
        private final Color color;

        Tint(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public Color color() {
            return color;
        }
    }

    private final String id;
    private String name;
    private Tint tint;
    private boolean collapsed;

    ReaderTabGroup(String id, String name, Tint tint) {
        if (id == null || tint == null) {
            throw new IllegalArgumentException("id and tint must not be null");
        }
        this.id = id;
        this.name = name == null ? "" : name.trim();
        this.tint = tint;
    }

    public String id() {
        return id;
    }

    /** Der Gruppenname oder leer. */
    public String name() {
        return name;
    }

    public Tint tint() {
        return tint;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    void rename(String name) {
        this.name = name == null ? "" : name.trim();
    }

    void recolor(Tint tint) {
        if (tint != null) {
            this.tint = tint;
        }
    }

    void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }
}
