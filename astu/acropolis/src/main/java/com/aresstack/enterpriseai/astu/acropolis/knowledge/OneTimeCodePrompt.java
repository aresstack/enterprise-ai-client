package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.OneTimeCodeChallenge;

/**
 * Port zur Oberfläche: fragt den Benutzer nach dem Einmalcode einer Quelle, während ein Lesezugriff wartet. Die
 * Oberfläche sendet den Code selbst an {@link OneTimeCodeChallenge#submit(char[])}; er erreicht weder Modell noch
 * Verlauf, Werkzeugargumente oder Protokoll. Blockiert bis zur Entscheidung, nie auf dem EDT aufrufen.
 */
public interface OneTimeCodePrompt {

    /** Ergebnis der Abfrage. */
    enum Outcome {
        /** Die Quelle hat den Code angenommen; Dateien laden jetzt ohne neue Abfrage. */
        UNLOCKED,
        /** Abgebrochen, nicht möglich oder unterbrochen; der Lesezugriff gilt als verweigert. */
        DECLINED
    }

    /**
     * @param source    die Quelle, die den Code verlangt
     * @param challenge ihre Abfrage
     */
    Outcome unlock(SourceAddress source, OneTimeCodeChallenge challenge);

    /** Keine Oberfläche: lehnt immer ab. */
    static OneTimeCodePrompt none() {
        return (source, challenge) -> Outcome.DECLINED;
    }
}
