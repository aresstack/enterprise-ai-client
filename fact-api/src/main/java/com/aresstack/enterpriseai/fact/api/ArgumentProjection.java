package com.aresstack.enterpriseai.fact.api;

import com.aresstack.enterpriseai.astu.json.Json;
import com.aresstack.enterpriseai.astu.json.JsonException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The searchable arguments of a fact type (issue #108): declarative {@link ArgumentRule}s that derive
 * {@link FactArgument}s from a payload. Part of the type definition and versioned with it; a store keeps the derived
 * arguments in step with the payload on every write and can rebuild them from stored facts alone. The projection is
 * no second source of truth: the payload decides.
 */
public final class ArgumentProjection {

    /** Version of the stored rule format. */
    static final int FORMAT_VERSION = 1;

    private static final ArgumentProjection NONE = new ArgumentProjection(Collections.<ArgumentRule>emptyList());

    private final List<ArgumentRule> rules;

    private ArgumentProjection(List<ArgumentRule> rules) {
        this.rules = Collections.unmodifiableList(new ArrayList<ArgumentRule>(rules));
    }

    /** No searchable arguments. */
    public static ArgumentProjection none() {
        return NONE;
    }

    public static ArgumentProjection of(ArgumentRule... rules) {
        return of(Arrays.asList(rules));
    }

    public static ArgumentProjection of(List<ArgumentRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return NONE;
        }
        for (ArgumentRule rule : rules) {
            if (rule == null) {
                throw new IllegalArgumentException("argument rule must not be null");
            }
        }
        return new ArgumentProjection(rules);
    }

    /**
     * Restore a projection from {@link #toJson()}; empty text is {@link #none()}.
     *
     * @throws IllegalArgumentException if the text is no projection
     */
    public static ArgumentProjection parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return NONE;
        }
        Map<String, Object> root = Json.parseObject(json);
        Integer version = Json.integer(root, "version");
        if (version == null || version != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported argument projection format " + version);
        }
        List<ArgumentRule> rules = new ArrayList<ArgumentRule>();
        for (Object rule : Json.array(root, "rules")) {
            if (!(rule instanceof Map)) {
                throw new IllegalArgumentException("argument rule is no object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rule;
            rules.add(ArgumentRule.fromMap(map));
        }
        return of(rules);
    }

    public List<ArgumentRule> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** The stored form; {@code ""} for {@link #none()}. */
    public String toJson() {
        if (rules.isEmpty()) {
            return "";
        }
        List<Object> list = new ArrayList<Object>();
        for (ArgumentRule rule : rules) {
            list.add(rule.toMap());
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", FORMAT_VERSION);
        root.put("rules", list);
        return Json.write(root);
    }

    /**
     * The arguments of {@code payload}, ordinals counted per role in rule and element order.
     *
     * @throws IllegalArgumentException if the payload is no JSON object or names a reference that is no fact id
     */
    public List<FactArgument> project(FactPayload payload) {
        List<FactArgument> arguments = new ArrayList<FactArgument>();
        for (Projected projected : projectWithTypes(payload)) {
            arguments.add(projected.argument());
        }
        return Collections.unmodifiableList(arguments);
    }

    /**
     * Like {@link #project}, each argument with the fact type its reference must have (from its rule).
     *
     * @throws IllegalArgumentException if the payload is no JSON object or names a reference that is no fact id
     */
    public List<Projected> projectWithTypes(FactPayload payload) {
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Object> root;
        try {
            root = Json.parseObject(payload.json());
        } catch (JsonException e) {
            throw new IllegalArgumentException("payload is no JSON object: " + e.getMessage(), e);
        }
        List<Projected> arguments = new ArrayList<Projected>();
        Map<String, Integer> ordinals = new HashMap<String, Integer>();
        for (ArgumentRule rule : rules) {
            Object target = resolve(root, rule.path());
            if (target instanceof List) {
                for (Object element : (List<?>) target) {
                    add(rule, element, arguments, ordinals);
                }
            } else if (target != null) {
                add(rule, target, arguments, ordinals);
            }
        }
        return Collections.unmodifiableList(arguments);
    }

    /** A derived argument and the type its referenced fact must have ({@code null}: any type). */
    public static final class Projected {
        private final FactArgument argument;
        private final FactTypeId referenceType;

        Projected(FactArgument argument, FactTypeId referenceType) {
            this.argument = argument;
            this.referenceType = referenceType;
        }

        public FactArgument argument() {
            return argument;
        }

        public FactTypeId referenceType() {
            return referenceType;
        }
    }

    private static void add(ArgumentRule rule, Object element, List<Projected> arguments,
                            Map<String, Integer> ordinals) {
        String role = rule.role();
        FactId reference = null;
        String value = null;
        if (element instanceof String) {
            if (role == null) {
                return; // the role would have to come from an object member
            }
            value = (String) element;
        } else if (element instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) element;
            if (role == null) {
                role = Json.string(object, rule.roleField());
                if (role == null || !CatalogName.isValid(role)) {
                    return;
                }
            }
            if (rule.referenceField() != null) {
                String id = Json.string(object, rule.referenceField());
                if (id != null && !id.trim().isEmpty()) {
                    try {
                        reference = FactId.of(UUID.fromString(id.trim()));
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("argument reference is no fact id", e);
                    }
                }
            }
            if (rule.valueField() != null) {
                value = Json.string(object, rule.valueField());
            }
        } else {
            return;
        }
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > FactArgument.MAX_VALUE_LENGTH) {
            trimmed = trimmed.substring(0, FactArgument.MAX_VALUE_LENGTH).trim();
        }
        if (reference == null && trimmed.isEmpty()) {
            return;
        }
        Integer next = ordinals.get(role);
        int ordinal = next == null ? 0 : next;
        ordinals.put(role, ordinal + 1);
        arguments.add(new Projected(FactArgument.of(role, ordinal, reference, trimmed.isEmpty() ? null : trimmed),
                reference == null ? null : rule.referenceType()));
    }

    private static Object resolve(Map<String, Object> root, String path) {
        Object current = root;
        for (String name : path.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(name);
        }
        return current;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ArgumentProjection
                && rules.equals(((ArgumentProjection) other).rules);
    }

    @Override
    public int hashCode() {
        return rules.hashCode();
    }

    @Override
    public String toString() {
        return "ArgumentProjection" + rules;
    }
}
