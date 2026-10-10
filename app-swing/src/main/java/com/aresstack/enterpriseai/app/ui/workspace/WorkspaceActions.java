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

    /**
     * Ein gespeicherter Chat soll gelöscht werden (askai arch: ohne Rückfrage, mit ↩). Bis zum Beenden lässt er sich
     * mit {@link #restoreSavedChatRequested} zurückholen; erst dann gehen Nachrichten und Anhänge endgültig.
     */
    default void deleteSavedChatRequested(String chatId) {
    }

    /** Ein in diesem Lauf gelöschter Chat soll zurückkommen. */
    default void restoreSavedChatRequested(String chatId) {
    }

    /** Ein Chat soll einen neuen Titel bekommen (askai arch: „Umbenennen“). */
    default void renameChatRequested(String chatId, String title) {
    }

    /** Der vom Nutzer gewählte Titel des aktuellen Chats, oder {@code null} (dann die erste Nachricht). */
    default String currentChatTitle() {
        return null;
    }
}
