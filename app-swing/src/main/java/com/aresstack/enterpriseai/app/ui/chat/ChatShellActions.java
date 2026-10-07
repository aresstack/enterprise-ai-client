package com.aresstack.enterpriseai.app.ui.chat;

/**
 * Die Bedienabsichten der Chat-Oberfläche. Die Ansicht entscheidet nicht, was Senden bedeutet; sie meldet
 * nur, dass der Nutzer es will. Erfüllt wird das von einer Anbindung, die das {@link ChatShellModel}
 * fortschreibt (in AP4 ein Fake, danach der Chat-Use-Case aus {@code application}).
 *
 * <p>Das ist bewusst kein Chat-Port: keine Nachrichten-, Rollen- oder Providertypen, nur Nutzerabsichten.
 */
public interface ChatShellActions {

    /** Der Nutzer will {@code text} (bereits getrimmt, nicht leer) senden; {@code ragEnabled} ist der Schalter. */
    void sendRequested(String text, boolean ragEnabled);

    /** Der Nutzer will die laufende Antwort abbrechen. */
    void stopRequested();
}
