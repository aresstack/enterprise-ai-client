package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import com.aresstack.enterpriseai.ui.comic.paint.StrokeIcon;

import java.awt.Graphics2D;
import java.awt.geom.Arc2D;

/**
 * Die Strichsymbole der gelben Suchvorschläge (askai arch: grüner Haken nach der Suche, rote Kreispfeil-Wiederholung
 * nach einem Fehler), als Java2D-Linien statt Unicode-Zeichen. Sie malen in der Vordergrundfarbe des Tags.
 */
final class SearchTagIcons {

    private SearchTagIcons() {
    }

    /** Haken: schon gesucht. */
    static final class Check extends StrokeIcon {
        Check() {
            super(12, 12);
        }

        @Override
        protected void paint(Graphics2D g2) {
            g2.drawLine(1, 6, 4, 10);
            g2.drawLine(4, 10, 11, 2);
        }
    }

    /** Kreispfeil: erneut versuchen. */
    static final class Retry extends StrokeIcon {
        Retry() {
            super(12, 12);
        }

        @Override
        protected void paint(Graphics2D g2) {
            g2.draw(new Arc2D.Double(1.5, 1.5, 9, 9, 60, 280, Arc2D.OPEN));
            // Pfeilspitze am oberen Ende des Bogens
            g2.drawLine(8, 1, 9, 4);
            g2.drawLine(9, 4, 6, 4);
        }
    }
}
