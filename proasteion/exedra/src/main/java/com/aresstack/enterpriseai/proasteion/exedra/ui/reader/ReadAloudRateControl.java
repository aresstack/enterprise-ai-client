package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechRate;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * The speaking-rate slider next to the read-aloud button in the reader toolbar: {@link SpeechRate#MIN} to
 * {@link SpeechRate#MAX} in steps of five percent, with the value beside it ("0,8×", "1,0×", "1,25×").
 */
final class ReadAloudRateControl extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String TOOLTIP = "Sprechgeschwindigkeit";
    private static final int PERCENT = 100;
    private static final int STEP_PERCENT = 5;
    private static final int SLIDER_WIDTH = 84;
    private static final float LABEL_SIZE = 11f;

    private final JSlider slider;
    private final JLabel value = new JLabel();
    private DoubleConsumer listener = rate -> {
    };
    private boolean updating;

    ReadAloudRateControl(int height) {
        super(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setOpaque(false);
        slider = new JSlider(percent(SpeechRate.MIN), percent(SpeechRate.MAX), percent(SpeechRate.NORMAL));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setMinorTickSpacing(STEP_PERCENT);
        slider.setSnapToTicks(true);
        slider.setToolTipText(TOOLTIP);
        slider.setPreferredSize(new Dimension(SLIDER_WIDTH, height));
        slider.getAccessibleContext().setAccessibleName(TOOLTIP);
        value.setFont(ResearchUiTypography.regular(LABEL_SIZE));
        value.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        value.setToolTipText(TOOLTIP);
        value.setPreferredSize(new Dimension(value.getFontMetrics(value.getFont()).stringWidth(label(1.25)) + 2,
                height));
        add(slider);
        add(value);
        slider.addChangeListener(event -> {
            double rate = rate();
            value.setText(label(rate));
            if (!updating) {
                listener.accept(rate);
            }
        });
        value.setText(label(SpeechRate.NORMAL));
    }

    /** Called with the new rate whenever the user moves the slider. */
    void onChange(DoubleConsumer listener) {
        this.listener = listener == null ? rate -> {
        } : listener;
    }

    /** Shows {@code rate} without reporting it back. */
    void setRate(double rate) {
        updating = true;
        try {
            slider.setValue(percent(SpeechRate.clamp(rate)));
            value.setText(label(rate()));
        } finally {
            updating = false;
        }
    }

    double rate() {
        return slider.getValue() / (double) PERCENT;
    }

    JSlider slider() {
        return slider;
    }

    String valueText() {
        return value.getText();
    }

    /** "1,0×", "0,8×", "1,25×". */
    static String label(double rate) {
        return new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(Locale.GERMAN)).format(rate) + "×";
    }

    private static int percent(double rate) {
        return (int) Math.round(rate * PERCENT);
    }
}
