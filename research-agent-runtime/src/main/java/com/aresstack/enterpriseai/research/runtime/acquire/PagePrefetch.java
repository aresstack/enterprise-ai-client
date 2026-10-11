package com.aresstack.enterpriseai.research.runtime.acquire;

import java.util.List;

/**
 * Optional an den Seitenwerkzeugen: der Lauf bleibt EIN Orchestrator-Thread, der die Seiten nacheinander
 * bewertet; die Seitenwerkzeuge dürfen die ausgewählten Treffer aber schon parallel laden (ein Pool fester Größe),
 * damit {@code web_open} danach nicht auf jede Seite einzeln wartet. Vorladen ist ein Cache und verbraucht kein
 * Recherche-Budget; gezählt wird erst das Öffnen.
 */
public interface PagePrefetch {

    /** @param urls die ausgewählten Treffer in Bewertungsreihenfolge */
    void prefetch(List<String> urls);
}
