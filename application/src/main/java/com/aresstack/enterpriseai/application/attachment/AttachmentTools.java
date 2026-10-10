package com.aresstack.enterpriseai.application.attachment;

import com.aresstack.enterpriseai.application.tool.AiTool;
import com.aresstack.enterpriseai.application.tool.ToolRegistry;
import com.aresstack.enterpriseai.domain.chat.ChatConversationId;

import java.util.Arrays;
import java.util.List;

/**
 * Die Anhang-Werkzeuge einer Unterhaltung für eine Anfrage: {@code read_attachment} und {@code search_attachment}
 * in einer {@link ToolRegistry}, dazu der System-Anteil, der dem Modell die vorhandenen Anhänge nennt (nur Kennung,
 * Name und Größe, nie den Inhalt).
 */
public final class AttachmentTools {

    private final AttachmentTexts texts;

    public AttachmentTools(AttachmentStore store, AttachmentTextExtractor extractor, ChatConversationId conversation) {
        if (store == null || extractor == null || conversation == null) {
            throw new IllegalArgumentException("store, extractor and conversation must not be null");
        }
        this.texts = new AttachmentTexts(store, extractor, conversation);
    }

    public ToolRegistry registry() {
        return new ToolRegistry(Arrays.<AiTool>asList(new ReadAttachmentTool(texts), new SearchAttachmentTool(texts)));
    }

    /** Hinweis für das Modell auf die Anhänge oder {@code null}, wenn die Unterhaltung keine hat. */
    public String context() {
        List<Attachment> attachments = texts.list();
        if (attachments.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder("Der Nutzer hat in dieser Unterhaltung Dateien angehängt. Ihr Inhalt "
                + "steht nicht im Verlauf; lies sie bei Bedarf mit dem Werkzeug " + ReadAttachmentTool.NAME
                + " oder durchsuche sie mit " + SearchAttachmentTool.NAME + ":");
        for (Attachment attachment : attachments) {
            context.append("\n- ").append(attachment.id()).append(": ").append(attachment.fileName()).append(" (")
                    .append(size(attachment.sizeBytes())).append(')');
        }
        return context.toString();
    }

    private static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        return (bytes / (1024 * 1024)) + " MB";
    }
}
