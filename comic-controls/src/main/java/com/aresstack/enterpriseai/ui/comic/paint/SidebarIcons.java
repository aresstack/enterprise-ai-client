package com.aresstack.enterpriseai.ui.comic.paint;

import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Die Symbole der Drawer-Reiter (statt der Beschriftungen): die Schriftrolle für „Chats“, der Chemiekolben aus
 * askai-java8 (arch, {@code ResearchPhaseToolbarContribution.FlaskIcon}) in Blau für „Wissensquellen“ und der gelbe
 * Stern (MainframeMates ★ im Reiterkopf) für „Favoriten“. Kolben und Stern behalten ihre Farbe in jedem Zustand;
 * die Rolle hat weißes Papier und zeichnet ihre Linien in der Vordergrundfarbe des Knopfs (Tinte in Ruhe, weiß auf
 * der dunklen Platte des aktiven Reiters).
 */
public final class SidebarIcons {

    private static final float SCROLL_GRID = 24f;
    private static final float SCROLL_STROKE = 1.6f;

    private SidebarIcons() {
    }

    /**
     * Die Schriftrolle (24×24-Raster, Strich 1,6, runde Enden): obere und untere Rolle als Kapseln mit dem
     * Rollenquerschnitt rechts oben und links unten, dazwischen die Pergamentbahn mit drei Textzeilen.
     */
    public static StrokeIcon scroll() {
        return new StrokeIcon(20, 20) {
            @Override
            protected void paint(Graphics2D g2) {
                Color ink = g2.getColor();
                g2.scale(getIconWidth() / SCROLL_GRID, getIconHeight() / SCROLL_GRID);
                g2.setStroke(new BasicStroke(SCROLL_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                RoundRectangle2D top = new RoundRectangle2D.Float(4f, 4f, 16f, 3.2f, 3.2f, 3.2f);
                RoundRectangle2D bottom = new RoundRectangle2D.Float(4f, 16.8f, 16f, 3.2f, 3.2f, 3.2f);
                g2.setColor(ResearchUiPalette.SCROLL_PAPER);
                g2.fill(new Rectangle2D.Float(6.2f, 7.2f, 11.6f, 9.6f));
                g2.fill(top);
                g2.fill(bottom);
                g2.setColor(ink);
                g2.draw(top);
                g2.draw(new Arc2D.Float(16.8f, 4f, 3.2f, 3.2f, 90f, 180f, Arc2D.OPEN));
                g2.draw(new Line2D.Float(6.2f, 7.2f, 6.2f, 16.8f));
                g2.draw(new Line2D.Float(17.8f, 7.2f, 17.8f, 16.8f));
                g2.draw(new Line2D.Float(9f, 10.2f, 15f, 10.2f));
                g2.draw(new Line2D.Float(9f, 12.6f, 15f, 12.6f));
                g2.draw(new Line2D.Float(9f, 15f, 13f, 15f));
                g2.draw(bottom);
                g2.draw(new Arc2D.Float(4f, 16.8f, 3.2f, 3.2f, 90f, -180f, Arc2D.OPEN));
            }
        };
    }

    /** Der Erlenmeyerkolben aus askai arch (16×18, Hals, Schultern, flacher Boden, Rand) in Akzentblau. */
    public static StrokeIcon flask() {
        return new StrokeIcon(16, 18) {
            @Override
            protected void paint(Graphics2D g2) {
                g2.setColor(ResearchUiPalette.ACCENT_BLUE);
                Path2D.Float flask = new Path2D.Float();
                flask.moveTo(6f, 1.5f);
                flask.lineTo(6f, 6.5f);
                flask.lineTo(2f, 15.5f);
                flask.lineTo(14f, 15.5f);
                flask.lineTo(10f, 6.5f);
                flask.lineTo(10f, 1.5f);
                g2.draw(flask);
                g2.drawLine(4, 1, 12, 1); // der Rand
            }
        };
    }

    /** Der gelbe Stern (gefüllt) bzw. sein Umriss für „noch kein Favorit“. */
    public static StrokeIcon star(final boolean filled) {
        return new StrokeIcon(16, 16) {
            @Override
            protected void paint(Graphics2D g2) {
                Path2D.Float star = new Path2D.Float();
                double cx = 8;
                double cy = 8.6;
                for (int i = 0; i < 10; i++) {
                    double radius = i % 2 == 0 ? 7.2 : 3.0;
                    double angle = Math.toRadians(-90 + i * 36);
                    float x = (float) (cx + radius * Math.cos(angle));
                    float y = (float) (cy + radius * Math.sin(angle));
                    if (i == 0) {
                        star.moveTo(x, y);
                    } else {
                        star.lineTo(x, y);
                    }
                }
                star.closePath();
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (filled) {
                    g2.setColor(ResearchUiPalette.FAVORITE_YELLOW);
                    g2.fill(star);
                    g2.setColor(ResearchUiPalette.FAVORITE_YELLOW_EDGE);
                    g2.draw(star);
                } else {
                    g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                    g2.draw(star);
                }
            }
        };
    }
}
