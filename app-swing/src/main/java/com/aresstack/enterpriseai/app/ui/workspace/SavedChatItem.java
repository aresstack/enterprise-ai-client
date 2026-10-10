package com.aresstack.enterpriseai.app.ui.workspace;

/** Ein gespeicherter Chat, wie der Drawer ihn zeigt: Kennung, Titel, letzte Änderung und Umfang. */
public final class SavedChatItem {

    private final String id;
    private final String title;
    private final long modifiedAtMillis;
    private final int messageCount;
    private final int attachmentCount;

    public SavedChatItem(String id, String title, long modifiedAtMillis, int messageCount, int attachmentCount) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        this.id = id;
        this.title = title == null || title.trim().isEmpty() ? "(ohne Titel)" : title.trim();
        this.modifiedAtMillis = modifiedAtMillis;
        this.messageCount = messageCount;
        this.attachmentCount = attachmentCount;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public long modifiedAtMillis() {
        return modifiedAtMillis;
    }

    public int messageCount() {
        return messageCount;
    }

    public int attachmentCount() {
        return attachmentCount;
    }
}
