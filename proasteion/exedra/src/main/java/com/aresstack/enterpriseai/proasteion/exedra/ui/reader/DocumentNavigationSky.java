package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import com.aresstack.enterpriseai.ui.comic.paint.SkyClouds;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiMetrics;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPainter;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Der Navigationsbanner über dem Dokument im Lesebereich: der Wolkenhimmel aus askai-java8 arch
 * {@code ResearchOutOfScopeSky} (dieselbe Wolkenzeichnung, derselbe durchsichtige Verlauf, dieselbe Durchlässigkeit
 * und Höhengrenze über {@link SkyClouds}), hier mit den Hauptkapiteln als EINE waagrechte Wolkenreihe statt
 * umbrechender Wolken.
 *
 * <ul>
 *   <li>Das aktive Kapitel steht waagrecht in der Mitte (FA-08); links die vorigen, rechts die folgenden. Vor dem
 *       ersten und nach dem letzten Kapitel ist so viel Rand, dass auch sie mittig stehen können; kein Umlauf.</li>
 *   <li>Das Mausrad über dem Banner verschiebt die Reihe waagrecht und rollt nie das Dokument (FA-09). Danach
 *       zentriert erst ein tatsächlicher Kapitelwechsel wieder.</li>
 *   <li>Ein Klick auf ein Kapitel meldet es dem {@link Listener} (FA-10).</li>
 *   <li>Der Pfeil links klappt den Himmel nach unten auf (FA-11): darunter die Gliederung NUR des aktiven Kapitels,
 *       Originalunterüberschriften und erkannte Themenabschnitte, je anklickbar (FA-12). Höchstens
 *       {@link ResearchUiMetrics#SKY_EXPANDED_MAX_PERCENT} % der Höhe, darüber rollt nur die Gliederung (FA-13).</li>
 *   <li>Lange Bezeichnungen werden mit „…“ gekürzt, der Tooltip zeigt sie ganz; die Breite einer Wolke richtet sich
 *       nach der verfügbaren Breite, so passen in schmalen Fenstern weniger Nachbarn (FA-14).</li>
 *   <li>Automatisch erkannte Abschnitte stehen kursiv in einer gestrichelten Wolke; Originalüberschriften gerade in
 *       einer durchgezogenen (FA-04).</li>
 * </ul>
 *
 * <p>{@link #contains(int, int)} beansprucht nur die bedienbare Zone; darunter, im Auslauf des Verlaufs, bleibt das
 * Dokument klick- und rollbar. Den nötigen Platz oben meldet der Banner über {@link #TOP_INSET_PROPERTY}.</p>
 */
public final class DocumentNavigationSky extends JPanel {

    /** Client-Property: so viel Platz braucht der Banner oben im Dokument (Pixel). */
    public static final String TOP_INSET_PROPERTY = "enterpriseai.readerTopInset";
    static final String GENERATED_HINT = "automatisch erkannter Abschnitt";
    static final int WHEEL_STEP = 48;
    private static final float FONT_SIZE = 12.5f;
    private static final int CHEVRON_SIZE = 22;
    private static final int ROW_HEIGHT = ResearchUiMetrics.CLOUD_CHIP_HEIGHT + 4;
    private static final int INDENT = 18;

    /** Was der Banner meldet; auf dem EDT. */
    public interface Listener {
        /** Ein Kapitel oder Unterabschnitt wurde angeklickt. */
        void sectionSelected(NavigationSection section);
    }

    private final ChapterStrip strip = new ChapterStrip();
    private final ExpandChevron chevron = new ExpandChevron();
    private final OutlinePanel outline = new OutlinePanel();
    private final JScrollPane outlineScroll;
    private Listener listener;
    private DocumentNavigation navigation;
    private List<NavigationSection> chapters = Collections.emptyList();
    private String activeChapterId;
    private String activeSectionId;
    private boolean expanded;
    private int contentBottom;

    public DocumentNavigationSky() {
        super(null);
        setOpaque(false);
        setName("reader.navigationSky");
        outlineScroll = new JScrollPane(outline, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        outlineScroll.setBorder(BorderFactory.createEmptyBorder());
        outlineScroll.setOpaque(false);
        outlineScroll.getViewport().setOpaque(false);
        outlineScroll.getVerticalScrollBar().setUnitIncrement(16);
        com.aresstack.enterpriseai.ui.comic.control.ComicScrollBarUI.install(outlineScroll.getVerticalScrollBar());
        // Die Gliederung rollt zuerst selbst; hat sie nichts zu rollen, geht das Rad an den Himmel (nie ans Dokument).
        SkyClouds.installWheelPriority(outlineScroll, outlineScroll);
        outlineScroll.setVisible(false);
        add(strip);
        add(chevron);
        add(outlineScroll);
        // Rad über dem Himmel (Luft oder Wolkenreihe): waagrecht durch die Kapitel, das Dokument bleibt stehen.
        addMouseWheelListener(strip::wheel);
        // Wie arch: ein Klick in die Luft des aufgeklappten Himmels klappt ihn wieder ein.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (expanded) {
                    setExpanded(false);
                }
            }
        });
        setVisible(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Zeigt die Navigation (leer: kein Banner). Das aktive Kapitel bleibt, wenn es noch existiert. EDT. */
    public void setNavigation(DocumentNavigation value) {
        navigation = value;
        chapters = value == null ? Collections.<NavigationSection>emptyList() : value.chapters();
        boolean keep = false;
        for (NavigationSection chapter : chapters) {
            keep |= chapter.id().equals(activeChapterId);
        }
        if (!keep) {
            activeChapterId = chapters.isEmpty() ? null : chapters.get(0).id();
            strip.manual = false;
        }
        strip.rebuild();
        outline.rebuild();
        setVisible(!chapters.isEmpty());
        if (chapters.isEmpty()) {
            publishTopInset(0);
        }
        revalidate();
        repaint();
    }

    public DocumentNavigation navigation() {
        return navigation;
    }

    /**
     * Das Kapitel, in dem das Dokument gerade steht, und darin der tiefste sichtbare Abschnitt (oder {@code null}).
     * Ein Kapitelwechsel zentriert die Reihe neu, auch nach manuellem Verschieben; derselbe Wert ändert nichts.
     */
    public void setActive(String chapterId, String sectionId) {
        boolean chapterChanged = chapterId != null && !chapterId.equals(activeChapterId);
        boolean sectionChanged = sectionId == null ? activeSectionId != null : !sectionId.equals(activeSectionId);
        if (chapterChanged) {
            activeChapterId = chapterId;
            strip.manual = false;
            strip.centerOnActive(true);
            outline.rebuild();
            revalidate();
        }
        if (chapterChanged || sectionChanged) {
            activeSectionId = sectionId;
            strip.repaint();
            outline.repaint();
        }
    }

    public String activeChapterId() {
        return activeChapterId;
    }

    public String activeSectionId() {
        return activeSectionId;
    }

    public void setExpanded(boolean value) {
        if (expanded != value) {
            expanded = value;
            outline.rebuild();
            revalidate();
            repaint();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Unterkante der bedienbaren Zone: ein angesprungener Abschnitt muss darunter stehen (FA-10). */
    public int coveredHeight() {
        return isVisible() ? contentBottom : 0;
    }

    // Für Tests (gleiches Paket).
    int stripOffsetForTest() {
        return (int) Math.round(strip.offset);
    }

    int targetOffsetForTest(String chapterId) {
        return (int) Math.round(strip.targetOffsetOf(chapterId));
    }

    boolean manualForTest() {
        return strip.manual;
    }

    void wheelForTest(MouseWheelEvent event) {
        strip.wheel(event);
    }

    JComponent stripForTest() {
        return strip;
    }

    List<NavigationSection> outlineSectionsForTest() {
        return outline.shown;
    }

    void clickChapterForTest(int index) {
        strip.select(index);
    }

    String chipTextForTest(int index) {
        return strip.chipText(index);
    }

    JScrollPane outlineScrollForTest() {
        return outlineScroll;
    }

    // ------------------------------------------------------------------ Treffer, Malen, Layout

    @Override
    public boolean contains(int x, int y) {
        return isVisible() && y >= 0 && y <= Math.min(contentBottom, getHeight());
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (contentBottom <= 0) {
            return;
        }
        Graphics2D g2 = ResearchUiPainter.prepare(graphics);
        try {
            int skyHeight = Math.min(getHeight(), contentBottom + ResearchUiMetrics.SKY_FADE_TAIL);
            SkyClouds.paintSky(g2, getWidth(), contentBottom, skyHeight);
        } finally {
            g2.dispose();
        }
    }

    @Override
    public void doLayout() {
        if (getWidth() <= 0 || getHeight() <= 0 || chapters.isEmpty()) {
            contentBottom = 0;
            publishTopInset(0);
            return;
        }
        int padH = ResearchUiMetrics.SKY_PADDING_H;
        int top = ResearchUiMetrics.SKY_PADDING_TOP;
        boolean expandable = !activeChildren().isEmpty();
        chevron.setVisible(expandable);
        chevron.setBounds(padH, top + (ResearchUiMetrics.CLOUD_CHIP_HEIGHT - CHEVRON_SIZE) / 2, CHEVRON_SIZE,
                CHEVRON_SIZE);
        int stripLeft = padH + CHEVRON_SIZE + 6;
        strip.setBounds(stripLeft, top, Math.max(0, getWidth() - stripLeft - padH),
                ResearchUiMetrics.CLOUD_CHIP_HEIGHT);
        strip.layoutChips();
        int bottom = top + ResearchUiMetrics.CLOUD_CHIP_HEIGHT + 8;
        boolean showOutline = expanded && expandable;
        outlineScroll.setVisible(showOutline);
        if (showOutline) {
            int outlineTop = bottom;
            int natural = outline.shown.size() * ROW_HEIGHT;
            int cap = Math.max(ROW_HEIGHT * 2,
                    getHeight() * ResearchUiMetrics.SKY_EXPANDED_MAX_PERCENT / 100 - outlineTop);
            int height = Math.min(natural, cap);
            outlineScroll.setBounds(stripLeft, outlineTop, Math.max(0, getWidth() - stripLeft - padH), height);
            outlineScroll.revalidate();
            bottom = outlineTop + height + 8;
        }
        contentBottom = bottom;
        publishTopInset(contentBottom);
    }

    private void publishTopInset(int pixels) {
        putClientProperty(TOP_INSET_PROPERTY, Integer.valueOf(pixels));
    }

    private NavigationSection activeChapter() {
        for (NavigationSection chapter : chapters) {
            if (chapter.id().equals(activeChapterId)) {
                return chapter;
            }
        }
        return null;
    }

    private List<NavigationSection> activeChildren() {
        NavigationSection chapter = activeChapter();
        return chapter == null ? Collections.<NavigationSection>emptyList() : chapter.children();
    }

    private void fireSelected(NavigationSection section) {
        if (listener != null && section != null) {
            listener.sectionSelected(section);
        }
    }

    static Font fontFor(NavigationSection section) {
        Font font = ResearchUiTypography.regular(FONT_SIZE);
        return section.isGenerated() ? font.deriveFont(Font.ITALIC) : font;
    }

    static String tooltipFor(NavigationSection section) {
        return section.isGenerated() ? section.label() + " (" + GENERATED_HINT + ")" : section.label();
    }

    /** Text gekürzt auf {@code width} Pixel, mit „…“. */
    static String elide(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end).trim() + ellipsis) > width) {
            end--;
        }
        return end == 0 ? ellipsis : text.substring(0, end).trim() + ellipsis;
    }

    private static Stroke borderFor(NavigationSection section, boolean active) {
        float width = active ? 1.8f : 1.2f;
        return section.isGenerated()
                ? new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[]{4f, 3f}, 0f)
                : new BasicStroke(width);
    }

    /** Eine Wolke mit Bezeichnung, wie die Wolken des Himmels; aktiv mit kräftigem Rand. */
    private static void paintCloud(Graphics2D g2, NavigationSection section, String text, int width, int height,
                                   boolean hovered, boolean active) {
        SkyClouds.paintCloudBase(g2, width, height, hovered || active, borderFor(section, active));
        if (active) {
            g2.setColor(ResearchUiPalette.ACCENT_BLUE);
            g2.setStroke(borderFor(section, true));
            g2.draw(SkyClouds.cloudShape(width, height));
        }
        g2.setFont(fontFor(section));
        FontMetrics metrics = g2.getFontMetrics();
        g2.setColor(ResearchUiPalette.CLOUD_TEXT);
        g2.drawString(text, ResearchUiMetrics.CLOUD_CHIP_PADDING_H, SkyClouds.cloudTextY(metrics, height));
    }

    // ------------------------------------------------------------------ Kapitelreihe

    /** Die Hauptkapitel als eine waagrechte Wolkenreihe, verschiebbar, mit Rand für die Zentrierung. */
    private final class ChapterStrip extends JComponent {

        private final List<Rectangle> chips = new ArrayList<Rectangle>();
        private final List<String> texts = new ArrayList<String>();
        private double offset;
        private double target;
        private boolean manual;
        private int hovered = -1;
        private int laidOutWidth = -1;
        private final Timer animation = new Timer(15, event -> step());

        ChapterStrip() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            ToolTipSupport.register(this);
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent event) {
                    int index = indexAt(event.getX());
                    if (index != hovered) {
                        hovered = index;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = -1;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    int index = indexAt(event.getX());
                    if (index >= 0) {
                        select(index);
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(this::wheel);
        }

        void rebuild() {
            laidOutWidth = -1;
            layoutChips();
            centerOnActive(false);
            repaint();
        }

        /** Breiten aus der verfügbaren Breite: in schmalen Fenstern schmalere Wolken, weniger Nachbarn (FA-14). */
        void layoutChips() {
            int width = getWidth();
            if (width == laidOutWidth && chips.size() == chapters.size()) {
                return;
            }
            boolean resized = laidOutWidth >= 0 && laidOutWidth != width;
            laidOutWidth = width;
            chips.clear();
            texts.clear();
            if (width <= 0) {
                return;
            }
            int maxChip = Math.max(80, Math.min(240, (int) (width * 0.42)));
            int height = ResearchUiMetrics.CLOUD_CHIP_HEIGHT;
            int x = 0;
            for (NavigationSection chapter : chapters) {
                FontMetrics metrics = getFontMetrics(fontFor(chapter));
                int pad = ResearchUiMetrics.CLOUD_CHIP_PADDING_H;
                String text = elide(chapter.label(), metrics, maxChip - 2 * pad);
                int chipWidth = Math.min(maxChip, metrics.stringWidth(text) + 2 * pad);
                chips.add(new Rectangle(x, 0, chipWidth, height));
                texts.add(text);
                x += chipWidth + ResearchUiMetrics.CLOUD_GAP_H;
            }
            if (resized && !manual) {
                centerOnActive(false);
            }
        }

        /** Rand links: so viel, dass das erste Kapitel mittig stehen kann. */
        private int lead() {
            return chips.isEmpty() ? 0 : getWidth() / 2 - chips.get(0).width / 2;
        }

        private double maxOffset() {
            if (chips.isEmpty()) {
                return 0;
            }
            Rectangle last = chips.get(chips.size() - 1);
            // Rand rechts entsprechend: das letzte Kapitel kann ebenfalls mittig stehen.
            return Math.max(0, last.x + last.width / 2.0 + lead() - getWidth() / 2.0);
        }

        double targetOffsetOf(String chapterId) {
            for (int i = 0; i < chapters.size() && i < chips.size(); i++) {
                if (chapters.get(i).id().equals(chapterId)) {
                    Rectangle chip = chips.get(i);
                    // Versatz so, dass die Mitte der Wolke in der Mitte der Reihe steht.
                    return clamp(lead() + chip.x + chip.width / 2.0 - getWidth() / 2.0);
                }
            }
            return offset;
        }

        private double clamp(double value) {
            return Math.max(0, Math.min(maxOffset(), value));
        }

        void centerOnActive(boolean animate) {
            if (manual || activeChapterId == null) {
                return;
            }
            target = targetOffsetOf(activeChapterId);
            if (!animate || !isShowing()) {
                offset = target;
                animation.stop();
                repaint();
            } else if (!animation.isRunning()) {
                animation.start();
            }
        }

        private void step() {
            double delta = target - offset;
            if (Math.abs(delta) < 0.75) {
                offset = target;
                animation.stop();
            } else {
                offset += delta * 0.25;
            }
            repaint();
        }

        void wheel(MouseWheelEvent event) {
            double rotation = event.getPreciseWheelRotation();
            if (rotation == 0) {
                rotation = event.getWheelRotation();
            }
            animation.stop();
            manual = true;
            offset = clamp(offset + rotation * WHEEL_STEP);
            target = offset;
            event.consume(); // nie zugleich das Dokument rollen
            repaint();
        }

        void select(int index) {
            if (index < 0 || index >= chapters.size()) {
                return;
            }
            NavigationSection chapter = chapters.get(index);
            setActive(chapter.id(), null);
            fireSelected(chapter);
        }

        String chipText(int index) {
            return index >= 0 && index < texts.size() ? texts.get(index) : null;
        }

        private int indexAt(int x) {
            double contentX = x + offset - lead();
            for (int i = 0; i < chips.size(); i++) {
                Rectangle chip = chips.get(i);
                if (contentX >= chip.x && contentX < chip.x + chip.width) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = indexAt(event.getX());
            return index < 0 || index >= chapters.size() ? null : tooltipFor(chapters.get(index));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            layoutChips();
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                int shift = (int) Math.round(lead() - offset);
                for (int i = 0; i < chips.size() && i < chapters.size(); i++) {
                    Rectangle chip = chips.get(i);
                    int x = chip.x + shift;
                    if (x + chip.width < 0 || x > getWidth()) {
                        continue;
                    }
                    Graphics2D cloud = (Graphics2D) g2.create(x, 0, chip.width, chip.height);
                    try {
                        NavigationSection chapter = chapters.get(i);
                        paintCloud(cloud, chapter, texts.get(i), chip.width, chip.height, i == hovered,
                                chapter.id().equals(activeChapterId));
                    } finally {
                        cloud.dispose();
                    }
                }
            } finally {
                g2.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ Aufklappen

    /** Der kleine Pfeil links: klappt die Gliederung des aktiven Kapitels auf und zu. */
    private final class ExpandChevron extends JComponent {

        private boolean hovered;

        ExpandChevron() {
            setName("reader.navigationExpand");
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    setExpanded(!expanded);
                }
            });
        }

        @Override
        public String getToolTipText() {
            return expanded ? "Unterabschnitte einklappen" : "Unterabschnitte des Kapitels zeigen";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                if (hovered) {
                    g2.setColor(ResearchUiPainter.mix(ResearchUiPalette.SKY_BAR_SURFACE, java.awt.Color.WHITE,
                            0.35f));
                    g2.fillOval(0, 0, getWidth(), getHeight());
                }
                java.awt.Color color = hovered ? ResearchUiPalette.CLOUD_TEXT : ResearchUiPalette.SKY_CAPTION;
                if (expanded) {
                    ResearchUiPainter.paintChevronUp(g2, getWidth() / 2, getHeight() / 2, 5, color);
                } else {
                    ResearchUiPainter.paintChevronDown(g2, getWidth() / 2, getHeight() / 2, 5, color);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Die Gliederung des aktiven Kapitels: je Abschnitt eine Wolke, eingerückt nach Tiefe. */
    private final class OutlinePanel extends JPanel implements Scrollable {

        private List<NavigationSection> shown = new ArrayList<NavigationSection>();

        OutlinePanel() {
            super(null);
            setOpaque(false);
        }

        void rebuild() {
            removeAll();
            shown = new ArrayList<NavigationSection>();
            if (expanded) {
                collect(activeChildren(), shown);
            }
            for (NavigationSection section : shown) {
                add(new SectionCloud(section));
            }
            revalidate();
            repaint();
        }

        private void collect(List<NavigationSection> sections, List<NavigationSection> into) {
            for (NavigationSection section : sections) {
                into.add(section);
                collect(section.children(), into);
            }
        }

        @Override
        public void doLayout() {
            int y = 0;
            NavigationSection chapter = activeChapter();
            int baseLevel = chapter == null ? 1 : chapter.level();
            for (java.awt.Component child : getComponents()) {
                SectionCloud cloud = (SectionCloud) child;
                int indent = Math.max(0, cloud.section.level() - baseLevel - 1) * INDENT;
                int available = Math.max(40, getWidth() - indent - 4);
                int width = Math.min(available, cloud.getPreferredSize().width);
                cloud.setBounds(indent, y + 2, width, ResearchUiMetrics.CLOUD_CHIP_HEIGHT);
                y += ROW_HEIGHT;
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Math.max(1, getWidth()), shown.size() * ROW_HEIGHT);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Ein Unterabschnitt in der Gliederung; Klick springt hin. */
    private final class SectionCloud extends JComponent {

        private final NavigationSection section;
        private boolean hovered;

        SectionCloud(NavigationSection section) {
            this.section = section;
            setToolTipText(tooltipFor(section));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    activeSectionId = SectionCloud.this.section.id();
                    DocumentNavigationSky.this.repaint();
                    fireSelected(SectionCloud.this.section);
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(fontFor(section));
            return new Dimension(metrics.stringWidth(section.label()) + 2 * ResearchUiMetrics.CLOUD_CHIP_PADDING_H,
                    ResearchUiMetrics.CLOUD_CHIP_HEIGHT);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = ResearchUiPainter.prepare(graphics);
            try {
                FontMetrics metrics = g2.getFontMetrics(fontFor(section));
                String text = elide(section.label(), metrics,
                        getWidth() - 2 * ResearchUiMetrics.CLOUD_CHIP_PADDING_H);
                paintCloud(g2, section, text, getWidth(), getHeight(), hovered,
                        section.id().equals(activeSectionId));
            } finally {
                g2.dispose();
            }
        }
    }

    /** Tooltips für Komponenten, die {@code getToolTipText(MouseEvent)} je Stelle beantworten. */
    private static final class ToolTipSupport {
        static void register(JComponent component) {
            javax.swing.ToolTipManager.sharedInstance().registerComponent(component);
        }
    }
}
