package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePassageSplitter;
import com.aresstack.enterpriseai.astu.acropolis.passage.PassageSegmentation;
import com.aresstack.enterpriseai.astu.acropolis.passage.SourcePassage;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeStructuralBlock;

import java.util.ArrayList;
import java.util.List;

/** Verbindet die Indexierung mit der Passagenbildung aus askai arch ({@link PassageSegmentation}). */
public final class SemanticPassageSplitter implements KnowledgePassageSplitter {

    private final PassageSegmentation segmentation;

    public SemanticPassageSplitter(PassageSegmentation segmentation) {
        if (segmentation == null) {
            throw new IllegalArgumentException("segmentation must not be null");
        }
        this.segmentation = segmentation;
    }

    @Override
    public String fingerprint() {
        return segmentation.fingerprint();
    }

    @Override
    public List<Passage> split(String resourceId, String text, List<KnowledgeStructuralBlock> structure) {
        List<SourcePassage> found = segmentation.segment(resourceId, text, structure);
        List<Passage> passages = new ArrayList<Passage>(found.size());
        for (SourcePassage passage : found) {
            passages.add(new Passage(passage.headingPath(), passage.text(), passage.vector()));
        }
        return passages;
    }
}
