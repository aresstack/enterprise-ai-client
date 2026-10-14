package com.aresstack.enterpriseai.fact.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One declarative rule of an {@link ArgumentProjection}: where in the payload the arguments of one kind are and how to
 * read them. {@code path} names a member by dotted names ({@code predicate}, {@code subject},
 * {@code arguments}); an array there yields one argument per element. An element is either a string (the normalized
 * value, for a rule with a fixed role) or an object read by member names: the role (fixed, or from
 * {@code roleField}), the referenced fact id ({@code referenceField}) and the normalized value ({@code valueField}).
 * Missing members yield no argument rather than a misleading one.
 */
public final class ArgumentRule {

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final Pattern PATH = Pattern.compile(NAME.pattern() + "(\\." + NAME.pattern() + "){0,7}");

    private final String path;
    private final String role;
    private final String roleField;
    private final String referenceField;
    private final String valueField;
    private final FactTypeId referenceType;

    private ArgumentRule(String path, String role, String roleField, String referenceField, String valueField,
                         FactTypeId referenceType) {
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("invalid argument path: " + path);
        }
        if ((role == null) == (roleField == null)) {
            throw new IllegalArgumentException("argument rule needs either a fixed role or a role field");
        }
        if (referenceField == null && valueField == null && role == null) {
            throw new IllegalArgumentException("argument rule reads neither a reference nor a value");
        }
        this.path = path;
        this.role = role == null ? null : CatalogName.check("argument role", role);
        this.roleField = field(roleField);
        this.referenceField = field(referenceField);
        this.valueField = field(valueField);
        this.referenceType = referenceType;
    }

    /**
     * Arguments with the fixed {@code role} at {@code path}: a string there is the value; an object is read by
     * {@code referenceField} and {@code valueField} (either may be {@code null}).
     */
    public static ArgumentRule fixed(String path, String role, String referenceField, String valueField) {
        return new ArgumentRule(path, role, null, referenceField, valueField, null);
    }

    /** Arguments at {@code path} whose role is read from the member {@code roleField} of each object. */
    public static ArgumentRule byRoleField(String path, String roleField, String referenceField, String valueField) {
        return new ArgumentRule(path, null, roleField, referenceField, valueField, null);
    }

    /** The same rule, accepting only references to facts of {@code type}. */
    public ArgumentRule referencing(FactTypeId type) {
        return new ArgumentRule(path, role, roleField, referenceField, valueField, type);
    }

    public String path() {
        return path;
    }

    /** Fixed role, or {@code null} if read from {@link #roleField()}. */
    public String role() {
        return role;
    }

    /** Member holding the role, or {@code null} for a fixed role. */
    public String roleField() {
        return roleField;
    }

    /** Member holding the referenced fact id, or {@code null}. */
    public String referenceField() {
        return referenceField;
    }

    /** Member holding the normalized value, or {@code null}. */
    public String valueField() {
        return valueField;
    }

    /** Required type of referenced facts, or {@code null} for any type. */
    public FactTypeId referenceType() {
        return referenceType;
    }

    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("path", path);
        put(map, "role", role);
        put(map, "roleField", roleField);
        put(map, "reference", referenceField);
        put(map, "value", valueField);
        put(map, "referenceType", referenceType == null ? null : referenceType.value());
        return map;
    }

    static ArgumentRule fromMap(Map<String, Object> map) {
        Object type = map.get("referenceType");
        return new ArgumentRule(text(map, "path"), text(map, "role"), text(map, "roleField"), text(map, "reference"),
                text(map, "value"), type instanceof String ? FactTypeId.of((String) type) : null);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ArgumentRule && toMap().equals(((ArgumentRule) other).toMap());
    }

    @Override
    public int hashCode() {
        return toMap().hashCode();
    }

    @Override
    public String toString() {
        return "ArgumentRule" + toMap();
    }

    private static String field(String name) {
        if (name == null) {
            return null;
        }
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid argument field name: " + name);
        }
        return name;
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : null;
    }
}
