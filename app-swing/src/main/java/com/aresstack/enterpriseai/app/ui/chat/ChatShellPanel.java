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
 * │ Indexierung: 3 von 10 … [X]  │  KnowledgeStatusBar (AP22, nur sichtbar mit Text)
 * ├──────────────────────────────┤
 * │ [ RAG ]  Nachricht... [Send] │  ChatComposerPanel
 * └──────────────────────────────┘
 * </pre>
 *
 * Alle Teile hängen nur am {@link ChatShellModel}, am {@link KnowledgeStatusModel} und an
 * {@link ChatShellActions}; keine Infrastruktur.
 */
public final class ChatShellPanel extends JPanel {

    private final ChatTitleBar titleBar;
    private final ChatTranscriptPanel transcript;
    private final KnowledgeStatusBar statusBar;
    private final ChatComposerPanel composer;

    /** Shell ohne Statuszeile zur Wissensbasis (z. B. die Agent-Ansicht). */
    public ChatShellPanel(ChatShellModel model, ChatShellActions actions,
                          ComicPalette comicPalette, BubblePalette bubblePalette) {
        this(model, actions, new KnowledgeStatusModel(), comicPalette, bubblePalette);
    }

    /** @param knowledgeStatus das Model der Statuszeile; die Anbindung der Indexierung schreibt hinein (AP22) */
    public ChatShellPanel(ChatShellModel model, ChatShellActions actions, KnowledgeStatusModel knowledgeStatus,
                          ComicPalette comicPalette, BubblePalette bubblePalette) {
        super(new BorderLayout());
        if (knowledgeStatus == null) {
            throw new IllegalArgumentException("knowledgeStatus must not be null");
        }
        this.titleBar = new ChatTitleBar("Chat", comicPalette);
        this.transcript = new ChatTranscriptPanel(model, comicPalette, bubblePalette);
        this.statusBar = new KnowledgeStatusBar(knowledgeStatus, comicPalette);
        this.composer = new ChatComposerPanel(model, actions, comicPalette);
        setBackground(comicPalette.getSurface());

        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(comicPalette.getSurface());
        JPanel composerPlate = new JPanel(new BorderLayout());
        composerPlate.setBackground(comicPalette.getSurface());
        composerPlate.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, comicPalette.getInk()),
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));
        composerPlate.add(composer, BorderLayout.CENTER);
        south.add(statusBar, BorderLayout.NORTH);
        south.add(composerPlate, BorderLayout.CENTER);

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

    /** Die Statuszeile zur Wissensbasis (AP22); ohne Text unsichtbar. */
    public KnowledgeStatusBar statusBar() {
        return statusBar;
    }
}
