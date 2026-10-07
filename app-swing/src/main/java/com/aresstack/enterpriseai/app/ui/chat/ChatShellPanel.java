package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Die App-Shell aus AP4:
 *
 * <pre>
 * ┌──────────────────────────────┐
 * │ Chat                         │  ChatTitleBar
 * ├──────────────────────────────┤
 * │ Chat Transcript              │  ChatTranscriptPanel
 * ├──────────────────────────────┤
 * │ [ RAG ]  Nachricht... [Send] │  ChatComposerPanel
 * └──────────────────────────────┘
 * </pre>
 *
 * Alle Teile hängen nur am {@link ChatShellModel} und an {@link ChatShellActions}; keine Infrastruktur.
 */
public final class ChatShellPanel extends JPanel {

    private final ChatTitleBar titleBar;
    private final ChatTranscriptPanel transcript;
    private final ChatComposerPanel composer;

    public ChatShellPanel(ChatShellModel model, ChatShellActions actions,
                          ComicPalette comicPalette, BubblePalette bubblePalette) {
        super(new BorderLayout());
        this.titleBar = new ChatTitleBar("Chat", comicPalette);
        this.transcript = new ChatTranscriptPanel(model, comicPalette, bubblePalette);
        this.composer = new ChatComposerPanel(model, actions, comicPalette);
        setBackground(comicPalette.getSurface());

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(comicPalette.getSurface());
        south.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, comicPalette.getInk()),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        south.add(composer, BorderLayout.CENTER);

        add(titleBar, BorderLayout.NORTH);
        add(transcript, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    public ChatTitleBar titleBar() {
        return titleBar;
    }

    public ChatTranscriptPanel transcript() {
        return transcript;
    }

    public ChatComposerPanel composer() {
        return composer;
    }
}
