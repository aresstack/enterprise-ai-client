package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

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

    /**
     * Wie {@link #sendRequested(String, boolean, List)}, dazu Seiten aus dem Lesebereich (nur Titel und Bookmark),
     * die dem Chat hinzugefügt werden. Ohne eigene Umsetzung gehen die Seiten verloren.
     */
    default void sendRequested(String text, boolean ragEnabled, List<Path> attachments, List<ChatPage> pages) {
        sendRequested(text, ragEnabled, attachments);
    }

    /**
     * Der Nutzer will den Entwurf senden (Text, neue Dateien, Seiten, Verweise auf Ressourcen des Chats); das ist der
     * Weg des Composers. Ohne eigene Umsetzung gehen Text, Dateien und Seiten über die älteren Methoden, Verweise
     * gehen verloren.
     */
    default void sendRequested(ChatDraft draft) {
        if (!draft.pages().isEmpty()) {
            sendRequested(draft.text(), draft.ragEnabled(), draft.attachments(), draft.pages());
        } else if (!draft.attachments().isEmpty()) {
            sendRequested(draft.text(), draft.ragEnabled(), draft.attachments());
        } else {
            sendRequested(draft.text(), draft.ragEnabled());
        }
    }

    /**
     * Alle Ressourcen der aktuellen Unterhaltung (Dateien, Seiten, verwendete Quellen), je Ressource einmal, für die
     * Ressourcenübersicht im Composer; leer, wenn die Anbindung keine kennt.
     */
    default List<ChatResource> chatResources() {
        return java.util.Collections.emptyList();
    }

    /** Ob die Anbindung Dateianhänge annimmt (dann zeigt der Composer die Büroklammer). */
    default boolean supportsAttachments() {
        return false;
    }

    /**
     * Der Nutzer hat im Composer den Denkaufwand gewählt ({@code low}, {@code medium}, {@code high} oder
     * {@code null} für den Standard des Modells); gilt ab der nächsten Nachricht.
     */
    default void reasoningChanged(String effort) {
    }

    /** Der Nutzer will die laufende Antwort abbrechen. */
    void stopRequested();
}
