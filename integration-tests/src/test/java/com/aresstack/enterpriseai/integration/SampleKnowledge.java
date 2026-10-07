package com.aresstack.enterpriseai.integration;

import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;

/** Die kleine Wissensbasis der Slices B, F und G: drei Handbuchseiten mit klar unterscheidbaren Themen. */
final class SampleKnowledge {

    static final String SOURCE_ID = "handbuch";
    static final String QUESTION = "Wie lange ist die Kündigungsfrist?";
    static final String EXPECTED_TITLE = "Kündigungsfrist";
    static final String EXPECTED_PHRASE = "drei Monate zum Quartalsende";

    private SampleKnowledge() {
    }

    static InMemoryKnowledgeSource handbuch() {
        return new InMemoryKnowledgeSource(SOURCE_ID)
                .add("urlaub", "Urlaubsregelung", "Mitarbeiterinnen und Mitarbeiter haben dreißig Tage Urlaub im "
                        + "Kalenderjahr. Resturlaub verfällt am 31. März des Folgejahres, wenn er nicht beantragt wurde.")
                .add("kuendigung", EXPECTED_TITLE, "Die Kündigungsfrist beträgt " + EXPECTED_PHRASE + ". Die "
                        + "Frist gilt für beide Seiten; eine Kündigung muss schriftlich erfolgen.")
                .add("gleitzeit", "Gleitzeit", "Die Kernarbeitszeit liegt zwischen neun und fünfzehn Uhr. Außerhalb "
                        + "der Kernarbeitszeit kann die Arbeitszeit frei gestaltet werden.");
    }

    static SourceScope scope() {
        return SourceScope.of("urlaub", "kuendigung", "gleitzeit");
    }
}
