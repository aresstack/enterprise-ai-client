package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.text.DefaultFormatter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The browser popup under the globe's arrow (#141), drawn as a comic speech bubble pointing up to the arrow: one
 * compact row per value (tool rounds, parallel tabs, random delay before a tab), each a field for any value and a
 * slider over the usual range, kept in sync; every change goes to the row's listener at once.
 */
final class BrowserBudgetPopup extends JPopupMenu {

    static final String TITLE = "Werkzeugrunden je Antwort mit Browser";
    static final String TABS_TITLE = "Parallele Tabs";
    static final String DELAY_TITLE = "Zufällige Verzögerung je Tab (max. ms)";
    /** Height of the bubble's pointer above the content. */
    private static final int POINTER = 8;

    /** The definition of one row. */
    static final class Row {

        final String title;
        final int value;
        final int min;
        final int max;
        final int sliderMin;
        final int sliderMax;
        final IntConsumer listener;

        Row(String title, int value, int min, int max, int sliderMin, int sliderMax, IntConsumer listener) {
            this.title = title;
            this.value = value;
            this.min = min;
            this.max = max;
            this.sliderMin = sliderMin;
            this.sliderMax = sliderMax;
            this.listener = listener == null ? v -> { } : listener;
        }
    }

    private final List<SpinnerNumberModel> numbers = new ArrayList<SpinnerNumberModel>();
    private final List<JSlider> sliders = new ArrayList<JSlider>();

    BrowserBudgetPopup(int value, int min, int max, int sliderMin, int sliderMax, IntConsumer listener) {
        this(ComicPalette.defaultPalette(), java.util.Collections.singletonList(
                new Row(TITLE, value, min, max, sliderMin, sliderMax, listener)));
    }

    BrowserBudgetPopup(final ComicPalette palette, List<Row> rows) {
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(getParent() == null ? palette.getSurface() : getParent().getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    int w = getWidth() - 3;
                    int h = getHeight() - 3 - POINTER;
                    Polygon pointer = new Polygon(new int[]{14, 22, 30}, new int[]{POINTER + 2, 1, POINTER + 2}, 3);
                    g2.setColor(palette.getSurface());
                    g2.fillRoundRect(1, POINTER + 1, w, h, 18, 18);
                    g2.fillPolygon(pointer);
                    g2.setColor(palette.getInk());
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawRoundRect(1, POINTER + 1, w, h, 18, 18);
                    g2.drawLine(14, POINTER + 1, 22, 1);
                    g2.drawLine(22, 1, 30, POINTER + 1);
                    g2.setColor(palette.getSurface());
                    g2.drawLine(16, POINTER + 1, 28, POINTER + 1);
                } finally {
                    g2.dispose();
                }
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(POINTER + 8, 12, 10, 12));
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        for (Row row : rows) {
            addRow(content, row, palette);
        }
        bubble.add(content, BorderLayout.CENTER);
        add(bubble);
    }

    private void addRow(JPanel content, final Row row, ComicPalette palette) {
        final SpinnerNumberModel number = new SpinnerNumberModel(clamp(row.value, row.min, row.max), row.min,
                row.max, 1);
        JSpinner field = new JSpinner(number);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(field, "#");
        field.setEditor(editor);
        field.setBorder(ComicBorder.popupBorder(palette));
        JFormattedTextField text = editor.getTextField();
        text.setColumns(5);
        if (text.getFormatter() instanceof DefaultFormatter) {
            // Typed digits apply while typing, not only on Enter.
            ((DefaultFormatter) text.getFormatter()).setCommitsOnValidEdit(true);
        }
        final JSlider slider = new JSlider(row.sliderMin, row.sliderMax, clamp(row.value, row.sliderMin, row.sliderMax));
        slider.setOpaque(false);
        slider.setMajorTickSpacing(Math.max(1, (row.sliderMax - row.sliderMin) / 4));
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        final boolean[] syncing = {false};
        number.addChangeListener(event -> {
            if (syncing[0]) {
                return;
            }
            int now = number.getNumber().intValue();
            syncing[0] = true;
            slider.setValue(clamp(now, row.sliderMin, row.sliderMax));
            syncing[0] = false;
            row.listener.accept(now);
        });
        slider.addChangeListener(event -> {
            if (syncing[0]) {
                return;
            }
            syncing[0] = true;
            number.setValue(slider.getValue());
            syncing[0] = false;
            row.listener.accept(slider.getValue());
        });
        JLabel title = new JLabel(row.title);
        title.setFont(ResearchUiTypography.semiBold(12f));
        title.setForeground(palette.getInk());
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        head.setOpaque(false);
        head.add(title);
        head.add(field);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(head);
        content.add(slider);
        numbers.add(number);
        sliders.add(slider);
    }

    /** The value of the first row (tool rounds). */
    int value() {
        return value(0);
    }

    int value(int row) {
        return numbers.get(row).getNumber().intValue();
    }

    JSlider slider() {
        return sliders.get(0);
    }

    JSlider slider(int row) {
        return sliders.get(row);
    }

    SpinnerNumberModel number() {
        return numbers.get(0);
    }

    SpinnerNumberModel number(int row) {
        return numbers.get(row);
    }

    int rowCount() {
        return numbers.size();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
