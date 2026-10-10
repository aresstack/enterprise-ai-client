package com.aresstack.enterpriseai.domain.source;

/**
 * Ein Eingabefeld eines Quelltyps, wie es der Dialog „+ Quelle“ zeigt: Schlüssel (ohne {@code source.<id>.}),
 * Beschriftung, Hinweis, Art und Vorgabe. Der Adapter liefert seine Felder selbst ({@link KnowledgeSourceType});
 * die Oberfläche baut daraus das Formular, ohne den Typ zu kennen. Keine Secrets: Zugangsdaten sind höchstens
 * ein Verweis ({@link Kind#SECRET_REF}).
 */
public final class SourceSettingField {

    /** Wie das Feld eingegeben wird. */
    public enum Kind {
        /** Einzeiliger Text. */
        TEXT,
        /** Ganze Zahl als Text. */
        NUMBER,
        /** Schalter; Wert {@code true} oder {@code false}. */
        FLAG,
        /** Verzeichnispfad mit Auswahlknopf. */
        DIRECTORY,
        /** Titel eines Eintrags im Tresor (KeePass), nie das Secret selbst. */
        SECRET_REF
    }

    private final String key;
    private final String label;
    private final String hint;
    private final Kind kind;
    private final String defaultValue;
    private final boolean required;

    private SourceSettingField(String key, String label, String hint, Kind kind, String defaultValue,
                               boolean required) {
        if (key == null || key.trim().isEmpty() || label == null || label.trim().isEmpty() || kind == null) {
            throw new IllegalArgumentException("key, label and kind must be set");
        }
        this.key = key.trim();
        this.label = label.trim();
        this.hint = hint == null ? "" : hint.trim();
        this.kind = kind;
        this.defaultValue = defaultValue == null ? "" : defaultValue.trim();
        this.required = required;
    }

    public static SourceSettingField of(String key, Kind kind, String label, String hint) {
        return new SourceSettingField(key, label, hint, kind, "", false);
    }

    public static SourceSettingField text(String key, String label, String hint) {
        return of(key, Kind.TEXT, label, hint);
    }

    /** Dasselbe Feld als Pflichtangabe. */
    public SourceSettingField required() {
        return new SourceSettingField(key, label, hint, kind, defaultValue, true);
    }

    /** Dasselbe Feld mit Vorgabe für neue Quellen. */
    public SourceSettingField withDefault(String value) {
        return new SourceSettingField(key, label, hint, kind, value, required);
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    /** Tooltip bzw. Erklärung; leer, wenn keine. */
    public String hint() {
        return hint;
    }

    public Kind kind() {
        return kind;
    }

    /** Vorgabe für neue Quellen; leer, wenn keine. */
    public String defaultValue() {
        return defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    @Override
    public String toString() {
        return "SourceSettingField[" + key + ", " + kind + (required ? ", Pflicht" : "") + "]";
    }
}
