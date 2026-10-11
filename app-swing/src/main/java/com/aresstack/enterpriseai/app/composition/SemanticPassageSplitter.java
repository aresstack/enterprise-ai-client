package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.application.knowledge.KnowledgePassageSplitter;
import com.aresstack.enterpriseai.application.passage.PassageSegmentation;
import com.aresstack.enterpriseai.application.passage.SourcePassage;

import java.util.ArrayList;
import java.util.List;

/** Verbindet die Indexierung mit der Passagenbildung aus askai arch ({@link PassageSegmentation}). */
final class SemanticPassageSplitter implements KnowledgePassageSplitter {

    private final PassageSegmentation segmentation;

    SemanticPassageSplitter(PassageSegmentation segmentation) {
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
    public List<Passage> split(String resourceId, String text) {
        List<SourcePassage> found = segmentation.segment(resourceId, text);
        List<Passage> passages = new ArrayList<Passage>(found.size());
        for (SourcePassage passage : found) {
            passages.add(new Passage(passage.headingPath(), passage.text(), passage.vector()));
        }
        return passages;
    }
}
