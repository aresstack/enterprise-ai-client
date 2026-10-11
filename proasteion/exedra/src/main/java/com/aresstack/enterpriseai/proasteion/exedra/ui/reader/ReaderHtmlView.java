package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.JEditorPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.ComponentView;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyleConstants;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Das Original-HTML einer Seite mit den Bildern an ihrer Stelle, wie MainframeMates {@code ConfluenceReaderTab} im
 * gerenderten Modus: ein nicht editierbares {@link JEditorPane} ({@code text/html}), Basis-URL gesetzt, Bilder im
 * Hintergrund nachgeladen, Klick auf ein Bild öffnet die Diashow (Handcursor über Bildern).
 *
 * <p>Anders als im Vorbild lädt die Ansicht Bilder nie selbst aus dem Netz: jedes {@code <img>} zeichnet eine eigene
 * {@link View}, die ihr Bild aus dem {@link ReaderImageStore} des Reiters holt (über die Quelle, mit deren Anmeldung)
 * und bis dahin einen Platzhalter zeigt. Bilder, die die Quelle nicht kennt, bleiben Platzhalter mit Alternativtext.</p>
 *
 * <p>Textstellen sind adressierbar über Zeichen-Offsets im einen {@link HTMLDocument} ({@code modelToView} zum
 * Rollen), über dessen Element-Baum ({@code HTML.Tag} und Attribute wie {@code id}) und über die {@code id} der
 * Überschriften.</p>
 */
public final class ReaderHtmlView extends JEditorPane implements WidthAwareHeight {

    private static final int PLACEHOLDER_W = 160;
    private static final int PLACEHOLDER_H = 100;

    private final ReaderImageStore store;
    private final Consumer<ReaderImage> imageClicked;
    private final Map<InlineImageView, Boolean> imageViews = new WeakHashMap<InlineImageView, Boolean>();

    /**
     * @param bodyHtml     der HTML-Körper der Seite (ohne {@code <html>}/{@code <body>})
     * @param baseUri      Basis für relative Adressen (Links, Bilder); darf leer sein
     * @param imageClicked Klick auf ein bekanntes Bild (öffnet die Diashow)
     */
    public ReaderHtmlView(String bodyHtml, String baseUri, ReaderImageStore store, final DesktopLinkOpener linkOpener,
                          Consumer<ReaderImage> imageClicked) {
        if (store == null || linkOpener == null || imageClicked == null) {
            throw new IllegalArgumentException("store, linkOpener and imageClicked must not be null");
        }
        this.store = store;
        this.imageClicked = imageClicked;
        setEditable(false);
        setEditorKit(new Kit());
        setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        String base = baseUri == null ? "" : baseUri.trim();
        try {
            if (!base.isEmpty()) {
                ((HTMLDocument) getDocument()).setBase(new URL(base));
            }
        } catch (Exception ignored) {
            // ohne Basis bleiben relative Adressen relativ
        }
        setText(wrapHtml(bodyHtml, base));
        setCaretPosition(0);

        addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED || onImage(event.getInputEvent())) {
                return;
            }
            String description = event.getDescription();
            if (description != null && description.startsWith("#") && description.length() > 1) {
                scrollToId(description.substring(1));
            } else if (event.getURL() != null) {
                linkOpener.open(event.getURL().toString());
            }
        });
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                ReaderImage image = imageAt(event.getPoint());
                if (image != null && event.getButton() == MouseEvent.BUTTON1) {
                    ReaderHtmlView.this.imageClicked.accept(image);
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                setCursor(imageAt(event.getPoint()) != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        // Alle bekannten Bilder im Hintergrund laden; jedes meldet sich, sobald es da ist.
        for (final ReaderImage image : store.images()) {
            store.request(image, loaded -> imageArrived(image));
        }
    }

    /** Das bekannte Bild unter dem Punkt oder {@code null}. */
    public ReaderImage imageAt(Point point) {
        for (InlineImageView view : new ArrayList<InlineImageView>(imageViews.keySet())) {
            if (view.painted != null && view.painted.contains(point) && view.image != null) {
                return view.image;
            }
        }
        return null;
    }

    /** Die Bilder, die die Ansicht gerade als {@code <img>} enthält (in Dokumentreihenfolge, für Tests). */
    public List<ReaderImage> inlineImages() {
        List<ReaderImage> result = new ArrayList<ReaderImage>();
        HTMLDocument doc = (HTMLDocument) getDocument();
        collectImages(doc.getDefaultRootElement(), result);
        return Collections.unmodifiableList(result);
    }

    /** Rollt zum Element mit dieser {@code id} (z. B. einer Überschrift); ohne Treffer nichts. */
    public void scrollToId(String id) {
        HTMLDocument doc = (HTMLDocument) getDocument();
        Element element = doc.getElement(id);
        if (element == null) {
            scrollToReference(id); // <a name="…">
            return;
        }
        try {
            Rectangle target = modelToView(element.getStartOffset());
            if (target != null) {
                Rectangle visible = getVisibleRect();
                target.height = Math.max(target.height, visible.height);
                scrollRectToVisible(target);
            }
        } catch (BadLocationException ignored) {
            // Element ohne Position: nicht rollen
        }
    }

    private void collectImages(Element element, List<ReaderImage> into) {
        if (element.getAttributes().getAttribute(StyleConstants.NameAttribute) == HTML.Tag.IMG) {
            ReaderImage image = store.bySrc(srcOf(element));
            if (image != null) {
                into.add(image);
            }
        }
        for (int i = 0; i < element.getElementCount(); i++) {
            collectImages(element.getElement(i), into);
        }
    }

    private boolean onImage(InputEvent input) {
        return input instanceof MouseEvent && imageAt(((MouseEvent) input).getPoint()) != null;
    }

    private void imageArrived(ReaderImage image) {
        AbstractDocument doc = (AbstractDocument) getDocument();
        doc.readLock();
        try {
            for (InlineImageView view : new ArrayList<InlineImageView>(imageViews.keySet())) {
                if (view.image == image) {
                    view.preferenceChanged(null, true, true);
                }
            }
        } finally {
            doc.readUnlock();
        }
        revalidate();
        repaint();
    }

    private String srcOf(Element element) {
        Object src = element.getAttributes().getAttribute(HTML.Attribute.SRC);
        if (src == null) {
            return "";
        }
        URL base = ((HTMLDocument) getDocument()).getBase();
        try {
            return base == null ? src.toString() : new URL(base, src.toString()).toString();
        } catch (Exception malformed) {
            return src.toString();
        }
    }

    /** Wie MainframeMate {@code ConfluenceReaderTab.wrapHtml}: Basis und schlichtes Stylesheet um den Körper. */
    static String wrapHtml(String bodyHtml, String baseUri) {
        StringBuilder sb = new StringBuilder("<html><head>");
        if (baseUri != null && !baseUri.isEmpty()) {
            sb.append("<base href=\"").append(baseUri.replace("\"", "&quot;")).append("\">");
        }
        sb.append("<style>")
                .append("body { font-family: sans-serif; font-size: 13px; }")
                .append("h1, h2, h3 { color: #333; }")
                .append("a { color: #0645ad; }")
                .append("pre, code { background: #f4f4f4; padding: 4px; }")
                .append("table { border-collapse: collapse; }")
                .append("td, th { border: 1px solid #ccc; padding: 4px; }")
                .append(".confluence-information-macro, .confluence-information-macro-body { ")
                .append("  background: #e8f5e9; border-left: 4px solid #4caf50; padding: 8px; margin: 8px 0; }")
                .append(".confluence-warning-macro, .confluence-warning-macro-body { ")
                .append("  background: #fff3e0; border-left: 4px solid #ff9800; padding: 8px; margin: 8px 0; }")
                .append("</style>")
                .append("</head><body>")
                .append(bodyHtml == null ? "" : bodyHtml)
                .append("</body></html>");
        return sb.toString();
    }

    /**
     * Höhe bei genau dieser Breite: die Seite im Lesebereich legt die Ansicht damit (wie das Markdown-Dokument), damit
     * sie beim Schmalerwerden nicht unten abgeschnitten wird.
     */
    @Override
    public int preferredHeightForWidth(int width) {
        java.awt.Insets insets = getInsets();
        AbstractDocument doc = (AbstractDocument) getDocument();
        doc.readLock();
        try {
            View root = getUI().getRootView(this);
            root.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
            return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
        } finally {
            doc.readUnlock();
        }
    }

    // ------------------------------------------------------------------ Darstellung der Bilder

    /** HTML-Kit mit eigener Bildansicht; eingebettete Objekte (Applets, Formular-Objekte) werden nie erzeugt. */
    private final class Kit extends HTMLEditorKit {
        private final ViewFactory factory = new HTMLFactory() {
            @Override
            public View create(Element element) {
                Object name = element.getAttributes().getAttribute(StyleConstants.NameAttribute);
                if (name == HTML.Tag.IMG) {
                    InlineImageView view = new InlineImageView(element, store.bySrc(srcOf(element)));
                    imageViews.put(view, Boolean.TRUE);
                    return view;
                }
                if (name == HTML.Tag.OBJECT || name == HTML.Tag.APPLET) {
                    return new ComponentView(element) {
                        @Override
                        protected Component createComponent() {
                            return null;
                        }
                    };
                }
                return super.create(element);
            }
        };

        @Override
        public ViewFactory getViewFactory() {
            return factory;
        }
    }

    /**
     * Ein {@code <img>}: das geladene Bild in Originalgröße (bzw. den Maßen aus {@code width}/{@code height}),
     * höchstens so breit wie die Ansicht; bis dahin ein Platzhalter mit Alternativtext.
     */
    private final class InlineImageView extends View {
        final ReaderImage image;
        private final String alt;
        private final int attrWidth;
        private final int attrHeight;
        Rectangle painted;

        InlineImageView(Element element, ReaderImage image) {
            super(element);
            this.image = image;
            AttributeSet attributes = element.getAttributes();
            Object altValue = attributes.getAttribute(HTML.Attribute.ALT);
            this.alt = altValue == null ? "" : altValue.toString();
            this.attrWidth = intAttribute(attributes, HTML.Attribute.WIDTH);
            this.attrHeight = intAttribute(attributes, HTML.Attribute.HEIGHT);
        }

        private BufferedImage bitmap() {
            ReaderImageStore.Loaded loaded = image == null ? null : store.loaded(image);
            return loaded == null ? null : loaded.image();
        }

        private Dimension size() {
            BufferedImage bitmap = bitmap();
            int w;
            int h;
            if (bitmap != null) {
                w = attrWidth > 0 ? attrWidth : bitmap.getWidth();
                h = attrHeight > 0 ? attrHeight
                        : attrWidth > 0 ? Math.max(1, bitmap.getHeight() * attrWidth / bitmap.getWidth())
                        : bitmap.getHeight();
            } else {
                w = attrWidth > 0 ? attrWidth : PLACEHOLDER_W;
                h = attrHeight > 0 ? attrHeight : PLACEHOLDER_H;
            }
            Container container = getContainer();
            int max = container == null ? 0 : container.getWidth() - 48;
            if (max > 40 && w > max) {
                h = Math.max(1, h * max / w);
                w = max;
            }
            return new Dimension(w, h);
        }

        @Override
        public float getPreferredSpan(int axis) {
            Dimension size = size();
            return axis == View.X_AXIS ? size.width : size.height;
        }

        @Override
        public float getAlignment(int axis) {
            return axis == View.Y_AXIS ? 1f : super.getAlignment(axis);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            Rectangle r = allocation.getBounds();
            painted = new Rectangle(r);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                BufferedImage bitmap = bitmap();
                if (bitmap != null) {
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2.drawImage(bitmap, r.x, r.y, r.width, r.height, null);
                    return;
                }
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(236, 236, 236));
                g2.fillRoundRect(r.x, r.y, r.width - 1, r.height - 1, 8, 8);
                g2.setColor(new Color(200, 200, 200));
                g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, 8, 8);
                String text = image == null ? (alt.isEmpty() ? "Bild" : alt)
                        : store.loaded(image) == null ? "Bild wird geladen …" : "Bild nicht ladbar";
                g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                g2.setFont(getFont().deriveFont(Font.ITALIC, 11f));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, r.x + Math.max(4, (r.width - fm.stringWidth(text)) / 2),
                        r.y + (r.height + fm.getAscent()) / 2 - 2);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Shape modelToView(int pos, Shape allocation, Position.Bias bias) throws BadLocationException {
            int p0 = getStartOffset();
            int p1 = getEndOffset();
            if (pos >= p0 && pos <= p1) {
                Rectangle r = allocation.getBounds();
                if (pos == p1) {
                    r.x += r.width;
                }
                r.width = 0;
                return r;
            }
            return null;
        }

        @Override
        public int viewToModel(float x, float y, Shape allocation, Position.Bias[] bias) {
            Rectangle r = allocation.getBounds();
            if (x < r.x + r.width / 2f) {
                bias[0] = Position.Bias.Forward;
                return getStartOffset();
            }
            bias[0] = Position.Bias.Backward;
            return getEndOffset();
        }
    }

    private static int intAttribute(AttributeSet attributes, HTML.Attribute name) {
        Object value = attributes.getAttribute(name);
        if (value == null) {
            return 0;
        }
        try {
            String text = value.toString().trim();
            return text.endsWith("%") ? 0 : Integer.parseInt(text.replace("px", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
