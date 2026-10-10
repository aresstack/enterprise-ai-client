package com.aresstack.enterpriseai.app.ui.chat;

import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Die Anhänge einer gesendeten Nutzernachricht als Chips direkt unter ihrer Blase (rechtsbündig), nach dem Senden
 * und nach dem Laden eines gespeicherten Chats — dieselben Chips wie im Composer, ohne ✕.
 */
final class AttachmentChipsPanel extends JPanel {

    AttachmentChipsPanel(List<String> fileNames) {
        super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        setOpaque(false);
        for (String fileName : fileNames) {
            add(ChatAttachmentStrip.chip(fileName, "Anhang: " + fileName, null));
        }
    }
}
