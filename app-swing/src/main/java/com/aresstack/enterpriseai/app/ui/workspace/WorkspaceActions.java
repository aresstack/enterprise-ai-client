package com.aresstack.enterpriseai.app.ui.workspace;

import com.aresstack.enterpriseai.app.ui.agent.ShellMode;

import java.util.Collections;
import java.util.List;

/**
 * Was die Arbeitsfläche nicht selbst kann und an die Composition Root gibt: einen neuen Chat beginnen (die
 * Anbindung eröffnet eine neue Unterhaltung und leert das Model), gespeicherte Chats auflisten, öffnen und
 * löschen (Chat-Historie) und die Einstellungen öffnen.
 */
public interface WorkspaceActions {

    /** „+ Neuer Chat“ im Drawer, für die gerade sichtbare Ansicht. */
    void newChatRequested(ShellMode mode);

    /** Das Zahnrad im Drawer. */
    void settingsRequested();

    /** Die gespeicherten Chats, zuletzt geänderte zuerst; ohne Historie leer. */
    default List<SavedChatItem> savedChats() {
        return Collections.emptyList();
    }

    /** Die Kennung des Chats, in den gerade geschrieben wird, oder {@code null} ohne Historie. */
    default String currentChatId() {
        return null;
    }

    /** Ein gespeicherter Chat soll im Chat-Modus geöffnet werden. */
    default void openSavedChatRequested(String chatId) {
    }

    /** Ein gespeicherter Chat soll samt Anhängen gelöscht werden (bestätigt ist das schon). */
    default void deleteSavedChatRequested(String chatId) {
    }
}
