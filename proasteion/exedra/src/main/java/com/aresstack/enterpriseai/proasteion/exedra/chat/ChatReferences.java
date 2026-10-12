package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.attachment.ReadAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.attachment.SearchAttachmentTool;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatBookmark;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;

import java.util.List;

/**
 * Wie das Modell von den ausdrücklichen Verweisen einer Nutzernachricht erfährt (Teil des System-Kontexts dieses
 * Turns, nie des Verlaufs): Art, Titel und stabile Kennung jeder Ressource und mit welchem Werkzeug es sie liest.
 * Kein Inhalt wird kopiert, keine Datei erneut abgelegt.
 */
final class ChatReferences {

    private ChatReferences() {
    }

    /** Der Hinweis für das Modell oder {@code null} ohne Verweise. */
    static String context(List<ChatResource> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        StringBuilder context = new StringBuilder("Die aktuelle Nachricht des Nutzers bezieht sich ausdrücklich auf "
                + "diese Ressourcen des Chats. Ihr Inhalt steht nicht im Verlauf; lies sie bei Bedarf mit dem genannten "
                + "Werkzeug und der angegebenen id:");
        for (ChatResource reference : references) {
            context.append("\n- ");
            switch (reference.kind()) {
                case FILE:
                    context.append("Datei \"").append(reference.title()).append("\", id = ").append(reference.id())
                            .append(" (").append(ReadAttachmentTool.NAME).append(" oder ")
                            .append(SearchAttachmentTool.NAME).append(')');
                    break;
                case PAGE:
                    context.append("Seite \"").append(reference.title()).append("\", id = ").append(reference.id())
                            .append(" (").append(ChatBookmark.READ_TOOL).append(')');
                    break;
                default:
                    context.append("Quelle \"").append(reference.title()).append("\", id = ").append(reference.id())
                            .append(" (").append(ChatBookmark.READ_TOOL).append(')');
                    break;
            }
        }
        return context.toString();
    }

    /** Ob ein Verweis die Wissens-Werkzeuge braucht (Seite oder Quelle). */
    static boolean needsKnowledge(List<ChatResource> references) {
        for (ChatResource reference : references) {
            if (reference.kind() != ChatResource.Kind.FILE) {
                return true;
            }
        }
        return false;
    }
}
