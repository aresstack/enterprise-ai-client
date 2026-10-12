package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Eine Chat-Ansicht (Chat oder Agent) nach askai-java8 (arch): ruhige Fläche ohne Titelplakette, der Verlauf
 * als Sprechblasen, darunter die stille Statuszeile der Wissensbasis und die eine Composer-Fläche.
 *
 * <pre>
 * ┌──────────────────────────────┐
 * │ Chat Transcript              │  ChatTranscriptPanel
 * ├──────────────────────────────┤
 * │ Indexierung: 3 von 10 …  [x] │  KnowledgeStatusBar (nur sichtbar mit Text)
 * │ ╭──────────────────────────╮ │
 * │ │ Nachricht…               │ │  ChatComposerPanel (eine abgerundete Fläche)
 * │ │ [RAG]            [Senden]│ │
 * │ ╰──────────────────────────╯ │
 * │           © 2026 AresStack   │  CopyrightFooter (bündig mit dem Composer)
 * └──────────────────────────────┘
 * </pre>
 *
 * Alle Teile hängen nur am {@link ChatShellModel}, am {@link KnowledgeStatusModel} und an
 * {@link ChatShellActions}; keine Infrastruktur. Den Rahmen (Hamburger, Modus-Pill, Drawer) stellt die
 * Arbeitsfläche in {@code app.ui.workspace}.
 */
public final class ChatShellPanel extends JPanel {

    private final ChatShellModel model;
    private final ChatTranscriptPanel transcript;
    private final KnowledgeStatusBar statusBar;
    private final ChatComposerPanel composer;
    /** Platz über dem Composer, z. B. die Phasenleiste des Recherche-Workflows; leer unsichtbar. */
    private final JPanel composerAccessory = new JPanel(new BorderLayout());

    /** Shell ohne Statuszeile zur Wissensbasis (z. B. die Agent-Ansicht). */
    public ChatShellPanel(ChatShellModel model, ChatShellActions actions,
                          ComicPalette comicPalette, BubblePalette bubblePalette) {
        this(model, actions, new KnowledgeStatusModel(), comicPalette, bubblePalette);
    }

    /** @param knowledgeStatus das Model der Statuszeile; die Anbindung der Indexierung schreibt hinein */
    public ChatShellPanel(ChatShellModel model, ChatShellActions actions, KnowledgeStatusModel knowledgeStatus,
                          ComicPalette comicPalette, BubblePalette bubblePalette) {
        super(new BorderLayout());
        if (knowledgeStatus == null) {
            throw new IllegalArgumentException("knowledgeStatus must not be null");
        }
        this.model = model;
        this.transcript = new ChatTranscriptPanel(model, comicPalette, bubblePalette);
        this.statusBar = new KnowledgeStatusBar(knowledgeStatus, comicPalette);
        this.composer = new ChatComposerPanel(model, actions, comicPalette);
        // Markierter Text einer Antwort → „Zitieren“ → Zitat-Chip im Composer.
        this.transcript.setQuoteHandler(composer::addQuote);
        setBackground(comicPalette.getSurface());

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        JPanel composerWrap = new JPanel(new BorderLayout());
        composerWrap.setOpaque(false);
        composerWrap.setBorder(BorderFactory.createEmptyBorder(4, CopyrightFooter.SIDE_MARGIN,
                CopyrightFooter.BOTTOM_MARGIN, CopyrightFooter.SIDE_MARGIN));
        composerWrap.add(composer, BorderLayout.CENTER);
        CopyrightFooter footer = new CopyrightFooter();
        composerWrap.add(footer, BorderLayout.SOUTH);
        composerAccessory.setOpaque(false);
        composerAccessory.setBorder(BorderFactory.createEmptyBorder(0, CopyrightFooter.SIDE_MARGIN, 0,
                CopyrightFooter.SIDE_MARGIN));
        composerAccessory.setVisible(false);
        JPanel above = new JPanel(new BorderLayout());
        above.setOpaque(false);
        above.add(composerAccessory, BorderLayout.NORTH);
        above.add(statusBar, BorderLayout.SOUTH);
        south.add(above, BorderLayout.NORTH);
        south.add(composerWrap, BorderLayout.CENTER);

        add(transcript, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    /** Das Model dieser Ansicht (die Arbeitsfläche liest Titel und Aktivität daraus). */
    public ChatShellModel model() {
        return model;
    }

    public ChatTranscriptPanel transcript() {
        return transcript;
    }

    public ChatComposerPanel composer() {
        return composer;
    }

    /** Setzt die Fläche über dem Composer ({@code null} entfernt sie). Nur auf dem EDT. */
    public void setComposerAccessory(JComponent accessory) {
        composerAccessory.removeAll();
        if (accessory != null) {
            composerAccessory.add(accessory, BorderLayout.CENTER);
        }
        composerAccessory.setVisible(accessory != null);
        revalidate();
        repaint();
    }

    /** Die Statuszeile zur Wissensbasis; ohne Text unsichtbar. */
    public KnowledgeStatusBar statusBar() {
        return statusBar;
    }
}
