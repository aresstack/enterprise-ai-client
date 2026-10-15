package com.aresstack.enterpriseai.research.runtime.knowledge;

/**
 * The current discovery strategy of the Research Agent: compact lexical queries made of the strongest literal terms
 * of the task, because a keyword search finds exact terms reliably and whole sentences poorly. A choice of the
 * agent for this iteration, not a statement about the sources (which may also search semantically).
 */
public final class LexicalDiscoveryStrategy implements DiscoveryStrategy {

    static final String RULES = "Die Erstsuche ist lexikalisch: formuliere kurze Schlüsselwortfolgen mit 1 bis 3 "
            + "Wörtern (höchstens " + ResearchQueries.MAX_QUERY_WORDS + "), keine Fragen und keine ganzen Sätze, ohne "
            + "Füllwörter, denn eine Schlüsselwortsuche findet genaue Begriffe zuverlässig und ganze Sätze schlecht. "
            + "Übernimm interne Bezeichner, Abkürzungen, System-, Produkt- und Projektnamen wörtlich und in ihrer "
            + "Schreibweise (z. B. HaVdB, BS-PIMA, Hashicorp Vault); übersetze, erkläre oder umschreibe sie nie. Keine "
            + "Synonymketten und keine Varianten derselben Begriffe.";

    public LexicalDiscoveryStrategy() {
    }

    @Override
    public String name() {
        return "lexical";
    }

    @Override
    public String planningRules() {
        return RULES;
    }

    @Override
    public boolean accepts(String query) {
        return ResearchQueries.isConcise(query);
    }

    @Override
    public String fallbackQuery(String task) {
        return ResearchQueries.literalQuery(task);
    }
}
