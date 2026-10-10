package com.aresstack.enterpriseai.application.history;

import java.util.ArrayList;
import java.util.List;

/** Eine gespeicherte Nachricht (aus askai-java8 arch): Rolle, Text, Zeitpunkt und Anhänge. */
public final class ChatMessageRecord {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    /** Ein Hinweis der Anwendung (z. B. ausgefallene Wissenssuche): gespeichert, aber kein Modell-Turn. */
    public static final String ROLE_INFO = "info";

    private String role;
    private String text;
    private long createdAt;
    private List<AttachmentRecord> attachments = new ArrayList<AttachmentRecord>();

    /** Für die Deserialisierung. */
    public ChatMessageRecord() {
    }

    public ChatMessageRecord(String role, String text, long createdAt, List<AttachmentRecord> attachments) {
        this.role = role;
        this.text = text;
        this.createdAt = createdAt;
        this.attachments = attachments != null ? new ArrayList<AttachmentRecord>(attachments)
                : new ArrayList<AttachmentRecord>();
    }

    public String getRole() {
        return role;
    }

    public String getText() {
        return text != null ? text : "";
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public List<AttachmentRecord> getAttachments() {
        return attachments != null ? attachments : new ArrayList<AttachmentRecord>();
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equals(role);
    }

    public boolean isInfo() {
        return ROLE_INFO.equals(role);
    }
}
