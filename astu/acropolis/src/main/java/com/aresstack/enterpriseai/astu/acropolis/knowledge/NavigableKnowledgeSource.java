package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.SourcePartition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A source with a page hierarchy and its spaces (issue #116), as {@link BrowseKnowledgeHierarchyUseCase} finds them.
 * If the source could not list its spaces right now, the list is empty and {@link #failure()} tells why.
 */
public final class NavigableKnowledgeSource {

    private final SourceAddress source;
    private final List<KnowledgeSpace> spaces;
    private final KnowledgeSourceException.Kind failure;

    NavigableKnowledgeSource(SourceAddress source, List<SourcePartition> partitions,
                             KnowledgeSourceException.Kind failure) {
        this.source = source;
        List<KnowledgeSpace> spaces = new ArrayList<KnowledgeSpace>();
        if (partitions != null) {
            for (SourcePartition partition : partitions) {
                spaces.add(new KnowledgeSpace(partition));
            }
        }
        this.spaces = Collections.unmodifiableList(spaces);
        this.failure = failure;
    }

    public SourceAddress source() {
        return source;
    }

    /** Return the spaces in the order of the source. */
    public List<KnowledgeSpace> spaces() {
        return spaces;
    }

    /** Return the space with exactly this key, or {@code null}. */
    public KnowledgeSpace space(String key) {
        if (key == null) {
            return null;
        }
        for (KnowledgeSpace space : spaces) {
            if (space.key().equals(key.trim())) {
                return space;
            }
        }
        return null;
    }

    /** Return why the spaces are missing; {@code null} if the source listed them. */
    public KnowledgeSourceException.Kind failure() {
        return failure;
    }
}
