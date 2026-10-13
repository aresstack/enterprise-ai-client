package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.update.JarPatcher;
import com.aresstack.enterpriseai.proasteion.application.update.RunningJar;
import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Merlin's crystal cave: an image-relative, animated comic scene, without external services.
 * Every visual and interactive element is painted on top of the original illustration.
 */
public final class MerlinCavePanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String IMAGE_RESOURCE = "/merlin-crystal-cave.jpg";

    public MerlinCavePanel(ComicPalette palette) {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel title = new JLabel("Merlin – Legacy Intelligence", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(palette.getInk());
        add(title, BorderLayout.NORTH);

        BufferedImage image = loadCaveImage();
        JPanel picture = image == null ? missingImage(palette) : new CrystalScene(image);
        picture.setPreferredSize(new Dimension(480, 280));
        add(picture, BorderLayout.CENTER);

        JLabel status = new JLabel("Aufenthaltsort: Kristallhöhle  ·  Status: Standby seit Einführung der KI",
                SwingConstants.CENTER);
        status.setForeground(palette.getInk());
        status.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.NORTH);
        footer.add(updateRow(), BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
    }

    /** "Update einspielen …": builds the next versioned fat jar from a local patch (issue #92). */
    private JPanel updateRow() {
        ComicButton button = new ComicButton(JarUpdateAction.BUTTON_TEXT);
        button.setToolTipText("Erzeugt aus einem lokalen Update-Paket die neue Jar-Datei neben der laufenden;"
                + " die laufende Version bleibt unverändert.");
        JarUpdateAction action = new JarUpdateAction(this, () -> RunningJar.of(MerlinCavePanel.class),
                new JarPatcher(), command -> {
                    Thread worker = new Thread(command, "jar-update");
                    worker.setDaemon(true);
                    worker.start();
                }, new JarUpdateAction.SwingDialogs(), busy -> {
                    button.setEnabled(!busy);
                    setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : null);
                });
        button.addActionListener(event -> action.run());
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.add(button);
        return row;
    }

    private BufferedImage loadCaveImage() {
        try (InputStream stream = getClass().getResourceAsStream(IMAGE_RESOURCE)) {
            return stream == null ? null : ImageIO.read(stream);
        } catch (IOException exception) {
            return null;
        }
    }

    private JPanel missingImage(ComicPalette palette) {
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setOpaque(false);
        JLabel message = new JLabel("Merlins Kristallhöhle wartet auf ihr Bild.", SwingConstants.CENTER);
        message.setForeground(palette.getInk());
        placeholder.add(message, BorderLayout.CENTER);
        return placeholder;
    }

    /**
     * Coordinates refer to the actual 1536 x 839 illustration. Painting is transformed into
     * image space, so it stays aligned when Swing letterboxes or scales the image.
     */
    private static final class CrystalScene extends JPanel {
        private static final long serialVersionUID = 1L;
        private static final int FRAME_DELAY_MS = 40;
        private static final long AWAKEN_DURATION_NS = 1250000000L;
        private static final long SPEECH_DELAY_NS = 250000000L;
        private static final long SPEECH_FADE_NS = 350000000L;
        private static final long SPEECH_HOLD_NS = 6800000000L;
        private static final long SPEECH_OUT_NS = 800000000L;

        // Actual crystals and glint-friendly facets (normalized to the original image).
        private static final double[][] CRYSTALS = {
                {0.078, 0.159, 0.88}, {0.094, 0.470, 0.86}, {0.059, 0.664, 0.90},
                {0.117, 0.778, 0.75}, {0.191, 0.464, 0.71}, {0.222, 0.672, 0.72},
                {0.455, 0.435, 0.90}, {0.433, 0.638, 0.82}, {0.547, 0.180, 1.00},
                {0.646, 0.075, 1.15}, {0.714, 0.187, 1.05}, {0.791, 0.282, 0.91},
                {0.828, 0.648, 0.94}, {0.773, 0.659, 0.84}, {0.721, 0.768, 0.78},
                {0.969, 0.540, 0.80}, {0.872, 0.789, 0.93}, {0.938, 0.808, 0.75}
        };

        private static final String[] REPLIES = {
                "Ach, jetzt erinnert ihr euch wieder an mich?",
                "Eure KI hat wohl die Zauberformel vergessen.",
                "Ich bin nicht veraltet. Ich bin legendär.",
                "Nur keine Panik. Ich habe schon schlimmere Bugs gesehen."
        };

        // Merlin's head, robe and chair are at approximately x=840..1040, y=355..675.
        private static final double MERLIN_X = 944.0;
        private static final double MERLIN_Y = 522.0;
        private static final double MERLIN_RADIUS_X = 124.0;
        private static final double MERLIN_RADIUS_Y = 183.0;
        private static final Color OUTLINE = new Color(30, 35, 62);
        private static final Color CREAM = new Color(255, 250, 226);
        private static final Color MAGIC = new Color(110, 232, 255);

        private final BufferedImage image;
        private final Timer timer;
        private final long originNs = System.nanoTime();
        private boolean hovered;
        private long awakenStartNs = -1L;
        private long speechStartNs = -1L;
        private int replyIndex = -1;

        private CrystalScene(BufferedImage image) {
            this.image = image;
            setOpaque(false);
            setFocusable(true);
            getAccessibleContext().setAccessibleName("Merlin um Rat fragen");
            getAccessibleContext().setAccessibleDescription(
                    "Interaktive Kristallhöhle. Merlin mit Maus anklicken oder mit Eingabe aktivieren.");

            timer = new Timer(FRAME_DELAY_MS, event -> repaint());
            timer.setCoalesce(true);
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    syncAnimation();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    setHover(isMerlinHit(event.getPoint()));
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent event) {
                    setHover(false);
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1
                            && isMerlinHit(event.getPoint())) {
                        requestFocusInWindow();
                        askMerlin();
                    }
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "askMerlin");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("SPACE"), "askMerlin");
            getActionMap().put("askMerlin", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    askMerlin();
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            syncAnimation();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            hovered = false;
            super.removeNotify();
        }

        private void syncAnimation() {
            if (isShowing()) {
                timer.start();
            } else {
                timer.stop();
                hovered = false;
            }
        }

        private void setHover(boolean value) {
            if (hovered == value) {
                return;
            }
            hovered = value;
            setCursor(value ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
            repaint();
        }

        private boolean isMerlinHit(java.awt.Point point) {
            Rectangle frame = imageBounds();
            if (frame.width <= 0 || frame.height <= 0 || !frame.contains(point)) {
                return false;
            }
            double imageX = (point.x - frame.x) * image.getWidth() / frame.getWidth();
            double imageY = (point.y - frame.y) * image.getHeight() / frame.getHeight();
            double dx = (imageX - MERLIN_X) / MERLIN_RADIUS_X;
            double dy = (imageY - MERLIN_Y) / MERLIN_RADIUS_Y;
            return dx * dx + dy * dy <= 1.0;
        }

        private void askMerlin() {
            long now = System.nanoTime();
            awakenStartNs = now;
            speechStartNs = now + SPEECH_DELAY_NS;
            replyIndex = (replyIndex + 1) % REPLIES.length;
            repaint();
        }

        private Rectangle imageBounds() {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return new Rectangle();
            }
            double scale = Math.min((double) getWidth() / image.getWidth(),
                    (double) getHeight() / image.getHeight());
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            return new Rectangle((getWidth() - width) / 2, (getHeight() - height) / 2,
                    width, height);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Rectangle frame = imageBounds();
            if (frame.width <= 0 || frame.height <= 0) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, frame.x, frame.y, frame.width, frame.height, this);

                Graphics2D overlay = (Graphics2D) g.create(frame.x, frame.y, frame.width, frame.height);
                try {
                    overlay.clipRect(0, 0, frame.width, frame.height);
                    overlay.scale(frame.width / (double) image.getWidth(),
                            frame.height / (double) image.getHeight());
                    long now = System.nanoTime();
                    drawCrystalLights(overlay, now);
                    if (hovered || isFocusOwner()) {
                        drawMerlinHover(overlay);
                    }
                    drawSpeechBubble(overlay, now);
                } finally {
                    overlay.dispose();
                }
            } finally {
                g.dispose();
            }
        }

        private void drawCrystalLights(Graphics2D g, long now) {
            double seconds = (now - originNs) / 1000000000.0;
            double surge = 0.0;
            if (awakenStartNs > 0L) {
                double elapsed = (double) (now - awakenStartNs) / AWAKEN_DURATION_NS;
                if (elapsed < 1.0) {
                    surge = Math.sin(Math.PI * Math.max(0.0, elapsed));
                }
            }
            for (int i = 0; i < CRYSTALS.length; i++) {
                double[] c = CRYSTALS[i];
                double cx = c[0] * image.getWidth();
                double cy = c[1] * image.getHeight();
                double phase = seconds * (1.4 + (i % 4) * 0.19) + i * 2.12;
                double wave = (1.0 + Math.sin(phase)) / 2.0;
                double pulse = wave * wave;
                double strength = Math.min(1.0, (0.24 + pulse * 0.8 + surge * 0.95) * c[2]);
                float radius = (float) (28.0 + c[2] * 36.0 + surge * 14.0);
                float alpha = (float) Math.min(0.8, 0.25 + 0.55 * strength);

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), radius,
                        new float[] {0f, 0.22f, 0.64f, 1f},
                        new Color[] {new Color(255, 255, 255, 245),
                                new Color(160, 244, 255, 175),
                                new Color(104, 167, 255, 65),
                                new Color(104, 167, 255, 0)}));
                g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));

                if (pulse > 0.67 || (surge > 0.22 && i % 3 == 0)) {
                    float glintAlpha = (float) Math.min(1.0,
                            (pulse * 0.80 + surge * 0.82) * c[2]);
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glintAlpha));
                    g.setColor(Color.WHITE);
                    g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));
                    double ray = (12.0 + 11.0 * strength + 5.0 * surge);
                    g.drawLine((int) (cx - ray), (int) cy, (int) (cx + ray), (int) cy);
                    g.drawLine((int) cx, (int) (cy - ray * 1.25),
                            (int) cx, (int) (cy + ray * 1.25));
                    g.setColor(new Color(255, 255, 245));
                    g.fill(new Ellipse2D.Double(cx - 3.8, cy - 3.8, 7.6, 7.6));
                }
            }
            if (surge > 0.0) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                        (float) (0.10 * surge)));
                g.setColor(MAGIC);
                g.fillRect(0, 0, image.getWidth(), image.getHeight());
            }
            g.setComposite(AlphaComposite.SrcOver);
        }

        private void drawMerlinHover(Graphics2D g) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.70f));
            g.setColor(MAGIC);
            g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 10f, new float[] {12f, 9f}, 0f));
            g.draw(new Ellipse2D.Double(MERLIN_X - MERLIN_RADIUS_X, MERLIN_Y - MERLIN_RADIUS_Y,
                    MERLIN_RADIUS_X * 2, MERLIN_RADIUS_Y * 2));
            g.setComposite(AlphaComposite.SrcOver);

            String label = "Merlin um Rat fragen";
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 29);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            double padding = 26;
            double w = metrics.stringWidth(label) + padding * 2;
            double h = 53;
            double x = Math.max(10, Math.min(image.getWidth() - w - 10, MERLIN_X - w / 2));
            double y = 694;
            RoundRectangle2D.Double outline = new RoundRectangle2D.Double(x, y, w, h, 22, 22);
            g.setColor(new Color(16, 27, 53, 230));
            g.fill(outline);
            g.setStroke(new BasicStroke(3f));
            g.setColor(new Color(125, 235, 255));
            g.draw(outline);
            g.setColor(Color.WHITE);
            g.drawString(label, (float) (x + padding), (float) (y + 36));
        }

        private void drawSpeechBubble(Graphics2D g, long now) {
            if (speechStartNs < 0L || replyIndex < 0 || now < speechStartNs) {
                return;
            }
            long elapsed = now - speechStartNs;
            long disappearAt = SPEECH_FADE_NS + SPEECH_HOLD_NS;
            if (elapsed >= disappearAt + SPEECH_OUT_NS) {
                speechStartNs = -1L;
                return;
            }
            double alpha = elapsed < SPEECH_FADE_NS
                    ? elapsed / (double) SPEECH_FADE_NS
                    : elapsed > disappearAt
                            ? 1.0 - (elapsed - disappearAt) / (double) SPEECH_OUT_NS
                            : 1.0;
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
                    (float) Math.min(1.0, Math.max(0.0, alpha))));
            drawComicBubble(g, REPLIES[replyIndex]);
            g.setComposite(AlphaComposite.SrcOver);
        }

        private void drawComicBubble(Graphics2D g, String reply) {
            double x = image.getWidth() * 0.357;
            double y = image.getHeight() * 0.291;
            double width = image.getWidth() * 0.242;
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 31);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            List<String> lines = wrapLines(reply, metrics, width - 56);
            double height = Math.max(137, lines.size() * 41 + 47);
            double radius = 40;

            // Rounded panel plus triangular tail, pointing towards Merlin's left shoulder.
            RoundRectangle2D.Double shadow = new RoundRectangle2D.Double(x + 8, y + 9,
                    width, height, radius, radius);
            g.setColor(new Color(14, 18, 42, 100));
            g.fill(shadow);
            Path2D.Double tail = new Path2D.Double();
            tail.moveTo(x + width - 73, y + height - 6);
            tail.lineTo(x + width + 25, y + height + 66);
            tail.lineTo(x + width - 26, y + height - 77);
            tail.closePath();
            g.setColor(CREAM);
            g.fill(tail);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(OUTLINE);
            g.draw(tail);

            RoundRectangle2D.Double shape = new RoundRectangle2D.Double(x, y,
                    width, height, radius, radius);
            g.setColor(CREAM);
            g.fill(shape);
            g.setColor(OUTLINE);
            g.draw(shape);

            g.setColor(OUTLINE);
            float lineY = (float) (y + 40);
            for (String line : lines) {
                g.drawString(line, (float) (x + 28), lineY);
                lineY += 41;
            }
        }

        private List<String> wrapLines(String text, FontMetrics metrics, double maximumWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (current.length() > 0 && metrics.stringWidth(candidate) > maximumWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            return lines;
        }
    }
}
