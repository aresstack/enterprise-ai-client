package com.aresstack.enterpriseai.app.ui.chat;

import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthBoundedBubble;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Die ein- und ausklappbare Quellenliste unter einer Assistentenantwort (AP22): eine Comic-Platte mit
 * Petrol-Streifen (Farbe des Assistenten), ein Schalter {@code Quellen (n)} und, aufgeklappt, je Quelle eine
 * Zeile mit Nummer, Titel und Überschrift sowie darunter Ort, Stand, Score und die Ränge beider Suchpfade.
 *
 * <p>Ort und Details stehen in einem markierbaren Textfeld, damit sich eine Adresse kopieren lässt. Das Layout
 * ist bewusst von Hand gesetzt ({@link WidthAwareHeight}): die Höhe hängt von der Breite ab, in der die
 * Transcript-Zeile die Platte setzt, und wird deterministisch für genau diese Breite berechnet (wie bei der
 * Sprechblase). Vorbild ist die Aktionszeile unter einer Assistentenkarte in askai-java8
 * ({@code BubbleTranscriptPanel.appendActionButtons}), hier mit Inhalt statt Knöpfen.
 */
public final class SourceListPanel extends ComicSectionPanel implements WidthBoundedBubble, WidthAwareHeight {

    static final String TOGGLE_PREFIX = "Quellen";
    private static final int MINIMUM_WIDTH = 160;
    private static final int NATURAL_WIDTH_CAP = 720;
    private static final int ROW_GAP = 8;
    private static final int LINE_GAP = 2;

    private final List<SourceReference> sources;
    private final ComicToggleButton toggle;
    private final List<JLabel> titles = new ArrayList<JLabel>();
    private final List<JTextArea> details = new ArrayList<JTextArea>();
    private boolean expanded;

    public SourceListPanel(List<SourceReference> sources, ComicPalette palette, BubblePalette bubblePalette) {
        super(palette);
        if (sources == null || sources.isEmpty() || bubblePalette == null) {
            throw new IllegalArgumentException("sources must not be empty and bubblePalette must not be null");
        }
        this.sources = Collections.unmodifiableList(new ArrayList<SourceReference>(sources));
        this.toggle = new ComicToggleButton(toggleText(sources.size()), palette);
        setLayout(null);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 12));
        setAccentStripe(palette.getAgentPetrol());
        toggle.setToolTipText("Quellen der Antwort ein- oder ausblenden");
        toggle.addActionListener(event -> setExpanded(toggle.isSelected()));
        add(toggle);
        for (SourceReference source : this.sources) {
            JLabel title = new JLabel(titleLine(source));
            title.setFont(title.getFont().deriveFont(Font.BOLD, Math.max(11f, title.getFont().getSize2D() - 1f)));
            title.setForeground(palette.getInk());
            title.setVisible(false);
            titles.add(title);
            add(title);

            JTextArea detail = new JTextArea(detailLine(source));
            detail.setFont(detail.getFont().deriveFont(Font.PLAIN, Math.max(11f, detail.getFont().getSize2D() - 2f)));
            detail.setForeground(bubblePalette.getInfoForeground());
            detail.setOpaque(false);
            detail.setEditable(false);
            detail.setLineWrap(true);
            detail.setWrapStyleWord(true);
            detail.setBorder(BorderFactory.createEmptyBorder());
            detail.setVisible(false);
            details.add(detail);
            add(detail);
        }
    }

    public List<SourceReference> getSources() {
        return sources;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expand) {
        if (expanded == expand) {
            return;
        }
        expanded = expand;
        if (toggle.isSelected() != expand) {
            toggle.setSelected(expand);
        }
        for (JLabel title : titles) {
            title.setVisible(expand);
        }
        for (JTextArea detail : details) {
            detail.setVisible(expand);
        }
        revalidate();
        repaint();
    }

    /** Der Schalter {@code Quellen (n)}. */
    public ComicToggleButton toggle() {
        return toggle;
    }

    /** Erste Zeile einer Quelle: {@code [1] Titel – Überschrift}. */
    public static String titleLine(SourceReference source) {
        String line = "[" + source.getNumber() + "] " + source.getTitle();
        return source.getHeading().isEmpty() ? line : line + " – " + source.getHeading();
    }

    /** Zweite Zeile: Ort, Stand, Score und Ränge, durch Punkte getrennt. */
    public static String detailLine(SourceReference source) {
        StringBuilder line = new StringBuilder("Ort: ").append(source.getLocation());
        if (!source.getRevision().isEmpty()) {
            line.append("  ·  Stand: ").append(source.getRevision());
        }
        line.append("  ·  Score ").append(String.format(Locale.ROOT, "%.3f", source.getScore()));
        if (source.getKeywordRank() > 0) {
            line.append("  ·  Volltext #").append(source.getKeywordRank());
        }
        if (source.getSemanticRank() > 0) {
            line.append("  ·  Semantik #").append(source.getSemanticRank());
        }
        return line.toString();
    }

    static String toggleText(int count) {
        return TOGGLE_PREFIX + " (" + count + ")";
    }

    @Override
    public int preferredWidthWithin(int limit) {
        int natural = naturalWidth();
        int preferred = Math.max(MINIMUM_WIDTH, Math.min(NATURAL_WIDTH_CAP, natural));
        return limit > 0 ? Math.max(Math.min(limit, MINIMUM_WIDTH), Math.min(limit, preferred)) : preferred;
    }

    @Override
    public int preferredHeightForWidth(int width) {
        Insets insets = getInsets();
        int inner = Math.max(24, width - insets.left - insets.right);
        int height = insets.top + toggle.getPreferredSize().height;
        if (expanded) {
            for (int i = 0; i < titles.size(); i++) {
                height += ROW_GAP + titles.get(i).getPreferredSize().height + LINE_GAP + detailHeight(details.get(i), inner);
            }
        }
        return height + insets.bottom;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = preferredWidthWithin(0);
        return new Dimension(width, preferredHeightForWidth(width));
    }

    @Override
    public void doLayout() {
        Insets insets = getInsets();
        int inner = Math.max(24, getWidth() - insets.left - insets.right);
        int y = insets.top;
        Dimension toggleSize = toggle.getPreferredSize();
        toggle.setBounds(insets.left, y, Math.min(inner, toggleSize.width), toggleSize.height);
        y += toggleSize.height;
        for (int i = 0; i < titles.size(); i++) {
            JLabel title = titles.get(i);
            JTextArea detail = details.get(i);
            if (!expanded) {
                title.setBounds(0, 0, 0, 0);
                detail.setBounds(0, 0, 0, 0);
                continue;
            }
            y += ROW_GAP;
            int titleHeight = title.getPreferredSize().height;
            title.setBounds(insets.left, y, inner, titleHeight);
            y += titleHeight + LINE_GAP;
            int detailHeight = detailHeight(detail, inner);
            detail.setBounds(insets.left, y, inner, detailHeight);
            y += detailHeight;
        }
    }

    private int naturalWidth() {
        Insets insets = getInsets();
        int width = toggle.getPreferredSize().width;
        if (expanded) {
            for (int i = 0; i < titles.size(); i++) {
                width = Math.max(width, titles.get(i).getPreferredSize().width);
                JTextArea detail = details.get(i);
                FontMetrics metrics = detail.getFontMetrics(detail.getFont());
                width = Math.max(width, metrics.stringWidth(detail.getText()) + 8);
            }
        }
        return width + insets.left + insets.right;
    }

    private static int detailHeight(JTextArea detail, int width) {
        detail.setSize(new Dimension(width, Short.MAX_VALUE));
        return detail.getPreferredSize().height;
    }
}
