package com.aresstack.enterpriseai.application.attachment;

import com.aresstack.enterpriseai.domain.chat.ChatConversationId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Findet Anhänge einer Unterhaltung und hält ihren extrahierten Text für die Dauer einer Anfrage. */
final class AttachmentTexts {

    private final AttachmentStore store;
    private final AttachmentTextExtractor extractor;
    private final ChatConversationId conversation;
    private final Map<String, String> texts = new HashMap<String, String>();

    AttachmentTexts(AttachmentStore store, AttachmentTextExtractor extractor, ChatConversationId conversation) {
        this.store = store;
        this.extractor = extractor;
        this.conversation = conversation;
    }

    List<Attachment> list() {
        return store.list(conversation);
    }

    Attachment find(String attachmentId) {
        for (Attachment attachment : list()) {
            if (attachment.id().equals(attachmentId) || attachment.fileName().equals(attachmentId)) {
                return attachment;
            }
        }
        throw new AttachmentException("Unbekannter Anhang " + attachmentId + "; vorhanden: " + ids());
    }

    synchronized String text(Attachment attachment) {
        String text = texts.get(attachment.id());
        if (text == null) {
            text = extractor.extractText(attachment.fileName(), store.read(conversation, attachment.id()));
            texts.put(attachment.id(), text == null ? "" : text);
        }
        return texts.get(attachment.id());
    }

    private String ids() {
        StringBuilder ids = new StringBuilder();
        for (Attachment attachment : list()) {
            if (ids.length() > 0) {
                ids.append(", ");
            }
            ids.append(attachment.id()).append(" (").append(attachment.fileName()).append(')');
        }
        return ids.length() == 0 ? "keine" : ids.toString();
    }
}
