package com.aresstack.enterpriseai.research.runtime.acquire;

import java.util.List;

/**
 * Optional an den Seitenwerkzeugen: der Lauf bleibt EIN Orchestrator-Thread, der die Seiten nacheinander
 * bewertet; die Seitenwerkzeuge dürfen die ausgewählten Treffer aber schon parallel laden (ein Pool fester Größe),
 * damit {@code web_open} danach nicht auf jede Seite einzeln wartet. Geladen wird nie mehr, als das Seitenbudget
 * noch erlaubt.
 */
public interface PagePrefetch {

    /**
     * @param urls     die ausgewählten Treffer in Bewertungsreihenfolge
     * @param maxPages wie viele davon höchstens geladen werden dürfen (Restbudget)
     */
    void prefetch(List<String> urls, int maxPages);
}
