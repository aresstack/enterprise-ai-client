package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Eine Wissensquelle, wie der Drawer-Reiter „Wissensquellen“ sie zeigt: ID, Typ und Umfang, das Häkchen, der
 * Indexstand als Text und was sich gerade mit ihr tun lässt. Unveränderlich; die Anbindung baut die Liste neu,
 * sobald sich etwas ändert.
 */
public final class KnowledgeSourceItem {

    /** Wie die Statuszeile der Quelle gefärbt wird. */
    public enum State {
        /** Neutral: Indexstand, „noch nicht indexiert“. */
        IDLE,
        /** Eine Indexierung dieser Quelle läuft. */
        RUNNING,
        /** Etwas braucht Aufmerksamkeit: fehlerhafte Einstellungen, gescheiterte Indexierung. */
        PROBLEM
    }

    private final String id;
    private final String kind;
    private final String scope;
    private final boolean enabled;
    private final String status;
    private final State state;
    private final boolean indexable;
    private final boolean editable;
    private final boolean removable;
    private String title = "";
    private String partitionsLabel = "";
    private boolean partitionsExpanded;
    private List<KnowledgeSourcePartitionItem> partitions;
    private String partitionsProblem = "";

    /**
     * @param kind      Typ für die Anzeige, z. B. „MediaWiki“
     * @param scope     Umfang, z. B. die Startpunkte
     * @param status    die Statuszeile (Indexstand, Lauf, Problem)
     * @param indexable ob „Jetzt indexieren“ gerade geht
     * @param editable  ob Bearbeiten geht (ohne Konfigurationsdatei nicht)
     */
    public KnowledgeSourceItem(String id, String kind, String scope, boolean enabled, String status, State state,
                               boolean indexable, boolean editable) {
        this(id, kind, scope, enabled, status, state, indexable, editable, editable);
    }

    /** @param removable ob Entfernen gerade geht (ohne Datei nicht, nicht während die Quelle indexiert wird) */
    public KnowledgeSourceItem(String id, String kind, String scope, boolean enabled, String status, State state,
                               boolean indexable, boolean editable, boolean removable) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        this.id = id.trim();
        this.kind = kind == null ? "" : kind.trim();
        this.scope = scope == null ? "" : scope.trim();
        this.enabled = enabled;
        this.status = status == null ? "" : status.trim();
        this.state = state == null ? State.IDLE : state;
        this.indexable = indexable;
        this.editable = editable;
        this.removable = removable;
    }

    public String id() {
        return id;
    }

    /** Was die Zeile groß zeigt: der Titel aus den Einstellungen, ohne ihn die ID. */
    public String title() {
        return title.isEmpty() ? id : title;
    }

    /** Dieselbe Quelle mit Titel ({@code null} oder leer: die ID). */
    public KnowledgeSourceItem withTitle(String value) {
        KnowledgeSourceItem copy = copy();
        copy.title = value == null ? "" : value.trim();
        return copy;
    }

    /**
     * Dieselbe Quelle mit aufklappbaren Bereichen (Confluence: Spaces).
     *
     * @param label    wie die Bereiche heißen, z. B. „Spaces“; leer = nichts Aufklappbares
     * @param expanded ob die Liste offen ist
     * @param items    die Bereiche; {@code null}, solange sie geladen werden
     * @param problem  warum sie nicht geladen werden konnten; leer, wenn nichts schiefging
     */
    public KnowledgeSourceItem withPartitions(String label, boolean expanded, List<KnowledgeSourcePartitionItem> items,
                                              String problem) {
        KnowledgeSourceItem copy = copy();
        copy.partitionsLabel = label == null ? "" : label.trim();
        copy.partitionsExpanded = expanded;
        copy.partitions = items == null ? null
                : Collections.unmodifiableList(new ArrayList<KnowledgeSourcePartitionItem>(items));
        copy.partitionsProblem = problem == null ? "" : problem.trim();
        return copy;
    }

    public String partitionsLabel() {
        return partitionsLabel;
    }

    public boolean partitionsExpanded() {
        return partitionsExpanded;
    }

    /** Die Bereiche oder {@code null}, solange sie (noch) nicht geladen sind. */
    public List<KnowledgeSourcePartitionItem> partitions() {
        return partitions;
    }

    public String partitionsProblem() {
        return partitionsProblem;
    }

    private KnowledgeSourceItem copy() {
        KnowledgeSourceItem copy = new KnowledgeSourceItem(id, kind, scope, enabled, status, state, indexable,
                editable, removable);
        copy.title = title;
        copy.partitionsLabel = partitionsLabel;
        copy.partitionsExpanded = partitionsExpanded;
        copy.partitions = partitions;
        copy.partitionsProblem = partitionsProblem;
        return copy;
    }

    public String kind() {
        return kind;
    }

    public String scope() {
        return scope;
    }

    /** Typ und Umfang in einer Zeile, z. B. „MediaWiki · Hauptseite, Handbuch“. */
    public String description() {
        if (scope.isEmpty()) {
            return kind;
        }
        return kind.isEmpty() ? scope : kind + " · " + scope;
    }

    public boolean enabled() {
        return enabled;
    }

    public String status() {
        return status;
    }

    public State state() {
        return state;
    }

    public boolean indexable() {
        return indexable;
    }

    public boolean editable() {
        return editable;
    }

    public boolean removable() {
        return removable;
    }

    @Override
    public String toString() {
        return title() + " (" + description() + (enabled ? "" : ", abgewählt") + (status.isEmpty() ? "" : ", " + status)
                + ")";
    }
}
