package com.aresstack.enterpriseai.astu.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ein Kapitel oder Unterabschnitt der Navigation. {@link Origin#HEADING} ist eine Originalüberschrift des Dokuments,
 * {@link Origin#TOPIC} ein automatisch erkannter Themenabschnitt mit erzeugter Bezeichnung; beide bleiben
 * unterscheidbar. Kinder stehen in Lesereihenfolge.
 */
public final class NavigationSection {

    /** Herkunft der Bezeichnung. */
    public enum Origin {
        /** Originalüberschrift; der Text stammt aus dem Dokument. */
        HEADING,
        /** Automatisch erkannter Themenabschnitt; die Bezeichnung ist erzeugt. */
        TOPIC
    }

    private final String id;
    private final String label;
    private final Origin origin;
    private final int level;
    private final SourcePosition position;
    private final String parentId;
    private final List<NavigationSection> children;

    /**
     * @param level    Tiefe: 1 = Hauptkapitel
     * @param parentId ID des übergeordneten Kapitels oder {@code null} für ein Hauptkapitel
     */
    public NavigationSection(String id, String label, Origin origin, int level, SourcePosition position,
                             String parentId, List<NavigationSection> children) {
        if (id == null || id.isEmpty() || origin == null || position == null) {
            throw new IllegalArgumentException("id, origin und position sind Pflicht");
        }
        if (level < 1) {
            throw new IllegalArgumentException("level beginnt bei 1: " + level);
        }
        this.id = id;
        this.label = label == null ? "" : label.trim();
        this.origin = origin;
        this.level = level;
        this.position = position;
        this.parentId = parentId;
        this.children = children == null ? Collections.<NavigationSection>emptyList()
                : Collections.unmodifiableList(new ArrayList<NavigationSection>(children));
    }

    public String id() {
        return id;
    }

    /** Die vollständige Bezeichnung; die Oberfläche kürzt, nie die Domain. */
    public String label() {
        return label;
    }

    public Origin origin() {
        return origin;
    }

    public boolean isGenerated() {
        return origin == Origin.TOPIC;
    }

    public int level() {
        return level;
    }

    public SourcePosition position() {
        return position;
    }

    /** Das übergeordnete Kapitel oder {@code null}. */
    public String parentId() {
        return parentId;
    }

    public List<NavigationSection> children() {
        return children;
    }

    /** Dieser Abschnitt mit anderer Bezeichnung (z. B. nach der Benennung). */
    public NavigationSection withLabel(String value) {
        return new NavigationSection(id, value, origin, level, position, parentId, children);
    }

    /** Dieser Abschnitt mit anderen Kindern. */
    public NavigationSection withChildren(List<NavigationSection> value) {
        return new NavigationSection(id, label, origin, level, position, parentId, value);
    }

    @Override
    public String toString() {
        return (origin == Origin.TOPIC ? "~" : "") + label + " " + position + (children.isEmpty() ? ""
                : " " + children);
    }
}
