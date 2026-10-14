package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import com.aresstack.enterpriseai.astu.navigation.SourcePosition;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Dokument im Lesebereich mit dem Navigationsbanner darüber: die Bildlauffläche liegt unten, der
 * {@link DocumentNavigationSky} als durchsichtige Ebene darüber (wie der Himmel über dem Chatverlauf). Der Banner
 * verkleinert die Breite des Dokuments nie (FA-07); oben bekommt das Dokument so viel Platz, wie der Banner meldet.
 *
 * <p>Quellpositionen (FA-05) werden auf die Ansicht abgebildet: der Block oberster Ebene, in dem die Startposition
 * liegt ({@link MarkdownMessageView#SOURCE_START}), darin die Zeile, in der der Anker beginnt. Beim Rollen meldet die
 * Seite dem Banner das Kapitel und den tiefsten Abschnitt an der Oberkante unter dem Banner (FA-08); ein Klick im
 * Banner rollt so, dass der Abschnitt direkt unter dem Banner steht (FA-10, FA-12). Das Dokument selbst wird nie
 * verändert.</p>
 */
final class NavigableDocumentPage extends JLayeredPane {

    /** Abstand unter dem Banner, an dem ein angesprungener Abschnitt steht und ab dem er als aktiv gilt. */
    static final int TARGET_GAP = 8;

    private final JScrollPane scroll;
    /** {@link MarkdownMessageView} oder die HTML-Lese-Ansicht ({@link ReaderHtmlView}). */
    private final JComponent view;
    private final JComponent page;
    private final int basePaddingTop;
    private final DocumentNavigationSky sky = new DocumentNavigationSky();
    /** Abschnitt → y in Seitenkoordinaten; leer = neu berechnen. */
    private final Map<String, Integer> positions = new LinkedHashMap<String, Integer>();
    /** Größe der Seite, für die {@link #positions} gemessen wurden. */
    private java.awt.Dimension positionsSize;
    private int topInset;
    /** Zuletzt angesprungener Abschnitt und die Rollposition danach. */
    private NavigationSection jumped;
    private int jumpedViewY = -1;

    /**
     * @param page           die Seite in der Bildlauffläche (enthält {@code view})
     * @param basePaddingTop oberer Innenabstand der Seite ohne Banner
     */
    NavigableDocumentPage(JScrollPane scroll, JComponent page, JComponent view, int basePaddingTop) {
        this.scroll = scroll;
        this.page = page;
        this.view = view;
        this.basePaddingTop = basePaddingTop;
        setLayout(null);
        add(scroll, JLayeredPane.DEFAULT_LAYER);
        add(sky, JLayeredPane.PALETTE_LAYER);
        sky.addPropertyChangeListener(DocumentNavigationSky.TOP_INSET_PROPERTY, event -> applyTopInset());
        sky.setListener(this::scrollTo);
        scroll.getViewport().addChangeListener(event -> syncActive());
        page.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                syncActive(); // Umbruch oder Inhalt geändert: positionOf misst neu
            }
        });
    }

    DocumentNavigationSky sky() {
        return sky;
    }

    JScrollPane scroll() {
        return scroll;
    }

    void setNavigation(DocumentNavigation navigation) {
        positions.clear();
        jumped = null;
        sky.setNavigation(navigation);
        syncActive();
    }

    @Override
    public void doLayout() {
        scroll.setBounds(0, 0, getWidth(), getHeight());
        sky.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        return scroll.getPreferredSize();
    }

    private void applyTopInset() {
        Object value = sky.getClientProperty(DocumentNavigationSky.TOP_INSET_PROPERTY);
        int inset = value instanceof Integer ? Math.max(0, (Integer) value) : 0;
        if (inset == topInset) {
            return;
        }
        topInset = inset;
        java.awt.Insets insets = page.getInsets();
        page.setBorder(javax.swing.BorderFactory.createEmptyBorder(basePaddingTop + topInset, insets.left,
                insets.bottom, insets.right));
        positions.clear();
        page.revalidate();
    }

    // ------------------------------------------------------------------ Springen und Mitlaufen

    /** Rollt so, dass der Abschnitt direkt unter dem Banner steht. */
    void scrollTo(NavigationSection section) {
        if (!page.isValid()) {
            // Lay out first when the banner just changed its height: the top padding moves every heading. Lay out the
            // viewport and the page directly as well, validate() skips a tree that is not displayable yet.
            scroll.validate();
            scroll.getViewport().doLayout();
            page.doLayout();
        }
        Integer y = positionOf(section);
        if (y == null) {
            return;
        }
        JViewport viewport = scroll.getViewport();
        int target = y - sky.coveredHeight() - TARGET_GAP;
        int max = Math.max(0, page.getHeight() - viewport.getExtentSize().height);
        Point to = new Point(0, Math.max(0, Math.min(max, target)));
        jumped = section;
        jumpedViewY = to.y;
        viewport.setViewPosition(to);
        syncActive();
    }

    /** Kapitel und tiefster Abschnitt an der Oberkante unter dem Banner. */
    void syncActive() {
        DocumentNavigation navigation = sky.navigation();
        if (navigation == null || navigation.isEmpty() || page.getHeight() <= 0) {
            return;
        }
        int line = scroll.getViewport().getViewPosition().y + sky.coveredHeight() + TARGET_GAP + 4;
        String chapterId = navigation.chapters().get(0).id();
        String sectionId = null;
        for (NavigationSection chapter : navigation.chapters()) {
            Integer y = positionOf(chapter);
            if (y != null && y <= line) {
                chapterId = chapter.id();
            }
        }
        NavigationSection active = navigation.find(chapterId);
        if (active != null) {
            for (NavigationSection section : descendants(active)) {
                Integer y = positionOf(section);
                if (y != null && y <= line) {
                    sectionId = section.id();
                }
            }
        }
        if (jumped != null && scroll.getViewport().getViewPosition().y == jumpedViewY) {
            // Nach einem Sprung gilt das gewählte Ziel, auch wenn es die Oberkante nicht erreichen kann (Dokumentende),
            // bis wieder gerollt wird.
            NavigationSection chapter = chapterOf(navigation, jumped);
            if (chapter != null) {
                chapterId = chapter.id();
                sectionId = chapter.id().equals(jumped.id()) ? null : jumped.id();
            }
        }
        sky.setActive(chapterId, sectionId);
    }

    private static NavigationSection chapterOf(DocumentNavigation navigation, NavigationSection section) {
        for (NavigationSection chapter : navigation.chapters()) {
            if (chapter.id().equals(section.id()) || descendants(chapter).contains(section)) {
                return chapter;
            }
        }
        return null;
    }

    private static List<NavigationSection> descendants(NavigationSection chapter) {
        List<NavigationSection> all = new ArrayList<NavigationSection>();
        collect(chapter.children(), all);
        return all;
    }

    private static void collect(List<NavigationSection> sections, List<NavigationSection> into) {
        for (NavigationSection section : sections) {
            into.add(section);
            collect(section.children(), into);
        }
    }

    /** y des Abschnitts in Seitenkoordinaten oder {@code null}, wenn die Seite noch nicht gelegt ist. */
    Integer positionOf(NavigationSection section) {
        if (page.getWidth() <= 0) {
            return null;
        }
        if (!page.getSize().equals(positionsSize)) {
            positions.clear(); // anderer Umbruch oder nachgeladene Bilder: neu messen
            positionsSize = page.getSize();
        }
        Integer known = positions.get(section.id());
        if (known == null) {
            known = locate(section.position());
            if (page.isValid()) {
                positions.put(section.id(), known); // nur fertig gelegte Blöcke merken (Viewport meldet sich mitten im Layout)
            }
        }
        return known;
    }

    /** Block oberster Ebene zur Startposition, darin die Zeile des Ankers. */
    private int locate(SourcePosition position) {
        if (view instanceof ReaderHtmlView) {
            // HTML-Lese-Ansicht (FA-05: stabile DOM-Referenz bevorzugt): zuerst die Überschrift im Element-Baum, sonst
            // der Anker im ganzen Dokument (Themenabschnitte beginnen mitten im Text).
            ReaderHtmlView html = (ReaderHtmlView) view;
            String anchor = normalize(position.anchor());
            int heading = html.headingOffset(anchor);
            if (heading >= 0) {
                try {
                    Rectangle rect = modelToView(html, heading);
                    if (rect != null) {
                        return SwingUtilities.convertPoint(html, rect.getLocation(), page).y;
                    }
                } catch (BadLocationException e) {
                    // weiter über den Text
                }
            }
            Integer y = anchorY(html, anchor);
            return y == null ? 0 : y;
        }
        MarkdownMessageView markdown = (MarkdownMessageView) view;
        List<JComponent> blocks = markdown.renderedBlocks();
        if (blocks.isEmpty()) {
            return 0;
        }
        JComponent block = null;
        if (markdown.sourceOffsetsExact()) {
            for (JComponent candidate : blocks) {
                Object start = candidate.getClientProperty(MarkdownMessageView.SOURCE_START);
                if (start instanceof Integer && (Integer) start <= position.start()) {
                    block = candidate;
                } else if (start instanceof Integer) {
                    break;
                }
            }
        }
        List<JComponent> searchIn = new ArrayList<JComponent>();
        if (block != null) {
            searchIn.add(block);
            // Ein Passagenanfang kann im Folgeblock liegen (gleiche Position, anderer Block): dort weitersuchen.
            int index = blocks.indexOf(block);
            if (index + 1 < blocks.size()) {
                searchIn.add(blocks.get(index + 1));
            }
        } else {
            searchIn.addAll(blocks); // Text vor dem Rendern umgeformt: über den Anker im ganzen Dokument
        }
        String anchor = normalize(position.anchor());
        for (JComponent candidate : searchIn) {
            Integer y = anchorY(candidate, anchor);
            if (y != null) {
                return y;
            }
        }
        JComponent fallback = block != null ? block : blocks.get(0);
        return SwingUtilities.convertPoint(fallback.getParent(), fallback.getLocation(), page).y;
    }

    /** y der Zeile, in der der Anker im Text einer Textkomponente unterhalb von {@code root} beginnt. */
    private Integer anchorY(Component root, String anchor) {
        if (anchor.isEmpty()) {
            return null;
        }
        String probe = anchor.length() > 24 ? anchor.substring(0, 24) : anchor;
        for (JTextComponent text : textComponents(root)) {
            String content;
            try {
                content = text.getDocument().getText(0, text.getDocument().getLength());
            } catch (BadLocationException e) {
                continue;
            }
            int[] map = new int[content.length()];
            StringBuilder normalized = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    map[normalized.length()] = i;
                    normalized.append(Character.toLowerCase(c));
                }
            }
            int hit = normalized.indexOf(probe);
            if (hit < 0) {
                continue;
            }
            try {
                Rectangle rect = modelToView(text, map[hit]);
                if (rect != null) {
                    return SwingUtilities.convertPoint(text, rect.getLocation(), page).y;
                }
            } catch (BadLocationException e) {
                // Zeile unbekannt: Oberkante der Komponente
            }
            return SwingUtilities.convertPoint(text.getParent(), text.getLocation(), page).y;
        }
        return null;
    }

    @SuppressWarnings("deprecation") // modelToView2D erst ab Java 9; der Client bleibt Java 8
    private static Rectangle modelToView(JTextComponent text, int offset) throws BadLocationException {
        return text.modelToView(offset);
    }

    private static List<JTextComponent> textComponents(Component root) {
        List<JTextComponent> found = new ArrayList<JTextComponent>();
        collectText(root, found);
        return found;
    }

    private static void collectText(Component component, List<JTextComponent> into) {
        if (component instanceof JTextComponent) {
            into.add((JTextComponent) component);
            return;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectText(child, into);
            }
        }
    }

    static String normalize(String value) {
        StringBuilder out = new StringBuilder();
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    out.append(Character.toLowerCase(c));
                }
            }
        }
        return out.toString();
    }
}
