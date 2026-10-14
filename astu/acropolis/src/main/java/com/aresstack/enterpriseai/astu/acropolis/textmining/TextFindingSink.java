package com.aresstack.enterpriseai.astu.acropolis.textmining;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceRef;

import java.util.List;

/**
 * Where the findings of a text extractor are kept (outbound port; the composition root connects the metadata
 * catalog of issue #106). Every call blocks on storage: never call it on the Swing event thread.
 */
public interface TextFindingSink {

    /**
     * Whether the findings stored for {@code resource} by this extractor were made from the same content (hash, else
     * revision) and the same extractor version; then nothing needs to be written again.
     */
    boolean isCurrent(KnowledgeResourceRef resource, String extractorId, String extractorVersion);

    /**
     * Replace the automatic findings of this extractor on this resource by {@code findings} in one step (empty list:
     * remove them). Findings of other extractors and findings a person added or reviewed stay.
     *
     * @param types every type the findings use; registered before they are written
     * @throws RuntimeException if storing fails; then nothing changed
     */
    void replace(KnowledgeResourceRef resource, String extractorId, String extractorVersion,
                 List<TextFindingType> types, List<TextFinding> findings);

    /** A sink that keeps nothing. */
    static TextFindingSink none() {
        return new TextFindingSink() {
            @Override
            public boolean isCurrent(KnowledgeResourceRef resource, String extractorId, String extractorVersion) {
                return true;
            }

            @Override
            public void replace(KnowledgeResourceRef resource, String extractorId, String extractorVersion,
                                List<TextFindingType> types, List<TextFinding> findings) {
                // keeps nothing
            }
        };
    }
}
