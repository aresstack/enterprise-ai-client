package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import java.util.function.Consumer;

/**
 * Eine Einmalcode-Abfrage der Quelle für eine Datei (etwa ein Zwei-Faktor-Plugin vor Confluence-Downloads). Der
 * Anzeigebereich zeigt sie an der Stelle der Datei ({@link ReaderSignInPanel}) und lädt nach der Bestätigung neu.
 * Rückrufe kommen auf dem EDT; das Netz bedient der Controller abseits davon. Der Code wird nur gesendet.
 */
public interface ReaderSignIn {

    /** Wofür der Code gilt (z. B. der Host der Quelle). */
    String service();

    /** Hinweis der Quelle (etwa „Code per E-Mail gesendet“), darf leer sein. */
    String message();

    /** Fehlermeldung des letzten Versuchs, sonst leer. */
    String error();

    boolean resendAvailable();

    /**
     * Sendet den Code; {@code next} bekommt {@code null}, wenn er angenommen wurde, sonst die nächste Abfrage (etwa
     * mit „Code ungültig“). Der Empfänger überschreibt {@code code} nach dem Aufruf.
     */
    void submit(char[] code, Consumer<ReaderSignIn> next, Consumer<String> failed);

    /** Fordert einen neuen Code an; {@code next} bekommt die Abfrage danach. */
    void resend(Consumer<ReaderSignIn> next, Consumer<String> failed);
}
