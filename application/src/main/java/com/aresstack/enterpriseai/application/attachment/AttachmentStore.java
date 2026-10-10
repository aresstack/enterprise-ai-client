package com.aresstack.enterpriseai.application.attachment;

import com.aresstack.enterpriseai.domain.chat.ChatConversationId;

import java.nio.file.Path;
import java.util.List;

/** Port: die lokale Ablage der Anhänge je Unterhaltung. */
public interface AttachmentStore {

    /**
     * Legt eine Kopie von {@code source} unter einer neuen Kennung ab.
     *
     * @throws AttachmentException wenn die Datei nicht lesbar, zu groß oder die Ablage nicht beschreibbar ist
     */
    Attachment add(ChatConversationId conversation, Path source);

    /** Die Anhänge der Unterhaltung in Ablagereihenfolge; leer, wenn es keine gibt. */
    List<Attachment> list(ChatConversationId conversation);

    /** @throws AttachmentException wenn der Anhang unbekannt oder nicht lesbar ist */
    byte[] read(ChatConversationId conversation, String attachmentId);

    /** Löscht alle Anhänge der Unterhaltung (beim Schließen); unbekannte Unterhaltungen sind kein Fehler. */
    void delete(ChatConversationId conversation);

    /** Löscht alle Anhänge aller Unterhaltungen (beim Start: frühere Unterhaltungen sind nicht mehr erreichbar). */
    void deleteAll();
}
