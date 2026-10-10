package com.aresstack.enterpriseai.ui.comic.paint;

import java.awt.Graphics2D;
import java.awt.geom.Line2D;

/**
 * AskAI's composer glyph set as reusable {@link StrokeIcon}s (send, stop, hamburger, gear, plus,
 * chevrons, close); pencil and refresh are own additions for list rows. The reference geometry is
 * verbatim — no icon assets, every glyph is drawn with the owning component's foreground.
 */
public final class ComposerIcons {

    private ComposerIcons() {
    }

    /** The paper-plane "send" arrow. */
    public static StrokeIcon send() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                int[] x = {2, 13, 6};
                int[] y = {2, 7, 13};
                g2.drawPolygon(x, y, 3);
                g2.drawLine(3, 7, 11, 7);
            }
        };
    }

    /** AskAI's paperclip (attach files): an open hook curving up on the left and back down on the right. */
    public static StrokeIcon paperclip() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.drawLine(4, 4, 4, 11);
                g2.drawArc(4, 2, 6, 5, 90, 180);
                g2.drawLine(10, 4, 10, 12);
                g2.drawArc(3, 9, 7, 6, 0, -180);
            }
        };
    }

    /** The filled "stop" square. */
    public static StrokeIcon stop() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.fillRoundRect(4, 4, 8, 8, 2, 2);
            }
        };
    }

    /** The three-line hamburger. */
    public static StrokeIcon menu() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.drawLine(2, 4, 13, 4);
                g2.drawLine(2, 8, 13, 8);
                g2.drawLine(2, 12, 13, 12);
            }
        };
    }

    /** The settings gear: ring, hub and eight ticks. */
    public static StrokeIcon gear() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                int cx = 7;
                int cy = 7;
                int r = 4;
                g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);
                g2.drawOval(cx - 2, cy - 2, 4, 4);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    int x1 = (int) Math.round(cx + Math.cos(a) * r);
                    int y1 = (int) Math.round(cy + Math.sin(a) * r);
                    int x2 = (int) Math.round(cx + Math.cos(a) * (r + 2.5));
                    int y2 = (int) Math.round(cy + Math.sin(a) * (r + 2.5));
                    g2.drawLine(x1, y1, x2, y2);
                }
            }
        };
    }

    /** A "+" of the given size (AskAI's new-chat glyph), stroke scaled with the size. */
    public static StrokeIcon plus(final int size) {
        return new StrokeIcon(size, size) {
            @Override
            protected void paint(Graphics2D g2) {
                g2.setStroke(new java.awt.BasicStroke(Math.max(1.6f, size / 8f),
                        java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                double pad = size * 0.22;
                double center = size / 2.0;
                g2.draw(new Line2D.Double(pad, center, size - pad, center));
                g2.draw(new Line2D.Double(center, pad, center, size - pad));
            }
        };
    }

    public static StrokeIcon chevronDown() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.drawLine(3, 6, 7, 10);
                g2.drawLine(7, 10, 11, 6);
            }
        };
    }

    public static StrokeIcon chevronLeft() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.drawLine(9, 3, 5, 7);
                g2.drawLine(5, 7, 9, 11);
            }
        };
    }

    public static StrokeIcon chevronRight() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.drawLine(6, 3, 10, 7);
                g2.drawLine(10, 7, 6, 11);
            }
        };
    }

    /** A pencil (edit): a slanted shaft with a tip and a short cap line. */
    public static StrokeIcon pencil() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                int[] x = {3, 10, 12, 5, 3};
                int[] y = {10, 3, 5, 12, 12};
                g2.drawPolyline(x, y, 5);
                g2.drawLine(3, 10, 5, 12);
                g2.drawLine(9, 4, 11, 6);
            }
        };
    }

    /** Circular arrows (re-index, refresh): an open ring with an arrowhead at its end. */
    public static StrokeIcon refresh() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.draw(new java.awt.geom.Arc2D.Double(2.5, 2.5, 10, 10, 60, 280, java.awt.geom.Arc2D.OPEN));
                g2.drawLine(10, 1, 10, 4);
                g2.drawLine(10, 4, 13, 4);
            }
        };
    }

    /** The small ✕ glyph (discard). */
    public static StrokeIcon close() {
        return new StrokeIcon() {
            @Override
            protected void paint(Graphics2D g2) {
                g2.drawLine(4, 4, 12, 12);
                g2.drawLine(12, 4, 4, 12);
            }
        };
    }
}
