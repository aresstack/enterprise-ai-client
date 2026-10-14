package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The arrangement of the navigation drawer (#114): the order of its sections, which of them show and the share of the
 * height each visible one gets. Immutable; every change returns a new layout whose visible shares add up to 1. A
 * global UI preference, never per tab, page, source or chat; {@link #encode} and {@link #parse} turn it into the three
 * strings of the application state and back, falling back to the default for anything invalid.
 */
public final class NavigationSectionLayout {

    /** The page tree. */
    public static final String TREE = "tree";
    /** The references of the page to other pages. */
    public static final String OUTGOING = "outgoing";
    /** The pages that link to the page. */
    public static final String INCOMING = "incoming";
    /** All sections in their default order. */
    private static final List<String> SECTIONS = Collections.unmodifiableList(
            java.util.Arrays.asList(TREE, OUTGOING, INCOMING));

    /** Default shares, taken from the former split of tree and references (0.62). */
    private static final double TREE_SHARE = 0.62;
    private static final double OUTGOING_SHARE = 0.22;
    private static final double INCOMING_SHARE = 0.16;
    /** The smallest share a visible section keeps when a divider is dragged. */
    static final double MIN_SHARE = 0.05;
    private static final String SEPARATOR = ",";
    /** Weights closer than this count as equal: stored values keep four decimals. */
    private static final double SAME_WEIGHT = 1e-3;

    private final List<String> order;
    private final Set<String> visible;
    /** Weight of every section, also of hidden ones, so a section comes back with its last share. */
    private final Map<String, Double> weights;

    private NavigationSectionLayout(List<String> order, Set<String> visible, Map<String, Double> weights) {
        this.order = Collections.unmodifiableList(new ArrayList<String>(order));
        this.visible = Collections.unmodifiableSet(new LinkedHashSet<String>(visible));
        this.weights = Collections.unmodifiableMap(new LinkedHashMap<String, Double>(weights));
    }

    /** All three sections, tree first, the tree with the largest share. */
    public static NavigationSectionLayout defaults() {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        weights.put(TREE, TREE_SHARE);
        weights.put(OUTGOING, OUTGOING_SHARE);
        weights.put(INCOMING, INCOMING_SHARE);
        return new NavigationSectionLayout(SECTIONS, new LinkedHashSet<String>(SECTIONS), weights);
    }

    /** All sections in their order, visible or not. */
    public List<String> order() {
        return order;
    }

    /** The visible sections from top to bottom. */
    public List<String> visibleOrder() {
        List<String> shown = new ArrayList<String>();
        for (String id : order) {
            if (visible.contains(id)) {
                shown.add(id);
            }
        }
        return shown;
    }

    public boolean isVisible(String id) {
        return visible.contains(id);
    }

    /** The share of the height of {@code id}: 0 when hidden, the visible ones add up to 1. */
    public double share(String id) {
        if (!visible.contains(id)) {
            return 0d;
        }
        double sum = 0d;
        for (String shown : visible) {
            sum += weights.get(shown);
        }
        return sum <= 0d ? 1d / visible.size() : weights.get(id) / sum;
    }

    /** Hide a visible section or show a hidden one (with its last share). */
    public NavigationSectionLayout toggle(String id) {
        requireKnown(id);
        Set<String> next = new LinkedHashSet<String>(visible);
        if (!next.remove(id)) {
            next.add(id);
        }
        return new NavigationSectionLayout(order, next, normalizedWeights(next));
    }

    /**
     * Put {@code id} at {@code position} among the visible sections (0 = top, past the end = bottom) and show it;
     * hidden sections keep their place relative to the others.
     */
    public NavigationSectionLayout moveTo(String id, int position) {
        requireKnown(id);
        List<String> shown = visibleOrder();
        shown.remove(id);
        int at = Math.max(0, Math.min(position, shown.size()));
        shown.add(at, id);
        List<String> next = new ArrayList<String>(shown);
        for (String other : order) {
            if (!next.contains(other)) {
                next.add(other); // hidden sections after the visible ones
            }
        }
        Set<String> nextVisible = new LinkedHashSet<String>(visible);
        nextVisible.add(id);
        return new NavigationSectionLayout(next, nextVisible, normalizedWeights(nextVisible));
    }

    /**
     * Move the border below the visible section at {@code index} by {@code delta} of the total height, taking from or
     * giving to the section below it; neither gets smaller than {@link #MIN_SHARE}.
     */
    public NavigationSectionLayout resize(int index, double delta) {
        List<String> shown = visibleOrder();
        if (index < 0 || index + 1 >= shown.size()) {
            return this;
        }
        String upper = shown.get(index);
        String lower = shown.get(index + 1);
        double pair = share(upper) + share(lower);
        double newUpper = Math.max(MIN_SHARE, Math.min(pair - MIN_SHARE, share(upper) + delta));
        Map<String, Double> next = new LinkedHashMap<String, Double>(weights);
        for (String id : shown) {
            next.put(id, share(id));
        }
        next.put(upper, newUpper);
        next.put(lower, pair - newUpper);
        return new NavigationSectionLayout(order, visible, next);
    }

    /** Weights where the visible ones are their shares (sum 1) and the hidden ones keep theirs. */
    private Map<String, Double> normalizedWeights(Set<String> nextVisible) {
        Map<String, Double> next = new LinkedHashMap<String, Double>(weights);
        double sum = 0d;
        for (String id : nextVisible) {
            sum += weights.get(id);
        }
        for (String id : nextVisible) {
            next.put(id, sum <= 0d ? 1d / nextVisible.size() : weights.get(id) / sum);
        }
        return next;
    }

    private static void requireKnown(String id) {
        if (!SECTIONS.contains(id)) {
            throw new IllegalArgumentException("unknown navigation section: " + id);
        }
    }

    // ------------------------------------------------------------------ application state

    /** The order of all sections, e.g. {@code tree,outgoing,incoming}. */
    public String encodeOrder() {
        return join(order);
    }

    /** The visible sections in order; empty when none shows. */
    public String encodeVisible() {
        return join(visibleOrder());
    }

    /** The weights of all sections in their order, e.g. {@code 0.6,0.25,0.15}. */
    public String encodeSizes() {
        List<String> values = new ArrayList<String>();
        for (String id : order) {
            values.add(String.format(Locale.ROOT, "%.4f", weights.get(id)));
        }
        return join(values);
    }

    /**
     * Read the three stored strings; {@code null} or invalid values fall back to the default for that part: an order
     * must name every section once, visible sections must be known, sizes must be positive numbers, one per section.
     */
    public static NavigationSectionLayout parse(String order, String visible, String sizes) {
        NavigationSectionLayout fallback = defaults();
        List<String> parsedOrder = split(order);
        if (parsedOrder.size() != SECTIONS.size() || !new LinkedHashSet<String>(parsedOrder).containsAll(SECTIONS)) {
            parsedOrder = fallback.order;
        }
        Set<String> parsedVisible = new LinkedHashSet<String>();
        if (visible == null) {
            parsedVisible.addAll(fallback.visible);
        } else {
            for (String id : split(visible)) {
                if (!SECTIONS.contains(id)) {
                    parsedVisible = new LinkedHashSet<String>(fallback.visible);
                    break;
                }
                parsedVisible.add(id);
            }
        }
        Map<String, Double> parsedWeights = new LinkedHashMap<String, Double>(fallback.weights);
        List<String> values = split(sizes);
        if (values.size() == parsedOrder.size()) {
            Map<String, Double> read = new LinkedHashMap<String, Double>();
            try {
                for (int i = 0; i < values.size(); i++) {
                    double value = Double.parseDouble(values.get(i));
                    if (!(value > 0d) || Double.isInfinite(value)) {
                        throw new NumberFormatException(values.get(i));
                    }
                    read.put(parsedOrder.get(i), value);
                }
                parsedWeights = read;
            } catch (NumberFormatException invalid) {
                // keep the default shares
            }
        }
        NavigationSectionLayout layout = new NavigationSectionLayout(parsedOrder, parsedVisible, parsedWeights);
        return new NavigationSectionLayout(parsedOrder, parsedVisible, layout.normalizedWeights(parsedVisible));
    }

    private static List<String> split(String value) {
        List<String> parts = new ArrayList<String>();
        if (value == null) {
            return parts;
        }
        for (String part : value.split(SEPARATOR)) {
            if (!part.trim().isEmpty()) {
                parts.add(part.trim());
            }
        }
        return parts;
    }

    private static String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (text.length() > 0) {
                text.append(SEPARATOR);
            }
            text.append(value);
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NavigationSectionLayout)) {
            return false;
        }
        NavigationSectionLayout that = (NavigationSectionLayout) other;
        if (!order.equals(that.order) || !visible.equals(that.visible)) {
            return false;
        }
        for (String id : order) {
            if (Math.abs(weights.get(id) - that.weights.get(id)) > SAME_WEIGHT) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return order.hashCode() * 31 + visible.hashCode();
    }

    @Override
    public String toString() {
        return "order=" + encodeOrder() + " visible=" + encodeVisible() + " sizes=" + encodeSizes();
    }
}
