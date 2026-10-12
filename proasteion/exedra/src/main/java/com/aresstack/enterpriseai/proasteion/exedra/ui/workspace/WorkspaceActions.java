package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellMode;

import java.io.File;
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

    /** Ob „Chat exportieren …“ angeboten wird (nur mit Chat-Historie). */
    default boolean canExportChats() {
        return false;
    }

    /**
     * Ein gespeicherter Chat soll in eine Datei exportiert werden („Chat exportieren …“): aus dem gespeicherten
     * Zustand, ohne den Chat zu verändern.
     *
     * @param format JSON, Markdown oder Word
     * @return {@code null} bei Erfolg, sonst ein Satz für den Nutzer
     */
    default String exportChatRequested(String chatId, File target, ChatExportFormat format) {
        return "Export ist nicht verfügbar.";
    }

    /** Der vom Nutzer gewählte Titel des aktuellen Chats, oder {@code null} (dann die erste Nachricht). */
    default String currentChatTitle() {
        return null;
    }
}
