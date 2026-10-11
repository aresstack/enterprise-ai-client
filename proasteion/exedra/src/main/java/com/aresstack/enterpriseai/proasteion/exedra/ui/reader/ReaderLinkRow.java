package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Eine Zeile mit wenigen Details, schmal genug für die Seitenleiste (Treffer der Quellensuche, Ressourcen einer Quelle,
 * Favoriten): Titel, darunter bis zu zwei Zeilen Auszug und die Fundstelle. Ein Klick öffnet den Verweis als Vorschau,
 * ein Doppelklick heftet ihn an (wie in MainframeMate). Optional rechts ein Knopf (z. B. Entfernen).
 */
public final class ReaderLinkRow extends JPanel {

    /** Was ein Klick auf die Zeile tut. */
    public interface Opener {
        void open(ReaderLink link, boolean pin);
    }

    private static final Color HOVER_WASH = ResearchUiPainter.mix(ResearchUiPalette.ACCENT_BLUE, Color.WHITE, 0.94f);
    private static final int SNIPPET_CHARS = 160;

    private final ReaderLink link;
    private boolean hovered;

    public ReaderLinkRow(final ReaderLink link, final Opener opener, JComponent trailing, ComicPalette palette) {
        super(new BorderLayout(6, 0));
        if (link == null || palette == null) {
            throw new IllegalArgumentException("link and palette must not be null");
        }
        this.link = link;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Klick: Vorschau · Doppelklick: anheften");

        JPanel text = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(0, super.getPreferredSize().height); // Breite gibt die Liste vor
            }
        };
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        JLabel title = new JLabel(link.title());
        title.setFont(ResearchUiTypography.semiBold(12.5f));
        title.setForeground(palette.getInk());
        title.setAlignmentX(LEFT_ALIGNMENT);
        text.add(title);
        if (!link.snippet().isEmpty()) {
            JTextArea snippet = new JTextArea(cut(link.snippet()));
            snippet.setLineWrap(true);
            snippet.setWrapStyleWord(true);
            snippet.setEditable(false);
            snippet.setFocusable(false);
            snippet.setHighlighter(null);
            snippet.setOpaque(false);
            snippet.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
            snippet.setFont(ResearchUiTypography.regular(11.5f));
            snippet.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
            snippet.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            snippet.setAlignmentX(LEFT_ALIGNMENT);
            text.add(snippet);
        }
        if (!link.location().isEmpty() && !link.location().equals(link.title())) {
            JLabel location = new JLabel(link.location());
            location.setFont(ResearchUiTypography.regular(10.5f));
            location.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
            location.setAlignmentX(LEFT_ALIGNMENT);
            text.add(location);
        }
        add(text, BorderLayout.CENTER);
        if (trailing != null) {
            JPanel east = new JPanel(new BorderLayout());
            east.setOpaque(false);
            east.add(trailing, BorderLayout.NORTH);
            add(east, BorderLayout.EAST);
        }
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (SwingUtilities.isLeftMouseButton(event) && opener != null) {
                    opener.open(link, event.getClickCount() >= 2);
                }
            }
        };
        addMouseListener(mouse);
        for (java.awt.Component part : text.getComponents()) {
            part.addMouseListener(mouse); // der Auszug fängt sonst die Klicks ab
        }
    }

    public ReaderLink link() {
        return link;
    }

    private static String cut(String value) {
        return value.length() <= SNIPPET_CHARS ? value : value.substring(0, SNIPPET_CHARS).trim() + "…";
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (hovered) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                ResearchUiPainter.fillRound(g2, 0, 0, getWidth(), getHeight(), ResearchUiMetrics.CHAT_ROW_RADIUS,
                        HOVER_WASH);
            } finally {
                g2.dispose();
            }
        }
        super.paintComponent(graphics);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
