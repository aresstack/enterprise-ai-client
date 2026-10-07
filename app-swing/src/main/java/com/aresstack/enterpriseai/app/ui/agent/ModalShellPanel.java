package com.aresstack.enterpriseai.app.ui.agent;

import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;

/**
 * Die Shell mit Modus-Umschaltung (AP21):
 *
 * <pre>
 * ┌──────────────────────────────┐
 * │ [ Chat ] [ Agent ]           │  ModeSwitchBar
 * ├──────────────────────────────┤
 * │ Chat-Shell  |  Agent-Shell   │  CardLayout: genau eine sichtbar
 * └──────────────────────────────┘
 * </pre>
 *
 * Beide Karten sind vollständige, voneinander unabhängige {@link ChatShellPanel}s mit eigenem Model und eigener
 * Anbindung. Umschalten wechselt nur die Karte: Eine im Hintergrund weiterlaufende Agent-Antwort schreibt in ihr
 * eigenes Model, nicht in den Chat.
 */
public final class ModalShellPanel extends JPanel implements ShellModeModel.Listener {

    private final ShellModeModel model;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final ModeSwitchBar switchBar;
    private final ChatShellPanel chatShell;
    private final ChatShellPanel agentShell;

    /** @param agentShell die Agent-Ansicht oder {@code null}, wenn kein Agent konfiguriert ist */
    public ModalShellPanel(ShellModeModel model, ChatShellPanel chatShell, ChatShellPanel agentShell,
                           ComicPalette palette) {
        super(new BorderLayout());
        if (model == null || chatShell == null || palette == null) {
            throw new IllegalArgumentException("model, chatShell and palette must not be null");
        }
        if (model.isAgentAvailable() != (agentShell != null)) {
            throw new IllegalArgumentException("agentShell must be given exactly when the agent mode is available");
        }
        this.model = model;
        this.chatShell = chatShell;
        this.agentShell = agentShell;
        this.switchBar = new ModeSwitchBar(model, palette);
        setBackground(palette.getSurface());
        deck.setBackground(palette.getSurface());
        deck.add(chatShell, ShellMode.CHAT.name());
        if (agentShell != null) {
            deck.add(agentShell, ShellMode.AGENT.name());
        }
        add(switchBar, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);
        model.addListener(this);
        modeChanged(model.getMode());
    }

    @Override
    public void modeChanged(ShellMode mode) {
        cards.show(deck, mode.name());
    }

    public ModeSwitchBar switchBar() {
        return switchBar;
    }

    public ChatShellPanel chatShell() {
        return chatShell;
    }

    /** Die Agent-Ansicht oder {@code null} ohne Agent. */
    public ChatShellPanel agentShell() {
        return agentShell;
    }

    /** Die gerade sichtbare Shell. */
    public JComponent visibleShell() {
        for (Component card : deck.getComponents()) {
            if (card.isVisible()) {
                return (JComponent) card;
            }
        }
        return chatShell;
    }
}
