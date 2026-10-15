package com.aresstack.enterpriseai.research.runtime.knowledge;

import java.util.List;

/**
 * Decides after reading whether the evidence so far answers the research task, or whether a materially different
 * search is still justified. Reading something is not the same as having enough.
 */
public interface EvidenceJudge {

    /**
     * @param task     the research task in natural language
     * @param read     the resources read so far
     * @param excerpts their excerpts, in the same order
     */
    boolean sufficient(String task, List<KnowledgeHit> read, List<String> excerpts);
}
