package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechRate;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The speaking-rate slider next to the read-aloud button in the reader toolbar: {@link SpeechRate#MIN} to
 * {@link SpeechRate#MAX} in steps of five percent, with the value beside it ("0,8×", "1,0×", "1,25×"). A click on
 * the value (or Enter/Space on it) opens a menu upwards with fixed rates, normal speed among them (#99); a choice
 * moves the slider, so it reports through the same path as dragging it.
 */
final class ReadAloudRateControl extends JPanel {

    private static final long serialVersionUID = 1L;
    static final String TOOLTIP = "Sprechgeschwindigkeit";
    static final String PRESET_TOOLTIP = "Sprechgeschwindigkeit auswählen";
    /** The rates of the menu; {@link SpeechRate#NORMAL} is the reset. */
    private static final double[] PRESETS = {SpeechRate.MIN, 0.75, SpeechRate.NORMAL, 1.25, 1.5, SpeechRate.MAX};
    private static final String NORMAL_SUFFIX = " – Normal";
    private static final int FOCUS_LINE = 1;
    private static final int PERCENT = 100;
    private static final int STEP_PERCENT = 5;
    private static final int SLIDER_WIDTH = 84;
    private static final float LABEL_SIZE = 11f;

    private final JSlider slider;
    private final JLabel value = new JLabel();
    private DoubleConsumer listener = rate -> {
    };
    private boolean updating;
    private Consumer<JPopupMenu> menuShower = this::showAboveValue;

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
        value.setToolTipText(PRESET_TOOLTIP);
        value.getAccessibleContext().setAccessibleName(TOOLTIP);
        value.getAccessibleContext().setAccessibleDescription(PRESET_TOOLTIP + " (Enter oder Leertaste)");
        value.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        value.setFocusable(true);
        // The same border in both states keeps the width stable; focus only colours the underline.
        value.setBorder(BorderFactory.createEmptyBorder(0, 0, FOCUS_LINE, 0));
        value.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                value.setBorder(BorderFactory.createMatteBorder(0, 0, FOCUS_LINE, 0, ResearchUiPalette.ACCENT_BLUE));
            }

            @Override
            public void focusLost(FocusEvent event) {
                value.setBorder(BorderFactory.createEmptyBorder(0, 0, FOCUS_LINE, 0));
            }
        });
        value.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                openPresets();
            }
        });
        bindOpen(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
        bindOpen(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
        value.setPreferredSize(new Dimension(widestLabel() + 2, height));
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

    /** Opens the preset menu above the value, as a click on it does. */
    void openPresets() {
        menuShower.accept(presetMenu());
    }

    /** The preset menu for the current rate: the matching preset is selected, an in-between rate selects none. */
    JPopupMenu presetMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        int current = slider.getValue();
        for (final double preset : PRESETS) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(presetLabel(preset));
            item.setFont(ResearchUiTypography.regular(LABEL_SIZE));
            item.setSelected(percent(preset) == current);
            item.addActionListener(event -> slider.setValue(percent(preset)));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** How the preset menu appears (tests: catch it without a screen). */
    void setMenuShower(Consumer<JPopupMenu> shower) {
        this.menuShower = shower == null ? this::showAboveValue : shower;
    }

    JLabel valueLabel() {
        return value;
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

    /** "1,0× – Normal" for {@link SpeechRate#NORMAL}, otherwise the plain label. */
    static String presetLabel(double rate) {
        return percent(rate) == percent(SpeechRate.NORMAL) ? label(rate) + NORMAL_SUFFIX : label(rate);
    }

    /** The control sits at the bottom of the window: the menu opens upwards. */
    private void showAboveValue(JPopupMenu menu) {
        menu.show(value, 0, -menu.getPreferredSize().height);
    }

    private void bindOpen(KeyStroke key) {
        String name = "reader.rate.presets";
        value.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        value.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                openPresets();
            }
        });
    }

    /** Width of the widest value the slider can show, so the toolbar does not jump while it moves. */
    private int widestLabel() {
        int widest = 0;
        for (int percent = percent(SpeechRate.MIN); percent <= percent(SpeechRate.MAX); percent += STEP_PERCENT) {
            widest = Math.max(widest, value.getFontMetrics(value.getFont()).stringWidth(label(percent
                    / (double) PERCENT)));
        }
        return widest;
    }

    private static int percent(double rate) {
        return (int) Math.round(rate * PERCENT);
    }
}
