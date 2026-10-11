package com.aresstack.enterpriseai.astu.acropolis.navigation;

import java.util.List;

/** Testbenenner ohne eigene Bezeichnungen: die der Analyse bleiben. */
final class AnalysisLabels implements SectionLabeler {

    static final AnalysisLabels KEEP = new AnalysisLabels();

    private AnalysisLabels() {
    }

    @Override
    public List<String> labels(String documentTitle, List<String> sectionTexts) {
        return null;
    }
}
