package com.aresstack.enterpriseai.domain.chat;

/**
 * Eine unveränderliche Chat-Nachricht: Rolle plus Textinhalt.
 *
 * <p>{@link #toString()} gibt bewusst nur Rolle und Länge aus, damit Nachrichteninhalte nicht
 * versehentlich in Logs landen.
 */
public final class ChatMessage {

    private final ChatRole role;
    private final String content;

    public ChatMessage(ChatRole role, String content) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content);
    }

    public static ChatMessage developer(String content) {
        return new ChatMessage(ChatRole.DEVELOPER, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content);
    }

    public ChatRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ChatMessage)) {
            return false;
        }
        ChatMessage that = (ChatMessage) other;
        return role == that.role && content.equals(that.content);
    }

    @Override
    public int hashCode() {
        return 31 * role.hashCode() + content.hashCode();
    }

    @Override
    public String toString() {
        return "ChatMessage[" + role + ", " + content.length() + " chars]";
    }
}
