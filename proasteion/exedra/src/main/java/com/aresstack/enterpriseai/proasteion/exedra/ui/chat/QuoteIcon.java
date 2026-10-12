package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.paint.StrokeIcon;

import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.QuadCurve2D;

/** Öffnendes Anführungszeichen (zwei gefüllte Tropfen mit Schwung) als Java2D-Strich, kein Unicode-Zeichen. */
final class QuoteIcon extends StrokeIcon {

    QuoteIcon() {
        super(13, 13);
    }

    @Override
    protected void paint(Graphics2D g2) {
        drawMark(g2, 1.5f);
        drawMark(g2, 7.5f);
    }

    private static void drawMark(Graphics2D g2, float x) {
        g2.fill(new Ellipse2D.Float(x, 6f, 4.2f, 4.2f));
        g2.draw(new QuadCurve2D.Float(x + 0.6f, 8f, x + 0.2f, 3.5f, x + 3.6f, 2f));
    }
}
