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
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.InlineView;
import java.awt.Color;
import java.awt.Component;
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
 * und bis dahin einen Platzhalter zeigt. Ein {@code <img>}, das die Quelle nicht als Bild meldet (Symbol eines
 * unbekannten Makros), ist kein Inhaltsbild: es zeigt nur seinen Alternativtext in Textgröße oder gar nichts, nie einen
 * Bildplatzhalter, und ein Klick darauf gehört dem umgebenden Link.</p>
 *
 * <p>Textstellen sind adressierbar über Zeichen-Offsets im einen {@link HTMLDocument} ({@code modelToView} zum
 * Rollen), über dessen Element-Baum ({@code HTML.Tag} und Attribute wie {@code id}) und über die {@code id} der
 * Überschriften.</p>
 */
public final class ReaderHtmlView extends JEditorPane implements WidthAwareHeight {

    private static final int PLACEHOLDER_W = 160;
    private static final int PLACEHOLDER_H = 100;
    /** Room an image leaves for the body margin, list indents and table cell padding around it. */
    static final int IMAGE_MARGIN = 48;
    /** Below this width an image is not shrunk any further. */
    private static final int IMAGE_MIN_WIDTH = 40;

    private final ReaderImageStore store;
    private final Consumer<ReaderImage> imageClicked;
    private final Map<InlineImageView, Boolean> imageViews = new WeakHashMap<InlineImageView, Boolean>();
    /** The width the view is laid out for (or measured at); images shrink to it. 0 = not known yet. */
    private int layoutWidth;

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

    /** Bevorzugte Größe der {@code <img>}, die kein bekanntes Bild sind (für Tests). */
    List<Dimension> unknownImageSizes() {
        List<Dimension> result = new ArrayList<Dimension>();
        for (InlineImageView view : new ArrayList<InlineImageView>(imageViews.keySet())) {
            if (view.image == null) {
                result.add(view.size());
            }
        }
        return result;
    }

    /** Bevorzugte Größe der bekannten Bilder bei der aktuellen Breite (für Tests). */
    List<Dimension> knownImageSizes() {
        List<Dimension> result = new ArrayList<Dimension>();
        for (InlineImageView view : new ArrayList<InlineImageView>(imageViews.keySet())) {
            if (view.image != null) {
                result.add(view.size());
            }
        }
        return result;
    }

    /** Die Bilder, die die Ansicht gerade als {@code <img>} enthält (in Dokumentreihenfolge, für Tests). */
    public List<ReaderImage> inlineImages() {
        List<ReaderImage> result = new ArrayList<ReaderImage>();
        HTMLDocument doc = (HTMLDocument) getDocument();
        collectImages(doc.getDefaultRootElement(), result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Anfang der ersten Überschrift ({@code h1}–{@code h6}), deren Text normalisiert (nur Buchstaben und Ziffern,
     * klein) gleich {@code normalizedText} ist; {@code -1} ohne Treffer.
     */
    public int headingOffset(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return -1;
        }
        HTMLDocument doc = (HTMLDocument) getDocument();
        doc.readLock();
        try {
            return headingOffset(doc, doc.getDefaultRootElement(), normalizedText);
        } finally {
            doc.readUnlock();
        }
    }

    private static int headingOffset(HTMLDocument doc, Element element, String wanted) {
        Object name = element.getAttributes().getAttribute(StyleConstants.NameAttribute);
        if (name == HTML.Tag.H1 || name == HTML.Tag.H2 || name == HTML.Tag.H3 || name == HTML.Tag.H4
                || name == HTML.Tag.H5 || name == HTML.Tag.H6) {
            try {
                String text = doc.getText(element.getStartOffset(),
                        element.getEndOffset() - element.getStartOffset());
                if (wanted.equals(NavigableDocumentPage.normalize(text))) {
                    return element.getStartOffset();
                }
            } catch (BadLocationException ignored) {
                // Element ohne Text: weiter
            }
            return -1;
        }
        for (int i = 0; i < element.getElementCount(); i++) {
            int found = headingOffset(doc, element.getElement(i), wanted);
            if (found >= 0) {
                return found;
            }
        }
        return -1;
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
            followWidth(width);
            View root = getUI().getRootView(this);
            root.setSize(Math.max(1, width - insets.left - insets.right), Integer.MAX_VALUE);
            return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + insets.top + insets.bottom;
        } finally {
            doc.readUnlock();
        }
    }

    /**
     * Lay the images out for {@code width}: they scale with the width the view actually gets, not with the width it had
     * before, so a narrower reader (sidebar shown, window smaller) never keeps a diagram at its old size.
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        if (width != layoutWidth) {
            AbstractDocument doc = (AbstractDocument) getDocument();
            doc.readLock();
            try {
                followWidth(width);
            } finally {
                doc.readUnlock();
            }
        }
        super.setBounds(x, y, width, height);
    }

    /** Remember the layout width; on a change the images report new sizes (caller holds the read lock). */
    private void followWidth(int width) {
        if (width <= 0 || width == layoutWidth) {
            return;
        }
        layoutWidth = width;
        for (InlineImageView view : new ArrayList<InlineImageView>(imageViews.keySet())) {
            view.preferenceChanged(null, true, true);
        }
    }

    /** The widest an image may be at the current layout width; 0 = no limit known yet. */
    int imageWidthLimit() {
        int width = layoutWidth > 0 ? layoutWidth : getWidth();
        int limit = width - IMAGE_MARGIN;
        return limit > IMAGE_MIN_WIDTH ? limit : width > 0 ? IMAGE_MIN_WIDTH : 0;
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
                if (name == HTML.Tag.IMPLIED && isPreformatted(element)) {
                    // Swing's LineView never wraps a code line; a paragraph view wraps it at the reader width.
                    return new javax.swing.text.html.ParagraphView(element);
                }
                View view = super.create(element);
                return view instanceof InlineView ? new BreakableInlineView(element) : view;
            }
        };

        @Override
        public ViewFactory getViewFactory() {
            return factory;
        }
    }

    private static boolean isPreformatted(Element element) {
        Object whiteSpace = element.getAttributes().getAttribute(CSS.Attribute.WHITE_SPACE);
        return whiteSpace != null && "pre".equals(whiteSpace.toString());
    }

    /** Schmaler als das gibt ein langes Wort (Adresse, Pfad) in einer Tabellenspalte nicht nach. */
    static final float LONG_WORD_MIN_SPAN = 80f;

    /**
     * Text, dessen lange Wörter umbrechen dürfen. Swing nennt sonst das längste Wort als Mindestbreite; eine lange
     * Adresse in einer Tabellenzelle macht dann die Spalte und damit die Tabelle breiter als den Lesebereich, und der
     * Text war rechts abgeschnitten. Mit gedeckelter Mindestbreite wird die Spalte schmal genug, und
     * {@code GlyphView.breakView} trennt das Wort, wo es keine Leerstelle gibt, am letzten Zeichen, das noch passt.
     */
    static final class BreakableInlineView extends InlineView {
        BreakableInlineView(Element element) {
            super(element);
        }

        @Override
        public float getMinimumSpan(int axis) {
            float span = super.getMinimumSpan(axis);
            return axis == View.X_AXIS && wraps() ? Math.min(span, LONG_WORD_MIN_SPAN) : span;
        }

        /**
         * Only {@code white-space: nowrap} keeps its real minimum width. Code in {@code <pre>} wraps as well (issue
         * 90): a long code line must not make the whole reader scroll sideways.
         */
        private boolean wraps() {
            Object whiteSpace = getAttributes().getAttribute(CSS.Attribute.WHITE_SPACE);
            return whiteSpace == null || !"nowrap".equals(whiteSpace.toString());
        }
    }

    /**
     * Ein {@code <img>}: das geladene Bild in Originalgröße (bzw. den Maßen aus {@code width}/{@code height}),
     * höchstens so breit wie die Ansicht; bis dahin ein Platzhalter. Ohne bekanntes Bild nur der Alternativtext in
     * Textgröße (ohne Alternativtext keine Fläche), gleich was {@code width}/{@code height} sagen.
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

        private Font altFont() {
            return ReaderHtmlView.this.getFont().deriveFont(Font.ITALIC);
        }

        Dimension size() {
            if (image == null) {
                if (alt.isEmpty()) {
                    return new Dimension(0, 0);
                }
                FontMetrics fm = ReaderHtmlView.this.getFontMetrics(altFont());
                return new Dimension(fm.stringWidth(alt), fm.getHeight());
            }
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
            int max = imageWidthLimit();
            if (max > 0 && w > max) {
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

        /** An image shrinks with the reader, so it never makes the page wider than the reader (issue 90). */
        @Override
        public float getMinimumSpan(int axis) {
            return axis == View.X_AXIS && image != null ? 0f : getPreferredSpan(axis);
        }

        @Override
        public float getAlignment(int axis) {
            if (axis == View.Y_AXIS && image == null && !alt.isEmpty()) {
                FontMetrics fm = ReaderHtmlView.this.getFontMetrics(altFont());
                return fm.getAscent() / (float) fm.getHeight(); // Grundlinie wie der Text daneben
            }
            return axis == View.Y_AXIS ? 1f : super.getAlignment(axis);
        }

        @Override
        public void paint(Graphics g, Shape allocation) {
            Rectangle r = allocation.getBounds();
            painted = new Rectangle(r);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                if (image == null) {
                    if (!alt.isEmpty()) {
                        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g2.setColor(ResearchUiPalette.LIGHT_TEXT_MUTED);
                        g2.setFont(altFont());
                        g2.drawString(alt, r.x, r.y + g2.getFontMetrics().getAscent());
                    }
                    return;
                }
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
                String text = store.loaded(image) == null ? "Bild wird geladen …" : "Bild nicht ladbar";
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
