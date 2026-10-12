package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.control.ResearchPillButton;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Die gelben Anschlussfragen zu einem Zitat über dem Editor (dieselbe Optik wie die Suchvorschläge des Agenten):
 * je Vorschlag eine Pille; ein Klick reicht ihren Text an den Composer weiter, gesendet wird nie. Ohne Vorschläge
 * unsichtbar.
 */
final class QuoteSuggestionStrip extends JPanel {

    private static final int PILL_HEIGHT = 24;
    private static final int PILL_RADIUS = 12;
    private static final int PILL_PADDING = 10;
    private static final float PILL_FONT_SIZE = 11.5f;

    private final Consumer<String> onChosen;
    private final List<String> suggestions = new ArrayList<String>();

    QuoteSuggestionStrip(Consumer<String> onChosen) {
        this.onChosen = onChosen;
        setOpaque(false);
        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 3));
        setName("composer.quoteSuggestions");
        setVisible(false);
    }

    void show(List<String> values) {
        suggestions.clear();
        if (values != null) {
            suggestions.addAll(values);
        }
        removeAll();
        for (final String suggestion : suggestions) {
            add(pill(suggestion));
        }
        setVisible(!suggestions.isEmpty());
        revalidate();
        repaint();
    }

    void clear() {
        if (!suggestions.isEmpty() || isVisible()) {
            show(Collections.<String>emptyList());
        }
    }

    List<String> suggestions() {
        return Collections.unmodifiableList(new ArrayList<String>(suggestions));
    }

    private ResearchPillButton pill(final String suggestion) {
        Color surface = ResearchUiPalette.TAG_SUGGESTION_SURFACE;
        Color text = ResearchUiPalette.TAG_SUGGESTION_TEXT;
        ResearchPillButton pill = new ResearchPillButton(suggestion, PILL_HEIGHT, PILL_RADIUS, PILL_PADDING);
        pill.setFont(pill.getFont().deriveFont(Font.BOLD, PILL_FONT_SIZE));
        pill.setFills(surface, surface.brighter(), surface.darker());
        pill.setForegrounds(text, text, text);
        pill.setName("composer.quoteSuggestion");
        pill.setToolTipText("In das Eingabefeld übernehmen");
        pill.setFocusable(false);
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pill.addActionListener(event -> onChosen.accept(suggestion));
        return pill;
    }
}
