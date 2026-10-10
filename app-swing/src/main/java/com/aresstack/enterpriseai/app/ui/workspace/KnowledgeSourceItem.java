package com.aresstack.enterpriseai.app.ui.workspace;

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
        return id + " (" + description() + (enabled ? "" : ", abgewählt") + (status.isEmpty() ? "" : ", " + status)
                + ")";
    }
}
