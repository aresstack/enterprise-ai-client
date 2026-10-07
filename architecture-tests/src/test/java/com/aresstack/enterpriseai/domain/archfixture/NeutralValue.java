package com.aresstack.enterpriseai.domain.archfixture;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Gegenprobe: ein Domain-Wert mit ausschließlich neutralen JDK-Typen darf keine Regel verletzen. */
public final class NeutralValue {

    private static final int LIMIT = 10;

    private final URI id;
    private final List<String> tags;
    private final double[] vector;

    public NeutralValue(URI id, List<String> tags, double[] vector) {
        this.id = id;
        this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
        this.vector = vector.clone();
    }

    public URI id() {
        return id;
    }

    public List<String> tags() {
        return tags.size() > LIMIT ? tags.subList(0, LIMIT) : tags;
    }

    public double[] vector() {
        return vector.clone();
    }
}
