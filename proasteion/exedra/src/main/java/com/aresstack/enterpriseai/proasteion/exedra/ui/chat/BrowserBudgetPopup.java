package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

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
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;

/**
 * The browser budget popup under the globe's arrow (#141): a field for any value and a slider over the usual range,
 * kept in sync; every change goes to the listener at once.
 */
final class BrowserBudgetPopup extends JPopupMenu {

    static final String TITLE = "Werkzeugrunden je Antwort mit Browser";

    private final SpinnerNumberModel number;
    private final JSlider slider;
    private final int sliderMin;
    private final int sliderMax;
    private boolean syncing;

    BrowserBudgetPopup(int value, int min, int max, int sliderMin, int sliderMax, final IntConsumer listener) {
        this.sliderMin = sliderMin;
        this.sliderMax = sliderMax;
        this.number = new SpinnerNumberModel(clamp(value, min, max), min, max, 1);
        JSpinner field = new JSpinner(number);
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(field, "#");
        field.setEditor(editor);
        JFormattedTextField text = editor.getTextField();
        text.setColumns(4);
        if (text.getFormatter() instanceof DefaultFormatter) {
            // Typed digits apply while typing, not only on Enter.
            ((DefaultFormatter) text.getFormatter()).setCommitsOnValidEdit(true);
        }
        this.slider = new JSlider(sliderMin, sliderMax, clamp(value, sliderMin, sliderMax));
        slider.setMajorTickSpacing(Math.max(1, (sliderMax - sliderMin) / 4));
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        number.addChangeListener(event -> {
            if (syncing) {
                return;
            }
            int now = number.getNumber().intValue();
            syncing = true;
            slider.setValue(clamp(now, this.sliderMin, this.sliderMax));
            syncing = false;
            listener.accept(now);
        });
        slider.addChangeListener(event -> {
            if (syncing) {
                return;
            }
            syncing = true;
            number.setValue(slider.getValue());
            syncing = false;
            listener.accept(slider.getValue());
        });
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel title = new JLabel(TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        row.setOpaque(false);
        row.add(field);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);
        content.add(row);
        content.add(slider);
        add(content);
    }

    int value() {
        return number.getNumber().intValue();
    }

    JSlider slider() {
        return slider;
    }

    SpinnerNumberModel number() {
        return number;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
