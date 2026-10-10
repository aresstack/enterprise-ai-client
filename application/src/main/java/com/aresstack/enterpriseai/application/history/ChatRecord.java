package com.aresstack.enterpriseai.application.history;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein gespeicherter Chat (aus askai-java8 arch), geschlüsselt über die Kennung seiner Unterhaltung: Titel,
 * Zeitpunkte und die geordneten Nachrichten.
 */
public final class ChatRecord {

    private String id;
    private String title;
    /** Vom Nutzer umbenannt: der Titel folgt dann nicht mehr der ersten Nachricht. */
    private boolean titleEdited;
    private long createdAt;
    private long modifiedAt;
    private List<ChatMessageRecord> messages = new ArrayList<ChatMessageRecord>();

    /** Für die Deserialisierung. */
    public ChatRecord() {
    }

    public ChatRecord(String id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.modifiedAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isTitleEdited() {
        return titleEdited;
    }

    /** Setzt einen vom Nutzer gewählten Titel. */
    public void rename(String newTitle) {
        this.title = newTitle;
        this.titleEdited = true;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(long modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public List<ChatMessageRecord> getMessages() {
        if (messages == null) {
            messages = new ArrayList<ChatMessageRecord>();
        }
        return messages;
    }

    public boolean isEmpty() {
        return getMessages().isEmpty();
    }

    /** Wie viele Anhänge alle Nachrichten zusammen haben. */
    public int attachmentCount() {
        int count = 0;
        for (ChatMessageRecord message : getMessages()) {
            count += message.getAttachments().size();
        }
        return count;
    }
}
