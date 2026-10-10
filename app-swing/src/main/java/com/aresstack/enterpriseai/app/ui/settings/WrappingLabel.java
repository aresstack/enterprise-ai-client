package com.aresstack.enterpriseai.app.ui.settings;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.Dimension;
import java.awt.Insets;

/**
 * Beschriftung, die sich der Breite der Seite fügt: HTML-Text bricht in der zugeteilten Breite um und meldet die
 * dazu passende Höhe, einzeiliger Text wird am Ende mit „…“ gekürzt (voller Text gehört dann in den Tooltip).
 * Die Mindestbreite ist 0, damit lange Pfade die Einstellungsseite nicht über den sichtbaren Bereich hinaus
 * verbreitern.
 */
final class WrappingLabel extends JLabel {

    WrappingLabel(String text) {
        super(text);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        View view = (View) getClientProperty(BasicHTML.propertyKey);
        int width = getWidth();
        if (isPreferredSizeSet() || view == null || width <= 0) {
            return size;
        }
        Insets insets = getInsets();
        view.setSize(Math.max(1, width - insets.left - insets.right), 0);
        int height = (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
        return new Dimension(Math.min(size.width, width), height);
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        return new Dimension(0, getPreferredSize().height);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void reshape(int x, int y, int width, int height) {
        int oldWidth = getWidth();
        super.reshape(x, y, width, height);
        if (width != oldWidth && getClientProperty(BasicHTML.propertyKey) != null) {
            // Neue Breite, neue Zeilenzahl: Höhe im nächsten Layoutlauf neu verteilen.
            SwingUtilities.invokeLater(this::revalidate);
        }
    }
}
