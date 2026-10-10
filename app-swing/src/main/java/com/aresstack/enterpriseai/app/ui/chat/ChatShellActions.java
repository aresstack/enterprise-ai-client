package com.aresstack.enterpriseai.app.ui.chat;

import java.nio.file.Path;
import java.util.List;

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

    /**
     * Wie {@link #sendRequested(String, boolean)}, mit Dateien, die der Unterhaltung als Anhänge hinzugefügt
     * werden. Ohne Unterstützung für Anhänge ({@link #supportsAttachments()}) werden sie übergangen.
     */
    default void sendRequested(String text, boolean ragEnabled, List<Path> attachments) {
        sendRequested(text, ragEnabled);
    }

    /** Ob die Anbindung Dateianhänge annimmt (dann zeigt der Composer die Büroklammer). */
    default boolean supportsAttachments() {
        return false;
    }

    /** Der Nutzer will die laufende Antwort abbrechen. */
    void stopRequested();
}
