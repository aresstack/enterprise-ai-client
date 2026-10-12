package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Die Anhänge einer gesendeten Nutzernachricht als Chips direkt unter ihrer Blase (rechtsbündig), nach dem Senden
 * und nach dem Laden eines gespeicherten Chats — dieselben Chips wie im Composer, ohne ✕; Seiten (Bookmarks) mit
 * ihrem Titel, ausdrückliche Verweise mit dem Symbol ihrer Art.
 */
final class AttachmentChipsPanel extends JPanel {

    AttachmentChipsPanel(List<String> fileNames, List<ChatPage> pages) {
        this(fileNames, pages, java.util.Collections.<ChatResource>emptyList());
    }

    /** Wie oben, dazu die ausdrücklichen Verweise auf Ressourcen des Chats (mit dem Symbol ihrer Art). */
    AttachmentChipsPanel(List<String> fileNames, List<ChatPage> pages, List<ChatResource> references) {
        super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        setOpaque(false);
        for (String fileName : fileNames) {
            add(ChatAttachmentStrip.chip(fileName, "Anhang: " + fileName, null));
        }
        for (ChatPage page : pages) {
            add(ChatAttachmentStrip.chip(page.title(), ChatAttachmentStrip.pageTooltip(page), null));
        }
        for (ChatResource reference : references) {
            add(ChatAttachmentStrip.referenceChip(reference, null));
        }
    }
}
