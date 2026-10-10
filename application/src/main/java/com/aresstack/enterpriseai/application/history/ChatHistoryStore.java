package com.aresstack.enterpriseai.application.history;

import java.util.List;

/**
 * Port: die gespeicherten Chats (aus askai-java8 arch, {@code ChatHistoryStore}). Ein Chat und seine Anhänge
 * gehören zusammen: {@link #delete} entfernt auch die Anhänge der Unterhaltung. Lese- und Schreibfehler stören die
 * Oberfläche nicht: Lesen liefert dann nichts, Schreiben wird übergangen.
 */
public interface ChatHistoryStore {

    /** Speichert (oder überschreibt) einen Chat; ein leerer Chat wird stattdessen gelöscht. */
    void save(ChatRecord record);

    /** Der Chat mit dieser Kennung oder {@code null}, wenn es ihn nicht gibt oder er unlesbar ist. */
    ChatRecord load(String chatId);

    /** Alle gespeicherten, nicht leeren Chats, zuletzt geänderte zuerst. */
    List<ChatRecord> list();

    /** Löscht den Chat samt Anhängen; unbekannte Kennungen sind kein Fehler. */
    void delete(String chatId);
}
