package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Ein ausdrücklicher Verweis einer Nutzernachricht auf eine Ressource, die schon zum Chat gehört (aus der
 * Ressourcenübersicht im Composer gewählt): Art, stabile Kennung und Titel zur Anzeige. Fachlich etwas anderes als
 * die Datei selbst ({@link AttachmentRecord}), eine hinzugefügte Seite ({@link BookmarkRecord}) oder eine von RAG
 * verwendete Quelle ({@link SourceRecord}): der Verweis sagt nur „diese Frage bezieht sich ausdrücklich darauf“. Nie
 * ein Inhalt.
 */
public final class ResourceReferenceRecord {

    /** Eine Datei des Chats; Kennung = Attachment-ID im Ablageordner des Chats. */
    public static final String KIND_ATTACHMENT = "attachment";
    /** Eine hinzugefügte Seite; Kennung = Bookmark. */
    public static final String KIND_PAGE = "page";
    /** Eine von RAG verwendete Quelle; Kennung = Resource-ID aus dem Index. */
    public static final String KIND_SOURCE = "source";

    private String kind;
    private String id;
    private String title;

    /** Für die Deserialisierung. */
    public ResourceReferenceRecord() {
    }

    public ResourceReferenceRecord(String kind, String id, String title) {
        this.kind = kind;
        this.id = id;
        this.title = title;
    }

    public String getKind() {
        return kind != null ? kind : "";
    }

    public String getId() {
        return id != null ? id : "";
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    /** Ein lesbarer Verweis: bekannte Art und nicht leere Kennung. */
    public boolean isValid() {
        return !getId().trim().isEmpty() && (KIND_ATTACHMENT.equals(kind) || KIND_PAGE.equals(kind)
                || KIND_SOURCE.equals(kind));
    }
}
