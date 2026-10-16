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

    /**
     * „+ Neuer Chat“ im Drawer, für die gerade sichtbare Ansicht.
     *
     * @return {@code false}, wenn sich gerade kein neuer Chat beginnen lässt (der bisherige bleibt offen)
     */
    boolean newChatRequested(ShellMode mode);

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

    // ------------------------------------------------------------------ chat projects (#143)

    /** All chat projects, depth first in tree order; empty without chat projects. */
    default List<ChatProjectItem> chatProjects() {
        return Collections.emptyList();
    }

    /** The project of a chat, or {@code null} for a chat without project. */
    default String projectOfChat(String chatId) {
        return null;
    }

    /**
     * Moves a chat into a project ({@code null} = "Chats ohne Projekt"). History and attachments stay; only new
     * questions see the new project context.
     *
     * @return {@code null} on success, otherwise a sentence for the user (e.g. while an answer is running)
     */
    default String moveChatRequested(String chatId, String projectId) {
        return "Projekte sind nicht verfügbar.";
    }

    /** Whether a project may be moved below {@code parentId} ({@code null} = top level); never into itself. */
    default boolean canMoveProject(String projectId, String parentId) {
        return false;
    }

    /** Moves a project below {@code parentId} ({@code null} = top level), as last child. */
    default boolean moveProjectRequested(String projectId, String parentId) {
        return false;
    }

    /** Creates a project; {@code null} = not created. */
    default String createProjectRequested(String name, String parentId) {
        return null;
    }

    default void renameProjectRequested(String projectId, String name) {
    }

    /** Replaces the instructions of a project; they apply to the next question. */
    default void updateProjectInstructionsRequested(String projectId, String instructions) {
    }

    /** How many chats the project and its sub-projects contain (what deleting it would move out). */
    default int chatsInProject(String projectId) {
        return 0;
    }

    /** Deletes a project and its sub-projects; their chats become chats without project, none is deleted. */
    default void deleteProjectRequested(String projectId) {
    }

    /**
     * Starts a new chat that belongs to the project.
     *
     * @return {@code false} when no new chat can start right now
     */
    default boolean newChatInProjectRequested(String projectId) {
        return false;
    }
}
